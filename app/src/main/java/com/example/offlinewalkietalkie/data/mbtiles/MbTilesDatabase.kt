package com.example.offlinewalkietalkie.data.mbtiles

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.Closeable
import java.io.File

/**
 * SQLite reader for offline MBTiles tile packages conforming to the MBTiles 1.3 specification.
 *
 * Provides:
 * - Read-only SQLite access with zero write locking.
 * - Automatic TMS-to-XYZ tile coordinate conversion:
 *   tms_row = (1 shl zoom) - 1 - xyz_y
 * - Direct tile BLOB queries and raster Bitmap decoding.
 */
class MbTilesDatabase(val file: File) : Closeable {

    companion object {
        private const val TAG = "TL/MbTiles"

        /**
         * Converts Slippy/XYZ tile coordinates (top-left origin) to MBTiles TMS coordinates (bottom-left origin).
         */
        fun xyzToTms(zoom: Int, y: Int): Int {
            return (1 shl zoom) - 1 - y
        }

        /**
         * Converts MBTiles TMS coordinates to Slippy/XYZ tile coordinates.
         */
        fun tmsToXyz(zoom: Int, tmsRow: Int): Int {
            return (1 shl zoom) - 1 - tmsRow
        }
    }

    private var db: SQLiteDatabase? = null

    init {
        open()
    }

    @Synchronized
    private fun open() {
        if (db == null || !db!!.isOpen) {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        }
    }

    /**
     * Reads the MBTiles metadata table.
     */
    @Synchronized
    fun readMetadata(): MbTilesMetadata {
        open()
        val metaMap = mutableMapOf<String, String>()
        db?.rawQuery("SELECT name, value FROM metadata", null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            val valIdx = cursor.getColumnIndex("value")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && valIdx >= 0) {
                    val k = cursor.getString(nameIdx)
                    val v = cursor.getString(valIdx)
                    metaMap[k] = v
                }
            }
        }

        var tileCount = 0
        db?.rawQuery("SELECT COUNT(*) FROM tiles", null)?.use { cursor ->
            if (cursor.moveToNext()) {
                tileCount = cursor.getInt(0)
            }
        }

        return MbTilesMetadata(
            name = metaMap["name"] ?: file.nameWithoutExtension,
            format = metaMap["format"]?.lowercase() ?: "png",
            bounds = metaMap["bounds"],
            minZoom = metaMap["minzoom"]?.toIntOrNull() ?: 0,
            maxZoom = metaMap["maxzoom"]?.toIntOrNull() ?: 14,
            description = metaMap["description"],
            attribution = metaMap["attribution"],
            tileCount = tileCount,
            fileSizeBytes = file.length()
        )
    }

    /**
     * Retrieves the raw tile bytes by XYZ coordinates.
     */
    @Synchronized
    fun getTileBytes(zoom: Int, colX: Int, rowY: Int): ByteArray? {
        open()
        val tmsRow = xyzToTms(zoom, rowY)
        return db?.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(zoom.toString(), colX.toString(), tmsRow.toString())
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                cursor.getBlob(0)
            } else {
                null
            }
        }
    }

    /**
     * Retrieves the decoded Bitmap for a raster tile (PNG, JPG, WebP).
     */
    @Synchronized
    fun getTileBitmap(zoom: Int, colX: Int, rowY: Int): Bitmap? {
        val bytes = getTileBytes(zoom, colX, rowY) ?: return null
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode tile at z=$zoom, x=$colX, y=$rowY: ${e.message}")
            null
        }
    }

    /**
     * Gets an arbitrary sample tile from the package to preview in the UI.
     */
    @Synchronized
    fun getSampleTile(): MbTile? {
        open()
        return db?.rawQuery(
            "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles LIMIT 1",
            null
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                val z = cursor.getInt(0)
                val x = cursor.getInt(1)
                val tmsRow = cursor.getInt(2)
                val data = cursor.getBlob(3)
                val y = tmsToXyz(z, tmsRow)
                val bmp = try {
                    BitmapFactory.decodeByteArray(data, 0, data.size)
                } catch (e: Exception) { null }
                MbTile(zoom = z, column = x, row = y, data = data, bitmap = bmp)
            } else null
        }
    }

    @Synchronized
    override fun close() {
        try {
            if (db?.isOpen == true) {
                db?.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing MBTiles database: ${e.message}")
        } finally {
            db = null
        }
    }
}
