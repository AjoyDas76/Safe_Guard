package com.example.ai

import java.util.Calendar

/*
 * Tiny bilingual NLU (Bangla + English + a little Banglish) - port of the
 * website's detectIntent(). Rule-based on purpose: every answer is computed
 * from real data, so the chat can never "hallucinate" a sensor value.
 */

data class Scope(
    val since: Long, val until: Long,
    val bn: String, val poss: String, val en: String,
    val kind: String,            // today | yesterday | hours | week
    val hours: Int = 0, val capped: Boolean = false
)

data class Intent(
    val intent: String,
    val scope: Scope,
    val sensors: List<String>,
    val stats: Boolean,
    val hasScopeWord: Boolean,
    val filter: List<String> = emptyList()
)

private const val BN_DIG = "০১২৩৪৫৬৭৮৯"
fun bn2en(s: String): String = buildString {
    for (c in s) { val i = BN_DIG.indexOf(c); if (i >= 0) append(i) else append(c) }
}

private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

private object RX {
    val help = rx("সাহায্য|কি কি পার|কী কী পার|কি করতে পার|কী করতে পার|\\bhelp\\b|what can you|কিভাবে ব্যবহার")
    val greet = rx("^\\s*(hi|hello|hey|হাই|হ্যালো|হেলো|সালাম|আসসালামু|নমস্কার)")
    val alert = rx("অ্যালার্ট|এ্যালার্ট|এলার্ট|অ্যালার্ম|এলার্ম|সতর্ক|alert|alarm|warning")
    val count = rx("কয়টা|কয়টি|কতটা|কতটি|কতগুলো|কতগুলি|কত\\s*বার|কতবার|সংখ্যা|how many|count|number of|total|koyta|koyti")
    val last = rx("শেষ|সর্বশেষ|সাম্প্রতিক|\\blast\\b|latest|recent|কখন|\\bwhen\\b")
    val now = rx("এখন|বর্তমান|চলমান|সক্রিয়|\\bnow\\b|current|\\bactive\\b|right now|\\blive\\b")
    val anomaly = rx("অস্বাভাবিক|অসঙ্গতি|অ্যানোমালি|এনোমালি|অ্যানমালি|anomal|unusual|abnormal|strange|weird|সন্দেহজনক|ট্রেন্ড|\\btrend|প্যাটার্ন|pattern")
    val location = rx("অবস্থান|লোকেশন|কোথায়|জিপিএস|\\bgps\\b|location|\\bwhere\\b|ম্যাপ|\\bmap\\b")
    val status = rx("অবস্থা(?!ন)|কেমন|স্ট্যাটাস|status|condition|how is|how's|নিরাপদ|\\bsafe\\b|ঠিক আছে|ঠিকআছে|কি খবর|কী খবর|kemon|obostha|ওয়ার্কার|শ্রমিক|\\bworker\\b")
    val summary = rx("সামারি|সারাংশ|সারসংক্ষেপ|সংক্ষেপে|summary|overview|রিপোর্ট|\\breport\\b")
    val stats = rx("গড়|সর্বোচ্চ|সর্বনিম্ন|average|\\bavg\\b|\\bmax\\b|\\bmin\\b|highest|lowest|maximum|minimum|peak")
}

private val SENSOR_RX = listOf(
    "temp" to rx("তাপমাত্রা|তাপ|গরম|টেম্পারেচার|ঠান্ডা|\\btemp|temperature|\\bhot\\b|\\bheat"),
    "hum" to rx("আর্দ্রতা|আদ্রতা|হিউমিডিটি|humid"),
    "pres" to rx("বায়ুচাপ|চাপ|প্রেসার|প্রেশার|pressure|\\bhpa\\b"),
    "hr" to rx("হার্ট|হৃদ|পালস|নাড়ি|heart|pulse|\\bbpm\\b|\\bhr\\b"),
    "spo2" to rx("অক্সিজেন|স্যাচুরেশন|spo2|spo₂|oxygen|saturation"),
    "gas" to rx("গ্যাস|ধোঁয়া|ধোয়া|\\bgas\\b|smoke|mq-?2"),
    "obstacle" to rx("বাধা|অবস্টাকল|obstacle|proximity|দূরত্ব|distance|ultrasonic"),
    "motion" to rx("পড়ে গেছে|পড়ে গেল|পতন|ফল ডিটেক্ট|\\bfall\\b|motion|নড়াচড়া|হাঁট|দাঁড়|শুয়ে|walking|running|lying|standing|\\bsos\\b|এসওএস"),
    "signal" to rx("সিগন্যাল|সিগনাল|rssi|\\bsignal\\b|lora|নেটওয়ার্ক")
)

