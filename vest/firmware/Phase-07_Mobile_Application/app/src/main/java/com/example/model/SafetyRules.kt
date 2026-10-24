package com.example.model

data class SafetyRules(
    val tempHighCritical: Double = 45.0,
    val tempLowWarning: Double = 15.0,
    val pressureHighWarning: Double = 1050.0,
    val pressureLowWarning: Double = 950.0,
    val humidityHighWarning: Double = 90.0,
    val humidityLowWarning: Double = 20.0,
    val fallDetectionEnabled: Boolean = true,
    val immobilityTimeoutSec: Int = 60,
    val sosBuzzerEnabled: Boolean = true,
    // Smart Helmet thresholds
    val helmetHrMin: Int = 50,
    val helmetHrMax: Int = 120,
    val helmetSpo2Min: Int = 90,
    val helmetGasMargin: Int = 150,
    val helmetObstacleCm: Int = 50,
    val lastUpdated: Long = System.currentTimeMillis()
)
