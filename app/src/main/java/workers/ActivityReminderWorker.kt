package com.deepgamer.agriexpert.workers

import android.content.Context
import androidx.work.*
import com.deepgamer.agriexpert.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class ActivityReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val crop         = inputData.getString(KEY_CROP)     ?: "Your crop"
        val activityType = inputData.getString(KEY_TYPE)     ?: "farm activity"
        val notes        = inputData.getString(KEY_NOTES)    ?: ""
        val activityId   = inputData.getInt(KEY_ID, 0)

        NotificationHelper.send(
            context   = applicationContext,
            notifId   = NotificationHelper.NOTIF_CALENDAR + activityId + 5000,
            channelId = NotificationHelper.CHANNEL_CALENDAR,
            title     = "📋 Reminder: $activityType",
            body      = "$crop${if (notes.isNotBlank()) " — $notes" else ""}"
        )
        return Result.success()
    }

    companion object {
        const val KEY_CROP  = "activity_crop"
        const val KEY_TYPE  = "activity_type"
        const val KEY_NOTES = "activity_notes"
        const val KEY_ID    = "activity_id"

        fun schedule(
            context: Context,
            activityId: Int,
            crop: String,
            activityType: String,
            notes: String,
            reminderTimeMs: Long
        ) {
            val delayMs = reminderTimeMs - System.currentTimeMillis()
            if (delayMs <= 0) return

            val data = workDataOf(
                KEY_CROP  to crop,
                KEY_TYPE  to activityType,
                KEY_NOTES to notes,
                KEY_ID    to activityId
            )

            val request = OneTimeWorkRequestBuilder<ActivityReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(workTag(activityId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workTag(activityId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, activityId: Int) {
            WorkManager.getInstance(context).cancelAllWorkByTag(workTag(activityId))
        }

        fun workTag(activityId: Int) = "activity_reminder_$activityId"
    }
}