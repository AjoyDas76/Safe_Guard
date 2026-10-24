package com.example.model

data class AlertHistoryItem(
    val id: String,
    val alertKey: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val sensorType: String, // "TEMPERATURE", "HUMIDITY", "PRESSURE", "SOS", "FALL", "GEOFENCE"
    val triggeredValue: String,
    val safeLimit: String,
    val triggeredTimestamp: Long,
    val resolvedTimestamp: Long? = null,
    val isStillActive: Boolean = false
)
