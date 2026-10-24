package com.example.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * "Ask SafeGuard" answer layer - port of the handlers in the website's ai-ui.js.
 * Pure Kotlin: all data comes through [AiDataSource], so it can be tested with a
 * fake source on the JVM and wired to Firebase in the app (see FirebaseAiDataSource).
 * Every number in an answer is computed from real data (no hallucination).
 *
 * Text markup: **bold**. UI renders it; plain-text consumers can strip it.
 */

data class LiveSnapshot(
    val vest: VestLive?, val helmet: HelmetRow?,
    val vestOnline: Boolean, val helmetOnline: Boolean,
    /** True while the app's demo/simulation mode is feeding fake live values. */
    val simulated: Boolean = false
)

class Rows(val vest: List<VestRow>, val helmet: List<HelmetRow>, val vestOk: Boolean, val helmetOk: Boolean)

interface AiDataSource {
    fun thresholds(): Thresholds
    suspend fun live(): LiveSnapshot
    /** Logs for [scope]. Throws only if BOTH vest and helmet logs failed. */
    suspend fun rows(scope: Scope): Rows
}

data class ChatLine(val text: String, val small: Boolean = false)
data class ChatAction(val kind: String, val label: String, val hours: Int = 0, val url: String = "") {
    companion object { const val OPEN_ANALYZE = "open-analyze"; const val OPEN_MAPS = "open-maps" }
}
data class ChatReply(val lines: List<ChatLine>, val actions: List<ChatAction> = emptyList())

class AskEngine(private val ds: AiDataSource, private val bn: Boolean) {

    private fun t(b: String, e: String) = if (bn) b else e
    private val th get() = ds.thresholds()
    private val metrics get() = metricsFor(th)

