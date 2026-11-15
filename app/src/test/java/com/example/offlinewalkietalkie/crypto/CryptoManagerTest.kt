package com.example.offlinewalkietalkie.crypto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoManagerTest {
    @Test fun equalCountersForDifferentFrameHeadersUseDifferentNonces() {
        val alice = CryptoManager()
        val bob = CryptoManager()
        alice.deriveSessionKey("bob", bob.getPublicKeyBytes())
        bob.deriveSessionKey("alice", alice.getPublicKeyBytes())
        val nonce = ByteArray(12)
        val payload = "payload".toByteArray()
        val voice = alice.encrypt("bob", payload, nonce, byteArrayOf(10))!!
        val text = alice.encrypt("bob", payload, nonce, byteArrayOf(21))!!
        assertFalse(voice.first.contentEquals(text.first))
        assertTrue(payload.contentEquals(bob.decrypt("alice", voice.first, voice.second, nonce, byteArrayOf(10))))
        assertTrue(bob.decrypt("alice", voice.first, voice.second, nonce, byteArrayOf(21)) == null)
    }

    @Test
    fun stableIdentityWithinProcessDerivesSameBidirectionalKey() {
        val alice = CryptoManager()
        val bob = CryptoManager()

        val aliceIdentity = alice.getIdentityFingerprintHex()
        assertTrue(aliceIdentity.isNotBlank())
        assertTrue(aliceIdentity.length == 64)
        assertTrue(aliceIdentity == alice.getIdentityFingerprintHex())

        assertTrue(alice.deriveSessionKey("bob", bob.getPublicKeyBytes()))
        assertTrue(bob.deriveSessionKey("alice", alice.getPublicKeyBytes()))
        assertTrue(alice.calculateNumericSas("bob") == bob.calculateNumericSas("alice"))
    }

    @Test
    fun malformedPeerIdentityCannotCreateSession() {
        val crypto = CryptoManager()

        assertFalse(crypto.deriveSessionKey("attacker", byteArrayOf(1, 2, 3)))
        assertFalse(crypto.hasSessionKey("attacker"))
    }
}
