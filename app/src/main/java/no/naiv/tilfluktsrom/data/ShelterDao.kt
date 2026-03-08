package no.naiv.tilfluktsrom.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelterDao {

    @Query("SELECT * FROM shelters")
    fun getAllShelters(): Flow<List<Shelter>>

    @Query("SELECT * FROM shelters")
    suspend fun getAllSheltersList(): List<Shelter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shelters: List<Shelter>)

    @Query("DELETE FROM shelters")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM shelters")
    suspend fun count(): Int
}
