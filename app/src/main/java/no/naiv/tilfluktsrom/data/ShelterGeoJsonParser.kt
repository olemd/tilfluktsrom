package no.naiv.tilfluktsrom.data

import android.util.Log
import no.naiv.tilfluktsrom.util.CoordinateConverter
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Parses shelter GeoJSON data from the Geonorge ZIP download.
 * Converts coordinates from UTM33N (EPSG:25833) to WGS84 (EPSG:4326).
 */
object ShelterGeoJsonParser {

    private const val TAG = "ShelterGeoJsonParser"

    // Maximum uncompressed size to prevent ZIP bomb attacks (10 MB)
    private const val MAX_UNCOMPRESSED_SIZE = 10 * 1024 * 1024L

    // Norway's approximate bounding box in WGS84
    private const val NORWAY_LAT_MIN = 57.0
    private const val NORWAY_LAT_MAX = 72.0
    private const val NORWAY_LON_MIN = 3.0
    private const val NORWAY_LON_MAX = 33.0

    /**
     * Extract and parse GeoJSON from a ZIP input stream.
     */
    fun parseFromZip(zipStream: InputStream): List<Shelter> {
        val json = extractGeoJsonFromZip(zipStream)
        return parseGeoJson(json)
    }

    private fun extractGeoJsonFromZip(zipStream: InputStream): String {
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".geojson") || entry.name.endsWith(".json")) {
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (zis.read(chunk).also { bytesRead = it } != -1) {
                        totalRead += bytesRead
                        if (totalRead > MAX_UNCOMPRESSED_SIZE) {
                            throw IllegalStateException(
                                "GeoJSON exceeds maximum size (${MAX_UNCOMPRESSED_SIZE / 1024}KB)"
                            )
                        }
                        buffer.write(chunk, 0, bytesRead)
                    }
                    return buffer.toString(Charsets.UTF_8.name())
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("No GeoJSON file found in ZIP archive")
    }

    /**
     * Parse raw GeoJSON string into Shelter objects.
     * Malformed individual features are skipped rather than failing the entire parse.
     */
    fun parseGeoJson(json: String): List<Shelter> {
        val root = JSONObject(json)
        val features = root.getJSONArray("features")
        val shelters = mutableListOf<Shelter>()
        var skipped = 0

        for (i in 0 until features.length()) {
            try {
                val feature = features.getJSONObject(i)
                val geometry = feature.getJSONObject("geometry")
                val properties = feature.getJSONObject("properties")

                val coordinates = geometry.getJSONArray("coordinates")
                val easting = coordinates.getDouble(0)
                val northing = coordinates.getDouble(1)

                // Convert UTM33N to WGS84
                val latLon = CoordinateConverter.utm33nToWgs84(easting, northing)

                // Validate coordinates are within Norway
                if (latLon.latitude !in NORWAY_LAT_MIN..NORWAY_LAT_MAX ||
                    latLon.longitude !in NORWAY_LON_MIN..NORWAY_LON_MAX
                ) {
                    Log.w(TAG, "Skipping shelter at index $i: coordinates outside Norway " +
                        "(${latLon.latitude}, ${latLon.longitude})")
                    skipped++
                    continue
                }

                // Require a valid primary key — without it shelters can collide in the DB
                val lokalId = properties.optString("lokalId", null)
                if (lokalId.isNullOrBlank()) {
                    Log.w(TAG, "Skipping shelter at index $i: missing lokalId")
                    skipped++
                    continue
                }

                val plasser = properties.optInt("plasser", 0)
                if (plasser < 0) {
                    Log.w(TAG, "Skipping shelter at index $i: negative capacity ($plasser)")
                    skipped++
                    continue
                }

                shelters.add(
                    Shelter(
                        lokalId = lokalId,
                        romnr = properties.optInt("romnr", 0),
                        plasser = plasser,
                        adresse = properties.optString("adresse", ""),
                        latitude = latLon.latitude,
                        longitude = latLon.longitude
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed shelter feature at index $i", e)
                skipped++
            }
        }

        if (skipped > 0) {
            Log.w(TAG, "Skipped $skipped malformed features out of ${features.length()}")
        }

        if (shelters.isEmpty()) {
            throw IllegalStateException(
                "No valid shelters found in GeoJSON (${features.length()} features were malformed)"
            )
        }

        return shelters
    }
}
