package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RescheduleAllWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Wire to Room DAO when task entity/DAO is finalized:
            // val now = System.currentTimeMillis()
            // val futureTasks = AppDatabase.getInstance(applicationContext).taskDao().getFutureTasks(now)
            // futureTasks.forEach { task ->
            //     val delay = task.reminderTimeMillis - System.currentTimeMillis()
            //     if (delay > 0L) {
            //         CalendarReminderWorker.schedule(applicationContext, task.id, task.name, task.notes, delay)
            //     }
            // }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}