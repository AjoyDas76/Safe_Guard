package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.GeoFenceManager
import com.example.manager.BreadcrumbsAndBeaconManager
import com.example.manager.VestTelemetryManager
import com.example.model.DangerZone
import com.example.model.WorkerProfile
import com.example.ui.components.GlassCard
import com.example.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTrackingScreen(
    profile: WorkerProfile = WorkerProfile()
) {
    val vestData by VestTelemetryManager.vestData.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val dangerZones by GeoFenceManager.dangerZones.collectAsState()
    val breachedZones by GeoFenceManager.breachedZones.collectAsState()
    val nearestDangerZone by GeoFenceManager.nearestDangerZone.collectAsState()

    val breadcrumbs by BreadcrumbsAndBeaconManager.breadcrumbs.collectAsState()
    val lastKnownLocation by BreadcrumbsAndBeaconManager.lastKnownLocation.collectAsState()
    val isTrailVisible by BreadcrumbsAndBeaconManager.isTrailVisible.collectAsState()
    val isVoiceAlertEnabled by BreadcrumbsAndBeaconManager.isVoiceAlertEnabled.collectAsState()

    var googleMapInstance by remember { mutableStateOf<GoogleMap?>(null) }
    var workerMarker by remember { mutableStateOf<Marker?>(null) }
    val zoneCircles = remember { mutableStateListOf<Circle>() }
    val zoneMarkers = remember { mutableStateListOf<Marker>() }

    var breadcrumbsPolyline by remember { mutableStateOf<Polyline?>(null) }
    val breadcrumbMarkers = remember { mutableStateListOf<Marker>() }
    var lastKnownMarker by remember { mutableStateOf<Marker?>(null) }
    var lastKnownCircle by remember { mutableStateOf<Circle?>(null) }

    // User requested Satellite view: default to HYBRID (satellite imagery with road/label overlays)
    var currentMapType by remember { mutableStateOf(GoogleMap.MAP_TYPE_HYBRID) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    val isDark = MaterialTheme.colorScheme.background == CommandNavyDark
    val primaryAccent = if (isDark) CyanNeon else MaterialTheme.colorScheme.primary
    val onlineColor = if (isDark) EmeraldOnline else CommandLightEmerald
    val offlineColor = if (isDark) OfflineRed else CommandLightRed

    // UI Dialog & BottomSheet state
    var showAddZoneDialog by remember { mutableStateOf(false) }
    var showZoneManagerSheet by remember { mutableStateOf(false) }
    var pendingZoneCenter by remember { mutableStateOf<LatLng?>(null) }
    var isHudExpanded by remember { mutableStateOf(false) }

    val workerLatLng = remember(vestData.gps.latitude, vestData.gps.longitude) {
        val lat = if (vestData.gps.latitude != 0.0) vestData.gps.latitude else 23.8103
        val lng = if (vestData.gps.longitude != 0.0) vestData.gps.longitude else 90.4125
        LatLng(lat, lng)
    }

    // Sync map type when changed
    LaunchedEffect(currentMapType, googleMapInstance) {
        googleMapInstance?.mapType = currentMapType
    }

    // Render danger zones
    LaunchedEffect(dangerZones, googleMapInstance) {
        val map = googleMapInstance ?: return@LaunchedEffect
        zoneCircles.forEach { it.remove() }
        zoneCircles.clear()
        zoneMarkers.forEach { it.remove() }
        zoneMarkers.clear()

        dangerZones.forEach { zone ->
            val center = LatLng(zone.centerLat, zone.centerLng)
            val circle = map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(zone.radiusMeters)
                    .strokeColor(android.graphics.Color.argb(220, 220, 38, 38)) // Red border
                    .strokeWidth(4f)
                    .fillColor(android.graphics.Color.argb(60, 239, 68, 68)) // Translucent red fill
            )
            zoneCircles.add(circle)

            // Add marker for zone title
            val marker = map.addMarker(
                MarkerOptions()
                    .position(center)
                    .title("⚠️ ${zone.name}")
                    .snippet("${zone.hazardType} (${zone.radiusMeters.toInt()}m radius)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            marker?.let { zoneMarkers.add(it) }
        }
    }

    // Update marker whenever position changes
    LaunchedEffect(workerLatLng, googleMapInstance) {
        val map = googleMapInstance ?: return@LaunchedEffect
        if (workerMarker == null) {
            val markerOptions = MarkerOptions()
                .position(workerLatLng)
                .title("${profile.workerName} (${profile.workerId})")
                .snippet("Status: ${vestData.status.motion_state}")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            workerMarker = map.addMarker(markerOptions)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(workerLatLng, 16f))
        } else {
            workerMarker?.position = workerLatLng
            workerMarker?.snippet = "Status: ${vestData.status.motion_state}"
        }
    }

    // Render breadcrumbs trajectory trail
    LaunchedEffect(breadcrumbs, isTrailVisible, googleMapInstance) {
        val map = googleMapInstance ?: return@LaunchedEffect
        breadcrumbsPolyline?.remove()
        breadcrumbsPolyline = null
        breadcrumbMarkers.forEach { it.remove() }
        breadcrumbMarkers.clear()

        if (isTrailVisible && breadcrumbs.size >= 2) {
            val polylineOptions = PolylineOptions()
                .addAll(breadcrumbs.map { LatLng(it.latitude, it.longitude) })
                .width(8f)
                .color(android.graphics.Color.argb(225, 6, 182, 212)) // High-tech glowing cyan trail
                .geodesic(true)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .jointType(JointType.ROUND)

            breadcrumbsPolyline = map.addPolyline(polylineOptions)

            // Render waypoint markers at key interval points
            breadcrumbs.forEachIndexed { index, pt ->
                if (pt.isDanger || index == 0 || index == breadcrumbs.lastIndex || index % 5 == 0) {
                    val dot = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(pt.latitude, pt.longitude))
                            .title(if (pt.isDanger) "⚠️ Hazard Point #${index + 1}" else "Beacon Ping #${index + 1}")
                            .snippet("${pt.motionState} • ${timeFormat.format(Date(pt.timestamp))}")
                            .icon(BitmapDescriptorFactory.defaultMarker(if (pt.isDanger) BitmapDescriptorFactory.HUE_RED else BitmapDescriptorFactory.HUE_AZURE))
                            .anchor(0.5f, 0.5f)
                    )
                    dot?.let { breadcrumbMarkers.add(it) }
                }
            }
        }
    }

    // Render Last Known Location Pin and Radar Pulse when offline
    LaunchedEffect(lastKnownLocation, vestData.isOnline, googleMapInstance) {
        val map = googleMapInstance ?: return@LaunchedEffect
        lastKnownMarker?.remove()
        lastKnownMarker = null
        lastKnownCircle?.remove()
        lastKnownCircle = null

        if (!vestData.isOnline && lastKnownLocation != null) {
            val lkl = lastKnownLocation!!
            val lklPos = LatLng(lkl.latitude, lkl.longitude)

            lastKnownCircle = map.addCircle(
                CircleOptions()
                    .center(lklPos)
                    .radius(45.0)
                    .strokeColor(android.graphics.Color.argb(220, 239, 68, 68))
                    .strokeWidth(3f)
                    .fillColor(android.graphics.Color.argb(45, 239, 68, 68))
            )

            lastKnownMarker = map.addMarker(
                MarkerOptions()
                    .position(lklPos)
                    .title("📍 LAST KNOWN FIX (${lkl.beaconNode})")
                    .snippet("Lost: ${lkl.getTimeAgo()} • Status: ${lkl.motionState}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Google Map View
        val mapView = remember { MapView(context) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }

                    getMapAsync { map ->
                        googleMapInstance = map
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isScrollGesturesEnabledDuringRotateOrZoom = true
                        map.uiSettings.isZoomControlsEnabled = false
                        map.uiSettings.isCompassEnabled = true
                        map.uiSettings.isMyLocationButtonEnabled = false
                        map.mapType = currentMapType

                        // Click listeners for defining custom danger zones
                        map.setOnMapLongClickListener { point ->
                            pendingZoneCenter = point
                            showAddZoneDialog = true
                        }

                        val markerOptions = MarkerOptions()
                            .position(workerLatLng)
                            .title("${profile.workerName} (${profile.workerId})")
                            .snippet("Status: ${vestData.status.motion_state}")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                        workerMarker = map.addMarker(markerOptions)
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(workerLatLng, 16f))
                    }
                }
            }
        )

        // 2. Compact Map Control Dock at Top-End
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 14.dp),
            shape = RoundedCornerShape(16.dp),
            color = (if (isDark) CommandSurfaceDark else Color.White).copy(alpha = 0.94f),
            border = androidx.compose.foundation.BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f)),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Satellite / Hybrid Mode Toggle
                IconButton(
                    onClick = {
                        currentMapType = if (currentMapType == GoogleMap.MAP_TYPE_HYBRID || currentMapType == GoogleMap.MAP_TYPE_SATELLITE) {
                            GoogleMap.MAP_TYPE_NORMAL
                        } else {
                            GoogleMap.MAP_TYPE_HYBRID
                        }
                        googleMapInstance?.mapType = currentMapType
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (currentMapType == GoogleMap.MAP_TYPE_NORMAL) Icons.Default.Layers else Icons.Default.Satellite,
                        contentDescription = "Map Style",
                        tint = primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Voice Alert Toggle (Green when active, Muted Red when off)
                IconButton(
                    onClick = {
                        val newState = BreadcrumbsAndBeaconManager.toggleVoiceAlert(context)
                        Toast.makeText(
                            context,
                            if (newState) "Voice Warning: ON" else "Voice Warning: MUTED",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isVoiceAlertEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Danger Voice Warning",
                        tint = if (isVoiceAlertEnabled) (if (isDark) EmeraldOnline else CommandLightEmerald) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Breadcrumbs Trail Toggle with Badge
                IconButton(
                    onClick = {
                        val newState = BreadcrumbsAndBeaconManager.toggleTrailVisibility(context)
                        Toast.makeText(
                            context,
                            if (newState) "Trail Path: ON (${breadcrumbs.size})" else "Trail Path: OFF",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (breadcrumbs.isNotEmpty()) {
                                Badge(
                                    containerColor = if (isTrailVisible) primaryAccent else MaterialTheme.colorScheme.outline
                                ) {
                                    Text("${breadcrumbs.size}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Trail Path",
                            tint = if (isTrailVisible) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Danger Zones Management Sheet with Badge
                IconButton(
                    onClick = { showZoneManagerSheet = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (dangerZones.isNotEmpty()) {
                                Badge(containerColor = Color(0xFFDC2626)) {
                                    Text("${dangerZones.size}", fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Danger Zones",
                            tint = if (dangerZones.isNotEmpty()) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick Add Danger Zone at Worker Position
                IconButton(
                    onClick = {
                        pendingZoneCenter = workerLatLng
                        showAddZoneDialog = true
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = "Add Danger Zone",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Divider(
                    modifier = Modifier
                        .width(22.dp)
                        .padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                // Center on Worker
                IconButton(
                    onClick = {
                        googleMapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(workerLatLng, 17f))
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Center on Worker",
                        tint = primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Focus on Last Known Location (Shown when offline and fix available)
                if (!vestData.isOnline && lastKnownLocation != null) {
                    IconButton(
                        onClick = {
                            val pos = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
                            googleMapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 17.5f))
                            Toast.makeText(context, "Centered on Last Known Fix (${lastKnownLocation!!.beaconNode})", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = "Last Known Location",
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 3. Bottom HUD Section (Collapsible Mini Bar & Expanded Detailed Telemetry)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Collapsible Glass Container
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                borderColor = if (breachedZones.isNotEmpty()) Color(0xFFDC2626) else null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Always visible Mini Summary Header Bar (Tapping toggles expand/collapse)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isHudExpanded = !isHudExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Status Dot & Worker ID + Motion
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (vestData.isOnline) onlineColor else offlineColor)
                            )
                            Column {
                                Text(
                                    text = profile.workerName.ifBlank { "Safety Vest" },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (vestData.isOnline) "Motion: ${vestData.status.motion_state}" else "Status: OFFLINE",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Middle: Live Status Chip
                        val breach = breachedZones.firstOrNull()
                        val near = nearestDangerZone
                        when {
                            breach != null -> {
                                Surface(
                                    color = Color(0xFFDC2626),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "🚨 BREACH",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            near != null && near.isApproaching -> {
                                Surface(
                                    color = Color(0xFFF59E0B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "⚠️ NEAR ${near.distanceToEdgeMeters.toInt()}m",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            !vestData.isOnline && lastKnownLocation != null -> {
                                Surface(
                                    color = Color(0xFFF97316).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316))
                                ) {
                                    Text(
                                        text = "📍 LAST FIX",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF97316),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "✓ SAFE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF10B981) else Color(0xFF047857),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        // Right: Expand/Collapse Chevron Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isHudExpanded) "HIDE" else "DETAILS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent
                            )
                            Icon(
                                imageVector = if (isHudExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isHudExpanded) "Collapse HUD" else "Expand HUD",
                                tint = primaryAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Expanded Telemetry View (Animated)
                    AnimatedVisibility(
                        visible = isHudExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.8.dp)

                            // Latitude & Longitude with Fix Timestamp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("LATITUDE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (vestData.isOnline) String.format(Locale.US, "%.6f° N", workerLatLng.latitude) else "---",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("LONGITUDE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (vestData.isOnline) String.format(Locale.US, "%.6f° E", workerLatLng.longitude) else "---",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Fix: ${timeFormat.format(Date(if (vestData.lastPacketTime > 0) vestData.lastPacketTime else System.currentTimeMillis()))}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Danger Zone Alerts
                            if (breachedZones.isNotEmpty()) {
                                val b = breachedZones.first()
                                Surface(
                                    color = Color(0xFFDC2626).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                        Column {
                                            Text(
                                                text = "🚨 DANGER ZONE BREACH: ${b.name}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFFDC2626)
                                            )
                                            Text(
                                                text = "Worker inside ${b.hazardType} hazard zone! Evacuate immediately.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            } else if (nearestDangerZone != null && nearestDangerZone!!.isApproaching) {
                                val n = nearestDangerZone!!
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.NearMe, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                        Column {
                                            Text(
                                                text = "⚠️ NEAR ${n.zone.name} (${n.distanceToEdgeMeters.toInt()}m away)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF59E0B)
                                            )
                                            Text(
                                                text = "Proximity to restricted perimeter. Audio alert active.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            // Last Known Location Card
                            if (!vestData.isOnline && lastKnownLocation != null) {
                                val lkl = lastKnownLocation!!
                                Surface(
                                    color = (if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)).copy(alpha = 0.95f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📍 LAST FIX: ${lkl.beaconNode}",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFF97316)
                                            )
                                            Text(
                                                text = "Lost: ${lkl.getTimeAgo()}",
                                                fontSize = 9.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Worker GPS", "${lkl.latitude}, ${lkl.longitude}")
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copied GPS to clipboard!", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(30.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("COPY GPS", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    try {
                                                        val uri = Uri.parse("google.navigation:q=${lkl.latitude},${lkl.longitude}")
                                                        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                            setPackage("com.google.android.apps.maps")
                                                        }
                                                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                            context.startActivity(mapIntent)
                                                        } else {
                                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${lkl.latitude},${lkl.longitude}?q=${lkl.latitude},${lkl.longitude}")))
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Could not open map navigation", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1.2f).height(30.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("NAVIGATE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            // Footer Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "💡 Tip: Long-press map to place zone",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (breadcrumbs.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                BreadcrumbsAndBeaconManager.clearBreadcrumbs(context)
                                                Toast.makeText(context, "Trail cleared", Toast.LENGTH_SHORT).show()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Clear Trail", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            googleMapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(workerLatLng, 18f))
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Focus Target", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
}

    // 5. Add Custom Danger Zone Dialog
    if (showAddZoneDialog) {
        val center = pendingZoneCenter ?: workerLatLng
        AddDangerZoneDialog(
            initialLat = center.latitude,
            initialLng = center.longitude,
            onDismiss = { showAddZoneDialog = false },
            onConfirm = { name, hazardType, lat, lng, radius, desc ->
                val newZone = DangerZone(
                    id = "custom_zone_${System.currentTimeMillis()}",
                    name = name,
                    hazardType = hazardType,
                    centerLat = lat,
                    centerLng = lng,
                    radiusMeters = radius,
                    description = desc
                )
                GeoFenceManager.addCustomZone(newZone)
                showAddZoneDialog = false
                Toast.makeText(context, "Danger Zone '${name}' added successfully!", Toast.LENGTH_SHORT).show()
                googleMapInstance?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
            }
        )
    }

    // 6. Danger Zones List & Management Bottom Sheet
    if (showZoneManagerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showZoneManagerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DANGER ZONES DIRECTORY",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Restricted hazardous zones triggering real-time alarms",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        pendingZoneCenter = workerLatLng
                        showZoneManagerSheet = false
                        showAddZoneDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFFDC2626))
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                if (dangerZones.isEmpty()) {
                    Text(
                        text = "No active danger zones configured.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(dangerZones, key = { it.id }) { zone ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = zone.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "${zone.hazardType} • Radius: ${zone.radiusMeters.toInt()}m",
                                            fontSize = 11.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Coords: ${String.format(Locale.US, "%.4f, %.4f", zone.centerLat, zone.centerLng)}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (zone.description.isNotEmpty()) {
                                            Text(
                                                text = zone.description,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            googleMapInstance?.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(zone.centerLat, zone.centerLng),
                                                    17f
                                                )
                                            )
                                            showZoneManagerSheet = false
                                        }) {
                                            Icon(Icons.Default.MyLocation, contentDescription = "View on map", tint = primaryAccent)
                                        }

                                        IconButton(onClick = {
                                            GeoFenceManager.removeZone(zone.id)
                                            Toast.makeText(context, "Zone removed", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        GeoFenceManager.resetToDefaults()
                        Toast.makeText(context, "Reset to standard site zones", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Reset to Standard Zones", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun AddDangerZoneDialog(
    initialLat: Double,
    initialLng: Double,
    onDismiss: () -> Unit,
    onConfirm: (name: String, hazardType: String, lat: Double, lng: Double, radius: Double, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hazardType by remember { mutableStateOf("Toxic Gas Risk") }
    var latStr by remember { mutableStateOf(String.format(Locale.US, "%.5f", initialLat)) }
    var lngStr by remember { mutableStateOf(String.format(Locale.US, "%.5f", initialLng)) }
    var radiusMeters by remember { mutableStateOf(100f) }
    var description by remember { mutableStateOf("") }

    val hazardTypes = listOf(
        "Toxic Gas Risk",
        "High Voltage / Transformer",
        "Overhead Crane / Heavy Lift",
        "Deep Trench / Excavation",
        "Chemical Storage",
        "Confined Space Entry"
    )
    var expandedHazardMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = Color(0xFFDC2626))
                Text("Define Danger Zone", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Zone Name (e.g., Boiler Room A)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hazard Type Selector
                Box {
                    OutlinedTextField(
                        value = hazardType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hazard Classification") },
                        trailingIcon = {
                            IconButton(onClick = { expandedHazardMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedHazardMenu = true }
                    )
                    DropdownMenu(
                        expanded = expandedHazardMenu,
                        onDismissRequest = { expandedHazardMenu = false }
                    ) {
                        hazardTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    hazardType = type
                                    expandedHazardMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latStr,
                        onValueChange = { latStr = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngStr,
                        onValueChange = { lngStr = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Radius Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Danger Radius:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${radiusMeters.toInt()} meters", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                    Slider(
                        value = radiusMeters,
                        onValueChange = { radiusMeters = it },
                        valueRange = 20f..500f,
                        steps = 24,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFDC2626),
                            activeTrackColor = Color(0xFFDC2626)
                        )
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Safety Note / Mandate") },
                    placeholder = { Text("e.g. Authorized personnel only") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (name.isNotBlank()) name else "Danger Zone ${hazardType}"
                    val lat = latStr.toDoubleOrNull() ?: initialLat
                    val lng = lngStr.toDoubleOrNull() ?: initialLng
                    onConfirm(finalName, hazardType, lat, lng, radiusMeters.toDouble(), description)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Save Zone", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

