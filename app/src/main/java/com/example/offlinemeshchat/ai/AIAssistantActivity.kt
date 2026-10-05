package com.example.offlinemeshchat.ai

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinemeshchat.AppDatabase
import com.example.offlinemeshchat.databinding.ActivityAiAssistantBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for the offline AI assistant.
 *
 * This provides a dedicated interface for interacting with the local AI
 * without internet connectivity.
 *
 * AI conversation history is stored locally using Room.
 */
class AIAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiAssistantBinding
    private lateinit var aiAssistant: LocalAIAssistant

    private lateinit var database: AppDatabase
    private lateinit var aiMessageDao: AIMessageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize local database
        database = AppDatabase.getDatabase(this)
        aiMessageDao = database.aiMessageDao()

        // Initialize AI assistant
        aiAssistant = LocalAIAssistant(this)

        setupButtons()

        // Load previous AI conversation
        loadAIHistory()

        // Initialize Gemma
        initializeAI()
    }

    private fun setupButtons() {

        binding.backToChatButton.setOnClickListener {
            finish()
        }

        binding.clearAIHistoryButton.setOnClickListener {

            lifecycleScope.launch(Dispatchers.IO) {
                aiMessageDao.deleteAllMessages()

                withContext(Dispatchers.Main) {
                    binding.aiConversationContainer.removeAllViews()
                }
            }
        }

        binding.aiSendButton.setOnClickListener {
            sendAIMessage()
        }

        binding.quickHelpButton.setOnClickListener {
            binding.aiInput.setText("help")
            sendAIMessage()
        }

        binding.quickDraftButton.setOnClickListener {
            binding.aiInput.setText("help me draft a message")
            sendAIMessage()
        }

        binding.quickCalcButton.setOnClickListener {
            binding.aiInput.setText("calculate 25 + 17")
            sendAIMessage()
        }
    }

    private fun initializeAI() {

        lifecycleScope.launch {

            val success = withContext(Dispatchers.IO) {
                aiAssistant.initialize()
            }

            if (success) {
                binding.aiStatusText.text = "AI Status: Ready ✓"
                binding.aiStatusText.setTextColor(
                    Color.parseColor("#4CAF50")
                )
            } else {
                binding.aiStatusText.text =
                    "AI Status: Basic mode (rule-based)"

                binding.aiStatusText.setTextColor(
                    Color.parseColor("#FF9800")
                )
            }
        }
    }

    private fun loadAIHistory() {

        lifecycleScope.launch {

            val history = withContext(Dispatchers.IO) {
                aiMessageDao.getAllMessages()
            }

            runOnUiThread {

                binding.aiConversationContainer.removeAllViews()

                if (history.isEmpty()) {

                    addAIMessage(
                        "Hello! I'm your offline AI assistant. " +
                                "I work completely locally on your device. " +
                                "How can I help you today?"
                    )

                } else {

                    for (message in history) {

                        if (message.isUser) {
                            addUserMessage(message.message)
                        } else {
                            addAIMessage(message.message)
                        }
                    }
                }
            }
        }
    }

    private fun sendAIMessage() {

        val input = binding.aiInput.text
            .toString()
            .trim()

        if (input.isEmpty()) {

            Toast.makeText(
                this,
                "Enter a question",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        // Display user message immediately
        addUserMessage(input)

        // Save user message locally
        lifecycleScope.launch {

            withContext(Dispatchers.IO) {

                aiMessageDao.insertMessage(
                    AIMessage(
                        message = input,
                        isUser = true
                    )
                )
            }
        }

        binding.aiInput.text.clear()

        // Disable input while processing
        binding.aiSendButton.isEnabled = false
        binding.aiSendButton.text = "..."

        // Generate AI response
        lifecycleScope.launch {

            val response = withContext(Dispatchers.IO) {
                aiAssistant.generateResponse(input)
            }

            // Display AI response
            addAIMessage(response)

            // Save AI response locally
            withContext(Dispatchers.IO) {

                aiMessageDao.insertMessage(
                    AIMessage(
                        message = response,
                        isUser = false
                    )
                )
            }

            // Re-enable input
            binding.aiSendButton.isEnabled = true
            binding.aiSendButton.text = "ASK"
        }
    }

    private fun addUserMessage(text: String) {

        val bubble = TextView(this)

        bubble.text = text
        bubble.textSize = 16f

        bubble.setTextColor(
            Color.WHITE
        )

        bubble.setPadding(
            24,
            16,
            24,
            16
        )

        bubble.gravity =
            Gravity.CENTER_VERTICAL

        bubble.setBackgroundColor(
            Color.parseColor("#6750A4")
        )

        val params =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.gravity =
            Gravity.END

        params.setMargins(
            40,
            8,
            8,
            8
        )

        bubble.layoutParams =
            params

        binding.aiConversationContainer.addView(
            bubble
        )

        scrollToBottom()
    }


    private fun addAIMessage(text: String) {

        val bubble = TextView(this)

        bubble.text = text
        bubble.textSize = 16f

        bubble.setTextColor(
            Color.parseColor("#222222")
        )

        bubble.setPadding(
            24,
            16,
            24,
            16
        )

        bubble.gravity =
            Gravity.START

        bubble.setBackgroundColor(
            Color.WHITE
        )

        val params =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.gravity =
            Gravity.START

        params.setMargins(
            8,
            8,
            40,
            8
        )

        bubble.layoutParams =
            params

        binding.aiConversationContainer.addView(
            bubble
        )

        scrollToBottom()
    }

    private fun scrollToBottom() {

        binding.aiScrollView.post {

            binding.aiScrollView.fullScroll(
                android.view.View.FOCUS_DOWN
            )
        }
    }

    override fun onDestroy() {

        super.onDestroy()

        aiAssistant.shutdown()
    }
}