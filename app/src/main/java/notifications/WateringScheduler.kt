package com.devdeepsy.nidusflora.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.devdeepsy.nidusflora.models.CropEntry
import java.util.concurrent.TimeUnit

object WateringScheduler {

    fun schedule(context: Context, crop: CropEntry) {
        val appContext = context.applicationContext
        val nextWateringMs = calculateNextWateringMs(crop) ?: return
        val delayMs = (nextWateringMs - System.currentTimeMillis()).coerceAtLeast(0L)

        val data = workDataOf(
            "crop_name" to crop.cropName,
            "crop_id"   to crop.id
        )

        val request = OneTimeWorkRequestBuilder<WateringWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workTag(crop.id))
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueWorkName(crop.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, cropId: Int) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueWorkName(cropId))
    }

    fun workTag(cropId: Int) = "watering_$cropId"

    private fun uniqueWorkName(cropId: Int) = "watering_work_$cropId"

    private fun calculateNextWateringMs(crop: CropEntry): Long? {
        if (crop.wateringIntervalDays <= 0) return null
        val baseMs = crop.lastWateredDate ?: crop.sowingDate
        val intervalMs = TimeUnit.DAYS.toMillis(crop.wateringIntervalDays.toLong())
        return baseMs + intervalMs
    }
}