    private fun fmtTime(ts: Long) = SimpleDateFormat("h:mm a", Locale.US).format(Date(ts))
    private fun fmtDT(ts: Long) = SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(ts))
    private fun scopeTxt(s: Scope) = if (bn) s.bn else s.en

    private val alertName = mapOf(
        "fall" to ("ফল" to "Fall"), "sos" to ("SOS" to "SOS"), "temp" to ("তাপমাত্রা" to "Temperature"),
        "humidity" to ("আর্দ্রতা" to "Humidity"), "pressure" to ("বায়ুচাপ" to "Pressure"), "obstacle" to ("বাধা" to "Obstacle"),
        "gas" to ("গ্যাস" to "Gas"), "hr" to ("হার্ট রেট" to "Heart rate"), "spo2" to ("SpO₂" to "SpO₂")
    )
    private fun aName(k: String): String { val n = alertName[k] ?: (k to k); return if (bn) n.first else n.second }
    private fun mLabel(k: String): String { val m = metrics[k]!!; return if (bn) m.bn else m.en }
    private fun fmtVal(k: String, v: Double): String { val m = metrics[k]!!; return fx(v, m.dec) + m.unit }
    private fun inRange(k: String, v: Double): Boolean {
        val m = metrics[k]!!
        if (m.limLo != null && v < m.limLo) return false
        if (m.limHi != null && v > m.limHi) return false
        return true
    }
    private fun rangeText(k: String): String {
        val m = metrics[k]!!
        if (!m.hasLim) return ""
        val lo = m.limLo; val hi = m.limHi
        return if (lo != null && hi != null) nstr(lo) + "–" + nstr(hi) + m.unit
        else if (lo != null) "≥ " + nstr(lo) + m.unit else "≤ " + nstr(hi!!) + m.unit
    }
    private fun L(text: String) = ChatLine(text)
    /** Safety: never let the user mistake demo values for real ones. */
    private fun simNote(live: LiveSnapshot?): List<ChatLine> =
        if (live != null && live.simulated)
            listOf(L(t("⚠️ **সিমুলেশন মোড চালু** — লাইভ মানগুলো আসল নয় (ডেমো ডেটা)।", "⚠️ **Simulation mode is ON** — live values are demo data, not real.")))
        else emptyList()
    private fun S(text: String) = ChatLine(text, small = true)

    private fun liveValue(k: String, live: LiveSnapshot?): Double? {
        if (live == null) return null
        if (k == "temp") return live.vest?.temp
        if (k == "hum") return live.vest?.hum
        if (k == "pres") return live.vest?.pres
        val h = live.helmet ?: return null
        return when (k) {
            "hr" -> if (h.hr > 0) h.hr else null
            "spo2" -> if (h.spo2 > 0) h.spo2 else null
            "gas" -> if (h.gas > 0) h.gas else null
            else -> null
        }
    }
    private fun isOnlineFor(k: String, live: LiveSnapshot?) =
        if (live == null) false else if (metrics[k]!!.src == "vest") live.vestOnline else live.helmetOnline

    private fun liveAlerts(live: LiveSnapshot?): List<AlertHit> {
        val out = mutableListOf<AlertHit>()
        if (live == null) return out
        val v = live.vest
        if (live.vestOnline && v != null)
            out.addAll(evalVest(VestRow(0, v.temp, v.hum, v.pres, v.fall, v.sos, v.motion, v.lat, v.lng), th))
        val h = live.helmet
        if (live.helmetOnline && h != null) out.addAll(evalHelmet(h, th))
        return out
    }

    private class Stat(val n: Int, val min: Pt, val max: Pt, val avg: Double)
    private fun statsOf(k: String, rows: Rows): Stat? {
        val pts = buildSeries(k, metrics[k]!!, rows.vest, rows.helmet)
        if (pts.isEmpty()) return null
        var mn = pts[0]; var mx = pts[0]; var sum = 0.0
        pts.forEach { if (it.v < mn.v) mn = it; if (it.v > mx.v) mx = it; sum += it.v }
        return Stat(pts.size, mn, mx, sum / pts.size)
    }
    private fun lastSeen(ts: List<Long>) = if (ts.isEmpty()) null else fmtTime(ts.last())

    // ------------------------------------------------------------ router
    suspend fun answer(question: String): ChatReply {
        val it = detectIntent(question)
        return when (it.intent) {
            "greet" -> hGreet()
            "help" -> hHelp()
            "alertCount" -> hAlertCount(it)
            "lastAlert" -> hLastAlert(it)
            "activeAlerts" -> hActiveAlerts()
            "status" -> hStatus()
            "sensor" -> hSensor(it)
            "location" -> hLocation()
            "summary" -> hSummary(it)
            "anomaly" -> hAnomaly(it)
            else -> hUnknown()
        }
    }

    fun welcome() = ChatReply(listOf(
        L(t("হ্যালো! আমি **SafeGuard** 👋", "Hello! I'm **SafeGuard** 👋")),
        S(t("লাইভ সেন্সর ডেটা ও লগ দেখে উত্তর দিই। নিচের প্রশ্নগুলো চেপে দেখুন, অথবা নিজের মতো লিখুন (বাংলা/English)।",
            "I answer from the live sensor data and logs. Tap a question below or type your own (Bangla/English)."))
    ))

    private fun hGreet() = ChatReply(listOf(
        L(t("হ্যালো! আমি **SafeGuard**। লাইভ সেন্সর ডেটা আর লগ দেখে আপনার প্রশ্নের উত্তর দিই।", "Hello! I'm **SafeGuard**. I answer from the live sensor data and logs.")),
        S(t("যেমন: \"আজকে কয়টা অ্যালার্ট হয়েছে?\" বা \"ওয়ার্কারের অবস্থা এখন কেমন?\"", "e.g. \"How many alerts today?\" or \"What is the worker status now?\""))
    ))

    private fun hHelp() = ChatReply(listOf(
        L(t("**আমি যা যা বলতে পারি:**", "**What I can answer:**")),
        L(t("• আজ/গত N ঘণ্টায় কয়টা অ্যালার্ট হয়েছে", "• How many alerts today / in the last N hours")),
        L(t("• ওয়ার্কারের বর্তমান অবস্থা (ভেস্ট + হেলমেট)", "• Current worker status (vest + helmet)")),
        L(t("• তাপমাত্রা, আর্দ্রতা, বায়ুচাপ, হার্ট রেট, SpO₂, গ্যাস — এখনকার মান ও গড়/সর্বোচ্চ/সর্বনিম্ন", "• Temperature, humidity, pressure, heart rate, SpO₂, gas — now and avg/max/min")),
        L(t("• সর্বশেষ অ্যালার্ট কখন হয়েছিল", "• When the last alert happened")),
        L(t("• ওয়ার্কার এখন কোথায় (GPS)", "• Where the worker is (GPS)")),
        L(t("• অস্বাভাবিক কোনো প্যাটার্ন আছে কি না (AI অ্যানোমালি ডিটেকশন)", "• Any unusual pattern (AI anomaly detection)")),
        L(t("• আজকের সামারি", "• Today's summary"))
    ))

    private fun hUnknown() = ChatReply(listOf(
        L(t("দুঃখিত, প্রশ্নটা পুরোপুরি বুঝতে পারিনি। এগুলো জিজ্ঞেস করে দেখুন:", "Sorry, I didn't quite get that. Try one of these:")),
        L(t("• আজকে কয়টা অ্যালার্ট হয়েছে?", "• How many alerts today?")),
        L(t("• ওয়ার্কারের অবস্থা এখন কেমন?", "• What is the worker status now?")),
        L(t("• তাপমাত্রা কত?", "• What is the temperature?")),
        L(t("• অস্বাভাবিক কিছু আছে?", "• Is anything unusual?"))
    ))

    // ------------------------------------------------------------ alerts
    private suspend fun hAlertCount(it: Intent): ChatReply {
        val rows = ds.rows(it.scope)
        val filter = it.filter.takeIf { f -> f.isNotEmpty() }
        val keep = { inc: Incident -> filter == null || filter.contains(inc.key) }
        val vInc = vestIncidents(rows.vest, th).filter(keep)
        val hInc = helmetIncidents(rows.helmet, th).filter(keep)
        val all = (vInc + hInc).sortedBy { x -> x.start }
        val snaps = all.sumOf { x -> x.snaps }
        val total = rows.vest.size + rows.helmet.size
        val scopeName = scopeTxt(it.scope)
        val what = if (filter != null) filter.joinToString("/") { k -> aName(k) } + " " else ""
        val out = mutableListOf<ChatLine>()
        val actions = mutableListOf<ChatAction>()

        if (total == 0) {
            out.add(L(t("⚠️ $scopeName কোনো লগ ডেটা পাওয়া যায়নি, তাই অ্যালার্ট গণনা করা যায়নি (ডিভাইস অফলাইন ছিল?)।",
                "⚠️ No log data found $scopeName, so alerts cannot be counted (device offline?).")))
        } else if (all.isEmpty()) {
            out.add(L(t("✅ $scopeName কোনো ${what}অ্যালার্ট রেকর্ড হয়নি।", "✅ No ${what}alerts recorded $scopeName.")))
        } else {
            out.add(L(t("$scopeName মোট **${all.size}টি** ${what}অ্যালার্ট ঘটনা রেকর্ড হয়েছে (ভেস্ট ${vInc.size}, হেলমেট ${hInc.size})।",
                "**${all.size}** ${what}alert incident(s) recorded $scopeName (vest ${vInc.size}, helmet ${hInc.size}).")))
            val by = LinkedHashMap<String, Int>()
            all.forEach { x -> by[x.key] = (by[x.key] ?: 0) + 1 }
            out.add(L(by.entries.joinToString(" · ") { e -> aName(e.key) + " ×" + e.value }))
            val last = all.last()
            out.add(L(t("সর্বশেষ: ${fmtTime(last.end)} — ${last.last.bn}", "Latest: ${fmtTime(last.end)} — ${last.last.en}")))
        }
        if (filter == null && (it.scope.kind == "today" || it.scope.kind == "hours")) {
            try {
                val lv = ds.live()
                val now = liveAlerts(lv)
                if (now.isNotEmpty()) out.add(L("🔴 " + t("এখন সক্রিয়: ", "Active now: ") + now.joinToString(", ") { a -> if (bn) a.bn else a.en }))
                out.addAll(simNote(lv))
            } catch (_: Exception) { /* live part is optional */ }
        }
        if (all.isNotEmpty()) out.add(S(t("একই ঘটনার টানা ~৩০ সেকেন্ডের লগ একসাথে ১টি ধরা হয়েছে (মোট ${snaps}টি লগ-স্ন্যাপশট)।",
            "Consecutive ~30 s logs of the same event count as one incident ($snaps log snapshots in total).")))
        if (it.scope.capped) out.add(S(t("সর্বোচ্চ ৭ দিনের ডেটা দেখা হয়।", "At most 7 days of data are scanned.")))
        return ChatReply(out, actions)
    }

    private suspend fun hLastAlert(it: Intent): ChatReply {
        val scope = if (it.scope.kind == "today")
            Scope(System.currentTimeMillis() - 7 * 86_400_000L, System.currentTimeMillis(), "গত ৭ দিনে", "গত ৭ দিনের", "in the last 7 days", "week")
        else it.scope
        val rows = ds.rows(scope)
        val filter = it.filter.takeIf { f -> f.isNotEmpty() }
        val all = (vestIncidents(rows.vest, th) + helmetIncidents(rows.helmet, th))
            .filter { x -> filter == null || filter.contains(x.key) }.sortedBy { x -> x.start }
        if (all.isEmpty()) return ChatReply(listOf(L(t("✅ ${scopeTxt(scope)} কোনো অ্যালার্ট পাওয়া যায়নি।", "✅ No alerts found ${scopeTxt(scope)}."))))
        val last = all.last()
        val ago = System.currentTimeMillis() - last.end
        val range = last.end - last.start > 60000
        return ChatReply(listOf(
            L(t("সর্বশেষ অ্যালার্ট: **${aName(last.key)}** — ${fmtDT(last.start)}" + (if (range) " থেকে ${fmtTime(last.end)}" else "") + " (${durBn(ago)} আগে)",
                "Latest alert: **${aName(last.key)}** — ${fmtDT(last.start)}" + (if (range) " to ${fmtTime(last.end)}" else "") + " (${durEn(ago)} ago)")),
            L(if (bn) last.last.bn else last.last.en),
            S(t("${scopeTxt(scope)} মোট ${all.size}টি ঘটনা।", "${all.size} incident(s) ${scopeTxt(scope)}."))
        ))
    }

    private suspend fun hActiveAlerts(): ChatReply {
        val live = ds.live()
        if (!live.vestOnline && !live.helmetOnline)
            return ChatReply(listOf(L(t("⚪ ভেস্ট ও হেলমেট দুটোই এখন অফলাইন, তাই লাইভ অ্যালার্ট বলা যাচ্ছে না।", "⚪ Both vest and helmet are offline, so there are no live alerts to report."))))
        val now = liveAlerts(live)
        if (now.isEmpty()) {
            val out = mutableListOf(L(t("✅ এখন কোনো সক্রিয় অ্যালার্ট নেই।", "✅ No active alerts right now.")))
            out.addAll(simNote(live))
            if (!(live.vestOnline && live.helmetOnline))
                out.add(S(t("(" + (if (live.vestOnline) "হেলমেট" else "ভেস্ট") + " অফলাইন)", "(" + (if (live.vestOnline) "helmet" else "vest") + " offline)")))
            return ChatReply(out)
        }
        return ChatReply(listOf(L("🔴 " + t("এখন **${now.size}টি** সক্রিয় অ্যালার্ট:", "**${now.size}** active alert(s) now:"))) +
            now.map { a -> L("• " + if (bn) a.bn else a.en) } + simNote(live))
    }

    // ------------------------------------------------------------ status
    private suspend fun hStatus(): ChatReply {
        val live = ds.live()
        val rows = try { ds.rows(parseScope("today")) } catch (_: Exception) { null }
        val out = mutableListOf<ChatLine>()
        out.addAll(simNote(live))
        fun dev(name: String, on: Boolean, last: String?) =
            if (on) "🟢 $name: " + t("অনলাইন", "online")
            else "🔴 $name: " + t("অফলাইন", "offline") + (if (last != null) " (" + t("সর্বশেষ ডেটা ", "last data ") + last + ")" else "")
        out.add(L(dev(t("ভেস্ট", "Vest"), live.vestOnline, rows?.let { r -> lastSeen(r.vest.map { x -> x.ts }) })))
        out.add(L(dev(t("হেলমেট", "Helmet"), live.helmetOnline, rows?.let { r -> lastSeen(r.helmet.map { x -> x.ts }) })))

        val v = live.vest; val h = live.helmet
        if (live.vestOnline && v != null) {
            val env = mutableListOf<String>()
            v.temp?.let { x -> env.add(fmtVal("temp", x)) }
            v.hum?.let { x -> env.add(fmtVal("hum", x)) }
            v.pres?.let { x -> env.add(fmtVal("pres", x)) }
            out.add(L("📟 " + env.joinToString(" · ")))
            if (v.motion.isNotEmpty()) out.add(L("🚶 " + t("গতি: ", "Motion: ") + v.motion + (if (v.fall) " — ⚠️ FALL" else "") + (if (v.sos) " — 🆘 SOS" else "")))
        }
        if (live.helmetOnline && h != null) {
            val parts = mutableListOf<String>()
            if (h.hr > 0) parts.add("❤️ " + nstr(h.hr) + " bpm")
            if (h.spo2 > 0) parts.add("SpO₂ " + nstr(h.spo2) + "%")
            if (h.gas > 0) parts.add(t("গ্যাস ", "gas ") + nstr(h.gas) + (if (h.gasFlag) " ⚠️" else ""))
            out.add(L("⛑️ " + if (parts.isNotEmpty()) parts.joinToString(" · ") else t("হার্ট রেট/SpO₂ এখনো পড়া যায়নি (সেন্সরে আঙুল নেই?)", "No heart-rate / SpO₂ reading yet (finger off the sensor?)")))
        }
        val act = liveAlerts(live)
        val verdict = when {
            !live.vestOnline && !live.helmetOnline -> "⚪ " + t("**তথ্য নেই** — দুটো ডিভাইসই অফলাইন", "**No data** — both devices offline")
            act.any { a -> a.key == "fall" || a.key == "sos" } -> "🔴 " + t("**জরুরি** — ফল/SOS সক্রিয়!", "**EMERGENCY** — fall/SOS active!")
            act.isNotEmpty() -> "🟠 " + t("**সতর্কতা** — " + act.joinToString(", ") { a -> a.bn }, "**Warning** — " + act.joinToString(", ") { a -> a.en })
            !live.vestOnline || !live.helmetOnline -> "🟡 " + t("**আংশিক** — কোনো অ্যালার্ট নেই, তবে একটি ডিভাইস অফলাইন", "**Partial** — no alerts, but one device is offline")
            else -> "🟢 " + t("**স্বাভাবিক** — কোনো সক্রিয় অ্যালার্ট নেই", "**Normal** — no active alerts")
        }
        out.add(L("")); out.add(L(verdict))
        return ChatReply(out)
    }

    // ------------------------------------------------------------ sensors
    private suspend fun hSensor(it: Intent): ChatReply {
        val wantStats = it.stats || it.hasScopeWord
        val live = try { ds.live() } catch (_: Exception) { null }
        val rows = try { ds.rows(it.scope) } catch (_: Exception) { null }
        val out = mutableListOf<ChatLine>()
        out.addAll(simNote(live))
        val devName = { k: String -> if (metrics[k]!!.src == "vest") t("ভেস্ট", "vest") else t("হেলমেট", "helmet") }

        it.sensors.forEach { k ->
            if (metrics.containsKey(k)) {
                val cur = liveValue(k, live); val online = isOnlineFor(k, live)
                if (!wantStats || it.scope.kind == "today") {
                    if (cur == null) {
                        out.add(L("• **" + mLabel(k) + "**: " + t("এখন কোনো মান নেই", "no current reading") + (if (online) "" else " (" + devName(k) + t(" অফলাইন", " offline") + ")")))
                    } else {
                        val ok = inRange(k, cur) && !(k == "gas" && live?.helmet?.gasFlag == true)
                        out.add(L("• **" + mLabel(k) + "**: **" + fmtVal(k, cur) + "** " + (if (ok) "✅" else "⚠️") +
                            (if (metrics[k]!!.hasLim) " (" + t("নিরাপদ সীমা ", "safe ") + rangeText(k) + ")" else "") +
                            (if (online) "" else " " + t("(সর্বশেষ জানা মান, ডিভাইস অফলাইন)", "(last known value, device offline)"))))
                    }
                }
                if (rows != null && (wantStats || it.scope.kind == "today")) {
                    val st = statsOf(k, rows)
                    if (st != null) out.add(S("   " + scopeTxt(it.scope) + ": " + t("গড় ", "avg ") + fmtVal(k, st.avg) + " · " + t("সর্বোচ্চ ", "max ") + fmtVal(k, st.max.v) + " (" + fmtTime(st.max.ts) + ") · " + t("সর্বনিম্ন ", "min ") + fmtVal(k, st.min.v) + " (" + fmtTime(st.min.ts) + ")"))
                    else if (wantStats) out.add(L("• " + mLabel(k) + ": " + t(scopeTxt(it.scope) + " কোনো লগ নেই", "no logs " + scopeTxt(it.scope))))
                }
            } else if (k == "obstacle") {
                val h = if (live != null && live.helmetOnline) live.helmet else null
                if (h != null) {
                    fun d(x: Double?) = if (x == null || x >= NO_ECHO) "—" else nstr(x) + " cm"
                    out.add(L("• **" + t("বাধা/দূরত্ব", "Obstacle / distance") + "**: " + t("বাম ", "L ") + d(h.distL) + " · " + t("সামনে ", "F ") + d(h.distF) + " · " + t("ডান ", "R ") + d(h.distR) + (if (h.obstFlag) " ⚠️ " + t("বাধা শনাক্ত", "obstacle detected") else " ✅")))
                } else out.add(L("• **" + t("বাধা/দূরত্ব", "Obstacle") + "**: " + t("হেলমেট অফলাইন", "helmet offline")))
                if (rows != null) {
                    val n = helmetIncidents(rows.helmet, th).count { x -> x.key == "obstacle" }
                    out.add(S("   " + scopeTxt(it.scope) + ": " + n + t("বার বাধা শনাক্ত", " obstacle event(s)")))
                }
            } else if (k == "motion") {
                val v = if (live != null && live.vestOnline) live.vest else null
                if (v != null) out.add(L("• **" + t("গতি", "Motion") + "**: " + (if (v.motion.isEmpty()) "—" else v.motion) + (if (v.fall) " ⚠️ FALL" else " ✅") + (if (v.sos) " 🆘 SOS" else "")))
                else out.add(L("• **" + t("গতি", "Motion") + "**: " + t("ভেস্ট অফলাইন", "vest offline")))
                if (rows != null) {
                    val inc = vestIncidents(rows.vest, th)
                    val f = inc.count { x -> x.key == "fall" }; val s = inc.count { x -> x.key == "sos" }
                    out.add(S("   " + scopeTxt(it.scope) + ": " + t("ফল $f বার, SOS $s বার", "$f fall(s), $s SOS")))
                }
            } else if (k == "signal") {
                val r = if (live != null && live.vestOnline) live.vest?.rssi else null
                if (r == null) out.add(L("• **" + t("LoRa সিগন্যাল", "LoRa signal") + "**: " + t("এখন কোনো মান নেই", "no current value")))
                else out.add(L("• **" + t("LoRa সিগন্যাল", "LoRa signal") + "**: **" + nstr(r) + " dBm** (" + (if (r > -100) t("ভালো", "good") else if (r > -115) t("মাঝারি", "fair") else t("দুর্বল", "weak")) + ")"))
            }
        }
        if (it.scope.capped) out.add(S(t("সর্বোচ্চ ৭ দিনের ডেটা দেখা হয়।", "At most 7 days of data are scanned.")))
        return ChatReply(out)
    }

    // ------------------------------------------------------------ location
    private suspend fun hLocation(): ChatReply {
        val live = ds.live()
        val v = live.vest
        if (v == null || v.lat == null || v.lng == null || (v.lat == 0.0 && v.lng == 0.0))
            return ChatReply(listOf(L(t("📍 এখন কোনো GPS ফিক্স নেই (খোলা আকাশের নিচে নিলে ফিক্স পেতে সময় লাগে)।", "📍 No GPS fix right now (it can take a while to get a fix outdoors)."))))
        val out = mutableListOf(L("📍 " + t("ওয়ার্কারের অবস্থান: ", "Worker location: ") + "**" + fx(v.lat, 5) + ", " + fx(v.lng, 5) + "**"))
        if (!live.vestOnline) out.add(S(t("(ভেস্ট অফলাইন — এটি সর্বশেষ জানা অবস্থান)", "(vest offline — last known position)")))
        out.addAll(simNote(live))
        return ChatReply(out, listOf(ChatAction(ChatAction.OPEN_MAPS, t("গুগল ম্যাপে দেখুন", "Open in Google Maps"), url = "https://www.google.com/maps?q=" + v.lat + "," + v.lng)))
    }

    // ------------------------------------------------------------ summary + anomaly
    private suspend fun hSummary(it: Intent): ChatReply {
        val rows = ds.rows(it.scope)
        val out = mutableListOf(L(t("**" + (if (it.scope.poss.isNotEmpty()) it.scope.poss else it.scope.bn) + " সামারি**", "**Summary — " + scopeTxt(it.scope) + "**")))
        val actions = mutableListOf<ChatAction>()
        out.add(L(t("লগ: ভেস্ট ${rows.vest.size}, হেলমেট ${rows.helmet.size}", "Logs: vest ${rows.vest.size}, helmet ${rows.helmet.size}")))
        listOf("temp", "hum", "pres", "hr", "spo2").forEach { k ->
            val st = statsOf(k, rows)
            if (st != null) out.add(L("• " + mLabel(k) + ": " + t("গড় ", "avg ") + fmtVal(k, st.avg) + " (" + fmtVal(k, st.min.v) + " – " + fmtVal(k, st.max.v) + ")"))
        }
        val inc = vestIncidents(rows.vest, th) + helmetIncidents(rows.helmet, th)
        out.add(L(t("অ্যালার্ট ঘটনা: **${inc.size}**", "Alert incidents: **${inc.size}**")))
        val gaps = findGaps(rows.vest.map { x -> x.ts }).size + findGaps(rows.helmet.map { x -> x.ts }).size
        if (gaps > 0) out.add(L(t("ডেটা বিরতি: $gaps বার", "Data gaps: $gaps")))
        if (rows.vest.size + rows.helmet.size >= 25) {
            val an = analyze(rows.vest, rows.helmet, th)
            val mm = an.findings.filter { f -> f.sev != "low" && f.kind != "gap" }
            if (mm.isNotEmpty()) {
                out.add(L("🤖 " + t("${mm.size}টি অস্বাভাবিক প্যাটার্ন পাওয়া গেছে", "${mm.size} unusual pattern(s) found")))
                actions.add(ChatAction(ChatAction.OPEN_ANALYZE, t("বিস্তারিত", "Details")))
            } else out.add(L("🤖 " + t("কোনো অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি", "no unusual pattern found")))
        }
        return ChatReply(out, actions)
    }

    private suspend fun hAnomaly(it: Intent): ChatReply {
        val hours = when (it.scope.kind) { "hours" -> it.scope.hours; "week" -> 168; "today" -> 6; else -> 24 }
        val rows = ds.rows(Scope(System.currentTimeMillis() - hours * 3_600_000L, System.currentTimeMillis(), "", "", "", "hours", hours))
        val n = rows.vest.size + rows.helmet.size
        if (n < 25) return ChatReply(listOf(L(t("বিশ্লেষণের জন্য যথেষ্ট লগ নেই (গত $hours ঘণ্টায় ${n}টি)। অন্তত ~২৫টি লগ লাগে।", "Not enough logs to analyse ($n in the last $hours h). About 25 are needed."))))
        val an = analyze(rows.vest, rows.helmet, th)
        val main = an.findings.filter { f -> f.sev != "low" }
        val head = if (an.verdict == "normal") "✅ " + t("গত $hours ঘণ্টার ডেটায় কোনো অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি।", "No unusual pattern in the last $hours h of data.")
        else (if (an.verdict == "high") "🔴 " else "🟠 ") + t("গত $hours ঘণ্টায় **${main.size}টি** অস্বাভাবিক বিষয় পাওয়া গেছে:", "**${main.size}** unusual finding(s) in the last $hours h:")
        val out = mutableListOf(L(head))
        main.take(3).forEach { f -> out.add(L("• " + fmtTime(f.endTs) + " — " + if (bn) f.bn else f.en)) }
        if (an.counts.missed > 0) out.add(S(t("${an.counts.missed}টি ফিক্সড থ্রেশহোল্ডে ধরা পড়ত না।", "${an.counts.missed} of these would not trigger the fixed thresholds.")))
        return ChatReply(out, listOf(ChatAction(ChatAction.OPEN_ANALYZE, t("পুরো বিশ্লেষণ ও চার্ট দেখুন", "Open full analysis & charts"), hours = hours)))
    }
}
