package com.devdeepsy.nidusflora.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devdeepsy.nidusflora.workers.WeatherWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        NotificationHelper.createAllChannels(appContext)
        WeatherWorker.schedule(appContext)
    }
}