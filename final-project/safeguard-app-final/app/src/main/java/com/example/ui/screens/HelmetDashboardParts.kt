package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.manager.HelmetTelemetryManager
import com.example.model.*
import com.example.ui.components.webGlass

/** Helmet pieces used inside the Dashboard so vest + helmet appear on one screen. */

private fun glassBrush(isDark: Boolean) = com.example.ui.components.webGlassBrush(isDark)

// ---------------------------------------------------------
// Heart rate / SpO2 / Gas / Obstacle cards
// ---------------------------------------------------------
private val alertRedFor = { dark: Boolean -> if (dark) Color(0xFFFF334B) else Color(0xFFDC2626) }

@Composable
fun HelmetHrTile(helmet: HelmetData, isDark: Boolean, modifier: Modifier = Modifier) {
    val p = helmet.reading
    val warn = helmet.isOnline && helmet.hrConfirmedBad
    val has = helmet.isOnline && p.heartRate > 0
    FuturisticMetricCard(
        title = "HEART RATE",
        value = if (has) "${p.heartRate.toInt()}" else "--",
        unit = "bpm",
        icon = Icons.Default.Favorite,
        borderColor = Color(0xFFFF3366),
        status = if (!has) "Waiting..." else if (warn) "Abnormal" else "Normal",
        range = "Range: ${HelmetThresholds.HR_MIN} - ${HelmetThresholds.HR_MAX} bpm",
        sparkValue = if (has) p.heartRate.toFloat() else null,
        isWarning = warn, isDark = isDark, modifier = modifier
    )
}

@Composable
fun HelmetSpo2Tile(helmet: HelmetData, isDark: Boolean, modifier: Modifier = Modifier) {
    val p = helmet.reading
    val warn = helmet.isOnline && helmet.spo2ConfirmedBad
    val has = helmet.isOnline && p.spo2 > 0
    FuturisticMetricCard(
        title = "BLOOD OXYGEN",
        value = if (has) "${p.spo2.toInt()}" else "--",
        unit = "%",
        icon = Icons.Default.Air,
        borderColor = if (isDark) Color(0xFF5B9BFF) else Color(0xFF2F6FD6),
        status = if (!has) "Waiting..." else if (warn) "Low SpO₂" else "Normal",
        range = "Min: ${HelmetThresholds.SPO2_MIN}%",
        sparkValue = if (has) p.spo2.toFloat() else null,
        isWarning = warn, isDark = isDark, modifier = modifier
    )
}

@Composable
fun HelmetGasTile(helmet: HelmetData, isDark: Boolean, modifier: Modifier = Modifier) {
    val p = helmet.reading
    val warn = helmet.isOnline && p.gasFlag
    FuturisticMetricCard(
        title = "GAS LEVEL",
        value = if (helmet.isOnline) "${p.gasLevel.toInt()}" else "--",
        unit = "ADC",
        icon = Icons.Default.Cloud,
        borderColor = Color(0xFFF59E0B),
        status = if (!helmet.isOnline) "Waiting..." else if (warn) "Gas detected" else "Normal",
        sparkValue = if (helmet.isOnline) p.gasLevel.toFloat() else null,
        isWarning = warn, isDark = isDark, modifier = modifier
    )
}

@Composable
fun HelmetObstacleTile(helmet: HelmetData, isDark: Boolean, modifier: Modifier = Modifier) {
    val warn = helmet.isOnline && helmet.reading.anyObstacle
    FuturisticMetricCard(
        title = "OBSTACLE",
        value = if (!helmet.isOnline) "--" else if (warn) "ALERT" else "SAFE",
        icon = Icons.Default.Sensors,
        borderColor = Color(0xFFEAB308),
        status = if (!helmet.isOnline) "Waiting..." else if (warn) "Obstacle near" else "Path clear",
        range = "Limit: ${HelmetThresholds.OBST_CM} cm",
        isWarning = warn, isDark = isDark, modifier = modifier
    )
}

// ---------------------------------------------------------
// Left / Front / Right proximity bars
// ---------------------------------------------------------
@Composable
fun HelmetProximityCard(helmet: HelmetData, isDark: Boolean) {
    val p = helmet.reading
    val on = helmet.isOnline
    val titleColor = if (isDark) Color(0xFF5B9BFF) else Color(0xFF2F6FD6)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val red = if (isDark) Color(0xFFFF334B) else Color(0xFFDC2626)
    val green = if (isDark) Color(0xFF00E676) else Color(0xFF059669)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .webGlass(isDark, accent = if (on && p.anyObstacle) red else null, glow = if (isDark) Color(0xFF5B9BFF) else Color(0xFF2F6FD6))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("OBSTACLE PROXIMITY (ULTRASONIC)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = titleColor, letterSpacing = 0.8.sp)

            HelmetSide.values().forEach { side ->
                val d = p.dist(side)
                val hit = on && p.obst(side)
                val clear = d >= HelmetThresholds.NO_ECHO
                val fill = if (!on || clear) 0f else (1f - d.toFloat() / HelmetThresholds.PROX_SCALE_CM).coerceIn(0.03f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(side.label.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Text(if (!on) "--" else "$d cm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (hit) red else textColor)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor.copy(alpha = 0.10f))
                    ) {
                        if (fill > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fill)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (hit) red else green)
                            )
                        }
                    }
                }
            }
            Text(
                "Alert when an object is closer than ${HelmetThresholds.OBST_CM} cm",
                fontSize = 10.sp, color = subColor
            )
        }
    }
}

