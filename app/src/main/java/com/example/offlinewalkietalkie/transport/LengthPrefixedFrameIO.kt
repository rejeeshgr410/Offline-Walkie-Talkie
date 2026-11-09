package com.example.offlinewalkietalkie.transport

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A bounded, length-prefixed message stream shared by both native Bluetooth transports.
 * InputStream.read() is NOT guaranteed to fill a 4-byte header or a packet in one call.
 *
 * Wire format: unsigned 4-byte big-endian length followed by that many bytes.
 * The size limit keeps a malformed or untrusted peer from allocating huge arrays.
 */
internal object LengthPrefixedFrameIO {
    const val MAX_FRAME_BYTES = 65_535

    /** Returns null only for a clean EOF between messages; truncated messages throw. */
    @Throws(IOException::class)
    fun read(input: InputStream): ByteArray? {
        val first = input.read()
        if (first == -1) return null

        val tail = ByteArray(3)
        DataInputStream(input).readFully(tail)
        val length = ((first and 0xff) shl 24) or
            ((tail[0].toInt() and 0xff) shl 16) or
            ((tail[1].toInt() and 0xff) shl 8) or
            (tail[2].toInt() and 0xff)

        if (length !in 1..MAX_FRAME_BYTES) {
            throw IOException("Invalid Bluetooth frame size: $length")
        }

        return ByteArray(length).also { DataInputStream(input).readFully(it) }
    }

    @Throws(IOException::class)
    fun write(output: OutputStream, frame: ByteArray) {
        val size = frame.size
        if (size !in 1..MAX_FRAME_BYTES) {
            throw IOException("Invalid Bluetooth frame size: $size")
        }
        output.write(
            byteArrayOf(
                (size ushr 24).toByte(),
                (size ushr 16).toByte(),
                (size ushr 8).toByte(),
                size.toByte()
            )
        )
        output.write(frame)
        output.flush()
    }
}
