package com.example.notifications

import android.util.Log
import com.example.data.local.DisasterDatabase
import com.example.data.local.DisasterEventEntity
import com.example.data.local.PreparednessPreferences
import com.example.data.local.PushAlertEntity
import com.example.domain.model.DisasterCategory
import com.example.domain.model.GeoAlertUtils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "DisasterFcmService"

class DisasterFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val prefs = PreparednessPreferences(applicationContext)
        val pushManager = DisasterPushManager(prefs)
        serviceScope.launch {
            runCatching { pushManager.handleTokenRefresh(token) }
                .onFailure { Log.w(TAG, "Unable to persist refreshed FCM token", it) }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Received FCM message from=${remoteMessage.from} data=${remoteMessage.data}")

        val data = remoteMessage.data
        val notif = remoteMessage.notification

        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: notif?.title?.takeIf { it.isNotBlank() }
            ?: "แจ้งเตือนภัยพิบัติเร่งด่วน (FCM)"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: notif?.body?.takeIf { it.isNotBlank() }
            ?: "ตรวจพบเหตุการณ์ภัยพิบัติใหม่ในพื้นที่เฝ้าระวัง โปรดตรวจสอบรายละเอียด"

        val eventId = data["eventId"]?.takeIf { it.isNotBlank() }
        val categoryName = data["category"]?.uppercase().orEmpty().let { raw ->
            runCatching { DisasterCategory.valueOf(raw).name }.getOrDefault(DisasterCategory.OTHER.name)
        }
        val severityScore = data["severityScore"]?.toIntOrNull()?.coerceIn(1, 5) ?: 4
        val eventLat = data["latitude"]?.toDoubleOrNull()
        val eventLon = data["longitude"]?.toDoubleOrNull()
        val place = data["place"]?.takeIf { it.isNotBlank() } ?: "พื้นที่เฝ้าระวังใกล้เคียง"
        val source = data["source"]?.takeIf { it.isNotBlank() } ?: "Firebase Cloud Messaging"
        val magnitude = data["magnitude"]?.toDoubleOrNull()
        val timestampMs = data["timestampMs"]?.toLongOrNull() ?: System.currentTimeMillis()
        val alertId = remoteMessage.messageId ?: "fcm_${timestampMs}"

        serviceScope.launch {
            val prefs = PreparednessPreferences(applicationContext)
            val db = DisasterDatabase.getInstance(applicationContext)
            val config = prefs.pushAlertConfig.first()

            val distanceKm = if (eventLat != null && eventLon != null) {
                GeoAlertUtils.haversineDistanceKm(
                    config.latitude,
                    config.longitude,
                    eventLat,
                    eventLon
                )
            } else {
                null
            }

            // Broadcast if within immediate radius, or critical severity 5, or a general console broadcast without coordinates
            val withinImmediateRadius = distanceKm == null ||
                distanceKm <= config.radiusKm ||
                severityScore >= 5

            val alertEntity = PushAlertEntity(
                id = alertId,
                eventId = eventId,
                title = title,
                body = body,
                categoryName = categoryName,
                severityScore = severityScore,
                latitude = eventLat,
                longitude = eventLon,
                distanceKm = distanceKm,
                place = place,
                source = source,
                receivedAtMs = timestampMs,
                withinImmediateRadius = withinImmediateRadius,
                isRead = false
            )
            db.pushAlertDao().upsert(alertEntity)

            if (eventLat != null && eventLon != null) {
                val syncedEventId = eventId ?: alertId
                db.disasterEventDao().insertAll(
                    listOf(
                        DisasterEventEntity(
                            id = syncedEventId,
                            title = title,
                            categoryName = categoryName,
                            severityScore = severityScore,
                            magnitude = magnitude,
                            depthKm = null,
                            latitude = eventLat,
                            longitude = eventLon,
                            place = place,
                            timestampMs = timestampMs,
                            source = source,
                            externalUrl = null,
                            tsunamiAlert = data["tsunamiAlert"]?.toBooleanStrictOrNull() ?: false,
                            isHistorical = false
                        )
                    )
                )
            }

            if (config.enabled && withinImmediateRadius) {
                DisasterNotificationHelper.showDisasterNotification(
                    context = applicationContext,
                    notificationId = alertId.hashCode(),
                    title = title,
                    body = body,
                    severityScore = severityScore,
                    distanceKm = distanceKm,
                    eventId = eventId,
                    latitude = eventLat,
                    longitude = eventLon
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
