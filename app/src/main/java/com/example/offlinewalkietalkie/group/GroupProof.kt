package com.example.offlinewalkietalkie.group

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Proves group membership on a link without a person having to compare codes again.
 *
 * The proof is an HMAC over the group secret, bound to three things:
 * - both member ids, so it only counts between these two devices;
 * - the sender's id, so one side cannot reflect the other's proof back;
 * - the Nearby authentication code of this exact connection. A device relaying between
 *   two victims holds two separate connections with different codes, so a proof it
 *   forwards from one leg is wrong on the other.
 */
object GroupProof {

    const val SECRET_BYTES = 16

    fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also { SecureRandom().nextBytes(it) }

    fun compute(secret: ByteArray, senderId: String, peerId: String, linkCode: String): String {
        val (low, high) = if (senderId < peerId) senderId to peerId else peerId to senderId
        val message = "TLv1|$low|$high|$linkCode|$senderId".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message).toHex()
    }

    /** Constant-time comparison so timing does not leak how much of a guess was right. */
    fun verify(
        secret: ByteArray,
        senderId: String,
        peerId: String,
        linkCode: String,
        proofHex: String
    ): Boolean {
        val expected = compute(secret, senderId, peerId, linkCode)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII),
            proofHex.toByteArray(Charsets.US_ASCII)
        )
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } catch (e: NumberFormatException) {
            null
        }
    }
}
