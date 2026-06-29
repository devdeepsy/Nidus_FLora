package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.*
import com.devdeepsy.nidusflora.notifications.NotificationHelper
import com.devdeepsy.nidusflora.WeatherCache
import java.util.concurrent.TimeUnit

/**
 * Runs once a day. Reads the cached weather and fires a farming-tip notification.
 * Does NOT fetch live data — WeatherFragment owns fetching; this just reads the cache.
 */
class WeatherWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val cache = WeatherCache(applicationContext)
        val tip = cache.getCachedTip() ?: return Result.success() // nothing cached yet

        NotificationHelper.send(
            context    = applicationContext,
            notifId    = NotificationHelper.NOTIF_WEATHER_TIP,
            channelId  = NotificationHelper.CHANNEL_WEATHER,
            title      = "Today's farming tip",
            body       = tip
        )
        return Result.success()
    }

    companion object {
        private const val WORK_TAG = "weather_daily_tip"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }

        /** Delay until 7:00 AM local time */
        private fun calculateInitialDelay(): Long {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 7)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (timeInMillis <= now) add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis - now
        }
    }
}