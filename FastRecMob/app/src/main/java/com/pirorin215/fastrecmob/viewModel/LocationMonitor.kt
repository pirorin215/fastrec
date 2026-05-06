package com.pirorin215.fastrecmob.viewModel

import android.content.Context
import com.pirorin215.fastrecmob.LocationData
import com.pirorin215.fastrecmob.LocationTracker
import com.pirorin215.fastrecmob.data.LastKnownLocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocationMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val lastKnownLocationRepository: LastKnownLocationRepository,
    private val logManager: LogManager
) {
    private val locationTracker = LocationTracker(context)

    private val _currentForegroundLocation = MutableStateFlow<LocationData?>(null)
    val currentForegroundLocation = _currentForegroundLocation.asStateFlow()

    private var lowPowerLocationJob: Job? = null

    fun startLowPowerLocationUpdates() {
        if (lowPowerLocationJob?.isActive == true) {
            logManager.addDebugLog("Location updates already active")
            return
        }
        logManager.addLog("Starting location updates")
        lowPowerLocationJob = scope.launch {
            // Initialize from repository if currently null to provide immediate last known location
            if (_currentForegroundLocation.value == null) {
                lastKnownLocationRepository.lastKnownLocationFlow.first()?.let { lastLocation ->
                    _currentForegroundLocation.value = lastLocation
                    logManager.addDebugLog("Initialized location from cache: Lat=${lastLocation.latitude}, Lng=${lastLocation.longitude}")
                }
            }

            while (true) {
                locationTracker.getLowPowerLocation().onSuccess { locationData ->
                    _currentForegroundLocation.value = locationData
                    // Persist successfully fetched location to repository
                    lastKnownLocationRepository.saveLastKnownLocation(locationData)
                    logManager.addDebugLog("Location updated and saved: Lat=${locationData.latitude}, Lng=${locationData.longitude}")
                }.onFailure { e ->
                    // Keep previous location instead of clearing it to ensure we always have a "last known" value
                    logManager.addDebugLog("Location update failed, keeping previous value: ${e.message}")
                }
                delay(30000L) // Update every 30 seconds
            }
        }
    }

    private suspend fun saveCurrentLocationAsLastKnown() {
        locationTracker.getCurrentLocation().onSuccess { locationData ->
            _currentForegroundLocation.value = locationData
            lastKnownLocationRepository.saveLastKnownLocation(locationData)
            logManager.addDebugLog("Saved location")
        }.onFailure { e ->
            logManager.addDebugLog("Failed to save location: ${e.message}")
        }
    }

    suspend fun updateLocation() {
        logManager.addDebugLog("Updating location")
        saveCurrentLocationAsLastKnown()
    }

    fun stopLowPowerLocationUpdates() {
        lowPowerLocationJob?.cancel()
        lowPowerLocationJob = null
        // CRITICAL: Do NOT clear _currentForegroundLocation.value here.
        // Keeping the last value ensures it's available for background transcription tasks.
        logManager.addLog("Stopped location updates (keeping last known location)")
    }

    fun onCleared() {
        stopLowPowerLocationUpdates()
    }
}
