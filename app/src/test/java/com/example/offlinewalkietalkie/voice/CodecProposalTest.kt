package com.example.offlinewalkietalkie.voice

import org.junit.Test
import org.junit.Assert.*

class CodecProposalTest {
    @Test fun everyPeerMustAcceptTheExactProposal() {
        val proposal = CodecProposal(setOf("B", "C"), true, 42)
        proposal.acknowledge("stranger", proposal.payload)
        proposal.acknowledge("B", CodecProposal(setOf("B"), false, 42).payload)
        proposal.acknowledge("C", CodecProposal(setOf("C"), true, 41).payload)
        assertEquals(setOf("B", "C"), proposal.pending())
        proposal.acknowledge("B", proposal.payload)
        assertFalse(proposal.canCommit(setOf("B", "C")))
        proposal.acknowledge("C", proposal.payload)
        assertTrue(proposal.canCommit(setOf("B", "C")))
        assertFalse(proposal.canCommit(setOf("B")))
        assertFalse(proposal.canCommit(setOf("B", "C", "D")))
    }
}
