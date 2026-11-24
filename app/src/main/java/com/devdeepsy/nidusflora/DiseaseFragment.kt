package com.devdeepsy.nidusflora

import android.Manifest
import android.content.ContentResolver
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class DiseaseFragment : Fragment() {
    private lateinit var ivPlantPreview: ImageView
    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
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

    private val PROMPT = """
You are an expert plant pathologist and botanist.

FIRST, determine if the image contains a plant, crop, leaf, or any vegetation.

If the image does NOT contain a plant or crop, respond with ONLY this exact line:
NOT_A_PLANT

If it IS a plant, answer in exactly this numbered format, one item per line:
1. Crop: <specific crop name, or 'Unknown plant' if unsure>
2. Disease: <disease name, or 'Healthy' if no disease found>
3. Confidence: <High / Medium / Low>
4. Symptoms: <what you observe>
5. Treatment: <simple, locally available remedies where possible>
6. Prevention: <prevention tips>

Do not add any introduction or closing text. Keep it concise and practical for a home farmer in India.
""".trimIndent()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isAdded || context == null) return@registerForActivityResult
        if (isGranted) {
            Toast.makeText(context, "Permission granted! Tap again to take photo.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission is required to diagnose plants.", Toast.LENGTH_LONG).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraImageUri
        if (success && uri != null && isAdded) {
            selectedImageUri = uri
            ivPlantPreview.setImageURI(uri)
            btnAnalyze.isEnabled = true
        } else if (isAdded) {
            cameraImageUri = null
            if (selectedImageUri == uri) {
                selectedImageUri = null
                btnAnalyze.isEnabled = false
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && isAdded) {
            selectedImageUri = uri
            ivPlantPreview.setImageURI(uri)
            btnAnalyze.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_disease, container, false)

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
            val ctx = context ?: return@setOnClickListener
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                cleanupOldDiseaseImages(keepUri = selectedImageUri)

                val imageDir = File(ctx.cacheDir, "disease_images").apply { mkdirs() }
                val imageFile = File(imageDir, "plant_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    imageFile
                )
                cameraImageUri = uri
                cameraLauncher.launch(uri)
            } else {
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

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ivPlantPreview.setImageDrawable(null)
    }

    private fun cleanupOldDiseaseImages(keepUri: Uri?) {
        val ctx = context ?: return
        val dir = File(ctx.cacheDir, "disease_images")
        if (!dir.exists() || !dir.isDirectory) return

        val keepNames = setOfNotNull(
            keepUri?.lastPathSegment,
            selectedImageUri?.lastPathSegment,
            cameraImageUri?.lastPathSegment
        )
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keepNames) {
                file.delete()
            }
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqMaxSide: Int): Int {
        var inSampleSize = 1
        val longestSide = max(width, height)
        if (longestSide > reqMaxSide) {
            val halfSide = longestSide / 2
            while ((halfSide / inSampleSize) >= reqMaxSide) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    private fun openImageStreamOrThrow(contentResolver: ContentResolver, uri: Uri) =
        try {
            contentResolver.openInputStream(uri)
                ?: throw IOException("openInputStream returned null for uri=$uri (scheme=${uri.scheme})")
        } catch (e: SecurityException) {
            throw IOException("Permission denied opening uri=$uri (scheme=${uri.scheme})", e)
        } catch (e: IOException) {
            throw IOException("I/O error opening uri=$uri (scheme=${uri.scheme})", e)
        }
    private fun compressImageToBase64(contentResolver: ContentResolver, uri: Uri): String {
        val maxDim = 1024

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        openImageStreamOrThrow(contentResolver, uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0 || boundsOptions.outMimeType == null) {
            throw IOException(
                "Invalid/corrupt image bounds for uri=$uri: w=${boundsOptions.outWidth}, h=${boundsOptions.outHeight}, mime=${boundsOptions.outMimeType}"
            )
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxDim)
        }

        val decoded = openImageStreamOrThrow(contentResolver, uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IOException("BitmapFactory.decodeStream returned null for uri=$uri")

        val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
        val finalBitmap = if (ratio < 1f) {
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
            }
            scaled
        } else {
            decoded
        }

        return try {
            ByteArrayOutputStream().use { outputStream ->
                if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)) {
                    throw IOException("JPEG compression failed for uri=$uri")
                }
                Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            finalBitmap.recycle()
        }
    }

    private fun callVisionAPI(base64Image: String, apiKey: String): String {
        val url = URL("https://openrouter.ai/api/v1/chat/completions")

        val textPart = JSONObject().apply {
            put("type", "text")
            put("text", PROMPT)   // move your existing prompt string into a const PROMPT
        }
        val imagePart = JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image"))
        }

        val body = JSONObject().apply {
            put("model", "deepseek/deepseek-v4.1-flash-1017")
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().put(textPart).put(imagePart)) // text FIRST
                }
            ))
            put("max_tokens", 1500)
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val raw = if (code in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val err = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $code"
                throw IOException("API request failed ($code): $err")
            }

            android.util.Log.d("DiseaseFragment", "RAW RESPONSE: ${raw.take(3000)}")

            val json = JSONObject(raw)
            json.optJSONObject("error")?.let {
                throw IOException("API error: ${it.optString("message", it.toString())}")
            }

            val choice = json.getJSONArray("choices").getJSONObject(0)
            val finish = choice.optString("finish_reason", "?")
            val message = choice.getJSONObject("message")
            val content = if (message.isNull("content")) "" else message.getString("content")

            if (content.isBlank()) throw IOException("Model returned empty content (finish_reason=$finish)")
            content
        } finally {
            connection.disconnect()
        }
    }

    private fun analyzeImage(uri: Uri) {
        val resolver = context?.contentResolver ?: return
        val apiKey = BuildConfig.OPENROUTER_API_KEY_DISEASE
        if (apiKey.isBlank()) {
            tvFullResult.text = "Error: Missing OPENROUTER_API_KEY_DISEASE in local.properties"
            cardResult.visibility = View.VISIBLE
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        btnAnalyze.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val base64 = compressImageToBase64(resolver, uri)
                val result = callVisionAPI(base64, apiKey)

                withContext(Dispatchers.Main) {
                    if (!isAdded || view == null) return@withContext
                    parseAndDisplayResult(result)
                    cardResult.visibility = View.VISIBLE
                    loadingOverlay.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                }
            } catch (e: Exception) {
                android.util.Log.e("DiseaseFragment", "analyzeImage failed for uri=$uri", e)
                withContext(Dispatchers.Main) {
                    if (!isAdded || view == null) return@withContext
                    tvFullResult.text = "Error: ${e.message ?: e::class.java.simpleName}"
                    cardResult.visibility = View.VISIBLE
                    loadingOverlay.visibility = View.GONE
                    btnAnalyze.isEnabled = true
                }
            }
        }
    }

    private fun parseAndDisplayResult(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.contains("NOT_A_PLANT")) {
            tvCropIdentified.text = "❌ Not a plant image"
            tvDiseaseResult.text = ""
            tvFullResult.text = "Please upload a clear photo of a plant, crop, or leaf for disease analysis."
            return
        }

        val numbered = Regex("""^(\d)[.)]\s*(.*)$""")
        var crop = ""
        var disease = ""
        val details = mutableListOf<String>()

        cleaned.lines().map { it.replace("*", "").trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val m = numbered.find(line)
            val rest = m?.groupValues?.get(2).orEmpty().substringAfter(":").trim()
            when (m?.groupValues?.get(1)) {
                "1" -> crop = rest
                "2" -> disease = rest
                else -> details.add(line)
            }
        }

        tvCropIdentified.text = "🌿 Crop: ${crop.ifBlank { "Unknown" }}"
        tvDiseaseResult.text = "🦠 Disease: ${disease.ifBlank { "Unknown" }}"
        tvFullResult.text = if (crop.isBlank() && disease.isBlank()) cleaned else details.joinToString("\n\n")
    }
}