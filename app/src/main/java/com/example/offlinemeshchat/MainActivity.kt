package com.example.offlinemeshchat

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.offlinemeshchat.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var database: AppDatabase
    private lateinit var messageDao: MessageDao

    private lateinit var connectionManager: MeshConnectionManager

    // ---------------------------------------------------------
    // CONTACT / CHAT STATE
    // ---------------------------------------------------------

    private val meshPhones = mutableListOf<String>()
    private val oldChatPhones = mutableListOf<String>()

    private var selectedPhoneId: String? = null

    private var ownPhoneId: String = ""

    @Volatile
    private var isConnecting = false


    // =========================================================
    // ACTIVITY START
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        AppContextHolder.initialize(
            applicationContext
        )

        ownPhoneId =
            PhoneIdentity.getPhoneId(this)

        database =
            AppDatabase.getDatabase(this)

        messageDao =
            database.messageDao()

        setupConnectionManager()

        setupButtons()


        updateStatus(
            "Your ID: $ownPhoneId\nNot connected"
        )

        updateButtonStateDisconnected()

        loadChatHistory()
    }


    // =========================================================
    // CONTACT LIST
    // =========================================================

    private fun updatePhoneList(phones: List<String>) {
        runOnUiThread {
            meshPhones.clear()
            meshPhones.addAll(phones.filter { it != ownPhoneId }.distinct().sorted())
            updateContactSections()
        }
    }

    private fun updateContactSections() {
        val activePhones = meshPhones.filter { it != ownPhoneId }.distinct().sorted()
        val savedPhones = oldChatPhones.filter { it != ownPhoneId && !activePhones.contains(it) }.distinct().sorted()

        binding.discoverableListContainer.removeAllViews()
        binding.oldChatsListContainer.removeAllViews()

        if (activePhones.isEmpty()) {
            addSectionMessage(binding.discoverableListContainer, "No phones currently discoverable on the mesh.")
        } else {
            for (phoneId in activePhones) {
                binding.discoverableListContainer.addView(createContactRow(phoneId, true))
            }
        }

        if (savedPhones.isEmpty()) {
            addSectionMessage(binding.oldChatsListContainer, "No old chats yet.")
        } else {
            for (phoneId in savedPhones) {
                binding.oldChatsListContainer.addView(createContactRow(phoneId, false))
            }
        }
    }

    private fun createContactRow(phoneId: String, active: Boolean): TextView {
        val row = TextView(this)
        row.text = if (active) "🟢  $phoneId" else "⚪  $phoneId"
        row.textSize = 17f
        row.setTextColor(Color.parseColor("#FBE4D8"))
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(20, 20, 20, 20)
        row.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        row.setOnClickListener { openChat(phoneId) }
        return row
    }

    private fun addSectionMessage(container: android.widget.LinearLayout, message: String) {
        val textView = TextView(this)
        textView.text = message
        textView.textSize = 14f
        textView.setTextColor(Color.parseColor("#777777"))
        textView.setPadding(20, 18, 20, 18)
        container.addView(textView)
    }

    private fun updateChatList() {
        updateContactSections()
    }

    private fun addContactIfNeeded(phoneId: String) {
        if (phoneId == ownPhoneId) return
        if (!oldChatPhones.contains(phoneId)) {
            oldChatPhones.add(phoneId)
            oldChatPhones.sort()
        }
        updateContactSections()
    }

    private fun openChat(phoneId: String) {
        if (phoneId == ownPhoneId) return
        addContactIfNeeded(phoneId)
        selectedPhoneId = phoneId
        val intent = android.content.Intent(this, ChatActivity::class.java)
        intent.putExtra("PHONE_ID", phoneId)
        startActivity(intent)
    }

    private fun showNoContactsMessage() {
        updateContactSections()
    }

    // =========================================================
    // CONNECTION MANAGER
    // =========================================================

    private fun setupConnectionManager() {

        connectionManager =
            MeshConnectionManager(

                onResponseReceived = { response ->

                    handleESP32Response(
                        response
                    )
                },

                onConnectionChanged = { connected ->

                    runOnUiThread {

                        if (connected) {

                            updateStatus(
                                "Your ID: $ownPhoneId\n" +
                                        "Connected to ESP32"
                            )

                            updateButtonStateConnected()

                        } else {

                            meshPhones.clear()

                            updateStatus(
                                "Your ID: $ownPhoneId\n" +
                                        "Disconnected"
                            )

                            updateButtonStateDisconnected()
                            updateContactSections()
                        }
                    }
                },

                onError = { error ->

                    runOnUiThread {

                        Toast.makeText(
                            this,
                            error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        ConnectionManagerHolder.connectionManager =
            connectionManager
    }


    // =========================================================
    // BUTTONS
    // =========================================================

    private fun setupButtons() {

        binding.connectButton.setOnClickListener {

            if (
                connectionManager.isConnected()
            ) {

                disconnectFromESP32()

            } else {

                connectToESP32()
            }
        }

        binding.discoverVisibleButton.setOnClickListener {

            discoverPhones()
        }
        binding.aiButton.setOnClickListener {
            startActivity(
                android.content.Intent(
                    this,
                    com.example.offlinemeshchat.ai.AIAssistantActivity::class.java
                )
            )
        }
    }


    // =========================================================
    // ESP32 CONNECTION
    // =========================================================

    private fun connectToESP32() {
        if (isConnecting) return
        if (connectionManager.isConnected()) return

        isConnecting = true

        updateStatus(
            "Your ID: $ownPhoneId\nConnecting..."
        )

        updateButtonStateConnecting()
        connectionManager.connect()
    }

    private fun disconnectFromESP32() {
        isConnecting = false
        connectionManager.disconnect()

        updateStatus(
            "Your ID: $ownPhoneId\nDisconnected"
        )

        updateButtonStateDisconnected()
    }

    private fun updateButtonStateConnected() {
        isConnecting = false

        binding.connectButton.text = "DISCONNECT"
        binding.connectButton.isEnabled = true
        binding.discoverVisibleButton.isEnabled = true

        binding.connectButton.setBackgroundColor(
            Color.parseColor("#DFB6B2")
        )
        binding.discoverVisibleButton.setBackgroundColor(
            Color.parseColor("#DFB6B2")
        )
    }

    private fun updateButtonStateDisconnected() {
        isConnecting = false

        binding.connectButton.text = "CONNECT TO MESH"
        binding.connectButton.isEnabled = true
        binding.discoverVisibleButton.isEnabled = false

        binding.connectButton.setBackgroundColor(
            Color.parseColor("#DFB6B2")
        )
        binding.discoverVisibleButton.setBackgroundColor(
            Color.parseColor("#522B5B")
        )
    }

    private fun updateButtonStateConnecting() {
        binding.connectButton.text = "CONNECTING..."
        binding.connectButton.isEnabled = false
        binding.discoverVisibleButton.isEnabled = false

        binding.connectButton.setBackgroundColor(
            Color.parseColor("#854F6C")
        )
        binding.discoverVisibleButton.setBackgroundColor(
            Color.parseColor("#522B5B")
        )
    }

    // =========================================================
    // DISCOVERY
    // =========================================================

    private fun discoverPhones() {
        if (!connectionManager.isConnected()) {
            Toast.makeText(
                this,
                "Connect to an ESP32 first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (connectionManager.discoverPhones()) {
            Toast.makeText(
                this,
                "Discovering phones...",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =========================================================
    // ESP32 RESPONSE HANDLING
    // =========================================================

    private fun handleESP32Response(
        response: String
    ) {

        val parsed =
            MeshProtocol.parseResponse(
                response
            )

        when (parsed) {

            is MeshProtocol.Response.Pong -> {

                updateStatus(
                    "Your ID: $ownPhoneId\n" +
                            "ESP32 connected"
                )
            }


            is MeshProtocol.Response.Registered -> {

                updateStatus(
                    "Your ID: $ownPhoneId\n" +
                            "Registered with " +
                            parsed.phoneId
                )

                connectionManager.discoverPhones()
            }


            is MeshProtocol.Response.PhoneList -> {

                updatePhoneList(
                    parsed.phoneIds
                )
            }


            is MeshProtocol.Response.Delivered -> {

                /*
                 * For now, the ESP32 protocol returns only
                 * the message ID in DELIVERED.
                 *
                 * The exact destination association will
                 * be improved in the next protocol update.
                 */
                val destination =
                    selectedPhoneId
                        ?: return

                val deliveryText =
                    "✓ Delivered [ID: " +
                            "${parsed.messageId}]"

                addDeliveryMessage(
                    destination,
                    deliveryText
                )
            }


            is MeshProtocol.Response.MessageReceived -> {

                receiveEncryptedMessage(
                    parsed.senderId,
                    parsed.encryptedMessage
                )
            }


            is MeshProtocol.Response.Error -> {

                Toast.makeText(
                    this,
                    parsed.message,
                    Toast.LENGTH_SHORT
                ).show()
            }


            is MeshProtocol.Response.Unknown -> {

                // Ignore unknown responses.
            }
        }
    }


    // =========================================================
    // RECEIVE MESSAGE
    // =========================================================

    /*
     * REPLACE ONLY the existing receiveEncryptedMessage() function
     * in MainActivity.kt with this version.
     *
     * IMPORTANT:
     * When ChatActivity is open, MainActivity is stopped.
     * Therefore MainActivity.lifecycleScope must NOT be used to
     * save an incoming message while ChatActivity is visible.
     */
    private fun receiveEncryptedMessage(
        senderId: String,
        encryptedMessage: String
    ) {
        try {

            val plaintext =
                EncryptionHelper.decryptSafe(
                    encryptedMessage
                ) ?: run {
                    Toast.makeText(
                        this,
                        "Unable to decrypt message from $senderId",
                        Toast.LENGTH_SHORT
                    ).show()

                    return
                }

            addContactIfNeeded(senderId)

            /*
             * If ChatActivity is currently open, use ITS lifecycleScope.
             *
             * MainActivity is stopped while ChatActivity is on screen,
             * so MainActivity.lifecycleScope may already be cancelled.
             * That was preventing the database insert and refresh.
             */
            val openChat =
                ChatActivity.currentChatActivity

            if (openChat != null) {

                openChat.lifecycleScope.launch {

                    withContext(Dispatchers.IO) {

                        messageDao.insertMessage(
                            Message(
                                node = senderId,
                                text = plaintext,
                                isSent = false
                            )
                        )
                    }

                    /*
                     * Database insert has completed.
                     * Now refresh the already-open conversation.
                     */
                    if (
                        !isFinishing &&
                        !isDestroyed
                    ) {
                        openChat.refreshMessages()
                    }
                }

                return

            } else {

                /*
                 * No ChatActivity is open.
                 * MainActivity is active in this case, so its
                 * lifecycleScope is safe to use.
                 */
                lifecycleScope.launch {

                    withContext(Dispatchers.IO) {

                        messageDao.insertMessage(
                            Message(
                                node = senderId,
                                text = plaintext,
                                isSent = false
                            )
                        )
                    }

                    runOnUiThread {

                        openChat(senderId)
                    }
                }
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Unable to decrypt message.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    // =========================================================
    // DATABASE
    // =========================================================

    private fun addSentMessage(
        phoneId: String,
        message: String
    ) {

        lifecycleScope.launch {

            withContext(
                Dispatchers.IO
            ) {

                messageDao.insertMessage(
                    Message(
                        node = phoneId,
                        text = message,
                        isSent = true
                    )
                )
            }

            runOnUiThread {

                addContactIfNeeded(
                    phoneId
                )

                /*
                 * Stay in the conversation where
                 * the message was sent.
                 */
                openChat(
                    phoneId
                )
            }
        }
    }


    private fun addDeliveryMessage(
        phoneId: String,
        message: String
    ) {

        lifecycleScope.launch {

            // First save the delivery message to the database.
            withContext(
                Dispatchers.IO
            ) {
                messageDao.insertMessage(
                    Message(
                        node = phoneId,
                        text = message,
                        isSent = true
                    )
                )
            }

            // Refresh the open ChatActivity, if one is visible.
            ChatActivity.currentChatActivity?.refreshMessages()
        }
    }


    private fun loadChatHistory() {

        lifecycleScope.launch {
            val phones = withContext(Dispatchers.IO) { messageDao.getAllPhones() }

            runOnUiThread {
                oldChatPhones.clear()
                oldChatPhones.addAll(phones.filter { it != ownPhoneId }.distinct().sorted())

                if (selectedPhoneId == null && oldChatPhones.isNotEmpty()) {
                    selectedPhoneId = oldChatPhones[0]
                }

                updateContactSections()
            }
        }
    }


// =========================================================
// STATUS
// =========================================================

    private fun updateStatus(
        text: String
    ) {

        runOnUiThread {

            binding.statusText.text =
                text
        }
    }


// =========================================================
// CLEANUP
// =========================================================

    override fun onDestroy() {

        super.onDestroy()

        if (
            ::connectionManager.isInitialized
        ) {

            connectionManager.shutdown()
        }
    }
}
