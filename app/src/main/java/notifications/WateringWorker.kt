package com.devdeepsy.nidusflora.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class WateringWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val cropName = inputData.getString("crop_name")?.takeIf { it.isNotBlank() } ?: "Your crop"
        val cropId = inputData.getInt("crop_id", 0)

        NotificationHelper.createAllChannels(applicationContext)
        NotificationHelper.send(
            context   = applicationContext,
            notifId   = NotificationHelper.calendarNotifId(cropId),
            channelId = NotificationHelper.CHANNEL_CALENDAR,
            title     = "💧 Time to water!",
            body      = "$cropName needs watering today."
        )

        return Result.success()
    }
}