val SENSOR_ALERT_KEYS: Map<String, List<String>> = mapOf(
    "temp" to listOf("temp"), "hum" to listOf("humidity"), "pres" to listOf("pressure"),
    "hr" to listOf("hr"), "spo2" to listOf("spo2"), "gas" to listOf("gas"),
    "obstacle" to listOf("obstacle"), "motion" to listOf("fall", "sos")
)

private fun startOfDay(now: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = now
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** "last 2 hours" / "গত ৩ ঘণ্টা" / today / yesterday / week */
fun parseScope(q: String, now: Long = System.currentTimeMillis()): Scope {
    val s = bn2en(q.lowercase())
    val sod = startOfDay(now)
    val day = 86_400_000L
    if (Regex("গতকাল|yesterday").containsMatchIn(s))
        return Scope(sod - day, sod, "গতকাল", "গতকালের", "yesterday", "yesterday")
    val m = Regex("(\\d+)\\s*(ঘণ্টা|ঘন্টা|hours?|hrs?|h\\b)").find(s)
    if (m != null) {
        val n = m.groupValues[1].toInt().coerceIn(1, 168)
        return Scope(now - n * 3_600_000L, now, "গত $n ঘণ্টায়", "গত $n ঘণ্টার", "in the last $n h", "hours", hours = n)
    }
    if (Regex("ঘণ্টা|ঘন্টা|\\bhour").containsMatchIn(s) && Regex("গত|শেষ|last|past").containsMatchIn(s))
        return Scope(now - 3_600_000L, now, "গত ১ ঘণ্টায়", "গত ১ ঘণ্টার", "in the last hour", "hours", hours = 1)
    if (Regex("সপ্তাহ|\\bweek|7\\s*(দিন|days)|মাস|month").containsMatchIn(s))
        return Scope(now - 7 * day, now, "গত ৭ দিনে", "গত ৭ দিনের", "in the last 7 days", "week",
            capped = Regex("মাস|month").containsMatchIn(s))
    return Scope(sod, now, "আজ", "আজকের", "today", "today")
}

fun detectIntent(q: String, now: Long = System.currentTimeMillis()): Intent {
    val s = q.trim()
    val scope = parseScope(s, now)
    val sensors = SENSOR_RX.filter { it.second.containsMatchIn(s) }.map { it.first }
    val hasScopeWord = scope.kind != "today" || Regex("আজ|today|আজকে", RegexOption.IGNORE_CASE).containsMatchIn(s)
    fun mk(intent: String, filter: List<String> = emptyList()) =
        Intent(intent, scope, sensors, RX.stats.containsMatchIn(s), hasScopeWord, filter)

    if (s.length <= 30 && RX.greet.containsMatchIn(s)) return mk("greet")
    if (RX.help.containsMatchIn(s)) return mk("help")
    if (RX.alert.containsMatchIn(s)) {
        val filter = sensors.flatMap { SENSOR_ALERT_KEYS[it] ?: emptyList() }
        if (RX.last.containsMatchIn(s) && !RX.count.containsMatchIn(s)) return mk("lastAlert", filter)
        if (RX.now.containsMatchIn(s) && !RX.count.containsMatchIn(s)) return mk("activeAlerts", filter)
        return mk("alertCount", filter)
    }
    if (RX.anomaly.containsMatchIn(s)) return mk("anomaly")
    if (RX.location.containsMatchIn(s)) return mk("location")
    if (sensors.isNotEmpty()) return mk("sensor")
    if (RX.summary.containsMatchIn(s)) return mk("summary")
    if (RX.status.containsMatchIn(s)) return mk("status")
    return mk("unknown")
}
