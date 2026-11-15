package com.example.offlinewalkietalkie.voice

import java.nio.ByteBuffer
import java.security.SecureRandom

/** Acceptance is counted only after the caller authenticates the encrypted receipt. */
class CodecProposal(private val peers: Set<String>, enabled: Boolean, id: Long = SecureRandom().nextLong()) {
    val payload: ByteArray = ByteBuffer.allocate(9).putLong(id).put(if (enabled) 1.toByte() else 0.toByte()).array()
    private val accepted = mutableSetOf<String>()
    fun acknowledge(peer: String, receipt: ByteArray) {
        if (peer in peers && receipt.contentEquals(payload)) accepted.add(peer)
    }
    fun pending(): Set<String> = peers - accepted
    fun canCommit(livePeers: Set<String>): Boolean = accepted.containsAll(peers) && livePeers == peers
}
