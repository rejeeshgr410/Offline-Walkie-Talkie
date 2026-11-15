package com.example.offlinewalkietalkie.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A public fingerprint is a hint, not proof of possession. Each returning link
 * must answer a fresh encrypted challenge before its candidate key can be used.
 * Mode and sender/recipient identities are authenticated to prevent reflection.
 */
class TrustedReconnectProof(
    private val crypto: CryptoManager,
    private val peerId: String,
    private val peerIdentity: String,
    private val sas: String
) {
    private val random = SecureRandom()
    private val challenge = ByteArray(32).also(random::nextBytes)
    var verified: Boolean = false
        private set

    fun createChallenge(): ByteArray? = seal(REQUEST, challenge)

    fun replyToChallenge(packet: ByteArray): ByteArray? {
        val received = open(REQUEST, packet) ?: return null
        return seal(RESPONSE, received)
    }

    fun verifyResponse(packet: ByteArray): Boolean {
        val response = open(RESPONSE, packet) ?: return false
        if (!MessageDigest.isEqual(challenge, response)) return false
        verified = true
        return true
    }

    private fun aad(mode: Byte, sender: String, recipient: String) =
        "TalkLoop/trusted-reconnect/v1|$mode|$sender|$recipient|$sas".toByteArray(Charsets.UTF_8)

    private fun seal(mode: Byte, body: ByteArray): ByteArray? {
        val nonce = ByteArray(12).also(random::nextBytes)
        val encrypted = crypto.encrypt(peerId, body, nonce,
            aad(mode, crypto.getIdentityFingerprintHex(), peerIdentity)) ?: return null
        return byteArrayOf(mode) + nonce + encrypted.first + encrypted.second
    }

    private fun open(mode: Byte, packet: ByteArray): ByteArray? {
        if (packet.size != 1 + 12 + 32 + 16 || packet[0] != mode) return null
        return crypto.decrypt(peerId, packet.copyOfRange(13, 45), packet.copyOfRange(45, 61),
            packet.copyOfRange(1, 13), aad(mode, peerIdentity, crypto.getIdentityFingerprintHex()))
    }

    companion object {
        const val REQUEST: Byte = 1
        const val RESPONSE: Byte = 2
    }
}
