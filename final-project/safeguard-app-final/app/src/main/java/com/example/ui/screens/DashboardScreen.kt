package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TelemetryRepository
import com.example.manager.HelmetTelemetryManager
import com.example.manager.VestTelemetryManager
import com.example.model.EventSnapshot
import com.example.model.HelmetData
import com.example.model.HelmetEvent
import com.example.model.HazardAlert
import com.example.model.VestData
import com.example.model.WorkerProfile
import com.example.ui.components.GlassCard
import com.example.ui.components.webGlass
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    profile: WorkerProfile = WorkerProfile()
) {
    // PERFORMANCE: telemetry flows are NOT collected here. Each LazyColumn item below collects only
    // the flows it needs, so a Firebase update recomposes just the cards whose data changed
    // instead of the whole dashboard (matters on low-end phones: updates arrive ~1/s).
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // side padding lives INSIDE the list so the cards' neon glow isn't clipped at the edges
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. WORKER STATUS Card (Cyan to Golden Amber Neon Gradient Border)
        item(key = "worker_status", contentType = "card") {
            val vestData by VestTelemetryManager.vestData.collectAsState()
            val helmetData by HelmetTelemetryManager.helmetData.collectAsState()
            WorkerStatusCard(
                vestData = vestData,
                profile = profile,
                isDark = isDark,
                helmetData = helmetData
            )
        }

        // 2. 2x2 Telemetry Grid (Temperature, Humidity, Pressure, Active Alerts)
        item(key = "metrics", contentType = "card") {
            val vestData by VestTelemetryManager.vestData.collectAsState()
            val helmetData by HelmetTelemetryManager.helmetData.collectAsState()
            val vestAlerts by VestTelemetryManager.activeAlerts.collectAsState()
            val helmetHazards by HelmetTelemetryManager.activeHazards.collectAsState()
            // Vest + helmet are shown together: one alert list
            val activeAlerts = remember(vestAlerts, helmetHazards) { vestAlerts + helmetHazards }
            val anyOnline = vestData.isOnline || helmetData.isOnline
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Row 1: Temperature & Humidity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val rules by com.example.data.SafetyRulesManager.rules.collectAsState()
                    val env = vestData.environment
                    val on = vestData.isOnline
                    val tempBad = on && (env.temperature > rules.tempHighCritical || env.temperature < rules.tempLowWarning)
                    FuturisticMetricCard(
                        title = "TEMPERATURE",
                        value = if (on) String.format(Locale.US, "%.1f", env.temperature) else "--",
                        unit = "°C",
                        icon = Icons.Default.DeviceThermostat,
                        borderColor = Color(0xFFFF3366),
                        status = if (!on) "Waiting..." else if (tempBad) (if (env.temperature > rules.tempHighCritical) "High Temperature" else "Low Temperature") else "Normal",
                        range = "Range: ${rules.tempLowWarning.toInt()}°C - ${rules.tempHighCritical.toInt()}°C",
                        sparkValue = if (on) env.temperature.toFloat() else null,
                        isWarning = tempBad,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )

                    val humBad = on && (env.humidity > rules.humidityHighWarning || env.humidity < rules.humidityLowWarning)
                    FuturisticMetricCard(
                        title = "HUMIDITY",
                        value = if (on) String.format(Locale.US, "%.1f", env.humidity) else "--",
                        unit = "%",
                        icon = Icons.Default.WaterDrop,
                        borderColor = if (isDark) Color(0xFF00F2FF) else Color(0xFF0077FF),
                        status = if (!on) "Waiting..." else if (humBad) (if (env.humidity > rules.humidityHighWarning) "High Humidity" else "Low Humidity") else "Normal",
                        range = "Range: ${rules.humidityLowWarning.toInt()}% - ${rules.humidityHighWarning.toInt()}%",
                        sparkValue = if (on) env.humidity.toFloat() else null,
                        isWarning = humBad,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Pressure & Active Alerts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val rulesP by com.example.data.SafetyRulesManager.rules.collectAsState()
                    val envP = vestData.environment
                    val onP = vestData.isOnline
                    val presBad = onP && (envP.pressure > rulesP.pressureHighWarning || envP.pressure < rulesP.pressureLowWarning)
                    FuturisticMetricCard(
                        title = "PRESSURE",
                        value = if (onP) String.format(Locale.US, "%.0f", envP.pressure) else "--",
                        unit = "hPa",
                        icon = Icons.Default.Speed,
                        borderColor = Color(0xFFA855F7),
                        status = if (!onP) "Waiting..." else if (presBad) (if (envP.pressure > rulesP.pressureHighWarning) "High Pressure" else "Low Pressure") else "Normal",
                        range = "Range: ${rulesP.pressureLowWarning.toInt()} - ${rulesP.pressureHighWarning.toInt()} hPa",
                        sparkValue = if (onP) envP.pressure.toFloat() else null,
                        isWarning = presBad,
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )

                    HelmetHrTile(helmet = helmetData, isDark = isDark, modifier = Modifier.weight(1f))
                }

                // Row 3: Blood Oxygen & Gas Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HelmetSpo2Tile(helmet = helmetData, isDark = isDark, modifier = Modifier.weight(1f))
                    HelmetGasTile(helmet = helmetData, isDark = isDark, modifier = Modifier.weight(1f))
                }

                // Row 4: Active Alerts (vest + helmet) & Obstacle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val alertsCountStr = if (anyOnline) "${activeAlerts.size}" else "--"
                    FuturisticMetricCard(
                        title = "ACTIVE ALERTS",
                        value = alertsCountStr,
                        icon = Icons.Default.Notifications,
                        borderColor = Color(0xFFFF3366),
                        status = if (!anyOnline) "Waiting..." else if (activeAlerts.isNotEmpty()) "Attention needed" else "All clear",
                        isWarning = anyOnline && activeAlerts.isNotEmpty(),
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )
                    HelmetObstacleTile(helmet = helmetData, isDark = isDark, modifier = Modifier.weight(1f))
                }
            }
        }

        // 3. MOTION STATE Card (Neon Amber Border)
        item(key = "motion", contentType = "card") {
            val vestState = VestTelemetryManager.vestData.collectAsState()
            val motionLabel by remember {
                derivedStateOf { vestState.value.let { if (it.isOnline) it.status.motion_state else "--" } }
            }
            FuturisticMotionCard(
                motionState = motionLabel,
                isDark = isDark
            )
        }

        // 3b. Helmet obstacle proximity (left / front / right)
        item(key = "proximity", contentType = "card") {
            val helmetData by HelmetTelemetryManager.helmetData.collectAsState()
            HelmetProximityCard(helmet = helmetData, isDark = isDark)
        }

        // 4. ACTIVE ALERTS Container (Bottom Section matching mockup)
        item(key = "alerts", contentType = "card") {
            val vestAlerts by VestTelemetryManager.activeAlerts.collectAsState()
            val helmetHazards by HelmetTelemetryManager.activeHazards.collectAsState()
            val activeAlerts = remember(vestAlerts, helmetHazards) { vestAlerts + helmetHazards }
            FuturisticAlertsSection(
                alerts = activeAlerts,
                isDark = isDark
            )
        }

        // 5. Sensor Diagnostics & System Link Card
        item(key = "diagnostics", contentType = "card") {
            val vestData by VestTelemetryManager.vestData.collectAsState()
            val helmetData by HelmetTelemetryManager.helmetData.collectAsState()
            SystemDiagnosticsCard(vestData = vestData, profile = profile, isDark = isDark, helmetData = helmetData)
        }

        // 6. Device Event History Log Card (Styling aligned with Vest Diagnostics and System Link card)
        item(key = "history", contentType = "card") {
            val vestState = VestTelemetryManager.vestData.collectAsState()
            val helmetState = HelmetTelemetryManager.helmetData.collectAsState()
            // only recomposes when online/offline actually flips, not on every telemetry packet
            val anyOnline by remember { derivedStateOf { vestState.value.isOnline || helmetState.value.isOnline } }
            val eventHistoryVest by VestTelemetryManager.eventHistory.collectAsState()
            val helmetEvents by HelmetTelemetryManager.events.collectAsState()
            val eventHistory = remember(eventHistoryVest, helmetEvents) {
                (eventHistoryVest.map { it.timestamp to (it as Any) } + helmetEvents.map { it.timestamp to (it as Any) })
                    .sortedByDescending { it.first }
                    .map { it.second }
            }
            val glassBgGradient = if (isDark) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x35FFFFFF),  // Frosty crystal top highlight
                        Color(0xEE0B1524),  // Deep smoked dark-slate glass
                        Color(0xF5060D17)   // Deep solid-translucent base
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFAFFFFFF),
                        Color(0xF0F1F5F9),
                        Color(0xE8E2E8F0)
                    )
                )
            }
            val cardBorder = if (isDark) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color(0xFF0284C7).copy(alpha = 0.5f)
            val titleColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
            val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .webGlass(isDark, glow = if (isDark) Color(0xFF00F2FF) else Color(0xFF0077FF))
            ) {


                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row with Title, Badges & Counter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Event History Log",
                                tint = titleColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "EVENT HISTORY LOG",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                maxLines = 1,
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                color = if (isDark) Color(0x3310B981) else Color(0x22059669),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isDark) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF059669).copy(alpha = 0.6f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color(0xFF10B981) else Color(0xFF059669))
                                    )
                                    Text(
                                        text = "CACHED",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF10B981) else Color(0xFF059669),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Right side badge: single line, no wrapping
                        Surface(
                            color = if (!anyOnline) {
                                if (isDark) Color(0x2BFF5252) else Color(0x1ADC2626)
                            } else {
                                if (isDark) Color(0x3300E5FF) else Color(0x1F0284C7)
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (!anyOnline) {
                                    if (isDark) Color(0xFFFF5252).copy(alpha = 0.6f) else Color(0xFFDC2626).copy(alpha = 0.6f)
                                } else {
                                    if (isDark) Color(0xFF00E5FF).copy(alpha = 0.4f) else Color(0xFF0284C7).copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Text(
                                text = if (!anyOnline) "OFFLINE" else "${eventHistory.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!anyOnline) {
                                    if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
                                } else {
                                    if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
                                },
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Subtitle / Stream Status Description (single line, clean)
                    Text(
                        text = if (!anyOnline) "Device offline • displaying cached telemetry" else "Real-time stream & local database persistence active",
                        fontSize = 11.sp,
                        maxLines = 1,
                        color = if (!anyOnline) {
                            if (isDark) Color(0xFFFF5252).copy(alpha = 0.85f) else Color(0xFFDC2626).copy(alpha = 0.85f)
                        } else {
                            subtextColor
                        }
                    )

                    // Offline Warning banner inside card (strictly single line)
                    if (!anyOnline) {
                        Surface(
                            color = if (isDark) Color(0x2BFF5252) else Color(0x1ADC2626),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFFFF5252).copy(alpha = 0.5f) else Color(0xFFDC2626).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626))
                                )
                                Text(
                                    text = "OFFLINE — Telemetry paused",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
                                )
                            }
                        }
                    }

                    // History Content List
                    if (eventHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    if (!anyOnline) {
                                        if (isDark) Color(0xFFFF5252).copy(alpha = 0.35f) else Color(0xFFDC2626).copy(alpha = 0.35f)
                                    } else {
                                        if (isDark) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0xFF0284C7).copy(alpha = 0.3f)
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (!anyOnline) Icons.Default.CloudOff else Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = if (!anyOnline) {
                                        if (isDark) Color(0xFFFF5252).copy(alpha = 0.8f) else Color(0xFFDC2626).copy(alpha = 0.8f)
                                    } else {
                                        if (isDark) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color(0xFF0284C7).copy(alpha = 0.8f)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = if (!anyOnline) "Device Offline (---)" else "Awaiting Telemetry Stream...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (!anyOnline) {
                                        if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
                                    } else {
                                        if (isDark) Color.White else Color(0xFF0F172A)
                                    }
                                )
                                Text(
                                    text = if (!anyOnline) "No live telemetry packets received. System is currently offline." else "Vest (LoRa 'worker1') and helmet ('helmet1') packets will be logged here",
                                    fontSize = 11.sp,
                                    color = subtextColor
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            eventHistory.forEach { entry ->
                                when (entry) {
                                    is EventSnapshot -> EventSnapshotRow(snapshot = entry, isDark = isDark)
                                    is HelmetEvent -> HelmetEventRow(event = entry, isDark = isDark)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// WORKER STATUS Card (Matching exact user mockup design)
// ---------------------------------------------------------
@Composable
fun WorkerStatusCard(
    vestData: VestData,
    profile: WorkerProfile,
    isDark: Boolean = true,
    helmetData: HelmetData? = null
) {
    // Frosted Glass multi-stop gradient with high luminosity top highlight and deep dark smoked body
    val glassBgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x35FFFFFF),  // Frosty crystal rim highlight
                Color(0xEE0B1524),  // Deep smoked dark-slate glass for sharp text
                Color(0xF5060D17)   // Deep solid-translucent base
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFAFFFFFF),
                Color(0xF0F1F5F9),
                Color(0xE8E2E8F0)
            )
        )
    }

    val headerTitleColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)

    val borderBrush = if (isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF00E5FF),
                Color(0xFFFFA000),
                Color(0xFFFF9100)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF0284C7),
                Color(0xFFEA580C),
                Color(0xFFD97706)
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .webGlass(isDark, glow = if (isDark) Color(0xFF00F2FF) else Color(0xFF0077FF))
    ) {

        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "WORKER STATUS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = headerTitleColor,
                letterSpacing = 1.sp
            )

            WorkerStatusRow(
                label = "ID",
                value = profile.workerId.ifBlank { "VST-001" },
                isDark = isDark
            )
            WorkerStatusRow(
                label = "NAME",
                value = profile.workerName.ifBlank { "Rahim Uddin" },
                isDark = isDark
            )
            WorkerStatusRow(
                label = "VEST ID",
                value = if (profile.batchId.isNotBlank()) profile.batchId else "SV-2026-014",
                isDark = isDark
            )
            WorkerStatusRow(
                label = "STATUS",
                value = if (vestData.isOnline) "Active" else "Inactive",
                valueColor = if (vestData.isOnline) {
                    if (isDark) Color(0xFF00E676) else Color(0xFF059669)
                } else {
                    if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
                },
                isDark = isDark
            )
            WorkerStatusRow(
                label = "SHIFT",
                value = profile.shift.ifBlank { "Day Shift (08:00 - 20:00)" },
                isDark = isDark
            )
            if (helmetData != null) {
                WorkerStatusRow(
                    label = "HELMET ID",
                    value = HelmetTelemetryManager.HELMET_ID,
                    isDark = isDark
                )
                WorkerStatusRow(
                    label = "HELMET",
                    value = if (helmetData.isOnline) "Active" else "Inactive",
                    valueColor = if (helmetData.isOnline) {
                        if (isDark) Color(0xFF00E676) else Color(0xFF059669)
                    } else {
                        if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
                    },
                    isDark = isDark
                )
            }
        }
    }
}

