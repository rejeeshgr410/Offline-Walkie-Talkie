package com.example.offlinewalkietalkie.identity

import com.example.offlinewalkietalkie.data.TrustedContact
import com.example.offlinewalkietalkie.transport.ConnectionRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DirectPeerTrustTest {
    private val identity = "a".repeat(64)
    private val contact = TrustedContact("contact", "PIXEL", identity, "123456", true)
    private fun decision(name: String, key: String?, verified: Boolean = true) =
        DirectPeerTrust.evaluate(ConnectionRequest("rotated-ble-address", name, "654321", key),
            listOf(contact.copy(isVerified = verified)))

    @Test fun returningVerifiedKeySurvivesAddressNameAndSasChanges() {
        assertEquals(DirectPeerTrust.Decision.TRUSTED, decision("renamed phone", identity))
    }

    @Test fun knownNameWithChangedOrMissingIdentityIsBlocked() {
        assertEquals(DirectPeerTrust.Decision.IDENTITY_CHANGED, decision(" pixel ", "b".repeat(64)))
        assertEquals(DirectPeerTrust.Decision.IDENTITY_CHANGED, decision("pixel", null))
    }

    @Test fun untrustedOrUnverifiedPeerStillRequiresFreshSas() {
        assertEquals(DirectPeerTrust.Decision.REQUIRE_SAS, decision("stranger", "b".repeat(64)))
        assertEquals(DirectPeerTrust.Decision.REQUIRE_SAS, decision("PIXEL", identity, false))
        assertEquals(DirectPeerTrust.Decision.REQUIRE_SAS, decision("stranger", null))
        assertEquals(DirectPeerTrust.Decision.REQUIRE_SAS, decision("stranger", "malformed"))
    }

    @Test fun identityMatchWinsOverNonUniqueDisplayName() {
        val other = contact.copy(contactId = "other", publicKeyHex = "b".repeat(64))
        assertEquals(DirectPeerTrust.Decision.TRUSTED,
            DirectPeerTrust.evaluate(ConnectionRequest("new-address", "PIXEL", "123456", identity), listOf(other, contact)))
    }

    @Test fun nameChangeAlarmDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(DirectPeerTrust.Decision.IDENTITY_CHANGED, decision("pixel", "b".repeat(64)))
        } finally { Locale.setDefault(previous) }
    }
}
