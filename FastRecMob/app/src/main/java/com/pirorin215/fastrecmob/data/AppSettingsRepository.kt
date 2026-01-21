package com.pirorin215.fastrecmob.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStoreのインスタンスをContextの拡張プロパティとして定義
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/**
 * Generic setting key with type safety
 * @param T The type of the setting value
 */
sealed class SettingKey<T> {
    abstract val defaultValue: T

    /**
     * Direct mapping for primitive types (String, Int, Boolean, Float)
     */
    class Direct<T> internal constructor(
        internal val preferencesKey: Preferences.Key<T>,
        override val defaultValue: T
    ) : SettingKey<T>()

    /**
     * Mapped type for complex types that need conversion (e.g., ThemeMode)
     */
    class Mapped<T, R> internal constructor(
        internal val preferencesKey: Preferences.Key<R>,
        override val defaultValue: T,
        internal val toStored: (T) -> R,
        internal val fromStored: (R) -> T
    ) : SettingKey<T>()
}

/**
 * Transcription provider options
 */
enum class TranscriptionProvider {
    GOOGLE,
    GROQ;

    companion object {
        fun fromString(value: String?): TranscriptionProvider {
            return try {
                valueOf(value ?: "GOOGLE")
            } catch (e: IllegalArgumentException) {
                GOOGLE
            }
        }
    }
}

/**
 * Centralized definition of all app settings
 */
object Settings {
    val API_KEY = SettingKey.Direct(
        stringPreferencesKey("google_cloud_api_key"),
        ""
    )

    val GROQ_API_KEY = SettingKey.Direct(
        stringPreferencesKey("groq_api_key"),
        ""
    )

    val TRANSCRIPTION_PROVIDER = SettingKey.Mapped(
        preferencesKey = stringPreferencesKey("transcription_provider"),
        defaultValue = TranscriptionProvider.GOOGLE,
        toStored = { it.name },
        fromStored = { TranscriptionProvider.fromString(it) }
    )

    val GEMINI_API_KEY = SettingKey.Direct(
        stringPreferencesKey("gemini_api_key"),
        ""
    )

    val TRANSCRIPTION_CACHE_LIMIT = SettingKey.Direct(
        intPreferencesKey("transcription_cache_limit"),
        100
    )

    val AUDIO_DIR_NAME = SettingKey.Direct(
        stringPreferencesKey("audio_dir_name"),
        "FastRecRecordings"
    )

    val TRANSCRIPTION_FONT_SIZE = SettingKey.Direct(
        intPreferencesKey("transcription_font_size"),
        14
    )

    val THEME_MODE = SettingKey.Mapped(
        preferencesKey = stringPreferencesKey("theme_mode"),
        defaultValue = ThemeMode.SYSTEM,
        toStored = { it.name },
        fromStored = { ThemeMode.valueOf(it) }
    )

    val IS_API_KEY_VERIFIED = SettingKey.Direct(
        booleanPreferencesKey("is_api_key_verified"),
        false
    )

    val GOOGLE_TODO_LIST_NAME = SettingKey.Direct(
        stringPreferencesKey("google_todo_list_name"),
        "fastrec"
    )

    val GOOGLE_TASK_TITLE_LENGTH = SettingKey.Direct(
        intPreferencesKey("google_task_title_length"),
        20
    )

    val GOOGLE_TASKS_SYNC_INTERVAL_MINUTES = SettingKey.Direct(
        intPreferencesKey("google_tasks_sync_interval_minutes"),
        5
    )

    val SHOW_COMPLETED_GOOGLE_TASKS = SettingKey.Direct(
        booleanPreferencesKey("show_completed_google_tasks"),
        false
    )

    val AUTO_START_ON_BOOT = SettingKey.Direct(
        booleanPreferencesKey("auto_start_on_boot"),
        false
    )

    val CHUNK_BURST_SIZE = SettingKey.Direct(
        intPreferencesKey("chunk_burst_size"),
        8
    )

    val VOLTAGE_RETRY_COUNT = SettingKey.Direct(
        intPreferencesKey("voltage_retry_count"),
        5
    )

    val VOLTAGE_ACQUISITION_INTERVAL = SettingKey.Direct(
        intPreferencesKey("voltage_acquisition_interval"),
        1000
    )

    val LOW_VOLTAGE_THRESHOLD = SettingKey.Direct(
        floatPreferencesKey("low_voltage_threshold"),
        3.61f
    )

    val LOW_VOLTAGE_NOTIFY_EVERY_TIME = SettingKey.Direct(
        booleanPreferencesKey("low_voltage_notify_every_time"),
        false
    )

    val TRANSCRIPTION_NOTIFICATION_ENABLED = SettingKey.Direct(
        booleanPreferencesKey("transcription_notification_enabled"),
        false
    )
}

class AppSettingsRepository(private val context: Context) {

    /**
     * Generic method to get a Flow for any setting
     *
     * Usage example:
     * ```kotlin
     * val apiKeyFlow = repository.getFlow(Settings.API_KEY)
     * val chunkSizeFlow = repository.getFlow(Settings.CHUNK_BURST_SIZE)
     * ```
     */
    fun <T> getFlow(key: SettingKey<T>): Flow<T> {
        return when (key) {
            is SettingKey.Direct -> {
                context.dataStore.data.map { preferences ->
                    @Suppress("UNCHECKED_CAST")
                    (preferences[key.preferencesKey] as? T) ?: key.defaultValue
                }
            }
            is SettingKey.Mapped<T, *> -> {
                context.dataStore.data.map { preferences ->
                    @Suppress("UNCHECKED_CAST")
                    val stored = preferences[key.preferencesKey as Preferences.Key<Any>]
                    if (stored != null) {
                        (key.fromStored as (Any) -> T)(stored)
                    } else {
                        key.defaultValue
                    }
                }
            }
        }
    }

    /**
     * Generic method to set a value for any setting
     *
     * Usage example:
     * ```kotlin
     * repository.setValue(Settings.API_KEY, "new-api-key")
     * repository.setValue(Settings.CHUNK_BURST_SIZE, 16)
     * ```
     */
    suspend fun <T> setValue(key: SettingKey<T>, value: T) {
        when (key) {
            is SettingKey.Direct -> {
                context.dataStore.edit { preferences ->
                    @Suppress("UNCHECKED_CAST")
                    preferences[key.preferencesKey as Preferences.Key<Any>] = value as Any
                }
            }
            is SettingKey.Mapped<T, *> -> {
                context.dataStore.edit { preferences ->
                    @Suppress("UNCHECKED_CAST")
                    val stored = (key.toStored as (T) -> Any)(value)
                    preferences[key.preferencesKey as Preferences.Key<Any>] = stored
                }
            }
        }
    }

}
