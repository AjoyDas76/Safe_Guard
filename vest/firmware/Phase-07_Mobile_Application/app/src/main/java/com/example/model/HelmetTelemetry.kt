package com.example.model

import com.example.data.SafetyRulesManager

/**
 * Helmet thresholds. The values are editable from the Safety Rules screen
 * (defaults match the helmet firmware Nano1_SensorNode_v3.ino and the web dashboard).
 */
object HelmetThresholds {
    const val NO_ECHO = 999
    const val PROX_SCALE_CM = 100
    val OBST_CM: Int get() = SafetyRulesManager.rules.value.helmetObstacleCm
    val GAS_MARGIN: Int get() = SafetyRulesManager.rules.value.helmetGasMargin
    val HR_MIN: Int get() = SafetyRulesManager.rules.value.helmetHrMin
    val HR_MAX: Int get() = SafetyRulesManager.rules.value.helmetHrMax
    val SPO2_MIN: Int get() = SafetyRulesManager.rules.value.helmetSpo2Min
}

enum class HelmetSide(val key: String, val label: String, val labelBn: String) {
    LEFT("L", "LEFT", "বাম"),
    FRONT("F", "FRONT", "সামনে"),
    RIGHT("R", "RIGHT", "ডান")
}

/** One normalized reading of /helmet1/live (or one /helmet1/logs record). */
data class HelmetReading(
    val heartRate: Double = 0.0,
    val spo2: Double = 0.0,
    val gasLevel: Double = 0.0,
    val gasBaseline: Double = 0.0,
    val gasFlag: Boolean = false,
    val obstacleFlag: Boolean = false,
    val distLeft: Int = HelmetThresholds.NO_ECHO,
    val distFront: Int = HelmetThresholds.NO_ECHO,
    val distRight: Int = HelmetThresholds.NO_ECHO,
    val obstLeft: Boolean = false,
    val obstFront: Boolean = false,
    val obstRight: Boolean = false
) {
    fun dist(side: HelmetSide): Int = when (side) {
        HelmetSide.LEFT -> distLeft
        HelmetSide.FRONT -> distFront
        HelmetSide.RIGHT -> distRight
    }

    fun obst(side: HelmetSide): Boolean = when (side) {
        HelmetSide.LEFT -> obstLeft
        HelmetSide.FRONT -> obstFront
        HelmetSide.RIGHT -> obstRight
    }

    val anyObstacle: Boolean get() = obstacleFlag || obstLeft || obstFront || obstRight
    val hrBad: Boolean get() = heartRate > 0 && (heartRate < HelmetThresholds.HR_MIN || heartRate > HelmetThresholds.HR_MAX)
    val spo2Bad: Boolean get() = spo2 > 0 && spo2 < HelmetThresholds.SPO2_MIN
    val gasAlertLevel: Double? get() = if (gasBaseline > 0) gasBaseline + HelmetThresholds.GAS_MARGIN else null
}

data class HelmetData(
    val reading: HelmetReading = HelmetReading(),
    val lastPacketTime: Long = 0L,
    val isOnline: Boolean = false,
    val accessDenied: Boolean = false,
    /** True once at least one packet has been received in this session. */
    val hasData: Boolean = false,
    val hrConfirmedBad: Boolean = false,
    val spo2ConfirmedBad: Boolean = false
)

enum class HelmetAlertKey(val label: String) {
    OBSTACLE("OBSTACLE"),
    GAS("GAS"),
    HR("HEART RATE"),
    SPO2("SPO2")
}

data class HelmetAlert(
    val id: String,
    val key: HelmetAlertKey,
    val title: String,
    val detail: String,
    val detailBn: String,
    val triggeredValue: String,
    val safeLimit: String
)

/** A /helmet1/logs record, used for reports and the alert history. */
data class HelmetLogEntry(
    val timestamp: Long,
    val reading: HelmetReading
)

data class HelmetEvent(
    val timestamp: Long,
    val label: String,
    val isAlert: Boolean,
    val badge: String,
    val details: String,
    val isOffline: Boolean = false,
    val reading: HelmetReading? = null,
    val alertTitles: List<String> = emptyList()
)

data class HelmetOfflineEvent(
    val from: Long,
    val to: Long,
    val durationMs: Long,
    val ongoing: Boolean
)

data class HelmetChartPoint(
    val timestamp: Long,
    val heartRate: Double,
    val spo2: Double,
    val gasLevel: Double,
    val gasAlertLevel: Double?,
    val distLeft: Int = HelmetThresholds.NO_ECHO,
    val distFront: Int = HelmetThresholds.NO_ECHO,
    val distRight: Int = HelmetThresholds.NO_ECHO
)

data class HelmetAlertLogRow(
    val timestamp: Long,
    val alerts: List<HelmetAlert>
)
