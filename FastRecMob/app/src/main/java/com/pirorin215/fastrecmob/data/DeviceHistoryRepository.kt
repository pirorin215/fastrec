package com.pirorin215.fastrecmob.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.deviceHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_history")

class DeviceHistoryRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEVICE_HISTORY_LIST = stringPreferencesKey("device_history_list")
    }

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        // Constants for filtering
        private const val TIME_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes in milliseconds
        private const val LOCATION_THRESHOLD_SQUARED = 0.000001 // Approx 11m squared difference for lat/lon
    }

    val deviceHistoryFlow: Flow<List<DeviceHistoryEntry>> = context.deviceHistoryDataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.DEVICE_HISTORY_LIST] ?: "[]"
            try {
                json.decodeFromString<List<DeviceHistoryEntry>>(jsonString)
            } catch (e: Exception) {
                // Log the error or handle it appropriately, return empty list to prevent crash
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun addEntry(entry: DeviceHistoryEntry) {
        context.deviceHistoryDataStore.edit { preferences ->
            val jsonString = preferences[PreferencesKeys.DEVICE_HISTORY_LIST] ?: "[]"
            val currentList = try {
                json.decodeFromString<MutableList<DeviceHistoryEntry>>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }

            // Check if currentList is not empty for filtering
            if (currentList.isNotEmpty()) {
                val lastEntry = currentList.first()

                val timeDiff = entry.timestamp - lastEntry.timestamp

                val newLat = entry.latitude
                val newLon = entry.longitude
                val lastLat = lastEntry.latitude
                val lastLon = lastEntry.longitude

                val locationIsSimilar = if (newLat != null && newLon != null && lastLat != null && lastLon != null) {
                    val latDiff = newLat - lastLat
                    val lonDiff = newLon - lastLon
                    val distanceSquared = latDiff * latDiff + lonDiff * lonDiff
                    distanceSquared < LOCATION_THRESHOLD_SQUARED
                } else {
                    false // If location data is incomplete, assume not similar enough to block
                }

                // Only prevent adding if BOTH time is within threshold AND location is similar
                if (timeDiff < TIME_THRESHOLD_MS && locationIsSimilar) {
                    return@edit // Don't add if within 30 minutes AND location is too similar
                }
            }

            currentList.add(0, entry) // Add new entry to the beginning of the list
            preferences[PreferencesKeys.DEVICE_HISTORY_LIST] = json.encodeToString(currentList)
        }
    }

    suspend fun clearAllEntries() {
        context.deviceHistoryDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.DEVICE_HISTORY_LIST)
        }
    }

    suspend fun deleteEntriesByTimestamps(timestamps: List<Long>) {
        context.deviceHistoryDataStore.edit { preferences ->
            val jsonString = preferences[PreferencesKeys.DEVICE_HISTORY_LIST] ?: "[]"
            val currentList = try {
                json.decodeFromString<MutableList<DeviceHistoryEntry>>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }

            // 指定されたタイムスタンプのエントリを削除
            val filteredList = currentList.filterNot { entry ->
                timestamps.contains(entry.timestamp)
            }

            preferences[PreferencesKeys.DEVICE_HISTORY_LIST] = json.encodeToString(filteredList)
        }
    }
}
