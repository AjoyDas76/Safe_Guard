package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AiUiState
import com.example.data.PrefsManager
import com.example.data.WorkerProfileRepository
import com.example.manager.EmergencySirenManager
import com.example.manager.HelmetTelemetryManager
import com.example.manager.VestTelemetryManager
import com.example.model.WorkerProfile
import com.example.ui.components.*
import com.example.ui.theme.*

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

/** The 4 screens that live directly on the floating dock; everything else is under "More". */
private val DockDestinations = listOf(
    NavDestination.DASHBOARD, NavDestination.LIVE_CHART, NavDestination.MAP_TRACKING, NavDestination.ALERTS
)
private val MoreDestinations = listOf(
    NavDestination.WORKER_PROFILES, NavDestination.SAFETY_RULES, NavDestination.REPORT,
    NavDestination.AI_ANALYZE, NavDestination.SETTINGS
)

private fun dockLabel(d: NavDestination) = when (d) {
    NavDestination.DASHBOARD -> "Dashboard"
    NavDestination.LIVE_CHART -> "Live"
    NavDestination.MAP_TRACKING -> "Map"
    NavDestination.ALERTS -> "Alerts"
    else -> d.title
}

private val DockHeight = 68.dp
/** Space the floating dock needs below the content (dock + its margins). */
private val DockReserved = 88.dp

