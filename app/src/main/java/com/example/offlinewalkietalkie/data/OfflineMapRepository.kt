package com.example.offlinewalkietalkie.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OfflineMapArea(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double,
    val roadCount: Int,
    val file: File
)

data class OfflineMapPoint(val latitude: Double, val longitude: Double)

/**
 * Manages offline vector map packs, downloads bounded road networks from OSM Overpass API,
 * manages local storage quotas (up to 250MB), and verifies pack geometry.
 */
class OfflineMapRepository(private val context: Context) {
    companion object {
        const val MAX_STORAGE_QUOTA_BYTES = 250L * 1024 * 1024 // 250 MB Storage Quota
    }

    private val directory = File(context.filesDir, "offline-map-areas").apply { mkdirs() }
    @Volatile private var activeConnection: HttpURLConnection? = null

    init {
        unpackBundledPacksIfNeeded()
    }

    fun getStorageUsedBytes(): Long {
        return directory.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getStorageQuotaBytes(): Long = MAX_STORAGE_QUOTA_BYTES

    fun deleteAllDownloadedAreas(): Boolean {
        var allDeleted = true
        directory.listFiles()?.forEach { file ->
            if (!file.delete()) allDeleted = false
        }
        return allDeleted
    }

    private fun unpackBundledPacksIfNeeded() {
        runCatching {
            val assetManager = context.assets
            val assetFiles = assetManager.list("offline-maps") ?: emptyArray()
            for (assetName in assetFiles) {
                val targetFile = File(directory, assetName)
                if (!targetFile.exists()) {
                    assetManager.open("offline-maps/$assetName").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun restoreBundledPacks(): List<OfflineMapArea> {
        runCatching {
            val assetManager = context.assets
            val assetFiles = assetManager.list("offline-maps") ?: emptyArray()
            for (assetName in assetFiles) {
                val targetFile = File(directory, assetName)
                assetManager.open("offline-maps/$assetName").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return list()
    }

    fun list(): List<OfflineMapArea> = directory.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { file -> runCatching { readArea(file) }.getOrNull() }
        ?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun download(name: String, latitude: Double, longitude: Double, radiusKm: Double, onProgress: (Long) -> Unit): OfflineMapArea {
        require(latitude in -85.0..85.0 && longitude in -180.0..180.0)
        require(radiusKm in 0.2..2.0) { "Choose an area radius from 0.2 to 2 km." }

        if (getStorageUsedBytes() >= MAX_STORAGE_QUOTA_BYTES) {
            throw IllegalStateException("Storage quota exceeded (250 MB). Delete older areas to free space.")
        }

        val radiusMeters = (radiusKm * 1000).toInt()
        val query = "[out:json][timeout:60];(way[\"highway\"](around:$radiusMeters,$latitude,$longitude););out geom;"
        val body = "data=${URLEncoder.encode(query, "UTF-8")}".toByteArray(Charsets.UTF_8)
        val connection = (URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("User-Agent", "TalkLoopOfflineMaps/1.0 (OpenStreetMap data download)")
        }
        activeConnection = connection
        try {
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) throw IllegalStateException("Map data server returned HTTP ${connection.responseCode}.")
            val output = StringBuilder()
            connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    output.append(buffer, 0, count)
                    onProgress(output.length.toLong())
                    require(output.length <= 15_000_000) { "This map area is too large. Choose a smaller radius." }
                    if (getStorageUsedBytes() + output.length > MAX_STORAGE_QUOTA_BYTES) {
                        throw IllegalStateException("Storage quota limit reached (250 MB). Download stopped.")
                    }
                }
            }
            val osm = JSONObject(output.toString())
            val ways = osm.optJSONArray("elements") ?: throw IllegalStateException("Map data response was invalid.")
            val safeName = name.trim().replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').take(40).ifBlank { "Saved-area" }
            val id = "${safeName}-${latitude.toString().replace('.', '_')}-${longitude.toString().replace('.', '_')}"
            val file = File(directory, "$id.json")
            val wrapper = JSONObject().put("name", name.trim().ifBlank { "Saved area" })
                .put("latitude", latitude).put("longitude", longitude).put("radiusKm", radiusKm)
                .put("roadCount", ways.length()).put("osm", osm)
            val temp = File(directory, "$id.tmp")
            temp.writeText(wrapper.toString(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            check(temp.renameTo(file)) { "Could not save the downloaded map area." }
            onProgress(-1L)
            return OfflineMapArea(id, wrapper.getString("name"), latitude, longitude, radiusKm, ways.length(), file)
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    fun cancelActiveDownload() { activeConnection?.disconnect() }

    fun delete(area: OfflineMapArea) { area.file.delete() }

    fun readRoads(area: OfflineMapArea): List<List<OfflineMapPoint>> {
        val elements = JSONObject(area.file.readText(Charsets.UTF_8)).optJSONObject("osm")?.optJSONArray("elements") ?: return emptyList()
        return buildList {
            for (i in 0 until elements.length()) {
                val geometry = elements.optJSONObject(i)?.optJSONArray("geometry") ?: continue
                val points = buildList {
                    for (j in 0 until geometry.length()) {
                        val point = geometry.optJSONObject(j) ?: continue
                        add(OfflineMapPoint(point.optDouble("lat"), point.optDouble("lon")))
                    }
                }
                if (points.size > 1) add(points)
            }
        }
    }

    private fun readArea(file: File): OfflineMapArea {
        val json = JSONObject(file.readText(Charsets.UTF_8))
        return OfflineMapArea(file.nameWithoutExtension, json.getString("name"), json.getDouble("latitude"), json.getDouble("longitude"), json.getDouble("radiusKm"), json.optInt("roadCount"), file)
    }
}
