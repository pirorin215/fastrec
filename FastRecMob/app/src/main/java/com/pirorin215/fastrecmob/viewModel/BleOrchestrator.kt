package com.pirorin215.fastrecmob.viewModel

import android.content.Context
import android.util.Log
import com.pirorin215.fastrecmob.data.BleRepository
import com.pirorin215.fastrecmob.data.ConnectionState
import com.pirorin215.fastrecmob.data.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import android.bluetooth.BluetoothGattCharacteristic
import com.pirorin215.fastrecmob.viewModel.LocationMonitor
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.Settings
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import com.pirorin215.fastrecmob.data.DeviceSettings
import com.pirorin215.fastrecmob.data.DeviceInfoResponse
import com.pirorin215.fastrecmob.data.FileEntry

import com.pirorin215.fastrecmob.viewModel.LogManager
import com.pirorin215.fastrecmob.LocationTracker
import com.pirorin215.fastrecmob.data.DeviceHistoryRepository

class BleOrchestrator(
    private val scope: CoroutineScope,
    private val context: Context,
    private val repository: BleRepository,
    private val connectionStateFlow: StateFlow<ConnectionState>,
    private val onDeviceReadyEvent: SharedFlow<Unit>,
    private val transcriptionManager: TranscriptionManager,
    private val locationMonitor: LocationMonitor,
    private val appSettingsRepository: AppSettingsRepository,
    private val bleSelectionManager: BleSelectionManager,
    private val transcriptionResults: StateFlow<List<TranscriptionResult>>,
    private val logManager: LogManager,
    private val disconnectSignal: SharedFlow<Unit>,
    private val locationTracker: LocationTracker,
    private val deviceHistoryRepository: DeviceHistoryRepository
) {
    companion object {
        const val TAG = "BleOrchestrator"

    }

    internal val _currentOperation = MutableStateFlow(BleOperation.IDLE)
    val currentOperation = _currentOperation.asStateFlow()

    /**
     * Primary mutex for all BLE operations
     *
     * USAGE RULES:
     * - Protects all BLE communication (commands, file transfers, settings)
     * - Shared with BleDeviceCommandManager and FileTransferManager
     * - Always use withLock { } to ensure proper release
     * - NEVER nest with other mutexes to avoid deadlocks
     *
     * DEADLOCK PREVENTION:
     * - This is the ONLY mutex for BLE operations
     * - TranscriptionManager uses separate mutexes (independent from BLE)
     * - File processing uses AtomicBoolean (isProcessingFiles) instead of mutex
     */
    internal val bleMutex = Mutex()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    internal val bleDeviceCommandManager by lazy {
        BleDeviceCommandManager(
            scope = scope,
            context = context,
            sendCommand = { command -> sendCommand(command) },
            logManager = logManager,
            _currentOperation = _currentOperation,
            bleMutex = bleMutex,
            onFileListUpdated = { checkForNewWavFilesAndProcess() },
            _navigationEvent = _navigationEvent,
            locationTracker = locationTracker,
            deviceHistoryRepository = deviceHistoryRepository,
            appSettingsRepository = appSettingsRepository
        )
    }

    // --- Delegated Properties from Managers ---
    val fileList: StateFlow<List<com.pirorin215.fastrecmob.data.FileEntry>> get() = bleDeviceCommandManager.fileList
    val deviceInfo: StateFlow<com.pirorin215.fastrecmob.data.DeviceInfoResponse?> get() = bleDeviceCommandManager.deviceInfo
    val deviceSettings: StateFlow<com.pirorin215.fastrecmob.data.DeviceSettings> get() = bleDeviceCommandManager.deviceSettings

    private val fileTransferManager by lazy {
        FileTransferManager(
            context = context,
            scope = scope,
            repository = repository,
            transcriptionManager = transcriptionManager,
            audioDirNameFlow = appSettingsRepository.getFlow(Settings.AUDIO_DIR_NAME)
                .stateIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = "FastRecRecordings"
                ),
            bleMutex = bleMutex,
            logManager = logManager,
            sendCommandCallback = { command -> sendCommand(command) },
            sendAckCallback = { ackValue -> sendAck(ackValue) },
            _currentOperation = _currentOperation,
            bleDeviceCommandManager = bleDeviceCommandManager,
            _connectionState = connectionStateFlow,
            disconnectSignal = disconnectSignal,
            appSettingsRepository = appSettingsRepository
        )
    }

    val downloadProgress: StateFlow<Int> get() = fileTransferManager.downloadProgress
    val currentFileTotalSize: StateFlow<Long> get() = fileTransferManager.currentFileTotalSize
    val fileTransferState: StateFlow<String> get() = fileTransferManager.fileTransferState
    val transferKbps: StateFlow<Float> get() = fileTransferManager.transferKbps

    // AtomicBoolean to prevent concurrent file processing
    // This is safer than Mutex as it avoids potential deadlock with bleMutex
    private val isProcessingFiles = AtomicBoolean(false)

    // Retry job for file list fetch when device is busy (recording)
    private var fileListRetryJob: Job? = null

    init {
        // Monitor disconnect signal to cancel ongoing retry
        disconnectSignal
            .onEach {
                addDebugLog("BLE disconnected, canceling file list retry")
                fileListRetryJob?.cancel()
                fileListRetryJob = null
            }
            .launchIn(scope)

        onDeviceReadyEvent
            .onEach {
                addLog("Starting initial sync")
                startFullSync()
            }
            .launchIn(scope)

        repository.events.onEach { event ->
            when(event) {
                is com.pirorin215.fastrecmob.data.BleEvent.CharacteristicChanged -> {
                    handleCharacteristicChanged(event.characteristic, event.value)
                }
                else -> {}
            }
        }.launchIn(scope)
    }
    
    fun stop() {
        bleDeviceCommandManager.stopTimeSyncJob()
        fileListRetryJob?.cancel()
        fileListRetryJob = null
        addLog("Orchestrator stopped.")
    }

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        logManager.addLog(message, level)
    }

    fun addDebugLog(message: String) {
        logManager.addDebugLog(message)
    }

    fun clearLogs() {
        logManager.clearLogs()
        logManager.addLog("Logs cleared")
    }

    private fun startFullSync() {
        scope.launch {
            addLog("Fetching file list")
            val success = bleDeviceCommandManager.fetchFileList(connectionStateFlow.value)

            if (!success) {
                // Device may be busy (recording), start retry with short intervals
                addLog("File list fetch failed, starting retry (device may be recording)")
                startFileListRetry()
            }
        }
    }

    /**
     * Retry file list fetch with short intervals when device is busy (recording).
     * Microcontroller enters deep sleep after inactivity, so we retry frequently
     * during the short window after recording ends, before deep sleep.
     * Stops retrying if BLE disconnects (deep sleep entered).
     */
    private fun startFileListRetry() {
        // Cancel any existing retry job
        fileListRetryJob?.cancel()

        fileListRetryJob = scope.launch {
            var retryCount = 0
            val maxRetries = 6 // 30 seconds total (5s * 6)
            val retryDelay = 5000L // 5 seconds

            while (retryCount < maxRetries) {
                // Check if still connected (device may have entered deep sleep)
                if (connectionStateFlow.value !is ConnectionState.Connected) {
                    addLog("BLE disconnected during retry, stopping")
                    break
                }

                delay(retryDelay)
                retryCount++
                addDebugLog("Retrying file list fetch ($retryCount/$maxRetries)")

                val success = bleDeviceCommandManager.fetchFileList(connectionStateFlow.value)
                if (success) {
                    addLog("File list fetch succeeded on retry $retryCount")
                    break
                }
            }

            if (retryCount >= maxRetries && connectionStateFlow.value is ConnectionState.Connected) {
                addLog("File list fetch failed after $maxRetries retries", LogLevel.ERROR)
            }

            fileListRetryJob = null
        }
    }

    private fun performPostTransferSync() {
        scope.launch {
            addDebugLog("Post-transfer sync...")

            // Re-fetch the file list to ensure the UI is up-to-date, but without triggering a new processing loop.
            bleDeviceCommandManager.fetchFileList(connectionStateFlow.value, extension = "wav", triggerCallback = false)

            // Step 2: Time Sync
            val timeSyncSuccess = bleDeviceCommandManager.syncTime(connectionStateFlow.value)
            if (!timeSyncSuccess) {
                addLog("Time sync failed", LogLevel.ERROR)
                return@launch
            }

            // Step 3: Fetch Device Info
            val voltageRetryCount = appSettingsRepository.getFlow(Settings.VOLTAGE_RETRY_COUNT).first()
            val deviceInfoSuccess = bleDeviceCommandManager.fetchDeviceInfo(connectionStateFlow.value, voltageRetryCount)
            if (!deviceInfoSuccess) {
                addLog("Device info fetch failed", LogLevel.ERROR)
                return@launch
            }

            // Step 4: Update location (since GET:info was successful)
            addDebugLog("Updating location")
            locationMonitor.updateLocation()
        }
    }

    private fun checkForNewWavFilesAndProcess() {
        scope.launch {
            // Use AtomicBoolean to prevent concurrent execution
            // This avoids deadlock risk with bleMutex used by internal operations
            if (!isProcessingFiles.compareAndSet(false, true)) {
                addDebugLog("Processing already in progress")
                return@launch
            }
            try {
                bleDeviceCommandManager.stopTimeSyncJob()

                val currentWavFilesOnMicrocontroller = bleDeviceCommandManager.fileList.value.filter { it.name.endsWith(".wav", ignoreCase = true) }
                val transcribedFileNames = this@BleOrchestrator.transcriptionResults.value.map { it.fileName }.toSet()

                // 未文字起こしファイル（ダウンロード対象）
                val filesToDownload = currentWavFilesOnMicrocontroller.filter { fileEntry ->
                    !transcribedFileNames.contains(fileEntry.name)
                }

                // 文字起こし済みだがマイコンに残っているファイル（削除対象）
                // 前回削除に失敗した可能性があるため、再度削除を試行する
                val filesToDeleteOnly = currentWavFilesOnMicrocontroller.filter { fileEntry ->
                    transcribedFileNames.contains(fileEntry.name)
                }

                if (filesToDeleteOnly.isNotEmpty()) {
                    addLog("Deleting ${filesToDeleteOnly.size} transcribed files from device")
                    for (fileEntry in filesToDeleteOnly) {
                        addDebugLog("Deleting: ${fileEntry.name}")
                        fileTransferManager.deleteFileAndUpdateList(fileEntry.name)
                    }
                }

                if (filesToDownload.isEmpty()) {
                    addDebugLog("No new files to download")
                    performPostTransferSync()
                    return@launch
                }

                addLog("Processing ${filesToDownload.size} new WAV files")

                for (fileEntry in filesToDownload) {
                    addDebugLog("Processing: ${fileEntry.name}")

                    val downloadSuccess = fileTransferManager.downloadFile(fileEntry.name)

                    if (downloadSuccess) {
                        addDebugLog("Download OK, deleting from device")
                        fileTransferManager.deleteFileAndUpdateList(fileEntry.name)
                    } else {
                        addLog("Download failed: ${fileEntry.name}", LogLevel.ERROR)
                    }
                }

                addDebugLog("File processing complete")
                performPostTransferSync()

            } finally {
                isProcessingFiles.set(false)
            }
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != UUID.fromString(BleRepository.RESPONSE_UUID_STRING)) return

        when (_currentOperation.value) {
            BleOperation.FETCHING_FILE_LIST, BleOperation.FETCHING_DEVICE_INFO, BleOperation.SENDING_TIME, BleOperation.FETCHING_SETTINGS, BleOperation.SENDING_SETTINGS -> {
                bleDeviceCommandManager.handleResponse(value, _currentOperation.value)
            }
            BleOperation.DOWNLOADING_FILE, BleOperation.DELETING_FILE -> {
                fileTransferManager.handleCharacteristicChanged(characteristic, value)
            }
            else -> {
                addLog("Received data in unexpected state (${_currentOperation.value}): ${value.toString(Charsets.UTF_8)}")
            }
        }
    }

    fun sendCommand(command: String) {
        addLog("Sending command: $command")
        repository.sendCommand(command)
    }

    private fun sendAck(ackValue: ByteArray) {
        repository.sendAck(ackValue)
    }

    fun fetchFileList(extension: String) {
        scope.launch {
            bleDeviceCommandManager.fetchFileList(connectionStateFlow.value, extension)
        }
    }

    suspend fun getSettings() {
        bleDeviceCommandManager.getSettings(connectionStateFlow.value)
    }

    fun sendSettings() {
        bleDeviceCommandManager.sendSettings(connectionStateFlow.value)
    }

    fun updateSettings(updater: (com.pirorin215.fastrecmob.data.DeviceSettings) -> com.pirorin215.fastrecmob.data.DeviceSettings) {
        bleDeviceCommandManager.updateSettings(updater)
    }

    fun downloadFile(fileName: String) {
        scope.launch {
            fileTransferManager.downloadFile(fileName)
        }
    }

    fun toggleSelection(fileName: String) {
        bleSelectionManager.toggleSelection(fileName)
    }

    fun clearSelection() {
        bleSelectionManager.clearSelection()
    }
}