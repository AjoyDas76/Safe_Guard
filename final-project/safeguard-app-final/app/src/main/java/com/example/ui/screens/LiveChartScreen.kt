package com.example.ui.screens

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.manager.VestTelemetryManager
import com.example.model.EventSnapshot
import com.example.model.VestData
import com.example.ui.components.GlassCard
import com.example.ui.components.TelemetryMarkerView
import com.example.ui.theme.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class ChartMetric {
    ALL,
    TEMPERATURE,
    HUMIDITY,
    PRESSURE
}

/**
 * Supported time window zoom presets for real-time telemetry observation.
 * By default, 4 seconds is active showing exactly 4 data points/dots.
 */
enum class TimeWindow(val label: String, val secondsSpan: Float) {
    SEC_4("4s (Focus)", 3.0f),
    SEC_10("10s", 9.0f),
    SEC_30("30s", 29.0f),
    ALL("All", 60.0f)
}

/**
 * Dedicated, distinct color tokens for each environmental sensor metric:
 * - Temperature: Coral Red
 * - Humidity: Electric Cyan
 * - Pressure: Royal Purple
 */
object TelemetryPalette {
    // Temperature: Coral / Fire Red
    val TempDark = Color(0xFFFF5252)
    val TempLight = Color(0xFFDC2626)
    const val TempHexDark = "#FF5252"
    const val TempHexLight = "#DC2626"

    // Humidity: Electric Cyan / Sky Blue
    val HumDark = Color(0xFF00E5FF)
    val HumLight = Color(0xFF0284C7)
    const val HumHexDark = "#00E5FF"
    const val HumHexLight = "#0284C7"

    // Pressure: Royal Purple / Violet
    val PresDark = Color(0xFFA855F7)
    val PresLight = Color(0xFF7C3AED)
    const val PresHexDark = "#A855F7"
    const val PresHexLight = "#7C3AED"
}

