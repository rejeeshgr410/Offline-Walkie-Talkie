package com.example.offlinewalkietalkie.channel

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.SecureRandom

data class QrJoinData(
    val channelId: Long,
    val channelName: String,
    val hostFingerprint: String,
    val oneTimeSecret: String,
    val shortCode: String,
    val expiresEpochMs: Long
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("v", 3)
            put("cid", channelId)
            put("name", channelName)
            put("fp", hostFingerprint)
            put("sec", oneTimeSecret)
            put("code", shortCode)
            put("exp", expiresEpochMs)
        }.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): QrJoinData? {
            return try {
                val json = JSONObject(jsonStr)
                if (json.optInt("v", 0) != 3) return null
                QrJoinData(
                    channelId = json.getLong("cid"),
                    channelName = json.getString("name"),
                    hostFingerprint = json.getString("fp"),
                    oneTimeSecret = json.getString("sec"),
                    shortCode = json.getString("code"),
                    expiresEpochMs = json.getLong("exp")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Section 19.10 Auto-Rotating QR Join Manager.
 *
 * Rules:
 * - Each QR secret is one-time and expires after 10 minutes.
 * - Auto-rotates after every successful join.
 * - Generates QR Bitmaps via ZXing and easy-to-read 6-character short codes.
 */
class QrJoinManager(
    val channelId: Long,
    val channelName: String,
    val hostFingerprint: String
) {
    companion object {
        const val EXPIRY_DURATION_MS = 10 * 60 * 1000L // 10 minutes
        private val SHORT_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Unambiguous chars
    }

    private val secureRandom = SecureRandom()

    private val _currentJoinData = MutableStateFlow(createFreshJoinData())
    val currentJoinData: StateFlow<QrJoinData> = _currentJoinData.asStateFlow()

    private fun createFreshJoinData(): QrJoinData {
        val secretBytes = ByteArray(16)
        secureRandom.nextBytes(secretBytes)
        val oneTimeSecret = secretBytes.joinToString("") { "%02x".format(it) }

        val codeBuilder = StringBuilder(6)
        for (i in 0 until 6) {
            val idx = secureRandom.nextInt(SHORT_CODE_CHARS.length)
            codeBuilder.append(SHORT_CODE_CHARS[idx])
        }
        val shortCode = codeBuilder.toString()
        val expiresEpochMs = System.currentTimeMillis() + EXPIRY_DURATION_MS

        return QrJoinData(
            channelId = channelId,
            channelName = channelName,
            hostFingerprint = hostFingerprint,
            oneTimeSecret = oneTimeSecret,
            shortCode = shortCode,
            expiresEpochMs = expiresEpochMs
        )
    }

    /**
     * Auto-rotates QR code and 6-char short code.
     */
    fun rotate(): QrJoinData {
        val next = createFreshJoinData()
        _currentJoinData.value = next
        return next
    }

    /**
     * Check if current join code has expired; if so, rotates automatically.
     */
    fun checkExpiry(): Boolean {
        if (System.currentTimeMillis() >= _currentJoinData.value.expiresEpochMs) {
            rotate()
            return true
        }
        return false
    }

    fun remainingSeconds(): Int {
        val remainingMs = _currentJoinData.value.expiresEpochMs - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs / 1000).toInt() else 0
    }

    /**
     * Verify candidate one-time secret from scanned QR.
     * If valid, consumes it and immediately auto-rotates.
     */
    fun verifyAndConsumeSecret(candidateSecret: String): Boolean {
        checkExpiry()
        val current = _currentJoinData.value
        if (System.currentTimeMillis() < current.expiresEpochMs &&
            current.oneTimeSecret.equals(candidateSecret, ignoreCase = true)
        ) {
            rotate() // One-time use: auto-rotate immediately (§19.10)
            return true
        }
        return false
    }

    /**
     * Verify candidate 6-character short code.
     * If valid, consumes it and immediately auto-rotates.
     */
    fun verifyAndConsumeShortCode(candidateCode: String): Boolean {
        checkExpiry()
        val current = _currentJoinData.value
        val normalizedCandidate = candidateCode.trim().uppercase()
        if (System.currentTimeMillis() < current.expiresEpochMs &&
            current.shortCode.equals(normalizedCandidate, ignoreCase = true)
        ) {
            rotate() // One-time use: auto-rotate immediately (§19.10)
            return true
        }
        return false
    }

    /**
     * Generates a 512x512 QR code Bitmap using ZXing.
     */
    fun generateQrBitmap(size: Int = 512): Bitmap {
        val content = _currentJoinData.value.toJson()
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
