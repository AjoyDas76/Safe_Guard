package com.example.ai

import com.example.data.SafetyRulesManager
import com.example.manager.HelmetTelemetryManager
import com.example.manager.VestTelemetryManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Connects the AI engine to the app's real data. READ-ONLY - nothing is written to Firebase.
 *
 *  - thresholds : the editable Safety Rules (so the AI agrees with the app's own alerts)
 *  - live       : the existing VestTelemetryManager / HelmetTelemetryManager state
 *  - vest logs  : /worker1/logs  (field "ts")        - the same path the website reads
 *  - helmet logs: /helmet1/logs  (via HelmetTelemetryManager.fetchLogs, rules already applied)
 *
 * NOTE: the app's own VestTelemetryManager.fetchHistory() reads /worker1/history and falls back to
 * SYNTHETIC data when it is empty. The AI must never analyse synthetic data, so it reads the real
 * /worker1/logs directly (exactly like the website).
 */
object FirebaseAiDataSource : AiDataSource {

    private const val RTDB_URL = "https://worker-safety-vest-92b97-default-rtdb.firebaseio.com"
    private const val VEST_LOGS = "worker1/logs"
    private const val LOG_LIMIT = 20000
    private const val CACHE_TTL_MS = 30_000L

    private class Cached<T>(val at: Long, val rows: List<T>)
    private val vestCache = ConcurrentHashMap<Long, Cached<VestRow>>()
    private val helmetCache = ConcurrentHashMap<Long, Cached<HelmetRow>>()

    /** Drop cached logs (the Analyze screen always works on fresh data). */
    fun clearCache() { vestCache.clear(); helmetCache.clear() }

    // ------------------------------------------------------------ thresholds
    override fun thresholds(): Thresholds {
        val r = SafetyRulesManager.rules.value
        return Thresholds(
            tempMin = r.tempLowWarning, tempMax = r.tempHighCritical,
            humMin = r.humidityLowWarning, humMax = r.humidityHighWarning,
            presMin = r.pressureLowWarning, presMax = r.pressureHighWarning,
            hrMin = r.helmetHrMin.toDouble(), hrMax = r.helmetHrMax.toDouble(),
            spo2Min = r.helmetSpo2Min.toDouble()
        )
    }

    // ------------------------------------------------------------ live
    override suspend fun live(): LiveSnapshot {
        val v = VestTelemetryManager.vestData.value
        val h = HelmetTelemetryManager.helmetData.value
        // The data classes carry demo defaults before the first packet; ignore them until real data arrived.
        val vestSeen = v.lastPacketTime > 0L
        val vest = if (vestSeen) VestLive(
            temp = v.environment.temperature, hum = v.environment.humidity, pres = v.environment.pressure,
            motion = v.status.motion_state.trim().uppercase(),
            fall = v.alerts.fall_detected || v.status.motion_state.trim().equals("FALL", ignoreCase = true),
            sos = v.alerts.sos_active,
            lat = v.gps.latitude, lng = v.gps.longitude, rssi = null
        ) else null
        val helmet = if (h.hasData) h.reading.toRow(System.currentTimeMillis()) else null
        return LiveSnapshot(
            vest, helmet, vestOnline = v.isOnline, helmetOnline = h.isOnline,
            simulated = VestTelemetryManager.isSimulationActive()
        )
    }

    private fun com.example.model.HelmetReading.toRow(ts: Long) = HelmetRow(
        ts = ts, hr = heartRate, spo2 = spo2, gas = gasLevel, gasBase = gasBaseline, gasFlag = gasFlag,
        obstFlag = anyObstacle,
        distL = distLeft.toDouble(), distF = distFront.toDouble(), distR = distRight.toDouble()
    )

    // ------------------------------------------------------------ logs
    override suspend fun rows(scope: Scope): Rows = coroutineScope {
        val v = async { runCatching { vestLogs(scope.since) } }
        val h = async { runCatching { helmetLogs(scope.since) } }
        val vr = v.await()
        val hr = h.await()
        if (vr.isFailure && hr.isFailure) throw (vr.exceptionOrNull() ?: hr.exceptionOrNull() ?: RuntimeException("NO_DB"))
        Rows(
            vest = (vr.getOrNull() ?: emptyList()).filter { it.ts <= scope.until },
            helmet = (hr.getOrNull() ?: emptyList()).filter { it.ts <= scope.until },
            vestOk = vr.isSuccess, helmetOk = hr.isSuccess
        )
    }

    private suspend fun vestLogs(since: Long): List<VestRow> {
        val key = since / 60_000L
        vestCache[key]?.let { if (System.currentTimeMillis() - it.at < CACHE_TTL_MS) return it.rows }
        val snap = suspendCancellableCoroutine<DataSnapshot> { cont ->
            FirebaseDatabase.getInstance(RTDB_URL).getReference(VEST_LOGS)
                .orderByChild("ts").startAt(since.toDouble()).limitToLast(LOG_LIMIT)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) { if (cont.isActive) cont.resume(snapshot) }
                    override fun onCancelled(error: DatabaseError) {
                        if (cont.isActive) cont.resumeWithException(RuntimeException(error.code.toString() + ":" + error.message, error.toException()))
                    }
                })
        }
        // Parsing up to 20 000 children must not run on the main thread.
        val rows = withContext(Dispatchers.Default) {
            val out = ArrayList<VestRow>()
            for (c in snap.children) {
                val ts = (c.child("ts").value as? Number)?.toLong() ?: continue
                fun num(k: String) = (c.child(k).value as? Number)?.toDouble()
                fun flag(k: String): Boolean = when (val x = c.child(k).value) { is Boolean -> x; is Number -> x.toInt() != 0; else -> false }
                out.add(VestRow(
                    ts = ts, temp = num("temperature"), hum = num("humidity"), pres = num("pressure"),
                    fall = flag("fall"), sos = flag("sos"),
                    motion = (c.child("motion_state").value as? String)?.trim()?.uppercase() ?: "",
                    lat = num("latitude"), lng = num("longitude")
                ))
            }
            out.sortBy { it.ts }
            out
        }
        vestCache[key] = Cached(System.currentTimeMillis(), rows)
        return rows
    }

    private suspend fun helmetLogs(since: Long): List<HelmetRow> {
        val key = since / 60_000L
        helmetCache[key]?.let { if (System.currentTimeMillis() - it.at < CACHE_TTL_MS) return it.rows }
        val entries = suspendCancellableCoroutine<List<com.example.model.HelmetLogEntry>> { cont ->
            HelmetTelemetryManager.fetchLogs(since) { result ->
                if (!cont.isActive) return@fetchLogs
                result.fold({ cont.resume(it) }, { cont.resumeWithException(it) })
            }
        }
        val rows = entries.map { it.reading.toRow(it.timestamp) }.sortedBy { it.ts }
        helmetCache[key] = Cached(System.currentTimeMillis(), rows)
        return rows
    }
}
