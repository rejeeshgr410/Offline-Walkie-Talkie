package com.example.offlinewalkietalkie.group

/**
 * An in-memory radio environment for driving many [GroupSessionManager]s against each
 * other. Physical multi-device testing needs one phone per node; this exercises the same
 * logic for 2, 3, 4, 5+ nodes deterministically.
 *
 * Models the real Nearby handshake shape: connect() raises a request on the far side,
 * and both sides only see onConnected once that side accepts.
 */
class MeshSimulator(private val clock: TestClock = TestClock()) {

    val now: TestClock get() = clock

    inner class Node(val memberId: String, val displayName: String) {
        lateinit var manager: GroupSessionManager
        var advert: GroupAdvert? = null

        /** Set false to simulate a phone going out of range or powering off. */
        var reachable: Boolean = true

        val connectCalls = mutableListOf<String>()
        val acceptCalls = mutableListOf<String>()
        val sentControl = mutableListOf<Pair<String, ByteArray>>()

        val endpointId: String get() = endpointFor(memberId)

        val port = object : GroupTransportPort {
            override suspend fun connect(endpointId: String) {
                connectCalls.add(endpointId)
                deliverConnectRequest(this@Node, endpointId)
            }

            override suspend fun acceptConnection(endpointId: String) {
                acceptCalls.add(endpointId)
                completeConnection(this@Node, endpointId)
            }

            override suspend fun rejectConnection(endpointId: String) {
                links.remove(linkKey(this@Node.endpointId, endpointId))
            }

            override suspend fun disconnect(endpointId: String) {
                breakLink(this@Node, endpointId)
            }

            override suspend fun sendControl(endpointId: String, payload: ByteArray) {
                sentControl.add(endpointId to payload)
                deliverControl(this@Node, endpointId, payload)
            }

            override suspend fun publishAdvert(advert: GroupAdvert) {
                this@Node.advert = advert
            }

            override suspend fun clearAdvert() {
                this@Node.advert = null
            }
        }
    }

    private val nodes = linkedMapOf<String, Node>()
    private val links = mutableSetOf<String>()

    fun endpointFor(memberId: String) = "EP_$memberId"

    fun nodeOf(memberId: String): Node = nodes.getValue(memberId)

    fun allNodes(): List<Node> = nodes.values.toList()

    fun addNode(memberId: String, displayName: String = "Phone-$memberId"): Node {
        val node = Node(memberId, displayName)
        node.manager = GroupSessionManager(
            transport = node.port,
            localDisplayName = displayName,
            localMemberId = memberId,
            now = { clock.millis },
            idFactory = { len -> "G".repeat(len) }
        )
        nodes[memberId] = node
        return node
    }

    private fun nodeByEndpoint(endpointId: String): Node? =
        nodes.values.find { it.endpointId == endpointId }

    private fun linkKey(a: String, b: String) = listOf(a, b).sorted().joinToString("~")

    fun isLinked(a: String, b: String) =
        links.contains(linkKey(endpointFor(a), endpointFor(b)))

    fun linkCount() = links.size

    /** Every reachable node sees every other reachable node's advert. */
    suspend fun discoverAll() {
        val visible = nodes.values.filter { it.reachable }
        for (observer in visible) {
            for (target in visible) {
                if (observer.memberId == target.memberId) continue
                val advert = target.advert ?: continue
                observer.manager.onEndpointDiscovered(target.endpointId, advert)
            }
        }
    }

    private suspend fun deliverConnectRequest(from: Node, toEndpointId: String) {
        val target = nodeByEndpoint(toEndpointId) ?: return
        if (!target.reachable || !from.reachable) return
        target.manager.onConnectionRequest(from.endpointId, from.advert)
    }

    private suspend fun completeConnection(accepter: Node, initiatorEndpointId: String) {
        val initiator = nodeByEndpoint(initiatorEndpointId) ?: return
        if (!initiator.reachable || !accepter.reachable) return
        val key = linkKey(accepter.endpointId, initiator.endpointId)
        if (!links.add(key)) return
        initiator.manager.onConnected(accepter.endpointId)
        accepter.manager.onConnected(initiator.endpointId)
    }

    private suspend fun breakLink(from: Node, toEndpointId: String) {
        val target = nodeByEndpoint(toEndpointId) ?: return
        if (!links.remove(linkKey(from.endpointId, target.endpointId))) return
        target.manager.onDisconnected(from.endpointId)
    }

    private suspend fun deliverControl(from: Node, toEndpointId: String, payload: ByteArray) {
        val target = nodeByEndpoint(toEndpointId) ?: return
        if (!target.reachable || !from.reachable) return
        if (!links.contains(linkKey(from.endpointId, target.endpointId))) return
        val message = GroupMessage.decode(payload) ?: return
        target.manager.onControlMessage(from.endpointId, message)
    }

    /** Simulate a device dropping off: every link to it fails on both sides. */
    suspend fun killNode(memberId: String) {
        val node = nodes[memberId] ?: return
        node.reachable = false
        val peers = nodes.values.filter { it.memberId != memberId }
        for (peer in peers) {
            val key = linkKey(node.endpointId, peer.endpointId)
            if (links.remove(key)) {
                peer.manager.onDisconnected(node.endpointId)
            }
        }
    }

    suspend fun reviveNode(memberId: String) {
        nodes[memberId]?.reachable = true
    }

    /** Advance time and let every node run its timers, repeatedly. */
    suspend fun settle(rounds: Int = 6, advanceMs: Long = 0) {
        repeat(rounds) {
            if (advanceMs > 0) clock.advance(advanceMs)
            discoverAll()
            nodes.values.filter { it.reachable }.forEach { it.manager.tick() }
        }
    }
}

class TestClock(var millis: Long = 1_000_000L) {
    fun advance(by: Long) {
        millis += by
    }
}
