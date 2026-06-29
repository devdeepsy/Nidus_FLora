package com.devdeepsy.nidusflora.notifications

import android.content.Context
import androidx.work.*
import com.devdeepsy.nidusflora.models.CropEntry
import java.util.concurrent.TimeUnit

object WateringScheduler {

    fun schedule(context: Context, crop: CropEntry) {
        cancel(context, crop.id)

        val nextWateringMs = calculateNextWateringMs(crop) ?: return
        val delayMs = nextWateringMs - System.currentTimeMillis()
        if (delayMs <= 0) return

        val data = workDataOf(
            "crop_name" to crop.cropName,
            "crop_id"   to crop.id          // Int, matches WateringWorker
        )

        val request = OneTimeWorkRequestBuilder<WateringWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workTag(crop.id))
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(context: Context, cropId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag(workTag(cropId))
    }

    fun workTag(cropId: Int) = "watering_$cropId"

    private fun calculateNextWateringMs(crop: CropEntry): Long? {
        val baseMs      = crop.lastWateredDate ?: crop.sowingDate
        val intervalMs  = crop.wateringIntervalDays * 24L * 60 * 60 * 1000
        return baseMs + intervalMs
    }
}