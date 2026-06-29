package com.devdeepsy.nidusflora.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devdeepsy.nidusflora.workers.WeatherWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Re-schedule all periodic workers after device reboot
        WeatherWorker.schedule(context)
    }
}