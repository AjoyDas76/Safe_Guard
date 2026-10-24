package com.example.ai

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/*
 * SafeGuard AI engine - Kotlin port of the website's ai-engine.js.
 *
 * Pure Kotlin on purpose (no Android / Firebase imports) so that
 *   - it runs fully offline, no API key, no cost (same as the website),
 *   - it can be unit-tested on the JVM and compared 1:1 with the JS engine.
 *
 * Differences from the website: the fixed thresholds are NOT hard-coded here,
 * they are passed in as [Thresholds] (the app builds them from the editable
 * Safety Rules screen), so the AI always agrees with the app's own alerts.
 */

// ---------------------------------------------------------------- thresholds
data class Thresholds(
    val tempMin: Double = 15.0, val tempMax: Double = 45.0,
    val humMin: Double = 20.0, val humMax: Double = 90.0,
    val presMin: Double = 950.0, val presMax: Double = 1050.0,
    val hrMin: Double = 50.0, val hrMax: Double = 120.0,
    val spo2Min: Double = 90.0
)

const val NO_ECHO = 999
const val GAP_MS = 90_000L // logs are written about every 30 s

// ---------------------------------------------------------------- rows
data class VestRow(
    val ts: Long,
    val temp: Double?, val hum: Double?, val pres: Double?,
    val fall: Boolean, val sos: Boolean,
    val motion: String,
    val lat: Double?, val lng: Double?
)

data class HelmetRow(
    val ts: Long,
    val hr: Double, val spo2: Double,
    val gas: Double, val gasBase: Double, val gasFlag: Boolean,
    val obstFlag: Boolean,
    val distL: Double?, val distF: Double?, val distR: Double?
)

/** Live (latest) vest values. */
data class VestLive(
    val temp: Double?, val hum: Double?, val pres: Double?,
    val motion: String, val fall: Boolean, val sos: Boolean,
    val lat: Double?, val lng: Double?, val rssi: Double?
)

data class AlertHit(val key: String, val bn: String, val en: String)

// ---------------------------------------------------------------- number helpers
/** Same output as JavaScript's Number.prototype.toFixed. */
fun fx(v: Double, d: Int): String {
    if (v.isNaN()) return "NaN"
    if (v < 0) return "-" + fx(-v, d)
    return BigDecimal(v).setScale(d, RoundingMode.HALF_UP).toPlainString()
}

/** Prints whole numbers without ".0" (like JS string concatenation). */
fun nstr(v: Double): String =
    if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

fun durBn(ms: Long): String {
    val m = Math.round(ms / 60000.0)
    return if (m < 1) Math.round(ms / 1000.0).toString() + " সেকেন্ড"
    else if (m < 60) "$m মিনিট"
    else (m / 60).toString() + " ঘণ্টা " + (m % 60) + " মিনিট"
}

fun durEn(ms: Long): String {
    val m = Math.round(ms / 60000.0)
    return if (m < 1) Math.round(ms / 1000.0).toString() + " s"
    else if (m < 60) "$m min"
    else (m / 60).toString() + " h " + (m % 60) + " min"
}

