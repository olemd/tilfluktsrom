package no.naiv.tilfluktsrom.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A public shelter (offentlig tilfluktsrom).
 * Coordinates are stored in WGS84 (EPSG:4326) after conversion from UTM33N.
 */
@Entity(tableName = "shelters")
data class Shelter(
    @PrimaryKey
    val lokalId: String,
    val romnr: Int,
    val plasser: Int,
    val adresse: String,
    val latitude: Double,
    val longitude: Double
)
