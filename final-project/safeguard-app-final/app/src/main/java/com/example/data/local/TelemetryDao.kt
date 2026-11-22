package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TelemetryEntity): Long

    @Query("SELECT * FROM telemetry_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogsFlow(): Flow<List<TelemetryEntity>>

    @Query("SELECT * FROM telemetry_logs WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getLogsSince(sinceTimestamp: Long): List<TelemetryEntity>

    @Query("SELECT * FROM telemetry_logs WHERE fallDetected = 1 OR sosActive = 1 ORDER BY timestamp DESC LIMIT 50")
    fun getEmergencyLogsFlow(): Flow<List<TelemetryEntity>>

    @Query("SELECT COUNT(*) FROM telemetry_logs")
    suspend fun getCount(): Int

    @Query("DELETE FROM telemetry_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeOldLogs(cutoffTimestamp: Long)
}
