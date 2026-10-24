package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_history")
data class AlertHistoryEntity(
    @PrimaryKey
    val id: String,
    val alertKey: String,
    val title: String,
    val description: String,
    val severity: String,
    val sensorType: String,
    val triggeredValue: String,
    val safeLimit: String,
    val triggeredTimestamp: Long,
    val resolvedTimestamp: Long? = null,
    val isStillActive: Boolean = false
)
