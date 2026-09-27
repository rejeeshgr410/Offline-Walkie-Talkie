package com.example.offlinewalkietalkie.mesh

import java.util.concurrent.ConcurrentHashMap

data class MeshNeighbor(
    val nodeId: Long,
    val endpointId: String,
    val linkQuality: Int = 100, // 0-100%
    val isRelay: Boolean = false,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)

data class MeshRoute(
    val destinationNodeId: Long,
    val nextHopNodeId: Long,
    val nextHopEndpointId: String,
    val hopCount: Int,
    val expiresEpochMs: Long
)

/**
 * Section 1 & Section 3 Bounded Forwarding Tree and Route Table.
 *
 * Enforces:
 * - Maximum hop limit: 3 to 5 hops (§2 release targets).
 * - Bounded forwarding tree maintenance.
 * - Route expiry and next-hop selection (avoiding echoing back to ingress link).
 */
class MeshRouteTable(
    val maxHopLimit: Int = 3
) {
    companion object {
        const val DEFAULT_ROUTE_TTL_MS = 30_000L // 30s route expiry
    }

    // Key: endpointId -> MeshNeighbor
    private val neighbors = ConcurrentHashMap<String, MeshNeighbor>()

    // Key: destinationNodeId -> MeshRoute
    private val routes = ConcurrentHashMap<Long, MeshRoute>()

    fun addOrUpdateNeighbor(
        nodeId: Long,
        endpointId: String,
        linkQuality: Int = 100,
        isRelay: Boolean = false
    ) {
        val neighbor = MeshNeighbor(
            nodeId = nodeId,
            endpointId = endpointId,
            linkQuality = linkQuality,
            isRelay = isRelay,
            lastSeenEpochMs = System.currentTimeMillis()
        )
        neighbors[endpointId] = neighbor

        // Immediate direct neighbor is always a 1-hop route
        addRoute(
            destinationNodeId = nodeId,
            nextHopNodeId = nodeId,
            nextHopEndpointId = endpointId,
            hopCount = 1
        )
    }

    fun removeNeighbor(endpointId: String) {
        val neighbor = neighbors.remove(endpointId)
        if (neighbor != null) {
            // Invalidate routes using this neighbor as next hop
            val routesToRemove = routes.filter { it.value.nextHopEndpointId == endpointId }.keys
            routesToRemove.forEach { routes.remove(it) }
        }
    }

    fun addRoute(
        destinationNodeId: Long,
        nextHopNodeId: Long,
        nextHopEndpointId: String,
        hopCount: Int,
        ttlMs: Long = DEFAULT_ROUTE_TTL_MS
    ): Boolean {
        // Enforce maximum bounded hop limit (§2)
        if (hopCount > maxHopLimit) {
            return false
        }

        val existing = routes[destinationNodeId]
        if (existing == null || hopCount <= existing.hopCount || System.currentTimeMillis() > existing.expiresEpochMs) {
            routes[destinationNodeId] = MeshRoute(
                destinationNodeId = destinationNodeId,
                nextHopNodeId = nextHopNodeId,
                nextHopEndpointId = nextHopEndpointId,
                hopCount = hopCount,
                expiresEpochMs = System.currentTimeMillis() + ttlMs
            )
            return true
        }
        return false
    }

    fun getNextHop(destinationNodeId: Long): MeshRoute? {
        pruneExpiredRoutes()
        return routes[destinationNodeId]
    }

    /**
     * Next hops for distributing broadcast channel traffic down the bounded forwarding tree.
     * Excludes the ingress link to prevent loops / echoes.
     */
    fun getForwardingNextHops(excludeEndpointId: String? = null): List<String> {
        return neighbors.values
            .map { it.endpointId }
            .filter { excludeEndpointId == null || it != excludeEndpointId }
    }

    fun getNeighborByEndpoint(endpointId: String): MeshNeighbor? = neighbors[endpointId]

    fun getAllNeighbors(): List<MeshNeighbor> = neighbors.values.toList()

    fun pruneExpiredRoutes() {
        val now = System.currentTimeMillis()
        val expired = routes.filter { it.value.expiresEpochMs <= now }.keys
        expired.forEach { routes.remove(it) }
    }

    fun clear() {
        neighbors.clear()
        routes.clear()
    }
}
