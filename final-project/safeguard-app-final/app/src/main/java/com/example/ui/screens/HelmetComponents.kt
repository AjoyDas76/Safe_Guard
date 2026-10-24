package com.example.ui.screens

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.export.HelmetExcelExporter
import com.example.manager.HelmetTelemetryManager
import com.example.model.*
import com.example.ui.components.GlassCard
import com.example.ui.components.OnlineStatusPill
import com.example.ui.components.TelemetryMetricCard
import com.example.ui.theme.*
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*


/** Helmet palette: same colours the web dashboard uses (HR red, SpO₂ cyan, gas purple). */
internal object HelmetPalette {
    val HrDark = Color(0xFFFF3366); val HrLight = Color(0xFFE11D48)
    val SpoDark = Color(0xFF38BDF8); val SpoLight = Color(0xFF0369A1)
    val GasDark = Color(0xFFF59E0B); val GasLight = Color(0xFFD97706)
    val AlertLine = Color(0xFFFF3366)
    val Obstacle = Color(0xFFF97316)
    val GasBar = Color(0xFFF59E0B)
}

/** Live helmet charts (heart rate, SpO₂, gas level, obstacle distance) for the Live Chart screen. */
@Composable
fun HelmetLiveChartsContent() {
    val chart by HelmetTelemetryManager.chartHistory.collectAsState()
    val data by HelmetTelemetryManager.helmetData.collectAsState()
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val hrColor = if (isDark) HelmetPalette.HrDark else HelmetPalette.HrLight
    val spoColor = if (isDark) HelmetPalette.SpoDark else HelmetPalette.SpoLight
    val gasColor = if (isDark) HelmetPalette.GasDark else HelmetPalette.GasLight
    val green = if (isDark) EmeraldOnline else CommandLightEmerald
    val red = if (isDark) OfflineRed else CommandLightRed
    val p = data.reading
    val labels = chart.map { fmtClock(it.timestamp) }

    fun dist(v: Int): Float? = if (v >= HelmetThresholds.NO_ECHO) null else v.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("REAL-TIME HELMET GRAPH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                Text(
                    if (data.isOnline) "Live stream • updates on every helmet packet" else "Helmet offline • waiting for data",
                    fontSize = 11.sp,
                    color = if (data.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            OnlineStatusPill(isOnline = data.isOnline)
        }

        // Current values strip
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            LiveValueChip("HR", if (data.isOnline && p.heartRate > 0) "${p.heartRate.toInt()} bpm" else "--", hrColor, Modifier.weight(1f))
            LiveValueChip("SpO₂", if (data.isOnline && p.spo2 > 0) "${p.spo2.toInt()}%" else "--", spoColor, Modifier.weight(1f))
            LiveValueChip("GAS", if (data.isOnline) p.gasLevel.toInt().toString() else "--", gasColor, Modifier.weight(1f))
            LiveValueChip(
                "OBST", if (!data.isOnline) "--" else if (p.anyObstacle) "ALERT" else "SAFE",
                if (data.isOnline && p.anyObstacle) red else green, Modifier.weight(1f)
            )
        }

        HelmetChartCard("HEART RATE (bpm)", hrColor) {
            HelmetLineChart(
                labels = labels,
                series = listOf(ChartSeries("Heart Rate (bpm)", chart.map { if (it.heartRate > 0) it.heartRate.toFloat() else null }, hrColor.toArgb())),
                isDark = isDark
            )
        }
        HelmetChartCard("BLOOD OXYGEN SpO₂ (%)", spoColor) {
            HelmetLineChart(
                labels = labels,
                series = listOf(ChartSeries("SpO₂ (%)", chart.map { if (it.spo2 > 0) it.spo2.toFloat() else null }, spoColor.toArgb())),
                isDark = isDark, yMin = 80f, yMax = 100f
            )
        }
        HelmetChartCard("GAS LEVEL (MQ-2)", gasColor) {
            HelmetLineChart(
                labels = labels,
                series = listOf(
                    ChartSeries("Gas Level", chart.map { it.gasLevel.toFloat() }, gasColor.toArgb()),
                    ChartSeries("Alert level", chart.map { it.gasAlertLevel?.toFloat() }, HelmetPalette.AlertLine.toArgb(), dashed = true)
                ),
                isDark = isDark
            )
        }
        HelmetChartCard("OBSTACLE DISTANCE (cm)", HelmetPalette.Obstacle) {
            HelmetLineChart(
                labels = labels,
                series = listOf(
                    ChartSeries("Left", chart.map { dist(it.distLeft) }, HelmetPalette.Obstacle.toArgb()),
                    ChartSeries("Front", chart.map { dist(it.distFront) }, if (isDark) 0xFF00E5FF.toInt() else 0xFF0284C7.toInt()),
                    ChartSeries("Right", chart.map { dist(it.distRight) }, if (isDark) 0xFF10B981.toInt() else 0xFF059669.toInt()),
                    ChartSeries("Limit", chart.map { HelmetThresholds.OBST_CM.toFloat() }, HelmetPalette.AlertLine.toArgb(), dashed = true)
                ),
                isDark = isDark, yMin = 0f
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LiveValueChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, borderColor = color.copy(alpha = 0.5f), contentPadding = PaddingValues(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.6.sp)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}


@Composable
fun HelmetReportSection(period: ReportPeriod) {
    val context = LocalContext.current
    val state = rememberLogs(period)
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val hrColor = if (isDark) HelmetPalette.HrDark else HelmetPalette.HrLight
    val spoColor = if (isDark) HelmetPalette.SpoDark else HelmetPalette.SpoLight
    val gasColor = if (isDark) HelmetPalette.GasDark else HelmetPalette.GasLight
    val amber = if (isDark) SafetyAmber else CommandLightSecondary
    val red = if (isDark) HazardCrimson else CommandLightRed
    val green = if (isDark) EmeraldOnline else CommandLightEmerald

    val entries = (state as? LogsState.Loaded)?.entries ?: emptyList()
    val offline = remember(state) { HelmetTelemetryManager.offlineEvents(entries) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Engineering, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                "HELMET ${period.title.uppercase()} REPORT",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp
            )
        }

        when (state) {
            is LogsState.Loading -> StateMessage("Loading helmet report...", loading = true)
            is LogsState.Error -> StateMessage("হেলমেট রিপোর্ট লোড করতে ব্যর্থ হয়েছে: ${state.message}")
            is LogsState.Loaded -> {
                if (entries.isEmpty()) {
                    StateMessage("এই সময়সীমার জন্য হেলমেটের কোনো রিপোর্ট ডাটা জমা হয়নি।\nহেলমেট চালু থাকলে ও ডাটা পাঠালে এখানে স্বয়ংক্রিয়ভাবে হিস্ট্রি জমা হবে।")
                } else {
                    val hrs = entries.map { it.reading.heartRate }.filter { it > 0 }
                    val sps = entries.map { it.reading.spo2 }.filter { it > 0 }
                    val gases = entries.map { it.reading.gasLevel }
                    fun f1(v: Double?, u: String) = if (v == null) "--" else String.format(Locale.US, "%.1f%s", v, u)

                    StatGrid(
                        listOf(
                            "Records" to entries.size.toString(),
                            "Avg Heart Rate" to f1(hrs.takeIf { it.isNotEmpty() }?.average(), " bpm"),
                            "HR Min – Max" to if (hrs.isEmpty()) "--" else "${hrs.min().toInt()} – ${hrs.max().toInt()}",
                            "Avg SpO₂" to f1(sps.takeIf { it.isNotEmpty() }?.average(), "%"),
                            "Min SpO₂" to if (sps.isEmpty()) "--" else "${sps.min().toInt()}%",
                            "Peak Gas Level" to if (gases.isEmpty()) "--" else gases.max().toInt().toString(),
                            "Obstacle Snapshots" to entries.count { it.reading.anyObstacle }.toString(),
                            "Gas Snapshots" to entries.count { it.reading.gasFlag }.toString(),
                            "Vitals Snapshots" to entries.count { it.reading.hrBad || it.reading.spo2Bad }.toString(),
                            "Offline Events" to offline.size.toString()
                        )
                    )

                    val (pts, labels) = aggregate(entries, period)
                    val showDots = period != ReportPeriod.DAILY

                    HelmetChartCard("HEART RATE & SpO₂ TREND", hrColor) {
                        HelmetLineChart(
                            labels = labels,
                            series = listOf(
                                ChartSeries("Heart Rate (bpm)", pts.map { it.hr }, hrColor.toArgb(), dots = showDots),
                                ChartSeries("SpO₂ (%)", pts.map { it.sp }, spoColor.toArgb(), dots = showDots)
                            ),
                            isDark = isDark, heightDp = 210
                        )
                    }
                    HelmetChartCard("GAS LEVEL", gasColor) {
                        HelmetLineChart(
                            labels = labels,
                            series = listOf(
                                ChartSeries(if (period == ReportPeriod.DAILY) "Gas Level" else "Peak Gas Level", pts.map { it.gas }, HelmetPalette.GasBar.toArgb(), dots = showDots, fill = true),
                                ChartSeries("Alert level", pts.map { it.thr }, HelmetPalette.AlertLine.toArgb(), dashed = true)
                            ),
                            isDark = isDark, heightDp = 210
                        )
                    }
                    HelmetChartCard("OBSTACLE, GAS & VITALS ALERTS", amber) {
                        HelmetStackedBarChart(labels = labels, pts = pts, isDark = isDark)
                    }
                }

                if (entries.isNotEmpty()) {
                    HelmetConnectivityCard(offline, red, green)

                    var exporting by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            exporting = true
                            val file = HelmetExcelExporter.export(
                                context = context,
                                entries = entries,
                                offline = offline,
                                rangeName = period.title,
                                helmetId = HelmetTelemetryManager.HELMET_ID,
                                workerId = com.example.data.WorkerProfileRepository.activeWorker.value.workerId
                            )
                            exporting = false
                            if (file != null && file.exists()) {
                                Toast.makeText(context, "Helmet Excel report generated!", Toast.LENGTH_SHORT).show()
                                HelmetExcelExporter.share(context, file)
                            } else {
                                Toast.makeText(context, "Error generating helmet report", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !exporting,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("EXPORT HELMET REPORT (.XLSX)", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.8.sp)
                    }
                }
            }
        }
    }
}


private sealed class LogsState {
    object Loading : LogsState()
    data class Error(val message: String) : LogsState()
    data class Loaded(val entries: List<HelmetLogEntry>) : LogsState()
}


@Composable
private fun rememberLogs(period: ReportPeriod): LogsState {
    var state by remember(period) { mutableStateOf<LogsState>(LogsState.Loading) }
    LaunchedEffect(period) {
        state = LogsState.Loading
        HelmetTelemetryManager.fetchLogs(System.currentTimeMillis() - period.durationMillis) { result ->
            state = result.fold(
                onSuccess = { LogsState.Loaded(it) },
                onFailure = { LogsState.Error(it.message ?: "Unknown error") }
            )
        }
    }
    return state
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            pair.forEach { (label, value) ->
                GlassCard(modifier = Modifier.weight(1f), contentPadding = PaddingValues(10.dp)) {
                    Column {
                        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.6.sp)
                        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StateMessage(text: String, loading: Boolean = false) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


private data class AggPoint(
    val ts: Long, val hr: Float?, val sp: Float?, val gas: Float, val thr: Float?,
    val obst: Int, val gasN: Int, val vit: Int
)

private fun aggregate(entries: List<HelmetLogEntry>, period: ReportPeriod): Pair<List<AggPoint>, List<String>> {
    val dayFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val bucket = maxOf(1, Math.ceil(entries.size / 720.0).toInt())
    val groups = LinkedHashMap<String, MutableList<HelmetLogEntry>>()
    entries.forEachIndexed { i, e ->
        val key = if (period == ReportPeriod.DAILY) (i / bucket).toString() else dayFmt.format(Date(e.timestamp))
        groups.getOrPut(key) { mutableListOf() }.add(e)
    }
    val points = groups.values.map { g ->
        val hrs = g.map { it.reading.heartRate }.filter { it > 0 }
        val sps = g.map { it.reading.spo2 }.filter { it > 0 }
        val thr = g.mapNotNull { it.reading.gasAlertLevel }.lastOrNull()
        AggPoint(
            ts = g.first().timestamp,
            hr = if (hrs.isEmpty()) null else (Math.round(hrs.average() * 10) / 10f),
            sp = if (sps.isEmpty()) null else (Math.round(sps.average() * 10) / 10f),
            gas = g.maxOf { it.reading.gasLevel }.toFloat(),
            thr = thr?.toFloat(),
            obst = g.count { it.reading.anyObstacle },
            gasN = g.count { it.reading.gasFlag },
            vit = g.count { it.reading.hrBad || it.reading.spo2Bad }
        )
    }
    val labels = points.map { if (period == ReportPeriod.DAILY) timeFmt.format(Date(it.ts)) else dayFmt.format(Date(it.ts)) }
    return points to labels
}


@Composable
private fun HelmetConnectivityCard(events: List<HelmetOfflineEvent>, red: Color, green: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "CONNECTIVITY" + if (events.isNotEmpty()) " (${events.size})" else "",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.8.sp
            )
            if (events.isEmpty()) {
                Text("এই সময়সীমায় কোনো সংযোগ বিচ্ছিন্নতা পাওয়া যায়নি — হেলমেট সবসময় ডাটা পাঠিয়েছে।", fontSize = 11.sp, color = green)
            } else {
                events.asReversed().take(50).forEach { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (e.ongoing) Icons.Default.Warning else Icons.Default.PowerOff, null, tint = red, modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                (if (e.ongoing) "বর্তমানে অফলাইন" else "অফলাইন হয়েছিল") + " — " + fmtShort(e.from),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                (if (e.ongoing) "শেষ ডাটা পাওয়া গিয়েছিল এই সময়ে" else "সংযোগ ফিরেছে: ${fmtShort(e.to)}") + " · স্থায়িত্ব: ${fmtDuration(e.durationMs)}",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// Charts
// =====================================================================

internal data class ChartSeries(
    val label: String,
    val values: List<Float?>,
    val color: Int,
    val dashed: Boolean = false,
    val dots: Boolean = false,
    val fill: Boolean = false
)

@Composable
internal fun HelmetChartCard(title: String, accent: Color, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), borderColor = accent.copy(alpha = 0.45f)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 0.8.sp)
            content()
        }
    }
}

@Composable
internal fun HelmetLineChart(
    labels: List<String>,
    series: List<ChartSeries>,
    isDark: Boolean,
    heightDp: Int = 170,
    yMin: Float? = null,
    yMax: Float? = null
) {
    val axisTxt = if (isDark) AndroidColor.parseColor("#9AA8BC") else AndroidColor.parseColor("#475569")
    val axisGrid = if (isDark) AndroidColor.argb(40, 255, 255, 255) else AndroidColor.argb(40, 0, 0, 0)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(false)
                setScaleEnabled(false)
                axisRight.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
                setNoDataText("Waiting for data...")
            }
        },
        update = { chart ->
            chart.setNoDataTextColor(axisTxt)
            chart.legend.apply {
                isEnabled = series.size > 1
                this.textColor = axisTxt
                textSize = 9f
            }
            chart.xAxis.apply {
                this.textColor = axisTxt
                textSize = 8f
                setLabelCount(5, false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = labels.getOrNull(value.toInt()) ?: ""
                }
            }
            chart.axisLeft.apply {
                this.textColor = axisTxt
                textSize = 8f
                this.gridColor = axisGrid
                if (yMin != null) axisMinimum = yMin else resetAxisMinimum()
                if (yMax != null) axisMaximum = yMax else resetAxisMaximum()
            }

            val sets = series.map { s ->
                val entries = s.values.mapIndexedNotNull { i, v -> if (v == null) null else Entry(i.toFloat(), v) }
                LineDataSet(entries, s.label).apply {
                    color = s.color
                    lineWidth = 1.8f
                    setDrawValues(false)
                    setDrawCircles(s.dots)
                    setCircleColor(s.color)
                    circleRadius = 3f
                    mode = LineDataSet.Mode.LINEAR
                    if (s.dashed) enableDashedLine(12f, 8f, 0f)
                    if (s.fill) {
                        setDrawFilled(true)
                        fillColor = s.color
                        fillAlpha = 40
                    }
                    setDrawHighlightIndicators(false)
                }
            }
            chart.data = if (sets.all { it.entryCount == 0 }) null else LineData(sets)
            chart.invalidate()
        }
    )
}

