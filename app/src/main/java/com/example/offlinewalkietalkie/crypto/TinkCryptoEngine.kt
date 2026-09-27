package com.example.offlinewalkietalkie.crypto

import com.google.crypto.tink.subtle.ChaCha20Poly1305
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.abs

/**
 * TalkLoop V3 Cryptographic Engine based on Google Tink 1.16.0.
 * Implements X25519 ECDH, HKDF-SHA256, ChaCha20-Poly1305 AEAD, and SAS derivation.
 */
class TinkCryptoEngine {

    companion object {
        private const val HOP_KEY_INFO = "talkloop-v3-hop"
        private const val CHANNEL_KEY_INFO = "talkloop-v3-channel"
        private const val SAS_KEY_INFO = "talkloop-v3-sas"
        private const val KEY_SIZE_BYTES = 32
        private const val NONCE_SIZE_BYTES = 12
    }

    private val secureRandom = SecureRandom()

    // Generate fresh X25519 private key (32 bytes)
    fun generatePrivateKey(): ByteArray {
        return X25519.generatePrivateKey()
    }

    // Compute public key from private key (32 bytes)
    fun computePublicKey(privateKey: ByteArray): ByteArray {
        return X25519.publicFromPrivate(privateKey)
    }

    // Compute shared secret using ECDH X25519
    fun computeSharedSecret(myPrivateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        return X25519.computeSharedSecret(myPrivateKey, peerPublicKey)
    }

    // HKDF-SHA256 key derivation for hop-by-hop link encryption
    fun deriveHopKey(sharedSecret: ByteArray, salt: ByteArray? = null): ByteArray {
        val actualSalt = salt ?: ByteArray(32)
        return Hkdf.computeHkdf(
            "HmacSha256",
            sharedSecret,
            actualSalt,
            HOP_KEY_INFO.toByteArray(Charsets.UTF_8),
            KEY_SIZE_BYTES
        )
    }

    // HKDF-SHA256 key derivation for end-to-end channel encryption
    fun deriveChannelKey(sharedSecret: ByteArray, salt: ByteArray? = null): ByteArray {
        val actualSalt = salt ?: ByteArray(32)
        return Hkdf.computeHkdf(
            "HmacSha256",
            sharedSecret,
            actualSalt,
            CHANNEL_KEY_INFO.toByteArray(Charsets.UTF_8),
            KEY_SIZE_BYTES
        )
    }

    // Derive a 6-digit Short Authentication String (SAS) from transcript and keys
    fun deriveSasCode(transcriptHash: ByteArray): String {
        val sasKey = Hkdf.computeHkdf(
            "HmacSha256",
            transcriptHash,
            ByteArray(32),
            SAS_KEY_INFO.toByteArray(Charsets.UTF_8),
            4
        )
        val intVal = ByteBuffer.wrap(sasKey).int
        val positive = abs(intVal % 1_000_000)
        return String.format(Locale.US, "%06d", positive)
    }

    // Generate a secure 12-byte nonce
    fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.nextBytes(nonce)
        return nonce
    }

    // Encrypt plaintext using Tink ChaCha20Poly1305 with Associated Data (AAD)
    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val cipher = ChaCha20Poly1305(key)
        // ChaCha20Poly1305 in Tink expects nonce prepended or handled in encrypt
        // Tink's ChaCha20Poly1305.encrypt(plaintext, associatedData) generates a 12-byte nonce internally and prepends it:
        // ciphertext = 12-byte nonce + encrypted + 16-byte tag
        return cipher.encrypt(plaintext, associatedData)
    }

    // Decrypt ciphertext using Tink ChaCha20Poly1305 with Associated Data (AAD)
    fun decrypt(key: ByteArray, ciphertextWithNonceAndTag: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val cipher = ChaCha20Poly1305(key)
        return cipher.decrypt(ciphertextWithNonceAndTag, associatedData)
    }
}
