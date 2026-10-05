package com.example.offlinemeshchat

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinemeshchat.databinding.ActivityChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    private var phoneId: String = ""
    private var messageLoadJob: Job? = null

    companion object {
        var currentChatActivity: ChatActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        phoneId = intent.getStringExtra("PHONE_ID") ?: ""

        if (phoneId.isEmpty()) {
            Toast.makeText(
                this,
                "No chat selected.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        database = AppDatabase.getDatabase(this)
        messageDao = database.messageDao()

        // Header
        binding.chatPersonName.text = phoneId
        binding.chatPersonStatus.text = "Online • Mesh"

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.moreButton.setOnClickListener {
            Toast.makeText(
                this,
                "Chat options",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.sendButton.setOnClickListener {
            sendMessage()
        }

        // The paperclip is visual for now.
        binding.attachButton.setOnClickListener {
            Toast.makeText(
                this,
                "Attachments are not available in offline mode yet.",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Send when the keyboard action is pressed.
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        loadMessages()
    }

    private fun sendMessage() {

        val message = binding.messageInput.text
            .toString()
            .trim()

        if (message.isEmpty()) {
            return
        }

        val manager =
            ConnectionManagerHolder.connectionManager

        if (manager == null) {
            Toast.makeText(
                this,
                "Connect to ESP32 first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!manager.isConnected()) {
            Toast.makeText(
                this,
                "ESP32 is not connected.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (
            message.length >
            EncryptionHelper.MAX_PLAINTEXT_LENGTH
        ) {
            Toast.makeText(
                this,
                "Message is too long.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {

            try {

                val encryptedMessage =
                    withContext(Dispatchers.Default) {
                        EncryptionHelper.encrypt(message)
                    }

                val sent =
                    withContext(Dispatchers.IO) {
                        manager.sendMessage(
                            destinationPhoneId = phoneId,
                            encryptedMessage = encryptedMessage
                        )
                    }

                if (sent) {

                    saveSentMessage(message)

                    binding.messageInput.text.clear()

                    loadMessages()

                } else {

                    Toast.makeText(
                        this@ChatActivity,
                        "Message could not be sent.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: IllegalArgumentException) {

                Toast.makeText(
                    this@ChatActivity,
                    "Message is too long for mesh transport.",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {

                Toast.makeText(
                    this@ChatActivity,
                    "Failed to send message.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun saveSentMessage(
        text: String
    ) = withContext(Dispatchers.IO) {

        messageDao.insertMessage(
            Message(
                node = phoneId,
                text = text,
                isSent = true
            )
        )
    }

    private fun loadMessages() {

        messageLoadJob?.cancel()

        messageLoadJob =
            lifecycleScope.launch {

                val messages =
                    withContext(Dispatchers.IO) {
                        messageDao.getMessagesForNode(phoneId)
                    }

                if (
                    isFinishing ||
                    isDestroyed
                ) {
                    return@launch
                }

                binding.chatContainer.removeAllViews()

                for (message in messages) {

                    when {

                        message.text.startsWith(
                            "✓ Delivered"
                        ) -> {
                            addDeliveryText(
                                message.text
                            )
                        }

                        message.isSent -> {
                            addSentBubble(
                                message.text,
                                message.timestamp
                            )
                        }

                        else -> {
                            addReceivedBubble(
                                message.text,
                                message.timestamp
                            )
                        }
                    }
                }

                binding.chatScrollView.post {

                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {
                        binding.chatScrollView.fullScroll(
                            View.FOCUS_DOWN
                        )
                    }
                }
            }
    }

    private fun formatTime(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        ).format(Date(timestamp))
    }

    private fun addSentBubble(
        text: String,
        timestamp: Long
    ) {

        val wrapper = android.widget.LinearLayout(this)

        wrapper.orientation =
            android.widget.LinearLayout.VERTICAL

        wrapper.gravity =
            Gravity.END

        val bubble =
            TextView(this)

        bubble.text =
            text

        bubble.textSize =
            16f

        bubble.setTextColor(
            Color.parseColor("#2B124C")
        )

        bubble.setBackgroundResource(
            R.drawable.chat_sent_bubble
        )

        bubble.setPadding(
            18,
            12,
            18,
            8
        )

        val bubbleParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        bubbleParams.gravity =
            Gravity.END

        bubbleParams.setMargins(
            40,
            6,
            6,
            0
        )

        bubble.layoutParams =
            bubbleParams

        wrapper.addView(bubble)

        val meta =
            TextView(this)

        meta.text =
            "${formatTime(timestamp)}  ✓✓"

        meta.textSize =
            10f

        meta.setTextColor(
            Color.parseColor("#DFB6B2")
        )

        meta.gravity =
            Gravity.END

        val metaParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        metaParams.gravity =
            Gravity.END

        metaParams.setMargins(
            0,
            2,
            16,
            2
        )

        meta.layoutParams =
            metaParams

        wrapper.addView(meta)

        binding.chatContainer.addView(
            wrapper
        )
    }

    private fun addReceivedBubble(
        text: String,
        timestamp: Long
    ) {

        val wrapper = android.widget.LinearLayout(this)

        wrapper.orientation =
            android.widget.LinearLayout.VERTICAL

        wrapper.gravity =
            Gravity.START

        val bubble =
            TextView(this)

        bubble.text =
            text

        bubble.textSize =
            16f

        bubble.setTextColor(
            Color.parseColor("#FBE4D8")
        )

        bubble.setBackgroundResource(
            R.drawable.chat_received_bubble
        )

        bubble.setPadding(
            18,
            12,
            18,
            8
        )

        val bubbleParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        bubbleParams.gravity =
            Gravity.START

        bubbleParams.setMargins(
            6,
            6,
            40,
            0
        )

        bubble.layoutParams =
            bubbleParams

        wrapper.addView(bubble)

        val meta =
            TextView(this)

        meta.text =
            formatTime(timestamp)

        meta.textSize =
            10f

        meta.setTextColor(
            Color.parseColor("#854F6C")
        )

        val metaParams =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        metaParams.setMargins(
            18,
            2,
            0,
            2
        )

        meta.layoutParams =
            metaParams

        wrapper.addView(meta)

        binding.chatContainer.addView(
            wrapper
        )
    }

    private fun addDeliveryText(
        text: String
    ) {

        val delivery =
            TextView(this)

        delivery.text =
            text

        delivery.textSize =
            10f

        delivery.setTextColor(
            Color.parseColor("#854F6C")
        )

        delivery.gravity =
            Gravity.END

        val params =
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )

        params.setMargins(
            8,
            1,
            14,
            3
        )

        delivery.layoutParams =
            params

        binding.chatContainer.addView(
            delivery
        )
    }

    fun refreshMessages() {

        if (
            !isFinishing &&
            !isDestroyed
        ) {
            loadMessages()
        }
    }

    override fun onStart() {

        super.onStart()

        currentChatActivity =
            this

        loadMessages()
    }

    override fun onStop() {

        super.onStop()

        if (
            currentChatActivity === this
        ) {
            currentChatActivity = null
        }
    }

    override fun onDestroy() {

        messageLoadJob?.cancel()
        messageLoadJob = null

        if (
            currentChatActivity === this
        ) {
            currentChatActivity = null
        }

        super.onDestroy()
    }
}
