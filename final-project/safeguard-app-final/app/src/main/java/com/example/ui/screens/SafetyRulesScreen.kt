package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SafetyRulesManager
import com.example.model.SafetyRules
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SafetyRulesScreen() {
    val context = LocalContext.current
    val rules by SafetyRulesManager.rules.collectAsState()

    // Local state for editable thresholds
    var tempHigh by remember(rules) { mutableStateOf(rules.tempHighCritical.toString()) }
    var tempLow by remember(rules) { mutableStateOf(rules.tempLowWarning.toString()) }
    var presHigh by remember(rules) { mutableStateOf(rules.pressureHighWarning.toString()) }
    var presLow by remember(rules) { mutableStateOf(rules.pressureLowWarning.toString()) }
    var humHigh by remember(rules) { mutableStateOf(rules.humidityHighWarning.toString()) }
    var humLow by remember(rules) { mutableStateOf(rules.humidityLowWarning.toString()) }
    var fallEnabled by remember(rules) { mutableStateOf(rules.fallDetectionEnabled) }
    var immobilityTimeout by remember(rules) { mutableStateOf(rules.immobilityTimeoutSec.toString()) }
    var sosBuzzer by remember(rules) { mutableStateOf(rules.sosBuzzerEnabled) }
    var helmetHrMin by remember(rules) { mutableStateOf(rules.helmetHrMin.toString()) }
    var helmetHrMax by remember(rules) { mutableStateOf(rules.helmetHrMax.toString()) }
    var helmetSpo2Min by remember(rules) { mutableStateOf(rules.helmetSpo2Min.toString()) }
    var helmetGasMargin by remember(rules) { mutableStateOf(rules.helmetGasMargin.toString()) }
    var helmetObstacleCm by remember(rules) { mutableStateOf(rules.helmetObstacleCm.toString()) }

    var hasUnsavedChanges by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val primaryColor = if (isDark) CyanNeon else CommandLightPrimary
    val hazardColor = if (isDark) HazardCrimson else CommandLightRed
    val amberColor = if (isDark) SafetyAmber else CommandLightSecondary
    val emeraldColor = if (isDark) EmeraldOnline else CommandLightEmerald

    val lastUpdatedFormatted = remember(rules.lastUpdated) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
        sdf.format(Date(rules.lastUpdated))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + com.example.ui.components.LocalDockInset.current),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Safety Rules Header Banner
        item {
            GlassCard(
                borderColor = primaryColor.copy(alpha = 0.5f),
                contentPadding = PaddingValues(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(primaryColor.copy(alpha = 0.15f))
                                    .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Gavel,
                                    contentDescription = "Safety Rules",
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "SAFETY RULES & THRESHOLDS",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else TextDark,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "OSHA & Industrial Safety Threshold Configurator",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = emeraldColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ADMIN LEVEL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = emeraldColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Last synced with telemetry engine: $lastUpdatedFormatted",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Action Buttons (Save & Reset)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val th = tempHigh.toDoubleOrNull() ?: rules.tempHighCritical
                        val tl = tempLow.toDoubleOrNull() ?: rules.tempLowWarning
                        val ph = presHigh.toDoubleOrNull() ?: rules.pressureHighWarning
                        val pl = presLow.toDoubleOrNull() ?: rules.pressureLowWarning
                        val hh = humHigh.toDoubleOrNull() ?: rules.humidityHighWarning
                        val hl = humLow.toDoubleOrNull() ?: rules.humidityLowWarning
                        val itSec = immobilityTimeout.toIntOrNull() ?: rules.immobilityTimeoutSec
                        val hHrMin = helmetHrMin.toIntOrNull() ?: rules.helmetHrMin
                        val hHrMax = helmetHrMax.toIntOrNull() ?: rules.helmetHrMax
                        val hSpo2 = helmetSpo2Min.toIntOrNull() ?: rules.helmetSpo2Min
                        val hGas = helmetGasMargin.toIntOrNull() ?: rules.helmetGasMargin
                        val hObst = helmetObstacleCm.toIntOrNull() ?: rules.helmetObstacleCm

                        SafetyRulesManager.updateRules(
                            rules.copy(
                                tempHighCritical = th,
                                tempLowWarning = tl,
                                pressureHighWarning = ph,
                                pressureLowWarning = pl,
                                humidityHighWarning = hh,
                                humidityLowWarning = hl,
                                fallDetectionEnabled = fallEnabled,
                                immobilityTimeoutSec = itSec,
                                sosBuzzerEnabled = sosBuzzer,
                                helmetHrMin = hHrMin,
                                helmetHrMax = hHrMax,
                                helmetSpo2Min = hSpo2,
                                helmetGasMargin = hGas,
                                helmetObstacleCm = hObst
                            )
                        )
                        hasUnsavedChanges = false
                        Toast.makeText(context, "Safety rules updated and applied to live telemetry!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1.2f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply Rules", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val osha = SafetyRulesManager.resetToOshaDefaults()
                        tempHigh = osha.tempHighCritical.toString()
                        tempLow = osha.tempLowWarning.toString()
                        presHigh = osha.pressureHighWarning.toString()
                        presLow = osha.pressureLowWarning.toString()
                        humHigh = osha.humidityHighWarning.toString()
                        humLow = osha.humidityLowWarning.toString()
                        fallEnabled = osha.fallDetectionEnabled
                        immobilityTimeout = osha.immobilityTimeoutSec.toString()
                        sosBuzzer = osha.sosBuzzerEnabled
                        helmetHrMin = osha.helmetHrMin.toString()
                        helmetHrMax = osha.helmetHrMax.toString()
                        helmetSpo2Min = osha.helmetSpo2Min.toString()
                        helmetGasMargin = osha.helmetGasMargin.toString()
                        helmetObstacleCm = osha.helmetObstacleCm.toString()
                        hasUnsavedChanges = false
                        Toast.makeText(context, "Thresholds reset to standard OSHA limits", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(0.9f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                ) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("OSHA Default", fontSize = 11.sp)
                }
            }
        }

        // Section 3: Temperature Rules
        item {
            ThresholdCategoryCard(
                title = "TEMPERATURE ALERT THRESHOLDS",
                icon = Icons.Default.Thermostat,
                accentColor = hazardColor,
                description = "Define upper heat exhaustion limits and low ambient temperature warnings."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tempHigh,
                            onValueChange = {
                                tempHigh = it
                                hasUnsavedChanges = true
                            },
                            label = { Text("High Critical Limit", fontSize = 11.sp) },
                            suffix = { Text("°C", color = hazardColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = tempLow,
                            onValueChange = {
                                tempLow = it
                                hasUnsavedChanges = true
                            },
                            label = { Text("Low Cold Warning", fontSize = 11.sp) },
                            suffix = { Text("°C", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Text(
                        text = "Breaching either limit immediately sounds the Emergency Siren Alarm! (High > $tempHigh°C, Low < $tempLow°C).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 4: Atmospheric Pressure Rules
        item {
            ThresholdCategoryCard(
                title = "PRESSURE (BAROMETRIC) THRESHOLDS",
                icon = Icons.Default.Speed,
                accentColor = amberColor,
                description = "Emergency siren sounds if barometric pressure exceeds hyperbaric high or drops into depressurization low."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = presHigh,
                            onValueChange = {
                                presHigh = it
                                hasUnsavedChanges = true
                            },
                            label = { Text("Critical High Pressure", fontSize = 11.sp) },
                            suffix = { Text("hPa", color = amberColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = presLow,
                            onValueChange = {
                                presLow = it
                                hasUnsavedChanges = true
                            },
                            label = { Text("Critical Low Pressure", fontSize = 11.sp) },
                            suffix = { Text("hPa", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Text(
                        text = "Emergency siren triggers if pressure > $presHigh hPa or < $presLow hPa (Nominal: ~1013 hPa).",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 5: Humidity Rules
        item {
            ThresholdCategoryCard(
                title = "RELATIVE HUMIDITY THRESHOLDS",
                icon = Icons.Default.WaterDrop,
                accentColor = primaryColor,
                description = "Emergency siren sounds if relative humidity exceeds condensing limit or drops below static dryness limit."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = humHigh,
                            onValueChange = {
                                humHigh = it
                                hasUnsavedChanges = true
                            },
                            label = { Text("Critical High Humidity", fontSize = 11.sp) },
                            suffix = { Text("%", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = humLow,
                            onValueChange = {
                                humLow = it
                                hasUnsavedChanges = true
                            },
                            label = { Text("Critical Low Humidity", fontSize = 11.sp) },
                            suffix = { Text("%", color = amberColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Text(
                        text = "Emergency siren triggers if humidity > $humHigh% or < $humLow%.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 6: Motion & Fall Alert Rules
        item {
            ThresholdCategoryCard(
                title = "MOTION & IMPACT SAFETY ENFORCEMENT",
                icon = Icons.Default.DirectionsRun,
                accentColor = emeraldColor,
                description = "IMU accelerometer fall detection, zero-motion timeout, and emergency buzzer behavior."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Fall detection toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automated Fall Detection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else TextDark
                            )
                            Text(
                                text = "Instantly triggers emergency dispatcher alert upon sudden deceleration/impact",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fallEnabled,
                            onCheckedChange = {
                                fallEnabled = it
                                hasUnsavedChanges = true
                            }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Immobility Timeout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Man-Down Immobility Timeout",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else TextDark
                            )
                            Text(
                                text = "Trigger alert if worker remains stationary with zero motion detected",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = immobilityTimeout,
                            onValueChange = {
                                immobilityTimeout = it
                                hasUnsavedChanges = true
                            },
                            suffix = { Text("sec", fontSize = 12.sp) },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // SOS Vest Hardware Buzzer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hardware Emergency Buzzer & Strobe",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else TextDark
                            )
                            Text(
                                text = "Sound physical vest alarm and beacon LEDs when SOS button is pressed",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = sosBuzzer,
                            onCheckedChange = {
                                sosBuzzer = it
                                hasUnsavedChanges = true
                            }
                        )
                    }
                }
            }
        }

        // Section 7: Smart Helmet - vital signs
        item {
            ThresholdCategoryCard(
                title = "HELMET VITAL SIGNS THRESHOLDS",
                icon = Icons.Default.Favorite,
                accentColor = hazardColor,
                description = "MAX30100 heart rate and blood oxygen (SpO₂) limits checked on every helmet reading."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = helmetHrMin,
                            onValueChange = { helmetHrMin = it; hasUnsavedChanges = true },
                            label = { Text("Min Heart Rate", fontSize = 11.sp) },
                            suffix = { Text("bpm", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = helmetHrMax,
                            onValueChange = { helmetHrMax = it; hasUnsavedChanges = true },
                            label = { Text("Max Heart Rate", fontSize = 11.sp) },
                            suffix = { Text("bpm", color = hazardColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = helmetSpo2Min,
                        onValueChange = { helmetSpo2Min = it; hasUnsavedChanges = true },
                        label = { Text("Minimum Blood Oxygen (SpO₂)", fontSize = 11.sp) },
                        suffix = { Text("%", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Text(
                        text = "Alert if heart rate is outside $helmetHrMin–$helmetHrMax bpm or SpO₂ is below $helmetSpo2Min% for 3 consecutive readings.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 8: Smart Helmet - gas and obstacle
        item {
            ThresholdCategoryCard(
                title = "HELMET GAS & OBSTACLE THRESHOLDS",
                icon = Icons.Default.Sensors,
                accentColor = amberColor,
                description = "MQ-2 gas margin above the clean-air baseline and ultrasonic obstacle distance."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = helmetGasMargin,
                            onValueChange = { helmetGasMargin = it; hasUnsavedChanges = true },
                            label = { Text("Gas Margin Above Baseline", fontSize = 11.sp) },
                            suffix = { Text("ADC", color = amberColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = helmetObstacleCm,
                            onValueChange = { helmetObstacleCm = it; hasUnsavedChanges = true },
                            label = { Text("Obstacle Distance", fontSize = 11.sp) },
                            suffix = { Text("cm", color = hazardColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    Text(
                        text = "Gas alert when level exceeds baseline + $helmetGasMargin. Obstacle alert when an object (left, front or right) is closer than $helmetObstacleCm cm.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ThresholdCategoryCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    description: String,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark

    GlassCard(
        borderColor = accentColor.copy(alpha = 0.4f),
        contentPadding = PaddingValues(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(16.dp))
                }

                Column {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = description,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            content()
        }
    }
}
