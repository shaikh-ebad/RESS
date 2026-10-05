package com.example.offlinemeshchat

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class MeshConnectionManager(
    private val onResponseReceived: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val ESP32_PORT = 3333
        private const val CONNECTION_TIMEOUT = 5000
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
    }

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private val networkExecutor =
        Executors.newSingleThreadExecutor()

    private val listenerExecutor =
        Executors.newCachedThreadPool()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    @Volatile
    private var connected = false

    /*
     * Used to identify the currently active connection.
     *
     * If an old listener is still finishing while a new
     * connection has already started, the old listener
     * cannot change the state of the new connection.
     */
    @Volatile
    private var connectionGeneration = 0


    // ---------------------------------------------------------
    // CONNECT
    // ---------------------------------------------------------

    fun connect(
        ipAddress: String = DEFAULT_ESP32_IP
    ) {

        networkExecutor.execute {

            val thisGeneration =
                synchronized(this) {
                    connectionGeneration++

                    disconnectInternal()

                    connectionGeneration
                }

            try {

                val newSocket =
                    Socket()

                newSocket.connect(
                    InetSocketAddress(
                        ipAddress,
                        ESP32_PORT
                    ),
                    CONNECTION_TIMEOUT
                )

                val newWriter =
                    PrintWriter(
                        newSocket.getOutputStream(),
                        true
                    )

                val newReader =
                    BufferedReader(
                        InputStreamReader(
                            newSocket.getInputStream()
                        )
                    )

                /*
                 * Make sure this connection is still the
                 * active connection before installing it.
                 */
                if (
                    thisGeneration !=
                    connectionGeneration
                ) {

                    try {
                        newReader.close()
                    } catch (_: Exception) {
                    }

                    try {
                        newWriter.close()
                    } catch (_: Exception) {
                    }

                    try {
                        newSocket.close()
                    } catch (_: Exception) {
                    }

                    return@execute
                }

                socket = newSocket
                writer = newWriter
                reader = newReader

                connected = true

                notifyConnectionChanged(true)

                startListening(
                    thisGeneration,
                    newReader
                )

                /*
                 * Register this Android phone.
                 */
                val phoneId =
                    PhoneIdentity.getPhoneId(
                        AppContextHolder.context
                    )

                sendInternal(
                    MeshProtocol.createRegistration(
                        phoneId
                    )
                )

            } catch (e: Exception) {

                /*
                 * Only report failure if this is still
                 * the active connection attempt.
                 */
                if (
                    thisGeneration ==
                    connectionGeneration
                ) {

                    connected = false

                    disconnectInternal()

                    notifyConnectionChanged(false)

                    notifyError(
                        "Connection failed: ${
                            e.message
                                ?: e.javaClass.simpleName
                        }"
                    )
                }
            }
        }
    }


    // ---------------------------------------------------------
    // LISTEN FOR ESP32
    // ---------------------------------------------------------

    private fun startListening(
        generation: Int,
        currentReader: BufferedReader
    ) {

        listenerExecutor.execute {

            try {

                while (
                    connected &&
                    generation ==
                    connectionGeneration
                ) {

                    val response =
                        currentReader.readLine()

                    if (response == null) {
                        break
                    }

                    if (response.isNotBlank()) {

                        mainHandler.post {

                            /*
                             * Ignore responses from an old
                             * connection.
                             */
                            if (
                                generation ==
                                connectionGeneration
                            ) {

                                onResponseReceived(
                                    response
                                )
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                if (
                    connected &&
                    generation ==
                    connectionGeneration
                ) {

                    notifyError(
                        "Connection lost: ${
                            e.message
                                ?: e.javaClass.simpleName
                        }"
                    )
                }

            } finally {

                /*
                 * Only the current listener is allowed
                 * to mark the connection as disconnected.
                 */
                if (
                    generation ==
                    connectionGeneration
                ) {

                    if (connected) {

                        connected = false

                        notifyConnectionChanged(
                            false
                        )
                    }
                }
            }
        }
    }


    // ---------------------------------------------------------
    // SEND COMMAND
    // ---------------------------------------------------------

    fun send(
        command: String
    ): Boolean {

        if (!isConnected()) {

            notifyError(
                "ESP32 is not connected."
            )

            return false
        }

        networkExecutor.execute {

            try {

                sendInternal(
                    command
                )

            } catch (e: Exception) {

                notifyError(
                    "Send failed: ${
                        e.message
                            ?: e.javaClass.simpleName
                    }"
                )
            }
        }

        return true
    }


    // ---------------------------------------------------------
    // ACTUAL TCP WRITE
    // ---------------------------------------------------------

    private fun sendInternal(
        command: String
    ) {

        if (!connected) {
            return
        }

        val currentWriter =
            writer
                ?: throw IllegalStateException(
                    "TCP writer is not available."
                )

        currentWriter.println(
            command
        )

        if (
            currentWriter.checkError()
        ) {

            throw IllegalStateException(
                "TCP write failed."
            )
        }
    }


    // ---------------------------------------------------------
    // SEND MESSAGE
    // ---------------------------------------------------------

    fun sendMessage(
        destinationPhoneId: String,
        encryptedMessage: String
    ): Boolean {

        val command =
            MeshProtocol.createMessage(
                destinationPhoneId,
                encryptedMessage
            )

        return send(
            command
        )
    }


    // ---------------------------------------------------------
    // DISCOVER PHONES
    // ---------------------------------------------------------

    fun discoverPhones(): Boolean {

        return send(
            MeshProtocol.createDiscoveryRequest()
        )
    }


    // ---------------------------------------------------------
    // CONNECTION STATE
    // ---------------------------------------------------------

    fun isConnected(): Boolean {

        return connected &&
                socket?.isConnected == true &&
                socket?.isClosed == false
    }


    // ---------------------------------------------------------
    // DISCONNECT
    // ---------------------------------------------------------

    fun disconnect() {
        networkExecutor.execute {
            try {
                if (connected) {
                    sendInternal("DISCONNECT")

                    // Give ESP32 time to process DISCONNECT
                    Thread.sleep(100)
                }
            } catch (_: Exception) {
            } finally {
                synchronized(this) {
                    connectionGeneration++
                    connected = false
                    disconnectInternal()
                }

                notifyConnectionChanged(false)
            }
        }
    }


    private fun disconnectInternal() {

        connected = false

        try {
            reader?.close()
        } catch (_: Exception) {
        }

        try {
            writer?.close()
        } catch (_: Exception) {
        }

        try {
            socket?.close()
        } catch (_: Exception) {
        }

        reader = null
        writer = null
        socket = null
    }


    // ---------------------------------------------------------
    // CALLBACKS
    // ---------------------------------------------------------

    private fun notifyConnectionChanged(
        state: Boolean
    ) {

        mainHandler.post {

            onConnectionChanged(
                state
            )
        }
    }


    private fun notifyError(
        message: String
    ) {

        mainHandler.post {

            onError(
                message
            )
        }
    }


    // ---------------------------------------------------------
    // SHUTDOWN
    // ---------------------------------------------------------

    fun shutdown() {

        synchronized(this) {

            connectionGeneration++

            connected = false

            disconnectInternal()
        }

        networkExecutor.shutdownNow()

        listenerExecutor.shutdownNow()
    }
}