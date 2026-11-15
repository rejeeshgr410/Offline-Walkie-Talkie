package com.example.offlinewalkietalkie.data

import com.example.offlinewalkietalkie.channel.*
import org.junit.Assert.*
import org.junit.Test

class TextMessageStoreTest {
    @Test fun interruptedOutgoingRemainsOwnedAndRetryable() {
        val message = TextMessage(sequence = 7, senderMemberId = 99, senderName = "Me",
            content = "مرحبا 👋", isOutgoing = true)
        val restored = TextMessageStore.decode(TextMessageStore.encode(listOf(message))).single()
        assertTrue(restored.isOutgoing)
        assertEquals(TextDeliveryStatus.FAILED, restored.status)
        assertEquals(message.messageId, restored.messageId)
        assertEquals(message.content, restored.content)
    }
    @Test fun readStatusAndDeliverySurviveRestart() {
        val message = TextMessage(sequence = 1, senderMemberId = 8, senderName = "Peer",
            content = "Received", status = TextDeliveryStatus.DELIVERED, isRead = true)
        assertEquals(message, TextMessageStore.decode(TextMessageStore.encode(listOf(message))).single())
        assertTrue(TextMessageStore.decode("corrupted").isEmpty())
    }
}
