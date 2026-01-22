package com.pirorin215.fastrecmob.viewModel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pirorin215.fastrecmob.LocationData
import com.pirorin215.fastrecmob.LocationTracker
import com.pirorin215.fastrecmob.MainActivity
import com.pirorin215.fastrecmob.R
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.Settings
import com.pirorin215.fastrecmob.data.TranscriptionResult
import com.pirorin215.fastrecmob.data.TranscriptionResultRepository
import com.pirorin215.fastrecmob.data.TranscriptionProvider
import com.pirorin215.fastrecmob.data.AIProvider
import com.pirorin215.fastrecmob.data.ProviderMode
import com.pirorin215.fastrecmob.service.SpeechToTextService
import com.pirorin215.fastrecmob.service.GroqSpeechService
import com.pirorin215.fastrecmob.service.GeminiService
import com.pirorin215.fastrecmob.service.GroqLLMService
import com.pirorin215.fastrecmob.data.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appSettingsRepository: AppSettingsRepository,
    private val transcriptionResultRepository: TranscriptionResultRepository,
    private val currentForegroundLocationFlow: StateFlow<LocationData?>,
    private val audioDirNameFlow: StateFlow<String>,
    private val transcriptionCacheLimitFlow: StateFlow<Int>,
    private val logManager: LogManager,
    private val googleTaskTitleLengthFlow: StateFlow<Int>, // New parameter
    private val googleTasksIntegration: GoogleTasksManager,
    private val locationTracker: LocationTracker
) {

    companion object {
        const val TRANSCRIPTION_CHANNEL_ID = "TranscriptionChannel"
        const val TRANSCRIPTION_NOTIFICATION_ID = 2002
    }

    private var speechToTextService: SpeechToTextService? = null
    private var groqSpeechService: GroqSpeechService? = null
    private var geminiService: GeminiService? = null  // For AI button feature
    private var groqLLMService: GroqLLMService? = null  // For AI button with Groq
    private var notificationIdCounter = TRANSCRIPTION_NOTIFICATION_ID

    private val _transcriptionState = MutableStateFlow("Idle")
    val transcriptionState: StateFlow<String> = _transcriptionState.asStateFlow()

    private val _transcriptionResult = MutableStateFlow<String?>(null)
    val transcriptionResult: StateFlow<String?> = _transcriptionResult.asStateFlow()

    private val _transcriptionCompletedFlow = MutableSharedFlow<String>() // ADD
    val transcriptionCompletedFlow: SharedFlow<String> = _transcriptionCompletedFlow.asSharedFlow() // ADD

    private val _audioFileCount = MutableStateFlow(0)
    val audioFileCount: StateFlow<Int> = _audioFileCount.asStateFlow()

    /**
     * In-memory queue for immediate processing (no need to wait for DataStore Flow propagation)
     *
     * USAGE RULES:
     * - queueMutex protects pendingQueue operations
     * - Keep lock time minimal (add/remove operations only)
     * - Independent from BLE operations (no interaction with bleMutex)
     */
    private val pendingQueue = mutableListOf<TranscriptionResult>()
    private val queueMutex = Mutex()

    init {
        createTranscriptionNotificationChannel()

        // Watch transcription provider and API keys to initialize appropriate service
        appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).onEach { provider ->
            when (provider) {
                TranscriptionProvider.GOOGLE -> {
                    val apiKey = appSettingsRepository.getFlow(Settings.API_KEY).first()
                    if (apiKey.isNotBlank()) {
                        speechToTextService = SpeechToTextService(context, apiKey)
                        logManager.addDebugLog("Google Speech service initialized")
                    } else {
                        speechToTextService = null
                        logManager.addDebugLog("Google Speech service cleared: no API key")
                    }
                    groqSpeechService = null
                }
                TranscriptionProvider.GROQ -> {
                    val apiKey = appSettingsRepository.getFlow(Settings.GROQ_API_KEY).first()
                    if (apiKey.isNotBlank()) {
                        groqSpeechService = GroqSpeechService(context, apiKey)
                        logManager.addDebugLog("Groq Speech service initialized")
                    } else {
                        groqSpeechService = null
                        logManager.addDebugLog("Groq Speech service cleared: no API key")
                    }
                    speechToTextService = null
                }
            }
        }.launchIn(scope)

        // Also watch individual API key changes
        appSettingsRepository.getFlow(Settings.API_KEY)
            .onEach { apiKey ->
                val provider = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).first()
                if (provider == TranscriptionProvider.GOOGLE) {
                    if (apiKey.isNotBlank()) {
                        speechToTextService = SpeechToTextService(context, apiKey)
                        logManager.addDebugLog("Google Speech service initialized")
                    } else {
                        speechToTextService = null
                        logManager.addDebugLog("Speech service cleared: no API key")
                    }
                }
            }
            .launchIn(scope)

        appSettingsRepository.getFlow(Settings.GROQ_API_KEY)
            .onEach { apiKey ->
                val provider = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).first()
                if (provider == TranscriptionProvider.GROQ) {
                    if (apiKey.isNotBlank()) {
                        groqSpeechService = GroqSpeechService(context, apiKey)
                        logManager.addDebugLog("Groq Speech service initialized")
                    } else {
                        groqSpeechService = null
                        logManager.addDebugLog("Groq Speech service cleared: no API key")
                    }
                }
            }
            .launchIn(scope)

        // Initialize Gemini service for AI button feature
        appSettingsRepository.getFlow(Settings.GEMINI_API_KEY)
            .combine(appSettingsRepository.getFlow(Settings.GEMINI_MODEL)) { apiKey, model ->
                Pair(apiKey, model)
            }
            .onEach { (apiKey, model) ->
                if (apiKey.isNotBlank()) {
                    geminiService = GeminiService(context, apiKey, model.modelName)
                    logManager.addDebugLog("Gemini service initialized with model: ${model.modelName}")
                } else {
                    geminiService = null
                    logManager.addDebugLog("Gemini service cleared: no API key")
                }
            }
            .launchIn(scope)

        // Initialize Groq LLM service for AI button feature
        appSettingsRepository.getFlow(Settings.AI_PROVIDER)
            .combine(appSettingsRepository.getFlow(Settings.GROQ_API_KEY)) { provider, groqApiKey ->
                Pair(provider, groqApiKey)
            }
            .onEach { (provider, groqApiKey) ->
                if (provider == AIProvider.GROQ && groqApiKey.isNotBlank()) {
                    groqLLMService = GroqLLMService(context, groqApiKey)
                    logManager.addDebugLog("Groq LLM service initialized")
                } else {
                    groqLLMService = null
                    logManager.addDebugLog("Groq LLM service cleared")
                }
            }
            .launchIn(scope)

        audioDirNameFlow
            .onEach {
                logManager.addDebugLog("Audio directory changed")
                updateLocalAudioFileCount()
            }
            .launchIn(scope)
    }

    private fun createTranscriptionNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRANSCRIPTION_CHANNEL_ID,
                "文字起こし通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "文字起こし完了時の通知"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun sendTranscriptionNotification(transcriptionText: String) {
        val isEnabled = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_NOTIFICATION_ENABLED).first()
        if (!isEnabled) {
            return
        }

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            action = "SHOW_UI"
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TRANSCRIPTION_CHANNEL_ID)
            .setContentTitle(transcriptionText.take(50))
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationIdCounter++, notification)
        logManager.addDebugLog("Notification sent")
    }

    fun updateLocalAudioFileCount() {
        scope.launch {
            val audioDirName = audioDirNameFlow.value
            val audioDir = context.getExternalFilesDir(audioDirName)
            if (audioDir != null && audioDir.exists()) {
                val count = audioDir.listFiles { _, name ->
                    name.matches(Regex("""R\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2}\.wav"""))
                }?.size ?: 0
                _audioFileCount.value = count
                logManager.addDebugLog("Audio file count: $count")
            } else {
                _audioFileCount.value = 0
                logManager.addDebugLog("Audio directory not found")
            }
        }
    }

    /**
     * Mutex for transcription processing
     *
     * USAGE RULES:
     * - Protects sequential transcription processing
     * - Prevents concurrent transcription operations
     * - Independent from BLE operations (no deadlock risk with bleMutex)
     * - Only used in processPendingTranscriptions()
     */
    private val transcriptionMutex = Mutex()

    suspend fun doTranscription(resultToProcess: TranscriptionResult) {
        logManager.addDebugLog("Starting transcription: ${resultToProcess.fileName}")
        val filePath = FileUtil.getAudioFile(context, audioDirNameFlow.value, resultToProcess.fileName).absolutePath
        _transcriptionState.value = "Transcribing ${File(filePath).name}"

        // Detect recording type from filename (AI*.wav vs R*.wav)
        val recordingType = when {
            resultToProcess.fileName.startsWith("AI") && resultToProcess.fileName.length > 2 && resultToProcess.fileName[2].isDigit() -> "AI"
            else -> "NORMAL"
        }
        logManager.addDebugLog("Recording type detected: $recordingType")

        // Get current transcription provider
        val provider = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_PROVIDER).first()
        val currentService = when (provider) {
            TranscriptionProvider.GOOGLE -> speechToTextService
            TranscriptionProvider.GROQ -> groqSpeechService
        }
        val providerName = when (provider) {
            TranscriptionProvider.GOOGLE -> "Google"
            TranscriptionProvider.GROQ -> "Groq"
        }

        val locationData = currentForegroundLocationFlow.value ?: run {
            logManager.addDebugLog("Foreground location not available, using low-power")
            locationTracker.getLowPowerLocation().getOrNull()
        }
        if (locationData != null) {
            logManager.addDebugLog("Location: Lat=${locationData.latitude}, Lng=${locationData.longitude}")
        }

        if (currentService == null) {
            val errorMessage = "APIキーが設定されていません。設定画面で入力してください。"
            _transcriptionState.value = "Error: $errorMessage"
            logManager.addLog("Transcription failed: API key not set", LogLevel.ERROR)
            val errorResult = resultToProcess.copy(
                transcription = "文字起こしエラー: $errorMessage",
                locationData = locationData ?: resultToProcess.locationData,
                transcriptionStatus = "FAILED",
                recordingType = recordingType
            )
            transcriptionResultRepository.addResult(errorResult)
            return
        }

        logManager.addDebugLog("Calling $providerName speech service...")
        val result = when (provider) {
            TranscriptionProvider.GOOGLE -> (currentService as SpeechToTextService).transcribeFile(filePath)
            TranscriptionProvider.GROQ -> (currentService as GroqSpeechService).transcribeFile(filePath)
        }
        logManager.addDebugLog("$providerName speech service completed")

        result.onSuccess { fullTranscription ->
            val googleTaskTitleLength = googleTaskTitleLengthFlow.first()

            val rawTitle = fullTranscription.take(googleTaskTitleLength)
            val cleanTitle = rawTitle.replace("\n", "")
            val title = if (cleanTitle.isBlank()) "Transcription" else cleanTitle

            // For AI mode, process with selected AI provider
            var aiResponse: String? = null
            var finalNotes: String? = null
            var notificationText = fullTranscription  // Default notification text

            if (recordingType == "AI") {
                val aiProvider = appSettingsRepository.getFlow(Settings.AI_PROVIDER).first()
                logManager.addDebugLog("AI mode detected, calling $aiProvider service...")

                val aiResult = when (aiProvider) {
                    AIProvider.GEMINI -> geminiService?.generateResponse(fullTranscription)
                    AIProvider.GROQ -> groqLLMService?.generateResponse(fullTranscription)
                }

                aiResult?.onSuccess { response ->
                    aiResponse = response
                    finalNotes = response  // AI response goes to notes
                    notificationText = response  // Show AI response in notification
                    logManager.addDebugLog("$aiProvider response received: ${response.take(50)}...")
                }?.onFailure { error ->
                    logManager.addLog("$aiProvider API failed: ${error.message}", LogLevel.ERROR)
                    // AI失敗を明示的に記録
                    aiResponse = null
                    val errorMessage = "AI応答の生成に失敗しました: ${error.message}"
                    finalNotes = "$errorMessage\n\n【文字起こし】\n$fullTranscription"
                    notificationText = "⚠️ AI応答の生成に失敗しました\n$title"
                }
            } else {
                // Normal mode: use transcription as notes if overflow
                finalNotes = if (fullTranscription.length > googleTaskTitleLength) {
                    fullTranscription
                } else {
                    null
                }
            }

            _transcriptionResult.value = title
            logManager.addLog("Transcribed: '$title' (type: $recordingType)")
            val newResult = resultToProcess.copy(
                transcription = title,
                googleTaskNotes = finalNotes,
                recordingType = recordingType,
                aiResponse = aiResponse,
                locationData = locationData ?: resultToProcess.locationData,
                transcriptionStatus = "COMPLETED"
            )
            transcriptionResultRepository.addResult(newResult)
            // Send notification (AI response or transcription)
            sendTranscriptionNotification(notificationText)
            // Immediately sync with Google Tasks after successful transcription
            googleTasksIntegration.syncTranscriptionResultsWithGoogleTasks(audioDirNameFlow.value)
            // Notify that processing for this file is complete
            scope.launch { _transcriptionCompletedFlow.emit(resultToProcess.fileName) }
        }.onFailure { error ->
            val errorMessage = error.message ?: "不明なエラー"
            val displayMessage = if (errorMessage.contains("API key authentication failed") || errorMessage.contains("API key is not set")) {
                "文字起こしエラー: APIキーに問題がある可能性があります。設定画面をご確認ください。詳細: $errorMessage"
            } else {
                "文字起こしエラー: $errorMessage"
            }
            _transcriptionState.value = "Error: $displayMessage"
            _transcriptionResult.value = null
            logManager.addLog("Transcription failed: $errorMessage", LogLevel.ERROR)
            val errorResult = resultToProcess.copy(
                transcription = displayMessage,
                locationData = locationData ?: resultToProcess.locationData,
                transcriptionStatus = "FAILED",
                recordingType = recordingType
            )
            transcriptionResultRepository.addResult(errorResult)
            // Immediately sync with Google Tasks after failure to create a task indicating the error
            googleTasksIntegration.syncTranscriptionResultsWithGoogleTasks(audioDirNameFlow.value)
            // Notify that processing for this file is complete (even on failure)
            scope.launch { _transcriptionCompletedFlow.emit(resultToProcess.fileName) }
        }
    }

    fun processPendingTranscriptions() {
        scope.launch {
            if (!transcriptionMutex.tryLock()) {
                logManager.addDebugLog("Transcription already in progress")
                return@launch
            }
            try {
                while(true) {
                    // Short delay to allow multiple files to queue up
                    delay(100)

                    // First, check in-memory queue (instant, no Flow propagation delay)
                    val queuedResult: TranscriptionResult? = queueMutex.withLock {
                        if (pendingQueue.isNotEmpty()) {
                            val result = pendingQueue.removeAt(0)
                            logManager.addDebugLog("Queue: ${result.fileName} (${pendingQueue.size} remaining)")
                            result
                        } else {
                            null
                        }
                    }

                    if (queuedResult != null) {
                        logManager.addDebugLog("Processing from queue: ${queuedResult.fileName}")
                        doTranscription(queuedResult)
                        continue // Immediately check for next item
                    }

                    // If queue is empty, check DataStore Flow (for items added before app restart or from other sources)
                    val resultFromDataStore = transcriptionResultRepository.transcriptionResultsFlow.first()
                        .filter { it.transcriptionStatus == "PENDING" }
                        .minByOrNull { com.pirorin215.fastrecmob.data.FileUtil.getTimestampFromFileName(it.fileName) ?: Long.MAX_VALUE }

                    if (resultFromDataStore != null) {
                        logManager.addDebugLog("Processing from DataStore: ${resultFromDataStore.fileName}")
                        doTranscription(resultFromDataStore)
                        continue
                    }

                    // Both queue and DataStore are empty
                    logManager.addDebugLog("No more pending transcriptions")
                    break
                }
                logManager.addDebugLog("All transcriptions processed")
                _transcriptionState.value = "Idle"
            } finally {
                transcriptionMutex.unlock()
                cleanupTranscriptionResultsAndAudioFiles()
            }
        }
    }

    suspend fun cleanupTranscriptionResultsAndAudioFiles() = withContext(Dispatchers.IO) {
        try {
            logManager.addDebugLog("Running cleanup...")
            val limit = transcriptionCacheLimitFlow.value
            // We need to fetch the current list from repository
            val currentTranscriptionResults = transcriptionResultRepository.transcriptionResultsFlow.first()
                .filter { !it.isDeletedLocally } // Assuming we only count non-deleted ones for limit? Original code did this.
                .sortedBy { it.lastEditedTimestamp } // Oldest first

            if (currentTranscriptionResults.size > limit) {
                val resultsToDelete = currentTranscriptionResults.take(currentTranscriptionResults.size - limit)
                logManager.addLog("Cleanup: deleting ${resultsToDelete.size} old results (limit: $limit)")

                resultsToDelete.forEach { result ->
                    // Delete from DataStore
                    transcriptionResultRepository.removeResult(result)
                    logManager.addDebugLog("Deleted result: ${result.fileName}")

                    // Delete associated audio file
                    val audioDirName = audioDirNameFlow.value
                    val audioFile = FileUtil.getAudioFile(context, audioDirName, result.fileName)
                    if (audioFile.exists()) {
                        if (audioFile.delete()) {
                            logManager.addDebugLog("Deleted audio: ${result.fileName}")
                        } else {
                            logManager.addLog("Failed to delete audio: ${result.fileName}", LogLevel.ERROR)
                        }
                    }
                }
                logManager.addDebugLog("Cleanup complete")
            } else {
                logManager.addDebugLog("Cache within limit ($limit)")
            }
        } catch (e: Exception) {
            logManager.addLog("Cleanup error: ${e.message}", LogLevel.ERROR)
        } finally {
            updateLocalAudioFileCount() // Ensure count is updated after cleanup
        }
    }

    fun retranscribe(result: TranscriptionResult) {
        scope.launch {
            logManager.addLog("Retranscribing: ${result.fileName}")

            val audioDirName = audioDirNameFlow.value
            val audioFile = FileUtil.getAudioFile(context, audioDirName, result.fileName)
            if (!audioFile.exists()) {
                logManager.addLog("Retranscribe failed: file not found", LogLevel.ERROR)
                // Optionally update status to FAILED here if desired
                val updatedResult = result.copy(transcriptionStatus = "FAILED", transcription = "Audio file not found.")
                transcriptionResultRepository.addResult(updatedResult)
                return@launch
            }

            logManager.addDebugLog("Marking as PENDING: ${result.fileName}")
            val pendingResult = result.copy(
                transcriptionStatus = "PENDING",
                googleTaskId = null  // Clear to force new task creation on retranscription
            )
            transcriptionResultRepository.addResult(pendingResult)

            // Immediately trigger processing
            processPendingTranscriptions()
        }
    }

    suspend fun addPendingTranscription(fileName: String) {
        logManager.addDebugLog("Creating transcription record: $fileName")
        val existing = transcriptionResultRepository.transcriptionResultsFlow.first().find { it.fileName == fileName }
        if (existing != null) {
            logManager.addDebugLog("Record exists: $fileName (${existing.transcriptionStatus})")
            // If the record is already completed or failed, it means we likely just need to trigger the post-transcription logic (like file deletion).
            // This can happen if the app crashed or was stopped after transcription but before deletion.
            if (existing.transcriptionStatus == "COMPLETED" || existing.transcriptionStatus == "FAILED") {
                logManager.addDebugLog("Emitting completion for processed file")
                scope.launch { _transcriptionCompletedFlow.emit(fileName) }
            }
            // If status is PENDING, we do nothing and let the normal processing flow handle it.
            return
        }

        logManager.addDebugLog("New transcription record: $fileName")

        // Detect recording type from filename
        val recordingType = when {
            fileName.startsWith("AI") && fileName.length > 2 && fileName[2].isDigit() -> "AI"
            else -> "NORMAL"
        }
        logManager.addDebugLog("Recording type: $recordingType")

        val newResult = TranscriptionResult(
            fileName = fileName,
            transcription = "", // Empty transcription initially
            locationData = null, // Location will be fetched during transcription
            transcriptionStatus = "PENDING",
            recordingType = recordingType
        )

        // Add to DataStore (for persistence across app restarts)
        transcriptionResultRepository.addResult(newResult)

        // Add to in-memory queue immediately (for fast processing without waiting for Flow propagation)
        queueMutex.withLock {
            pendingQueue.add(newResult)
            logManager.addDebugLog("Queued: $fileName (size: ${pendingQueue.size})")
        }

        logManager.addDebugLog("Pending transcription saved: $fileName")
    }

    fun addManualTranscription(text: String) {
        scope.launch {
            var locationData: LocationData? = currentForegroundLocationFlow.value
            logManager.addDebugLog("Manual transcription: ${if (locationData != null) "with location" else "no location"}")

            val timestamp = System.currentTimeMillis()
            val manualFileName = "M${FileUtil.formatTimestampForFileName(timestamp)}.txt"

            val googleTaskTitleLength = googleTaskTitleLengthFlow.first()
            val rawTitle = text.take(googleTaskTitleLength)
            val cleanTitle = rawTitle.replace("\n", "")
            val title = if (cleanTitle.isBlank()) "Manual Transcription" else cleanTitle

            val notes = if (text.length > googleTaskTitleLength) {
                text
            } else {
                null
            }

            val newResult = TranscriptionResult(
                fileName = manualFileName,
                transcription = title, // Save the title as the main transcription
                locationData = locationData,
                googleTaskNotes = notes // Save full transcription as notes if overflow
            )

            transcriptionResultRepository.addResult(newResult)
            logManager.addLog("Manual transcription added")

            scope.launch { cleanupTranscriptionResultsAndAudioFiles() }
        }
    }

    fun clearTranscriptionResults() {
        scope.launch {
            logManager.addLog("Clearing all transcriptions")
            val currentTranscriptionResults = transcriptionResultRepository.transcriptionResultsFlow.first()

            val updatedListForRepo = mutableListOf<TranscriptionResult>()

            // First, delete all associated WAV files
            currentTranscriptionResults.forEach { result ->
                val audioFile = FileUtil.getAudioFile(context, audioDirNameFlow.value, result.fileName)
                if (audioFile.exists()) {
                    if (audioFile.delete()) {
                        logManager.addDebugLog("Deleted: ${result.fileName}")
                    } else {
                        logManager.addLog("Failed to delete: ${result.fileName}", LogLevel.ERROR)
                    }
                }

                if (result.googleTaskId == null) {
                    // Local-only item: permanently delete now (from DataStore)
                    transcriptionResultRepository.permanentlyRemoveResult(result)
                } else {
                    // Synced item: soft delete (mark for remote deletion during next sync)
                    updatedListForRepo.add(result.copy(isDeletedLocally = true))
                }
            }
            transcriptionResultRepository.updateResults(updatedListForRepo) // Only updates for synced items

            logManager.addLog("All transcriptions cleared")
            updateLocalAudioFileCount() // Update count after deletions
        }
    }

    fun removeTranscriptionResult(result: TranscriptionResult) {
        scope.launch {
            if (result.googleTaskId != null) {
                // If synced with Google Tasks, perform a soft delete locally.
                // Actual deletion from Google Tasks and permanent local deletion will happen during sync.
                transcriptionResultRepository.removeResult(result) // This now sets isDeletedLocally = true
                logManager.addDebugLog("Soft-deleted: ${result.fileName}")
            } else {
                // If not synced with Google Tasks, permanently delete locally.
                transcriptionResultRepository.permanentlyRemoveResult(result)
                logManager.addLog("Deleted: ${result.fileName}")
            }

            val audioFile = FileUtil.getAudioFile(context, audioDirNameFlow.value, result.fileName)
            if (audioFile.exists()) {
                if (audioFile.delete()) {
                    logManager.addDebugLog("Audio deleted: ${result.fileName}")
                    updateLocalAudioFileCount()
                } else {
                    logManager.addLog("Failed to delete audio: ${result.fileName}", LogLevel.ERROR)
                }
            }
        }
    }

    fun updateTranscriptionResult(originalResult: TranscriptionResult, newTranscription: String, newNote: String?) {
        scope.launch {
            // Create a new TranscriptionResult with the updated transcription and notes
            val updatedResult = originalResult.copy(
                transcription = newTranscription,
                googleTaskNotes = newNote, // Update googleTaskNotes
                lastEditedTimestamp = System.currentTimeMillis()
            )
            // The addResult method now handles updates, so we just call that.
            transcriptionResultRepository.addResult(updatedResult)
            logManager.addLog("Updated: ${originalResult.fileName}")
        }
    }

    fun removeTranscriptionResults(fileNames: Set<String>, clearSelectionCallback: () -> Unit) {
        scope.launch {
            // We need to fetch the current results to filter
            val currentResults = transcriptionResultRepository.transcriptionResultsFlow.first()
            val resultsToRemove = currentResults.filter { fileNames.contains(it.fileName) }
            resultsToRemove.forEach { result ->
                removeTranscriptionResult(result) // Use the existing single delete function
            }
            clearSelectionCallback() // Clear selection after deletion
            logManager.addLog("Removed ${resultsToRemove.size} transcriptions")
        }
    }




    fun resetTranscriptionState() {
        _transcriptionState.value = "Idle"
        _transcriptionResult.value = null
    }
    
    fun onCleared() {
        // Placeholder for future cleanup if needed
    }
}