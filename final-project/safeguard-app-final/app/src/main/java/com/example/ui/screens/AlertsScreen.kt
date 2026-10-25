package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlertHistoryManager
import com.example.manager.EmergencySirenManager
import com.example.manager.HelmetTelemetryManager
import com.example.manager.VestTelemetryManager
import com.example.model.AlertHistoryItem
import com.example.model.AlertSeverity
import com.example.model.HazardAlert
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AlertsScreen() {
    val vestAlerts by VestTelemetryManager.activeAlerts.collectAsState()
    val helmetHazards by HelmetTelemetryManager.activeHazards.collectAsState()
    val helmetData by HelmetTelemetryManager.helmetData.collectAsState()
    val alertHistory by AlertHistoryManager.alertHistory.collectAsState()
    val vestData by VestTelemetryManager.vestData.collectAsState()
    // Vest + helmet alerts are shown together
    val activeAlerts = vestAlerts + helmetHazards
    val anyOnline = vestData.isOnline || helmetData.isOnline
    val isSirenActive by EmergencySirenManager.isAlarmActive.collectAsState()
    val currentSirenReason by EmergencySirenManager.currentEmergencyReason.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val emeraldColor = if (isDark) EmeraldOnline else CommandLightEmerald
    val hazardColor = if (isDark) HazardCrimson else CommandLightRed
    val amberColor = if (isDark) SafetyAmber else CommandLightSecondary
    val primaryAccent = if (isDark) CyanNeon else CommandLightPrimary

    // Filter history based on selected chip
    val filteredHistory = remember(alertHistory, selectedFilter) {
        when (selectedFilter) {
            "CRITICAL" -> alertHistory.filter { it.severity == AlertSeverity.CRITICAL }
            "TEMPERATURE" -> alertHistory.filter { it.sensorType.equals("TEMPERATURE", ignoreCase = true) }
            "HUMIDITY" -> alertHistory.filter { it.sensorType.equals("HUMIDITY", ignoreCase = true) }
            "PRESSURE" -> alertHistory.filter { it.sensorType.equals("PRESSURE", ignoreCase = true) }
            "HELMET" -> alertHistory.filter { it.sensorType.startsWith("HELMET", ignoreCase = true) }
            "EMERGENCY" -> alertHistory.filter {
                it.sensorType.equals("SOS", ignoreCase = true) ||
                        it.sensorType.equals("FALL", ignoreCase = true) ||
                        it.sensorType.equals("GEOFENCE", ignoreCase = true)
            }
            else -> alertHistory
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear Alarm History?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to clear all past alarm records? This action cannot be undone.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AlertHistoryManager.clearHistory()
                        showClearDialog = false
                        Toast.makeText(context, "Alarm history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = hazardColor)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp + com.example.ui.components.LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Emergency Siren Active Banner (if alarm is sounding)
        if (isSirenActive) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = hazardColor,
                    backgroundColor = hazardColor.copy(alpha = if (isDark) 0.18f else 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(hazardColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "Siren Alert",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "SIREN ALARM SOUNDING NOW",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = hazardColor,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = currentSirenReason.ifEmpty { "Critical Safety Threshold Breach!" },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                EmergencySirenManager.silenceAlarm()
                                Toast.makeText(context, "Siren silenced by supervisor", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = hazardColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Silence", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Section: ACTIVE HAZARDS (বর্তমান সক্রিয় অ্যালার্টসমূহ)
        item {
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (activeAlerts.isNotEmpty()) hazardColor else emeraldColor)
                    )
                    Text(
                        text = "ACTIVE HAZARDS (${activeAlerts.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }

                if (activeAlerts.isNotEmpty()) {
                    Text(
                        text = "Sounding Live Alarms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = hazardColor
                    )
                }
            }
        }

        if (activeAlerts.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = emeraldColor,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Active Hazard Alarms",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vest (temperature, humidity, pressure, motion, SOS) and helmet (heart rate, SpO₂, gas, obstacle) are all within safe limits.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            items(activeAlerts, key = { it.id }) { alert ->
                ActiveHazardAlertCard(alert = alert)
            }
        }

        // 4. Section: PAST ALARM & HAZARD HISTORY (পূর্বে কোন টাইমে কোন অ্যালার্ট বেজেছিল)
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ALARM & HAZARD HISTORY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${alertHistory.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (alertHistory.isNotEmpty()) {
                    Text(
                        text = "Clear History",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showClearDialog = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Filter chips for historical alarms
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filters = listOf(
                    "ALL" to "All (${alertHistory.size})",
                    "CRITICAL" to "Critical",
                    "TEMPERATURE" to "Temp",
                    "HUMIDITY" to "Humidity",
                    "PRESSURE" to "Pressure",
                    "HELMET" to "Helmet",
                    "EMERGENCY" to "SOS & Fall"
                )
                items(filters) { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryAccent.copy(alpha = 0.2f),
                            selectedLabelColor = primaryAccent
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // List of past alert/alarm events
        if (filteredHistory.isEmpty()) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "No Alarm Records Found",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedFilter == "ALL") "All past alarms and safety incident logs will appear here." else "No records match the '$selectedFilter' filter.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredHistory, key = { it.id }) { item ->
                AlertHistoryItemCard(item = item)
            }
        }

        // 5. Section: SAFETY SIMULATION & HARDWARE TESTING CONTROLS
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SAFETY SIMULATION & HARDWARE TESTING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Vest Alarm & Sensor Test Triggers",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Trigger alarms manually to test the emergency siren, alert log timeline, and notifications.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Row 1: Fall & SOS triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                VestTelemetryManager.simulateFallAlert()
                                Toast.makeText(context, "Fall impact triggered! Siren sounding.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = hazardColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fall Impact", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                VestTelemetryManager.simulateSosAlert()
                                Toast.makeText(context, "SOS panic triggered! Siren sounding.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = amberColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SOS Panic", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Row 2: Sensor Threshold Breach Simulation (Temp, Humidity, Pressure)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // High Temp (> 45°C)
                        OutlinedButton(
                            onClick = {
                                VestTelemetryManager.simulateTempAlert(49.2)
                                Toast.makeText(context, "High Temp (49.2°C) breach triggered!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Temp > 49°C", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                        }

                        // High Humidity (> 90%)
                        OutlinedButton(
                            onClick = {
                                VestTelemetryManager.simulateHumidityAlert(94.5)
                                Toast.makeText(context, "High Humidity (94.5%) breach triggered!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Hum > 94%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                        }

                        // High Pressure (> 1050 hPa)
                        OutlinedButton(
                            onClick = {
                                VestTelemetryManager.simulatePressureAlert(1065.0)
                                Toast.makeText(context, "High Pressure (1065 hPa) breach triggered!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Pres > 1065", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA855F7))
                        }
                    }

                    // Reset to Safe Baseline (Clears active hazards & records resolved timestamps)
                    Button(
                        onClick = {
                            VestTelemetryManager.clearActiveAlerts()
                            EmergencySirenManager.silenceAlarm()
                            Toast.makeText(context, "All sensors reset to safe baseline. Alarms resolved.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset All Sensors to Safe Baseline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Card displaying an ACTIVE Hazard Alert (Currently Sounding/Active)
 */
@Composable
fun ActiveHazardAlertCard(alert: HazardAlert) {
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val isCritical = alert.severity == AlertSeverity.CRITICAL

    // Sensor-specific colors adhering strictly to persistent rules:
    // Temperature: Red / Coral (#FF5252 / #DC2626)
    // Humidity: Cyan / Electric Blue (#00E5FF / #0284C7)
    // Pressure: Purple / Violet (#A855F7)
    val accentColor = when (alert.sensorType.uppercase()) {
        "TEMPERATURE" -> if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
        "HUMIDITY" -> if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
        "PRESSURE" -> if (isDark) Color(0xFFA855F7) else Color(0xFF7C3AED)
        "GEOFENCE" -> if (isDark) SafetyAmber else CommandLightSecondary
        "HELMET_OBSTACLE" -> if (isDark) Color(0xFFF97316) else Color(0xFFEA580C)
        "HELMET_GAS" -> if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)
        "HELMET_HR", "HELMET_SPO2" -> if (isDark) Color(0xFFFF3366) else Color(0xFFE11D48)
        else -> if (isDark) HazardCrimson else CommandLightRed
    }

    val exactTime = remember(alert.timestamp) {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        sdf.format(Date(alert.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()))
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = accentColor,
        backgroundColor = accentColor.copy(alpha = if (isDark) 0.12f else 0.08f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Sensor Icon + Title + Severity Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (alert.sensorType.uppercase()) {
                            "TEMPERATURE" -> Icons.Default.Thermostat
                            "HUMIDITY" -> Icons.Default.WaterDrop
                            "PRESSURE" -> Icons.Default.Speed
                            "SOS" -> Icons.Default.Emergency
                            "FALL" -> Icons.Default.AccessibilityNew
                            "GEOFENCE" -> Icons.Default.WrongLocation
                            "HELMET_OBSTACLE" -> Icons.Default.Sensors
                            "HELMET_GAS" -> Icons.Default.Cloud
                            "HELMET_HR" -> Icons.Default.Favorite
                            "HELMET_SPO2" -> Icons.Default.Air
                            else -> Icons.Default.Warning
                        }
                        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = alert.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Text(
                                text = "SOUNDING NOW • Triggered at $exactTime",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }

                Surface(
                    color = accentColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isCritical) "CRITICAL" else "WARNING",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = alert.description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(color = accentColor.copy(alpha = 0.25f), thickness = 0.8.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Breach: ${alert.triggeredValue} (Safe: ${alert.safeLimit})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = alert.sensorType.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card displaying a PAST ALARM / HAZARD RECORD with full time-stamped details:
 * - When it rang (exact time + date)
 * - How long it was active / when it was resolved
 * - What sensor breached and the recorded value
 */
@Composable
fun AlertHistoryItemCard(item: AlertHistoryItem) {
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark

    // Distinct Sensor Colors per user persistent rules:
    val accentColor = when (item.sensorType.uppercase()) {
        "TEMPERATURE" -> if (isDark) Color(0xFFFF5252) else Color(0xFFDC2626)
        "HUMIDITY" -> if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)
        "PRESSURE" -> if (isDark) Color(0xFFA855F7) else Color(0xFF7C3AED)
        "GEOFENCE" -> if (isDark) SafetyAmber else CommandLightSecondary
        else -> if (isDark) HazardCrimson else CommandLightRed
    }

    val exactTriggerTime = remember(item.triggeredTimestamp) {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        sdf.format(Date(item.triggeredTimestamp))
    }

    val dateFormatted = remember(item.triggeredTimestamp) {
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdf.format(Date(item.triggeredTimestamp))
    }

    val relativeTime = remember(item.triggeredTimestamp) {
        val diff = System.currentTimeMillis() - item.triggeredTimestamp
        when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            else -> "${diff / 86400_000L}d ago"
        }
    }

    val durationText = remember(item.triggeredTimestamp, item.resolvedTimestamp, item.isStillActive) {
        if (item.isStillActive) {
            "Active / Sounding"
        } else if (item.resolvedTimestamp != null && item.resolvedTimestamp >= item.triggeredTimestamp) {
            val durSec = (item.resolvedTimestamp - item.triggeredTimestamp) / 1000
            val resolvedTime = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(item.resolvedTimestamp))
            if (durSec <= 0) {
                "Cleared at $resolvedTime"
            } else if (durSec < 60) {
                "${durSec}s • Cleared $resolvedTime"
            } else {
                val mins = durSec / 60
                val secs = durSec % 60
                "${mins}m ${secs}s • Cleared $resolvedTime"
            }
        } else {
            "Resolved"
        }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (item.isStillActive) accentColor else null,
        backgroundColor = if (item.isStillActive) accentColor.copy(alpha = if (isDark) 0.1f else 0.06f) else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Trigger Time Badge (PROMINENT) + Status Pill (Active / Resolved)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = "Triggered Time",
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$exactTriggerTime ($dateFormatted)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "• $relativeTime",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge: ACTIVE vs RESOLVED (Guaranteed single line, never wrapping)
                Surface(
                    color = if (item.isStillActive) {
                        Color(0xFFEF4444)
                    } else {
                        if (isDark) EmeraldOnline.copy(alpha = 0.2f) else Color(0xFFD1FAE5)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (item.isStillActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDark) EmeraldOnline else Color(0xFF047857),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "RESOLVED",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) EmeraldOnline else Color(0xFF047857),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Middle Row: Alert Title & Sensor Type Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Category tag with distinct sensor color
                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = item.sensorType.uppercase(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = item.description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.8.dp)

            // Bottom Row: Breached Value + Duration / Resolved Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Breach: ${item.triggeredValue} (Limit: ${item.safeLimit})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = durationText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
