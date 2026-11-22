package com.example.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.AlertHistoryManager
import com.example.data.SafetyRulesManager
import com.example.model.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Smart Safety Helmet telemetry (Firebase RTDB /helmet1/live and /helmet1/logs).
 * Mirrors the helmet module of the web dashboard: same paths, same thresholds,
 * same 6 s offline window and the same 3-update debounce for abnormal vitals.
 */
object HelmetTelemetryManager {
    private const val TAG = "HelmetTelemetryManager"
    private const val RTDB_URL = "https://worker-safety-vest-92b97-default-rtdb.firebaseio.com"
    private const val LIVE_PATH = "helmet1/live"
    private const val LOGS_PATH = "helmet1/logs"

    private const val STALE_MS = 6000L
    private const val HEALTH_CONFIRM = 3
    private const val MAX_CHART_POINTS = 60
    private const val MAX_EVENTS = 10
    private const val MAX_REPORT_ENTRIES = 20000
    private const val OFFLINE_GAP_MS = 90_000L // logs are written every ~30 s

    const val HELMET_ID = "HELMET-001"

    private var database: FirebaseDatabase? = null
    private var liveRef: DatabaseReference? = null
    private var logsRef: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var notificationHelper: NotificationHelper? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _helmetData = MutableStateFlow(HelmetData())
    val helmetData: StateFlow<HelmetData> = _helmetData.asStateFlow()

    private val _activeAlerts = MutableStateFlow<List<HelmetAlert>>(emptyList())
    val activeAlerts: StateFlow<List<HelmetAlert>> = _activeAlerts.asStateFlow()

    /** Helmet alerts in the shared HazardAlert shape (used by the merged Dashboard / Alerts lists). */
    private val _activeHazards = MutableStateFlow<List<HazardAlert>>(emptyList())
    val activeHazards: StateFlow<List<HazardAlert>> = _activeHazards.asStateFlow()

    private val _chartHistory = MutableStateFlow<List<HelmetChartPoint>>(emptyList())
    val chartHistory: StateFlow<List<HelmetChartPoint>> = _chartHistory.asStateFlow()

    private val _events = MutableStateFlow<List<HelmetEvent>>(emptyList())
    val events: StateFlow<List<HelmetEvent>> = _events.asStateFlow()

    private var lastRaw: String? = null
    private var hrBadCount = 0
    private var spo2BadCount = 0

    private val watchdog = Runnable { goOffline() }

    fun init(context: Context) {
        if (listener != null) return
        val appCtx = context.applicationContext
        if (notificationHelper == null) notificationHelper = NotificationHelper(appCtx)
        try {
            database = FirebaseDatabase.getInstance(RTDB_URL)
            liveRef = database?.getReference(LIVE_PATH)
            logsRef = database?.getReference(LOGS_PATH)
            attachListener()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing helmet RTDB: ${e.message}", e)
        }
    }