fun median(arr: List<Double>): Double {
    val s = arr.sorted()
    val n = s.size
    return if (n % 2 == 1) s[(n - 1) / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
}

// ---------------------------------------------------------------- alert evaluation (fixed thresholds)
fun evalVest(r: VestRow, th: Thresholds): List<AlertHit> {
    val a = mutableListOf<AlertHit>()
    if (r.fall) a.add(AlertHit("fall", "ফল ডিটেক্ট", "Fall detected"))
    if (r.sos) a.add(AlertHit("sos", "SOS সক্রিয়", "SOS active"))
    val t = r.temp
    if (t != null && (t > th.tempMax || t < th.tempMin)) {
        val hi = t > th.tempMax
        a.add(AlertHit("temp", (if (hi) "উচ্চ" else "নিম্ন") + " তাপমাত্রা " + fx(t, 1) + "°C", (if (hi) "High" else "Low") + " temperature " + fx(t, 1) + "°C"))
    }
    val h = r.hum
    if (h != null && (h > th.humMax || h < th.humMin)) {
        val hi = h > th.humMax
        a.add(AlertHit("humidity", (if (hi) "উচ্চ" else "নিম্ন") + " আর্দ্রতা " + fx(h, 1) + "%", (if (hi) "High" else "Low") + " humidity " + fx(h, 1) + "%"))
    }
    val p = r.pres
    if (p != null && (p > th.presMax || p < th.presMin)) {
        val hi = p > th.presMax
        a.add(AlertHit("pressure", (if (hi) "উচ্চ" else "নিম্ন") + " চাপ " + fx(p, 0) + " hPa", (if (hi) "High" else "Low") + " pressure " + fx(p, 0) + " hPa"))
    }
    return a
}

fun evalHelmet(r: HelmetRow, th: Thresholds): List<AlertHit> {
    val a = mutableListOf<AlertHit>()
    if (r.obstFlag) a.add(AlertHit("obstacle", "বাধা শনাক্ত", "Obstacle detected"))
    if (r.gasFlag) a.add(AlertHit("gas", "গ্যাস শনাক্ত (MQ-2 " + nstr(r.gas) + ")", "Gas detected (MQ-2 " + nstr(r.gas) + ")"))
    if (r.hr > 0 && (r.hr < th.hrMin || r.hr > th.hrMax)) {
        val lo = r.hr < th.hrMin
        a.add(AlertHit("hr", (if (lo) "কম" else "বেশি") + " হার্ট রেট " + nstr(r.hr) + " bpm", (if (lo) "Low" else "High") + " heart rate " + nstr(r.hr) + " bpm"))
    }
    if (r.spo2 > 0 && r.spo2 < th.spo2Min)
        a.add(AlertHit("spo2", "SpO₂ কম " + nstr(r.spo2) + "%", "Low SpO₂ " + nstr(r.spo2) + "%"))
    return a
}

// ---------------------------------------------------------------- incident grouping
data class Incident(val key: String, val start: Long, var end: Long, var snaps: Int, val first: AlertHit, var last: AlertHit)

/** Consecutive logs of the same alert (<= gapMs apart) count as ONE incident. */
fun <T> groupIncidents(rows: List<T>, tsOf: (T) -> Long, evalFn: (T) -> List<AlertHit>, gapMs: Long = GAP_MS): List<Incident> {
    val open = LinkedHashMap<String, Incident>()
    val done = mutableListOf<Incident>()
    rows.sortedBy(tsOf).forEach { r ->
        val ts = tsOf(r)
        val seen = HashSet<String>()
        evalFn(r).forEach { h ->
            seen.add(h.key)
            val cur = open[h.key]
            if (cur != null && ts - cur.end <= gapMs) {
                cur.end = ts; cur.snaps++; cur.last = h
            } else {
                if (cur != null) done.add(cur)
                open[h.key] = Incident(h.key, ts, ts, 1, h, h)
            }
        }
        val it = open.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!seen.contains(e.key) && ts - e.value.end > gapMs) { done.add(e.value); it.remove() }
        }
    }
    open.values.forEach { done.add(it) }
    return done.sortedBy { it.start }
}

fun vestIncidents(rows: List<VestRow>, th: Thresholds) = groupIncidents(rows, { it.ts }, { evalVest(it, th) })
fun helmetIncidents(rows: List<HelmetRow>, th: Thresholds) = groupIncidents(rows, { it.ts }, { evalHelmet(it, th) })

// ---------------------------------------------------------------- metrics config
class Metric(
    val key: String, val src: String, val bn: String, val en: String, val unit: String,
    val floor: Double, val jump: Double, val slope: Double,
    val hasLim: Boolean, val limLo: Double?, val limHi: Double?, val dec: Int
)

