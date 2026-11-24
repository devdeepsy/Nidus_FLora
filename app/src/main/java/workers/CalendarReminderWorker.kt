package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.*
import com.devdeepsy.nidusflora.notifications.NotificationHelper
import java.util.concurrent.TimeUnit

class CalendarReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val taskName = inputData.getString(KEY_TASK_NAME)?.takeIf { it.isNotBlank() } ?: "Your crop task"
            val taskNotes = inputData.getString(KEY_TASK_NOTES).orEmpty()
            val taskId = inputData.getInt(KEY_TASK_ID, 0)

            NotificationHelper.createAllChannels(applicationContext)
            NotificationHelper.send(
                context = applicationContext,
                notifId = NotificationHelper.calendarNotifId(taskId),
                channelId = NotificationHelper.CHANNEL_CALENDAR,
                title = "Reminder: $taskName",
                body = taskNotes.ifBlank { "Time to tend to your crops!" }
            )
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_NAME = "task_name"
        const val KEY_TASK_NOTES = "task_notes"

        fun workTag(taskId: Int) = "calendar_task_$taskId"

        private fun uniqueWorkName(taskId: Int) = "calendar_task_work_$taskId"

        fun schedule(
            context: Context,
            taskId: Int,
            taskName: String,
            taskNotes: String,
            delayMillis: Long
        ) {
            if (delayMillis <= 0L) return

            val data = workDataOf(
                KEY_TASK_ID to taskId,
                KEY_TASK_NAME to taskName,
                KEY_TASK_NOTES to taskNotes
            )
            val request = OneTimeWorkRequestBuilder<CalendarReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(workTag(taskId))
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(taskId),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, taskId: Int) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(uniqueWorkName(taskId))
        }

        fun scheduleAll(context: Context) {
            val request = OneTimeWorkRequestBuilder<RescheduleAllWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "reschedule_all_calendar_tasks",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}