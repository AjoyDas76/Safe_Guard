package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.EventSnapshot
import com.example.model.HelmetLogEntry
import com.example.model.HelmetOfflineEvent
import com.example.model.WorkerProfile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExporter {
    private const val TAG = "ExcelExporter"

    fun exportToExcel(
        context: Context,
        profile: WorkerProfile = WorkerProfile(),
        events: List<EventSnapshot>,
        timeframeName: String = "Telemetry Report",
        helmetLogs: List<HelmetLogEntry> = emptyList(),
        helmetOffline: List<HelmetOfflineEvent> = emptyList()
    ): File? {
        try {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(reportsDir, "SafeGuard_Report_${profile.workerId}_$timestamp.xlsx")

            val fos = FileOutputStream(file)
            val zos = ZipOutputStream(fos)

            // 1. [Content_Types].xml
            val extraSheets = if (helmetLogs.isNotEmpty() || helmetOffline.isNotEmpty()) (if (helmetOffline.isNotEmpty()) 2 else 1) else 0
            addZipEntry(zos, "[Content_Types].xml", getContentTypesXml(extraSheets))

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", getRootRelsXml())

            // 3. xl/_rels/workbook.xml.rels
            addZipEntry(zos, "xl/_rels/workbook.xml.rels", getWorkbookRelsXml(extraSheets))

            // 4. xl/workbook.xml
            addZipEntry(zos, "xl/workbook.xml", getWorkbookXml(extraSheets))

            // 5. xl/styles.xml
            addZipEntry(zos, "xl/styles.xml", getStylesXml())

            // 6. xl/worksheets/sheet1.xml
            val sheetXml = buildSheetXml(profile, events, timeframeName)
            addZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml)

            // Helmet data (same columns as the web dashboard helmet report)
            if (extraSheets >= 1) {
                addZipEntry(zos, "xl/worksheets/sheet2.xml",
                    HelmetExcelExporter.dataSheet(helmetLogs, timeframeName, com.example.manager.HelmetTelemetryManager.HELMET_ID, profile.workerId))
            }
            if (extraSheets >= 2) {
                addZipEntry(zos, "xl/worksheets/sheet3.xml", HelmetExcelExporter.offlineSheet(helmetOffline))
            }

            zos.close()
            fos.close()

            Log.d(TAG, "Successfully created XLSX at: ${file.absolutePath}, size: ${file.length()} bytes")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting Excel file: ${e.message}", e)
            return null
        }
    }

    fun shareExcelFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SafeGuard Vest & Helmet Report - ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Attached is the SafeGuard Smart Safety Vest and Helmet telemetry report (.xlsx).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Share SafeGuard Report (.xlsx)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun addZipEntry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
    }

    private fun getContentTypesXml(extra: Int): String {
        val extraXml = (2..(1 + extra)).joinToString("") { "  <Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
$extraXml  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
    }

    private fun getRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun getWorkbookRelsXml(extra: Int): String {
        val extraXml = (2..(1 + extra)).joinToString("") { "  <Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>\n" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
$extraXml
</Relationships>"""
    }

    private fun getWorkbookXml(extra: Int): String {
        val names = listOf("Helmet Telemetry", "Helmet Offline")
        val extraXml = (2..(1 + extra)).joinToString("") { "    <sheet name=\"${names[it - 2]}\" sheetId=\"$it\" r:id=\"rId${it + 1}\"/>\n" }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Vest Telemetry" sheetId="1" r:id="rId1"/>
$extraXml  </sheets>
</workbook>"""
    }

    private fun getStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><name val="Calibri"/><sz val="11"/></font>
    <font><b/><color rgb="FFFFFFFF"/><name val="Calibri"/><sz val="11"/></font>
    <font><b/><color rgb="FF003366"/><name val="Calibri"/><sz val="14"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF006699"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE6F2F8"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border>
      <left style="thin"><color rgb="FFB0C4DE"/></left>
      <right style="thin"><color rgb="FFB0C4DE"/></right>
      <top style="thin"><color rgb="FFB0C4DE"/></top>
      <bottom style="thin"><color rgb="FFB0C4DE"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildSheetXml(
        profile: WorkerProfile,
        events: List<EventSnapshot>,
        timeframeName: String
    ): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val nowStr = dateFormat.format(Date())

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
        sb.append("  <sheetData>\n")

        var rowIndex = 1

        // Row 1: Title
        sb.append("    <row r=\"$rowIndex\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\" s=\"2\"><is><t>SAFEGUARD - SAFETY VEST &amp; HELMET TELEMETRY REPORT ($timeframeName)</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex++

        // Row 2: Worker Identity Header
        sb.append("    <row r=\"$rowIndex\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>Worker ID: ${escapeXml(profile.workerId)}</t></is></c>\n")
        sb.append("      <c r=\"C$rowIndex\" t=\"inlineStr\"><is><t>Worker Name: ${escapeXml(profile.workerName)}</t></is></c>\n")
        sb.append("      <c r=\"E$rowIndex\" t=\"inlineStr\"><is><t>Batch ID: ${escapeXml(profile.batchId)}</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex++

        // Row 3: Department & Export Date
        sb.append("    <row r=\"$rowIndex\">\n")
        sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>Department: ${escapeXml(profile.department)}</t></is></c>\n")
        sb.append("      <c r=\"C$rowIndex\" t=\"inlineStr\"><is><t>LoRa Node: ${escapeXml(profile.loraNode)} (${escapeXml(profile.loraFrequency)})</t></is></c>\n")
        sb.append("      <c r=\"E$rowIndex\" t=\"inlineStr\"><is><t>Exported At: $nowStr</t></is></c>\n")
        sb.append("    </row>\n")
        rowIndex++

        // Row 4: Blank
        rowIndex++

        // Row 5: Table Column Headers (Styled with s="1" - white text on tech blue)
        sb.append("    <row r=\"$rowIndex\">\n")
        val headers = listOf(
            "A" to "Timestamp",
            "B" to "Temperature (°C)",
            "C" to "Humidity (%)",
            "D" to "Pressure (hPa)",
            "E" to "Motion State",
            "F" to "Fall Detected",
            "G" to "SOS Active",
            "H" to "Hazard Status"
        )
        for ((col, hText) in headers) {
            sb.append("      <c r=\"$col$rowIndex\" t=\"inlineStr\" s=\"1\"><is><t>${escapeXml(hText)}</t></is></c>\n")
        }
        sb.append("    </row>\n")
        rowIndex++

        // Rows 6+: Data rows
        for (event in events) {
            val dateFormatted = dateFormat.format(Date(event.timestamp))
            val isOffline = !event.isOnline || event.motion_state.equals("OFFLINE", ignoreCase = true)
            val hazardStatus = if (isOffline) {
                "OFFLINE"
            } else if (event.fall_detected || event.sos_active) {
                "EMERGENCY"
            } else if (event.temperature > 45 || event.temperature < 15 || event.humidity > 90 || event.humidity < 20 || event.pressure > 1050 || event.pressure < 950) {
                "WARNING"
            } else {
                "NORMAL"
            }

            sb.append("    <row r=\"$rowIndex\">\n")
            sb.append("      <c r=\"A$rowIndex\" t=\"inlineStr\"><is><t>$dateFormatted</t></is></c>\n")
            if (isOffline) {
                sb.append("      <c r=\"B$rowIndex\" t=\"inlineStr\"><is><t>---</t></is></c>\n")
                sb.append("      <c r=\"C$rowIndex\" t=\"inlineStr\"><is><t>---</t></is></c>\n")
                sb.append("      <c r=\"D$rowIndex\" t=\"inlineStr\"><is><t>---</t></is></c>\n")
            } else {
                sb.append("      <c r=\"B$rowIndex\"><v>${String.format(Locale.US, "%.1f", event.temperature)}</v></c>\n")
                sb.append("      <c r=\"C$rowIndex\"><v>${String.format(Locale.US, "%.1f", event.humidity)}</v></c>\n")
                sb.append("      <c r=\"D$rowIndex\"><v>${String.format(Locale.US, "%.1f", event.pressure)}</v></c>\n")
            }
            sb.append("      <c r=\"E$rowIndex\" t=\"inlineStr\"><is><t>${if (isOffline) "OFFLINE" else escapeXml(event.motion_state)}</t></is></c>\n")
            sb.append("      <c r=\"F$rowIndex\" t=\"inlineStr\"><is><t>${if (isOffline) "---" else if (event.fall_detected) "YES (CRITICAL)" else "NO"}</t></is></c>\n")
            sb.append("      <c r=\"G$rowIndex\" t=\"inlineStr\"><is><t>${if (isOffline) "---" else if (event.sos_active) "ACTIVE (EMERGENCY)" else "NORMAL"}</t></is></c>\n")
            sb.append("      <c r=\"H$rowIndex\" t=\"inlineStr\"><is><t>$hazardStatus</t></is></c>\n")
            sb.append("    </row>\n")
            rowIndex++
        }

        sb.append("  </sheetData>\n")
        sb.append("</worksheet>")
        return sb.toString()
    }
}
