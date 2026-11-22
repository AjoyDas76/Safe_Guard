package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.*
import com.example.ui.components.webGlass
import com.example.ui.components.webTextMain
import com.example.ui.components.webTextMuted
import com.example.ui.theme.CommandNavyDark
import com.example.ui.theme.CyanNeon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WINDOWS = linkedMapOf("1h" to 3_600_000L, "6h" to 6 * 3_600_000L, "24h" to 86_400_000L, "7d" to 7 * 86_400_000L)

private fun windowKeyForHours(h: Int) = if (h <= 1) "1h" else if (h <= 6) "6h" else if (h <= 24) "24h" else "7d"

/** Sensor colours follow AGENTS.md (temperature red, humidity cyan, pressure purple). */
private fun metricColor(key: String, isDark: Boolean): Color = when (key) {
    "temp" -> if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
    "hum" -> if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
    "pres" -> if (isDark) Color(0xFFA855F7) else Color(0xFF7C3AED)
    "hr" -> Color(0xFFEC4899)
    "spo2" -> Color(0xFF10B981)
    else -> Color(0xFFFB923C) // gas
}

private val SevHigh = Color(0xFFEF4444)
private val SevMed = Color(0xFFF59E0B)
private val SevLow = Color(0xFF94A3B8)
private val OkGreen = Color(0xFF10B981)

private fun sevColor(s: String) = when (s) { "high" -> SevHigh; "medium" -> SevMed; else -> SevLow }
private fun fmtDT(ts: Long) = SimpleDateFormat("d MMM, h:mm a", Locale.US).format(Date(ts))
private fun fmtTime(ts: Long) = SimpleDateFormat("h:mm a", Locale.US).format(Date(ts))

/**
 * "AI Analyze" - same anomaly analysis as the website (robust z-score, sudden jump, sustained trend with
 * time-to-limit forecast, flat-line, data gaps, prolonged inactivity, temperature + heart-rate cross-signal).
 * Manual only: it runs when ANALYZE is pressed, never automatically.
 */