    private fun attachListener() {
        val ref = liveRef ?: return
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val raw = snapshot.value?.toString() ?: return
                if (raw == lastRaw) return
                lastRaw = raw
                processSnapshot(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Helmet listener cancelled: ${error.message}")
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    _helmetData.value = _helmetData.value.copy(accessDenied = true, isOnline = false)
                }
            }
        }
        listener = l
        ref.addValueEventListener(l)
    }

    // ---------- parsing ----------
    private fun num(snap: DataSnapshot, key: String): Double? {
        val v = snap.child(key).value ?: return null
        return when (v) {
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    internal fun parseReading(snap: DataSnapshot): HelmetReading {
        val rules = SafetyRulesManager.rules.value
        fun rawFlag(key: String) = (num(snap, key) ?: 0.0) == 1.0
        fun dist(key: String) = (num(snap, key) ?: HelmetThresholds.NO_ECHO.toDouble()).toInt()

        val dL = dist("distLeft")
        val dF = dist("distFront")
        val dR = dist("distRight")
        // Obstacle: use the distance and the editable Safety Rules limit; fall back to the firmware flag
        // only when the sensor reports "nothing in range".
        fun hit(d: Int, flagKey: String) =
            if (d < HelmetThresholds.NO_ECHO) d < rules.helmetObstacleCm else rawFlag(flagKey)
        val allClear = dL >= HelmetThresholds.NO_ECHO && dF >= HelmetThresholds.NO_ECHO && dR >= HelmetThresholds.NO_ECHO

        val gasLevel = num(snap, "gasLevel") ?: 0.0
        val gasBase = num(snap, "gasBaseline") ?: 0.0
        val gasFlag = if (gasBase > 0) gasLevel > gasBase + rules.helmetGasMargin else rawFlag("gas")

        return HelmetReading(
            heartRate = num(snap, "heartRate") ?: 0.0,
            spo2 = num(snap, "spo2") ?: 0.0,
            gasLevel = gasLevel,
            gasBaseline = gasBase,
            gasFlag = gasFlag,
            obstacleFlag = allClear && rawFlag("obstacle"),
            distLeft = dL,
            distFront = dF,
            distRight = dR,
            obstLeft = hit(dL, "obstLeft"),
            obstFront = hit(dF, "obstFront"),
            obstRight = hit(dR, "obstRight")
        )
    }

    // ---------- alert rules (same as the web dashboard) ----------
    /**
     * [hrConfirmed]/[spo2Confirmed] tell whether abnormal vitals are confirmed.
     * The live view debounces them (3 updates); logs do not.
     */
    fun evaluateAlerts(p: HelmetReading, hrConfirmed: Boolean, spo2Confirmed: Boolean): List<HelmetAlert> {
        val list = mutableListOf<HelmetAlert>()
        var sideHit = false
        HelmetSide.values().forEach { s ->
            if (!p.obst(s)) return@forEach
            sideHit = true
            val d = p.dist(s)
            val dTxt = if (d >= HelmetThresholds.NO_ECHO) "" else "$d cm"
            list.add(
                HelmetAlert(
                    id = "helmet_obstacle_${s.key.lowercase()}",
                    key = HelmetAlertKey.OBSTACLE,
                    title = "Obstacle Detected — ${s.label}",
                    detail = (if (dTxt.isNotEmpty()) "Object $dTxt away" else "Object detected") +
                            " — closer than ${HelmetThresholds.OBST_CM} cm",
                    detailBn = "${s.labelBn} দিকে বাধা" + (if (dTxt.isNotEmpty()) " ($dTxt)" else "") +
                            " — ${HelmetThresholds.OBST_CM} সেমি-র চেয়ে কাছে",
                    triggeredValue = if (dTxt.isNotEmpty()) dTxt else "DETECTED",
                    safeLimit = "> ${HelmetThresholds.OBST_CM} cm"
                )
            )
        }
        if (!sideHit && p.obstacleFlag) {
            list.add(
                HelmetAlert(
                    id = "helmet_obstacle",
                    key = HelmetAlertKey.OBSTACLE,
                    title = "Obstacle Detected",
                    detail = "An ultrasonic sensor reports an obstacle",
                    detailBn = "আলট্রাসনিক সেন্সর বাধা শনাক্ত করেছে",
                    triggeredValue = "DETECTED",
                    safeLimit = "> ${HelmetThresholds.OBST_CM} cm"
                )
            )
        }
        if (p.gasFlag) {
            val extra = if (p.gasBaseline > 0) " — baseline ${p.gasBaseline.toInt()} (+${(p.gasLevel - p.gasBaseline).toInt()})" else ""
            list.add(
                HelmetAlert(
                    id = "helmet_gas",
                    key = HelmetAlertKey.GAS,
                    title = "Gas Detected",
                    detail = "MQ-2 level ${p.gasLevel.toInt()}$extra",
                    detailBn = "গ্যাস শনাক্ত হয়েছে: MQ-2 লেভেল ${p.gasLevel.toInt()}" +
                            (if (p.gasBaseline > 0) " (বেসলাইন ${p.gasBaseline.toInt()})" else ""),
                    triggeredValue = "${p.gasLevel.toInt()} ADC",
                    safeLimit = p.gasAlertLevel?.let { "< ${it.toInt()} ADC" } ?: "Baseline + ${HelmetThresholds.GAS_MARGIN}"
                )
            )
        }
        if (hrConfirmed) {
            val low = p.heartRate < HelmetThresholds.HR_MIN
            list.add(
                HelmetAlert(
                    id = "helmet_hr",
                    key = HelmetAlertKey.HR,
                    title = if (low) "Low Heart Rate" else "High Heart Rate",
                    detail = "Reading is ${p.heartRate.toInt()} bpm — outside the safe range of ${HelmetThresholds.HR_MIN} – ${HelmetThresholds.HR_MAX} bpm",
                    detailBn = "${if (low) "কম" else "বেশি"} হার্ট রেট: ${p.heartRate.toInt()} bpm (নিরাপদ সীমা ${HelmetThresholds.HR_MIN}–${HelmetThresholds.HR_MAX})",
                    triggeredValue = "${p.heartRate.toInt()} bpm",
                    safeLimit = "${HelmetThresholds.HR_MIN} – ${HelmetThresholds.HR_MAX} bpm"
                )
            )
        }
        if (spo2Confirmed) {
            list.add(
                HelmetAlert(
                    id = "helmet_spo2",
                    key = HelmetAlertKey.SPO2,
                    title = "Low SpO₂",
                    detail = "Reading is ${p.spo2.toInt()}% — below the safe minimum of ${HelmetThresholds.SPO2_MIN}%",
                    detailBn = "SpO₂ কম: ${p.spo2.toInt()}% (নিরাপদ সীমা ${HelmetThresholds.SPO2_MIN}% এর উপরে)",
                    triggeredValue = "${p.spo2.toInt()}%",
                    safeLimit = "≥ ${HelmetThresholds.SPO2_MIN}%"
                )
            )
        }
        return list
    }

    // ---------- live processing ----------
    private fun processSnapshot(snapshot: DataSnapshot) {
        val now = System.currentTimeMillis()
        mainHandler.removeCallbacks(watchdog)
        mainHandler.postDelayed(watchdog, STALE_MS)

        val p = parseReading(snapshot)
        val wasOffline = !_helmetData.value.isOnline

        hrBadCount = if (p.hrBad) hrBadCount + 1 else 0
        spo2BadCount = if (p.spo2Bad) spo2BadCount + 1 else 0
        val hrConfirmed = hrBadCount >= HEALTH_CONFIRM
        val spo2Confirmed = spo2BadCount >= HEALTH_CONFIRM

        _helmetData.value = HelmetData(
            reading = p,
            lastPacketTime = now,
            isOnline = true,
            accessDenied = false,
            hasData = true,
            hrConfirmedBad = hrConfirmed,
            spo2ConfirmedBad = spo2Confirmed
        )

        val alerts = evaluateAlerts(p, hrConfirmed, spo2Confirmed)
        val hazards = alerts.map { toHazard(it) }
        _activeAlerts.value = alerts
        _activeHazards.value = hazards
        AlertHistoryManager.onHelmetAlertsChanged(hazards)

        if (alerts.isNotEmpty()) {
            notifyAlerts(alerts, hazards)
        }

        val chart = _chartHistory.value.toMutableList()
        chart.add(
            HelmetChartPoint(
                timestamp = now,
                heartRate = p.heartRate,
                spo2 = p.spo2,
                gasLevel = p.gasLevel,
                gasAlertLevel = p.gasAlertLevel,
                distLeft = p.distLeft,
                distFront = p.distFront,
                distRight = p.distRight
            )
        )
        _chartHistory.value = chart.takeLast(MAX_CHART_POINTS)

        val hr = if (p.heartRate > 0) p.heartRate.toInt().toString() else "--"
        val sp = if (p.spo2 > 0) p.spo2.toInt().toString() else "--"
        addEvent(
            HelmetEvent(
                timestamp = now,
                label = if (alerts.isNotEmpty()) "Helmet Hazard Alert" else "Helmet Sync",
                isAlert = alerts.isNotEmpty(),
                badge = if (alerts.isNotEmpty()) "ALERT" else "SAFE",
                details = "HR $hr | SpO₂ $sp | Gas ${p.gasLevel.toInt()}",
                reading = p,
                alertTitles = alerts.map { it.title }
            )
        )
        if (wasOffline) Log.i(TAG, "Helmet streaming")
    }

    private fun toHazard(a: HelmetAlert) = HazardAlert(
        id = a.id,
        title = "Helmet · ${a.title}",
        description = a.detail,
        severity = AlertSeverity.CRITICAL,
        sensorType = "HELMET_${a.key.name}",
        triggeredValue = a.triggeredValue,
        safeLimit = a.safeLimit
    )

    private fun notifyAlerts(alerts: List<HelmetAlert>, hazards: List<HazardAlert>) {
        notificationHelper?.handleAlerts(hazards)
        val top = alerts.first()
        EmergencySirenManager.triggerEmergencyAlarm("HELMET HAZARD: ${top.title.uppercase()} (${top.triggeredValue})")
    }

    private fun addEvent(e: HelmetEvent) {
        _events.value = (listOf(e) + _events.value).take(MAX_EVENTS)
    }

    private fun goOffline() {
        if (!_helmetData.value.isOnline) return
        Log.w(TAG, "Helmet watchdog: no data for ${STALE_MS / 1000}s -> OFFLINE")
        hrBadCount = 0
        spo2BadCount = 0
        lastRaw = null
        _helmetData.value = _helmetData.value.copy(isOnline = false, hrConfirmedBad = false, spo2ConfirmedBad = false)
        _activeAlerts.value = emptyList()
        _activeHazards.value = emptyList()
        AlertHistoryManager.onHelmetAlertsChanged(emptyList())
        addEvent(
            HelmetEvent(
                timestamp = System.currentTimeMillis(),
                label = "Helmet Connection Lost",
                isAlert = true,
                badge = "OFFLINE",
                details = "No data received",
                isOffline = true
            )
        )
    }

    // ---------- logs (reports + alert history) ----------
    fun fetchLogs(startMillis: Long, onResult: (Result<List<HelmetLogEntry>>) -> Unit) {
        val ref = logsRef
        if (ref == null) {
            onResult(Result.failure(IllegalStateException("Helmet database not initialised")))
            return
        }
        ref.orderByChild("timestamp").startAt(startMillis.toDouble()).limitToLast(MAX_REPORT_ENTRIES)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<HelmetLogEntry>()
                    for (child in snapshot.children) {
                        val ts = (child.child("timestamp").value as? Number)?.toLong() ?: continue
                        list.add(HelmetLogEntry(ts, parseReading(child)))
                    }
                    list.sortBy { it.timestamp }
                    onResult(Result.success(list))
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            })
    }

    fun offlineEvents(entries: List<HelmetLogEntry>): List<HelmetOfflineEvent> {
        val out = mutableListOf<HelmetOfflineEvent>()
        for (i in 1 until entries.size) {
            val gap = entries[i].timestamp - entries[i - 1].timestamp
            if (gap > OFFLINE_GAP_MS) out.add(HelmetOfflineEvent(entries[i - 1].timestamp, entries[i].timestamp, gap, false))
        }
        if (entries.isNotEmpty()) {
            val last = entries.last().timestamp
            val now = System.currentTimeMillis()
            if (now - last > OFFLINE_GAP_MS) out.add(HelmetOfflineEvent(last, now, now - last, true))
        }
        return out
    }

    /** Alert rows for logged readings (logs are not debounced, like the web dashboard). */
    fun alertRows(entries: List<HelmetLogEntry>): List<HelmetAlertLogRow> =
        entries.mapNotNull { e ->
            val a = evaluateAlerts(e.reading, e.reading.hrBad, e.reading.spo2Bad)
            if (a.isEmpty()) null else HelmetAlertLogRow(e.timestamp, a)
        }
}
