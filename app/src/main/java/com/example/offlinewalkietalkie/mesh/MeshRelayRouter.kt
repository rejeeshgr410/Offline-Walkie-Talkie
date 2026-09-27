package com.example.offlinewalkietalkie.mesh

import com.example.offlinewalkietalkie.protocol.InnerPacket
import com.example.offlinewalkietalkie.protocol.TwoLayerCodec
import java.util.concurrent.ConcurrentHashMap

sealed class MeshOutcome {
    data class DeliveredLocally(val innerPacket: InnerPacket) : MeshOutcome()
    data class Forwarded(
        val nextHopEndpointIds: List<String>,
        val rewrappedPackets: Map<String, ByteArray>,
        val remainingHopLimit: Byte
    ) : MeshOutcome()
    object DroppedDuplicate : MeshOutcome()
    object DroppedHopLimitExceeded : MeshOutcome()
    object DroppedAuthFailed : MeshOutcome()
    object DroppedNoRoute : MeshOutcome()
}

/**
 * Section 1, Section 3, Section 19.1 & Section 19.2 Multi-Hop Mesh Relay Router.
 *
 * Enforces:
 * - Hop-by-hop OuterPacket re-encryption across intermediate relays without voice keys (§19.1).
 * - Bounded forwarding tree routing and loop prevention via SeenPacketCache.
 * - Hop limit decrement and enforcement (§2: initial 3, max 5).
 * - Honest relay role: relays forward authenticated ciphertext without decrypting voice (§19.2).
 */
