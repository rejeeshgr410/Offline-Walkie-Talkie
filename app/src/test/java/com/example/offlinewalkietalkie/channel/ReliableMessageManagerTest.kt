package com.example.offlinewalkietalkie.channel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReliableMessageManagerTest {

    @Test
    fun testReliableSendAndAckSuccess() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var sentPackets = 0
        val manager = ReliableMessageManager(
            sendRawPacket = { _, _ ->
                sentPackets++
                true
            },
            scope = testScope
        )

        var delivered = false
        var failed = false

        manager.sendReliable(
            memberId = 100L,
            sequence = 1,
            endpointId = "ep1",
            packetBytes = byteArrayOf(1, 2, 3),
            onDelivered = { delivered = true },
            onFailed = { failed = true }
        )

        testDispatcher.scheduler.advanceTimeBy(10)
        assertEquals(1, sentPackets)
        assertFalse(delivered)

        // Peer sends ACK for seq 1
        manager.onAckReceived(100L, 1)

        assertTrue("Should be marked delivered upon receiving ACK", delivered)
        assertFalse(failed)
    }

    @Test
    fun testRetransmitBackoffAndFailureAfter3Attempts() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var sendAttempts = 0
        val manager = ReliableMessageManager(
            sendRawPacket = { _, _ ->
                sendAttempts++
                true
            },
            scope = testScope
        )

        var failed = false
        manager.sendReliable(
            memberId = 200L,
            sequence = 2,
            endpointId = "ep2",
            packetBytes = byteArrayOf(4, 5, 6),
            onDelivered = {},
            onFailed = { failed = true }
        )

        // Attempt 0 (initial)
        testDispatcher.scheduler.advanceTimeBy(10)
        assertEquals(1, sendAttempts)

        // Attempt 1 after 300ms
        testDispatcher.scheduler.advanceTimeBy(300)
        assertEquals(2, sendAttempts)

        // Attempt 2 after 600ms
        testDispatcher.scheduler.advanceTimeBy(600)
        assertEquals(3, sendAttempts)

        // Attempt 3 after 1200ms
        testDispatcher.scheduler.advanceTimeBy(1200)
        assertEquals(4, sendAttempts)

        // Advance to failure
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Message should fail after 3 retries without ACK", failed)
    }

    @Test
    fun testDeduplicationCacheFiltersDuplicates() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val manager = ReliableMessageManager(
            sendRawPacket = { _, _ -> true },
            scope = testScope
        )

        var ackSentCount = 0
        val sendAck: suspend () -> Unit = {
            ackSentCount++
        }

        // First arrival of frame (seq 5 from member 300)
        val firstProcessed = manager.processIncomingReliable(300L, 5, sendAck)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("First packet should be processed", firstProcessed)
        assertEquals(1, ackSentCount)

        // Duplicate arrival of same frame
        val duplicateProcessed = manager.processIncomingReliable(300L, 5, sendAck)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Duplicate packet must be dropped from application processing", duplicateProcessed)
        assertEquals(2, ackSentCount) // ACK should still be sent to confirm receipt to peer
    }
}