@Composable
private fun WorkerStatusRow(
    label: String,
    value: String,
    valueColor: Color? = null,
    isDark: Boolean = true
) {
    val defaultValColor = if (isDark) Color.White else Color(0xFF0F172A)
    val actualValColor = valueColor ?: defaultValColor
    val labelColor = if (isDark) Color(0xFF8FA0B5) else Color(0xFF64748B)

    var previousVal by remember { mutableStateOf(value) }
    val glowAnim = remember { Animatable(0f) }

    LaunchedEffect(value) {
        if (previousVal != value && previousVal.isNotBlank() && previousVal != "--") {
            previousVal = value
            glowAnim.snapTo(1f)
            glowAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        } else {
            previousVal = value
        }
    }

    val animatedColor = if (glowAnim.value > 0.005f) {
        lerp(actualValColor, Color.White, glowAnim.value * 0.45f)
    } else {
        actualValColor
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            modifier = Modifier.weight(0.35f)
        )
        Box(
            modifier = Modifier.weight(0.65f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (glowAnim.value > 0.005f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(26.dp)
                        .graphicsLayer {
                            alpha = glowAnim.value
                            scaleX = 1f + (glowAnim.value * 0.15f)
                        }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    actualValColor.copy(alpha = glowAnim.value * 0.45f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                )
            }
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(
                        initialOffsetY = { -it / 3 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(240))).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180))
                    )
                },
                label = "StatusRowTransition"
            ) { targetVal ->
                Text(
                    text = targetVal,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 1f + (glowAnim.value * 0.05f)
                        scaleY = 1f + (glowAnim.value * 0.05f)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------
// Futuristic Metric Card (Circular Icon + Title + Big Value)
// ---------------------------------------------------------
@Composable
fun FuturisticMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    borderColor: Color,
    iconColor: Color = borderColor,
    watermark: String? = null,
    isDark: Boolean = true,
    modifier: Modifier = Modifier,
    unit: String = "",
    status: String? = null,
    range: String? = null,
    sparkValue: Float? = null,
    isWarning: Boolean = false
) {
    // Same card as the website's sensor-card-v2 (Temperature / Humidity / Pressure).
    val history = com.example.ui.components.rememberSparkHistory(sparkValue)
    com.example.ui.components.WebSensorCard(
        title = title,
        value = value,
        unit = unit,
        icon = icon,
        accent = iconColor,
        isDark = isDark,
        modifier = modifier,
        status = status,
        statusColor = if (isWarning) com.example.ui.components.WebRed else com.example.ui.components.WebGreen,
        range = range,
        sparkValues = history,
        isWarning = isWarning
    )
}

// ---------------------------------------------------------
// MOTION STATE Card (Full Width with Amber Neon Border)
// ---------------------------------------------------------
@Composable
fun FuturisticMotionCard(
    motionState: String,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val displayState = if (motionState.isBlank() || motionState == "--" || motionState.equals("offline", ignoreCase = true)) "--" else motionState.uppercase()

    var previousState by remember { mutableStateOf(displayState) }
    val motionGlowAnim = remember { Animatable(0f) }

    LaunchedEffect(displayState) {
        if (previousState != displayState && previousState.isNotBlank() && previousState != "--" && displayState != "--") {
            previousState = displayState
            motionGlowAnim.snapTo(1f)
            motionGlowAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        } else {
            previousState = displayState
        }
    }

    val glassBgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x35FFFFFF),  // Frosty crystal top highlight
                Color(0xEE0B1524),  // Deep smoked dark-slate glass
                Color(0xF5060D17)   // Deep solid-translucent base
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFAFFFFFF),
                Color(0xF0F1F5F9),
                Color(0xE8E2E8F0)
            )
        )
    }

    val iconBg = if (isDark) Color(0x33000000) else Color(0x14000000)
    val titleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    val valueColor = if (isDark) Color.White else Color(0xFF0F172A)
    val amberBorder = if (isDark) Color(0xFFFFA000) else Color(0xFFEA580C)

    val dynamicAmberBorder = if (motionGlowAnim.value > 0.005f) {
        lerp(amberBorder, Color.White, motionGlowAnim.value * 0.45f)
    } else {
        amberBorder
    }
    val dynamicAmberBorderWidth = (1.6f + (motionGlowAnim.value * 1.2f)).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .webGlass(isDark, glow = if (isDark) Color(0xFFFFA000) else Color(0xFFEA580C))
    ) {


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Circular Icon + Title placed to the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .border(1.3.dp, amberBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = "Motion State",
                        tint = amberBorder,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Text(
                    text = "MOTION STATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Glowing Motion State Text
            Box(
                contentAlignment = Alignment.CenterStart
            ) {
                if (motionGlowAnim.value > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(38.dp)
                            .graphicsLayer {
                                alpha = motionGlowAnim.value
                                scaleX = 1f + (motionGlowAnim.value * 0.2f)
                                scaleY = 1f + (motionGlowAnim.value * 0.3f)
                            }
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        amberBorder.copy(alpha = motionGlowAnim.value * 0.70f),
                                        amberBorder.copy(alpha = motionGlowAnim.value * 0.25f),
                                        Color.Transparent
                                    )
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                    )
                }

                val currentMotionColor = if (motionGlowAnim.value > 0.005f) {
                    lerp(valueColor, amberBorder, motionGlowAnim.value * 0.85f)
                } else {
                    valueColor
                }
                val motionScale = 1f + (motionGlowAnim.value * 0.08f)

                AnimatedContent(
                    targetState = displayState,
                    transitionSpec = {
                        (slideInVertically(
                            initialOffsetY = { -it / 3 },
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(240))).togetherWith(
                            slideOutVertically(
                                targetOffsetY = { it / 3 },
                                animationSpec = tween(240, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(180))
                        )
                    },
                    label = "MotionGlowTransition"
                ) { targetMotion ->
                    Text(
                        text = targetMotion,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentMotionColor,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .graphicsLayer {
                                scaleX = motionScale
                                scaleY = motionScale
                            }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------
// ACTIVE ALERTS Container (Matching Bottom of Screenshot)
// ---------------------------------------------------------
@Composable
fun FuturisticAlertsSection(
    alerts: List<HazardAlert>,
    isDark: Boolean = true,
    modifier: Modifier = Modifier
) {
    val glassBgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x35FFFFFF),  // Frosty crystal top highlight
                Color(0xEE0B1524),  // Deep smoked dark-slate glass
                Color(0xF5060D17)   // Deep solid-translucent base
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFAFFFFFF),
                Color(0xF0F1F5F9),
                Color(0xE8E2E8F0)
            )
        )
    }
    val cardBorder = if (isDark) Color(0xFF00E5FF).copy(alpha = 0.55f) else Color(0xFF0284C7).copy(alpha = 0.6f)
    val titleColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
    val noAlertsColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .webGlass(isDark, glow = if (isDark) Color(0xFF00F2FF) else Color(0xFF0077FF))
    ) {


        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ACTIVE ALERTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    letterSpacing = 0.8.sp
                )

                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF334B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${alerts.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (alerts.isEmpty()) {
                Text(
                    text = "No active hazardous alerts",
                    fontSize = 11.5.sp,
                    color = noAlertsColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                alerts.forEach { alert ->
                    val rowBg = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFFFFF1F2)
                    val alertTitleColor = if (isDark) Color.White else Color(0xFF991B1B)
                    val alertDescColor = if (isDark) Color(0xFF94A3B8) else Color(0xFFB91C1C)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(rowBg)
                            .border(1.dp, Color(0xFFFF334B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = alertTitleColor
                            )
                            Text(
                                text = alert.description,
                                fontSize = 10.5.sp,
                                color = alertDescColor
                            )
                        }

                        Surface(
                            color = Color(0xFFFF334B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = alert.triggeredValue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// LoRa & System Diagnostics Card (No Battery Monitoring)
// ---------------------------------------------------------
@Composable
fun SystemDiagnosticsCard(
    vestData: VestData,
    profile: WorkerProfile,
    isDark: Boolean = true,
    helmetData: HelmetData? = null
) {
    val isFall = vestData.alerts.fall_detected
    val isSos = vestData.alerts.sos_active
    val glassBgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x35FFFFFF),  // Frosty crystal top highlight
                Color(0xEE0B1524),  // Deep smoked dark-slate glass
                Color(0xF5060D17)   // Deep solid-translucent base
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFAFFFFFF),
                Color(0xF0F1F5F9),
                Color(0xE8E2E8F0)
            )
        )
    }
    val cardBorder = if (isDark) Color(0xFF00E5FF).copy(alpha = 0.45f) else Color(0xFF0284C7).copy(alpha = 0.5f)
    val titleColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
    val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val workerNameColor = if (isDark) Color.White else Color(0xFF0F172A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .webGlass(isDark, glow = if (isDark) Color(0xFF00F2FF) else Color(0xFF0077FF))
    ) {


        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DIAGNOSTICS & SYSTEM LINK",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                letterSpacing = 0.8.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LoRa Link
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val linkColor = if (vestData.isOnline) {
                        if (isDark) Color(0xFF00E676) else Color(0xFF059669)
                    } else {
                        if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
                    }

                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = "LoRa Node",
                        tint = linkColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (vestData.isOnline) "LoRa Link OK" else "Lost Link",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = linkColor
                        )
                        Text(
                            text = "Gateway Node: ${profile.loraNode}",
                            fontSize = 9.5.sp,
                            color = subtextColor
                        )
                    }
                }

                // Monitored Worker Identity
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = profile.workerName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = workerNameColor
                        )
                        Text(
                            text = "ID: ${profile.workerId.ifBlank { "WKR-01" }}",
                            fontSize = 9.5.sp,
                            color = subtextColor
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Badge,
                        contentDescription = "Worker",
                        tint = titleColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Fall and SOS status pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val fallBg = if (!vestData.isOnline) {
                    if (isDark) Color(0x1A94A3B8) else Color(0x1A64748B)
                } else if (isFall) {
                    Color(0x33FF334B)
                } else {
                    if (isDark) Color(0x1F00E676) else Color(0x1F059669)
                }
                val fallColor = if (!vestData.isOnline) {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                } else if (isFall) {
                    Color(0xFFFF334B)
                } else {
                    if (isDark) Color(0xFF00E676) else Color(0xFF059669)
                }

                Surface(
                    color = fallBg,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, fallColor.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Fall",
                            tint = fallColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (!vestData.isOnline) "FALL: ---" else if (isFall) "FALL DETECTED" else "FALL: NORMAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = fallColor
                        )
                    }
                }

                val sosBg = if (!vestData.isOnline) {
                    if (isDark) Color(0x1A94A3B8) else Color(0x1A64748B)
                } else if (isSos) {
                    Color(0x33FF334B)
                } else {
                    if (isDark) Color(0x1F00E676) else Color(0x1F059669)
                }
                val sosColor = if (!vestData.isOnline) {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                } else if (isSos) {
                    Color(0xFFFF334B)
                } else {
                    if (isDark) Color(0xFF00E676) else Color(0xFF059669)
                }

                Surface(
                    color = sosBg,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sosColor.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Emergency,
                            contentDescription = "SOS",
                            tint = sosColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (!vestData.isOnline) "SOS: ---" else if (isSos) "SOS ACTIVE" else "SOS: IDLE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = sosColor
                        )
                    }
                }
            }

            if (helmetData != null) {
                HelmetDiagnosticsSection(helmet = helmetData, isDark = isDark)
            }
        }
    }
}

