package no.naiv.tilfluktsrom.util

import kotlin.math.*

/**
 * Converts UTM zone 33N (EPSG:25833) coordinates to WGS84 (EPSG:4326).
 *
 * The shelter data from Geonorge uses EUREF89 UTM zone 33N. EUREF89 is
 * identical to WGS84 for all practical purposes (sub-meter difference).
 *
 * The conversion uses the Karney method (series expansion) for accuracy.
 */
object CoordinateConverter {

    // WGS84 ellipsoid parameters
    private const val A = 6378137.0              // semi-major axis (meters)
    private const val F = 1.0 / 298.257223563    // flattening
    private const val E2 = 2 * F - F * F         // eccentricity squared

    // UTM parameters
    private const val K0 = 0.9996                // scale factor
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING = 0.0       // northern hemisphere
    private const val ZONE_33_CENTRAL_MERIDIAN = 15.0 // degrees

    data class LatLon(val latitude: Double, val longitude: Double)

    /**
     * Convert UTM33N easting/northing to WGS84 latitude/longitude.
     */
    fun utm33nToWgs84(easting: Double, northing: Double): LatLon {
        val x = easting - FALSE_EASTING
        val y = northing - FALSE_NORTHING

        val e1 = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))

        val m = y / K0
        val mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256))

        // Footprint latitude using series expansion
        val phi1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4) / 512) * sin(8 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)

        val n1 = A / sqrt(1 - E2 * sinPhi1 * sinPhi1)
        val t1 = tanPhi1 * tanPhi1
        val c1 = (E2 / (1 - E2)) * cosPhi1 * cosPhi1
        val r1 = A * (1 - E2) / (1 - E2 * sinPhi1 * sinPhi1).pow(1.5)
        val d = x / (n1 * K0)

        val lat = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2 -
            (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * E2 / (1 - E2)) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * E2 / (1 - E2) - 3 * c1 * c1) * d.pow(6) / 720
        )

        val lon = (d -
            (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * E2 / (1 - E2) + 24 * t1 * t1) * d.pow(5) / 120
        ) / cosPhi1

        return LatLon(
            latitude = Math.toDegrees(lat),
            longitude = ZONE_33_CENTRAL_MERIDIAN + Math.toDegrees(lon)
        )
    }
}
