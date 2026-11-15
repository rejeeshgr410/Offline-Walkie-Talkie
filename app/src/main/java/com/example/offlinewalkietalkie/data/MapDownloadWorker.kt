package com.example.offlinewalkietalkie.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persistent user-requested area downloads, bounded to the repository's small-area quota. */
class MapDownloadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val repository = OfflineMapRepository(context)
    private var lastNotificationBytes = -100000L
    private fun notifyProgress(bytes: Long, finished: Boolean = false) {
        if (bytes >= 0 && !finished && bytes - lastNotificationBytes < 65536) return
        lastNotificationBytes = bytes
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(android.app.NotificationChannel("map_downloads", "Offline map downloads", android.app.NotificationManager.IMPORTANCE_LOW))
        val builder = androidx.core.app.NotificationCompat.Builder(applicationContext, "map_downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (finished) "Offline map saved" else "Downloading offline map")
            .setContentText(inputData.getString("name") ?: "Saved area")
            .setOnlyAlertOnce(true).setOngoing(!finished)
        if (!finished) builder.setProgress(0, 0, true).addAction(android.R.drawable.ic_media_pause,
            "Pause", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
        try { manager.notify(1002, builder.build()) } catch (_: SecurityException) { /* Notifications declined. */ }
    }
    override fun doWork(): Result {
        return try {
            repository.download(inputData.getString("name") ?: "Saved area",
                inputData.getDouble("lat", Double.NaN), inputData.getDouble("lon", Double.NaN),
                inputData.getDouble("radius", 1.0)) { bytes ->
                if (isStopped) { repository.cancelActiveDownload(); throw InterruptedException() }
                setProgressAsync(workDataOf("bytes" to bytes))
                notifyProgress(bytes)
            }
            notifyProgress(-1, finished = true)
            Result.success()
        } catch (e: Exception) {
            if (!isStopped && e is java.io.IOException && runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("error" to (e.message ?: "Map download failed")))
        }
    }

    override fun onStopped() {
        repository.cancelActiveDownload()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(1002)
    }

    companion object {
        const val UNIQUE_NAME = "offline-area-download"
        fun enqueue(context: Context, name: String, lat: Double, lon: Double, radius: Double) {
            val data = workDataOf("name" to name, "lat" to lat, "lon" to lon, "radius" to radius)
            context.getSharedPreferences("map_download", 0).edit()
                .putString("name", name).putString("lat", lat.toString())
                .putString("lon", lon.toString()).putString("radius", radius.toString()).apply()
            val request = OneTimeWorkRequestBuilder<MapDownloadWorker>().setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            context.getSharedPreferences("map_download", 0).edit().putString("work_id", request.id.toString()).apply()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
        fun pause(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
        fun resume(context: Context) {
            val p = context.getSharedPreferences("map_download", 0)
            val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return
            val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return
            enqueue(context, p.getString("name", "Saved area")!!, lat, lon,
                p.getString("radius", "1.0")!!.toDouble())
        }
    }
}

data class MapCatalogArea(val name: String, val latitude: Double, val longitude: Double)

object MapAreaCatalog {
    // Small neighbourhood extracts around city centres, not full-city tile downloads.
    val areas = listOf(MapCatalogArea("Dubai, UAE", 25.2048, 55.2708),
        MapCatalogArea("Sharjah, UAE", 25.3463, 55.4209),
        MapCatalogArea("Abu Dhabi, UAE", 24.4539, 54.3773),
        MapCatalogArea("Kochi, India", 9.9312, 76.2673),
        MapCatalogArea("London, UK", 51.5074, -0.1278))
    fun search(query: String) = areas.filter { it.name.contains(query.trim(), ignoreCase = true) }
}
