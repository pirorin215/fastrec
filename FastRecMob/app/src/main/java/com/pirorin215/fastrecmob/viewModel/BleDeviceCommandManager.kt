package com.pirorin215.fastrecmob.viewModel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pirorin215.fastrecmob.MainActivity
import com.pirorin215.fastrecmob.R
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.DeviceInfoResponse
import com.pirorin215.fastrecmob.data.FileEntry
import com.pirorin215.fastrecmob.data.parseFileEntries
import com.pirorin215.fastrecmob.data.DeviceSettings
import com.pirorin215.fastrecmob.data.Settings
import com.pirorin215.fastrecmob.LocationTracker
import com.pirorin215.fastrecmob.data.DeviceHistoryEntry
import com.pirorin215.fastrecmob.data.DeviceHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.UUID // Only if needed for handling specific UUIDs directly

class BleDeviceCommandManager(
    private val scope: CoroutineScope,
    private val context: Context, // Potentially not needed if BleRepository handles context
    private val sendCommand: (String) -> Unit,
    private val logManager: LogManager,
    private val _currentOperation: MutableStateFlow<BleOperation>,
    private val bleMutex: Mutex,
    private val onFileListUpdated: () -> Unit, // Callback from BleDeviceManager
    private val _navigationEvent: MutableSharedFlow<NavigationEvent>, // From BleSettingsManager
    private val locationTracker: LocationTracker,
    private val deviceHistoryRepository: DeviceHistoryRepository,
    private val appSettingsRepository: AppSettingsRepository
) {
    // --- Properties from BleDeviceManager ---
    private val _deviceInfo = MutableStateFlow<DeviceInfoResponse?>(null)
    val deviceInfo = _deviceInfo.asStateFlow()

    private val _fileList = MutableStateFlow<List<FileEntry>>(emptyList())
    val fileList = _fileList.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val responseBuffer = StringBuilder()
    private var currentDeviceCommandCompletion: CompletableDeferred<Pair<Boolean, String?>>? = null
    private var timeSyncJob: Job? = null

    companion object {
        const val TIME_SYNC_INTERVAL_MS = 300000L // 5 minutes
        const val LOW_VOLTAGE_CHANNEL_ID = "LowVoltageChannel"
        const val LOW_VOLTAGE_NOTIFICATION_ID = 2001
    }

    // 低電圧通知済みフラグ（初回のみ通知の場合に使用）
    private var hasNotifiedLowVoltage = false

    init {
        createLowVoltageNotificationChannel()
    }

    private fun createLowVoltageNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOW_VOLTAGE_CHANNEL_ID,
                "低電圧警告",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "バッテリー電圧が低下した際の警告通知"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private suspend fun checkAndNotifyLowVoltage(voltage: Float) {
        val threshold = appSettingsRepository.getFlow(Settings.LOW_VOLTAGE_THRESHOLD).first()
        val notifyEveryTime = appSettingsRepository.getFlow(Settings.LOW_VOLTAGE_NOTIFY_EVERY_TIME).first()

        // しきい値が0の場合は通知OFF
        if (threshold <= 0f) {
            return
        }

        // 電圧がしきい値以上なら通知フラグをリセット
        if (voltage >= threshold) {
            hasNotifiedLowVoltage = false
            return
        }

        // 初回のみ通知モードで既に通知済みなら何もしない
        if (!notifyEveryTime && hasNotifiedLowVoltage) {
            logManager.addLog("Low voltage detected (${voltage}V) but already notified (first-only mode).")
            return
        }

        // 通知を発行
        sendLowVoltageNotification(voltage, threshold)
        hasNotifiedLowVoltage = true
        logManager.addLog("Low voltage notification sent: ${voltage}V < ${threshold}V")
    }

    private fun sendLowVoltageNotification(voltage: Float, threshold: Float) {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            action = "SHOW_UI"
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, LOW_VOLTAGE_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.low_voltage_notification_title))
            .setContentText(context.getString(R.string.low_voltage_notification_text, voltage, threshold))
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(LOW_VOLTAGE_NOTIFICATION_ID, notification)
    }

    // --- Properties from BleSettingsManager ---
    private val _deviceSettings = MutableStateFlow(DeviceSettings())
    val deviceSettings = _deviceSettings.asStateFlow()

    private var currentSettingsCommandCompletion: CompletableDeferred<Pair<Boolean, String?>>? = null

    // --- Methods from BleDeviceManager ---
    suspend fun syncTime(connectionState: com.pirorin215.fastrecmob.data.ConnectionState): Boolean {
        if (connectionState !is com.pirorin215.fastrecmob.data.ConnectionState.Connected) {
            logManager.addLog("Cannot sync time, not connected.")
            return false
        }

        return bleMutex.withLock {
            if (_currentOperation.value != BleOperation.IDLE) {
                logManager.addLog("Cannot sync time, busy: ${_currentOperation.value}")
                return@withLock false
            }

            try {
                _currentOperation.value = BleOperation.SENDING_TIME
                responseBuffer.clear()
                val timeCompletion = CompletableDeferred<Pair<Boolean, String?>>()
                currentDeviceCommandCompletion = timeCompletion

                val currentTimestampSec = System.currentTimeMillis() / 1000
                val timeCommand = "SET:time:$currentTimestampSec"
                logManager.addLog("Sending time synchronization command: $timeCommand")
                sendCommand(timeCommand)

                val (timeSyncSuccess, _) = withTimeoutOrNull(5000L) {
                    timeCompletion.await()
                } ?: Pair(false, "Timeout")

                if (timeSyncSuccess) {
                    logManager.addLog("Time synchronization successful.")
                } else {
                    logManager.addLog("Time synchronization failed or timed out.")
                }
                timeSyncSuccess
            } catch (e: Exception) {
                logManager.addLog("Error during time sync: ${e.message}")
                false
            } finally {
                _currentOperation.value = BleOperation.IDLE
                currentDeviceCommandCompletion = null
            }
        }
    }


    fun startTimeSyncJob() {
        timeSyncJob?.cancel()
        timeSyncJob = scope.launch {
            while (true) {
                delay(TIME_SYNC_INTERVAL_MS)
                if (_currentOperation.value == BleOperation.IDLE) {
                    // Using tryLock to avoid waiting if another operation is in progress
                    if (bleMutex.tryLock()) {
                        try {
                            if (_currentOperation.value == BleOperation.IDLE) {
                                val periodicTimestampSec = System.currentTimeMillis() / 1000
                                val periodicTimeCommand = "SET:time:$periodicTimestampSec"
                                logManager.addLog("定期時刻同期コマンド送信 (Best-effort): $periodicTimeCommand")
                                sendCommand(periodicTimeCommand)
                                // 【設計意図】定期時刻同期はBest-effortで動作します
                                // - 応答を待たずにコマンドを送信するだけ（他の操作をブロックしない）
                                // - デバイスが受信するか不明だが、5分後に再試行される
                                // - 確実な同期が必要な場合は syncTime() を明示的に呼び出す
                            }
                        } finally {
                            bleMutex.unlock()
                        }
                    } else {
                        logManager.addLog("定期時刻同期をスキップ: 他の操作が実行中です")
                    }
                }
            }
        }
    }

    fun stopTimeSyncJob() {
        timeSyncJob?.cancel()
        timeSyncJob = null
    }

    suspend fun fetchDeviceInfo(connectionState: com.pirorin215.fastrecmob.data.ConnectionState, retryCount: Int = 3): Boolean {
        if (connectionState !is com.pirorin215.fastrecmob.data.ConnectionState.Connected) {
            logManager.addLog("Cannot fetch device info, not connected.")
            return false
        }

        return bleMutex.withLock {
            if (_currentOperation.value != BleOperation.IDLE) {
                logManager.addLog("Cannot fetch device info, busy: ${_currentOperation.value}")
                return@withLock false
            }

            try {
                _currentOperation.value = BleOperation.FETCHING_DEVICE_INFO
                logManager.addLog("Requesting device info from device (retry count: $retryCount)...")

                val deviceInfoResponses = mutableListOf<DeviceInfoResponse>()

                // 指定回数だけ取得を試行
                repeat(retryCount) { attemptIndex ->
                    responseBuffer.clear()

                    val commandCompletion = CompletableDeferred<Pair<Boolean, String?>>()
                    currentDeviceCommandCompletion = commandCompletion

                    sendCommand("GET:info")

                    val (success, _) = withTimeoutOrNull(15000L) {
                        commandCompletion.await()
                    } ?: Pair(false, "Timeout")

                    if (success && _deviceInfo.value != null) {
                        deviceInfoResponses.add(_deviceInfo.value!!)
                        logManager.addLog("GET:info attempt ${attemptIndex + 1}/$retryCount succeeded (voltage: ${_deviceInfo.value!!.batteryVoltage}V)")
                    } else {
                        logManager.addLog("GET:info attempt ${attemptIndex + 1}/$retryCount failed or timed out.")
                    }

                    // 最後の試行でなければ500msec待機
                    if (attemptIndex < retryCount - 1) {
                        delay(500L)
                    }
                }

                // 応答が1つ以上あれば、最も高い電圧を持つものを選択
                if (deviceInfoResponses.isNotEmpty()) {
                    val bestResponse = deviceInfoResponses.maxByOrNull { it.batteryVoltage }
                    _deviceInfo.value = bestResponse

                    // デバイス履歴に保存
                    val locationResult = locationTracker.getCurrentLocation()
                    val locationData = locationResult.getOrNull()
                    val historyEntry = DeviceHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        latitude = locationData?.latitude,
                        longitude = locationData?.longitude,
                        batteryLevel = bestResponse!!.batteryLevel,
                        batteryVoltage = bestResponse.batteryVoltage
                    )
                    deviceHistoryRepository.addEntry(historyEntry)

                    // 低電圧チェックと通知
                    checkAndNotifyLowVoltage(bestResponse.batteryVoltage)

                    logManager.addLog("Selected best voltage: ${bestResponse.batteryVoltage}V from ${deviceInfoResponses.size} attempts.")
                    true
                } else {
                    logManager.addLog("All GET:info attempts failed.")
                    false
                }
            } catch (e: Exception) {
                logManager.addLog("Error fetchDeviceInfo: ${e.message}")
                false
            } finally {
                _currentOperation.value = BleOperation.IDLE
                currentDeviceCommandCompletion = null
            }
        }
    }

    suspend fun fetchFileList(connectionState: com.pirorin215.fastrecmob.data.ConnectionState, extension: String = "wav", triggerCallback: Boolean = true): Boolean {
        if (connectionState !is com.pirorin215.fastrecmob.data.ConnectionState.Connected) {
            logManager.addLog("Cannot fetch file list, not connected.")
            return false
        }

        return bleMutex.withLock {
            if (_currentOperation.value != BleOperation.IDLE) {
                logManager.addLog("Cannot fetch file list, busy: ${_currentOperation.value}")
                return@withLock false
            }

            try {
                _currentOperation.value = BleOperation.FETCHING_FILE_LIST
                responseBuffer.clear()
                logManager.addLog("Requesting file list (GET:ls:$extension)...")

                val commandCompletion = CompletableDeferred<Pair<Boolean, String?>>()
                currentDeviceCommandCompletion = commandCompletion

                sendCommand("GET:ls:$extension")

                val (success, _) = withTimeoutOrNull(15000L) {
                    commandCompletion.await()
                } ?: Pair(false, "Timeout")

                if (success) {
                    logManager.addLog("GET:ls:$extension completed.")
                    if (extension == "wav" && triggerCallback) {
                        onFileListUpdated()
                    }
                } else {
                    logManager.addLog("GET:ls:$extension failed or timed out.")
                }
                success
            } catch (e: Exception) {
                logManager.addLog("Error fetchFileList: ${e.message}")
                false
            } finally {
                _currentOperation.value = BleOperation.IDLE
                currentDeviceCommandCompletion = null
            }
        }
    }


    fun removeFileFromList(fileName: String, triggerCallback: Boolean = true) {
        _fileList.value = _fileList.value.filterNot { it.name == fileName }
        logManager.addLog("Removed '$fileName' from local file list.")
        if (triggerCallback) {
            onFileListUpdated() // Callback to trigger checking for new files
        }
    }

    // --- Methods from BleSettingsManager ---
    suspend fun getSettings(connectionState: com.pirorin215.fastrecmob.data.ConnectionState): Boolean {
        if (connectionState !is com.pirorin215.fastrecmob.data.ConnectionState.Connected) {
            logManager.addLog("Cannot get settings, not connected.")
            return false
        }

        return bleMutex.withLock {
            if (_currentOperation.value != BleOperation.IDLE) {
                logManager.addLog("Cannot get settings, busy: ${_currentOperation.value}")
                return@withLock false
            }

            try {
                _currentOperation.value = BleOperation.FETCHING_SETTINGS
                responseBuffer.clear()
                logManager.addLog("Requesting settings from device...")

                val commandCompletion = CompletableDeferred<Pair<Boolean, String?>>()
                currentSettingsCommandCompletion = commandCompletion // Use specific settings completion

                sendCommand("GET:setting_ini")

                val (success, _) = withTimeoutOrNull(15000L) { // Timeout for response
                    commandCompletion.await()
                } ?: Pair(false, "Timeout")

                if (success) {
                    logManager.addLog("GET:setting_ini command completed successfully.")
                } else {
                    logManager.addLog("GET:setting_ini command failed or timed out.")
                }
                success
            } catch (e: Exception) {
                logManager.addLog("Error getSettings: ${e.message}")
                false
            } finally {
                _currentOperation.value = BleOperation.IDLE
                currentSettingsCommandCompletion = null // Clear completion object
            }
        }
    }


    fun sendSettings(connectionState: com.pirorin215.fastrecmob.data.ConnectionState) {
        if (_currentOperation.value != BleOperation.IDLE || connectionState !is com.pirorin215.fastrecmob.data.ConnectionState.Connected) {
            logManager.addLog("Cannot send settings, busy or not connected.")
            return
        }
        val settings = _deviceSettings.value
        logManager.addLog("Sending current _deviceSettings: $settings")
        _currentOperation.value = BleOperation.SENDING_SETTINGS
        val iniString = settings.toIniString()
        logManager.addLog("Sending settings to device:\n$iniString")
        sendCommand("SET:setting_ini:$iniString")
        scope.launch {
            // Original BleSettingsManager had a delay here.
            // Consider if this navigation event should be triggered by a response to SET:setting_ini
            // rather than immediately after sending, to confirm success.
            // For now, mirroring original behavior.
            delay(500) // Small delay to allow command to be sent over BLE
            _navigationEvent.emit(NavigationEvent.NavigateBack)
            _currentOperation.value = BleOperation.IDLE // Reset operation after sending
        }
    }

    fun updateSettings(updater: (DeviceSettings) -> DeviceSettings) {
        _deviceSettings.value = updater(_deviceSettings.value)
    }

    // --- Combined handleResponse method ---
    fun handleResponse(value: ByteArray, operation: BleOperation) {
        when (operation) {
            BleOperation.FETCHING_DEVICE_INFO -> {
                val incomingString = value.toString(Charsets.UTF_8).trim()
                if (responseBuffer.isEmpty() && !incomingString.startsWith("{") && !incomingString.startsWith("ERROR:")) {
                    return
                }
                responseBuffer.append(value.toString(Charsets.UTF_8))
                val currentBufferAsString = responseBuffer.toString()

                if (currentBufferAsString.trim().endsWith("}")) {
                    logManager.addLog("Raw DeviceInfo JSON: $currentBufferAsString") // Log raw JSON
                    try {
                        val parsedResponse = json.decodeFromString<DeviceInfoResponse>(currentBufferAsString)
                        _deviceInfo.value = parsedResponse
                        currentDeviceCommandCompletion?.complete(Pair(true, null))

                        // Note: デバイス履歴への保存はfetchDeviceInfo()内で実行される（複数回取得の最良結果を保存するため）
                    } catch (e: Exception) {
                        logManager.addLog("Error parsing DeviceInfo: ${e.message}")
                        currentDeviceCommandCompletion?.complete(Pair(false, e.message))
                    }
                } else if (currentBufferAsString.startsWith("ERROR:")) {
                    logManager.addLog("Error response GET:info: $currentBufferAsString")
                    currentDeviceCommandCompletion?.complete(Pair(false, currentBufferAsString))
                }
            }
            BleOperation.FETCHING_FILE_LIST -> {
                val incomingString = value.toString(Charsets.UTF_8).trim()
                if (responseBuffer.isEmpty() && !incomingString.startsWith("[") && !incomingString.startsWith("ERROR:")) {
                    if (incomingString == "[]") {
                        _fileList.value = emptyList()
                        currentDeviceCommandCompletion?.complete(Pair(true, null))
                    }
                    return
                }
                responseBuffer.append(value.toString(Charsets.UTF_8))
                val currentBufferAsString = responseBuffer.toString()

                if (currentBufferAsString.trim().endsWith("]")) {
                    try {
                        _fileList.value = parseFileEntries(currentBufferAsString)
                        logManager.addLog("Parsed FileList. Count: ${_fileList.value.size}")
                        currentDeviceCommandCompletion?.complete(Pair(true, null))
                    } catch (e: Exception) {
                        logManager.addLog("Error parsing FileList: ${e.message}")
                        currentDeviceCommandCompletion?.complete(Pair(false, e.message))
                    }
                } else if (currentBufferAsString.startsWith("ERROR:")) {
                    logManager.addLog("Error response GET:ls: $currentBufferAsString")
                    _fileList.value = emptyList()
                    currentDeviceCommandCompletion?.complete(Pair(false, currentBufferAsString))
                }
            }
            BleOperation.SENDING_TIME -> {
                val response = value.toString(Charsets.UTF_8).trim()
                if (response.startsWith("OK: Time")) {
                    currentDeviceCommandCompletion?.complete(Pair(true, null))
                    responseBuffer.clear()
                } else if (response.startsWith("ERROR:")) {
                    currentDeviceCommandCompletion?.complete(Pair(false, response))
                    responseBuffer.clear()
                } else {
                    logManager.addLog("Unexpected response during SET:time: $response")
                }
            }
            BleOperation.FETCHING_SETTINGS -> {
                responseBuffer.append(value.toString(Charsets.UTF_8))

                scope.launch {
                    delay(200)

                    if (_currentOperation.value == BleOperation.FETCHING_SETTINGS) {
                        val settingsString = responseBuffer.toString().trim()
                        logManager.addLog("Assembled remote settings (GET:setting_ini): $settingsString")

                        if (settingsString.startsWith("ERROR:")) {
                            logManager.addLog("Error response GET:setting_ini: $settingsString")
                            currentSettingsCommandCompletion?.complete(Pair(false, settingsString))
                        } else {
                            try {
                                val remoteSettings = DeviceSettings.fromIniString(settingsString)
                                _deviceSettings.value = remoteSettings
                                logManager.addLog("Applied remote settings to local state: $remoteSettings")
                                currentSettingsCommandCompletion?.complete(Pair(true, null))
                            } catch (e: Exception) {
                                logManager.addLog("Error parsing settings (GET:setting_ini): ${e.message}")
                                currentSettingsCommandCompletion?.complete(Pair(false, e.message))
                            }
                        }
                        _currentOperation.value = BleOperation.IDLE
                        responseBuffer.clear()
                    }
                }
            }
            // BleOperation.SENDING_SETTINGS does not have a direct handleResponse logic in original.
            // It just sends command and then navigates back and resets _currentOperation after a delay.
            // If response is expected, it should be handled here.
            else -> {
                // Responses for other operations are handled by other managers (e.g., FileTransferManager)
                // or are not awaited in this manager.
            }
        }
    }
}
