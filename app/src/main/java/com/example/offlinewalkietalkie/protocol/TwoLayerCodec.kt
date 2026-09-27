package com.example.offlinewalkietalkie.protocol

import com.example.offlinewalkietalkie.crypto.TinkCryptoEngine

/**
 * TwoLayerCodec: Handles cryptographic sealing and unsealing of the
 * hop-by-hop OuterPacket and end-to-end InnerPacket per Section 19.1.
 */
class TwoLayerCodec(private val cryptoEngine: TinkCryptoEngine = TinkCryptoEngine()) {

    /**
     * Seals an InnerPacket into an OuterPacket for transmission over the local radio link.
     */
    fun pack(
        innerPacket: InnerPacket,
        channelKey: ByteArray,
        hopKey: ByteArray,
        senderNodeId: Long
    ): ByteArray {
        // 1. Serialize inner packet plaintext
        val innerPlaintext = innerPacket.encode()

        // 2. End-to-end AEAD encryption with channelKey
        val encryptedInner = cryptoEngine.encrypt(
            key = channelKey,
            nonce = cryptoEngine.generateNonce(),
            plaintext = innerPlaintext
        )

        // 3. Construct OuterPacket
        val outerNonce = cryptoEngine.generateNonce()
        // Encrypt the encryptedInner payload with hopKey
        val encryptedOuterWithTag = cryptoEngine.encrypt(
            key = hopKey,
            nonce = outerNonce,
            plaintext = encryptedInner,
            associatedData = byteArrayOf((senderNodeId ushr 56).toByte(), (senderNodeId ushr 48).toByte())
        )

        // Extract 12-byte nonce (if prepended by Tink), ciphertext, and 16-byte auth tag
        // Tink ChaCha20Poly1305 encrypt output structure: [12-byte nonce][ciphertext][16-byte tag]
        val nonce = encryptedOuterWithTag.copyOfRange(0, 12)
        val ciphertext = encryptedOuterWithTag.copyOfRange(12, encryptedOuterWithTag.size - 16)
        val tag = encryptedOuterWithTag.copyOfRange(encryptedOuterWithTag.size - 16, encryptedOuterWithTag.size)

        val outerPacket = OuterPacket(
            senderNodeId = senderNodeId,
            hopLimit = innerPacket.hopLimit,
            nonce = nonce,
            ciphertext = ciphertext,
            authTag = tag
        )

        return outerPacket.encode()
    }

    /**
     * Decrypts the OuterPacket hop-by-hop layer using hopKey.
     * Intermediate mesh relay nodes can decrypt this without having channelKey.
     */
    fun unpackOuter(
        wireBytes: ByteArray,
        hopKey: ByteArray
    ): Pair<Long, ByteArray>? {
        val decoded = decodeOuter(wireBytes, hopKey) ?: return null
        return Pair(decoded.senderNodeId, decoded.innerCiphertext)
    }

    data class DecodedOuter(
        val senderNodeId: Long,
        val hopLimit: Byte,
        val innerCiphertext: ByteArray
    )

    fun decodeOuter(
        wireBytes: ByteArray,
        hopKey: ByteArray
    ): DecodedOuter? {
        val outerPacket = OuterPacket.decode(wireBytes) ?: return null

        val combinedForTink = outerPacket.nonce + outerPacket.ciphertext + outerPacket.authTag
        val associatedData = byteArrayOf((outerPacket.senderNodeId ushr 56).toByte(), (outerPacket.senderNodeId ushr 48).toByte())

        return try {
            val innerCiphertext = cryptoEngine.decrypt(
                key = hopKey,
                ciphertextWithNonceAndTag = combinedForTink,
                associatedData = associatedData
            )
            DecodedOuter(
                senderNodeId = outerPacket.senderNodeId,
                hopLimit = outerPacket.hopLimit,
                innerCiphertext = innerCiphertext
            )
        } catch (e: Throwable) {
            null // Authentication tag failure or corrupted frame
        }
    }

    /**
     * Decrypts the InnerPacket end-to-end layer using channelKey.
     */
    fun unpackInner(
        innerCiphertext: ByteArray,
        channelKey: ByteArray
    ): InnerPacket? {
        return try {
            val decryptedBytes = cryptoEngine.decrypt(
                key = channelKey,
                ciphertextWithNonceAndTag = innerCiphertext
            )
            InnerPacket.decode(decryptedBytes)
        } catch (e: Throwable) {
            null // Decryption failed or tag mismatch
        }
    }

    /**
     * Re-encrypts the inner ciphertext under a new hop key for the next link in a multi-hop mesh chain.
     * Intermediate relay nodes call this without possessing the end-to-end channelKey (§19.1 & §19.2).
     */
    fun rewrapOuter(
        innerCiphertext: ByteArray,
        newHopKey: ByteArray,
        newSenderNodeId: Long,
        hopLimit: Byte = 3
    ): ByteArray {
        val outerNonce = cryptoEngine.generateNonce()
        val encryptedOuterWithTag = cryptoEngine.encrypt(
            key = newHopKey,
            nonce = outerNonce,
            plaintext = innerCiphertext,
            associatedData = byteArrayOf((newSenderNodeId ushr 56).toByte(), (newSenderNodeId ushr 48).toByte())
        )

        val nonce = encryptedOuterWithTag.copyOfRange(0, 12)
        val ciphertext = encryptedOuterWithTag.copyOfRange(12, encryptedOuterWithTag.size - 16)
        val tag = encryptedOuterWithTag.copyOfRange(encryptedOuterWithTag.size - 16, encryptedOuterWithTag.size)

        val outerPacket = OuterPacket(
            senderNodeId = newSenderNodeId,
            hopLimit = hopLimit,
            nonce = nonce,
            ciphertext = ciphertext,
            authTag = tag
        )

        return outerPacket.encode()
    }
}
