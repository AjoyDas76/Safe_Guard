package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TelemetryEntity::class, AlertHistoryEntity::class], version = 2, exportSchema = false)
abstract class VestDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao
    abstract fun alertHistoryDao(): AlertHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: VestDatabase? = null

        fun getInstance(context: Context): VestDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VestDatabase::class.java,
                    "vest_command_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
