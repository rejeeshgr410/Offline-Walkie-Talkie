package com.example.offlinewalkietalkie.crypto

import org.junit.Assert.*
import org.junit.Test

class TrustedReconnectProofTest {
    private val alice = CryptoManager()
    private val bob = CryptoManager()

    private fun aliceProof(code: String = "123456") =
        TrustedReconnectProof(alice, "bob", bob.getIdentityFingerprintHex(), code)
    private fun bobProof(code: String = "123456") =
        TrustedReconnectProof(bob, "alice", alice.getIdentityFingerprintHex(), code)

    private fun deriveKeys() {
        assertTrue(alice.deriveSessionKey("bob", bob.getPublicKeyBytes()))
        assertTrue(bob.deriveSessionKey("alice", alice.getPublicKeyBytes()))
    }

    @Test fun trustedPeersRequirePrivateKeyPossessionBeforeVerification() {
        val a = aliceProof()
        val b = bobProof()
        assertNull(a.createChallenge())
        deriveKeys()
        assertFalse(a.verified) // Receiving the public AUTH alone grants nothing.
        val aChallenge = a.createChallenge()!!
        val bChallenge = b.createChallenge()!!
        assertTrue(a.verifyResponse(b.replyToChallenge(aChallenge)!!))
        assertTrue(b.verifyResponse(a.replyToChallenge(bChallenge)!!))
        assertTrue(a.verified)
        assertTrue(b.verified)
    }

    @Test fun copiedFingerprintAndPublicKeyCannotAnswerChallenge() {
        deriveKeys()
        val attacker = CryptoManager()
        // An impostor can derive its own ECDH key using Alice's public identity,
        // but cannot derive the Alice/Bob shared key even if it claims Bob's name.
        assertTrue(attacker.deriveSessionKey("alice", alice.getPublicKeyBytes()))
        val impostor = TrustedReconnectProof(attacker, "alice", alice.getIdentityFingerprintHex(), "123456")
        assertNull(impostor.replyToChallenge(aliceProof().createChallenge()!!))
    }

    @Test fun changedIdentityCannotDecryptBoundProof() {
        deriveKeys()
        val wrongIdentity = TrustedReconnectProof(bob, "alice", "c".repeat(64), "123456")
        assertNull(wrongIdentity.replyToChallenge(aliceProof().createChallenge()!!))
    }

    @Test fun replayAcrossReconnectOrFreshSasFails() {
        deriveKeys()
        val oldAttempt = aliceProof()
        val oldResponse = bobProof().replyToChallenge(oldAttempt.createChallenge()!!)!!
        assertFalse(aliceProof().verifyResponse(oldResponse))
        assertFalse(aliceProof("654321").verifyResponse(oldResponse))
        assertNull(bobProof("654321").replyToChallenge(oldAttempt.createChallenge()!!))
    }

    @Test fun reflectionTamperingAndMalformedPacketsFailClosed() {
        deriveKeys()
        val a = aliceProof()
        val challenge = a.createChallenge()!!
        assertFalse(a.verifyResponse(challenge))
        val reflected = challenge.copyOf().also { it[0] = TrustedReconnectProof.RESPONSE }
        assertFalse(a.verifyResponse(reflected))
        val response = bobProof().replyToChallenge(challenge)!!
        response[20] = (response[20].toInt() xor 1).toByte()
        assertFalse(a.verifyResponse(response))
        assertFalse(a.verifyResponse(byteArrayOf()))
        assertNull(a.replyToChallenge(ByteArray(100)))
        assertFalse(a.verified)
    }

    @Test fun freshSasPeerCanAnswerReturningPeersProofAfterManualApproval() {
        deriveKeys()
        // A peer with cleared contacts still handles proof packets after its
        // local SAS approval; trust need not be symmetric in the two stores.
        val returning = aliceProof()
        val freshlyPaired = bobProof()
        assertTrue(returning.verifyResponse(freshlyPaired.replyToChallenge(returning.createChallenge()!!)!!))
    }

    @Test fun removedSessionCannotAnswerAndRetryCanRecoverLostPackets() {
        deriveKeys()
        val a = aliceProof()
        val b = bobProof()
        val lostChallenge = a.createChallenge()!!
        bob.removeSession("alice")
        assertNull(b.replyToChallenge(lostChallenge))
        assertTrue(bob.deriveSessionKey("alice", alice.getPublicKeyBytes()))
        assertTrue(a.verifyResponse(b.replyToChallenge(a.createChallenge()!!)!!))
    }
}
