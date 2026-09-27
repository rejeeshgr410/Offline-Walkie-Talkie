package com.example.offlinewalkietalkie.ui.main

import com.example.offlinewalkietalkie.data.TrustedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedContactTest {

    @Test
    fun testTrustedContactJsonSerialization() {
        val contact = TrustedContact(
            contactId = "contact-101",
            displayName = "Dan",
            publicKeyHex = "0123456789ABCDEF",
            sasCode = "482915",
            isVerified = true,
            lastSeen = 1700000000L
        )

        val json = contact.toJson()
        val restored = TrustedContact.fromJson(json)

        assertEquals(contact.contactId, restored.contactId)
        assertEquals(contact.displayName, restored.displayName)
        assertEquals(contact.publicKeyHex, restored.publicKeyHex)
        assertEquals(contact.sasCode, restored.sasCode)
        assertTrue(restored.isVerified)
        assertEquals(contact.lastSeen, restored.lastSeen)
    }
}