class Cfg(
    val BASE_WIN: Int = 60, val MIN_BASE: Int = 20, val Z_FLAG: Double = 4.0,
    val TREND_WIN: Int = 20, val TREND_R2: Double = 0.8, val FLAT_RUN: Int = 60,
    val LYING_MS: Long = 5 * 60 * 1000L
)

val CFG = Cfg()

/** Per-metric tuning. floor = noise floor, jump = smallest sudden step, slope = smallest sustained drift per minute. */
fun metricsFor(th: Thresholds): LinkedHashMap<String, Metric> {
    val m = LinkedHashMap<String, Metric>()
    m["temp"] = Metric("temp", "vest", "তাপমাত্রা", "Temperature", "°C", 0.3, 3.0, 0.25, true, th.tempMin, th.tempMax, 1)
    m["hum"] = Metric("hum", "vest", "আর্দ্রতা", "Humidity", "%", 1.5, 10.0, 1.5, true, th.humMin, th.humMax, 1)
    m["pres"] = Metric("pres", "vest", "বায়ুচাপ", "Pressure", " hPa", 0.4, 4.0, 0.5, true, th.presMin, th.presMax, 1)
    m["hr"] = Metric("hr", "helmet", "হার্ট রেট", "Heart rate", " bpm", 6.0, 25.0, 4.0, true, th.hrMin, th.hrMax, 0)
    m["spo2"] = Metric("spo2", "helmet", "SpO₂", "SpO₂", "%", 1.0, 4.0, 1.0, true, th.spo2Min, null, 0)
    m["gas"] = Metric("gas", "helmet", "গ্যাস লেভেল", "Gas level", "", 15.0, 100.0, 20.0, false, null, null, 0)
    return m
}

// ---------------------------------------------------------------- series + detection
class Pt(val ts: Long, val v: Double, val vest: VestRow?, val helmet: HelmetRow?)

fun buildSeries(key: String, m: Metric, vest: List<VestRow>, helmet: List<HelmetRow>): List<Pt> {
    val pts = mutableListOf<Pt>()
    if (m.src == "vest") {
        vest.forEach { r ->
            val v = when (key) { "temp" -> r.temp; "hum" -> r.hum; "pres" -> r.pres; else -> null }
            if (v != null && r.ts != 0L) pts.add(Pt(r.ts, v, r, null))
        }
    } else {
        helmet.forEach { r ->
            val v: Double? = when (key) {
                "hr" -> if (r.hr > 0) r.hr else null   // 0 = no finger on sensor
                "spo2" -> if (r.spo2 > 0) r.spo2 else null
                "gas" -> if (r.gas > 0) r.gas else null
                else -> null
            }
            if (v != null && r.ts != 0L) pts.add(Pt(r.ts, v, null, r))
        }
    }
    pts.sortBy { it.ts }
    return pts
}

fun breaksFixed(key: String, m: Metric, p: Pt): Boolean {
    if (key == "gas") return p.helmet?.gasFlag == true
    if (!m.hasLim) return false
    if (m.limLo != null && p.v < m.limLo) return true
    if (m.limHi != null && p.v > m.limHi) return true
    return false
}

class Regression(val slope: Double, val r2: Double)

fun regress(pts: List<Pt>, a: Int, b: Int): Regression? {
    val n = b - a
    if (n < 5) return null
    val t0 = pts[a].ts
    var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0; var syy = 0.0
    for (i in a until b) {
        val x = (pts[i].ts - t0) / 60000.0
        val y = pts[i].v
        sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y
    }
    val den = n * sxx - sx * sx
    if (den == 0.0) return null
    val slope = (n * sxy - sx * sy) / den
    val ssTot = syy - (sy * sy) / n
    val intercept = (sy - slope * sx) / n
    var ssRes = 0.0
    for (i in a until b) {
        val x = (pts[i].ts - t0) / 60000.0
        val e = pts[i].v - (intercept + slope * x)
        ssRes += e * e
    }
    val r2 = if (ssTot > 1e-9) 1 - ssRes / ssTot else 0.0
    return Regression(slope, r2)
}

