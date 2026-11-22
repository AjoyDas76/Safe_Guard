package com.example.export

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.HelmetLogEntry
import com.example.model.HelmetOfflineEvent
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exports the helmet report (same columns as the web dashboard) as .xlsx. */
object HelmetExcelExporter {
    private const val TAG = "HelmetExcelExporter"

    fun export(
        context: Context,
        entries: List<HelmetLogEntry>,
        offline: List<HelmetOfflineEvent>,
        rangeName: String,
        helmetId: String,
        workerId: String
    ): File? {
        return try {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "helmet1_${rangeName.lowercase()}_report_$stamp.xlsx")
            val hasOffline = offline.isNotEmpty()

            ZipOutputStream(FileOutputStream(file)).use { zos ->
                put(zos, "[Content_Types].xml", contentTypes(hasOffline))
                put(zos, "_rels/.rels", rootRels())
                put(zos, "xl/_rels/workbook.xml.rels", workbookRels(hasOffline))
                put(zos, "xl/workbook.xml", workbook(rangeName, hasOffline))
                put(zos, "xl/styles.xml", styles())
                put(zos, "xl/worksheets/sheet1.xml", dataSheet(entries, rangeName, helmetId, workerId))
                if (hasOffline) put(zos, "xl/worksheets/sheet2.xml", offlineSheet(offline))
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Helmet export failed: ${e.message}", e)
            null
        }
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Smart Helmet Report - ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Attached is the Smart Safety Helmet report (.xlsx).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share Helmet Report (.xlsx)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun put(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun col(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        }
        return sb.toString()
    }

    private fun str(ref: String, text: String, style: Int = 0) =
        "<c r=\"$ref\" t=\"inlineStr\"${if (style > 0) " s=\"$style\"" else ""}><is><t>${esc(text)}</t></is></c>"

    private fun numCell(ref: String, v: Number) = "<c r=\"$ref\"><v>$v</v></c>"

    private fun row(r: Int, cells: List<String>) = "<row r=\"$r\">${cells.joinToString("")}</row>\n"

    internal fun dataSheet(entries: List<HelmetLogEntry>, range: String, helmetId: String, workerId: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/><col min=\"2\" max=\"11\" width=\"16\" customWidth=\"1\"/></cols><sheetData>\n")
        var r = 1
        sb.append(row(r++, listOf(str("A1", "SMART SAFETY HELMET - REPORT (${range.uppercase()})", 2))))
        sb.append(row(r, listOf(str("A$r", "Helmet ID"), str("B$r", helmetId)))); r++
        sb.append(row(r, listOf(str("A$r", "Worker ID"), str("B$r", workerId)))); r++
        sb.append(row(r, listOf(str("A$r", "Generated At"), str("B$r", fmt.format(Date()))))); r++
        sb.append(row(r, listOf(str("A$r", "Records"), numCell("B$r", entries.size)))); r++
        r++
        val headers = listOf(
            "Timestamp", "Heart Rate (bpm)", "SpO2 (%)", "Gas Level", "Gas Baseline",
            "Dist Left (cm)", "Dist Front (cm)", "Dist Right (cm)", "Obstacle", "Gas Alert", "Vitals Alert"
        )
        sb.append(row(r, headers.mapIndexed { i, h -> str("${col(i)}$r", h, 1) })); r++
        for (e in entries) {
            val p = e.reading
            val cells = mutableListOf<String>()
            cells.add(str("A$r", fmt.format(Date(e.timestamp))))
            cells.add(if (p.heartRate > 0) numCell("B$r", p.heartRate) else str("B$r", ""))
            cells.add(if (p.spo2 > 0) numCell("C$r", p.spo2) else str("C$r", ""))
            cells.add(numCell("D$r", p.gasLevel))
            cells.add(numCell("E$r", p.gasBaseline))
            cells.add(numCell("F$r", p.distLeft))
            cells.add(numCell("G$r", p.distFront))
            cells.add(numCell("H$r", p.distRight))
            cells.add(str("I$r", if (p.anyObstacle) "YES" else "NO"))
            cells.add(str("J$r", if (p.gasFlag) "YES" else "NO"))
            cells.add(str("K$r", if (p.hrBad || p.spo2Bad) "YES" else "NO"))
            sb.append(row(r, cells)); r++
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun duration(ms: Long): String {
        val m = Math.round(ms / 60000.0)
        return when {
            m < 1 -> "< 1 min"
            m < 60 -> "$m min"
            else -> "${m / 60} h ${m % 60} min"
        }
    }

    internal fun offlineSheet(events: List<HelmetOfflineEvent>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"3\" width=\"22\" customWidth=\"1\"/></cols><sheetData>\n")
        sb.append(row(1, listOf(str("A1", "Offline Since", 1), str("B1", "Reconnected / Status", 1), str("C1", "Duration", 1))))
        var r = 2
        for (e in events) {
            sb.append(
                row(
                    r, listOf(
                        str("A$r", fmt.format(Date(e.from))),
                        str("B$r", if (e.ongoing) "Still offline" else fmt.format(Date(e.to))),
                        str("C$r", duration(e.durationMs))
                    )
                )
            )
            r++
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun contentTypes(offline: Boolean) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
${if (offline) "  <Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>\n" else ""}  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookRels(offline: Boolean) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
${if (offline) "  <Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>\n" else ""}  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun workbook(range: String, offline: Boolean) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="helmet ${esc(range.lowercase())}" sheetId="1" r:id="rId1"/>
${if (offline) "    <sheet name=\"offline events\" sheetId=\"2\" r:id=\"rId3\"/>\n" else ""}  </sheets>
</workbook>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><name val="Calibri"/><sz val="11"/></font>
    <font><b/><color rgb="FFFFFFFF"/><name val="Calibri"/><sz val="11"/></font>
    <font><b/><color rgb="FF003366"/><name val="Calibri"/><sz val="14"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF006699"/></patternFill></fill>
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
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
}
