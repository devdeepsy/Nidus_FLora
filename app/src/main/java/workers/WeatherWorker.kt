package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.*
import com.devdeepsy.nidusflora.WeatherCache
import com.devdeepsy.nidusflora.notifications.NotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WeatherWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val cache = WeatherCache(applicationContext)
            val tip = cache.getCachedTip()?.takeIf { it.isNotBlank() } ?: return Result.success()

            NotificationHelper.createAllChannels(applicationContext)
            NotificationHelper.send(
                context = applicationContext,
                notifId = NotificationHelper.NOTIF_WEATHER_TIP,
                channelId = NotificationHelper.CHANNEL_WEATHER,
                title = "Today's farming tip",
                body = tip
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_TAG = "weather_daily_tip"
        private const val UNIQUE_WORK_NAME = "weather_daily_tip_work"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private fun calculateInitialDelay(): Long {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return (cal.timeInMillis - now).coerceAtLeast(0L)
        }
    }
}