@Composable
fun AiAnalysisScreen() {
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val scope = rememberCoroutineScope()
    var winKey by remember { mutableStateOf(AiUiState.lastWindowKey) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var metricSel by remember { mutableStateOf<String?>(null) }
    var showMinor by remember { mutableStateOf(false) }
    val result = AiUiState.lastAnalysis

    fun runAnalysis() {
        if (running) return
        running = true; message = null
        scope.launch {
            try {
                FirebaseAiDataSource.clearCache() // always analyse fresh data
                val now = System.currentTimeMillis()
                val rows = FirebaseAiDataSource.rows(Scope(now - (WINDOWS[winKey] ?: 0L), now, "", "", "", "hours"))
                val n = rows.vest.size + rows.helmet.size
                if (n < 25) {
                    AiUiState.lastAnalysis = null
                    message = "এই সময়সীমায় যথেষ্ট লগ ডেটা নেই (${n}টি)। বিশ্লেষণের জন্য অন্তত ~২৫টি লগ লাগে — ডিভাইস কিছুক্ষণ চালু রেখে বা বড় সময়সীমা বেছে আবার চেষ্টা করুন।"
                } else {
                    val t0 = System.nanoTime()
                    val r = analyze(rows.vest, rows.helmet, FirebaseAiDataSource.thresholds())
                    AiUiState.lastRunMs = (System.nanoTime() - t0) / 1_000_000L
                    AiUiState.lastWindowKey = winKey
                    AiUiState.lastAnalysis = r
                    showMinor = false
                    metricSel = null
                }
            } catch (e: Exception) {
                val m = e.message ?: ""
                message = if (m.contains("PERMISSION_DENIED", true) || m.startsWith("-3")) "ডাটাবেজ পড়ার অনুমতি পাওয়া যায়নি — লগইন/Firebase Rules দেখুন।"
                else "ডেটা আনতে সমস্যা হয়েছে: $m"
            } finally { running = false }
        }
    }

    // "Open full analysis" from the chat: pick the window and run once.
    LaunchedEffect(Unit) {
        val h = AiUiState.pendingAnalyzeHours
        if (h != null) {
            AiUiState.pendingAnalyzeHours = null
            winKey = windowKeyForHours(h)
            runAnalysis()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp + com.example.ui.components.LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("AI ANOMALY ANALYSIS", fontWeight = FontWeight.Black, fontSize = 16.sp, color = webTextMain(isDark), letterSpacing = 1.sp)

        // window + run
        Column(modifier = Modifier.fillMaxWidth().webGlass(isDark).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WINDOWS.keys.forEach { k ->
                    val sel = k == winKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) CyanNeon.copy(alpha = 0.22f) else Color.Transparent)
                            .border(1.dp, if (sel) CyanNeon else webTextMuted(isDark).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !running) { winKey = k }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("LAST " + k.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = webTextMain(isDark)) }
                }
            }
            Button(onClick = { runAnalysis() }, enabled = !running, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("ANALYZING…")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("ANALYZE", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (result == null && message == null && !running) {
            Text(
                "সময়সীমা বেছে ANALYZE চাপুন। সিস্টেম সংরক্ষিত লগ ডেটা দেখে ফিক্সড থ্রেশহোল্ডের বাইরের অস্বাভাবিক প্যাটার্ন (হঠাৎ লাফ, ধীরে ধীরে বাড়া/কমা, আটকে যাওয়া সেন্সর, ডেটা বিরতি, একসাথে একাধিক সেন্সরের পরিবর্তন) খুঁজে বের করবে। এটি নিজে থেকে চলে না — শুধু বাটন চাপলে।",
                fontSize = 12.sp, color = webTextMuted(isDark)
            )
        }
        message?.let { Text(it, fontSize = 13.sp, color = SevMed) }

        if (result != null && !running) ResultBody(result, isDark, metricSel, { metricSel = it }, showMinor, { showMinor = !showMinor })
    }
}

@Composable
private fun ResultBody(
    r: AnalysisResult, isDark: Boolean,
    metricSel: String?, onMetric: (String) -> Unit,
    showMinor: Boolean, onToggleMinor: () -> Unit
) {
    val verdictTxt = when (r.verdict) { "normal" -> "স্বাভাবিক"; "medium" -> "পর্যবেক্ষণ করুন"; else -> "অস্বাভাবিক!" }
    val verdictCol = when (r.verdict) { "normal" -> OkGreen; "medium" -> SevMed; else -> SevHigh }

    // summary cards (2 rows x 3)
    val cards = listOf(
        Triple("AI VERDICT", verdictTxt, verdictCol),
        Triple("RECORDS", "${r.counts.vest} / ${r.counts.helmet}", webTextMain(isDark)),
        Triple("ANOMALIES", "${r.counts.anomalies}", webTextMain(isDark)),
        Triple("MISSED BY FIXED LIMITS", "${r.counts.missed}", if (r.counts.missed > 0) SevMed else webTextMain(isDark)),
        Triple("DATA GAPS", "${r.counts.gaps}", webTextMain(isDark)),
        Triple("RUN TIME", "${AiUiState.lastRunMs} ms", webTextMain(isDark))
    )
    cards.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { c ->
                Column(
                    modifier = Modifier.weight(1f).webGlass(isDark, topLine = false).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(c.first, fontSize = 9.sp, color = webTextMuted(isDark), fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(c.second, fontSize = 15.sp, fontWeight = FontWeight.Black, color = c.third)
                }
            }
        }
    }

    // metric tabs (only metrics that have data)
    val keys = r.perMetric.keys.filter { r.perMetric[it]!!.points.isNotEmpty() }
    val withEp = keys.filter { k -> r.perMetric[k]!!.episodes.any { it.sev != "low" } }
    val selected = metricSel?.takeIf { it in keys } ?: withEp.firstOrNull() ?: keys.firstOrNull()
    if (selected != null) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(keys) { k ->
                val nEp = r.perMetric[k]!!.episodes.count { it.sev != "low" }
                val sel = k == selected
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) CyanNeon.copy(alpha = 0.22f) else Color.Transparent)
                        .border(1.dp, if (sel) CyanNeon else webTextMuted(isDark).copy(alpha = 0.4f), RoundedCornerShape(50))
                        .clickable { onMetric(k) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(r.metrics[k]!!.en, fontSize = 12.sp, color = webTextMain(isDark))
                    if (nEp > 0) Box(Modifier.clip(RoundedCornerShape(50)).background(SevHigh).padding(horizontal = 6.dp)) {
                        Text("$nEp", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        MetricChart(r, selected, isDark)
    }

    Text("FINDINGS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = webTextMain(isDark), letterSpacing = 1.sp)
    val main = r.findings.filter { it.sev != "low" }
    val minor = r.findings.filter { it.sev == "low" }
    if (main.isEmpty()) Text("✅ এই সময়সীমায় কোনো উল্লেখযোগ্য অস্বাভাবিক প্যাটার্ন পাওয়া যায়নি।", fontSize = 13.sp, color = OkGreen)
    main.forEach { FindingCard(it, isDark) }
    if (showMinor) minor.forEach { FindingCard(it, isDark) }
    if (minor.isNotEmpty()) {
        TextButton(onClick = onToggleMinor) { Text(if (showMinor) "Hide minor findings" else "Show ${minor.size} minor finding(s)") }
    }

    var how by remember { mutableStateOf(false) }
    TextButton(onClick = { how = !how }) { Text("এটি কীভাবে কাজ করে? (How it works)") }
    if (how) Text(
        "• Robust z-score: প্রতিটি রিডিং আগের ${CFG.BASE_WIN}টি রিডিংয়ের median/MAD-এর সাথে তুলনা; ${CFG.Z_FLAG.toInt()}σ-এর বেশি দূরে গেলে ফ্ল্যাগ।\n" +
            "• Sudden jump: পরপর দুই রিডিংয়ের মধ্যে অস্বাভাবিক বড় লাফ।\n" +
            "• Sustained trend: ~${CFG.TREND_WIN}টি রিডিংয়ের উইন্ডোতে রৈখিক রিগ্রেশন (R² ≥ ${CFG.TREND_R2}); বর্তমান হারে সীমা ছুঁতে কত মিনিট লাগবে তার পূর্বাভাস।\n" +
            "• Flat-line: ${CFG.FLAT_RUN}টি টানা রিডিং হুবহু একই হলে সেন্সর আটকে যাওয়ার সন্দেহ।\n" +
            "• Data gap: লগে ৩ মিনিটের বেশি ফাঁক।\n" +
            "• Prolonged inactivity: টানা ${CFG.LYING_MS / 60000} মিনিটের বেশি LYING/FALL।\n" +
            "• Cross-signal: তাপমাত্রা ও হার্ট রেট একসাথে বাড়লে হিট-স্ট্রেসের সম্ভাব্য প্যাটার্ন (রোগনির্ণয় নয় — যাচাই করতে হবে)।\n\n" +
            "সব হিসাব ফোনেই হয়; কোনো API কী, সার্ভার বা খরচ লাগে না। \"ফিক্সড থ্রেশহোল্ড\" মানে Safety Rules স্ক্রিনের বর্তমান সীমা।",
        fontSize = 12.sp, color = webTextMuted(isDark)
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun FindingCard(f: Finding, isDark: Boolean) {
    val col = sevColor(f.sev)
    val whenTxt = fmtDT(f.startTs) + if (f.endTs - f.startTs > 60_000) " → " + fmtTime(f.endTs) else ""
    Column(
        modifier = Modifier.fillMaxWidth().webGlass(isDark, accent = if (f.sev == "high") col else null, topLine = false).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(col).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(when (f.sev) { "high" -> "HIGH"; "medium" -> "MEDIUM"; else -> "MINOR" }, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(f.tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = webTextMuted(isDark))
            Spacer(Modifier.weight(1f))
            Text(whenTxt, fontSize = 10.sp, color = webTextMuted(isDark))
        }
        if (f.kind == "episode" || f.kind == "forecast" || f.kind == "cross") {
            Text(
                if (f.caught) "ফিক্সড থ্রেশহোল্ডেও অ্যালার্ট হতো" else "ফিক্সড থ্রেশহোল্ড মিস করত",
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (f.caught) OkGreen else SevMed
            )
        }
        Text(f.bn, fontSize = 13.sp, color = webTextMain(isDark))
    }
}

@Composable
private fun MetricChart(r: AnalysisResult, key: String, isDark: Boolean) {
    val res = r.perMetric[key] ?: return
    val m = r.metrics[key] ?: return
    val pts = res.points
    if (pts.size < 2) return
    val lineCol = metricColor(key, isDark)
    val limitCol = Color(0xFFFBBF24)
    val axisCol = webTextMuted(isDark)

    // downsample long series but never drop a flagged point
    val stride = maxOf(1, Math.ceil(pts.size / 600.0).toInt())
    val idx = pts.indices.filter { it % stride == 0 || res.flags[it] != null }
    var lo = idx.minOf { pts[it].v }; var hi = idx.maxOf { pts[it].v }
    m.limLo?.let { lo = minOf(lo, it) }; m.limHi?.let { hi = maxOf(hi, it) }
    if (hi - lo < 1e-9) { hi += 1.0; lo -= 1.0 }
    val pad = (hi - lo) * 0.08; lo -= pad; hi += pad
    val t0 = pts.first().ts.toDouble(); val t1 = pts.last().ts.toDouble()

    Column(modifier = Modifier.fillMaxWidth().webGlass(isDark, topLine = false).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(m.en + (if (m.unit.isNotBlank()) " (" + m.unit.trim() + ")" else ""), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = webTextMain(isDark))
            Text(fx(hi, m.dec) + " / " + fx(lo, m.dec), fontSize = 10.sp, color = axisCol)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val w = size.width; val h = size.height
            fun x(ts: Long) = (((ts - t0) / (t1 - t0)) * w).toFloat()
            fun y(v: Double) = (h - ((v - lo) / (hi - lo)) * h).toFloat()
            // fixed safe limits
            listOf(m.limLo, m.limHi).forEach { lim ->
                if (lim != null) drawLine(limitCol, Offset(0f, y(lim)), Offset(w, y(lim)), strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            }
            // value line (break the line across data gaps)
            for (k in 1 until idx.size) {
                val a = pts[idx[k - 1]]; val b = pts[idx[k]]
                if (b.ts - a.ts > GAP_MS * 2) continue
                drawLine(lineCol, Offset(x(a.ts), y(a.v)), Offset(x(b.ts), y(b.v)), strokeWidth = 3f)
            }
            // anomaly points
            idx.forEach { i ->
                if (res.flags[i] != null) drawCircle(Color(0xFFFF3366), radius = 6f, center = Offset(x(pts[i].ts), y(pts[i].v)))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtDT(pts.first().ts), fontSize = 9.sp, color = axisCol)
            Text(fmtDT(pts.last().ts), fontSize = 9.sp, color = axisCol)
        }
        Text("━ মান   ● অস্বাভাবিক বিন্দু   ┅ ফিক্সড নিরাপদ সীমা", fontSize = 9.sp, color = axisCol)
    }
}
