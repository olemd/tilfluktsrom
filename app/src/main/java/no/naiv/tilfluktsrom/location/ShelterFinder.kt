package no.naiv.tilfluktsrom.location

import no.naiv.tilfluktsrom.data.Shelter
import no.naiv.tilfluktsrom.util.DistanceUtils

/**
 * Result containing a shelter and its distance/bearing from the user.
 */
data class ShelterWithDistance(
    val shelter: Shelter,
    val distanceMeters: Double,
    val bearingDegrees: Double
)

/**
 * Finds the nearest shelters to a given location.
 */
object ShelterFinder {

    /**
     * Find the N nearest shelters to the given location.
     * Returns results sorted by distance (nearest first).
     */
    fun findNearest(
        shelters: List<Shelter>,
        latitude: Double,
        longitude: Double,
        count: Int = 3
    ): List<ShelterWithDistance> {
        return shelters
            .map { shelter ->
                ShelterWithDistance(
                    shelter = shelter,
                    distanceMeters = DistanceUtils.distanceMeters(
                        latitude, longitude,
                        shelter.latitude, shelter.longitude
                    ),
                    bearingDegrees = DistanceUtils.bearingDegrees(
                        latitude, longitude,
                        shelter.latitude, shelter.longitude
                    )
                )
            }
            .sortedBy { it.distanceMeters }
            .take(count)
    }
}
