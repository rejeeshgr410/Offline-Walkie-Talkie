package com.example.offlinewalkietalkie.channel

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 19.8 Reliable Control and Text Message Delivery.
 *
 * Implements:
 * - Acknowledgements (ACK)
 * - Bounded retransmission: 3 attempts at 300 ms, 600 ms, 1200 ms backoff.
 * - Deduplication by PacketIdentity (memberId + sequence).
 */
class ReliableMessageManager(
    private val sendRawPacket: suspend (endpointId: String, packet: ByteArray) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        val RETRY_BACKOFF_MS = listOf(300L, 600L, 1200L)
        const val MAX_RETRIES = 3
        const val DEDUPE_CACHE_LIMIT = 500
    }

    private data class PendingDelivery(
        val memberId: Long,
        val sequence: Int,
        val endpointId: String,
        val packetBytes: ByteArray,
        var attempt: Int = 0,
        var retryJob: Job? = null,
        val onDelivered: () -> Unit,
        val onFailed: (reason: String) -> Unit
    )

    // Key includes the endpoint because one text message can be sent to several peers.
    private val pendingAcks = ConcurrentHashMap<String, PendingDelivery>()

    // Key: "memberId:sequence" -> Timestamp of receipt
    private val dedupeCache = ConcurrentHashMap<String, Long>()

    /**
     * Send a reliable packet with automated retry backoff.
     */
    fun sendReliable(
        memberId: Long,
        sequence: Int,
        endpointId: String,
        packetBytes: ByteArray,
        onDelivered: () -> Unit = {},
        onFailed: (reason: String) -> Unit = {}
    ) {
        val key = "$memberId:$sequence:$endpointId"
        val pending = PendingDelivery(
            memberId = memberId,
            sequence = sequence,
            endpointId = endpointId,
            packetBytes = packetBytes,
            attempt = 0,
            onDelivered = onDelivered,
            onFailed = onFailed
        )
        pendingAcks[key] = pending

        scheduleAttempt(key, pending)
    }

    private fun scheduleAttempt(key: String, pending: PendingDelivery) {
        pending.retryJob?.cancel()
        pending.retryJob = scope.launch {
            // Initial or retransmission send
            sendRawPacket(pending.endpointId, pending.packetBytes)

            if (pending.attempt < MAX_RETRIES) {
                val delayMs = RETRY_BACKOFF_MS.getOrElse(pending.attempt) { 1200L }
                pending.attempt++
                delay(delayMs)
                // Check if still waiting for ACK
                if (pendingAcks.containsKey(key)) {
                    scheduleAttempt(key, pending)
                }
            } else {
                // Max retries exceeded without ACK
                pendingAcks.remove(key)
                pending.onFailed("Delivery timed out after 3 attempts")
            }
        }
    }

    /**
     * Called when an incoming ACK frame is received.
     */
    fun onAckReceived(memberId: Long, sequence: Int, endpointId: String) {
        val key = "$memberId:$sequence:$endpointId"
        val pending = pendingAcks.remove(key)
        if (pending != null) {
            pending.retryJob?.cancel()
            pending.onDelivered()
        }
    }

    /**
     * Verify incoming reliable frame.
     * @return true if packet is fresh and should be processed; false if it's a duplicate.
     */
    fun processIncomingReliable(
        senderMemberId: Long,
        sequence: Int,
        sendAckResponse: suspend () -> Unit
    ): Boolean {
        val key = "$senderMemberId:$sequence"
        val now = System.currentTimeMillis()

        // Always reply with ACK so the sender knows we received it
        scope.launch {
            try {
                sendAckResponse()
            } catch (e: Exception) {}
        }

        // Check if already in dedupe cache
        if (dedupeCache.containsKey(key)) {
            return false // Duplicate: drop processing
        }

        // Clean cache if too large
        if (dedupeCache.size > DEDUPE_CACHE_LIMIT) {
            val oldest = dedupeCache.entries.sortedBy { it.value }.take(100)
            oldest.forEach { dedupeCache.remove(it.key) }
        }

        dedupeCache[key] = now
        return true // Fresh packet
    }

    fun reset() {
        pendingAcks.values.forEach { it.retryJob?.cancel() }
        pendingAcks.clear()
        dedupeCache.clear()
    }
}
