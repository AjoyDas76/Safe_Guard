package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telemetry_logs")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val workerId: String,
    val workerName: String,
    val temperature: Double,
    val humidity: Double,
    val pressure: Double,
    val voltage: Double,
    val latitude: Double,
    val longitude: Double,
    val motionState: String,
    val fallDetected: Boolean,
    val sosActive: Boolean,
    val isOnline: Boolean,
    val alertsSummary: String
)
