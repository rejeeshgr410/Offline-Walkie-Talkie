package com.example.offlinewalkietalkie.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(context: Context? = null) {
    companion object {
        private const val TAG = "TL/CryptoManager"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val NONCE_SIZE = 12
        private const val IDENTITY_PREFS = "talkloop_device_identity"
        private const val PUBLIC_KEY = "public_key_x509"
        private const val PRIVATE_KEY = "private_key_pkcs8"

        private fun generateIdentity(): KeyPair {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            return kpg.generateKeyPair()
        }
    }

    /**
     * A stable device identity is required for trusted reconnect.  The private
     * key is encrypted at rest with an Android Keystore-backed master key.  A
     * context-free manager remains available for JVM tests and short-lived
     * utility use, where an ephemeral identity is intentional.
     */
    private val keyPair: KeyPair = context?.let(::loadOrCreateIdentity) ?: generateIdentity()
    private val sessionKeys = ConcurrentHashMap<String, SecretKeySpec>()

    private fun loadOrCreateIdentity(context: Context): KeyPair {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                IDENTITY_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val publicEncoded = prefs.getString(PUBLIC_KEY, null)?.let {
                Base64.decode(it, Base64.NO_WRAP)
            }
            val privateEncoded = prefs.getString(PRIVATE_KEY, null)?.let {
                Base64.decode(it, Base64.NO_WRAP)
            }
            if (publicEncoded != null && privateEncoded != null) {
                val factory = KeyFactory.getInstance("EC")
                KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(publicEncoded)),
                    factory.generatePrivate(PKCS8EncodedKeySpec(privateEncoded))
                )
            } else {
                generateIdentity().also { generated ->
                    check(
                        prefs.edit()
                            .putString(PUBLIC_KEY, Base64.encodeToString(generated.public.encoded, Base64.NO_WRAP))
                            .putString(PRIVATE_KEY, Base64.encodeToString(generated.private.encoded, Base64.NO_WRAP))
                            .commit()
                    ) { "Unable to persist device identity" }
                }
            }
        } catch (error: Throwable) {
            // Do not make the radio unusable if a device has a broken keystore.
            // The fallback is deliberately ephemeral, so it can never inherit
            // trust from the unavailable persistent identity.
            Log.e(TAG, "Persistent identity unavailable; using an untrusted ephemeral identity", error)
            generateIdentity()
        }
    }

    fun getPublicKeyBytes(): ByteArray {
        return keyPair.public.encoded
    }

    /** Domain-separated key for authenticated mesh envelopes on a verified link. */
    fun meshHopKey(peerId: String): ByteArray? = sessionKeys[peerId]?.encoded?.let {
        TinkCryptoEngine().deriveHopKey(it)
    }

    fun getIdentityKeyHex(): String = getPublicKeyBytes().joinToString("") { "%02x".format(it) }

    fun getIdentityFingerprintHex(): String = fingerprintPublicKey(getPublicKeyBytes())

    fun fingerprintPublicKey(publicKeyBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBytes)
            .joinToString("") { "%02x".format(it) }

    fun deriveSessionKey(peerId: String, peerPublicKeyBytes: ByteArray): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
            
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(keyPair.private)
            keyAgreement.doPhase(peerPublicKey, true)
            
            val sharedSecret = keyAgreement.generateSecret()
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(sharedSecret)
            
            sessionKeys[peerId] = SecretKeySpec(keyBytes, "AES")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Installs a pre-shared or host-distributed symmetric channel voice key directly,
     * avoiding incorrect parsing via X509EncodedKeySpec.
     */
    fun installChannelKey(channelId: Long, keyBytes: ByteArray) {
        if (keyBytes.size >= 16) {
            sessionKeys["channel:$channelId"] = SecretKeySpec(keyBytes, "AES")
        }
    }

    fun hasChannelKey(channelId: Long): Boolean = sessionKeys.containsKey("channel:$channelId")

    fun installDirectKey(keyId: String, keyBytes: ByteArray) {
        if (keyBytes.size >= 16) {
            sessionKeys[keyId] = SecretKeySpec(keyBytes, "AES")
        }
    }

    /**
     * Computes a 6-digit Short Authentication String (SAS) from the derived session key
     * for visual verification between peers (similar to Signal / Apple AirDrop / Vivo pairing).
     */
    fun calculateNumericSas(peerId: String): String {
        val key = sessionKeys[peerId]?.encoded ?: return "------"
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        val num = ((digest[0].toInt() and 0x7F) shl 16) or
                  ((digest[1].toInt() and 0xFF) shl 8) or
                  (digest[2].toInt() and 0xFF)
        return String.format("%06d", num % 1_000_000)
    }

    fun encrypt(peerId: String, plaintext: ByteArray, nonce: ByteArray, aad: ByteArray? = null): Pair<ByteArray, ByteArray>? {
        val secretKey = sessionKeys[peerId] ?: return null
        
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, contextualNonce(nonce, aad))
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            
            if (aad != null) {
                cipher.updateAAD(aad)
            }
            
            val cipherTextWithTag = cipher.doFinal(plaintext)
            val tagSize = 16
            if (cipherTextWithTag.size < tagSize) return null
            
            val cipherText = cipherTextWithTag.copyOfRange(0, cipherTextWithTag.size - tagSize)
            val authTag = cipherTextWithTag.copyOfRange(cipherTextWithTag.size - tagSize, cipherTextWithTag.size)
            
            return Pair(cipherText, authTag)
        } catch (e: Exception) {
            return null
        }
    }

    fun decrypt(peerId: String, ciphertext: ByteArray, authTag: ByteArray, nonce: ByteArray, aad: ByteArray? = null): ByteArray? {
        val secretKey = sessionKeys[peerId] ?: return null
        
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, contextualNonce(nonce, aad))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            
            if (aad != null) {
                cipher.updateAAD(aad)
            }
            
            val combined = ciphertext + authTag
            return cipher.doFinal(combined)
        } catch (e: Exception) {
            return null
        }
    }

    fun hasSessionKey(peerId: String): Boolean = sessionKeys.containsKey(peerId)

    // Voice and control have independent sequence counters. Bind nonce derivation
    // to the authenticated header so equal counters cannot reuse a GCM nonce.
    private fun contextualNonce(nonce: ByteArray, aad: ByteArray?): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("TalkLoop-frame-v2".toByteArray(Charsets.US_ASCII) + nonce + (aad ?: byteArrayOf()))
            .copyOf(12)
    
    fun removeSession(peerId: String) {
        sessionKeys.remove(peerId)
    }
}
