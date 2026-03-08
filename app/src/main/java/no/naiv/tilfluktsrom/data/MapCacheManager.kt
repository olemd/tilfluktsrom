package no.naiv.tilfluktsrom.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * Manages offline map tile caching for the surrounding area.
 *
 * OSMDroid's SqlTileWriter automatically caches every tile the MapView loads.
 * We exploit this by programmatically panning the map across the surrounding area
 * at multiple zoom levels, which causes tiles to be fetched and cached passively.
 *
 * This approach respects OSM's tile usage policy (no bulk download) while still
 * building up an offline cache for the user's area.
 */
class MapCacheManager(private val context: Context) {

    companion object {
        private const val TAG = "MapCacheManager"
        private const val PREFS_NAME = "map_cache_prefs"
        private const val KEY_CACHED_LAT = "cached_center_lat"
        private const val KEY_CACHED_LON = "cached_center_lon"
        private const val KEY_CACHE_RADIUS = "cache_radius_km"
        private const val KEY_CACHE_COMPLETE = "cache_complete"

        private const val CACHE_RADIUS_DEGREES = 0.15  // ~15km

        // Zoom levels to cache: overview down to street level
        private val CACHE_ZOOM_LEVELS = intArrayOf(10, 12, 14, 16)

        // Grid points per axis per zoom level for panning
        private const val GRID_SIZE = 3
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

        val margin = radius * 0.3
        return Math.abs(latitude - cachedLat) < (radius - margin) &&
               Math.abs(longitude - cachedLon) < (radius - margin)
    }

    /**
     * Seed the tile cache by panning the map across the area around the location.
     * OSMDroid's built-in SqlTileWriter caches each tile as it's loaded.
     *
     * Reports progress via callback (0.0 to 1.0).
     */
    suspend fun cacheMapArea(
        mapView: MapView,
        latitude: Double,
        longitude: Double,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            Log.i(TAG, "Seeding tile cache for area around $latitude, $longitude")

            val totalSteps = CACHE_ZOOM_LEVELS.size * GRID_SIZE * GRID_SIZE
            var step = 0

            for (zoom in CACHE_ZOOM_LEVELS) {
                mapView.controller.setZoom(zoom.toDouble())

                // Pan across a grid of points covering the area
                for (row in 0 until GRID_SIZE) {
                    for (col in 0 until GRID_SIZE) {
                        val lat = latitude - CACHE_RADIUS_DEGREES +
                            (2 * CACHE_RADIUS_DEGREES * row) / (GRID_SIZE - 1)
                        val lon = longitude - CACHE_RADIUS_DEGREES +
                            (2 * CACHE_RADIUS_DEGREES * col) / (GRID_SIZE - 1)

                        mapView.controller.setCenter(GeoPoint(lat, lon))
                        // Force a layout pass so tiles are requested
                        mapView.invalidate()

                        step++
                        onProgress(step.toFloat() / totalSteps)

                        // Brief delay to allow tile loading to start
                        delay(300)
                    }
                }
            }

            // Restore to user's location
            mapView.controller.setZoom(14.0)
            mapView.controller.setCenter(GeoPoint(latitude, longitude))

            prefs.edit()
                .putLong(KEY_CACHED_LAT, latitude.toBits())
                .putLong(KEY_CACHED_LON, longitude.toBits())
                .putFloat(KEY_CACHE_RADIUS, CACHE_RADIUS_DEGREES.toFloat())
                .putBoolean(KEY_CACHE_COMPLETE, true)
                .apply()

            Log.i(TAG, "Tile cache seeding complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed tile cache", e)
            false
        }
    }
}