@Composable
fun MainScaffold(
    profile: WorkerProfile = WorkerProfile(),
    onThemeToggle: (isDark: Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(NavDestination.DASHBOARD) }
    var moreOpen by remember { mutableStateOf(false) }
    // Floating "Ask SafeGuard" chat panel (same idea as the website's floating widget)
    var chatOpen by remember { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    val vestData by VestTelemetryManager.vestData.collectAsState()
    val activeAlerts by VestTelemetryManager.activeAlerts.collectAsState()
    val helmetAlerts by HelmetTelemetryManager.activeAlerts.collectAsState()
    val dynamicWorker by WorkerProfileRepository.activeWorker.collectAsState()
    val currentProfile = dynamicWorker

    val isSirenActive by EmergencySirenManager.isAlarmActive.collectAsState()
    val emergencyReason by EmergencySirenManager.currentEmergencyReason.collectAsState()

    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val alertCount = activeAlerts.size + helmetAlerts.size

    fun toggleTheme() {
        val next = !isDark
        PrefsManager.getInstance(context).isDarkMode = next
        onThemeToggle(next)
    }

    // Where to return to when leaving a screen that was opened from "More" (Back button).
    var returnScreen by remember { mutableStateOf<NavDestination?>(null) }
    fun navigateTo(dest: NavDestination) {
        if (dest in MoreDestinations) {
            if (currentScreen !in MoreDestinations) returnScreen = currentScreen
        } else {
            returnScreen = null
        }
        currentScreen = dest
    }

    // Back on a main (dock) screen asks for confirmation before leaving the app
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = !moreOpen && !chatOpen && currentScreen !in MoreDestinations && !showExitDialog) {
        showExitDialog = true
    }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit SafeGuard?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    var c: android.content.Context = context
                    while (c is android.content.ContextWrapper) {
                        if (c is android.app.Activity) { c.finish(); break }
                        c = c.baseContext
                    }
                }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Stay") }
            }
        )
    }

    BackHandler(enabled = moreOpen) { moreOpen = false }
    BackHandler(enabled = !moreOpen && !chatOpen && currentScreen in MoreDestinations) {
        currentScreen = returnScreen ?: NavDestination.DASHBOARD
        returnScreen = null
    }

    // the dock floats over the content (nothing is reserved behind it); screens add this as bottom padding
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockInset = DockReserved + navBottom

    CompositionLocalProvider(LocalDockInset provides dockInset) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
    ) {
        // Soft page background (the glass picks the colour blooms up)
        WebPageBackground(isDark = isDark, modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidTopBar(
                    isDark = isDark,
                    isOnline = vestData.isOnline,
                    onToggleTheme = { toggleTheme() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Foreground Screens
                Column(modifier = Modifier.fillMaxSize()) {
                        if (isSirenActive) {
                            Surface(
                                color = Color(0xFFDC2626),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(20.dp),
                                shadowElevation = 0.dp
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

        // ===== "More" sheet (floats just above the dock; the dock itself stays tappable) =====
        if (moreOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isDark) 0.40f else 0.22f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { moreOpen = false }
            )
        }
        AnimatedVisibility(
            visible = moreOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = DockReserved + 6.dp)
        ) {
            MoreSheet(
                isDark = isDark,
                profile = currentProfile,
                isOnline = vestData.isOnline,
                currentScreen = currentScreen,
                onSelect = { dest ->
                    chatOpen = false
                    moreOpen = false
                    navigateTo(dest)
                },
                onToggleTheme = { toggleTheme() }
            )
        }

        // ===== Floating glass dock - drawn over the content, nothing solid behind it =====
        LiquidDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            isDark = isDark,
            selectedIndex = if (moreOpen) DockDestinations.size
                else DockDestinations.indexOf(currentScreen).let { if (it < 0) DockDestinations.size else it },
            alertCount = alertCount,
            onSelect = { index ->
                chatOpen = false
                if (index < DockDestinations.size) {
                    moreOpen = false
                    navigateTo(DockDestinations[index])
                } else {
                    moreOpen = !moreOpen
                }
            }
        )

        // ===== Floating "Ask SafeGuard" - visible on every screen (like the website) =====
        // Hidden only while the chat panel is open and while the keyboard is up.
        val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val showFab = !chatOpen && !imeOpen && !moreOpen

        AnimatedVisibility(
            visible = showFab,
            enter = fadeIn() + scaleIn(initialScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(1f, 1f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = DockReserved + 8.dp)
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
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) CyanNeon.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        ),
                        shadowElevation = 0.dp
                    ) {
                        AiAssistantScreen(
                            onOpenAnalysis = { hours ->
                                chatOpen = false
                                AiUiState.pendingAnalyzeHours = hours
                                navigateTo(NavDestination.AI_ANALYZE)
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

// =============================================================================================
//  Liquid Glass UI pieces
// =============================================================================================

@Composable
private fun LiquidTopBar(isDark: Boolean, isOnline: Boolean, onToggleTheme: () -> Unit) {
    val accent = if (isDark) CyanNeon else CommandLightPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .webGlass(isDark, shape = RoundedCornerShape(50), cornerRadius = 40.dp, topLine = false)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.img_logo),
                    contentDescription = "SafeGuard Logo",
                    modifier = Modifier.padding(start = 4.dp).size(32.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "SafeGuard",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    color = webTextMain(isDark)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Dark / Light quick switch
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (isDark) 0.18f else 0.12f))
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDark) "Switch to light theme" else "Switch to dark theme",
                        tint = accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
                OnlineStatusPill(isOnline = isOnline)
            }
        }
    }
}

/**
 * Floating glass dock (the reference "Dock v2"): a rounded frosted bar with a bright "liquid" pill that
 * glides under the selected tab. The pill position is read only in the draw phase, so animating it
 * never recomposes anything (smooth at 120 Hz).
 */
