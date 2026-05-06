package com.pirorin215.fastrecmob.viewModel

import android.util.Log
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Environment
import com.pirorin215.fastrecmob.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG,   // Detailed logs for debugging
    INFO,    // Important state changes
    ERROR    // Errors only
}

class LogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Production uses INFO, Debug builds use DEBUG
    private val currentLogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val formattedMessage = "[$timestamp] $message"
        
        // Only log if the level is at or above the current log level
        if (shouldLog(level)) {
            Log.d("AppLog", formattedMessage)
            _logs.value = (_logs.value + formattedMessage).takeLast(500)
        }
    }

    fun addDebugLog(message: String) {
        addLog(message, LogLevel.DEBUG)
    }

    fun addErrorLog(message: String) {
        addLog(message, LogLevel.ERROR)
    }

    private fun shouldLog(level: LogLevel): Boolean {
        return when (currentLogLevel) {
            LogLevel.DEBUG -> true // Log everything in debug mode
            LogLevel.INFO -> level != LogLevel.DEBUG // Skip debug logs
            LogLevel.ERROR -> level == LogLevel.ERROR // Only errors
        }
    }

    fun saveLogsToFile(context: Context): String? {
        addLog("Saving app logs to file...", LogLevel.INFO)
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "app_log_$timestamp.txt"
            
            // Get Public Documents directory
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val fastRecDir = File(documentsDir, "FastRecMob/logs")
            if (!fastRecDir.exists()) {
                fastRecDir.mkdirs()
            }
            
            val logFile = File(fastRecDir, fileName)
            
            // Prepare log content with system status
            val logContent = StringBuilder()
            logContent.append("=== FastRecMob App Log Snapshot ===\n")
            logContent.append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            logContent.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n")
            
            // Add System BLE Status
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val connectedGattDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
            logContent.append("System BLE GATT Connections: ${connectedGattDevices.size}\n")
            connectedGattDevices.forEach { device ->
                logContent.append("  - Device: ${device.name} (${device.address})\n")
            }
            
            logContent.append("\n=== Log Messages ===\n")
            _logs.value.forEach { logContent.append(it).append("\n") }
            
            logFile.writeText(logContent.toString())
            return logFile.absolutePath
        } catch (e: Exception) {
            addErrorLog("Failed to save logs: ${e.message}")
            return null
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
