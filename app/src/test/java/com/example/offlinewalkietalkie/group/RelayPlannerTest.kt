package com.example.offlinewalkietalkie.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPlannerTest {

    private fun graph(vararg edges: Pair<String, String>): Map<String, Set<String>> {
        val map = mutableMapOf<String, MutableSet<String>>()
        edges.forEach { (a, b) ->
            map.getOrPut(a) { mutableSetOf() }.add(b)
            map.getOrPut(b) { mutableSetOf() }.add(a)
        }
        return map
    }

    @Test
    fun `full mesh needs no relaying at all`() {
        val g = graph("A" to "B", "A" to "C", "A" to "D", "B" to "C", "B" to "D", "C" to "D")
        listOf("B", "C", "D").forEach { self ->
            assertTrue(
                "$self must not relay in a full mesh",
                RelayPlanner.relayTargets(self, "A", g).isEmpty()
            )
        }
    }

    @Test
    fun `a line relays through the middle`() {
        val g = graph("A" to "B", "B" to "C")
        assertEquals(setOf("C"), RelayPlanner.relayTargets("B", "A", g))
        assertEquals(setOf("A"), RelayPlanner.relayTargets("B", "C", g))
    }

    @Test
    fun `only one of two possible bridges relays`() {
        // A reaches B and C; D is only reachable through B or C.
        val g = graph("A" to "B", "A" to "C", "B" to "D", "C" to "D")
        val fromB = RelayPlanner.relayTargets("B", "A", g)
        val fromC = RelayPlanner.relayTargets("C", "A", g)
        assertEquals("B is the lower id bridge", setOf("D"), fromB)
        assertTrue("C must stay quiet", fromC.isEmpty())
    }

    @Test
    fun `never relays back to where the frame came from`() {
        val g = graph("A" to "B", "B" to "C")
        assertTrue("A" !in RelayPlanner.relayTargets("B", "A", g))
    }

    @Test
    fun `unknown neighbourhood falls back to flooding`() {
        // No heartbeat from A yet, so B cannot tell what A reaches.
        val g = mapOf("B" to setOf("A", "C", "D"))
        assertEquals(setOf("C", "D"), RelayPlanner.relayTargets("B", "A", g))
    }

    @Test
    fun `second hop continues along a chain`() {
        val g = graph("A" to "B", "B" to "C", "C" to "D")
        // C got the frame from B; D is beyond B's reach.
        assertEquals(setOf("D"), RelayPlanner.relayTargets("C", "B", g))
    }
}
