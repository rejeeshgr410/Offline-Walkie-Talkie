package com.example.offlinewalkietalkie.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {
    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val NONCE_SIZE = 12
    }

    private val keyPair: KeyPair
    private val sessionKeys = mutableMapOf<String, SecretKeySpec>()

    init {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        keyPair = kpg.generateKeyPair()
    }

    fun getPublicKeyBytes(): ByteArray {
        return keyPair.public.encoded
    }

    fun deriveSessionKey(peerId: String, peerPublicKeyBytes: ByteArray) {
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)
        
        val sharedSecret = keyAgreement.generateSecret()
        
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret)
        
        // AES keys are typically 16, 24, or 32 bytes. SHA-256 gives 32 bytes, perfect for AES-256.
        sessionKeys[peerId] = SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(peerId: String, plaintext: ByteArray, nonce: ByteArray, aad: ByteArray? = null): Pair<ByteArray, ByteArray>? {
        val secretKey = sessionKeys[peerId] ?: return null
        
        try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, nonce)
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
            val gcmSpec = javax.crypto.spec.GCMParameterSpec(128, nonce)
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
    
    fun removeSession(peerId: String) {
        sessionKeys.remove(peerId)
    }
}
