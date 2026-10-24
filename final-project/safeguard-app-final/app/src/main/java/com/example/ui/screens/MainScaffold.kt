package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AiUiState
import com.example.data.WorkerProfileRepository
import com.example.manager.EmergencySirenManager
import com.example.manager.HelmetTelemetryManager
import com.example.manager.VestTelemetryManager
import com.example.model.WorkerProfile
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class NavDestination(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    LIVE_CHART("Live Chart", Icons.Default.ShowChart),
    MAP_TRACKING("Map Tracking", Icons.Default.Map),
    WORKER_PROFILES("Worker Profiles", Icons.Default.Badge),
    SAFETY_RULES("Safety Rules", Icons.Default.Gavel),
    ALERTS("Alerts", Icons.Default.Warning),
    REPORT("Report", Icons.Default.Assessment),
    AI_ANALYZE("AI Analyze", Icons.Default.AutoAwesome),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    profile: WorkerProfile = WorkerProfile(),
    onThemeToggle: (isDark: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(NavDestination.DASHBOARD) }
    // Floating "Ask SafeGuard" chat panel (same idea as the website's floating widget)
    var chatOpen by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val vestData by VestTelemetryManager.vestData.collectAsState()
    val activeAlerts by VestTelemetryManager.activeAlerts.collectAsState()
    val helmetAlerts by HelmetTelemetryManager.activeAlerts.collectAsState()
    val dynamicWorker by WorkerProfileRepository.activeWorker.collectAsState()
    val currentProfile = dynamicWorker

    val isSirenActive by EmergencySirenManager.isAlarmActive.collectAsState()
    val emergencyReason by EmergencySirenManager.currentEmergencyReason.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || (currentScreen != NavDestination.MAP_TRACKING && currentScreen != NavDestination.LIVE_CHART),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Drawer Header with Exact Logo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_logo),
                                contentDescription = "SafeGuard Logo",
                                modifier = Modifier.size(52.dp),
                                contentScale = ContentScale.Fit
                            )

                            Column {
                                Text(
                                    text = "SafeGuard",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "LoRa Safety Hub • ESP32",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Monitored Worker Profile Badge in Drawer
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (vestData.isOnline) {
                                                if (isDark) EmeraldOnline else CommandLightEmerald
                                            } else {
                                                if (isDark) OfflineRed else CommandLightRed
                                            }
                                        )
                                )
                                Column {
                                    Text(
                                        text = "${currentProfile.workerName} (${currentProfile.workerId})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Vest: ${currentProfile.assignedVestId} • Node: ${currentProfile.loraNode} • ${if (vestData.isOnline) "Active" else "Offline"}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                        // Navigation Items List
                        NavDestination.values().forEach { destination ->
                            val isSelected = currentScreen == destination
                            val alertCount = when (destination) {
                                NavDestination.ALERTS -> activeAlerts.size + helmetAlerts.size
                                else -> 0
                            }

                            val highlightBrush = if (isDark) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        CyanNeon.copy(alpha = 0.25f),
                                        TealAccent.copy(alpha = 0.15f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFE0F2FE),
                                        Color(0xFFF0FDF4)
                                    )
                                )
                            }
                            val highlightBorder = if (isDark) CyanNeon.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

                            val itemModifier = if (isSelected) {
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(highlightBrush)
                                    .border(1.dp, highlightBorder, RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            }

                            Row(
                                modifier = itemModifier
                                    .clickable {
                                        chatOpen = false
                                        currentScreen = destination
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    val iconTint = if (isSelected) {
                                        if (isDark) CyanNeon else MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val textColor = if (isSelected) {
                                        if (isDark) Color.White else MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.title,
                                        tint = iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = destination.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = textColor
                                    )
                                }

                                if (alertCount > 0) {
                                    Surface(
                                        color = if (isDark) HazardCrimson else CommandLightRed,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "$alertCount",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Drawer Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "Smart Industrial Safety Vest v1.8",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ESP32 LoRa Transceiver • RTDB Link",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    ) {
        val isDark = MaterialTheme.colorScheme.background == CommandNavyDark

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
        ) {
            // Same page background as the website (radial cyan/purple glow + faint grid)
            com.example.ui.components.WebPageBackground(
                isDark = isDark,
                modifier = Modifier.fillMaxSize()
            )

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    // Floating Pill Top Bar ("SafeGuard") matching design reference
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        val topBarBg = if (isDark) Color(0xCC061224) else Color(0xCCEBF4FC)
                        val topBarBorder = if (isDark) Color(0xFF00E5FF).copy(alpha = 0.55f) else Color(0xFF0284C7).copy(alpha = 0.8f)
                        val topBarTitleColor = if (isDark) Color.White else Color(0xFF0F172A)
                        val topBarIconTint = if (isDark) Color(0xFF00E5FF) else Color(0xFF0284C7)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50),
                            color = topBarBg,
                            border = androidx.compose.foundation.BorderStroke(1.4.dp, topBarBorder),
                            shadowElevation = if (isDark) 0.dp else 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Open Drawer Menu",
                                        tint = topBarIconTint
                                    )
                                }

                                Text(
                                    text = "SafeGuard",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.6.sp,
                                    color = topBarTitleColor
                                )

                                // Online status pill with wifi icon
                                OnlineStatusPill(
                                    isOnline = vestData.isOnline
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Ambient HUD decorations only on Dashboard for background sci-fi vibe without overlaying forms/lists
                    if (currentScreen == NavDestination.DASHBOARD) {
                        // Top-right dot matrix
                        DotMatrixHud(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 16.dp)
                        )

                        // Bottom-left radar
                        RadarReticleHud(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = 24.dp)
                                .size(60.dp)
                        )

                        // Bottom-right telemetry lines
                        TelemetryLinesHud(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 12.dp)
                                .size(width = 80.dp, height = 24.dp)
                        )
                    }

                    // Foreground Screens
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isSirenActive) {
                            Surface(
                                color = Color(0xFFDC2626),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "SIREN & ALARM ACTIVE",
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = emergencyReason.ifEmpty { "Critical Incident In Progress!" },
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            EmergencySirenManager.silenceAlarm()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color(0xFFDC2626)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = "SILENCE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentScreen) {
                                NavDestination.DASHBOARD -> DashboardScreen(profile = currentProfile)
                                NavDestination.LIVE_CHART -> LiveChartScreen()
                                NavDestination.MAP_TRACKING -> MapTrackingScreen(profile = currentProfile)
                                NavDestination.WORKER_PROFILES -> WorkerProfilesScreen(
                                    onWorkerSelected = {
                                        // Worker switched
                                    }
                                )
                                NavDestination.SAFETY_RULES -> SafetyRulesScreen()
                                NavDestination.ALERTS -> AlertsScreen()
                                NavDestination.REPORT -> ReportScreen(profile = currentProfile)
                                NavDestination.AI_ANALYZE -> AiAnalysisScreen()
                                NavDestination.SETTINGS -> SettingsScreen(
                                    profile = currentProfile,
                                    onThemeToggle = onThemeToggle,
                                    onLogout = onLogout
                                )
                            }
                        }
                    }
                }
            }

            // ===== Floating "Ask SafeGuard" - visible on every screen (like the website) =====
            // Hidden only while the chat panel is open and while the keyboard is up.
            val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val showFab = !chatOpen && !imeOpen

            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
                exit = fadeOut() + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                AskSafeGuardFab(
                    containerSize = rootSize,
                    onClick = { chatOpen = true }
                )
            }

            // Chat panel that opens over the current page (the user stays on the same screen)
            BackHandler(enabled = chatOpen) { chatOpen = false }

            AnimatedVisibility(
                visible = chatOpen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // dim background; tapping it closes the chat
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { chatOpen = false }
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(12.dp)
                            .fillMaxWidth()
                            .heightIn(max = 580.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) CyanNeon.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        ),
                        shadowElevation = 16.dp
                    ) {
                        AiAssistantScreen(
                            onOpenAnalysis = { hours ->
                                chatOpen = false
                                AiUiState.pendingAnalyzeHours = hours
                                currentScreen = NavDestination.AI_ANALYZE
                            },
                            onClose = { chatOpen = false },
                            handleIme = false
                        )
                    }
                }
            }
        }
    }
}
