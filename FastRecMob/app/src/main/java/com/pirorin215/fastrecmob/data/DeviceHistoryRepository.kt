package com.pirorin215.fastrecmob.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.pirorin215.fastrecmob.constants.TimeConstants
import com.pirorin215.fastrecmob.constants.LocationConstants
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
        // Constants moved to TimeConstants.kt and LocationConstants.kt
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
                    distanceSquared < LocationConstants.LOCATION_THRESHOLD_SQUARED
                } else {
                    false // If location data is incomplete, assume not similar enough to block
                }

                // Only prevent adding if BOTH time is within threshold AND location is similar
                if (timeDiff < TimeConstants.DEVICE_HISTORY_TIME_THRESHOLD_MS && locationIsSimilar) {
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

    /**
     * 指定した期間より古いエントリを削除する
     * @param retentionPeriodMs 保持期間（ミリ秒）
     */
    suspend fun deleteOldEntries(retentionPeriodMs: Long) {
        context.deviceHistoryDataStore.edit { preferences ->
            val jsonString = preferences[PreferencesKeys.DEVICE_HISTORY_LIST] ?: "[]"
            val currentList = try {
                json.decodeFromString<MutableList<DeviceHistoryEntry>>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }

            val cutoffTime = System.currentTimeMillis() - retentionPeriodMs
            
            // 保持期間内のデータのみを残す
            val filteredList = currentList.filter { entry ->
                entry.timestamp >= cutoffTime
            }

            // 変更がある場合のみ保存
            if (filteredList.size < currentList.size) {
                preferences[PreferencesKeys.DEVICE_HISTORY_LIST] = json.encodeToString(filteredList)
            }
        }
    }
}
