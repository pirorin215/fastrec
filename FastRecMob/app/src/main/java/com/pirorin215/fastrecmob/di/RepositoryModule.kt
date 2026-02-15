package com.pirorin215.fastrecmob.di

import android.content.Context
import com.pirorin215.fastrecmob.data.AppSettingsRepository
import com.pirorin215.fastrecmob.data.BleRepository
import com.pirorin215.fastrecmob.data.DeviceHistoryRepository
import com.pirorin215.fastrecmob.data.LastKnownLocationRepository
import com.pirorin215.fastrecmob.data.TranscriptionResultRepository
import org.koin.dsl.module

val repositoryModule = module {
    single { AppSettingsRepository(get<Context>()) }
    single { BleRepository(get<Context>()) }
    single { TranscriptionResultRepository(get<Context>()) }
    single { LastKnownLocationRepository(get<Context>()) }
    single { DeviceHistoryRepository(get<Context>()) }
}
