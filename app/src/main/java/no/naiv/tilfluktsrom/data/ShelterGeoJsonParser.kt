package no.naiv.tilfluktsrom.data

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
                    zis.copyTo(buffer)
                    return buffer.toString(Charsets.UTF_8.name())
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("No GeoJSON file found in ZIP archive")
    }

    /**
     * Parse raw GeoJSON string into Shelter objects.
     */
    fun parseGeoJson(json: String): List<Shelter> {
        val root = JSONObject(json)
        val features = root.getJSONArray("features")
        val shelters = mutableListOf<Shelter>()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val geometry = feature.getJSONObject("geometry")
            val properties = feature.getJSONObject("properties")

            val coordinates = geometry.getJSONArray("coordinates")
            val easting = coordinates.getDouble(0)
            val northing = coordinates.getDouble(1)

            // Convert UTM33N to WGS84
            val latLon = CoordinateConverter.utm33nToWgs84(easting, northing)

            shelters.add(
                Shelter(
                    lokalId = properties.optString("lokalId", "unknown-$i"),
                    romnr = properties.optInt("romnr", 0),
                    plasser = properties.optInt("plasser", 0),
                    adresse = properties.optString("adresse", ""),
                    latitude = latLon.latitude,
                    longitude = latLon.longitude
                )
            )
        }

        return shelters
    }
}
