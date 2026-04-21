package com.deepgamer.agriexpert

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.deepgamer.agriexpert.workers.WeatherWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WeatherFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var textLocation: TextView
    private lateinit var textTemperature: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var hourlyContainer: LinearLayout
    private lateinit var weeklyContainer: LinearLayout
    private val timeHandler = Handler(Looper.getMainLooper())
    private lateinit var timeRunnable: Runnable
    private lateinit var textDayTime: TextView
    private lateinit var iconCondition: ImageView

    private lateinit var textHigh: TextView
    private lateinit var textLow: TextView
    private lateinit var textWind: TextView
    private lateinit var textHumidity: TextView
    private lateinit var textFeelsLike: TextView
    private lateinit var textUV: TextView
    private lateinit var textFarmingTip: TextView

    private lateinit var loadingOverlay: FrameLayout
    private lateinit var ivLoadingIcon: ImageView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var cache: WeatherCache

    // Holds the city name from geocoding so parseAndDisplay can use it
    private var currentCityName: String = "Your Location"

    private val apiKey = "qSNDaR81BU7wRKDXCpdD4AATpq8dv5zo"
    private var isFragmentAttached = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_weather, container, false)

        val scrollView = view.findViewById<ScrollView>(R.id.scroll_weather)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val extra = (16 * resources.displayMetrics.density).toInt()
            v.updatePadding(bottom = navBarInset + extra)
            insets
        }
        scrollView.requestLayout()

        textLocation  = view.findViewById(R.id.tv_location)
        textTemperature = view.findViewById(R.id.tv_temperature)
        textDayTime   = view.findViewById(R.id.tv_day_time)
        iconCondition = view.findViewById(R.id.iv_condition_icon)

        textHigh      = view.findViewById(R.id.card_high)
        textLow       = view.findViewById(R.id.card_low)
        textWind      = view.findViewById(R.id.card_wind)
        textHumidity  = view.findViewById(R.id.card_Humidity)
        textFeelsLike = view.findViewById(R.id.card_feels_like)
        textUV        = view.findViewById(R.id.card_uv)
        textFarmingTip = view.findViewById(R.id.tv_farming_tip)

        hourlyContainer = view.findViewById(R.id.hourlyForecastContainer)
        weeklyContainer = view.findViewById(R.id.weeklyForecastContainer)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        progressBar   = view.findViewById(R.id.progressBar)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        ivLoadingIcon = view.findViewById(R.id.iv_loading_icon)
        swipeRefresh  = view.findViewById(R.id.swipeRefresh)

        cache = WeatherCache(requireContext())
        WeatherWorker.schedule(requireContext())

        swipeRefresh.setOnRefreshListener { fetchWeather(forceRefresh = true) }

        timeRunnable = object : Runnable {
            override fun run() {
                if (isAdded && isFragmentAttached) {
                    textDayTime.text = SimpleDateFormat("EEEE, h:mm a", Locale.getDefault()).format(Date())
                    val now = System.currentTimeMillis()
                    timeHandler.postDelayed(this, 60000 - (now % 60000))
                }
            }
        }
        timeHandler.post(timeRunnable)

        val rotate = RotateAnimation(0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        ivLoadingIcon.startAnimation(rotate)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentAttached = true
        checkLocationPermission()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentAttached = false
        timeHandler.removeCallbacks(timeRunnable)
    }

    private fun checkLocationPermission() {
        if (!isFragmentAttached) return
        when {
            ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> fetchWeather()

            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                showPermissionRationaleDialog()

            else -> @Suppress("DEPRECATION") ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
            )
        }
    }

    private fun fetchWeather(forceRefresh: Boolean = false) {
        if (!isFragmentAttached) return

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        // Serve from cache if fresh and not a manual pull-to-refresh
        if (!forceRefresh && cache.isFresh()) {
            val cachedJson = cache.getCachedJson()
            if (cachedJson != null) {
                val cachedCity = cache.getCachedCity() ?: "Your Location"
                parseAndDisplay(cachedJson)
                swipeRefresh.isRefreshing = false
                return
            }
        }

        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            safeRunOnUI { showLocationServicesDialog() }
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) handleLocation(location)
                else fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) handleLocation(lastLoc)
                    else safeRunOnUI { hideLoading(); safeToast("Unable to get location.") }
                }
            }
            .addOnFailureListener { e ->
                safeRunOnUI { hideLoading(); safeToast("Location error: ${e.message}") }
            }
    }

    private fun handleLocation(location: Location) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        @Suppress("DEPRECATION")
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        // Store city name in the field so parseAndDisplay can access it
        currentCityName = addresses?.getOrNull(0)?.locality ?: "Your Location"
        fetchWeatherFromAPI(location.latitude, location.longitude)
    }

    private fun fetchWeatherFromAPI(lat: Double, lon: Double) {
        if (!isFragmentAttached) return

        val url = "https://api.tomorrow.io/v4/weather/forecast?location=$lat,$lon&timesteps=1h,1d&units=metric&apikey=$apiKey"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isFragmentAttached) return
                safeRunOnUI { hideLoading(); safeToast("Network error: ${e.message}") }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isFragmentAttached) return
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e("WeatherFragment", "Empty API response")
                    safeRunOnUI { hideLoading(); safeToast("Empty response from weather service.") }
                    return
                }
                // Save raw JSON to cache, then display it
                val tip = extractTipFromJson(responseBody)
                cache.save(responseBody, tip ?: "", city = currentCityName)
                parseAndDisplay(responseBody)
            }
        })
    }

    /**
     * Parses the raw JSON string and updates all UI views.
     * Uses [currentCityName] for the location label.
     * Called from both live fetch and cache hit paths.
     */
    private fun parseAndDisplay(jsonString: String) {
        try {
            val root = JSONObject(jsonString)          // ← use a different name, no shadowing
            val timelines = root.getJSONObject("timelines")

            val hourlyArray = if (timelines.has("hourly")) timelines.getJSONArray("hourly") else JSONArray()
            val dailyArray  = if (timelines.has("daily"))  timelines.getJSONArray("daily")  else JSONArray()

            if (dailyArray.length() == 0 || hourlyArray.length() == 0) {
                safeRunOnUI { hideLoading(); safeToast("Weather data unavailable.") }
                return
            }

            val dailyValues   = dailyArray.getJSONObject(0).getJSONObject("values")
            val minTemp       = dailyValues.getDouble("temperatureMin")
            val maxTemp       = dailyValues.getDouble("temperatureMax")

            val currentValues = hourlyArray.getJSONObject(0).getJSONObject("values")
            val currentTemp   = currentValues.getDouble("temperature")
            val humidityVal   = currentValues.optDouble("humidity", 0.0)
            val windSpeed     = currentValues.optDouble("windSpeed", 0.0)
            val feelsLike     = currentValues.optDouble("temperatureApparent", currentTemp)
            val condition     = currentValues.optString("weatherCode", "1000")
            val uvIndex       = currentValues.optDouble("uvIndex", 0.0)

            val tip = getFarmingTip(currentTemp, humidityVal, windSpeed * 3.6, uvIndex)

            safeRunOnUI {
                if (!isFragmentAttached) return@safeRunOnUI

                textTemperature.text  = "%.1f°C".format(currentTemp)
                iconCondition.setImageResource(getWeatherIcon(condition))
                textLocation.text     = currentCityName
                textDayTime.text      = SimpleDateFormat("EEEE, h:mm a", Locale.getDefault()).format(Date())

                textHigh.text         = "High\n%.1f°C".format(maxTemp)
                textLow.text          = "Low\n%.1f°C".format(minTemp)
                textHumidity.text     = "Humidity\n%.0f%%".format(humidityVal)
                textWind.text         = "Wind\n%.1f km/h".format(windSpeed * 3.6)
                textFeelsLike.text    = "Feels Like\n%.1f°C".format(feelsLike)
                textUV.text           = "UV Index\n${uvIndex.toInt()}"
                textFarmingTip.text   = tip

                populateHourlyFromJson(hourlyArray)
                populateWeeklyFromJson(dailyArray)

                swipeRefresh.isRefreshing = false
                hideLoading()
            }
        } catch (e: Exception) {
            Log.e("WeatherFragment", "Parse error: ${e.message}")
            safeRunOnUI { hideLoading(); safeToast("Error reading weather data.") }
        }
    }

    /**
     * Extracts a farming tip from raw JSON without touching the UI.
     * Used by [fetchWeatherFromAPI] to save the tip to cache before display.
     */
    private fun extractTipFromJson(jsonString: String): String? {
        return try {
            val root      = JSONObject(jsonString)
            val hourly    = root.getJSONObject("timelines").getJSONArray("hourly")
            val v         = hourly.getJSONObject(0).getJSONObject("values")
            getFarmingTip(
                temp     = v.getDouble("temperature"),
                humidity = v.optDouble("humidity", 0.0),
                windKmh  = v.optDouble("windSpeed", 0.0) * 3.6,
                uvIndex  = v.optDouble("uvIndex", 0.0)
            )
        } catch (e: Exception) { null }
    }

    // Rain probability removed — the API always returns 0 for free-tier accounts
    private fun getFarmingTip(temp: Double, humidity: Double, windKmh: Double, uvIndex: Double): String {
        return when {
            temp >= 38                   -> "🌡️ Extreme heat — water plants early morning or after sunset."
            temp >= 32 && humidity < 40  -> "☀️ Hot and dry — water deeply, mulch to retain soil moisture."
            uvIndex >= 8                 -> "🕶️ Very high UV — avoid the garden between 11am–3pm."
            windKmh >= 30               -> "💨 Strong winds — stake tall plants, skip pesticide spraying."
            humidity >= 85               -> "💧 High humidity — watch for fungal disease, ensure airflow."
            temp in 18.0..28.0           -> "✅ Great growing conditions — ideal for planting or pruning."
            temp < 10                    -> "🥶 Cold weather — cover frost-sensitive plants tonight."
            else                         -> "🌱 Moderate conditions — a good day for regular maintenance."
        }
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!isFragmentAttached || requestCode != 1001) return
        when {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                fetchWeather()
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
                showPermissionRationaleDialog()
            else ->
                showPermanentlyDeniedDialog()
        }
    }

    private fun showPermissionRationaleDialog() {
        if (!isFragmentAttached) return
        AlertDialog.Builder(requireContext())
            .setTitle("Location Permission Needed")
            .setMessage("Farm Expert needs your location to show accurate local weather for your crops.")
            .setPositiveButton("Allow") { _, _ ->
                @Suppress("DEPRECATION")
                ActivityCompat.requestPermissions(requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            }
            .setNegativeButton("Not Now") { dialog, _ -> dialog.dismiss(); hideLoading() }
            .setCancelable(false).show()
    }

    private fun showPermanentlyDeniedDialog() {
        if (!isFragmentAttached) return
        AlertDialog.Builder(requireContext())
            .setTitle("Permission Required")
            .setMessage("Location permission was denied. Please enable it in App Settings to get weather for your area.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireActivity().packageName, null)))
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss(); hideLoading() }
            .setCancelable(false).show()
    }

    private fun showLocationServicesDialog() {
        if (!isFragmentAttached) return
        AlertDialog.Builder(requireContext())
            .setTitle("Location is Off")
            .setMessage("Please turn on location services so Farm Expert can show accurate weather for your area.")
            .setPositiveButton("Enable Location") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss(); hideLoading() }
            .setCancelable(false).show()
    }

    override fun onPause() {
        super.onPause()
        timeHandler.removeCallbacks(timeRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (isFragmentAttached &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fetchWeather()
        }
    }

    private fun safeRunOnUI(action: () -> Unit) {
        activity?.runOnUiThread { if (isAdded && isFragmentAttached) action() }
    }

    private fun hideLoading() {
        safeRunOnUI {
            loadingOverlay.animate().alpha(0f).setDuration(400).withEndAction {
                loadingOverlay.visibility = View.GONE
                ivLoadingIcon.clearAnimation()
            }.start()
        }
    }

    private fun safeToast(message: String) {
        activity?.runOnUiThread {
            if (isAdded && isFragmentAttached)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getWeatherIcon(conditionCode: String): Int {
        return when (conditionCode.toIntOrNull() ?: -1) {
            1000  -> R.drawable.sunny
            10001 -> R.drawable.night_clear
            1100  -> R.drawable.partly_cloudy
            1001, 1101             -> R.drawable.cloudy
            2000                   -> R.drawable.fog
            4000, 4001, 4200, 4201 -> R.drawable.rainy
            5000, 5001, 5100, 5101 -> R.drawable.snow
            6000, 7000             -> R.drawable.thunder
            else  -> R.drawable.sunny
        }
    }

    private fun parseTimeToHourLabel(timeStr: String, isNow: Boolean): String {
        if (isNow) return "Now"
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdfIn.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdfIn.parse(timeStr)
            SimpleDateFormat("h a", Locale.getDefault()).format(date ?: Date())
        } catch (e: Exception) { "--" }
    }

    private fun parseTimeToDayLabel(timeStr: String): String {
        return try {
            val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdfIn.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdfIn.parse(timeStr)
            SimpleDateFormat("EEE", Locale.getDefault()).format(date ?: Date()).uppercase(Locale.getDefault())
        } catch (e: Exception) { "DAY" }
    }

    private fun populateHourlyFromJson(hourlyArray: JSONArray) {
        safeRunOnUI {
            if (!isFragmentAttached) return@safeRunOnUI
            hourlyContainer.removeAllViews()
            for (i in 0 until minOf(24, hourlyArray.length())) {
                val hourObj = hourlyArray.getJSONObject(i)
                val values  = hourObj.optJSONObject("values")
                val temp    = values?.optDouble("temperature", Double.NaN) ?: Double.NaN

                val item   = layoutInflater.inflate(R.layout.hour_item, hourlyContainer, false)
                item.findViewById<TextView>(R.id.tv_hour_time).text =
                    parseTimeToHourLabel(hourObj.optString("time", ""), i == 0)
                item.findViewById<TextView>(R.id.tv_hour_temp).text =
                    if (!temp.isNaN()) "%.0f°C".format(temp) else "--"
                item.findViewById<ImageView>(R.id.iv_hour_icon)
                    .setImageResource(getWeatherIcon(values?.optString("weatherCode", "-1") ?: "-1"))
                hourlyContainer.addView(item)
            }
        }
    }

    private fun populateWeeklyFromJson(dailyArray: JSONArray) {
        safeRunOnUI {
            if (!isFragmentAttached) return@safeRunOnUI
            weeklyContainer.removeAllViews()
            for (i in 0 until minOf(7, dailyArray.length())) {
                val dayObj  = dailyArray.getJSONObject(i)
                val values  = dayObj.optJSONObject("values")
                val maxTemp = values?.optDouble("temperatureMax", Double.NaN) ?: Double.NaN
                val minTemp = values?.optDouble("temperatureMin", Double.NaN) ?: Double.NaN

                val item = layoutInflater.inflate(R.layout.day_item, weeklyContainer, false)
                item.findViewById<TextView>(R.id.tv_day_name).text =
                    parseTimeToDayLabel(dayObj.optString("time", ""))
                item.findViewById<TextView>(R.id.tv_day_temp).text =
                    if (!maxTemp.isNaN() && !minTemp.isNaN()) "%.0f° / %.0f°".format(maxTemp, minTemp) else "-- / --"
                item.findViewById<ImageView>(R.id.iv_day_icon)
                    .setImageResource(getWeatherIcon(values?.optString("weatherCode", "-1") ?: "-1"))
                weeklyContainer.addView(item)
            }
        }
    }
}