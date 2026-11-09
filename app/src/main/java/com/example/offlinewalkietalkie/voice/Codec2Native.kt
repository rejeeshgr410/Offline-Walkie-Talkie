package com.example.offlinewalkietalkie.voice

import android.util.Log

class Codec2Native {
    companion object {
        private const val TAG = "TL/Codec2Native"

        const val CODEC2_MODE_3200 = 0
        const val CODEC2_MODE_2400 = 1
        const val CODEC2_MODE_1600 = 2
        const val CODEC2_MODE_1400 = 3
        const val CODEC2_MODE_1300 = 4
        const val CODEC2_MODE_1200 = 5
        const val CODEC2_MODE_700 = 6

        var isLoaded = false
            private set

        init {
            try {
                System.loadLibrary("codec2_jni")
                isLoaded = true
                Log.i(TAG, "libcodec2_jni.so loaded successfully")
            } catch (t: Throwable) {
                isLoaded = false
                Log.w(TAG, "Failed to load libcodec2_jni.so: ${t.message}")
            }
        }

        @JvmStatic external fun create(mode: Int): Long
        @JvmStatic external fun destroy(handle: Long): Int
        @JvmStatic external fun getSamplesPerFrame(handle: Long): Int
        @JvmStatic external fun getBitsSize(handle: Long): Int
        @JvmStatic external fun encode(handle: Long, inPcm: ShortArray, outBits: ByteArray): Long
        @JvmStatic external fun decode(handle: Long, outPcm: ShortArray, inBits: ByteArray): Long
    }
}
