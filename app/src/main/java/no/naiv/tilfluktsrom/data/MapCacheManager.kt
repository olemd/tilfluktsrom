package no.naiv.tilfluktsrom.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Manages offline map tile caching for the surrounding area.
 *
 * On first launch, downloads map tiles for a region around the user's location.
 * On subsequent launches, checks if the current location is within the cached area.
 */
class MapCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "MapCacheManager"
        private const val PREFS_NAME = "map_cache_prefs"
        private const val KEY_CACHED_LAT = "cached_center_lat"
        private const val KEY_CACHED_LON = "cached_center_lon"
        private const val KEY_CACHE_RADIUS = "cache_radius_km"
        private const val KEY_CACHE_COMPLETE = "cache_complete"

        // Cache tiles for ~15km radius at useful zoom levels
        private const val CACHE_RADIUS_DEGREES = 0.15  // ~15km
        private const val MIN_ZOOM = 10
        private const val MAX_ZOOM = 16
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if we have a map cache that covers the given location.
     */
    fun hasCacheForLocation(latitude: Double, longitude: Double): Boolean {
        if (!prefs.getBoolean(KEY_CACHE_COMPLETE, false)) return false

        val cachedLat = Double.fromBits(prefs.getLong(KEY_CACHED_LAT, 0))
        val cachedLon = Double.fromBits(prefs.getLong(KEY_CACHED_LON, 0))
        val radius = prefs.getFloat(KEY_CACHE_RADIUS, 0f).toDouble()

        if (radius == 0.0) return false

        // Check if current location is within the cached region (with some margin)
        val margin = radius * 0.3
        return Math.abs(latitude - cachedLat) < (radius - margin) &&
               Math.abs(longitude - cachedLon) < (radius - margin)
    }

    /**
     * Download map tiles for the area around the given location.
     * Reports progress via callback (0.0 to 1.0).
     */
    suspend fun cacheMapArea(
        mapView: MapView,
        latitude: Double,
        longitude: Double,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "Starting map tile cache for area around $latitude, $longitude")

            val boundingBox = BoundingBox(
                latitude + CACHE_RADIUS_DEGREES,
                longitude + CACHE_RADIUS_DEGREES,
                latitude - CACHE_RADIUS_DEGREES,
                longitude - CACHE_RADIUS_DEGREES
            )

            val cacheManager = CacheManager(mapView)
            var complete = false
            var success = false

            cacheManager.downloadAreaAsync(
                context,
                boundingBox,
                MIN_ZOOM,
                MAX_ZOOM,
                object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        Log.i(TAG, "Map cache download complete")
                        success = true
                        complete = true
                    }

                    override fun onTaskFailed(errors: Int) {
                        Log.w(TAG, "Map cache download completed with $errors errors")
                        // Consider partial success if most tiles downloaded
                        success = errors < 50
                        complete = true
                    }

                    override fun updateProgress(
                        progress: Int,
                        currentZoomLevel: Int,
                        zoomMin: Int,
                        zoomMax: Int
                    ) {
                        val totalZoomLevels = zoomMax - zoomMin + 1
                        val zoomProgress = (currentZoomLevel - zoomMin).toFloat() / totalZoomLevels
                        onProgress(zoomProgress + (progress / 100f) / totalZoomLevels)
                    }

                    override fun downloadStarted() {
                        Log.i(TAG, "Map cache download started")
                    }

                    override fun setPossibleTilesInArea(total: Int) {
                        Log.i(TAG, "Total tiles to download: $total")
                    }
                }
            )

            // Wait for completion (the async callback runs on main thread)
            withContext(Dispatchers.IO) {
                while (!complete) {
                    Thread.sleep(500)
                }
            }

            if (success) {
                prefs.edit()
                    .putLong(KEY_CACHED_LAT, latitude.toBits())
                    .putLong(KEY_CACHED_LON, longitude.toBits())
                    .putFloat(KEY_CACHE_RADIUS, CACHE_RADIUS_DEGREES.toFloat())
                    .putBoolean(KEY_CACHE_COMPLETE, true)
                    .apply()
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache map tiles", e)
            false
        }
    }

    /**
     * Get the approximate number of cached tiles.
     */
    fun getCachedTileCount(): Long {
        return try {
            val writer = SqlTileWriter()
            val count = writer.getRowCount(null)
            writer.onDetach()
            count
        } catch (e: Exception) {
            0L
        }
    }
}
