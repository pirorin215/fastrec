package com.pirorin215.fastrecmob.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.pirorin215.fastrecmob.constants.TimeConstants
import com.pirorin215.fastrecmob.constants.LocationConstants
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.launch
import com.pirorin215.fastrecmob.service.GeocoderService

private val Context.deviceHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_history")

class DeviceHistoryRepository(
    private val context: Context,
    private val geocoderService: GeocoderService? = null
) {

    private object PreferencesKeys {
        val DEVICE_HISTORY_LIST = stringPreferencesKey("device_history_list")
    }

    companion object {
        // Constants moved to TimeConstants.kt and LocationConstants.kt
    }

    val deviceHistoryFlow: Flow<List<DeviceHistoryEntry>> = context.deviceHistoryDataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.DEVICE_HISTORY_LIST] ?: "[]"
            try {
                JsonUtil.json.decodeFromString<List<DeviceHistoryEntry>>(jsonString)
            } catch (e: Exception) {
                // Log the error or handle it appropriately, return empty list to prevent crash
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun addEntry(entry: DeviceHistoryEntry) {
        updateListInDataStore(context.deviceHistoryDataStore, PreferencesKeys.DEVICE_HISTORY_LIST) { currentList ->
            // Check if currentList is not empty for filtering
            if (currentList.isNotEmpty()) {
                val lastEntry: DeviceHistoryEntry = currentList.first()

                val timeDiff = entry.timestamp - lastEntry.timestamp

                val newLat = entry.latitude
                val newLon = entry.longitude
                val lastLat = lastEntry.latitude
                val lastLon = lastEntry.longitude

                val locationIsSimilar = if (newLat != null && newLon != null && lastLat != null && lastLon != null) {
                    val latDiff: Double = newLat - lastLat
                    val lonDiff: Double = newLon - lastLon
                    val distanceSquared: Double = latDiff * latDiff + lonDiff * lonDiff
                    distanceSquared < LocationConstants.LOCATION_THRESHOLD_SQUARED
                } else {
                    false // If location data is incomplete, assume not similar enough to block
                }

                // Only prevent adding if BOTH time is within threshold AND location is similar
                if (timeDiff < TimeConstants.DEVICE_HISTORY_TIME_THRESHOLD_MS && locationIsSimilar) {
                    return@updateListInDataStore // Don't add if within 30 minutes AND location is too similar
                }
            }

            // Add new entry to the beginning of the list
            currentList.add(0, entry)
        }

        // 緯度経度がある場合、非同期で住所も取得
        if (geocoderService != null && entry.latitude != null && entry.longitude != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val address = geocoderService.getAddressFromLocation(
                    entry.latitude,
                    entry.longitude
                )
                updateAddress(entry.timestamp, address)
            }
        }
    }

    suspend fun clearAllEntries() {
        context.deviceHistoryDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.DEVICE_HISTORY_LIST)
        }
    }

    suspend fun deleteEntriesByTimestamps(timestamps: List<Long>) {
        updateListInDataStore(context.deviceHistoryDataStore, PreferencesKeys.DEVICE_HISTORY_LIST) { currentList ->
            val filteredList = currentList.filterNot { entry: DeviceHistoryEntry ->
                timestamps.contains(entry.timestamp)
            }.toMutableList()
            currentList.clear()
            currentList.addAll(filteredList)
        }
    }

    /**
     * 指定した期間より古いエントリを削除する
     * @param retentionPeriodMs 保持期間（ミリ秒）
     */
    suspend fun deleteOldEntries(retentionPeriodMs: Long) {
        updateListInDataStore(context.deviceHistoryDataStore, PreferencesKeys.DEVICE_HISTORY_LIST) { currentList ->
            val filteredList: List<DeviceHistoryEntry> = currentList.filterByTimestamp(
                retentionPeriodMs
            ) { entry: DeviceHistoryEntry ->
                entry.timestamp
            }
            currentList.clear()
            currentList.addAll(filteredList)
        }
    }

    /**
     * 指定したタイムスタンプのエントリの住所を更新する
     * @param timestamp エントリのタイムスタンプ
     * @param address 住所文字列
     */
    suspend fun updateAddress(timestamp: Long, address: String?) {
        updateListInDataStore<DeviceHistoryEntry>(
            context.deviceHistoryDataStore,
            PreferencesKeys.DEVICE_HISTORY_LIST
        ) { currentList ->
            val index = currentList.indexOfFirst { it.timestamp == timestamp }
            if (index != -1) {
                currentList[index] = currentList[index].copy(address = address)
            }
        }
    }

    /**
     * 住所が未取得のエントリを取得する
     * @return 住所がnullで、緯度経度が存在するエントリのリスト
     */
    suspend fun getEntriesWithoutAddress(): List<DeviceHistoryEntry> {
        return context.deviceHistoryDataStore.data.first()[PreferencesKeys.DEVICE_HISTORY_LIST]?.let { jsonString ->
            try {
                JsonUtil.json.decodeFromString<List<DeviceHistoryEntry>>(jsonString)
                    .filter { it.address == null && it.latitude != null && it.longitude != null }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } ?: emptyList()
    }
}
