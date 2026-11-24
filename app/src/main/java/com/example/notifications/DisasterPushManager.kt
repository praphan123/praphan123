package com.example.notifications

import android.util.Log
import com.example.data.local.PreparednessPreferences
import com.example.data.remote.DisasterNetworkModule
import com.example.data.remote.FcmServerResponse
import com.example.data.remote.RegisterPushLocationRequest
import com.example.data.remote.SendTestAlertRequest
import com.example.domain.model.GeoAlertUtils
import com.example.domain.model.PushAlertConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "DisasterPushManager"

class DisasterPushManager(
    private val preferences: PreparednessPreferences
) {
    suspend fun syncSubscription(
        enabled: Boolean,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        radiusKm: Double,
        serverUrlOverride: String? = null
    ): Result<PushAlertConfig> {
        return try {
            val currentConfig = preferences.pushAlertConfig.first()
            val resolvedServerUrl = (serverUrlOverride ?: currentConfig.fcmServerUrl).trim()
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = enabled

            val token = if (enabled) {
                withTimeout(12_000L) {
                    messaging.token.awaitResult()
                }
            } else {
                currentConfig.fcmToken
            }

            val targetTopics = if (enabled) {
                GeoAlertUtils.buildLocationTopics(latitude, longitude)
            } else {
                emptySet()
            }

            val toUnsubscribe = currentConfig.subscribedTopics - targetTopics
            val toSubscribe = targetTopics - currentConfig.subscribedTopics

            toUnsubscribe.forEach { topic ->
                runCatching {
                    withTimeout(8_000L) {
                        messaging.unsubscribeFromTopic(topic).awaitResult()
                    }
                }.onFailure { Log.w(TAG, "Failed to unsubscribe topic $topic", it) }
            }

            toSubscribe.forEach { topic ->
                withTimeout(8_000L) {
                    messaging.subscribeToTopic(topic).awaitResult()
                }
            }

            val updated = PushAlertConfig(
                enabled = enabled,
                fcmToken = token,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
                radiusKm = radiusKm,
                subscribedTopics = targetTopics,
                lastSyncedAtMs = System.currentTimeMillis(),
                fcmServerUrl = resolvedServerUrl
            )
            preferences.savePushAlertConfig(updated)

            if (enabled && token != null && isValidHttpUrl(resolvedServerUrl)) {
                runCatching {
                    val api = DisasterNetworkModule.createFcmServerApi(resolvedServerUrl)
                    api.registerLocation(
                        RegisterPushLocationRequest(
                            fcmToken = token,
                            latitude = latitude,
                            longitude = longitude,
                            locationLabel = locationLabel,
                            radiusKm = radiusKm,
                            topics = targetTopics.toList()
                        )
                    )
                }.onFailure {
                    Log.w(TAG, "FCM server registration failed for $resolvedServerUrl", it)
                }
            }

            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync FCM subscription", e)
            Result.failure(e)
        }
    }

    suspend fun sendTestPushViaServer(serverUrl: String): Result<FcmServerResponse> {
        val trimmed = serverUrl.trim()
        if (!isValidHttpUrl(trimmed)) {
            return Result.failure(IllegalArgumentException("กรุณาระบุ URL เซิร์ฟเวอร์ขึ้นต้นด้วย https://"))
        }
        return try {
            val current = preferences.pushAlertConfig.first()
            val token = current.fcmToken ?: withTimeout(10_000L) {
                FirebaseMessaging.getInstance().token.awaitResult()
            }
            val api = DisasterNetworkModule.createFcmServerApi(trimmed)
            val response = api.sendTestAlert(
                SendTestAlertRequest(
                    fcmToken = token,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    locationLabel = current.locationLabel
                )
            )
            if (response.ok) Result.success(response)
            else Result.failure(IllegalStateException(response.error ?: "เซิร์ฟเวอร์ตอบกลับไม่สำเร็จ"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sendTestPushViaServer failed", e)
            Result.failure(e)
        }
    }

    suspend fun refreshTokenAndTopics(): Result<PushAlertConfig> {
        val current = preferences.pushAlertConfig.first()
        return syncSubscription(
            enabled = current.enabled,
            latitude = current.latitude,
            longitude = current.longitude,
            locationLabel = current.locationLabel,
            radiusKm = current.radiusKm,
            serverUrlOverride = current.fcmServerUrl
        )
    }

    suspend fun handleTokenRefresh(newToken: String) {
        preferences.saveFcmToken(newToken)
        val current = preferences.pushAlertConfig.first()
        if (current.enabled) {
            val topics = GeoAlertUtils.buildLocationTopics(current.latitude, current.longitude)
            val messaging = FirebaseMessaging.getInstance()
            topics.forEach { topic ->
                runCatching {
                    withTimeout(8_000L) {
                        messaging.subscribeToTopic(topic).awaitResult()
                    }
                }.onFailure { Log.w(TAG, "Failed to re-subscribe topic $topic on token refresh", it) }
            }
            val updated = current.copy(
                fcmToken = newToken,
                subscribedTopics = topics,
                lastSyncedAtMs = System.currentTimeMillis()
            )
            preferences.savePushAlertConfig(updated)
            if (isValidHttpUrl(updated.fcmServerUrl)) {
                runCatching {
                    DisasterNetworkModule.createFcmServerApi(updated.fcmServerUrl).registerLocation(
                        RegisterPushLocationRequest(
                            fcmToken = newToken,
                            latitude = updated.latitude,
                            longitude = updated.longitude,
                            locationLabel = updated.locationLabel,
                            radiusKm = updated.radiusKm,
                            topics = topics.toList()
                        )
                    )
                }.onFailure { Log.w(TAG, "Server sync failed on token refresh", it) }
            }
        }
    }

    companion object {
        fun isValidHttpUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase()
            return trimmed.startsWith("https://") || trimmed.startsWith("http://")
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            cont.resume(task.result as T)
        } else {
            cont.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed without exception")
            )
        }
    }
}
