package com.devdeepsy.nidusflora.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.devdeepsy.nidusflora.MainActivity
import com.devdeepsy.nidusflora.R

object NotificationHelper {

    // ── Channel IDs ──────────────────────────────────────────────────
    const val CHANNEL_WEATHER   = "weather_alerts"
    const val CHANNEL_CALENDAR  = "crop_calendar"
    const val CHANNEL_DISEASE   = "disease_alerts"
    const val CHANNEL_GENERAL   = "general_tips"

    // ── Notification IDs (unique per notification type) ───────────────
    const val NOTIF_WEATHER_TIP = 1001
    const val NOTIF_CALENDAR    = 2001   // base; add task ID offset for uniqueness
    const val NOTIF_DISEASE     = 3001
    const val NOTIF_GENERAL_TIP = 4001

    /** Call once from MainActivity.onCreate() */
    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        listOf(
            Triple(CHANNEL_WEATHER,  "Weather Alerts",       "Daily farming weather tips"),
            Triple(CHANNEL_CALENDAR, "Crop Calendar",        "Watering and task reminders"),
            Triple(CHANNEL_DISEASE,  "Disease Alerts",       "Plant disease scan results"),
            Triple(CHANNEL_GENERAL,  "General Farming Tips", "Seasonal tips and advice"),
        ).forEach { (id, name, desc) ->
            val importance = if (id == CHANNEL_CALENDAR)
                NotificationManager.IMPORTANCE_HIGH   // calendar reminders are time-sensitive
            else
                NotificationManager.IMPORTANCE_DEFAULT

            nm.createNotificationChannel(
                NotificationChannel(id, name, importance).apply { description = desc }
            )
        }
    }

    /**
     * Universal send function. Every feature calls this.
     *
     * @param notifId   Use one of the NOTIF_* constants (add a unique offset for lists).
     * @param channelId Use one of the CHANNEL_* constants.
     * @param title     Notification title.
     * @param body      Notification body text.
     * @param autoCancel  Dismiss on tap (default true).
     */
    fun send(
        context: Context,
        notifId: Int,
        channelId: String,
        title: String,
        body: String,
        autoCancel: Boolean = true
    ) {
        val tapIntent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)   // add a 24dp white icon (see note below)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(autoCancel)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notif)
    }
}