@Composable
private fun LiquidDock(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    selectedIndex: Int,
    alertCount: Int,
    onSelect: (Int) -> Unit
) {
    val accent = if (isDark) CyanNeon else CommandLightPrimary
    val shape = RoundedCornerShape(36.dp)
    val count = DockDestinations.size + 1
    val indicator = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "dockIndicator"
    )
    // one fixed label size for every tab (ignores the phone's font-size setting) so "Dashboard" always fits inside the pill
    val dockLabelSize = with(LocalDensity.current) { 8.5.dp.toSp() }
    val barFill = if (isDark) Color(0xCC1C1C24) else Color(0xE6EEEBF4)
    val pillFill = if (isDark) Color(0x33FFFFFF) else Color(0xFFFFFFFF)
    val pillEdge = if (isDark) Color(0x40FFFFFF) else Color(0x1F3A3354)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DockHeight)
                .webHalo(
                    shadowColor = if (isDark) Color(0x80000000) else Color(0x333A3354),
                    glowColor = null,
                    cornerRadius = 36.dp
                )
                .clip(shape)
                .background(barFill)
                .border(1.dp, webBorderBrush(isDark), shape)
        ) {
            // sliding liquid pill (draw-phase only)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val itemW = size.width / count
                        val padX = 2.dp.toPx()
                        val padY = 6.dp.toPx()
                        val r = (size.height - padY * 2) / 2f
                        val topLeft = Offset(indicator.value * itemW + padX, padY)
                        val sz = Size(itemW - padX * 2, size.height - padY * 2)
                        drawRoundRect(pillFill, topLeft, sz, CornerRadius(r, r))
                        drawRoundRect(pillEdge, topLeft, sz, CornerRadius(r, r), style = Stroke(1.dp.toPx()))
                    }
            )
            Row(modifier = Modifier.fillMaxSize()) {
                val items = DockDestinations.map { dockLabel(it) to it.icon } + ("More" to Icons.Default.GridView)
                items.forEachIndexed { index, (label, icon) ->
                    val selected = index == selectedIndex
                    val tint = if (selected) accent else webTextMain(isDark).copy(alpha = 0.88f)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
                            if (label == "Alerts" && alertCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 7.dp, y = (-4).dp)
                                        .defaultMinSize(minWidth = 15.dp, minHeight = 15.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) HazardCrimson else CommandLightRed)
                                        .padding(horizontal = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$alertCount", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        Text(
                            text = label,
                            fontSize = dockLabelSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreSheet(
    isDark: Boolean,
    profile: WorkerProfile,
    isOnline: Boolean,
    currentScreen: NavDestination,
    onSelect: (NavDestination) -> Unit,
    onToggleTheme: () -> Unit
) {
    val accent = if (isDark) CyanNeon else CommandLightPrimary
    val online = if (isDark) EmeraldOnline else CommandLightEmerald
    val offline = if (isDark) OfflineRed else CommandLightRed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // fully solid panel (no see-through), so the page behind never shows through the text
            .webHalo(shadowColor = if (isDark) Color(0x99000000) else Color(0x333A3354), glowColor = null, cornerRadius = 30.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(if (isDark) Color(0xFF1A1A22) else Color(0xFFFFFFFF))
            .border(1.dp, webBorderBrush(isDark), RoundedCornerShape(30.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // monitored worker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (isDark) Color(0x1AFFFFFF) else Color(0x0F3A3354))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (isOnline) online else offline))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${profile.workerName} (${profile.workerId})",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = webTextMain(isDark), maxLines = 1
                )
                Text(
                    "Vest: ${profile.assignedVestId} • Node: ${profile.loraNode} • ${if (isOnline) "Active" else "Offline"}",
                    fontSize = 10.5.sp, color = webTextMuted(isDark), maxLines = 1
                )
            }
        }

        // screens
        MoreDestinations.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { dest ->
                    val selected = dest == currentScreen
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) accent.copy(alpha = if (isDark) 0.22f else 0.14f) else if (isDark) Color(0x14FFFFFF) else Color(0x0D3A3354))
                            .border(1.dp, if (selected) accent.copy(alpha = 0.7f) else webBorderSubtle(isDark), RoundedCornerShape(20.dp))
                            .clickable { onSelect(dest) }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(dest.icon, contentDescription = dest.title, tint = if (selected) accent else webTextMain(isDark), modifier = Modifier.size(26.dp))
                        Text(
                            dest.title,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) accent else webTextMain(isDark),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // theme switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onToggleTheme() }
                .background(if (isDark) Color(0x14FFFFFF) else Color(0x0D3A3354))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(if (isDark) "Dark theme" else "Light theme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = webTextMain(isDark))
            }
            Switch(
                checked = isDark,
                onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFC9C6D2)
                )
            )
        }

        Text(
            "SafeGuard v1.3",
            fontSize = 10.sp, color = webTextMuted(isDark), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}