// ---------------------------------------------------------
// Event Snapshot Row
// ---------------------------------------------------------
@Composable
fun EventSnapshotRow(
    snapshot: EventSnapshot,
    isDark: Boolean = true
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val isOffline = !snapshot.isOnline || snapshot.motion_state.equals("OFFLINE", ignoreCase = true)
    val hasAlert = snapshot.fall_detected || snapshot.sos_active || snapshot.activeAlerts.isNotEmpty()

    val borderColor = if (isOffline) Color(0xFFFF5252).copy(alpha = 0.7f)
        else if (hasAlert) Color(0xFFFF334B).copy(alpha = 0.7f)
        else if (isDark) Color(0xFF00E5FF).copy(alpha = 0.3f)
        else Color(0xFF0284C7).copy(alpha = 0.35f)

    val bgColor = if (isOffline) Color(0x2BFF5252)
        else if (hasAlert) Color(0x33FF334B)
        else if (isDark) Color(0x22051224)
        else Color(0xCCFFFFFF)

    val timeColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subtextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val normalTagColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
    val normalTagBg = if (isDark) Color(0x3300E5FF) else Color(0x1F0284C7)

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOffline) Color(0xFFFF5252)
                                else if (hasAlert) Color(0xFFFF334B)
                                else normalTagColor
                            )
                    )
                    Text(
                        text = timeFormat.format(Date(snapshot.timestamp)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = timeColor
                    )
                }

                Surface(
                    color = if (isOffline) Color(0xFFFF5252).copy(alpha = 0.25f)
                        else if (hasAlert) Color(0xFFFF334B).copy(alpha = 0.25f)
                        else normalTagBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isOffline) "OFFLINE" else snapshot.motion_state.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOffline) Color(0xFFFF5252)
                            else if (hasAlert) Color(0xFFFF334B)
                            else normalTagColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isOffline) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Temp: ---",
                        fontSize = 11.sp,
                        color = subtextColor
                    )
                    Text(
                        text = "Hum: ---",
                        fontSize = 11.sp,
                        color = subtextColor
                    )
                    Text(
                        text = "Pres: ---",
                        fontSize = 11.sp,
                        color = subtextColor
                    )
                }

                Text(
                    text = "📡 Device Offline (Telemetry connection paused)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF5252)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Temp: ${String.format(Locale.US, "%.1f°C", snapshot.temperature)}",
                        fontSize = 11.sp,
                        color = subtextColor
                    )
                    Text(
                        text = "Hum: ${String.format(Locale.US, "%.1f%%", snapshot.humidity)}",
                        fontSize = 11.sp,
                        color = subtextColor
                    )
                    Text(
                        text = "Pres: ${String.format(Locale.US, "%.0f hPa", snapshot.pressure)}",
                        fontSize = 11.sp,
                        color = subtextColor
                    )
                }

                if (hasAlert && snapshot.activeAlerts.isNotEmpty()) {
                    Text(
                        text = "⚠️ Alert: ${snapshot.activeAlerts.joinToString(", ")}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFF334B)
                    )
                }
            }
        }
    }
}