class MeshRelayRouter(
    val localNodeId: Long,
    val maxHopLimit: Int = 3,
    private val codec: TwoLayerCodec = TwoLayerCodec()
) {
    val routeTable = MeshRouteTable(maxHopLimit = maxHopLimit)
    val seenCache = SeenPacketCache()

    // EndpointId -> 32-byte hopKey derived during neighbor link handshake
    val linkKeys = ConcurrentHashMap<String, ByteArray>()

    // Channel voice key: present ONLY if this node is an authorized channel member; null for relay-only nodes (§19.2)
    var channelVoiceKey: ByteArray? = null

    var isRelayEnabled: Boolean = true

    // Diagnostics counters
    var totalPacketsReceived = 0
    var totalPacketsRelayed = 0
    var totalDuplicatesDropped = 0
    var totalHopLimitDropped = 0
    var totalAuthFailedDropped = 0

    fun registerNeighborLink(endpointId: String, neighborNodeId: Long, hopKey: ByteArray, isRelay: Boolean = false) {
        linkKeys[endpointId] = hopKey
        routeTable.addOrUpdateNeighbor(
            nodeId = neighborNodeId,
            endpointId = endpointId,
            isRelay = isRelay
        )
    }

    fun unregisterNeighborLink(endpointId: String) {
        linkKeys.remove(endpointId)
        routeTable.removeNeighbor(endpointId)
    }

    /**
     * Process an incoming wire-level mesh packet from a neighboring radio endpoint.
     */
    fun processPacket(fromEndpointId: String, wireBytes: ByteArray): MeshOutcome {
        totalPacketsReceived++

        // 1. Authenticate hop-by-hop outer layer using the link key for this neighbor
        val linkKey = linkKeys[fromEndpointId]
        if (linkKey == null) {
            totalAuthFailedDropped++
            return MeshOutcome.DroppedAuthFailed
        }

        val decodedOuter = codec.decodeOuter(wireBytes, linkKey)
        if (decodedOuter == null) {
            totalAuthFailedDropped++
            return MeshOutcome.DroppedAuthFailed
        }

        // 2. Check hop limit
        if (decodedOuter.hopLimit <= 0) {
            totalHopLimitDropped++
            return MeshOutcome.DroppedHopLimitExceeded
        }

        // 3. Deduplication check using inner ciphertext fingerprint
        val cipherFingerprint = java.util.Arrays.hashCode(decodedOuter.innerCiphertext)
        if (seenCache.isDuplicate(decodedOuter.senderNodeId, cipherFingerprint)) {
            totalDuplicatesDropped++
            return MeshOutcome.DroppedDuplicate
        }

        // 4. Try local delivery if this node holds the channel key
        val localKey = channelVoiceKey
        if (localKey != null) {
            val inner = codec.unpackInner(decodedOuter.innerCiphertext, localKey)
            if (inner != null) {
                // If hop limit > 1 and relay is enabled, also forward down the tree to other peers
                if (isRelayEnabled && decodedOuter.hopLimit > 1) {
                    val remainingHops = (decodedOuter.hopLimit - 1).toByte()
                    val nextHops = routeTable.getForwardingNextHops(excludeEndpointId = fromEndpointId)
                    if (nextHops.isNotEmpty()) {
                        val rewrapped = mutableMapOf<String, ByteArray>()
                        for (nh in nextHops) {
                            val nhKey = linkKeys[nh] ?: continue
                            rewrapped[nh] = codec.rewrapOuter(
                                innerCiphertext = decodedOuter.innerCiphertext,
                                newHopKey = nhKey,
                                newSenderNodeId = localNodeId,
                                hopLimit = remainingHops
                            )
                        }
                        if (rewrapped.isNotEmpty()) {
                            totalPacketsRelayed++
                            // Both delivered locally and forwarded
                        }
                    }
                }
                return MeshOutcome.DeliveredLocally(inner)
            }
        }

        // 5. Relay-Only behavior (§19.1 & §19.2):
        // Node does NOT possess channelKey. It re-encrypts the outer envelope for next-hop links.
        if (!isRelayEnabled) {
            return MeshOutcome.DroppedNoRoute
        }

        val remainingHops = (decodedOuter.hopLimit - 1).toByte()
        if (remainingHops <= 0) {
            totalHopLimitDropped++
            return MeshOutcome.DroppedHopLimitExceeded
        }

        val nextHops = routeTable.getForwardingNextHops(excludeEndpointId = fromEndpointId)
        if (nextHops.isEmpty()) {
            return MeshOutcome.DroppedNoRoute
        }

        val rewrapped = mutableMapOf<String, ByteArray>()
        for (nh in nextHops) {
            val nhKey = linkKeys[nh] ?: continue
            rewrapped[nh] = codec.rewrapOuter(
                innerCiphertext = decodedOuter.innerCiphertext,
                newHopKey = nhKey,
                newSenderNodeId = localNodeId,
                hopLimit = remainingHops
            )
        }

        if (rewrapped.isEmpty()) {
            return MeshOutcome.DroppedNoRoute
        }

        totalPacketsRelayed++
        return MeshOutcome.Forwarded(
            nextHopEndpointIds = nextHops,
            rewrappedPackets = rewrapped,
            remainingHopLimit = remainingHops
        )
    }

    /**
     * Originates a new packet from this node for transmission down the bounded forwarding tree.
     */
    fun originatePacket(innerPacket: InnerPacket, channelKey: ByteArray): Map<String, ByteArray> {
        seenCache.markSeen(localNodeId, innerPacket.sequence)

        val nextHops = routeTable.getForwardingNextHops()
        val packets = mutableMapOf<String, ByteArray>()

        for (nh in nextHops) {
            val nhKey = linkKeys[nh] ?: continue
            val wireBytes = codec.pack(
                innerPacket = innerPacket,
                channelKey = channelKey,
                hopKey = nhKey,
                senderNodeId = localNodeId
            )
            packets[nh] = wireBytes
        }
        return packets
    }

    fun reset() {
        linkKeys.clear()
        routeTable.clear()
        seenCache.clear()
        channelVoiceKey = null
        totalPacketsReceived = 0
        totalPacketsRelayed = 0
        totalDuplicatesDropped = 0
        totalHopLimitDropped = 0
        totalAuthFailedDropped = 0
    }
}