private class Raw(val i: Int, val method: String, val dir: Int, val z: Double, val delta: Double? = null, val slope: Double? = null)

class Episode(
    val key: String, val startTs: Long, var endTs: Long, val startI: Int, var endI: Int,
    val methods: MutableSet<String>, var n: Int,
    var maxZ: Double, var maxJump: Double?, var slope: Double?, var dir: Int, var dirZ: Double
) {
    var peakV: Double = 0.0
    var caught: Boolean = false
    var sev: String = "low"
}

class TrendNow(val slope: Double, val r2: Double, val last: Double, val eta: Double?, val limit: Double?)

class MetricResult(
    val key: String, val points: List<Pt>, val flags: Array<MutableSet<String>?>,
    val episodes: List<Episode>, val trendNow: TrendNow?, val enough: Boolean
)

fun detectMetric(key: String, m: Metric, pts: List<Pt>): MetricResult {
    val flags = arrayOfNulls<MutableSet<String>>(pts.size)
    val raw = mutableListOf<Raw>()
    if (pts.size < CFG.MIN_BASE + 5) return MetricResult(key, pts, flags, emptyList(), null, false)

    val dts = mutableListOf<Double>()
    for (i in 1 until pts.size) dts.add((pts[i].ts - pts[i - 1].ts).toDouble())
    val typDt = median(dts).let { if (it == 0.0) 30000.0 else it }
    val maxStep = max(GAP_MS.toDouble(), typDt * 3)

    // (A) robust z-score vs previous BASE_WIN readings, (B) sudden jump
    for (i in CFG.MIN_BASE until pts.size) {
        val from = max(0, i - CFG.BASE_WIN)
        val base = ArrayList<Double>()
        for (j in from until i) base.add(pts[j].v)
        val med = median(base)
        val mad = median(base.map { abs(it - med) })
        val scale = max(1.4826 * mad, m.floor)
        val z = (pts[i].v - med) / scale
        if (abs(z) >= CFG.Z_FLAG && abs(pts[i].v - med) >= m.floor * 2) {
            raw.add(Raw(i, "z", if (z > 0) 1 else -1, z))
        }
        val step = pts[i].v - pts[i - 1].v
        if ((pts[i].ts - pts[i - 1].ts) <= maxStep && abs(step) >= m.jump) {
            raw.add(Raw(i, "jump", if (step > 0) 1 else -1, step / scale, delta = step))
        }
    }

    // (C) sustained trend: sliding window, OLS slope + R2
    val w = CFG.TREND_WIN
    var a = 0
    while (a + w <= pts.size) {
        val b = a + w
        if ((pts[b - 1].ts - pts[a].ts) <= w * maxStep) {
            val rg = regress(pts, a, b)
            if (rg != null && abs(rg.slope) >= m.slope && rg.r2 >= CFG.TREND_R2) {
                for (i in a until b) raw.add(Raw(i, "trend", if (rg.slope > 0) 1 else -1, 0.0, slope = rg.slope))
            }
        }
        a += floor(w / 2.0).toInt()
    }

    // trend of the most recent window (for "will cross the limit in N min")
    var trendNow: TrendNow? = null
    if (pts.size >= w) {
        val rg = regress(pts, pts.size - w, pts.size)
        if (rg != null) {
            val last = pts[pts.size - 1].v
            var eta: Double? = null
            var limit: Double? = null
            if (m.hasLim && abs(rg.slope) >= m.slope && rg.r2 >= CFG.TREND_R2) {
                if (rg.slope > 0 && m.limHi != null && last < m.limHi) { limit = m.limHi; eta = (limit - last) / rg.slope }
                if (rg.slope < 0 && m.limLo != null && last > m.limLo) { limit = m.limLo; eta = (limit - last) / rg.slope }
            }
            trendNow = TrendNow(rg.slope, rg.r2, last, if (eta != null && eta > 0 && eta <= 120) eta else null, limit)
        }
    }

    // (D) flat-line (stuck sensor) - only for float sensors that always carry noise
    if (key == "temp" || key == "hum" || key == "pres") {
        var runStart = 0
        for (i in 1..pts.size) {
            if (i == pts.size || pts[i].v != pts[runStart].v) {
                if (i - runStart >= CFG.FLAT_RUN) {
                    for (k in runStart until i) raw.add(Raw(k, "flat", 0, 0.0))
                }
                runStart = i
            }
        }
    }

    // flagged points -> episodes
    val sorted = raw.sortedBy { it.i } // stable, like JS Array.sort
    val episodes = mutableListOf<Episode>()
    sorted.forEach { f ->
        val t = pts[f.i].ts
        val last = episodes.lastOrNull()
        if (last != null && (t - last.endTs) <= 2 * max(typDt, 30000.0) + 60000) {
            last.endTs = max(last.endTs, t); last.endI = max(last.endI, f.i)
            last.methods.add(f.method); last.n++
            if (abs(f.z) > abs(last.maxZ)) last.maxZ = f.z
            if (f.delta != null && abs(f.delta) > abs(last.maxJump ?: 0.0)) last.maxJump = f.delta
            if (f.slope != null) last.slope = f.slope
            if (f.dir != 0 && abs(f.z) >= abs(last.dirZ)) { last.dir = f.dir; last.dirZ = f.z }
        } else {
            episodes.add(Episode(key, t, t, f.i, f.i, mutableSetOf(f.method), 1, f.z, f.delta, f.slope, f.dir, f.z))
        }
        val fl = flags[f.i] ?: mutableSetOf<String>().also { flags[f.i] = it }
        fl.add(f.method)
    }

    episodes.forEach { e ->
        var peak: Pt? = null
        val ref = pts[max(0, e.startI - 1)].v
        for (i in e.startI..e.endI) {
            if (peak == null || abs(pts[i].v - ref) > abs(peak.v - ref)) peak = pts[i]
        }
        e.peakV = peak?.v ?: pts[e.startI].v
        e.caught = false
        for (i in e.startI..e.endI) if (breaksFixed(key, m, pts[i])) { e.caught = true; break }
        e.sev = severityOf(e, m)
    }
    return MetricResult(key, pts, flags, episodes, trendNow, true)
}

