package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.PushAlertConfig
import com.example.domain.model.THAI_PROVINCE_PRESETS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preparednessDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "disaster_preparedness_prefs"
)

class PreparednessPreferences(private val context: Context) {
    private object Keys {
        val CHECKED_KIT_ITEMS = stringSetPreferencesKey("checked_kit_items")
        val SELECTED_PROVINCE_ID = stringPreferencesKey("selected_province_id")

        // FCM Push Alert configuration
        val PUSH_ALERTS_ENABLED = booleanPreferencesKey("push_alerts_enabled")
        val FCM_TOKEN = stringPreferencesKey("fcm_registration_token")
        val ALERT_LATITUDE = doublePreferencesKey("push_alert_latitude")
        val ALERT_LONGITUDE = doublePreferencesKey("push_alert_longitude")
        val ALERT_LOCATION_LABEL = stringPreferencesKey("push_alert_location_label")
        val ALERT_RADIUS_KM = doublePreferencesKey("push_alert_radius_km")
        val SUBSCRIBED_TOPICS = stringSetPreferencesKey("push_subscribed_topics")
        val LAST_FCM_SYNC_MS = longPreferencesKey("push_last_sync_ms")
        val NOTIFIED_EVENT_IDS = stringSetPreferencesKey("notified_disaster_event_ids")
        val FCM_SERVER_URL = stringPreferencesKey("fcm_server_base_url")
    }

    val checkedKitItemIds: Flow<Set<String>> = context.preparednessDataStore.data
        .map { prefs -> prefs[Keys.CHECKED_KIT_ITEMS] ?: emptySet() }

    val selectedProvinceId: Flow<String> = context.preparednessDataStore.data
        .map { prefs -> prefs[Keys.SELECTED_PROVINCE_ID] ?: "bkk" }

    val pushAlertConfig: Flow<PushAlertConfig> = context.preparednessDataStore.data
        .map { prefs ->
            val defaultPreset = THAI_PROVINCE_PRESETS.first()
            PushAlertConfig(
                enabled = prefs[Keys.PUSH_ALERTS_ENABLED] ?: false,
                fcmToken = prefs[Keys.FCM_TOKEN],
                latitude = prefs[Keys.ALERT_LATITUDE] ?: defaultPreset.latitude,
                longitude = prefs[Keys.ALERT_LONGITUDE] ?: defaultPreset.longitude,
                locationLabel = prefs[Keys.ALERT_LOCATION_LABEL] ?: defaultPreset.nameTh,
                radiusKm = prefs[Keys.ALERT_RADIUS_KM] ?: 150.0,
                subscribedTopics = prefs[Keys.SUBSCRIBED_TOPICS] ?: emptySet(),
                lastSyncedAtMs = prefs[Keys.LAST_FCM_SYNC_MS] ?: 0L,
                fcmServerUrl = prefs[Keys.FCM_SERVER_URL].orEmpty()
            )
        }

    val notifiedEventIds: Flow<Set<String>> = context.preparednessDataStore.data
        .map { prefs -> prefs[Keys.NOTIFIED_EVENT_IDS] ?: emptySet() }

    suspend fun toggleKitItem(itemId: String) {
        context.preparednessDataStore.edit { prefs ->
            val current = prefs[Keys.CHECKED_KIT_ITEMS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(itemId)) {
                current.remove(itemId)
            }
            prefs[Keys.CHECKED_KIT_ITEMS] = current
        }
    }

    suspend fun setSelectedProvince(provinceId: String) {
        context.preparednessDataStore.edit { prefs ->
            prefs[Keys.SELECTED_PROVINCE_ID] = provinceId
        }
    }

    suspend fun saveFcmToken(token: String) {
        context.preparednessDataStore.edit { prefs ->
            prefs[Keys.FCM_TOKEN] = token
        }
    }

    suspend fun savePushAlertConfig(config: PushAlertConfig) {
        context.preparednessDataStore.edit { prefs ->
            prefs[Keys.PUSH_ALERTS_ENABLED] = config.enabled
            config.fcmToken?.let { prefs[Keys.FCM_TOKEN] = it }
            prefs[Keys.ALERT_LATITUDE] = config.latitude
            prefs[Keys.ALERT_LONGITUDE] = config.longitude
            prefs[Keys.ALERT_LOCATION_LABEL] = config.locationLabel
            prefs[Keys.ALERT_RADIUS_KM] = config.radiusKm
            prefs[Keys.SUBSCRIBED_TOPICS] = config.subscribedTopics
            prefs[Keys.LAST_FCM_SYNC_MS] = config.lastSyncedAtMs
            prefs[Keys.FCM_SERVER_URL] = config.fcmServerUrl
        }
    }

    suspend fun saveFcmServerUrl(url: String) {
        context.preparednessDataStore.edit { prefs ->
            prefs[Keys.FCM_SERVER_URL] = url.trim()
        }
    }

    suspend fun markEventsNotified(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        context.preparednessDataStore.edit { prefs ->
            val existing = prefs[Keys.NOTIFIED_EVENT_IDS]?.toList() ?: emptyList()
            val merged = (existing + eventIds).distinct().takeLast(200).toSet()
            prefs[Keys.NOTIFIED_EVENT_IDS] = merged
        }
    }
}