@Composable
private fun HelmetStackedBarChart(labels: List<String>, pts: List<AggPoint>, isDark: Boolean) {
    val axisTxt = if (isDark) AndroidColor.parseColor("#9AA8BC") else AndroidColor.parseColor("#475569")
    val axisGrid = if (isDark) AndroidColor.argb(40, 255, 255, 255) else AndroidColor.argb(40, 0, 0, 0)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        factory = { ctx ->
            BarChart(ctx).apply {
                description.isEnabled = false
                setScaleEnabled(false)
                setPinchZoom(false)
                axisRight.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
            }
        },
        update = { chart ->
            chart.legend.apply { this.textColor = axisTxt; textSize = 9f }
            chart.xAxis.apply {
                this.textColor = axisTxt
                textSize = 8f
                setLabelCount(5, false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = labels.getOrNull(value.toInt()) ?: ""
                }
            }
            chart.axisLeft.apply {
                this.textColor = axisTxt
                textSize = 8f
                this.gridColor = axisGrid
                axisMinimum = 0f
                granularity = 1f
            }
            val entries = pts.mapIndexed { i, g -> BarEntry(i.toFloat(), floatArrayOf(g.obst.toFloat(), g.gasN.toFloat(), g.vit.toFloat())) }
            val set = BarDataSet(entries, "").apply {
                setColors(HelmetPalette.Obstacle.toArgb(), HelmetPalette.GasBar.toArgb(), HelmetPalette.AlertLine.toArgb())
                stackLabels = arrayOf("Obstacle", "Gas", "Vitals")
                setDrawValues(false)
            }
            chart.data = BarData(set).apply { barWidth = 0.8f }
            chart.invalidate()
        }
    )
}


// =====================================================================
// helpers
// =====================================================================

internal fun fmtClock(ts: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
internal fun fmtShort(ts: Long): String = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))
internal fun fmtFull(ts: Long): String = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(ts))

internal fun fmtDuration(ms: Long): String {
    val m = Math.round(ms / 60000.0).toInt()
    if (m < 1) return "< ১ মিনিট"
    if (m < 60) return "$m মিনিট"
    val h = m / 60
    val r = m % 60
    return "$h ঘণ্টা" + if (r > 0) " $r মিনিট" else ""
}
