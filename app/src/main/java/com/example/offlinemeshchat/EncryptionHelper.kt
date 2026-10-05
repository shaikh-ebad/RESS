package com.example.offlinemeshchat

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption helper for end-to-end message encryption.
 *
 * Uses AES-256-GCM.
 *
 * AES-GCM provides:
 * - Confidentiality: hides the message contents
 * - Integrity: detects modified ciphertext
 * - Authentication: detects incorrect/tampered ciphertext
 *
 * The ESP32 nodes do not decrypt the message.
 * They simply forward the encrypted ciphertext.
 *
 * IMPORTANT:
 * The shared key is hardcoded for this college prototype.
 * A production application should use secure key exchange/key storage.
 */
object EncryptionHelper {

    private const val TAG = "EncryptionHelper"

    // 32 bytes = 256-bit AES key
    private val secretKey =
        "R3SS9xK7mP2vL8qN4tY6wA1sD5fH0jC"
            .toByteArray(StandardCharsets.UTF_8)

    // AES-GCM recommended nonce size
    private const val NONCE_SIZE = 12

    // Authentication tag size = 128 bits = 16 bytes
    private const val TAG_SIZE_BITS = 128

    /**
     * ESP32 has:
     *
     * char data[100]
     *
     * AES-GCM format:
     *
     * [12-byte nonce][ciphertext + 16-byte authentication tag]
     *
     * The complete binary data is then Base64 encoded.
     *
     * We keep the plaintext limit at 40 characters/bytes
     * so that the resulting Base64 ciphertext safely fits
     * inside the ESP32 data[100] field.
     *
     * For normal ASCII text:
     * 40 bytes plaintext
     * + 12 bytes nonce
     * + 16 bytes GCM tag
     * = 68 bytes
     *
     * Base64 = 92 characters
     *
     * This leaves a small safety margin below 100.
     */
    const val MAX_PLAINTEXT_LENGTH = 40

    /**
     * Encrypt a message using AES-256-GCM.
     *
     * Format:
     *
     * [12-byte random nonce][ciphertext][16-byte authentication tag]
     *
     * The complete result is Base64 encoded.
     *
     * @param message plaintext message
     * @return Base64 encoded encrypted message
     */
    fun encrypt(message: String): String {

        if (message.length > MAX_PLAINTEXT_LENGTH) {
            throw IllegalArgumentException(
                "Message too long: ${message.length} chars " +
                        "(max $MAX_PLAINTEXT_LENGTH)"
            )
        }

        val plaintext =
            message.toByteArray(StandardCharsets.UTF_8)

        // Extra protection for Unicode characters such as emojis.
        if (plaintext.size > MAX_PLAINTEXT_LENGTH) {
            throw IllegalArgumentException(
                "Message is too large in UTF-8 bytes. " +
                        "Please use a shorter message."
            )
        }

        val key =
            SecretKeySpec(secretKey, "AES")

        // Generate a fresh random nonce for every message.
        val nonce = ByteArray(NONCE_SIZE)

        SecureRandom().nextBytes(nonce)

        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding")

        val parameterSpec =
            GCMParameterSpec(
                TAG_SIZE_BITS,
                nonce
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            parameterSpec
        )

        val encrypted =
            cipher.doFinal(plaintext)

        // Store nonce + ciphertext + authentication tag.
        val combined =
            nonce + encrypted

        val base64Result =
            Base64.encodeToString(
                combined,
                Base64.NO_WRAP
            )

        // Safety check for ESP32 data[100].
        if (base64Result.length > 99) {
            throw IllegalArgumentException(
                "Encrypted message is too large for the mesh packet."
            )
        }

        Log.d(
            TAG,
            "Encrypted ${message.length} chars -> " +
                    "${base64Result.length} Base64 characters"
        )

        return base64Result
    }

    /**
     * Decrypt an AES-256-GCM encrypted message.
     *
     * GCM automatically verifies the authentication tag.
     *
     * If the ciphertext has been modified, or the wrong key
     * is used, decryption fails.
     *
     * @param encryptedMessage Base64 encoded ciphertext
     * @return decrypted plaintext
     */
    fun decrypt(encryptedMessage: String): String {

        if (encryptedMessage.isEmpty()) {
            throw IllegalArgumentException(
                "Empty ciphertext"
            )
        }

        val combined =
            try {
                Base64.decode(
                    encryptedMessage,
                    Base64.NO_WRAP
                )
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid Base64 encoding: ${e.message}"
                )
            }

        // Minimum:
        // 12-byte nonce + 16-byte authentication tag
        if (combined.size < NONCE_SIZE + 16) {
            throw IllegalArgumentException(
                "Ciphertext too short: ${combined.size} bytes"
            )
        }

        // Extract the 12-byte nonce.
        val nonce =
            combined.copyOfRange(
                0,
                NONCE_SIZE
            )

        // Extract ciphertext + authentication tag.
        val encrypted =
            combined.copyOfRange(
                NONCE_SIZE,
                combined.size
            )

        val key =
            SecretKeySpec(
                secretKey,
                "AES"
            )

        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding"
            )

        val parameterSpec =
            GCMParameterSpec(
                TAG_SIZE_BITS,
                nonce
            )

        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            parameterSpec
        )

        val decrypted =
            try {
                cipher.doFinal(encrypted)
            } catch (e: Exception) {

                // This can happen if:
                // - ciphertext was modified
                // - authentication tag is invalid
                // - wrong key was used
                throw IllegalArgumentException(
                    "Decryption or authentication failed."
                )
            }

        return String(
            decrypted,
            StandardCharsets.UTF_8
        )
    }

    /**
     * Safely attempt to decrypt a message.
     *
     * Returns null instead of crashing if:
     * - ciphertext is malformed
     * - Base64 is invalid
     * - authentication fails
     * - ciphertext was modified
     */
    fun decryptSafe(
        encryptedMessage: String
    ): String? {

        return try {

            decrypt(encryptedMessage)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Safe decryption failed: ${e.message}"
            )

            null
        }
    }

    /**
     * Estimate the Base64 ciphertext size.
     *
     * AES-GCM overhead:
     *
     * 12 bytes nonce
     * + 16 bytes authentication tag
     * + plaintext bytes
     */
    fun estimateCiphertextSize(
        plaintextLength: Int
    ): Int {

        if (plaintextLength < 0) {
            return 0
        }

        val combinedSize =
            NONCE_SIZE +
                    plaintextLength +
                    16

        // Base64 size = 4 * ceil(bytes / 3)
        return ((combinedSize + 2) / 3) * 4
    }
}