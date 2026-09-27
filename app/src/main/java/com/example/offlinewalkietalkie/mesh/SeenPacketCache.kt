package com.example.offlinewalkietalkie.mesh

import java.util.Collections
import java.util.LinkedHashMap

/**
 * Section 19.1 & Section 19.6 Mesh Deduplication Cache.
 *
 * Tracks observed (memberId, sequence) tuples to prevent loops and duplicate packet forwarding.
 * Bounded to 1,000 entries with LRU eviction.
 */
class SeenPacketCache(val maxCapacity: Int = 1000) {

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<Long, Long>(maxCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?): Boolean {
                return size > maxCapacity
            }
        }
    )

    /**
     * Generate unique 64-bit key from memberId and sequence.
     */
    private fun generateKey(memberId: Long, sequence: Int): Long {
        return memberId xor ((sequence.toLong() and 0xFFFFFFFFL) shl 32)
    }

    /**
     * Checks if packet has been seen before.
     * If not seen, marks it as seen and returns false.
     * If already seen, returns true.
     */
    fun isDuplicate(memberId: Long, sequence: Int): Boolean {
        val key = generateKey(memberId, sequence)
        synchronized(cache) {
            if (cache.containsKey(key)) {
                return true
            }
            cache[key] = System.currentTimeMillis()
            return false
        }
    }

    fun markSeen(memberId: Long, sequence: Int) {
        val key = generateKey(memberId, sequence)
        cache[key] = System.currentTimeMillis()
    }

    fun size(): Int = cache.size

    fun clear() {
        cache.clear()
    }
}
