package com.devdeepsy.nidusflora.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.devdeepsy.nidusflora.MainActivity
import com.devdeepsy.nidusflora.R

object NotificationHelper {

    const val CHANNEL_WEATHER   = "weather_alerts"
    const val CHANNEL_CALENDAR  = "crop_calendar"
    const val CHANNEL_DISEASE   = "disease_alerts"
    const val CHANNEL_GENERAL   = "general_tips"

    const val NOTIF_WEATHER_TIP = 1001
    const val NOTIF_DISEASE     = 3001
    const val NOTIF_GENERAL_TIP = 4001
    private const val NOTIF_CALENDAR_BASE = 100_000

    fun calendarNotifId(cropId: Int): Int = NOTIF_CALENDAR_BASE + (cropId and 0x7FFF_FFFF)

    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return

        val channels = listOf(
            Triple(CHANNEL_WEATHER,  "Weather Alerts",       "Daily farming weather tips"),
            Triple(CHANNEL_CALENDAR, "Crop Calendar",        "Watering and task reminders"),
            Triple(CHANNEL_DISEASE,  "Disease Alerts",       "Plant disease scan results"),
            Triple(CHANNEL_GENERAL,  "General Farming Tips", "Seasonal tips and advice"),
        )

        for ((id, name, desc) in channels) {
            val importance = if (id == CHANNEL_CALENDAR) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply { description = desc }
            )
        }
    }

    fun send(
        context: Context,
        notifId: Int,
        channelId: String,
        title: String,
        body: String,
        autoCancel: Boolean = true
    ) {
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val tapIntent = PendingIntent.getActivity(
            appContext,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(autoCancel)
            .build()

        try {
            nm.notify(notifId, notif)
        } catch (_: SecurityException) {
            // Silently ignore if revoked at OS/OEM level
        }
    }
}