// ---------------------------------------------------------
// Helmet block inside "DIAGNOSTICS & SYSTEM LINK"
// ---------------------------------------------------------
@Composable
fun HelmetDiagnosticsSection(helmet: HelmetData, isDark: Boolean) {
    val on = helmet.isOnline
    val p = helmet.reading
    val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val ok = if (isDark) Color(0xFF00E676) else Color(0xFF059669)
    val bad = if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
    val warn = if (isDark) Color(0xFFFF9100) else Color(0xFFB45309)
    val idle = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val linkColor = if (on) ok else bad

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Divider(color = subtextColor.copy(alpha = 0.3f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Engineering, contentDescription = "Helmet", tint = linkColor, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        text = when {
                            helmet.accessDenied -> "Access Denied"
                            on -> "Helmet Link OK"
                            else -> "Helmet Lost Link"
                        },
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (helmet.accessDenied) warn else linkColor
                    )
                    Text("Wi-Fi / GSM • /helmet1", fontSize = 9.5.sp, color = subtextColor)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(HelmetTelemetryManager.HELMET_ID, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0F172A))
                Text(
                    "Sync: " + if (on && helmet.lastPacketTime > 0) fmtClock(helmet.lastPacketTime) else "--:--:--",
                    fontSize = 9.5.sp, color = subtextColor
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagPill(
                icon = Icons.Default.Favorite,
                text = when { !on -> "PULSE: ---"; p.heartRate > 0 -> "PULSE: READING"; else -> "PULSE: NO FINGER" },
                color = when { !on -> idle; p.heartRate > 0 -> ok; else -> warn },
                modifier = Modifier.weight(1f)
            )
            DiagPill(
                icon = Icons.Default.Cloud,
                text = when { !on -> "GAS: ---"; p.gasBaseline > 0 -> "GAS: READY"; else -> "GAS: WARMING" },
                color = when { !on -> idle; p.gasBaseline > 0 -> ok; else -> warn },
                modifier = Modifier.weight(1f)
            )
            DiagPill(
                icon = Icons.Default.Sensors,
                text = if (on) "ULTRASONIC: ON" else "ULTRASONIC: ---",
                color = if (on) ok else idle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiagPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Text(text, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 2, lineHeight = 10.sp)
        }
    }
}

// ---------------------------------------------------------
// Helmet row inside EVENT HISTORY LOG
// ---------------------------------------------------------
@Composable
fun HelmetEventRow(event: HelmetEvent, isDark: Boolean) {
    val offline = event.isOffline
    val hasAlert = !offline && event.isAlert
    val red = if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
    val alertRed = Color(0xFFFF334B)
    val normal = if (isDark) Color(0xFF5B9BFF) else Color(0xFF2F6FD6)
    val timeColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    val accent = if (offline) red else if (hasAlert) alertRed else normal
    val borderColor = if (offline) red.copy(alpha = 0.7f) else if (hasAlert) alertRed.copy(alpha = 0.7f) else normal.copy(alpha = if (isDark) 0.3f else 0.35f)
    val bgColor = if (offline) Color(0x2BFF5252) else if (hasAlert) Color(0x33FF334B) else if (isDark) Color(0x22051224) else Color(0xCCFFFFFF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                    Text(fmtClock(event.timestamp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = timeColor)
                    Icon(Icons.Default.Engineering, contentDescription = "Helmet", tint = accent, modifier = Modifier.size(13.dp))
                }
                Surface(color = accent.copy(alpha = 0.25f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = if (offline) "HELMET OFFLINE" else if (hasAlert) "HELMET ALERT" else "HELMET",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val r = event.reading
            if (offline || r == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HR: ---", fontSize = 11.sp, color = subtextColor)
                    Text("SpO₂: ---", fontSize = 11.sp, color = subtextColor)
                    Text("Gas: ---", fontSize = 11.sp, color = subtextColor)
                    Text("Obst: ---", fontSize = 11.sp, color = subtextColor)
                }
                Text("📡 Helmet Offline (no data received)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = red)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("HR: " + if (r.heartRate > 0) "${r.heartRate.toInt()}" else "--", fontSize = 11.sp, color = subtextColor)
                    Text("SpO₂: " + if (r.spo2 > 0) "${r.spo2.toInt()}%" else "--", fontSize = 11.sp, color = subtextColor)
                    Text("Gas: ${r.gasLevel.toInt()}", fontSize = 11.sp, color = subtextColor)
                    Text("Obst: " + if (r.anyObstacle) "ALERT" else "SAFE", fontSize = 11.sp, color = subtextColor)
                }
                Text(
                    "L ${distTxt(r.distLeft)} • F ${distTxt(r.distFront)} • R ${distTxt(r.distRight)}",
                    fontSize = 10.sp, color = subtextColor
                )
                if (hasAlert && event.alertTitles.isNotEmpty()) {
                    Text("⚠️ Alert: ${event.alertTitles.joinToString(", ")}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = alertRed)
                }
            }
        }
    }
}

private fun distTxt(d: Int) = if (d >= HelmetThresholds.NO_ECHO) "--" else "$d cm"

