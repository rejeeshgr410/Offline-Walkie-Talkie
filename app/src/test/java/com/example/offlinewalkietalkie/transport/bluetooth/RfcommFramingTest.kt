package com.example.offlinewalkietalkie.transport.bluetooth

import org.junit.Test
import org.junit.Assert.*
import java.io.*

class RfcommFramingTest {
    @Test fun fragmentedReadsAndBackToBackPackets() {
        val output = ByteArrayOutputStream()
        val payload = ByteArray(4096) { it.toByte() }
        RfcommFraming.write(output, payload)
        RfcommFraming.write(output, byteArrayOf(42))
        val input = object : ByteArrayInputStream(output.toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int) = super.read(b, off, minOf(1, len))
        }
        assertArrayEquals(payload, RfcommFraming.read(input))
        assertArrayEquals(byteArrayOf(42), RfcommFraming.read(input))
    }
    @Test(expected = IllegalArgumentException::class) fun hostileLengthRejected() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).writeInt(Int.MAX_VALUE)
        RfcommFraming.read(ByteArrayInputStream(output.toByteArray()))
    }
    @Test(expected = EOFException::class) fun truncatedFrameRejected() {
        RfcommFraming.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 2, 1)))
    }
}