fun severityOf(e: Episode, m: Metric): String {
    val az = abs(e.maxZ)
    if ("jump" in e.methods && abs(e.maxJump ?: 0.0) >= m.jump * 2) return "high"
    if (az >= 8) return "high"
    if ("z" in e.methods && "trend" in e.methods) return "high"
    if ("jump" in e.methods || az >= 5.5 || "trend" in e.methods) return "medium"
    return "low" // z in [4, 5.5) or flat-line
}

class Gap(val from: Long, val to: Long, val ms: Long)

fun findGaps(tsList: List<Long>): List<Gap> {
    val gaps = mutableListOf<Gap>()
    for (i in 1 until tsList.size) {
        val d = tsList[i] - tsList[i - 1]
        if (d > GAP_MS * 2) gaps.add(Gap(tsList[i - 1], tsList[i], d))
    }
    return gaps
}

class Lying(val from: Long, val to: Long, val ms: Long, val fall: Boolean)

fun findLying(vestRows: List<VestRow>): List<Lying> {
    val out = mutableListOf<Lying>()
    var start: Long? = null
    var prev = 0L
    var fallInside = false
    fun close(end: Long) {
        val s = start
        if (s != null && end - s >= CFG.LYING_MS) out.add(Lying(s, end, end - s, fallInside))
        start = null; fallInside = false
    }
    vestRows.forEach { r ->
        if (r.motion == "LYING" || r.motion == "FALL") {
            if (start == null) start = r.ts
            else if (r.ts - prev > GAP_MS * 2) { close(prev); start = r.ts }
            if (r.motion == "FALL" || r.fall) fallInside = true
            prev = r.ts
        } else if (start != null) {
            close(prev)
        }
    }
    if (start != null) close(prev)
    return out
}

