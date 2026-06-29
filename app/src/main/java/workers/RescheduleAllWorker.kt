package com.devdeepsy.nidusflora.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Fired by BootReceiver. Reads all future tasks from Room and re-schedules their workers.
 * Wire this to your Room DAO — replace TODO with your actual DAO call.
 */
class RescheduleAllWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // TODO: val db = AppDatabase.getInstance(applicationContext)
        // val futureTasks = db.taskDao().getFutureTasks(System.currentTimeMillis())
        // futureTasks.forEach { task ->
        //     val delay = task.reminderTimeMillis - System.currentTimeMillis()
        //     CalendarReminderWorker.schedule(applicationContext, task.id, task.name, task.notes, delay)
        // }
        return Result.success()
    }
}