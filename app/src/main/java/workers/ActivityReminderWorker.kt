package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.*
import com.devdeepsy.nidusflora.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class ActivityReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val crop = inputData.getString(KEY_CROP)?.takeIf { it.isNotBlank() } ?: "Your crop"
            val activityType = inputData.getString(KEY_TYPE)?.takeIf { it.isNotBlank() } ?: "farm activity"
            val notes = inputData.getString(KEY_NOTES).orEmpty()
            val activityId = inputData.getInt(KEY_ID, 0)

            NotificationHelper.createAllChannels(applicationContext)
            NotificationHelper.send(
                context = applicationContext,
                notifId = 300_000 + (activityId and 0x7FFF_FFFF),
                channelId = NotificationHelper.CHANNEL_CALENDAR,
                title = "📋 Reminder: $activityType",
                body = if (notes.isNotBlank()) "$crop — $notes" else crop
            )
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_CROP = "activity_crop"
        const val KEY_TYPE = "activity_type"
        const val KEY_NOTES = "activity_notes"
        const val KEY_ID = "activity_id"

        fun schedule(
            context: Context,
            activityId: Int,
            crop: String,
            activityType: String,
            notes: String,
            reminderTimeMs: Long
        ) {
            val delayMs = reminderTimeMs - System.currentTimeMillis()
            if (delayMs <= 0L) return

            val data = workDataOf(
                KEY_CROP to crop,
                KEY_TYPE to activityType,
                KEY_NOTES to notes,
                KEY_ID to activityId
            )

            val request = OneTimeWorkRequestBuilder<ActivityReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(workTag(activityId))
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(activityId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, activityId: Int) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(activityId))
        }

        fun workTag(activityId: Int) = "activity_reminder_$activityId"

        private fun uniqueWorkName(activityId: Int) = "activity_reminder_work_$activityId"
    }
}