package com.deepgamer.agriexpert.workers

import android.content.Context
import androidx.work.*
import com.deepgamer.agriexpert.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * One-shot worker per calendar task. Schedule one when a task is created/updated.
 * CalendarFragment should call schedule() / cancel() directly.
 */
class CalendarReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val taskName  = inputData.getString(KEY_TASK_NAME)  ?: "Your crop task"
        val taskNotes = inputData.getString(KEY_TASK_NOTES) ?: ""
        val taskId    = inputData.getInt(KEY_TASK_ID, 0)

        NotificationHelper.send(
            context   = applicationContext,
            notifId   = NotificationHelper.NOTIF_CALENDAR + taskId,
            channelId = NotificationHelper.CHANNEL_CALENDAR,
            title     = "Reminder: $taskName",
            body      = taskNotes.ifBlank { "Time to tend to your crops!" }
        )
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID    = "task_id"
        const val KEY_TASK_NAME  = "task_name"
        const val KEY_TASK_NOTES = "task_notes"

        fun workTag(taskId: Int) = "calendar_task_$taskId"

        /**
         * Schedule a one-shot reminder.
         * @param delayMillis how many ms from now to fire.
         */
        fun schedule(context: Context, taskId: Int, taskName: String, taskNotes: String, delayMillis: Long) {
            if (delayMillis <= 0) return

            val data = workDataOf(
                KEY_TASK_ID    to taskId,
                KEY_TASK_NAME  to taskName,
                KEY_TASK_NOTES to taskNotes
            )
            val request = OneTimeWorkRequestBuilder<CalendarReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(workTag(taskId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workTag(taskId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, taskId: Int) {
            WorkManager.getInstance(context).cancelAllWorkByTag(workTag(taskId))
        }

        /** Called by BootReceiver — re-read all pending tasks from Room and reschedule */
        fun scheduleAll(context: Context) {
            // Room query happens in a coroutine; launch from a one-shot worker
            val request = OneTimeWorkRequestBuilder<RescheduleAllWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}