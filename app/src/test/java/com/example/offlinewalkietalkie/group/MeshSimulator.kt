package com.example.offlinewalkietalkie.group

/**
 * An in-memory radio environment for driving many [GroupSessionManager]s against each
 * other. Physical multi-device testing needs one phone per node; this exercises the same
 * logic for 2, 3, 4, 5+ nodes deterministically.
 *
 * Models the real Nearby handshake: connect() raises a request with the same code on
 * BOTH ends, and the link only comes up once both ends have accepted. Radio range is
 * modelled too, so relay paths can be tested.
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
        val rejectCalls = mutableListOf<String>()
        val sentControl = mutableListOf<Pair<String, ByteArray>>()

        val endpointId: String get() = endpointFor(memberId)

        val port = object : GroupTransportPort {
            override suspend fun connect(endpointId: String) {
                connectCalls.add(endpointId)
                openPending(this@Node, endpointId)
            }

            override suspend fun acceptConnection(endpointId: String) {
                acceptCalls.add(endpointId)
                accept(this@Node, endpointId)
            }

            override suspend fun rejectConnection(endpointId: String) {
                rejectCalls.add(endpointId)
                if (pending.remove(linkKey(this@Node.endpointId, endpointId)) != null) {
                    nodeByEndpoint(endpointId)?.manager
                        ?.onConnectionFailed(this@Node.endpointId, "The other phone declined")
                }
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

    private class PendingLink(val code: String, val accepted: MutableSet<String> = mutableSetOf())

    private val nodes = linkedMapOf<String, Node>()
    private val links = mutableSetOf<String>()
    private val pending = mutableMapOf<String, PendingLink>()
    private var codeCounter = 1000

    /** Pairs that cannot hear each other. Everyone is in range unless listed here. */
    private val outOfRange = mutableSetOf<String>()

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

    fun setOutOfRange(a: String, b: String) {
        outOfRange.add(pairKey(a, b))
    }

    private fun pairKey(a: String, b: String) = listOf(a, b).sorted().joinToString("~")

    private fun inRange(a: Node, b: Node) =
        a.reachable && b.reachable && pairKey(a.memberId, b.memberId) !in outOfRange

    private fun nodeByEndpoint(endpointId: String): Node? =
        nodes.values.find { it.endpointId == endpointId }

    private fun linkKey(a: String, b: String) = listOf(a, b).sorted().joinToString("~")

    fun isLinked(a: String, b: String) =
        links.contains(linkKey(endpointFor(a), endpointFor(b)))

    fun linkCount() = links.size

    /** The code Nearby showed both phones for the pending or live link between two nodes. */
    fun codeFor(a: String, b: String): String? = pending[linkKey(endpointFor(a), endpointFor(b))]?.code

    /** Every node sees the adverts of the nodes in range of it. */
    suspend fun discoverAll() {
        for (observer in nodes.values) {
            for (target in nodes.values) {
                if (observer.memberId == target.memberId) continue
                if (!inRange(observer, target)) continue
                val advert = target.advert ?: continue
                observer.manager.onEndpointDiscovered(target.endpointId, advert)
            }
        }
    }

    /** Both ends learn about the request, with the same code, exactly like Nearby. */
    private suspend fun openPending(from: Node, toEndpointId: String) {
        val target = nodeByEndpoint(toEndpointId) ?: return
        if (!inRange(from, target)) return
        val key = linkKey(from.endpointId, target.endpointId)
        if (key in links || key in pending) return
        val code = (codeCounter++).toString()
        pending[key] = PendingLink(code)
        target.manager.onConnectionRequest(from.endpointId, from.advert, code)
        from.manager.onConnectionRequest(target.endpointId, target.advert, code)
    }

    private suspend fun accept(accepter: Node, otherEndpointId: String) {
        val other = nodeByEndpoint(otherEndpointId) ?: return
        val key = linkKey(accepter.endpointId, other.endpointId)
        val link = pending[key] ?: return
        link.accepted.add(accepter.memberId)
        if (!link.accepted.containsAll(listOf(accepter.memberId, other.memberId))) return
        if (!inRange(accepter, other)) return
        pending.remove(key)
        links.add(key)
        other.manager.onConnected(accepter.endpointId)
        accepter.manager.onConnected(other.endpointId)
    }

    private suspend fun breakLink(from: Node, toEndpointId: String) {
        val target = nodeByEndpoint(toEndpointId) ?: return
        val key = linkKey(from.endpointId, target.endpointId)
        pending.remove(key)
        if (!links.remove(key)) return
        target.manager.onDisconnected(from.endpointId)
        from.manager.onDisconnected(target.endpointId)
    }

    private suspend fun deliverControl(from: Node, toEndpointId: String, payload: ByteArray) {
        val target = nodeByEndpoint(toEndpointId) ?: return
        if (!inRange(from, target)) return
        if (!links.contains(linkKey(from.endpointId, target.endpointId))) return
        val message = GroupMessage.decode(payload) ?: return
        target.manager.onControlMessage(from.endpointId, message)
    }

    /**
     * The people holding the phones compare the code on each pending first-contact link
     * and tap Accept. Only codes that genuinely match on both ends get accepted.
     */
    suspend fun approveAll() {
        for (node in nodes.values) {
            val waiting = node.manager.state.value.awaitingApproval
            for (member in waiting) {
                val otherNode = nodes[member.memberId] ?: continue
                val shownHere = member.authCode
                val shownThere = otherNode.manager.state.value.member(node.memberId)?.authCode
                if (shownHere != null && shownHere == shownThere) {
                    node.manager.respondToRequest(member.memberId, accept = true)
                }
            }
        }
    }

    /** Simulate a device dropping off: every link to it fails on both sides. */
    suspend fun killNode(memberId: String) {
        val node = nodes[memberId] ?: return
        node.reachable = false
        for (peer in nodes.values.filter { it.memberId != memberId }) {
            val key = linkKey(node.endpointId, peer.endpointId)
            pending.remove(key)
            if (links.remove(key)) {
                peer.manager.onDisconnected(node.endpointId)
            }
        }
    }

    suspend fun reviveNode(memberId: String) {
        nodes[memberId]?.reachable = true
    }

    /** Advance time, let people confirm codes, and let every node run its timers. */
    suspend fun settle(rounds: Int = 6, advanceMs: Long = 0, autoApprove: Boolean = true) {
        repeat(rounds) {
            if (advanceMs > 0) clock.advance(advanceMs)
            discoverAll()
            if (autoApprove) approveAll()
            nodes.values.filter { it.reachable }.forEach { it.manager.tick() }
        }
    }
}

class TestClock(var millis: Long = 1_000_000L) {
    fun advance(by: Long) {
        millis += by
    }
}
