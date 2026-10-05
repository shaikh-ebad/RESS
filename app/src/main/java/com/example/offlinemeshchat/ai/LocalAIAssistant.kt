package com.example.offlinemeshchat.ai

import android.content.Context
import android.util.Log
import com.example.offlinemeshchat.AppDatabase
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Local AI Assistant using Gemma 3 1B.
 *
 * The Gemma model runs completely on the Android device.
 * No internet connection is required for AI inference.
 *
 * Previous AI conversation history is loaded from Room and
 * supplied to Gemma using the official Gemma turn format.
 */
class LocalAIAssistant(private val context: Context) {

    companion object {
        private const val TAG = "LocalAIAssistant"

        private const val MODEL_FILE =
            "gemma3-1B-it-int4.task"

        private const val LOCAL_MODEL_FILE =
            "gemma3-1B-it-int4.task"

        private const val MAX_TOKENS = 512
        private const val MAX_TOP_K = 40

        /*
         * Only the latest messages are included in the prompt.
         * This keeps the conversation context manageable.
         */
        private const val MAX_HISTORY_MESSAGES = 10

        /*
         * Gemma conversation control tokens.
         */
        private const val START_USER =
            "<start_of_turn>user\n"

        private const val START_MODEL =
            "<start_of_turn>model\n"

        private const val END_TURN =
            "<end_of_turn>\n"
    }

    private var llmInference: LlmInference? = null

    private val executor =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var isInitialized = false