// ---------------------------------------------------------------- findings / analyze
class Finding(
    val kind: String, val key: String, val sev: String,
    val startTs: Long, val endTs: Long, val caught: Boolean,
    val tag: String, val bn: String, val en: String
)

class AnalysisCounts(val vest: Int, val helmet: Int, val anomalies: Int, val missed: Int, val gaps: Int)

class AnalysisResult(
    val verdict: String,
    val findings: List<Finding>,
    val perMetric: Map<String, MetricResult>,
    val metrics: Map<String, Metric>,
    val counts: AnalysisCounts,
    val from: Long, val to: Long
)

fun describeEpisode(e: Episode, m: Metric): Finding {
    fun fmt(v: Double) = fx(v, m.dec) + m.unit
    val parts = mutableListOf<String>()
    val partsEn = mutableListOf<String>()
    if ("jump" in e.methods) {
        val mj = e.maxJump ?: 0.0
        parts.add("হঠাৎ লাফ " + (if (mj > 0) "+" else "") + fx(mj, m.dec) + m.unit)
        partsEn.add("sudden jump " + (if (mj > 0) "+" else "") + fx(mj, m.dec) + m.unit)
    }
    if ("z" in e.methods) {
        parts.add("সাম্প্রতিক স্বাভাবিক মান থেকে " + fx(abs(e.maxZ), 1) + "σ দূরে")
        partsEn.add(fx(abs(e.maxZ), 1) + "σ from recent normal")
    }
    if ("trend" in e.methods) {
        val sl = e.slope ?: 0.0
        parts.add("টানা " + (if (sl > 0) "বাড়ছে" else "কমছে") + " (" + fx(abs(sl), 2) + m.unit + "/মিনিট)")
        partsEn.add("sustained " + (if (sl > 0) "rise" else "fall") + " (" + fx(abs(sl), 2) + m.unit + "/min)")
    }
    if ("flat" in e.methods) {
        parts.add("মান একদম একই থেকে গেছে — সেন্সর আটকে যেতে পারে")
        partsEn.add("value frozen — sensor may be stuck")
    }
    val tag = if ("flat" in e.methods) "FLATLINE" else if ("jump" in e.methods) "JUMP" else if ("trend" in e.methods) "TREND" else "DEVIATION"
    return Finding(
        "episode", e.key, e.sev, e.startTs, e.endTs, e.caught, tag,
        m.bn + ": " + parts.joinToString(" · ") + " (সর্বোচ্চ/সর্বনিম্ন মান " + fmt(e.peakV) + ")",
        m.en + ": " + partsEn.joinToString(" · ") + " (peak " + fmt(e.peakV) + ")"
    )
}

fun describeForecast(key: String, m: Metric, t: TrendNow, lastTs: Long): Finding {
    val eta = t.eta ?: 0.0
    val min = max(1L, Math.round(eta))
    val limit = nstr(t.limit ?: 0.0)
    return Finding(
        "forecast", key, if (eta <= 15) "high" else "medium", lastTs, lastTs, false, "FORECAST",
        m.bn + " এই হারে " + (if (t.slope > 0) "বাড়লে" else "কমলে") + " প্রায় " + min + " মিনিটে নিরাপদ সীমা (" + limit + m.unit + ") ছুঁয়ে ফেলবে — এখনো সীমার ভেতরে",
        m.en + " will reach the safe limit (" + limit + m.unit + ") in about " + min + " min at the current rate — still inside the limit"
    )
}

