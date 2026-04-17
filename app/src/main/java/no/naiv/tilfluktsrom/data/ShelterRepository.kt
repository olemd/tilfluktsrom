package no.naiv.tilfluktsrom.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Repository managing shelter data: local Room cache + remote Geonorge download.
 *
 * Offline-first strategy:
 * 1. On first launch, seed from bundled shelters.json asset (no network needed)
 * 2. Try to download latest data from Geonorge in the background
 * 3. Refresh automatically when data is older than 7 days
 */
class ShelterRepository(private val context: Context) {

    companion object {
        private const val TAG = "ShelterRepository"
        private const val PREFS_NAME = "shelter_prefs"
        private const val KEY_LAST_UPDATE = "last_update_ms"
        private const val UPDATE_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
        private const val BUNDLED_ASSET = "shelters.json"

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
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "Tilfluktsrom/1.9.0")
                .build())
        })
        .build()

    /** Reactive stream of all shelters from local cache. */
    fun getAllShelters(): Flow<List<Shelter>> = dao.getAllShelters()

    /** Check if we have cached shelter data. */
    suspend fun hasCachedData(): Boolean = dao.count() > 0

    /** Timestamp (epoch ms) of the last successful data update, or 0 if never updated. */
    fun getLastUpdateMs(): Long = prefs.getLong(KEY_LAST_UPDATE, 0)

    /** Check if the cached data is stale and should be refreshed. */
    fun isDataStale(): Boolean {
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
        return System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS
    }

    /**
     * Seed the database from the bundled shelters.json asset.
     * This is pre-processed at build time (UTM33N already converted to WGS84).
     * Returns true if seeding succeeded.
     */
    suspend fun seedFromAsset(): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
            val shelters = parseBundledJson(json)
            Log.d(TAG, "Seeding ${shelters.size} shelters from bundled asset")

            db.withTransaction {
                dao.deleteAll()
                dao.insertAll(shelters)
            }

            // Mark as seeded but with timestamp 0 so it's considered stale
            // and will be refreshed from network when possible
            prefs.edit().putLong(KEY_LAST_UPDATE, 0).apply()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed from bundled asset", e)
            false
        }
    }

    /**
     * Download shelter data from Geonorge and cache it locally.
     * Returns true on success, false on failure.
     */
    suspend fun refreshData(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading shelter data from Geonorge...")

            val request = Request.Builder()
                .url(SHELTER_DATA_URL)
                .header("User-Agent", "Tilfluktsrom-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "Download failed: HTTP ${resp.code}")
                    return@withContext false
                }

                val body = resp.body ?: run {
                    Log.e(TAG, "Empty response body")
                    return@withContext false
                }

                val shelters = body.byteStream().use { stream ->
                    ShelterGeoJsonParser.parseFromZip(stream)
                }

                Log.d(TAG, "Parsed ${shelters.size} shelters, saving to database...")

                // Atomic replace: delete + insert in a single transaction
                db.withTransaction {
                    dao.deleteAll()
                    dao.insertAll(shelters)
                }

                prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
                Log.d(TAG, "Shelter data updated successfully")
                true
            }
        } catch (e: CancellationException) {
            throw e // Never swallow coroutine cancellation
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh shelter data", e)
            false
        }
    }

    /**
     * Parse the pre-processed bundled JSON (already WGS84, no coordinate conversion needed).
     */
    private fun parseBundledJson(json: String): List<Shelter> {
        val array = JSONArray(json)
        val shelters = mutableListOf<Shelter>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val lokalId: String? = obj.optString("lokalId", null)
            if (lokalId.isNullOrBlank()) continue

            shelters.add(
                Shelter(
                    lokalId = lokalId,
                    romnr = obj.optInt("romnr", 0),
                    plasser = obj.optInt("plasser", 0),
                    adresse = obj.optString("adresse", ""),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude")
                )
            )
        }

        return shelters
    }
}
