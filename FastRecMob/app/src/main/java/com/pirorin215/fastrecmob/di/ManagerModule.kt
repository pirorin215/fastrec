package com.pirorin215.fastrecmob.di

import android.content.Context
import com.pirorin215.fastrecmob.LocationTracker
import com.pirorin215.fastrecmob.viewModel.BleSelectionManager
import com.pirorin215.fastrecmob.viewModel.GoogleTasksManager
import com.pirorin215.fastrecmob.viewModel.LocationMonitor
import com.pirorin215.fastrecmob.viewModel.LogManager
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.TranscriptionResultRepository
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val managerModule = module {
    single { LogManager() }
    single { LocationTracker(get<Context>()) }
    single {
        LocationMonitor(
            get<Context>(),
            get<CoroutineScope>(),
            get(),
            get()
        )
    }
    single {
        BleSelectionManager(
            get()
        )
    }
    single {
        GoogleTasksManager(
            get(),
            get(),
            get(),
            get<Context>(),
            get<CoroutineScope>(),
            get()
        )
    }
}
