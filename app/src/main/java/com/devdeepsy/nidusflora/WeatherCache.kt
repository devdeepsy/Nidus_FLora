package com.devdeepsy.nidusflora

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin SharedPreferences cache for weather data.
 * TTL = 30 minutes. WeatherFragment checks this before hitting the network.
 */
class WeatherCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_JSON      = "cached_json"
        private const val KEY_TIMESTAMP = "cached_at"
        private const val KEY_TIP       = "cached_tip"

        private const val KEY_CITY      = "cached_city"
        private const val TTL_MS        = 30 * 60 * 1000L   // 30 minutes
    }

    fun isFresh(): Boolean {
        val cachedAt = prefs.getLong(KEY_TIMESTAMP, 0L)
        return System.currentTimeMillis() - cachedAt < TTL_MS
    }

    fun save(json: String, tip: String,city: String) {
        prefs.edit()
            .putString(KEY_JSON, json)
            .putString(KEY_TIP, tip)
            .putString(KEY_CITY, city)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getCachedCity(): String? = prefs.getString(KEY_CITY, null)

    fun getCachedJson(): String? = prefs.getString(KEY_JSON, null)

    fun getCachedTip(): String? = prefs.getString(KEY_TIP, null)


}