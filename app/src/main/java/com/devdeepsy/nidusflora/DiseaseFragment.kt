package com.devdeepsy.nidusflora

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.devdeepsy.nidusflora.AdManager
class DiseaseFragment: Fragment(){

    private val PREFS_NAME = "disease_prefs"
    private val KEY_SCAN_COUNT = "scan_count"
    private val KEY_LAST_RESET = "last_reset_data"
    private val KEY_AD_SCAN_COUNT = "ad_scan_count"
    private val MAX_FREE_SCANS = 3
    private val MAX_AD_SCANS = 2

    private lateinit var ivPlantPreview: ImageView
    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null

    private lateinit var TextView: TextView
    private lateinit var tvScanCounter: TextView
    private lateinit var tvScanWarning: TextView
    private lateinit var btnTakePhoto: Button
    private lateinit var btnChooseGallery: Button
    private lateinit var btnAnalyze: Button
    private lateinit var cardResult: CardView
    private lateinit var tvFullResult: TextView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var tvCropIdentified: TextView
    private lateinit var tvDiseaseResult: TextView


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_disease, container,false)

        tvScanCounter = view.findViewById(R.id.tvScanCounter)
        tvScanWarning = view.findViewById(R.id.tvScanWarning)
        btnTakePhoto = view.findViewById(R.id.btnTakePhoto)
        btnChooseGallery = view.findViewById(R.id.btnChooseGallery)
        btnAnalyze = view.findViewById(R.id.btnAnalyze)
        ivPlantPreview = view.findViewById(R.id.ivPlantPreview)
        cardResult = view.findViewById(R.id.cardResult)
        tvFullResult = view.findViewById(R.id.tvFullResult)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        tvCropIdentified = view.findViewById(R.id.tvCropIdentified)
        tvDiseaseResult = view.findViewById(R.id.tvDiseaseResult)


        btnTakePhoto.setOnClickListener {
            // 1. Check if we already have permission
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // 2. Permission exists, run your existing logic
                val imageFile = File(
                    requireContext().cacheDir,
                    "disease_images/plant_${System.currentTimeMillis()}.jpg"
                ).also { it.parentFile?.mkdirs() }

                cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    imageFile
                )
                cameraLauncher.launch(cameraImageUri)
            } else {
                // 3. Permission is missing, ask for it
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnChooseGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        btnAnalyze.setOnClickListener {
            val uri = selectedImageUri ?: return@setOnClickListener
            analyzeImage(uri)
        }

        resetIfNewDay()
        updateCounterUI()

        return view
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Optional: Trigger the button click again or show a "Try again" message
            Toast.makeText(context, "Permission granted! Tap again to take photo.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required to diagnose plants.", Toast.LENGTH_LONG).show()
        }
    }

    private fun getScanCount(): Int{
        val prefs= requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SCAN_COUNT ,0)
    }
    private fun incrementScanCount(){
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_SCAN_COUNT, getScanCount() + 1).apply()
    }
    private fun resetIfNewDay() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastReset = prefs.getString(KEY_LAST_RESET, "")
        if (lastReset != today) {
            prefs.edit()
                .putInt(KEY_SCAN_COUNT, 0)
                .putInt(KEY_AD_SCAN_COUNT, 0)  // ✅ Reset ad scans too
                .putString(KEY_LAST_RESET, today)
                .apply()
        }
    }
    private fun updateCounterUI() {
        val count = getScanCount()
        val remaining = MAX_FREE_SCANS - count
        tvScanCounter.text = "Scans today: $count / $MAX_FREE_SCANS"

        if (remaining <= 1 && remaining > 0) {
            tvScanWarning.text = "⚠ $remaining scan left"
            tvScanWarning.visibility = View.VISIBLE
        } else if (remaining <= 0) {
            // Check if user can watch an ad for more scans
            val adScansUsed = getAdScanCount()
            if (adScansUsed < MAX_AD_SCANS) {
                tvScanWarning.text = "📺 Watch ad for 1 more scan (${MAX_AD_SCANS - adScansUsed} left today)"
                tvScanWarning.visibility = View.VISIBLE
                tvScanWarning.setOnClickListener {
                    watchAdForExtraScan()
                }
                // Keep buttons enabled for ad-watch path
                btnTakePhoto.isEnabled = true
                btnChooseGallery.isEnabled = true
                btnAnalyze.isEnabled = true
            } else {
                tvScanWarning.text = "⚠ No scans left today"
                tvScanWarning.visibility = View.VISIBLE
                btnTakePhoto.isEnabled = false
                btnChooseGallery.isEnabled = false
                btnAnalyze.isEnabled = false
            }
        } else {
            tvScanWarning.visibility = View.GONE
        }
    }
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedImageUri = cameraImageUri
            ivPlantPreview.setImageURI(selectedImageUri)
            btnAnalyze.isEnabled = true
        }
    }
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            ivPlantPreview.setImageURI(selectedImageUri)
            btnAnalyze.isEnabled = true
        }
    }

    private fun compressImageToBase64(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val original = BitmapFactory.decodeStream(inputStream)

        // Scale down to max 1024px on longest side
        val maxDim = 1024
        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * ratio).toInt(),
                (original.height * ratio).toInt(),
                true
            )
        } else original

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    private fun callVisionAPI(base64Image: String): String {
        val apiKey = "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"
        val url = URL("https://openrouter.ai/api/v1/chat/completions")

        val imageMessage = JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$base64Image")
            })
        }

        val textMessage = JSONObject().apply {
            put("type", "text")
            put("text", """
You are an expert plant pathologist and botanist.

FIRST, determine if the image contains a plant, crop, leaf, or any vegetation.

If the image does NOT contain a plant or crop, respond with ONLY this exact line:
NOT_A_PLANT

If it IS a plant, provide:
1. Crop/Plant identified (be specific, e.g. 'Wheat', 'Tomato', 'Rice' — say 'Unknown plant' if unsure)
2. Disease name (or 'Healthy' if no disease found)
3. Confidence level (High / Medium / Low)
4. Symptoms observed
5. Recommended treatment (use simple, locally available remedies where possible)
6. Prevention tips

Always start with the crop identification. Keep the response concise and practical for a home farmer in India.
""".trimIndent())
        }

        val body = JSONObject().apply {
            put("model", "meta-llama/llama-3.2-11b-vision-instruct")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(imageMessage)
                        put(textMessage)
                    })
                })
            })
            put("max_tokens", 600)
        }

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.write(body.toString().toByteArray())

        val response = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
    private fun analyzeImage(uri: Uri) {
        val currentCount = getScanCount()
        val adScansUsed = getAdScanCount()
        val canUseAdScan = adScansUsed < MAX_AD_SCANS

        if (currentCount >= MAX_FREE_SCANS && !canUseAdScan) {
            tvFullResult.text = "Daily scan limit reached. Try again tomorrow or watch an ad for extra scans."
            cardResult.visibility = View.VISIBLE
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        btnAnalyze.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val base64 = compressImageToBase64(uri)
                val result = callVisionAPI(base64)

                withContext(Dispatchers.Main) {
                    if (!result.contains("NOT_A_PLANT")) {
                        // Only count the scan if we haven't used all free scans
                        if (currentCount < MAX_FREE_SCANS) {
                            incrementScanCount()
                        } else if (canUseAdScan) {
                            // This scan used an ad-watch slot
                            // We already incremented ad scan count when the ad was watched
                            // But we need to mark that this scan is using that slot
                            // The ad scan count was already incremented in watchAdForExtraScan()
                            // So we just need to make sure we don't double-count
                            // Use a separate flag to track if ad scan was used for this analysis
                        }
                        updateCounterUI()
                    }
                    parseAndDisplayResult(result)
                    cardResult.visibility = View.VISIBLE
                    loadingOverlay.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvFullResult.text = "Error: ${e.message}"
                    cardResult.visibility = View.VISIBLE
                    loadingOverlay.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                }
            }
        }
    }
    private fun parseAndDisplayResult(raw: String) {
        val trimmed = raw.trim()

        if (trimmed == "NOT_A_PLANT" || trimmed.contains("NOT_A_PLANT")) {
            tvCropIdentified.text = "❌ Not a plant image"
            tvDiseaseResult.text = ""
            tvFullResult.text = "Please upload a clear photo of a plant, crop, or leaf for disease analysis."
            return
        }

        val lines = raw.trim().split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        var cropLine = ""
        var diseaseLine = ""
        val detailLines = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("1.") -> cropLine = line.removePrefix("1.").trim()
                line.startsWith("2.") -> diseaseLine = line.removePrefix("2.").trim()
                else -> detailLines.add(line)
            }
        }

        tvCropIdentified.text = "🌿 Crop: $cropLine"
        tvDiseaseResult.text = "🦠 Disease: $diseaseLine"
        tvFullResult.text = detailLines.joinToString("\n\n")
    }

    private fun getAdScanCount(): Int {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AD_SCAN_COUNT, 0)
    }

    private fun incrementAdScanCount() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AD_SCAN_COUNT, getAdScanCount() + 1).apply()
    }
    private fun watchAdForExtraScan() {
        AdManager.showRewardedVideo(
            requireActivity(),
            object : AdManager.RewardedAdCallback {
                override fun onRewardEarned() {
                    incrementAdScanCount()
                    // Allow one extra scan by temporarily overriding the limit
                    // We'll track this separately
                    Toast.makeText(
                        requireContext(),
                        "✅ Extra scan unlocked!",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Reset the scan count UI to show the extra scan available
                    // The analyzeImage will check if ad scan was used
                    updateCounterUI()
                }

                override fun onAdFailedToShow() {
                    Toast.makeText(
                        requireContext(),
                        "Ad not ready. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}