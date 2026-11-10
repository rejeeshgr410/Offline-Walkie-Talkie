package com.example.offlinewalkietalkie.data.mbtiles

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Manages installed and bundled MBTiles offline map packages.
 */
class MbTilesRepository(private val context: Context) {

    companion object {
        private const val TAG = "TL/MbTilesRepo"
        const val MAX_MBTILES_QUOTA_BYTES = 500L * 1024 * 1024 // 500 MB quota for MBTiles
    }

    private val directory = File(context.filesDir, "mbtiles").apply { mkdirs() }

    init {
        unpackBundledMbTilesIfNeeded()
    }

    fun getStorageUsedBytes(): Long {
        return directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getStorageQuotaBytes(): Long = MAX_MBTILES_QUOTA_BYTES

    fun unpackBundledMbTilesIfNeeded() {
        runCatching {
            val assetManager = context.assets
            val assetFiles = assetManager.list("mbtiles") ?: emptyArray()
            for (assetName in assetFiles) {
                if (!assetName.endsWith(".mbtiles")) continue
                val targetFile = File(directory, assetName)
                if (!targetFile.exists()) {
                    assetManager.open("mbtiles/$assetName").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Unpacked bundled MBTiles asset: $assetName (${targetFile.length()} bytes)")
                }
            }
        }.onFailure {
            Log.w(TAG, "Failed unpacking bundled MBTiles: ${it.message}")
        }
    }

    fun restoreBundledPacks(): List<MbTilesPackage> {
        runCatching {
            val assetManager = context.assets
            val assetFiles = assetManager.list("mbtiles") ?: emptyArray()
            for (assetName in assetFiles) {
                if (!assetName.endsWith(".mbtiles")) continue
                val targetFile = File(directory, assetName)
                assetManager.open("mbtiles/$assetName").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return listPackages()
    }

    fun listPackages(): List<MbTilesPackage> {
        val files = directory.listFiles { f -> f.extension == "mbtiles" } ?: emptyArray()
        return files.mapNotNull { file ->
            try {
                MbTilesDatabase(file).use { db ->
                    val metadata = db.readMetadata()
                    MbTilesPackage(
                        id = file.nameWithoutExtension,
                        file = file,
                        metadata = metadata
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupted or invalid MBTiles file ${file.name}: ${e.message}")
                null
            }
        }.sortedBy { it.metadata.name.lowercase() }
    }

    fun importFromUri(uri: Uri, displayName: String? = null): MbTilesPackage {
        val cleanName = (displayName ?: "offline_map")
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "offline_map" }
        val targetFile = File(directory, "${cleanName}_${System.currentTimeMillis()}.mbtiles")

        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for selected MBTiles file.")

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (getStorageUsedBytes() > MAX_MBTILES_QUOTA_BYTES) {
            targetFile.delete()
            throw IllegalStateException("Storage quota exceeded (500 MB limit). Delete older packs.")
        }

        return try {
            MbTilesDatabase(targetFile).use { db ->
                val meta = db.readMetadata()
                MbTilesPackage(
                    id = targetFile.nameWithoutExtension,
                    file = targetFile,
                    metadata = meta
                )
            }
        } catch (e: Exception) {
            targetFile.delete()
            throw IllegalArgumentException("Selected file is not a valid SQLite MBTiles database: ${e.message}")
        }
    }

    fun deletePackage(pack: MbTilesPackage): Boolean {
        return pack.file.delete()
    }

    fun deleteAllPackages(): Boolean {
        var allOk = true
        directory.listFiles()?.forEach { f ->
            if (!f.delete()) allOk = false
        }
        return allOk
    }

    fun getSampleTile(pack: MbTilesPackage): MbTile? {
        return try {
            MbTilesDatabase(pack.file).use { db ->
                db.getSampleTile()
            }
        } catch (e: Exception) {
            null
        }
    }
}
