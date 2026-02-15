package com.pirorin215.fastrecmob.viewModel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.LastKnownLocationRepository
import com.pirorin215.fastrecmob.data.TranscriptionResultRepository


// import com.pirorin215.fastrecmob.viewModel.LogManager // Import LogManager once -- Keeping this if needed, but removing MainViewModel import

// MainViewModelFactory block removed

class AppSettingsViewModelFactory(
    private val application: Application,
    private val appSettingsRepository: AppSettingsRepository,
    private val transcriptionManager: TranscriptionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppSettingsViewModel(appSettingsRepository, transcriptionManager, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}