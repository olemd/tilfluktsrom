package no.naiv.tilfluktsrom.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Shelter::class], version = 1, exportSchema = false)
abstract class ShelterDatabase : RoomDatabase() {

    abstract fun shelterDao(): ShelterDao

    companion object {
        @Volatile
        private var INSTANCE: ShelterDatabase? = null

        fun getInstance(context: Context): ShelterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShelterDatabase::class.java,
                    "shelters.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
