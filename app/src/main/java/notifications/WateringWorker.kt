package com.deepgamer.agriexpert.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.deepgamer.agriexpert.notifications.NotificationHelper

class WateringWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val cropName = inputData.getString("crop_name") ?: "Your crop"
        val cropId   = inputData.getInt("crop_id", 0)

        NotificationHelper.send(
            context   = context,
            notifId   = NotificationHelper.NOTIF_CALENDAR + cropId,
            channelId = NotificationHelper.CHANNEL_CALENDAR,
            title     = "💧 Time to water!",
            body      = "$cropName needs watering today."
        )

        return Result.success()
    }
}