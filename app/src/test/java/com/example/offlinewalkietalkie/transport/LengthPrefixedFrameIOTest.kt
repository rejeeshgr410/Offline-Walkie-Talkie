package com.example.offlinewalkietalkie.transport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class LengthPrefixedFrameIOTest {
    @Test
    fun multipleFramesRoundTrip() {
        val output = ByteArrayOutputStream()
        val first = byteArrayOf(10, 20, 30)
        val second = ByteArray(LengthPrefixedFrameIO.MAX_FRAME_BYTES) { it.toByte() }
        LengthPrefixedFrameIO.write(output, first)
        LengthPrefixedFrameIO.write(output, second)

        val input = ByteArrayInputStream(output.toByteArray())
        assertArrayEquals(first, LengthPrefixedFrameIO.read(input))
        assertArrayEquals(second, LengthPrefixedFrameIO.read(input))
        assertNull(LengthPrefixedFrameIO.read(input))
    }

    @Test
    fun fragmentedInputStreamStillReadsFullHeadersAndPayloads() {
        val output = ByteArrayOutputStream()
        val expected = ByteArray(100) { it.toByte() }
        LengthPrefixedFrameIO.write(output, expected)
        val fragmented = object : InputStream() {
            private val input = ByteArrayInputStream(output.toByteArray())
            override fun read(): Int = input.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                input.read(b, off, len.coerceAtMost(1))
        }
        assertArrayEquals(expected, LengthPrefixedFrameIO.read(fragmented))
        assertNull(LengthPrefixedFrameIO.read(fragmented))
    }

    @Test
    fun truncatedHeaderAndPayloadFailClosed() {
        assertThrows(IOException::class.java) {
            LengthPrefixedFrameIO.read(ByteArrayInputStream(byteArrayOf(0, 0)))
        }
        assertThrows(IOException::class.java) {
            LengthPrefixedFrameIO.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 5, 1, 2)))
        }
    }

    @Test
    fun invalidFrameLengthsAreRejectedBeforeAllocating() {
        assertThrows(IOException::class.java) {
            LengthPrefixedFrameIO.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 0)))
        }
        assertThrows(IOException::class.java) {
            LengthPrefixedFrameIO.read(ByteArrayInputStream(byteArrayOf(0, 1, 0, 0)))
        }
        assertThrows(IOException::class.java) {
            LengthPrefixedFrameIO.write(ByteArrayOutputStream(), byteArrayOf())
        }
        assertThrows(IOException::class.java) {
            LengthPrefixedFrameIO.write(ByteArrayOutputStream(), ByteArray(65_536))
        }
    }
}
