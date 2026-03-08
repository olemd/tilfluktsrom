package no.naiv.tilfluktsrom.util

import kotlin.math.*

/**
 * Distance and bearing calculations using the Haversine formula.
 */
object DistanceUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculate the distance in meters between two WGS84 points.
     */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Calculate the initial bearing (in degrees, 0=north, clockwise)
     * from point 1 to point 2.
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Format distance for display: meters if <1km, otherwise km with one decimal.
     */
    fun formatDistance(meters: Double): String {
        if (meters.isNaN()) return "—"
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            "${"%.1f".format(meters / 1000)} km"
        }
    }
}
