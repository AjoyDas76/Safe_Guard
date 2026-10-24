package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.EventSnapshot
import com.example.model.HelmetLogEntry
import com.example.model.WorkerProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReportExporter {
    private const val TAG = "PdfReportExporter"

    fun exportToPdf(
        context: Context,
        profile: WorkerProfile,
        events: List<EventSnapshot>,
        periodTitle: String,
        helmetLogs: List<HelmetLogEntry> = emptyList()
    ): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595 x 842 points)
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Paints
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)

        try {
            // Header Background Bar (Navy / Dark Sci-Fi Command)
            paintBg.color = Color.parseColor("#0A192F")
            canvas.drawRect(0f, 0f, 595f, 110f, paintBg)

            // Accent Line (Neon Cyan)
            paintBg.color = Color.parseColor("#00E5FF")
            canvas.drawRect(0f, 108f, 595f, 112f, paintBg)

            // Header Title
            paintText.color = Color.WHITE
            paintText.textSize = 20f
            paintText.isFakeBoldText = true
            canvas.drawText("SAFEGUARD — SAFETY AUDIT REPORT", 30f, 45f, paintText)

            paintText.textSize = 11f
            paintText.color = Color.parseColor("#00E5FF")
            canvas.drawText("Smart Safety Vest & Helmet Telemetry and Incident Audit • Period: $periodTitle", 30f, 65f, paintText)

            paintText.color = Color.LTGRAY
            paintText.textSize = 9f
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).format(Date())
            canvas.drawText("Generated: $dateStr • System: LoRa ESP32 Hub • Helmet: Wi-Fi / GSM", 30f, 85f, paintText)

            // Worker Info Box (Card)
            paintBg.color = Color.parseColor("#F1F5F9")
            val cardRect = RectF(30f, 130f, 565f, 210f)
            canvas.drawRoundRect(cardRect, 8f, 8f, paintBg)

            paintText.color = Color.parseColor("#0F172A")
            paintText.textSize = 12f
            paintText.isFakeBoldText = true
            canvas.drawText("MONITORED WORKER PROFILE", 45f, 152f, paintText)

            paintText.isFakeBoldText = false
            paintText.textSize = 10f
            paintText.color = Color.parseColor("#334155")
            canvas.drawText("Name: ${profile.workerName} (ID: ${profile.workerId})", 45f, 172f, paintText)
            canvas.drawText("Dept: ${profile.department}", 45f, 188f, paintText)
            canvas.drawText("Assigned Vest: ${profile.assignedVestId} (${profile.loraFrequency})", 45f, 202f, paintText)

            canvas.drawText("Phone: ${profile.phone}", 320f, 172f, paintText)
            canvas.drawText("Blood Group: ${profile.bloodGroup}", 320f, 188f, paintText)
            canvas.drawText("Supervisor: ${profile.secondaryContactName} (${profile.secondaryContactPhone})", 320f, 202f, paintText)

            // Statistical Metrics Box
            var yPos = 235f
            paintText.isFakeBoldText = true
            paintText.textSize = 12f
            paintText.color = Color.parseColor("#0A192F")
            canvas.drawText("VEST SUMMARY & SENSOR AGGREGATES", 30f, yPos, paintText)

            yPos += 15f
            val avgTemp = if (events.isNotEmpty()) events.map { it.temperature }.average() else 0.0
            val maxTemp = if (events.isNotEmpty()) events.maxOfOrNull { it.temperature } ?: 0.0 else 0.0
            val avgHum = if (events.isNotEmpty()) events.map { it.humidity }.average() else 0.0
            val fallIncidents = events.count { it.fall_detected }
            val sosIncidents = events.count { it.sos_active }

            // 4 summary blocks
            drawSummaryMetric(canvas, 30f, yPos, 125f, "AVG TEMP", String.format(Locale.US, "%.1f°C", avgTemp), "#0284C7")
            drawSummaryMetric(canvas, 165f, yPos, 125f, "MAX TEMP", String.format(Locale.US, "%.1f°C", maxTemp), if (maxTemp > 45.0) "#DC2626" else "#EA580C")
            drawSummaryMetric(canvas, 300f, yPos, 125f, "AVG HUMIDITY", String.format(Locale.US, "%.1f%%", avgHum), "#0D9488")
            drawSummaryMetric(canvas, 435f, yPos, 130f, "CRITICAL ALARMS", "$fallIncidents Falls / $sosIncidents SOS", if (fallIncidents + sosIncidents > 0) "#DC2626" else "#16A34A")

            // Events Table
            yPos += 75f
            paintText.isFakeBoldText = true
            paintText.textSize = 12f
            paintText.color = Color.parseColor("#0A192F")
            canvas.drawText("VEST TELEMETRY LOGS (LATEST ENTRIES)", 30f, yPos, paintText)

            yPos += 15f
            // Table Header Bar
            paintBg.color = Color.parseColor("#1E293B")
            canvas.drawRect(30f, yPos, 565f, yPos + 22f, paintBg)

            paintText.color = Color.WHITE
            paintText.textSize = 9f
            paintText.isFakeBoldText = true
            canvas.drawText("TIME", 40f, yPos + 15f, paintText)
            canvas.drawText("TEMP", 110f, yPos + 15f, paintText)
            canvas.drawText("HUMIDITY", 170f, yPos + 15f, paintText)
            canvas.drawText("PRESSURE", 240f, yPos + 15f, paintText)
            canvas.drawText("MOTION", 310f, yPos + 15f, paintText)
            canvas.drawText("FALL / SOS", 380f, yPos + 15f, paintText)
            canvas.drawText("ALERTS", 460f, yPos + 15f, paintText)

            yPos += 22f

            // Table Rows
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val sampleEvents = events.take(18) // Fits comfortably on page 1

            sampleEvents.forEachIndexed { index, ev ->
                val isAlt = index % 2 == 1
                paintBg.color = if (isAlt) Color.parseColor("#F8FAFC") else Color.WHITE
                if (ev.fall_detected || ev.sos_active) {
                    paintBg.color = Color.parseColor("#FEE2E2") // Light red alert row
                }
                canvas.drawRect(30f, yPos, 565f, yPos + 20f, paintBg)

                paintText.isFakeBoldText = false
                paintText.color = Color.parseColor("#1E293B")
                paintText.textSize = 8.5f

                val timeStr = timeFmt.format(Date(ev.timestamp))
                canvas.drawText(timeStr, 40f, yPos + 14f, paintText)
                canvas.drawText(String.format(Locale.US, "%.1f°C", ev.temperature), 110f, yPos + 14f, paintText)
                canvas.drawText(String.format(Locale.US, "%.1f%%", ev.humidity), 170f, yPos + 14f, paintText)
                canvas.drawText(String.format(Locale.US, "%.0f hPa", ev.pressure), 240f, yPos + 14f, paintText)
                canvas.drawText(ev.motion_state, 310f, yPos + 14f, paintText)

                val statusFlag = when {
                    ev.fall_detected && ev.sos_active -> "FALL+SOS!"
                    ev.fall_detected -> "FALL!"
                    ev.sos_active -> "SOS!"
                    else -> "Normal"
                }
                if (ev.fall_detected || ev.sos_active) {
                    paintText.color = Color.parseColor("#DC2626")
                    paintText.isFakeBoldText = true
                } else {
                    paintText.color = Color.parseColor("#16A34A")
                }
                canvas.drawText(statusFlag, 380f, yPos + 14f, paintText)

                paintText.color = Color.parseColor("#64748B")
                paintText.isFakeBoldText = false
                val alertStr = if (ev.activeAlerts.isNotEmpty()) ev.activeAlerts.first().take(18) else "None"
                canvas.drawText(alertStr, 460f, yPos + 14f, paintText)

                yPos += 20f
            }

            // Footer
            paintBg.color = Color.parseColor("#E2E8F0")
            canvas.drawRect(30f, 800f, 565f, 801f, paintBg)

            paintText.textSize = 8f
            paintText.color = Color.parseColor("#64748B")
            canvas.drawText("CONFIDENTIAL • SAFEGUARD VEST & HELMET TELEMETRY • COMPLIANT WITH INDUSTRIAL OCCUPATIONAL SAFETY", 30f, 818f, paintText)
            canvas.drawText("Page 1 of 2", 520f, 818f, paintText)

            pdfDocument.finishPage(page)

            // ---------- Page 2: Smart Helmet ----------
            val page2 = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
            drawHelmetPage(page2.canvas, helmetLogs, periodTitle, profile.workerId)
            pdfDocument.finishPage(page2)

            // Save to file
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(reportsDir, "SafeGuard_Safety_Report_${profile.workerId}_$timestamp.pdf")
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            Log.i(TAG, "PDF generated at: ${file.absolutePath}, size: ${file.length()} bytes")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF report: ${e.message}", e)
            pdfDocument.close()
            return null
        }
    }

    private fun drawHelmetPage(canvas: Canvas, logs: List<HelmetLogEntry>, periodTitle: String, workerId: String) {
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG)

        paintBg.color = Color.parseColor("#0A192F")
        canvas.drawRect(0f, 0f, 595f, 90f, paintBg)
        paintBg.color = Color.parseColor("#00E5FF")
        canvas.drawRect(0f, 88f, 595f, 92f, paintBg)

        paintText.color = Color.WHITE
        paintText.textSize = 18f
        paintText.isFakeBoldText = true
        canvas.drawText("SAFEGUARD — SMART HELMET REPORT", 30f, 40f, paintText)
        paintText.textSize = 10f
        paintText.isFakeBoldText = false
        paintText.color = Color.parseColor("#00E5FF")
        canvas.drawText("Heart Rate • SpO2 • Gas Level • Obstacle • Period: $periodTitle • Helmet: HELMET-001 • Worker: $workerId", 30f, 62f, paintText)

        var y = 125f
        paintText.isFakeBoldText = true
        paintText.textSize = 12f
        paintText.color = Color.parseColor("#0A192F")
        canvas.drawText("HELMET SUMMARY & SENSOR AGGREGATES", 30f, y, paintText)
        y += 15f

        val hrs = logs.map { it.reading.heartRate }.filter { it > 0 }
        val sps = logs.map { it.reading.spo2 }.filter { it > 0 }
        val peakGas = logs.maxOfOrNull { it.reading.gasLevel } ?: 0.0
        val obst = logs.count { it.reading.anyObstacle }
        val gasN = logs.count { it.reading.gasFlag }
        val vit = logs.count { it.reading.hrBad || it.reading.spo2Bad }

        drawSummaryMetric(canvas, 30f, y, 125f, "AVG HEART RATE", if (hrs.isEmpty()) "--" else String.format(Locale.US, "%.0f bpm", hrs.average()), "#E11D48")
        drawSummaryMetric(canvas, 165f, y, 125f, "MIN SpO2", if (sps.isEmpty()) "--" else String.format(Locale.US, "%.0f%%", sps.min()), "#0369A1")
        drawSummaryMetric(canvas, 300f, y, 125f, "PEAK GAS LEVEL", if (logs.isEmpty()) "--" else String.format(Locale.US, "%.0f", peakGas), "#D97706")
        drawSummaryMetric(canvas, 435f, y, 130f, "HAZARD SNAPSHOTS", "$obst Obst / $gasN Gas / $vit Vitals", if (obst + gasN + vit > 0) "#DC2626" else "#16A34A")

        y += 75f
        paintText.isFakeBoldText = true
        paintText.textSize = 12f
        paintText.color = Color.parseColor("#0A192F")
        canvas.drawText("HELMET TELEMETRY LOGS (LATEST ENTRIES)", 30f, y, paintText)
        y += 15f

        paintBg.color = Color.parseColor("#1E293B")
        canvas.drawRect(30f, y, 565f, y + 22f, paintBg)
        paintText.color = Color.WHITE
        paintText.textSize = 9f
        canvas.drawText("TIME", 40f, y + 15f, paintText)
        canvas.drawText("HR", 105f, y + 15f, paintText)
        canvas.drawText("SpO2", 150f, y + 15f, paintText)
        canvas.drawText("GAS", 200f, y + 15f, paintText)
        canvas.drawText("L / F / R (cm)", 250f, y + 15f, paintText)
        canvas.drawText("STATUS", 360f, y + 15f, paintText)
        canvas.drawText("ALERTS", 440f, y + 15f, paintText)
        y += 22f

        if (logs.isEmpty()) {
            paintText.isFakeBoldText = false
            paintText.color = Color.parseColor("#64748B")
            paintText.textSize = 10f
            canvas.drawText("No helmet data was recorded for this period.", 40f, y + 20f, paintText)
        } else {
            val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)
            fun d(v: Int) = if (v >= 999) "--" else "$v"
            logs.takeLast(22).reversed().forEachIndexed { i, e ->
                val r = e.reading
                val alerts = mutableListOf<String>()
                if (r.anyObstacle) alerts.add("Obstacle")
                if (r.gasFlag) alerts.add("Gas")
                if (r.hrBad) alerts.add("HR")
                if (r.spo2Bad) alerts.add("SpO2")
                paintBg.color = if (alerts.isNotEmpty()) Color.parseColor("#FEE2E2") else if (i % 2 == 1) Color.parseColor("#F8FAFC") else Color.WHITE
                canvas.drawRect(30f, y, 565f, y + 20f, paintBg)

                paintText.isFakeBoldText = false
                paintText.textSize = 8.5f
                paintText.color = Color.parseColor("#1E293B")
                canvas.drawText(fmt.format(Date(e.timestamp)), 40f, y + 14f, paintText)
                canvas.drawText(if (r.heartRate > 0) "${r.heartRate.toInt()}" else "--", 105f, y + 14f, paintText)
                canvas.drawText(if (r.spo2 > 0) "${r.spo2.toInt()}%" else "--", 150f, y + 14f, paintText)
                canvas.drawText("${r.gasLevel.toInt()}", 200f, y + 14f, paintText)
                canvas.drawText("${d(r.distLeft)} / ${d(r.distFront)} / ${d(r.distRight)}", 250f, y + 14f, paintText)
                if (alerts.isNotEmpty()) {
                    paintText.color = Color.parseColor("#DC2626"); paintText.isFakeBoldText = true
                } else paintText.color = Color.parseColor("#16A34A")
                canvas.drawText(if (alerts.isNotEmpty()) "ALERT" else "Normal", 360f, y + 14f, paintText)
                paintText.color = Color.parseColor("#64748B"); paintText.isFakeBoldText = false
                canvas.drawText(if (alerts.isEmpty()) "None" else alerts.joinToString(",").take(18), 440f, y + 14f, paintText)
                y += 20f
            }
        }

        paintBg.color = Color.parseColor("#E2E8F0")
        canvas.drawRect(30f, 800f, 565f, 801f, paintBg)
        paintText.textSize = 8f
        paintText.isFakeBoldText = false
        paintText.color = Color.parseColor("#64748B")
        canvas.drawText("CONFIDENTIAL • SAFEGUARD SMART HELMET TELEMETRY", 30f, 818f, paintText)
        canvas.drawText("Page 2 of 2", 520f, 818f, paintText)
    }

    private fun drawSummaryMetric(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        title: String,
        value: String,
        colorHex: String
    ) {
        val rect = RectF(x, y, x + width, y + 50f)
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
        }
        val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            color = Color.parseColor(colorHex)
        }
        canvas.drawRoundRect(rect, 6f, 6f, paintBg)
        canvas.drawRoundRect(rect, 6f, 6f, paintBorder)

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG)
        paintText.textSize = 8.5f
        paintText.color = Color.parseColor("#64748B")
        paintText.isFakeBoldText = true
        canvas.drawText(title, x + 8f, y + 18f, paintText)

        paintText.textSize = 12f
        paintText.color = Color.parseColor(colorHex)
        paintText.isFakeBoldText = true
        canvas.drawText(value, x + 8f, y + 38f, paintText)
    }

    fun sharePdfFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SafeGuard Safety Audit PDF - ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Attached is the official SafeGuard Smart Safety Vest and Helmet incident audit report (.pdf).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Share Safety Report (.pdf)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