@Composable
private fun VestLiveChartContent() {
    val chartHistory by VestTelemetryManager.chartHistory.collectAsState()
    val vestData by VestTelemetryManager.vestData.collectAsState()
    var selectedMetric by remember { mutableStateOf(ChartMetric.ALL) }
    var selectedTimeWindow by remember { mutableStateOf(TimeWindow.SEC_4) }
    var selectedSnapshot by remember { mutableStateOf<EventSnapshot?>(null) }
    var isBrowsingHistory by remember { mutableStateOf(false) }
    var chartRef by remember { mutableStateOf<LineChart?>(null) }
    var isLiveStreaming by remember { mutableStateOf(true) }

    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark

    // Ensure baseline seed exists immediately on entry
    LaunchedEffect(Unit) {
        VestTelemetryManager.ensureInitialChartData()
    }

    // Active 1-second interval ticker for continuous real-time graph updating
    LaunchedEffect(isLiveStreaming) {
        while (isLiveStreaming) {
            delay(1000L)
            VestTelemetryManager.recordLiveTick()
        }
    }

    // Infinite pulsing animation for live stream indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp + com.example.ui.components.LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Header with Real-time Status Badge & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "REAL-TIME TELEMETRY GRAPH",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isLiveStreaming) "Live 1 Hz stream • Updating every second" else "Live stream paused",
                    fontSize = 11.sp,
                    color = if (isLiveStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Interactive Live Status Badge / Stream Toggle
            Surface(
                color = if (isLiveStreaming) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isLiveStreaming = !isLiveStreaming }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            // pulseAlpha is read inside the draw lambda (draw phase) so the 60fps pulse
                            // only redraws this dot instead of recomposing the whole screen
                            .drawBehind {
                                drawCircle(
                                    if (isLiveStreaming) Color(0xFF10B981).copy(alpha = pulseAlpha)
                                    else Color.Gray
                                )
                            }
                    )
                    Text(
                        text = if (isLiveStreaming) "LIVE 1s" else "PAUSED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isLiveStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        imageVector = if (isLiveStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isLiveStreaming) "Pause Stream" else "Resume Stream",
                        tint = if (isLiveStreaming) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Distinct Color Identification Legend Banner (Temp: Red, Humidity: Cyan, Pressure: Purple)
        SensorsColorIdentificationBanner(
            selectedMetric = selectedMetric,
            onMetricSelect = { selectedMetric = it },
            vestData = vestData,
            points = chartHistory,
            isDark = isDark
        )

        // Tab Selector Row with Color Bullets (All, Temp, Hum, Pres)
        SingleChoiceMetricSegment(
            selectedMetric = selectedMetric,
            onMetricSelected = { selectedMetric = it },
            isDark = isDark
        )

        // Time Window Presets & Zoom Controls Toolbar (Default: 4s with exactly 4 dots)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WINDOW:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
                TimeWindow.values().forEach { win ->
                    val isSelected = selectedTimeWindow == win
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                selectedTimeWindow = win
                                isBrowsingHistory = false
                                chartRef?.let { chart ->
                                    if (chartHistory.isNotEmpty()) {
                                        val base = chartHistory.first().timestamp
                                        val last = (chartHistory.last().timestamp - base) / 1000f
                                        if (win == TimeWindow.ALL) {
                                            chart.fitScreen()
                                        } else {
                                            chart.setVisibleXRange(1f, win.secondsSpan + 0.15f)
                                            chart.moveViewToX(last)
                                        }
                                        chart.invalidate()
                                    }
                                }
                            }
                    ) {
                        Text(
                            text = win.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Quick Zoom Out / In Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        chartRef?.let { chart ->
                            val curSpan = chart.highestVisibleX - chart.lowestVisibleX
                            when {
                                curSpan < 7f -> selectedTimeWindow = TimeWindow.SEC_10
                                curSpan < 22f -> selectedTimeWindow = TimeWindow.SEC_30
                                else -> selectedTimeWindow = TimeWindow.ALL
                            }
                            chart.zoomOut()
                            isBrowsingHistory = true
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        chartRef?.let { chart ->
                            val curSpan = chart.highestVisibleX - chart.lowestVisibleX
                            when {
                                curSpan > 22f -> selectedTimeWindow = TimeWindow.SEC_30
                                curSpan > 7f -> selectedTimeWindow = TimeWindow.SEC_10
                                else -> selectedTimeWindow = TimeWindow.SEC_4
                            }
                            chart.zoomIn()
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Main MPAndroidChart Container with Interactive Overlays
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        LineChart(context).apply {
                            chartRef = this
                            description.isEnabled = false
                            setTouchEnabled(true)
                            isDragEnabled = true
                            isDragXEnabled = true
                            isDragYEnabled = false
                            setScaleEnabled(true)
                            setScaleXEnabled(true)
                            setScaleYEnabled(false)
                            setPinchZoom(true)
                            isDoubleTapToZoomEnabled = false
                            setDrawGridBackground(false)
                            isHighlightPerTapEnabled = true
                            isHighlightPerDragEnabled = true

                            // Floating on-dot custom MarkerView tooltip
                            val telemetryMarker = TelemetryMarkerView(
                                context = context,
                                baseTimeProvider = { chartHistory.firstOrNull()?.timestamp ?: System.currentTimeMillis() },
                                getSnapshotAtRelSec = { relSec ->
                                    val base = chartHistory.firstOrNull()?.timestamp ?: return@TelemetryMarkerView null
                                    chartHistory.minByOrNull { Math.abs((it.timestamp - base) / 1000f - relSec) }
                                },
                                metricProvider = { selectedMetric },
                                isDarkProvider = { isDark }
                            )
                            telemetryMarker.chartView = this
                            marker = telemetryMarker

                            // On dot clicked listener
                            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                                override fun onValueSelected(e: Entry?, h: Highlight?) {
                                    if (e != null && chartHistory.isNotEmpty()) {
                                        val base = chartHistory.first().timestamp
                                        val matched = chartHistory.minByOrNull { Math.abs((it.timestamp - base) / 1000f - e.x) }
                                        selectedSnapshot = matched
                                    }
                                }
                                override fun onNothingSelected() {
                                    selectedSnapshot = null
                                }
                            })

                            // Gesture listener to detect swipe/pan into history vs live streaming
                            setOnChartGestureListener(object : OnChartGestureListener {
                                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                                    if (chartHistory.isNotEmpty()) {
                                        val base = chartHistory.first().timestamp
                                        val lastRelSec = (chartHistory.last().timestamp - base) / 1000f
                                        val isAtLiveEnd = highestVisibleX >= (lastRelSec - 0.7f)
                                        isBrowsingHistory = !isAtLiveEnd
                                    }
                                }
                                override fun onChartLongPressed(me: MotionEvent?) {}
                                override fun onChartDoubleTapped(me: MotionEvent?) {}
                                override fun onChartSingleTapped(me: MotionEvent?) {}
                                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {
                                    if (chartHistory.isNotEmpty()) {
                                        val base = chartHistory.first().timestamp
                                        val lastRelSec = (chartHistory.last().timestamp - base) / 1000f
                                        val isAtLiveEnd = highestVisibleX >= (lastRelSec - 0.7f)
                                        isBrowsingHistory = !isAtLiveEnd
                                    }
                                }
                                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
                                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
                            })

                            legend.textColor = if (isDark) AndroidColor.WHITE else AndroidColor.parseColor("#0F172A")
                            legend.textSize = 11f

                            xAxis.apply {
                                position = XAxis.XAxisPosition.BOTTOM
                                textColor = if (isDark) AndroidColor.LTGRAY else AndroidColor.parseColor("#475569")
                                gridColor = if (isDark) AndroidColor.argb(35, 255, 255, 255) else AndroidColor.argb(35, 15, 23, 42)
                                setDrawGridLines(true)
                                setAvoidFirstLastClipping(true)
                                isGranularityEnabled = true
                                granularity = 1f
                                setLabelCount(4, true)
                            }

                            axisLeft.apply {
                                textColor = if (isDark) AndroidColor.LTGRAY else AndroidColor.parseColor("#475569")
                                gridColor = if (isDark) AndroidColor.argb(35, 255, 255, 255) else AndroidColor.argb(35, 15, 23, 42)
                                setDrawGridLines(true)
                            }

                            axisRight.apply {
                                setDrawGridLines(false)
                            }
                        }
                    },
                    update = { chart ->
                        chartRef = chart
                        updateChartData(
                            chart = chart,
                            points = chartHistory,
                            metric = selectedMetric,
                            timeWindow = selectedTimeWindow,
                            isDark = isDark,
                            isLiveStreaming = isLiveStreaming,
                            isBrowsingHistory = isBrowsingHistory
                        )
                    }
                )

                // History indicator pill (visible when user scrolled back into history)
                if (isBrowsingHistory) {
                    Surface(
                        color = Color(0xFFE11D48).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isBrowsingHistory = false
                                chartRef?.let { chart ->
                                    if (chartHistory.isNotEmpty()) {
                                        val base = chartHistory.first().timestamp
                                        val last = (chartHistory.last().timestamp - base) / 1000f
                                        chart.moveViewToX(last)
                                        chart.invalidate()
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Return to Live",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "HISTORY • TAP FOR LIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Interactive Selected Point Inspector Banner (visible when any dot is clicked)
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedSnapshot != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val snapshot = selectedSnapshot
                    if (snapshot != null) {
                        val tempColor = if (isDark) TelemetryPalette.TempDark else TelemetryPalette.TempLight
                        val humColor = if (isDark) TelemetryPalette.HumDark else TelemetryPalette.HumLight
                        val presColor = if (isDark) TelemetryPalette.PresDark else TelemetryPalette.PresLight
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(snapshot.timestamp))

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = "Selected Point",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f°C", snapshot.temperature),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tempColor
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", snapshot.humidity),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = humColor
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.0fhPa", snapshot.pressure),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = presColor
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        selectedSnapshot = null
                                        chartRef?.highlightValues(null)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stats Summary Cards for Current Metric (colored to match active sensor)
        LiveStatsRow(
            metric = selectedMetric,
            points = chartHistory,
            vestData = vestData,
            isDark = isDark
        )
    }
}

/**
 * Visual Color Identification Legend Banner displaying distinct color swatches
 * and current readings for Temperature (Red), Humidity (Cyan), and Pressure (Purple).
 */
@Composable
fun SensorsColorIdentificationBanner(
    selectedMetric: ChartMetric,
    onMetricSelect: (ChartMetric) -> Unit,
    vestData: VestData,
    points: List<EventSnapshot>,
    isDark: Boolean
) {
    val curTemp = points.lastOrNull()?.temperature ?: vestData.environment.temperature
    val curHum = points.lastOrNull()?.humidity ?: vestData.environment.humidity
    val curPres = points.lastOrNull()?.pressure ?: vestData.environment.pressure

    val tempColor = if (isDark) TelemetryPalette.TempDark else TelemetryPalette.TempLight
    val humColor = if (isDark) TelemetryPalette.HumDark else TelemetryPalette.HumLight
    val presColor = if (isDark) TelemetryPalette.PresDark else TelemetryPalette.PresLight

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SensorIdentityChip(
            label = "Temperature",
            value = String.format(Locale.US, "%.1f°C", curTemp),
            color = tempColor,
            isSelected = selectedMetric == ChartMetric.ALL || selectedMetric == ChartMetric.TEMPERATURE,
            onClick = { onMetricSelect(if (selectedMetric == ChartMetric.TEMPERATURE) ChartMetric.ALL else ChartMetric.TEMPERATURE) },
            modifier = Modifier.weight(1f)
        )
        SensorIdentityChip(
            label = "Humidity",
            value = String.format(Locale.US, "%.1f%%", curHum),
            color = humColor,
            isSelected = selectedMetric == ChartMetric.ALL || selectedMetric == ChartMetric.HUMIDITY,
            onClick = { onMetricSelect(if (selectedMetric == ChartMetric.HUMIDITY) ChartMetric.ALL else ChartMetric.HUMIDITY) },
            modifier = Modifier.weight(1f)
        )
        SensorIdentityChip(
            label = "Pressure",
            value = String.format(Locale.US, "%.0fhPa", curPres),
            color = presColor,
            isSelected = selectedMetric == ChartMetric.ALL || selectedMetric == ChartMetric.PRESSURE,
            onClick = { onMetricSelect(if (selectedMetric == ChartMetric.PRESSURE) ChartMetric.ALL else ChartMetric.PRESSURE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SensorIdentityChip(
    label: String,
    value: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (isSelected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.8f)) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SingleChoiceMetricSegment(
    selectedMetric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    isDark: Boolean
) {
    val tempColor = if (isDark) TelemetryPalette.TempDark else TelemetryPalette.TempLight
    val humColor = if (isDark) TelemetryPalette.HumDark else TelemetryPalette.HumLight
    val presColor = if (isDark) TelemetryPalette.PresDark else TelemetryPalette.PresLight

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val options = listOf(
            Triple(ChartMetric.ALL, "All", null),
            Triple(ChartMetric.TEMPERATURE, "Temp (°C)", tempColor),
            Triple(ChartMetric.HUMIDITY, "Hum (%)", humColor),
            Triple(ChartMetric.PRESSURE, "Pres (hPa)", presColor)
        )

        options.forEach { (metric, label, dotColor) ->
            val isSelected = selectedMetric == metric
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected && dotColor != null -> dotColor.copy(alpha = 0.25f)
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        }
                    )
                    .then(
                        if (isSelected && dotColor != null) Modifier.border(1.dp, dotColor, RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = { onMetricSelected(metric) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (dotColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSelected && dotColor != null -> dotColor
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun updateChartData(
    chart: LineChart,
    points: List<EventSnapshot>,
    metric: ChartMetric,
    timeWindow: TimeWindow,
    isDark: Boolean,
    isLiveStreaming: Boolean,
    isBrowsingHistory: Boolean
) {
    if (points.isEmpty()) return

    // Base timestamp in seconds to eliminate Float32 precision truncation of large epoch timestamps
    val baseTime = points.first().timestamp
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Update X-axis Formatter dynamically relative to baseTime
    chart.xAxis.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val pointTimeMillis = baseTime + (value * 1000L).toLong()
            return sdf.format(Date(pointTimeMillis))
        }
    }

    // Dynamic X-axis label count and granularity based on selected time window
    chart.xAxis.apply {
        textColor = if (isDark) AndroidColor.LTGRAY else AndroidColor.parseColor("#475569")
        gridColor = if (isDark) AndroidColor.argb(35, 255, 255, 255) else AndroidColor.argb(35, 15, 23, 42)
        isGranularityEnabled = true
        when (timeWindow) {
            TimeWindow.SEC_4 -> {
                granularity = 1f
                setLabelCount(4, true)
            }
            TimeWindow.SEC_10 -> {
                granularity = 2f
                setLabelCount(5, true)
            }
            TimeWindow.SEC_30 -> {
                granularity = 5f
                setLabelCount(6, false)
            }
            TimeWindow.ALL -> {
                granularity = 10f
                setLabelCount(6, false)
            }
        }
    }

    // Dynamic theming
    chart.legend.textColor = if (isDark) AndroidColor.WHITE else AndroidColor.parseColor("#0F172A")
    chart.legend.textSize = 11f

    // Distinct color hex strings for chart lines and markers
    val tempHex = if (isDark) TelemetryPalette.TempHexDark else TelemetryPalette.TempHexLight
    val humHex = if (isDark) TelemetryPalette.HumHexDark else TelemetryPalette.HumHexLight
    val presHex = if (isDark) TelemetryPalette.PresHexDark else TelemetryPalette.PresHexLight
    val holeColor = if (isDark) AndroidColor.parseColor("#0F172A") else AndroidColor.WHITE

    val lineData = LineData()

    if (metric == ChartMetric.ALL) {
        // Dual Axis Mode:
        // Left Axis: Temp (°C) & Humidity (%) in 0-100 range
        // Right Axis: Pressure (hPa) in 900-1050 range
        chart.axisLeft.apply {
            isEnabled = true
            textColor = AndroidColor.parseColor(tempHex)
            gridColor = if (isDark) AndroidColor.argb(35, 255, 255, 255) else AndroidColor.argb(35, 15, 23, 42)
        }
        chart.axisRight.apply {
            isEnabled = true
            textColor = AndroidColor.parseColor(presHex) // Purple axis labels to match Pressure!
            gridColor = AndroidColor.TRANSPARENT
        }

        // 1. Temperature Dataset (RED / CORAL)
        val tempEntries = points.map { p ->
            val relSec = (p.timestamp - baseTime) / 1000f
            Entry(relSec, p.temperature.toFloat())
        }
        val tempSet = LineDataSet(tempEntries, "Temp (°C) [Red]").apply {
            axisDependency = YAxis.AxisDependency.LEFT
            color = AndroidColor.parseColor(tempHex)
            setCircleColor(AndroidColor.parseColor(tempHex))
            lineWidth = 2.5f
            circleRadius = 5f
            circleHoleRadius = 2.5f
            setDrawCircleHole(true)
            circleHoleColor = holeColor
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.12f
            setDrawFilled(true)
            fillColor = AndroidColor.parseColor(tempHex)
            fillAlpha = if (isDark) 35 else 20

            // Tap & Highlight interaction
            isHighlightEnabled = true
            highLightColor = AndroidColor.parseColor(tempHex)
            highlightLineWidth = 1.8f
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(true)
            enableDashedHighlightLine(8f, 6f, 0f)
        }
        lineData.addDataSet(tempSet)

        // 2. Humidity Dataset (CYAN / SKY BLUE)
        val humEntries = points.map { p ->
            val relSec = (p.timestamp - baseTime) / 1000f
            Entry(relSec, p.humidity.toFloat())
        }
        val humSet = LineDataSet(humEntries, "Humidity (%) [Cyan]").apply {
            axisDependency = YAxis.AxisDependency.LEFT
            color = AndroidColor.parseColor(humHex)
            setCircleColor(AndroidColor.parseColor(humHex))
            lineWidth = 2.5f
            circleRadius = 5f
            circleHoleRadius = 2.5f
            setDrawCircleHole(true)
            circleHoleColor = holeColor
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.12f
            setDrawFilled(true)
            fillColor = AndroidColor.parseColor(humHex)
            fillAlpha = if (isDark) 30 else 18

            // Tap & Highlight interaction
            isHighlightEnabled = true
            highLightColor = AndroidColor.parseColor(humHex)
            highlightLineWidth = 1.8f
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(true)
            enableDashedHighlightLine(8f, 6f, 0f)
        }
        lineData.addDataSet(humSet)

        // 3. Pressure Dataset (ROYAL PURPLE) mapped to Right Axis
        val presEntries = points.map { p ->
            val relSec = (p.timestamp - baseTime) / 1000f
            Entry(relSec, p.pressure.toFloat())
        }
        val presSet = LineDataSet(presEntries, "Pressure (hPa) [Purple]").apply {
            axisDependency = YAxis.AxisDependency.RIGHT
            color = AndroidColor.parseColor(presHex)
            setCircleColor(AndroidColor.parseColor(presHex))
            lineWidth = 2.5f
            circleRadius = 5f
            circleHoleRadius = 2.5f
            setDrawCircleHole(true)
            circleHoleColor = holeColor
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.12f
            setDrawFilled(false)

            // Tap & Highlight interaction
            isHighlightEnabled = true
            highLightColor = AndroidColor.parseColor(presHex)
            highlightLineWidth = 1.8f
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(true)
            enableDashedHighlightLine(8f, 6f, 0f)
        }
        lineData.addDataSet(presSet)

    } else {
        // Single Metric Mode: full attention and left-axis scaling
        chart.axisLeft.apply {
            isEnabled = true
            textColor = if (isDark) AndroidColor.LTGRAY else AndroidColor.parseColor("#475569")
            gridColor = if (isDark) AndroidColor.argb(35, 255, 255, 255) else AndroidColor.argb(35, 15, 23, 42)
        }
        chart.axisRight.isEnabled = false

        when (metric) {
            ChartMetric.TEMPERATURE -> {
                val tempEntries = points.map { p ->
                    val relSec = (p.timestamp - baseTime) / 1000f
                    Entry(relSec, p.temperature.toFloat())
                }
                val tempSet = LineDataSet(tempEntries, "Temperature (°C)").apply {
                    color = AndroidColor.parseColor(tempHex)
                    setCircleColor(AndroidColor.parseColor(tempHex))
                    lineWidth = 3f
                    circleRadius = 5.5f
                    circleHoleRadius = 2.8f
                    setDrawCircleHole(true)
                    circleHoleColor = holeColor
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.12f
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor(tempHex)
                    fillAlpha = if (isDark) 45 else 25

                    isHighlightEnabled = true
                    highLightColor = AndroidColor.parseColor(tempHex)
                    highlightLineWidth = 1.8f
                    setDrawHorizontalHighlightIndicator(false)
                    setDrawVerticalHighlightIndicator(true)
                    enableDashedHighlightLine(8f, 6f, 0f)
                }
                lineData.addDataSet(tempSet)
            }
            ChartMetric.HUMIDITY -> {
                val humEntries = points.map { p ->
                    val relSec = (p.timestamp - baseTime) / 1000f
                    Entry(relSec, p.humidity.toFloat())
                }
                val humSet = LineDataSet(humEntries, "Humidity (%)").apply {
                    color = AndroidColor.parseColor(humHex)
                    setCircleColor(AndroidColor.parseColor(humHex))
                    lineWidth = 3f
                    circleRadius = 5.5f
                    circleHoleRadius = 2.8f
                    setDrawCircleHole(true)
                    circleHoleColor = holeColor
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.12f
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor(humHex)
                    fillAlpha = if (isDark) 40 else 25

                    isHighlightEnabled = true
                    highLightColor = AndroidColor.parseColor(humHex)
                    highlightLineWidth = 1.8f
                    setDrawHorizontalHighlightIndicator(false)
                    setDrawVerticalHighlightIndicator(true)
                    enableDashedHighlightLine(8f, 6f, 0f)
                }
                lineData.addDataSet(humSet)
            }
            ChartMetric.PRESSURE -> {
                val presEntries = points.map { p ->
                    val relSec = (p.timestamp - baseTime) / 1000f
                    Entry(relSec, p.pressure.toFloat())
                }
                val presSet = LineDataSet(presEntries, "Pressure (hPa)").apply {
                    color = AndroidColor.parseColor(presHex)
                    setCircleColor(AndroidColor.parseColor(presHex))
                    lineWidth = 3f
                    circleRadius = 5.5f
                    circleHoleRadius = 2.8f
                    setDrawCircleHole(true)
                    circleHoleColor = holeColor
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.12f
                    setDrawFilled(true)
                    fillColor = AndroidColor.parseColor(presHex)
                    fillAlpha = if (isDark) 40 else 25

                    isHighlightEnabled = true
                    highLightColor = AndroidColor.parseColor(presHex)
                    highlightLineWidth = 1.8f
                    setDrawHorizontalHighlightIndicator(false)
                    setDrawVerticalHighlightIndicator(true)
                    enableDashedHighlightLine(8f, 6f, 0f)
                }
                lineData.addDataSet(presSet)
            }
            else -> {}
        }
    }

    chart.data = lineData
    lineData.notifyDataChanged()
    chart.notifyDataSetChanged()

    val lastRelSec = (points.last().timestamp - baseTime) / 1000f

    // Set viewport bounds according to selected time window
    chart.setVisibleXRangeMinimum(1f)
    if (timeWindow == TimeWindow.ALL) {
        chart.setVisibleXRangeMaximum(60f)
    } else {
        // Enforces exact requested span (e.g. 4 seconds: 3.15f visible range = 4 points/dots at 1 Hz)
        chart.setVisibleXRangeMaximum(timeWindow.secondsSpan + 0.15f)
    }

    // Auto-scroll to live edge if user is actively streaming and not exploring history
    if (isLiveStreaming && !isBrowsingHistory && points.isNotEmpty()) {
        chart.moveViewToX(lastRelSec)
    }

    chart.invalidate()
}

@Composable
fun LiveStatsRow(
    metric: ChartMetric,
    points: List<EventSnapshot>,
    vestData: VestData,
    isDark: Boolean
) {
    val currentVal: Double
    val unitStr: String
    val minVal: Double
    val maxVal: Double
    val avgVal: Double

    val activeColor = when (metric) {
        ChartMetric.TEMPERATURE -> if (isDark) TelemetryPalette.TempDark else TelemetryPalette.TempLight
        ChartMetric.HUMIDITY -> if (isDark) TelemetryPalette.HumDark else TelemetryPalette.HumLight
        ChartMetric.PRESSURE -> if (isDark) TelemetryPalette.PresDark else TelemetryPalette.PresLight
        ChartMetric.ALL -> MaterialTheme.colorScheme.primary
    }

    when (metric) {
        ChartMetric.TEMPERATURE, ChartMetric.ALL -> {
            currentVal = points.lastOrNull()?.temperature ?: vestData.environment.temperature
            unitStr = "°C"
            val temps = points.map { it.temperature }
            minVal = temps.minOrNull() ?: currentVal
            maxVal = temps.maxOrNull() ?: currentVal
            avgVal = if (temps.isNotEmpty()) temps.average() else currentVal
        }
        ChartMetric.HUMIDITY -> {
            currentVal = points.lastOrNull()?.humidity ?: vestData.environment.humidity
            unitStr = "%"
            val hums = points.map { it.humidity }
            minVal = hums.minOrNull() ?: currentVal
            maxVal = hums.maxOrNull() ?: currentVal
            avgVal = if (hums.isNotEmpty()) hums.average() else currentVal
        }
        ChartMetric.PRESSURE -> {
            currentVal = points.lastOrNull()?.pressure ?: vestData.environment.pressure
            unitStr = "hPa"
            val press = points.map { it.pressure }
            minVal = press.minOrNull() ?: currentVal
            maxVal = press.maxOrNull() ?: currentVal
            avgVal = if (press.isNotEmpty()) press.average() else currentVal
        }
    }

    val currentDisplay = String.format(Locale.US, "%.1f%s", currentVal, unitStr)
    val minDisplay = String.format(Locale.US, "%.1f%s", minVal, unitStr)
    val maxDisplay = String.format(Locale.US, "%.1f%s", maxVal, unitStr)
    val avgDisplay = String.format(Locale.US, "%.1f%s", avgVal, unitStr)

    val lastTimestamp = points.lastOrNull()?.timestamp ?: System.currentTimeMillis()
    val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastTimestamp))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatItemCard(label = "Current", value = currentDisplay, valueColor = activeColor, modifier = Modifier.weight(1f))
            StatItemCard(label = "Min", value = minDisplay, valueColor = activeColor, modifier = Modifier.weight(1f))
            StatItemCard(label = "Max", value = maxDisplay, valueColor = activeColor, modifier = Modifier.weight(1f))
            StatItemCard(label = "Avg", value = avgDisplay, valueColor = activeColor, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Last Point: $timeFormatted",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Text(
                text = "Buffer: ${points.size}s (1 Hz)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun StatItemCard(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}


/** Live Chart screen: vest charts as before, plus a VEST / HELMET switch for the helmet live charts. */
@Composable
fun LiveChartScreen() {
    var showHelmet by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val accent = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(false to "VEST DATA", true to "HELMET DATA").forEach { (isHelmet, label) ->
                val selected = showHelmet == isHelmet
                Surface(
                    color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else Color.Transparent),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showHelmet = isHelmet }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (showHelmet) HelmetLiveChartsContent() else VestLiveChartContent()
        }
    }
}
