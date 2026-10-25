package com.example.ui.screens

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.export.ExcelExporter
import com.example.export.PdfReportExporter
import com.example.manager.HelmetTelemetryManager
import com.example.manager.VestTelemetryManager
import com.example.model.HelmetLogEntry
import com.example.model.EventSnapshot
import com.example.model.WorkerProfile
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ReportPeriod(val title: String, val durationMillis: Long) {
    DAILY("Daily", 86400000L),
    WEEKLY("Weekly", 7L * 86400000L),
    MONTHLY("Monthly", 30L * 86400000L)
}

@Composable
fun ReportScreen(
    profile: WorkerProfile = WorkerProfile()
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.DAILY) }
    var historyData by remember { mutableStateOf<List<EventSnapshot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val primaryAccent = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary
    val emeraldAccent = if (isDark) EmeraldOnline else CommandLightEmerald
    val hazardAccent = if (isDark) HazardCrimson else CommandLightRed

    // Fetch history for period
    LaunchedEffect(selectedPeriod) {
        isLoading = true
        val startMillis = System.currentTimeMillis() - selectedPeriod.durationMillis
        VestTelemetryManager.fetchHistory(startMillis) { result ->
            historyData = result
            isLoading = false
        }
    }

    // Helmet logs for the same period (included in the PDF / Excel exports)
    var helmetLogs by remember { mutableStateOf<List<HelmetLogEntry>>(emptyList()) }
    LaunchedEffect(selectedPeriod) {
        helmetLogs = emptyList()
        HelmetTelemetryManager.fetchLogs(System.currentTimeMillis() - selectedPeriod.durationMillis) { result ->
            helmetLogs = result.getOrDefault(emptyList())
        }
    }

    // Export handler Excel
    fun triggerExport() {
        isExporting = true
        val file: File? = ExcelExporter.exportToExcel(
            context = context,
            profile = profile,
            events = historyData,
            timeframeName = selectedPeriod.title,
            helmetLogs = helmetLogs,
            helmetOffline = HelmetTelemetryManager.offlineEvents(helmetLogs)
        )
        isExporting = false

        if (file != null && file.exists()) {
            Toast.makeText(context, "Excel report generated successfully!", Toast.LENGTH_SHORT).show()
            ExcelExporter.shareExcelFile(context, file)
        } else {
            Toast.makeText(context, "Error generating Excel report", Toast.LENGTH_SHORT).show()
        }
    }

    // Export handler PDF
    fun triggerPdfExport() {
        isExportingPdf = true
        val file: File? = PdfReportExporter.exportToPdf(
            context = context,
            profile = profile,
            events = historyData,
            periodTitle = selectedPeriod.title,
            helmetLogs = helmetLogs
        )
        isExportingPdf = false

        if (file != null && file.exists()) {
            Toast.makeText(context, "PDF audit report generated successfully!", Toast.LENGTH_SHORT).show()
            PdfReportExporter.sharePdfFile(context, file)
        } else {
            Toast.makeText(context, "Error generating PDF report", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + com.example.ui.components.LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tab Header: Daily / Weekly / Monthly
        item {
            val tabAccent = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary

            TabRow(
                selectedTabIndex = selectedPeriod.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = tabAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedPeriod.ordinal]),
                        color = tabAccent
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                ReportPeriod.values().forEach { period ->
                    Tab(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        text = {
                            Text(
                                text = period.title,
                                fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        }

        // Worker Identity Static Header Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REPORT WORKER IDENTITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        Surface(
                            color = primaryAccent.copy(alpha = if (isDark) 0.15f else 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = profile.batchId,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Name: ${profile.workerName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Worker ID: ${profile.workerId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Dept: ${profile.department}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "LoRa: ${profile.loraNode}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        }
                    }
                }
            }
        }

        // Summary Metric Cards
        item {
            val avgTemp = if (historyData.isNotEmpty()) historyData.map { it.temperature }.average() else 28.0
            val avgHum = if (historyData.isNotEmpty()) historyData.map { it.humidity }.average() else 55.0
            val avgPres = if (historyData.isNotEmpty()) historyData.map { it.pressure }.average() else 1013.0
            val fallEvents = historyData.count { it.fall_detected }
            val sosEvents = historyData.count { it.sos_active }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${selectedPeriod.title.uppercase()} AGGREGATE SUMMARY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportStatBox(label = "Avg Temp", value = String.format(Locale.US, "%.1f°C", avgTemp), modifier = Modifier.weight(1f))
                    ReportStatBox(label = "Avg Hum", value = String.format(Locale.US, "%.1f%%", avgHum), modifier = Modifier.weight(1f))
                    ReportStatBox(label = "Avg Pres", value = String.format(Locale.US, "%.0fhPa", avgPres), modifier = Modifier.weight(1f))
                }

                val primaryAccent = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary
                val emeraldAccent = if (isDark) EmeraldOnline else CommandLightEmerald
                val hazardAccent = if (isDark) HazardCrimson else CommandLightRed

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportStatBox(
                        label = "Fall Incidents",
                        value = "$fallEvents",
                        color = if (fallEvents > 0) hazardAccent else emeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatBox(
                        label = "SOS Triggers",
                        value = "$sosEvents",
                        color = if (sosEvents > 0) hazardAccent else emeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatBox(
                        label = "Data Points",
                        value = "${historyData.size}",
                        color = primaryAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Historical Bar Chart
        item {
            Text(
                text = "TEMPERATURE & HAZARD FREQUENCY BAR CHART",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            val primaryAccent = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryAccent)
                    }
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            BarChart(ctx).apply {
                                description.isEnabled = false
                                setDrawGridBackground(false)
                                setDrawBarShadow(false)
                                isHighlightFullBarEnabled = false
                                legend.textColor = if (isDark) AndroidColor.WHITE else AndroidColor.parseColor("#0F172A")

                                xAxis.apply {
                                    position = XAxis.XAxisPosition.BOTTOM
                                    textColor = if (isDark) AndroidColor.LTGRAY else AndroidColor.parseColor("#475569")
                                    setDrawGridLines(false)
                                    granularity = 1f
                                    valueFormatter = object : ValueFormatter() {
                                        private val sdf = SimpleDateFormat("MM/dd", Locale.US)
                                        override fun getFormattedValue(value: Float): String {
                                            return "P${value.toInt() + 1}"
                                        }
                                    }
                                }

                                axisLeft.apply {
                                    textColor = if (isDark) AndroidColor.LTGRAY else AndroidColor.parseColor("#475569")
                                    gridColor = if (isDark) AndroidColor.argb(40, 255, 255, 255) else AndroidColor.argb(40, 15, 23, 42)
                                }
                                axisRight.isEnabled = false
                            }
                        },
                        update = { chart ->
                            updateBarChartData(chart, historyData, isDark)
                        }
                    )
                }
            }
        }

        // Smart Helmet report (heart rate, SpO2, gas, obstacle) for the same period
        item {
            HelmetReportSection(period = selectedPeriod)
        }

        // Export Action Buttons (PDF & Excel)
        item {
            val excelButtonContainer = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary
            val excelButtonContent = if (isDark) Color(0xFF002930) else MaterialTheme.colorScheme.onPrimary
            val pdfButtonContainer = if (isDark) Color(0xFFDC2626) else Color(0xFFB91C1C)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // PDF Export Button
                Button(
                    onClick = { triggerPdfExport() },
                    enabled = !isExportingPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pdfButtonContainer,
                        contentColor = Color.White
                    )
                ) {
                    if (isExportingPdf) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export PDF",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GENERATE AUDIT PDF (.PDF)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Excel Export Button
                Button(
                    onClick = { triggerExport() },
                    enabled = !isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = excelButtonContainer,
                        contentColor = excelButtonContent
                    )
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = excelButtonContent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Excel",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXPORT TO EXCEL (.XLSX)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "PDF and Excel exports include both the vest and the helmet data for the selected period.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun updateBarChartData(chart: BarChart, data: List<EventSnapshot>, isDark: Boolean) {
    if (data.isEmpty()) return

    val entries = ArrayList<BarEntry>()
    val samplePoints = if (data.size > 8) {
        val step = data.size / 8
        (0 until 8).map { data[it * step] }
    } else {
        data
    }

    samplePoints.forEachIndexed { index, snapshot ->
        entries.add(BarEntry(index.toFloat(), snapshot.temperature.toFloat()))
    }

    val barColorStr = if (isDark) "#00E5FF" else "#006D80"

    val dataSet = BarDataSet(entries, "Temperature Trend (°C)").apply {
        color = AndroidColor.parseColor(barColorStr)
        valueTextColor = if (isDark) AndroidColor.WHITE else AndroidColor.parseColor("#0F172A")
        valueTextSize = 10f
    }

    chart.data = BarData(dataSet)
    chart.notifyDataSetChanged()
    chart.invalidate()
}

@Composable
fun ReportStatBox(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
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
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}
