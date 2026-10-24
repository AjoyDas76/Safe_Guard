package com.example.model

data class AlertsData(
    val fall_detected: Boolean = false,
    val sos_active: Boolean = false
)

data class BatteryData(
    val voltage: Double = 0.0
) {
    val percentage: Int
        get() {
            // Standard Li-ion 3.3V (0%) to 4.2V (100%)
            val pct = ((voltage - 3.3) / (4.2 - 3.3) * 100.0).toInt()
            return pct.coerceIn(0, 100)
        }
}

data class EnvironmentData(
    val humidity: Double = 76.6,
    val pressure: Double = 997.9,
    val temperature: Double = 28.9
)

data class GpsData(
    val latitude: Double = 23.8103,
    val longitude: Double = 90.4125
)

data class StatusData(
    val motion_state: String = "LYING"
)

data class VestData(
    val alerts: AlertsData = AlertsData(),
    val battery: BatteryData = BatteryData(voltage = 3.95),
    val environment: EnvironmentData = EnvironmentData(),
    val gps: GpsData = GpsData(latitude = 23.8103, longitude = 90.4125), // Industrial site default coords
    val status: StatusData = StatusData(motion_state = "LYING"),
    val lastPacketTime: Long = 0L,
    val isOnline: Boolean = false
)

data class EventSnapshot(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val pressure: Double = 0.0,
    val voltage: Double = 0.0,
    val motion_state: String = "STANDBY",
    val fall_detected: Boolean = false,
    val sos_active: Boolean = false,
    val activeAlerts: List<String> = emptyList(),
    val isOnline: Boolean = true
)

enum class AlertSeverity {
    WARNING,
    CRITICAL
}

data class HazardAlert(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val sensorType: String,
    val triggeredValue: String,
    val safeLimit: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class WorkerProfile(
    val workerId: String = "VST-001",
    val workerName: String = "Rahim Uddin",
    val phone: String = "+880 1711-234567",
    val email: String = "rahim.safety@safeguard.io",
    val bloodGroup: String = "O+",
    val assignedVestId: String = "VEST-LoRa-01",
    val loraNode: String = "worker1",
    val loraFrequency: String = "915.0 MHz (LoRa P2P)",
    val department: String = "Hazardous Drilling & Unit 4",
    val shift: String = "Day Shift (08:00 - 20:00)",
    val batchId: String = "SV-2026-014",
    val emergencyContactName: String = "Dr. Farhana Akter",
    val emergencyContactPhone: String = "+880 1999-911911",
    val emergencyRelation: String = "Site Chief Medical Officer",
    val secondaryContactName: String = "Rafiqul Hasan (Supervisor)",
    val secondaryContactPhone: String = "+880 1812-334455",
    val medicalNotes: String = "No known allergies. Heat stress sensitive.",
    val isActive: Boolean = true
)

data class DangerZone(
    val id: String,
    val name: String,
    val hazardType: String, // Gas Leak, Heavy Crane, High Voltage, Deep Excavation
    val centerLat: Double,
    val centerLng: Double,
    val radiusMeters: Double = 150.0,
    val description: String = "Restricted hazard zone"
)
