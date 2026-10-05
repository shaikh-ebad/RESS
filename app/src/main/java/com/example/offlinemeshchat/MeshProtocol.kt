package com.example.offlinemeshchat

object MeshProtocol {

    /*
     * Android -> ESP32
     *
     * MSG|destinationPhoneId|encryptedMessage
     *
     * Example:
     * MSG|PHONE_002|encrypted_text_here
     */
    fun createMessage(
        destinationPhoneId: String,
        encryptedMessage: String
    ): String {

        return "MSG|$destinationPhoneId|$encryptedMessage"
    }


    /*
     * Android -> ESP32
     *
     * REGISTER|phoneId
     *
     * This tells the connected ESP32 which Android phone
     * is currently connected to it.
     */
    fun createRegistration(
        phoneId: String
    ): String {

        return "REGISTER|$phoneId"
    }


    /*
     * Android -> ESP32
     *
     * DISCOVER
     *
     * Requests information about phones known by the mesh.
     */
    fun createDiscoveryRequest(): String {

        return "DISCOVER"
    }


    /*
     * Parse a response received from ESP32.
     */
    fun parseResponse(response: String): Response {

        val parts = response.split("|", limit = 3)

        return when (parts[0]) {

            "PONG" -> {
                Response.Pong
            }

            "DELIVERED" -> {

                if (parts.size >= 2) {
                    Response.Delivered(parts[1])
                } else {
                    Response.Unknown(response)
                }
            }

            "MESSAGE" -> {

                if (parts.size >= 3) {
                    Response.MessageReceived(
                        senderId = parts[1],
                        encryptedMessage = parts[2]
                    )
                } else {
                    Response.Unknown(response)
                }
            }

            "PHONES" -> {

                val phones = if (parts.size >= 2 && parts[1].isNotEmpty()) {
                    parts[1]
                        .split(",")
                        .filter { it.isNotBlank() }
                } else {
                    emptyList()
                }

                Response.PhoneList(phones)
            }

            "REGISTERED" -> {

                if (parts.size >= 2) {
                    Response.Registered(parts[1])
                } else {
                    Response.Unknown(response)
                }
            }

            "ERROR" -> {

                val message =
                    if (parts.size >= 2) parts[1]
                    else "Unknown error"

                Response.Error(message)
            }

            else -> {
                Response.Unknown(response)
            }
        }
    }


    /*
     * Types of responses that Android can receive.
     */
    sealed class Response {

        data object Pong : Response()

        data class Delivered(
            val messageId: String
        ) : Response()

        data class MessageReceived(
            val senderId: String,
            val encryptedMessage: String
        ) : Response()

        data class PhoneList(
            val phoneIds: List<String>
        ) : Response()

        data class Registered(
            val phoneId: String
        ) : Response()

        data class Error(
            val message: String
        ) : Response()

        data class Unknown(
            val rawResponse: String
        ) : Response()
    }
}