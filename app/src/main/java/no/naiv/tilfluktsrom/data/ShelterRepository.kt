package no.naiv.tilfluktsrom.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository managing shelter data: local Room cache + remote Geonorge download.
 * Offline-first: always returns cached data when available, updates in background.
 */
class ShelterRepository(context: Context) {

    companion object {
        private const val TAG = "ShelterRepository"
        private const val PREFS_NAME = "shelter_prefs"
        private const val KEY_LAST_UPDATE = "last_update_ms"
        private const val UPDATE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

        // Geonorge GeoJSON download (ZIP containing all Norwegian shelters)
        private const val SHELTER_DATA_URL =
            "https://nedlasting.geonorge.no/geonorge/Samfunnssikkerhet/" +
            "TilfluktsromOffentlige/GeoJSON/" +
            "Samfunnssikkerhet_0000_Norge_25833_TilfluktsromOffentlige_GeoJSON.zip"
    }

    private val db = ShelterDatabase.getInstance(context)
    private val dao = db.shelterDao()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Reactive stream of all shelters from local cache. */
    fun getAllShelters(): Flow<List<Shelter>> = dao.getAllShelters()

    /** Check if we have cached shelter data. */
    suspend fun hasCachedData(): Boolean = dao.count() > 0

    /** Check if the cached data is stale and should be refreshed. */
    fun isDataStale(): Boolean {
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
        return System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS
    }

    /**
     * Download shelter data from Geonorge and cache it locally.
     * Returns true on success, false on failure.
     */
    suspend fun refreshData(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Downloading shelter data from Geonorge...")

            val request = Request.Builder()
                .url(SHELTER_DATA_URL)
                .header("User-Agent", "Tilfluktsrom-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body ?: run {
                Log.e(TAG, "Empty response body")
                return@withContext false
            }

            val shelters = body.byteStream().use { stream ->
                ShelterGeoJsonParser.parseFromZip(stream)
            }

            Log.i(TAG, "Parsed ${shelters.size} shelters, saving to database...")

            dao.deleteAll()
            dao.insertAll(shelters)

            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            Log.i(TAG, "Shelter data updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh shelter data", e)
            false
        }
    }
}
