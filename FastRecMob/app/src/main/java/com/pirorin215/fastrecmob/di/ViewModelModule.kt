package com.pirorin215.fastrecmob.di

import android.app.Application
import org.koin.android.ext.koin.androidApplication
import com.pirorin215.fastrecmob.viewModel.BleConnectionManager
import com.pirorin215.fastrecmob.viewModel.BleOrchestrator
import com.pirorin215.fastrecmob.viewModel.BleSelectionManager
import com.pirorin215.fastrecmob.viewModel.BleViewModel
import com.pirorin215.fastrecmob.viewModel.DeviceHistoryViewModel
import com.pirorin215.fastrecmob.viewModel.GoogleTasksManager
import com.pirorin215.fastrecmob.viewModel.GoogleTasksViewModel
import com.pirorin215.fastrecmob.viewModel.LocationMonitor
import com.pirorin215.fastrecmob.viewModel.LogManager
import com.pirorin215.fastrecmob.viewModel.MainViewModel
import com.pirorin215.fastrecmob.viewModel.TranscriptionManager
import com.pirorin215.fastrecmob.viewModel.TranscriptionViewModel
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.TranscriptionResultRepository
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        MainViewModel(
            androidApplication(),
            get<BleConnectionManager>(),
            get<BleOrchestrator>(),
            get<TranscriptionManager>(),
            get<BleSelectionManager>(),
            get<GoogleTasksManager>(),
            get<LocationMonitor>(),
            get<LogManager>(),
            get<TranscriptionResultRepository>(),
            get<AppSettingsRepository>()
        )
    }

    viewModel {
        TranscriptionViewModel(
            androidApplication(),
            get<TranscriptionManager>(),
            get<TranscriptionResultRepository>(),
            get<AppSettingsRepository>(),
            get<LogManager>(),
            get<BleSelectionManager>()
        )
    }

    viewModel { GoogleTasksViewModel(get<GoogleTasksManager>(), get<AppSettingsRepository>()) }

    viewModel { BleViewModel(get<BleConnectionManager>(), get<BleOrchestrator>(), get<BleSelectionManager>()) }

    viewModel { DeviceHistoryViewModel(get()) }
}