fun analyze(vestIn: List<VestRow>, helmetIn: List<HelmetRow>, th: Thresholds = Thresholds()): AnalysisResult {
    val vest = vestIn.filter { it.ts != 0L }.sortedBy { it.ts }
    val helmet = helmetIn.filter { it.ts != 0L }.sortedBy { it.ts }
    val metrics = metricsFor(th)

    val perMetric = LinkedHashMap<String, MetricResult>()
    val findings = mutableListOf<Finding>()
    metrics.forEach { (k, m) ->
        val pts = buildSeries(k, m, vest, helmet)
        val res = detectMetric(k, m, pts)
        perMetric[k] = res
        res.episodes.forEach { findings.add(describeEpisode(it, m)) }
        val tn = res.trendNow
        if (tn != null && tn.eta != null && tn.eta <= 60) findings.add(describeForecast(k, m, tn, pts[pts.size - 1].ts))
    }

    // cross-signal: temperature AND heart rate drifting up together (possible heat stress)
    fun rising(e: Episode): Boolean {
        val up = if ("trend" in e.methods) (e.slope ?: 0.0) > 0 else e.dir > 0
        return up && ("trend" in e.methods || "z" in e.methods)
    }
    val tEp = perMetric["temp"]?.episodes?.filter { rising(it) } ?: emptyList()
    val hEp = perMetric["hr"]?.episodes?.filter { rising(it) } ?: emptyList()
    tEp.forEach { t ->
        hEp.forEach { h ->
            if (t.startTs <= h.endTs + 5 * 60000 && h.startTs <= t.endTs + 5 * 60000) {
                findings.add(Finding(
                    "cross", "temp+hr", "high", minOf(t.startTs, h.startTs), maxOf(t.endTs, h.endTs), false, "CROSS",
                    "তাপমাত্রা ও হার্ট রেট একসাথে বাড়ছে — হিট-স্ট্রেসের সম্ভাব্য প্যাটার্ন (যাচাই করুন)",
                    "Temperature and heart rate are rising together — possible heat-stress pattern (verify)"
                ))
            }
        }
    }

    // prolonged lying
    findLying(vest).forEach { l ->
        findings.add(Finding(
            "lying", "motion", if (l.fall) "high" else "medium", l.from, l.to, l.fall, "INACTIVE",
            "ওয়ার্কার " + durBn(l.ms) + " ধরে শুয়ে/নিশ্চল অবস্থায়" + (if (l.fall) " (ফল সহ)" else ""),
            "Worker lying still for " + durEn(l.ms) + (if (l.fall) " (with fall)" else "")
        ))
    }

    // data gaps
    val vGaps = findGaps(vest.map { it.ts })
    val hGaps = findGaps(helmet.map { it.ts })
    listOf(Triple("ভেস্ট", "Vest", vGaps), Triple("হেলমেট", "Helmet", hGaps)).forEach { g ->
        if (g.third.isEmpty()) return@forEach
        var longest = g.third[0]
        g.third.forEach { if (it.ms > longest.ms) longest = it }
        findings.add(Finding(
            "gap", "gap", if (longest.ms >= 5 * 60000) "medium" else "low", longest.from, longest.to, false, "DATA GAP",
            g.first + " ডেটায় " + g.third.size + "টি বিরতি; সবচেয়ে বড়টি " + durBn(longest.ms),
            g.second + " data has " + g.third.size + " gap(s); longest " + durEn(longest.ms)
        ))
    }

    val order = mapOf("high" to 0, "medium" to 1, "low" to 2)
    val sortedF = findings.sortedWith(compareBy<Finding> { order[it.sev] ?: 3 }.thenByDescending { it.endTs })

    val real = sortedF.filter { it.kind != "gap" }
    val missed = real.count { !it.caught && it.sev != "low" }
    val verdict = if (sortedF.any { it.sev == "high" }) "high" else if (sortedF.any { it.sev == "medium" }) "medium" else "normal"

    val fromV = if (vest.isNotEmpty()) vest.first().ts else Long.MAX_VALUE
    val fromH = if (helmet.isNotEmpty()) helmet.first().ts else Long.MAX_VALUE
    val toV = if (vest.isNotEmpty()) vest.last().ts else 0L
    val toH = if (helmet.isNotEmpty()) helmet.last().ts else 0L

    return AnalysisResult(
        verdict, sortedF, perMetric, metrics,
        AnalysisCounts(vest.size, helmet.size, real.size, missed, vGaps.size + hGaps.size),
        minOf(fromV, fromH), maxOf(toV, toH)
    )
}
