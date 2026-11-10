package com.example.offlinewalkietalkie.data.mbtiles

import android.graphics.Bitmap
import java.io.File

/**
 * Metadata stored in an MBTiles SQLite database conforming to the MBTiles 1.3 spec.
 */
data class MbTilesMetadata(
    val name: String,
    val format: String, // "png", "jpg", "webp", "pbf"
    val bounds: String?, // "minLon,minLat,maxLon,maxLat"
    val minZoom: Int,
    val maxZoom: Int,
    val description: String?,
    val attribution: String?,
    val tileCount: Int,
    val fileSizeBytes: Long
)

/**
 * An imported or bundled MBTiles package ready for offline map rendering.
 */
data class MbTilesPackage(
    val id: String,
    val file: File,
    val metadata: MbTilesMetadata
)

/**
 * Coordinates and raw image/vector payload for a single tile.
 */
data class MbTile(
    val zoom: Int,
    val column: Int,
    val row: Int,
    val data: ByteArray,
    val bitmap: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MbTile

        if (zoom != other.zoom) return false
        if (column != other.column) return false
        if (row != other.row) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = zoom
        result = 31 * result + column
        result = 31 * result + row
        result = 31 * result + data.contentHashCode()
        return result
    }
}
