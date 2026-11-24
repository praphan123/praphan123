package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object DisasterNotificationHelper {
    const val CHANNEL_CRITICAL = "disaster_critical_alerts"
    const val CHANNEL_ADVISORY = "disaster_general_updates"

    const val EXTRA_ALERT_EVENT_ID = "extra_alert_event_id"
    const val EXTRA_OPEN_PUSH_INBOX = "extra_open_push_inbox"
    const val EXTRA_ALERT_LAT = "extra_alert_lat"
    const val EXTRA_ALERT_LON = "extra_alert_lon"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val criticalChannel = NotificationChannel(
            CHANNEL_CRITICAL,
            context.getString(R.string.fcm_channel_critical_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.fcm_channel_critical_desc)
            enableVibration(true)
            enableLights(true)
        }

        val advisoryChannel = NotificationChannel(
            CHANNEL_ADVISORY,
            context.getString(R.string.fcm_channel_advisory_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.fcm_channel_advisory_desc)
        }

        manager.createNotificationChannel(criticalChannel)
        manager.createNotificationChannel(advisoryChannel)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val osGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return osGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showDisasterNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        severityScore: Int,
        distanceKm: Double? = null,
        eventId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (!areNotificationsEnabled(context)) return
        createNotificationChannels(context)

        val channelId = if (severityScore >= 3) CHANNEL_CRITICAL else CHANNEL_ADVISORY
        val priority = if (severityScore >= 3) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val fullBody = buildString {
            if (distanceKm != null) {
                append("ห่างจากพื้นที่ของคุณ ~${"%.1f".format(distanceKm)} กม.\n")
            }
            append(body)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PUSH_INBOX, true)
            eventId?.let { putExtra(EXTRA_ALERT_EVENT_ID, it) }
            latitude?.let { putExtra(EXTRA_ALERT_LAT, it) }
            longitude?.let { putExtra(EXTRA_ALERT_LON, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_disaster_alert)
            .setColor(ContextCompat.getColor(context, R.color.alert_amber_accent))
            .setContentTitle(title)
            .setContentText(fullBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullBody))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}
