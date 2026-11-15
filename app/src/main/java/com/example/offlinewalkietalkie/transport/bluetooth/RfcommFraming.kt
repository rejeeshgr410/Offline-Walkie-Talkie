package com.example.offlinewalkietalkie.transport.bluetooth

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object RfcommFraming {
    const val MAX_PACKET_BYTES = 65535
    fun read(input: InputStream): ByteArray {
        val stream = DataInputStream(input)
        val length = stream.readInt()
        require(length in 1..MAX_PACKET_BYTES) { "Invalid RFCOMM packet length" }
        return ByteArray(length).also { stream.readFully(it) }
    }
    fun write(output: OutputStream, bytes: ByteArray) {
        require(bytes.size in 1..MAX_PACKET_BYTES)
        DataOutputStream(output).apply { writeInt(bytes.size); write(bytes); flush() }
    }
}
