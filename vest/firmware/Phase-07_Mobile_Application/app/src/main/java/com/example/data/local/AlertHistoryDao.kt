package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AlertHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AlertHistoryEntity>)

    @Query("SELECT * FROM alert_history ORDER BY triggeredTimestamp DESC")
    fun getAllHistoryFlow(): Flow<List<AlertHistoryEntity>>

    @Query("SELECT * FROM alert_history ORDER BY triggeredTimestamp DESC")
    suspend fun getAllHistoryList(): List<AlertHistoryEntity>

    @Query("SELECT * FROM alert_history WHERE isStillActive = 1")
    suspend fun getActiveItems(): List<AlertHistoryEntity>

    @Query("DELETE FROM alert_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM alert_history")
    suspend fun getCount(): Int
}
