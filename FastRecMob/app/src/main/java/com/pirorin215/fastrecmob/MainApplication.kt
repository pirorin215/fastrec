package com.pirorin215.fastrecmob

import android.app.Application
import com.pirorin215.fastrecmob.data.BleRepository
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.ConnectionState
import com.pirorin215.fastrecmob.data.LastKnownLocationRepository
import com.pirorin215.fastrecmob.data.Settings
import com.pirorin215.fastrecmob.data.TranscriptionResultRepository
import com.pirorin215.fastrecmob.data.DeviceHistoryRepository
import com.pirorin215.fastrecmob.viewModel.BleConnectionManager
import com.pirorin215.fastrecmob.viewModel.BleOrchestrator
import com.pirorin215.fastrecmob.viewModel.BleSelectionManager
import com.pirorin215.fastrecmob.viewModel.LocationMonitor
import com.pirorin215.fastrecmob.viewModel.LogManager
import com.pirorin215.fastrecmob.viewModel.TranscriptionManager
import com.pirorin215.fastrecmob.viewModel.GoogleTasksManager
import com.pirorin215.fastrecmob.viewModel.DeviceHistoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Start core components by accessing them.
        // This triggers the lazy initialization.
        logManager.addLog("Application created. Initializing core components.")
        bleConnectionManager
        bleOrchestrator
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Data Repositories ---
    val appSettingsRepository: AppSettingsRepository by lazy {
        AppSettingsRepository(this)
    }
    val lastKnownLocationRepository: LastKnownLocationRepository by lazy {
        LastKnownLocationRepository(this)
    }
    val transcriptionResultRepository: TranscriptionResultRepository by lazy {
        TranscriptionResultRepository(this)
    }
    val bleRepository: BleRepository by lazy {
        BleRepository(this)
    }
    val deviceHistoryRepository: DeviceHistoryRepository by lazy {
        DeviceHistoryRepository(this)
    }

    // --- Managers and Core Components ---
    val logManager: LogManager by lazy {
        LogManager()
    }
    val locationTracker: LocationTracker by lazy {
        LocationTracker(this)
    }

    // --- BLE Connection Handling ---
    val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val onDeviceReadyEvent = MutableSharedFlow<Unit>()
    val disconnectSignal = MutableSharedFlow<Unit>()

    val bleConnectionManager: BleConnectionManager by lazy {
        BleConnectionManager(
            context = this,
            scope = applicationScope,
            repository = bleRepository,
            logManager = logManager,
            _connectionStateFlow = connectionStateFlow,
            _onDeviceReadyEvent = onDeviceReadyEvent,
            _disconnectSignal = disconnectSignal
        )
    }

    // --- Location Tracking ---
    val locationMonitor: LocationMonitor by lazy {
        LocationMonitor(this, applicationScope, lastKnownLocationRepository, logManager)
    }

    // --- Google Tasks Integration ---
    val googleTasksIntegration: GoogleTasksManager by lazy {
        GoogleTasksManager(
            application = this,
            appSettingsRepository = appSettingsRepository,
            transcriptionResultRepository = transcriptionResultRepository,
            context = this,
            scope = applicationScope,
            logManager = logManager
        )
    }

    // --- Transcription Management ---
    val transcriptionManager: TranscriptionManager by lazy {
        TranscriptionManager(
            context = this,
            scope = applicationScope,
            appSettingsRepository = appSettingsRepository,
            transcriptionResultRepository = transcriptionResultRepository,
            currentForegroundLocationFlow = locationMonitor.currentForegroundLocation,
            audioDirNameFlow = appSettingsRepository.getFlow(Settings.AUDIO_DIR_NAME).stateIn(
                applicationScope,
                SharingStarted.WhileSubscribed(5000),
                "FastRecRecordings"
            ),
            transcriptionCacheLimitFlow = appSettingsRepository.getFlow(Settings.TRANSCRIPTION_CACHE_LIMIT).stateIn(
                applicationScope,
                SharingStarted.WhileSubscribed(5000),
                100
            ),
            logManager = logManager,
            googleTaskTitleLengthFlow = appSettingsRepository.getFlow(Settings.GOOGLE_TASK_TITLE_LENGTH).stateIn(
                applicationScope,
                SharingStarted.WhileSubscribed(5000),
                20
            ),
            googleTasksIntegration = googleTasksIntegration,
            locationTracker = locationTracker
        )
    }

    val bleSelectionManager: BleSelectionManager by lazy {
        BleSelectionManager(logManager = logManager)
    }

    val deviceHistoryViewModelFactory: DeviceHistoryViewModel.Factory by lazy {
        DeviceHistoryViewModel.Factory(deviceHistoryRepository)
    }

    // --- The Grand Orchestrator ---
    val bleOrchestrator: BleOrchestrator by lazy {
        BleOrchestrator(
            scope = applicationScope,
            context = this,
            repository = bleRepository,
            connectionStateFlow = connectionStateFlow,
            onDeviceReadyEvent = onDeviceReadyEvent,
            transcriptionManager = transcriptionManager,
            locationMonitor = locationMonitor,
            appSettingsRepository = appSettingsRepository,
            bleSelectionManager = bleSelectionManager,
            transcriptionResults = transcriptionResultRepository.transcriptionResultsFlow.stateIn(
                applicationScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            ),
            logManager = logManager,
            disconnectSignal = disconnectSignal,
            locationTracker = locationTracker,
            deviceHistoryRepository = deviceHistoryRepository
        )
    }
}