    /**
     * Initialize the Gemma model.
     */
    fun initialize(): Boolean {

        return try {

            Log.i(
                TAG,
                "Starting Gemma 3 initialization..."
            )

            val modelPath =
                copyModelToInternalStorage()

            Log.i(
                TAG,
                "Model path: $modelPath"
            )

            Log.i(
                TAG,
                "Model size: ${
                    File(modelPath).length() /
                            (1024 * 1024)
                } MB"
            )

            val options =
                LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setMaxTopK(MAX_TOP_K)
                    .build()

            llmInference =
                LlmInference.createFromOptions(
                    context,
                    options
                )

            isInitialized = true

            Log.i(
                TAG,
                "Gemma 3 1B initialized successfully"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to initialize Gemma: ${e.message}",
                e
            )

            llmInference = null
            isInitialized = false

            false
        }
    }

    /**
     * Check whether Gemma is ready.
     */
    fun isReady(): Boolean {

        return isInitialized &&
                llmInference != null
    }

    /**
     * Generate a response using Gemma.
     *
     * Previous conversation history is loaded from Room
     * and formatted using Gemma's conversation format.
     */
    fun generateResponse(
        inputText: String
    ): String {

        if (!isReady()) {
            return "AI is not initialized yet."
        }

        if (inputText.isBlank()) {
            return "Please enter a message."
        }

        return try {

            Log.d(
                TAG,
                "Generating Gemma response..."
            )

            /*
             * Access the local Room database.
             *
             * No internet connection is involved.
             */
            val database =
                AppDatabase.getDatabase(context)

            val dao =
                database.aiMessageDao()

            /*
             * Load saved conversation.
             */
            val history =
                kotlinx.coroutines.runBlocking {
                    dao.getAllMessages()
                }

            /*
             * Keep only recent messages.
             */
            val recentHistory =
                history.takeLast(
                    MAX_HISTORY_MESSAGES
                )

            /*
             * Build the Gemma prompt.
             *
             * Gemma expects:
             *
             * <start_of_turn>user
             * ...
             * <end_of_turn>
             *
             * <start_of_turn>model
             * ...
             * <end_of_turn>
             *
             * Finally the prompt ends with:
             *
             * <start_of_turn>model
             *
             * so Gemma knows that it should generate
             * the next assistant response.
             */

            val promptBuilder =
                StringBuilder()

            /*
             * First user turn contains the assistant instructions
             * and project-specific knowledge.
             *
             * Gemma does not use a separate system role, so
             * system-style instructions are placed inside the
             * initial user turn.
             */
            promptBuilder.append(
                START_USER
            )

            promptBuilder.append(
                """
                You are a helpful offline AI assistant inside an Android
                communication application called Offline Mesh Chat.

                Important rules:
                - You work completely offline.
                - Answer the user's question directly.
                - Be concise and helpful.
                - Do not claim to have internet access.
                - Do not invent current information.
                - Use previous conversation when it is relevant.
                - If previous assistant answers conflict with the project
                  knowledge below, trust the project knowledge.
                - Do not repeat the entire conversation.

                Project knowledge:
                - This project is called Offline Mesh Chat.
                - The project uses ESP32 DevKit V1 boards as mesh nodes.
                - ESP32 has built-in 2.4 GHz Wi-Fi.
                - ESP32 can operate as a Wi-Fi access point.
                - ESP32 can also operate as a Wi-Fi station.
                - Android phones connect to nearby ESP32 nodes using Wi-Fi.
                - The project uses ESP-NOW for communication between ESP32 nodes.
                - ESP-NOW is used to forward messages between mesh nodes.
                - Android communicates with the nearby ESP32 using TCP.
                - Messages can travel through multiple ESP32 nodes.
                - Messages can reach another Android phone through the mesh.
                - ESP32 nodes forward encrypted message data.
                - ESP32 nodes do not need to decrypt the message.
                - The Android application uses AES-256-GCM encryption.
                - The AI assistant runs locally on Android.
                - The AI assistant uses Gemma 3 1B.
                - The AI assistant does not run on the ESP32.
                - The AI assistant does not require the internet for inference.
                """.trimIndent()
            )

            promptBuilder.append(
                END_TURN
            )

            /*
             * Add previous conversation.
             */
            for (message in recentHistory) {

                if (message.isUser) {

                    promptBuilder.append(
                        START_USER
                    )

                    promptBuilder.append(
                        message.message
                    )

                    promptBuilder.append(
                        END_TURN
                    )

                } else {

                    promptBuilder.append(
                        START_MODEL
                    )

                    promptBuilder.append(
                        message.message
                    )

                    promptBuilder.append(
                        END_TURN
                    )
                }
            }

            /*
             * Add the new user question.
             */
            promptBuilder.append(
                START_USER
            )

            promptBuilder.append(
                inputText
            )

            promptBuilder.append(
                END_TURN
            )

            /*
             * Tell Gemma to generate the model's turn.
             */
            promptBuilder.append(
                START_MODEL
            )

            val prompt =
                promptBuilder.toString()

            Log.d(
                TAG,
                "History messages used: ${recentHistory.size}"
            )

            val response =
                llmInference!!.generateResponse(
                    prompt
                )

            /*
             * Clean any accidental turn markers that
             * Gemma might include in its visible response.
             */
            response
                .replace(
                    "<end_of_turn>",
                    ""
                )
                .trim()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Gemma inference failed: ${e.message}",
                e
            )

            /*
             * Keep the rule-based system as fallback.
             */
            generateRuleBasedResponse(
                inputText
            )
        }
    }

    /**
     * Generate a response asynchronously.
     */
    fun generateResponseAsync(
        inputText: String,
        onResponse: (String) -> Unit
    ) {

        executor.execute {

            val response =
                generateResponse(inputText)

            onResponse(response)
        }
    }

    /**
     * Copy the Gemma model from assets to
     * private app storage.
     */
    private fun copyModelToInternalStorage(): String {

        val modelFile =
            File(
                context.filesDir,
                LOCAL_MODEL_FILE
            )

        if (
            modelFile.exists() &&
            modelFile.length() >
            500L * 1024L * 1024L
        ) {

            Log.i(
                TAG,
                "Gemma model already exists"
            )

            return modelFile.absolutePath
        }

        Log.i(
            TAG,
            "Copying Gemma model from assets..."
        )

        val temporaryFile =
            File(
                context.filesDir,
                "$LOCAL_MODEL_FILE.tmp"
            )

        if (temporaryFile.exists()) {
            temporaryFile.delete()
        }

        context.assets
            .open(MODEL_FILE)
            .use { input ->

                FileOutputStream(
                    temporaryFile
                ).use { output ->

                    input.copyTo(
                        output,
                        bufferSize = 1024 * 1024
                    )
                }
            }

        if (modelFile.exists()) {
            modelFile.delete()
        }

        if (
            !temporaryFile.renameTo(
                modelFile
            )
        ) {

            throw IllegalStateException(
                "Could not finalize Gemma model file"
            )
        }

        Log.i(
            TAG,
            "Gemma model copied successfully"
        )

        return modelFile.absolutePath
    }

    /**
     * Rule-based fallback.
     */
    private fun generateRuleBasedResponse(
        inputText: String
    ): String {

        val lowerInput =
            inputText.lowercase().trim()

        // Greeting
        if (
            containsAny(
                lowerInput,
                "hello",
                "hi",
                "hey"
            )
        ) {
            return "Hello! I'm your offline AI assistant. How can I help you today?"
        }

        // Help
        if (
            containsAny(
                lowerInput,
                "help",
                "what can you do",
                "how do you work"
            )
        ) {

            return """
                I can help you with:
                • Answering questions
                • Drafting messages
                • Summarizing text
                • Simple calculations
                • Suggestions

                I work completely offline.
            """.trimIndent()
        }

        // Time/date
        if (
            containsAny(
                lowerInput,
                "time",
                "date",
                "day"
            )
        ) {

            val now =
                java.util.Date()

            val time =
                java.text.SimpleDateFormat(
                    "HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(now)

            val date =
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd",
                    java.util.Locale.getDefault()
                ).format(now)

            return "Current time: $time\nDate: $date"
        }

        // Math
        if (
            lowerInput.contains("calculate") ||
            lowerInput.contains("what is")
        ) {
            return tryCalculate(
                lowerInput
            )
        }

        // Message drafting
        if (
            containsAny(
                lowerInput,
                "draft",
                "write",
                "compose"
            )
        ) {

            return "I can help you draft a message. Tell me what you would like to say."
        }

        // Simple hello draft
        if (
            lowerInput.contains(
                "draft a hello"
            )
        ) {

            return """
                Here's a draft:

                Hello! How are you doing? I wanted to reach out and say hi.
            """.trimIndent()
        }

        // Thank-you draft
        if (
            lowerInput.contains(
                "draft a thank"
            )
        ) {

            return """
                Here's a draft:

                Thank you so much! I really appreciate your help.
            """.trimIndent()
        }

        // Summary
        if (
            containsAny(
                lowerInput,
                "summarize",
                "summary",
                "tldr"
            )
        ) {

            return "Please provide the text you'd like me to summarize."
        }

        // Weather
        if (
            containsAny(
                lowerInput,
                "weather"
            )
        ) {

            return "I work offline and cannot check current weather."
        }

        // Identity
        if (
            containsAny(
                lowerInput,
                "who are you",
                "what are you",
                "your name"
            )
        ) {

            return """
                I'm the local AI assistant inside Offline Mesh Chat.
                I run directly on your Android device using Gemma 3 1B.
            """.trimIndent()
        }

        // Capabilities
        if (
            containsAny(
                lowerInput,
                "can you",
                "are you able"
            )
        ) {

            return """
                I can answer questions, draft messages,
                summarize information and help with
                simple tasks while working offline.
            """.trimIndent()
        }

        return """
            I understand you said:

            "$inputText"

            I'm your offline AI assistant. Please try asking
            me a specific question.
        """.trimIndent()
    }

    private fun containsAny(
        text: String,
        vararg keywords: String
    ): Boolean {

        return keywords.any {
            text.contains(it)
        }
    }

    private fun tryCalculate(
        input: String
    ): String {

        val numbers =
            input.extractNumbers()

        if (numbers.size < 2) {
            return "Please provide two numbers. Example: calculate 5 + 3"
        }

        val a = numbers[0]
        val b = numbers[1]

        return when {

            input.contains("+") ->
                "$a + $b = ${a + b}"

            input.contains("-") ->
                "$a - $b = ${a - b}"

            input.contains("*") ||
                    input.contains("x") ->
                "$a × $b = ${a * b}"

            input.contains("/") ->
                if (b != 0) {
                    "$a ÷ $b = ${a / b}"
                } else {
                    "Cannot divide by zero"
                }

            else ->
                "Supported operations: +, -, *, /"
        }
    }

    private fun String.extractNumbers(): List<Int> {

        return Regex("-?\\d+")
            .findAll(this)
            .map {
                it.value.toInt()
            }
            .toList()
    }

    /**
     * Simple message suggestions.
     */
    fun getSuggestions(
        text: String,
        maxSuggestions: Int = 3
    ): List<String> {

        if (text.isBlank()) {

            return listOf(
                "Hello, how are you?",
                "I wanted to ask about...",
                "Can you help me with..."
            )
        }

        val suggestions =
            mutableListOf<String>()

        val lowerText =
            text.lowercase()

        if (
            lowerText.startsWith("hel")
        ) {
            suggestions.add(
                "Hello, how are you?"
            )

            suggestions.add(
                "Hello! I wanted to say..."
            )
        }

        if (
            lowerText.startsWith("th")
        ) {
            suggestions.add(
                "Thank you so much!"
            )

            suggestions.add(
                "Thanks for your help!"
            )
        }

        if (
            lowerText.startsWith("ca")
        ) {
            suggestions.add(
                "Can you help me?"
            )

            suggestions.add(
                "Can we meet later?"
            )
        }

        if (
            lowerText.startsWith("i ")
        ) {
            suggestions.add(
                "I wanted to tell you..."
            )

            suggestions.add(
                "I think that..."
            )
        }

        if (
            lowerText.startsWith("ho")
        ) {
            suggestions.add(
                "How are you doing?"
            )

            suggestions.add(
                "How was your day?"
            )
        }

        return suggestions.take(
            maxSuggestions
        )
    }

    /**
     * Basic sentiment analysis.
     */
    fun analyzeSentiment(
        text: String
    ): Sentiment {

        val lowerText =
            text.lowercase()

        val positiveWords =
            listOf(
                "good",
                "great",
                "awesome",
                "happy",
                "love",
                "excellent",
                "wonderful",
                "amazing",
                "thanks",
                "thank",
                "appreciate"
            )

        val negativeWords =
            listOf(
                "bad",
                "terrible",
                "awful",
                "hate",
                "angry",
                "sad",
                "frustrated",
                "annoying",
                "disappointed",
                "sorry"
            )

        val positiveCount =
            positiveWords.count {
                lowerText.contains(it)
            }

        val negativeCount =
            negativeWords.count {
                lowerText.contains(it)
            }

        return when {

            positiveCount > negativeCount ->
                Sentiment.POSITIVE

            negativeCount > positiveCount ->
                Sentiment.NEGATIVE

            else ->
                Sentiment.NEUTRAL
        }
    }

    /**
     * Basic text summarization fallback.
     */
    fun summarizeText(
        text: String
    ): String {

        if (text.length < 100) {
            return "Text is too short to summarize."
        }

        val sentences =
            text.split(
                Regex("[.!?]+")
            )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotEmpty()
                }

        return when {

            sentences.isEmpty() ->
                text.take(100) + "..."

            sentences.size == 1 ->
                sentences[0]

            else ->
                "${sentences.first()} ... ${sentences.last()}"
        }
    }

    /**
     * Release Gemma resources.
     */
    fun shutdown() {

        try {

            llmInference?.close()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Error closing Gemma: ${e.message}"
            )
        }

        llmInference = null
        isInitialized = false

        executor.shutdownNow()

        Log.i(
            TAG,
            "Local AI shutdown complete"
        )
    }

    enum class Sentiment {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }
}