package com.example.offlinewalkietalkie.channel

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QrJoinManagerTest {

    private lateinit var qrManager: QrJoinManager

    @Before
    fun setUp() {
        qrManager = QrJoinManager(
            channelId = 123456789L,
            channelName = "TestChannel",
            hostFingerprint = "A1B2C3D4E5F6"
        )
    }

    @Test
    fun testInitialJoinDataCreation() {
        val data = qrManager.currentJoinData.value
        assertEquals(123456789L, data.channelId)
        assertEquals("TestChannel", data.channelName)
        assertEquals("A1B2C3D4E5F6", data.hostFingerprint)
        assertEquals(32, data.oneTimeSecret.length) // 16 bytes hex = 32 chars
        assertEquals(6, data.shortCode.length)
        assertTrue(data.expiresEpochMs > System.currentTimeMillis())
        assertTrue(qrManager.remainingSeconds() in 590..600)
    }

    @Test
    fun testJsonSerializationAndDeserialization() {
        val original = qrManager.currentJoinData.value
        val jsonStr = original.toJson()
        val deserialized = QrJoinData.fromJson(jsonStr)

        assertNotNull(deserialized)
        assertEquals(original.channelId, deserialized!!.channelId)
        assertEquals(original.channelName, deserialized.channelName)
        assertEquals(original.hostFingerprint, deserialized.hostFingerprint)
        assertEquals(original.oneTimeSecret, deserialized.oneTimeSecret)
        assertEquals(original.shortCode, deserialized.shortCode)
        assertEquals(original.expiresEpochMs, deserialized.expiresEpochMs)
    }

    @Test
    fun testValidOneTimeSecretConsumesAndAutoRotates() {
        val secret1 = qrManager.currentJoinData.value.oneTimeSecret
        val code1 = qrManager.currentJoinData.value.shortCode

        // Valid secret verify
        val success = qrManager.verifyAndConsumeSecret(secret1)
        assertTrue(success)

        // Secret should have been rotated immediately (§19.10)
        val secret2 = qrManager.currentJoinData.value.oneTimeSecret
        val code2 = qrManager.currentJoinData.value.shortCode
        assertNotEquals(secret1, secret2)
        assertNotEquals(code1, code2)

        // Previous secret is now invalid
        val retry = qrManager.verifyAndConsumeSecret(secret1)
        assertFalse(retry)
    }

    @Test
    fun testValidShortCodeConsumesAndAutoRotates() {
        val code1 = qrManager.currentJoinData.value.shortCode

        // Verify case-insensitive short code
        val success = qrManager.verifyAndConsumeShortCode(code1.lowercase())
        assertTrue(success)

        // Rotated to new short code
        val code2 = qrManager.currentJoinData.value.shortCode
        assertNotEquals(code1, code2)

        // Replay of old short code fails
        assertFalse(qrManager.verifyAndConsumeShortCode(code1))
    }

    @Test
    fun testInvalidSecretFails() {
        val success = qrManager.verifyAndConsumeSecret("invalid_secret_value")
        assertFalse(success)
    }
}
