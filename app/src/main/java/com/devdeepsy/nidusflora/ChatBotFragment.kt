package com.devdeepsy.nidusflora

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.activity.result.contract.ActivityResultContracts

class ChatBotFragment : Fragment() , TextToSpeech.OnInitListener{
    private lateinit var rvMessages: RecyclerView
    private lateinit var userInput: EditText
    private lateinit var sendButton: Button
    private lateinit var newChatButton: ImageButton
    private lateinit var chatsButton: ImageButton
    private lateinit var languageButton: ImageButton
    private lateinit var ttsButton: ImageButton
    private lateinit var stsButton: ImageButton
    // Networking
    private val client = OkHttpClient()
    private val gson = Gson()
    // Adapter
    private lateinit var adapter: ChatAdapter
    // Data
    private var currentChatId: String = UUID.randomUUID().toString()
    private val chats = mutableListOf<ChatSession>()
    private val chatMessages = mutableMapOf<String, MutableList<ChatMessage>>()
    // Handler for UI thread
    private val uiHandler = Handler(Looper.getMainLooper())
    // Voice & Language
    private val RQ_SPEECH_REC = 102
    private lateinit var textToSpeech: TextToSpeech
    private var isTtsInitialized = false
    private var currentLanguage = "en" // Default English
    private val supportedLanguages = mapOf(
        "English" to "en",
        "Hindi" to "hi",
        "Bengali" to "bn",
        "Tamil" to "ta",
        "Telugu" to "te",
        "Marathi" to "mr"
    )
    // Typing animation
    private var typingAnimationHandler = Handler(Looper.getMainLooper())
    private var typingAnimationRunnable: Runnable? = null
    private val typingDots = arrayOf("Thinking", "Thinking.", "Thinking..", "Thinking...", "Thinking....", "Thinking.....")
    private var typingDotIndex = 1
    private var botName = "AgriBot"

    private val PREFS_NAME = "chatbot_prefs"
    private val KEY_MSG_COUNT = "msg_count"
    private val KEY_BONUS_COUNT = "bonus_count"
    private val KEY_AD_WATCHES = "ad_watches"
    private val KEY_LAST_DATE = "last_reset_date"

    private val FREE_LIMIT = 8
    private val BONUS_PER_AD = 4
    private val MAX_AD_WATCHES = 3

