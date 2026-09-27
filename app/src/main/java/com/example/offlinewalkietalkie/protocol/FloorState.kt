package com.example.offlinewalkietalkie.protocol

/**
 * PTT Floor State Machine per Section 6 & Section 19:
 * IDLE -> REQUESTING -> GRANTED -> TALKING -> RELEASING -> IDLE
 *                 \-> DENIED -> IDLE
 */
sealed class FloorState {
    object Idle : FloorState()
    object Requesting : FloorState()
    object Granted : FloorState()
    object Talking : FloorState()
    object HeldByMe : FloorState() // Alias/compatible with Talking for existing UI
    data class Denied(val reason: String = "") : FloorState()
    object Releasing : FloorState()
    data class HeldByOther(val peerId: String, val peerName: String) : FloorState()
}