    private lateinit var messageCounterBar: LinearLayout
    private lateinit var tvMessageCounter: TextView
    private lateinit var tvWatchAd: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chatbot, container, false)

        // Your existing binding code...
        rvMessages = view.findViewById(R.id.rvMessages)
        userInput = view.findViewById(R.id.userInput)
        sendButton = view.findViewById(R.id.sendButton)
        newChatButton = view.findViewById(R.id.newChatButton)
        chatsButton = view.findViewById(R.id.chatsButton)
        languageButton = view.findViewById(R.id.languageButton)
        ttsButton = view.findViewById(R.id.ttsButton)
        stsButton = view.findViewById(R.id.stsButton)
        messageCounterBar = view.findViewById(R.id.messageCounterBar)
        tvMessageCounter = view.findViewById(R.id.tvMessageCounter)
        tvWatchAd = view.findViewById(R.id.tvWatchAd)

        textToSpeech = TextToSpeech(requireContext(), this)
        tvWatchAd.setOnClickListener {
            checkAndResetIfNewDay()
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val adWatches = prefs.getInt(KEY_AD_WATCHES, 0)
            if (adWatches < MAX_AD_WATCHES) {
                showLimitDialog()
            } else {
                Toast.makeText(requireContext(), "No more bonus messages today", Toast.LENGTH_SHORT).show()
            }
        }
        // Load saved chats and setup UI
        loadChats()
        setupRecyclerView()
        bindActions()
        updateCounterUI()

        return view
    }
    private fun setupRecyclerView() {
        // Get or create messages for current chat
        val messages = chatMessages.getOrPut(currentChatId) { mutableListOf() }
        adapter = ChatAdapter(messages)
        rvMessages.adapter = adapter
        val lm = LinearLayoutManager(requireContext())
        lm.stackFromEnd = true
        rvMessages.layoutManager = lm

        // Add welcome message if this is a new chat
        if (messages.isEmpty()) {
            val welcomeMsg = ChatMessage(
                text = "Hello! I'm $botName. How can I help you today?",
                sender = Sender.ASSISTANT
            )

            messages.add(welcomeMsg)
            adapter.notifyItemInserted(messages.size - 1)
            scrollToBottom()
        }
    }
    private fun getWelcomeMessage(): String {
        return when (currentLanguage) {
            "hi" -> "नमस्ते! मैं आपका फार्म सहायक $botName  हूं। आज मैं आपकी कैसे मदद कर सकता हूं?"
            "bn" -> "হ্যালো! আমি আপনার খামার সহকারী। আজ আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
            "ta" -> "வணக்கம்! நான் உங்கள் பண்ணை உதவியாளர். இன்று நான் உங்களுக்கு எப்படி உதவ முடியும்?"
            "te" -> "హలో! నేను మీ ఫార్మ్ అసిస్టెంట్. ఈరోజు నేను మీకు ఎలా సహాయం చేయగలను?"
            "mr" -> "नमस्कार! मी तुमचा शेत सहाय्यक आहे. आज मी तुम्हाला कशी मदत करू शकतो?"
            else -> "Hello! I'm your farm assistant $botName . How can I help you today?"
        }
    }
    private fun bindActions() {
        sendButton.setOnClickListener {
            sendMessage()
        }

        newChatButton.setOnClickListener {
            createNewChat()
        }

        chatsButton.setOnClickListener {
            showChatsDialog()
        }

        languageButton.setOnClickListener {
            showLanguageDialog()
        }

        stsButton.setOnClickListener {
            askSpeechInput()
        }

        ttsButton.setOnClickListener {
            val messages = chatMessages[currentChatId] ?: return@setOnClickListener
            val lastAssistantMessage = messages.lastOrNull { it.sender == Sender.ASSISTANT && !it.isTyping }
            lastAssistantMessage?.let { message ->
                speakText(message.text) // Call speakText directly
            } ?: run {
                Toast.makeText(requireContext(), "No message to speak", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle keyboard send button
        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                return@setOnEditorActionListener true
            }
            false
        }

        // Handle enter key in multiline - send on Enter without new line
        userInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.isShiftPressed) {
                    // Allow new line with Shift+Enter
                    return@setOnKeyListener false
                }
                sendMessage()
                return@setOnKeyListener true
            }
            false
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            askSpeechInput()
        } else {
            Toast.makeText(requireContext(), "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RQ_SPEECH_REC && resultCode == Activity.RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            userInput.setText(result?.get(0) ?: "")
        }
    }
    private fun askSpeechInput() {
        // Check runtime mic permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Speech recognition not available", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something...")

        startActivityForResult(intent, RQ_SPEECH_REC)
    }
    private fun showLanguageDialog() {
        val languages = supportedLanguages.keys.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                val selectedLanguage = languages[which]
                currentLanguage = supportedLanguages[selectedLanguage] ?: "en"
                updateLanguage()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun updateLanguage() {
        // Update welcome message if it's the first message
        val messages = chatMessages[currentChatId]
        if (messages != null && messages.isNotEmpty()) {
            val firstMessage = messages.firstOrNull { it.sender == Sender.ASSISTANT }
            firstMessage?.let {
                messages[0] = it.copy(text = getWelcomeMessage())
                adapter.notifyItemChanged(0)
            }
        }

        // Update TTS language immediately
        if (isTtsInitialized) {
            try {
                val locale = getTTSSupportedLocale()
                val result = textToSpeech.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA) {
                    Toast.makeText(requireContext(), "TTS language data missing for $currentLanguage", Toast.LENGTH_SHORT).show()
                } else if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(requireContext(), "TTS language not supported: $currentLanguage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "TTS error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        Toast.makeText(requireContext(), "Language changed to $currentLanguage", Toast.LENGTH_SHORT).show()
    }
    private fun speakText(text: String) {
        if (!isTtsInitialized) {
            Toast.makeText(requireContext(), "TTS not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        val sanitizedText = text

        try {
            val locale = getTTSSupportedLocale()
            textToSpeech.language = locale
            textToSpeech.speak(sanitizedText, TextToSpeech.QUEUE_FLUSH, null, "tts_message")
        } catch (e: Exception) {
            Log.e("TTS", "Error in speakText: ${e.message}")
            Toast.makeText(requireContext(), "TTS error occurred", Toast.LENGTH_SHORT).show()
        }
    }
    private fun getTTSSupportedLocale(): Locale {
        return when (currentLanguage) {
            "hi" -> Locale("hi", "IN")  // Hindi, India
            "bn" -> Locale("bn", "BD")  // Bengali, Bangladesh
            "ta" -> Locale("ta", "IN")  // Tamil, India
            "te" -> Locale("te", "IN")  // Telugu, India
            "mr" -> Locale("mr", "IN")  // Marathi, India
            else -> Locale.US  // Default to US English
        }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true

            // Set initial language
            val locale = getTTSSupportedLocale()
            val result = textToSpeech.setLanguage(locale)
            val voices = textToSpeech.voices
            val selectedVoice = when (currentLanguage) {
                "hi" -> voices.firstOrNull { it.locale.language == "hi" && it.name.contains("local", true) }
                "es" -> voices.firstOrNull { it.locale.language == "es" }
                "fr" -> voices.firstOrNull { it.locale.language == "fr" }
                else -> voices.firstOrNull { it.locale.language == "en" }
            }
            selectedVoice?.let {
                textToSpeech.voice = it
                Log.d("TTS", "✅ Using voice: ${it.name} | Locale: ${it.locale}")
            } ?: Log.w("TTS", "⚠️ No suitable voice found for $currentLanguage")

            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    Log.e("TTS", "Language data missing for: $currentLanguage")
                    Toast.makeText(requireContext(), "TTS language data missing for $currentLanguage", Toast.LENGTH_SHORT).show()
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.e("TTS", "Language not supported: $currentLanguage")
                    Toast.makeText(requireContext(), "TTS language not supported: $currentLanguage", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.d("TTS", "TTS initialized successfully for language: $currentLanguage")
                }
            }

            // Set utterance progress listener
            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    uiHandler.post {
                        ttsButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.Dark_green))
                    }
                }

                override fun onDone(utteranceId: String?) {
                    uiHandler.post {
                        ttsButton.clearColorFilter()
                    }
                }

                override fun onError(utteranceId: String?) {
                    uiHandler.post {
                        ttsButton.clearColorFilter()
                        Toast.makeText(requireContext(), "TTS playback error", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } else {
            Log.e("TTS", "TTS Initialization failed")
            Toast.makeText(requireContext(), "TTS initialization failed", Toast.LENGTH_SHORT).show()
        }
    }
    private fun sendMessage() {
        if (!canSendMessage()) {
            showLimitDialog()
            return
        }
        val prompt = userInput.text.toString().trim()
        if (prompt.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }

        // Show user message instantly
        recordMessageSent()
        val userMsg = ChatMessage(text = prompt, sender = Sender.USER)
        val messages = chatMessages.getOrPut(currentChatId) { mutableListOf() }
        messages.add(userMsg)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()

        // Clear input
        userInput.setText("")

        // Add typing indicator with animation
        val placeholder = ChatMessage(text = "Thinking.", sender = Sender.ASSISTANT, isTyping = true)
        messages.add(placeholder)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()

        // Start typing animation
        startTypingAnimation(placeholder.id)

        // Send to AI with language context
        sendToAI(prompt, placeholder.id)
    }
    private fun startTypingAnimation(messageId: String) {
        typingDotIndex = 0
        typingAnimationRunnable = object : Runnable {
            override fun run() {
                val messages = chatMessages[currentChatId] ?: return
                val index = messages.indexOfFirst { it.id == messageId && it.isTyping }
                if (index != -1) {
                    typingDotIndex = (typingDotIndex + 1) % typingDots.size
                    messages[index] = messages[index].copy(text = typingDots[typingDotIndex])
                    adapter.notifyItemChanged(index)
                    typingAnimationHandler.postDelayed(this, 500)
                }
            }
        }
        typingAnimationHandler.postDelayed(typingAnimationRunnable!!, 500)
    }
    private fun stopTypingAnimation() {
        typingAnimationRunnable?.let {
            typingAnimationHandler.removeCallbacks(it)
        }
    }
    private fun createNewChat() {
        currentChatId = UUID.randomUUID().toString()
        val chatTitle = "Chat ${chats.size + 1}"
        val newChat = ChatSession(currentChatId, chatTitle, System.currentTimeMillis())
        chats.add(newChat)

        // Clear current messages
        chatMessages[currentChatId] = mutableListOf()

        // Reset adapter with new messages
        val messages = chatMessages[currentChatId]!!
        adapter = ChatAdapter(messages)
        rvMessages.adapter = adapter

        // Add welcome message
        val welcomeMsg = ChatMessage(text = "Hello! I'm your farm assistant. How can I help you today?", sender = Sender.ASSISTANT)
        messages.add(welcomeMsg)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()

        saveChats()
        Toast.makeText(requireContext(), "New chat started", Toast.LENGTH_SHORT).show()
    }
    private fun showChatsDialog() {
        if (chats.isEmpty()) {
            Toast.makeText(requireContext(), "No saved chats", Toast.LENGTH_SHORT).show()
            return
        }

        val chatTitles = chats.map { it.title }.toTypedArray()
        val checkedItems = BooleanArray(chats.size) { false }
        var isSelectionMode = false

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Your Chats  •  Long-press to select")
            .setItems(chatTitles) { _, which ->
                if (!isSelectionMode) {
                    switchToChat(chats[which].id)
                }
            }
            .setPositiveButton("New Chat") { _, _ -> createNewChat() }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        // Attach long-press listener to the ListView inside the dialog
        dialog.listView?.setOnItemLongClickListener { _, _, position, _ ->
            // Switch to multi-choice mode
            dialog.dismiss()
            showDeleteChatDialog()
            true
        }
    }

    private fun showDeleteChatDialog() {
        if (chats.isEmpty()) {
            Toast.makeText(requireContext(), "No chats to delete", Toast.LENGTH_SHORT).show()
            return
        }

        val chatTitles = chats.map { it.title }.toTypedArray()
        val checkedItems = BooleanArray(chats.size) { false }

        AlertDialog.Builder(requireContext())
            .setTitle("Select chats to delete")
            .setMultiChoiceItems(chatTitles, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Delete") { _, _ ->
                val toDelete = chats.filterIndexed { i, _ -> checkedItems[i] }
                if (toDelete.isEmpty()) {
                    Toast.makeText(requireContext(), "No chats selected", Toast.LENGTH_SHORT).show()
                } else {
                    confirmDeleteMultipleChats(toDelete)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteMultipleChats(chatsToDelete: List<ChatSession>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Chats")
            .setMessage("Are you sure you want to delete ${chatsToDelete.size} selected chat(s)? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteMultipleChats(chatsToDelete)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMultipleChats(chatsToDelete: List<ChatSession>) {
        chatsToDelete.forEach { chat ->
            chats.remove(chat)
            chatMessages.remove(chat.id)
        }

        // If current chat was deleted, switch to last chat or create new
        if (!chats.any { it.id == currentChatId }) {
            if (chats.isNotEmpty()) {
                switchToChat(chats.last().id)
            } else {
                createNewChat()
            }
        }

        saveChats()
        Toast.makeText(requireContext(), "${chatsToDelete.size} chat(s) deleted", Toast.LENGTH_SHORT).show()
    }

    private fun switchToChat(chatId: String) {
        currentChatId = chatId
        val messages = chatMessages.getOrPut(chatId) { mutableListOf() }
        adapter = ChatAdapter(messages)
        rvMessages.adapter = adapter
        scrollToBottom()
    }
    private fun sendToAI(prompt: String, placeholderId: String) {
        val url = "https://openrouter.ai/api/v1/chat/completions"

        // Build the conversation history with language context
        val messages = chatMessages[currentChatId] ?: return
        val messageHistory = JSONArray().apply {
            // System message with language context
            val systemMessage = when (currentLanguage) {
                "hi" -> "आप AgriBot हैं, भारत में शहरी और घरेलू बागवानों के लिए एक कृषि सहायक। केवल खेती से संबंधित प्रश्नों का उत्तर दें। 200 शब्दों से कम रखें।"
                "bn" -> "আপনি AgriBot, ভারতের শহুরে এবং গৃহ বাগানকারীদের জন্য একজন কৃষি সহকারী। শুধুমাত্র কৃষি সম্পর্কিত প্রশ্নের উত্তর দিন। 200 শব্দের মধ্যে রাখুন।"
                "ta" -> "நீங்கள் AgriBot, இந்தியாவில் நகர்ப்புற மற்றும் வீட்டு தோட்டக்காரர்களுக்கான விவசாய உதவியாளர். விவசாயம் தொடர்பான கேள்விகளுக்கு மட்டும் பதில் சொல்லுங்கள். 200 வார்த்தைகளுக்குள் வையுங்கள்."
                "te" -> "మీరు AgriBot, భారతదేశంలో పట్టణ మరియు గృహ తోటమాలికులకు వ్యవసాయ సహాయకుడు. వ్యవసాయానికి సంబంధించిన ప్రశ్నలకు మాత్రమే సమాధానం ఇవ్వండి. 200 పదాలలోపు ఉంచండి."
                "mr" -> "तुम्ही AgriBot आहात, भारतातील शहरी आणि घरगुती बागायतदारांसाठी कृषी सहाय्यक. फक्त शेतीशी संबंधित प्रश्नांची उत्तरे द्या. 200 शब्दांपेक्षा कम ठेवा."
                else -> """You are AgriBot, a farming assistant for urban and home gardeners in India.
                        Answer ONLY farming-related questions: plant care, soil, pests, watering, composting, seasonal crops, balcony/terrace/kitchen gardens.
                        If asked anything unrelated to farming, politely say you can only help with farming topics.
                        Keep responses under 200 words. Be practical and specific to Indian climate."""
            }
            put(JSONObject().put("role", "system").put("content", systemMessage))

            // Add conversation history
            messages.takeLast(6).forEach { message ->
                val role = when (message.sender) {
                    Sender.USER -> "user"
                    Sender.ASSISTANT -> "assistant"
                }
                if (message.id != placeholderId && !message.isTyping) {
                    put(JSONObject().put("role", role).put("content", message.text))
                }
            }
        }

        val jsonBody = JSONObject()
            .put("model", "meta-llama/llama-3.1-8b-instruct")
            .put("messages", messageHistory)
            .put("temperature", 0.7)
            .put("max_tokens", 500)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer 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")
            .addHeader("HTTP-Referer", "https://smartfarmhelper.app")
            .addHeader("X-Title", "SmartFarmHelper")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                uiHandler.post {
                    stopTypingAnimation()
                    updateMessage(placeholderId, "❌ Network error: ${e.message}. Please check your internet connection.")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                uiHandler.post {
                    stopTypingAnimation()
                    try {
                        if (response.isSuccessful) {
                            val jsonResponse = JSONObject(body)
                            val choices = jsonResponse.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val reply = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content")
                                    .trim()
                                updateMessage(placeholderId, reply)
                            } else {
                                updateMessage(placeholderId, "❌ No response from AI. Please try again.")
                            }
                        } else {
                            val errorMsg = when (response.code) {
                                401 -> "Invalid API key. Please check the configuration."
                                429 -> "Rate limit exceeded. Please try again in a minute."
                                400 -> "Bad request - please try a different question."
                                else -> "API error ${response.code}"
                            }
                            updateMessage(placeholderId, "⚠️ $errorMsg")
                        }
                    } catch (e: Exception) {
                        updateMessage(placeholderId, "❌ Error: ${e.message ?: "Unknown error"}")
                    }
                }
            }
        })
    }
    private fun updateMessage(messageId: String, newText: String) {
        val messages = chatMessages[currentChatId] ?: return
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messages[index] = messages[index].copy(text = newText, isTyping = false)
            adapter.notifyItemChanged(index)
            scrollToBottom()
            saveChats()
        }
    }
    private fun scrollToBottom() {
        rvMessages.postDelayed({
            if (adapter.itemCount > 0) {
                rvMessages.scrollToPosition(adapter.itemCount - 1)
            }
        }, 100)
    }
    private fun saveChats() {
        val sharedPref = requireContext().getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        // Save chat sessions
        val chatsJson = gson.toJson(chats)
        editor.putString("chat_sessions", chatsJson)

        // Save chat messages
        val messagesJson = gson.toJson(chatMessages)
        editor.putString("chat_messages", messagesJson)

        editor.apply()
    }
    private fun loadChats() {
        val sharedPref = requireContext().getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)

        // Load chat sessions
        val chatsJson = sharedPref.getString("chat_sessions", null)
        if (chatsJson != null) {
            val type = object : TypeToken<List<ChatSession>>() {}.type
            val loadedChats = gson.fromJson<List<ChatSession>>(chatsJson, type)
            chats.clear()
            chats.addAll(loadedChats)
        }

        // Load chat messages
        val messagesJson = sharedPref.getString("chat_messages", null)
        if (messagesJson != null) {
            val type = object : TypeToken<Map<String, List<ChatMessage>>>() {}.type
            val loadedMessages = gson.fromJson<Map<String, List<ChatMessage>>>(messagesJson, type)
            chatMessages.clear()
            loadedMessages.forEach { (key, value) ->
                chatMessages[key] = value.toMutableList()
            }
        }

        // Set current chat to the most recent one or create new
        if (chats.isNotEmpty()) {
            currentChatId = chats.last().id
        } else {
            currentChatId = UUID.randomUUID().toString()
            val newChat = ChatSession(currentChatId, "Chat 1", System.currentTimeMillis())
            chats.add(newChat)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        stopTypingAnimation()
        typingAnimationHandler.removeCallbacksAndMessages(null)
    }
    private fun checkAndResetIfNewDay() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        if (lastDate != today) {
            prefs.edit()
                .putInt(KEY_MSG_COUNT, 0)
                .putInt(KEY_BONUS_COUNT, 0)
                .putInt(KEY_AD_WATCHES, 0)
                .putString(KEY_LAST_DATE, today)
                .apply()
        }
    }
    private fun canSendMessage(): Boolean {
        checkAndResetIfNewDay()
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_MSG_COUNT, 0)
        val bonus = prefs.getInt(KEY_BONUS_COUNT, 0)
        return count < (FREE_LIMIT + bonus)
    }
    private fun recordMessageSent() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_MSG_COUNT, 0)
        prefs.edit().putInt(KEY_MSG_COUNT, count + 1).apply()
        updateCounterUI()
    }
    private fun showLimitDialog() {
        checkAndResetIfNewDay()
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val adWatches = prefs.getInt(KEY_AD_WATCHES, 0)

        if (adWatches < MAX_AD_WATCHES) {
            val remaining = MAX_AD_WATCHES - adWatches
            AlertDialog.Builder(requireContext())
                .setTitle("Daily Limit Reached")
                .setMessage("You've used all 8 free messages today.\n\nWatch a short ad to get +4 more messages. You can do this $remaining more time(s) today.")
                .setPositiveButton("Watch Ad") { _, _ ->
                    AdManager.showRewardedVideo(
                        requireActivity(),
                        object : AdManager.RewardedAdCallback {
                            override fun onRewardEarned() {
                                grantBonusMessages()
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
                .setNegativeButton("Maybe Later", null)
                .show()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Daily Limit Reached")
                .setMessage("You've used all your messages for today, including bonus messages.\n\nCome back tomorrow for 8 fresh messages!")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    private fun grantBonusMessages() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bonus = prefs.getInt(KEY_BONUS_COUNT, 0)
        val watches = prefs.getInt(KEY_AD_WATCHES, 0)
        prefs.edit()
            .putInt(KEY_BONUS_COUNT, bonus + BONUS_PER_AD)
            .putInt(KEY_AD_WATCHES, watches + 1)
            .apply()
        Toast.makeText(requireContext(), "+4 messages unlocked!", Toast.LENGTH_SHORT).show()
        updateCounterUI()
    }
    private fun updateCounterUI() {
        checkAndResetIfNewDay()
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_MSG_COUNT, 0)
        val bonus = prefs.getInt(KEY_BONUS_COUNT, 0)
        val adWatches = prefs.getInt(KEY_AD_WATCHES, 0)
        val total = FREE_LIMIT + bonus
        val remaining = (total - count).coerceAtLeast(0)

        tvMessageCounter.text = "$remaining messages remaining today"

        // Show/hide the watch ad button
        if (adWatches < MAX_AD_WATCHES) {
            tvWatchAd.visibility = View.VISIBLE
        } else {
            tvWatchAd.visibility = View.GONE
        }

        // Turn counter text red when low
        if (remaining <= 2) {
            tvMessageCounter.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.halo_red_light)
            )
        } else {
            tvMessageCounter.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.gray)
            )
        }
    }
    // Data classes
    data class ChatMessage(
        val id: String = UUID.randomUUID().toString(),
        val text: String,
        val sender: Sender,
        val timeMillis: Long = System.currentTimeMillis(),
        val isTyping: Boolean = false
    )
    data class ChatSession(
        val id: String,
        val title: String,
        val createdAt: Long
    )
    enum class Sender { USER, ASSISTANT }
    inner class ChatAdapter(private val items: MutableList<ChatMessage>) :
        RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

        inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val messageText: TextView = itemView as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(32, 24, 32, 24)
                textSize = 16f
                maxWidth = (280 * resources.displayMetrics.density).toInt()
                setLineSpacing(4f, 1f)
            }

            return MessageViewHolder(textView)
        }
        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val message = items[position]
            holder.messageText.text = message.text

            val params = holder.messageText.layoutParams as ViewGroup.MarginLayoutParams

            if (message.sender == Sender.USER) {
                // User message - right side, green background
                params.setMargins(100, 8, 16, 8)
                holder.messageText.gravity = Gravity.END
                holder.messageText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.Dark_green))
                holder.messageText.setTextColor(Color.WHITE)
                holder.messageText.text = message.text

                // Create rounded corners for user message
                val userDrawable = GradientDrawable()
                userDrawable.cornerRadii = floatArrayOf(16f, 16f, 4f, 16f, 16f, 16f, 16f, 4f)
                userDrawable.setColor(ContextCompat.getColor(requireContext(), R.color.Dark_green))
                holder.messageText.background = userDrawable

            } else {
                // Assistant message - left side, light gray background
                params.setMargins(16, 8, 100, 8)
                holder.messageText.gravity = Gravity.START
                holder.messageText.setBackgroundColor(Color.parseColor("#E0E0E0"))
                holder.messageText.setTextColor(Color.BLACK)

                // Create rounded corners for assistant message
                val assistantDrawable = GradientDrawable()
                assistantDrawable.cornerRadii = floatArrayOf(4f, 16f, 16f, 16f, 16f, 4f, 16f, 16f)
                assistantDrawable.setColor(Color.parseColor("#E0E0E0"))
                holder.messageText.background = assistantDrawable
            }

            holder.messageText.layoutParams = params
        }
        override fun getItemCount(): Int = items.size
    }
}