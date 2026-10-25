package com.example.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.GeoFenceManager
import com.example.data.SafetyRulesManager
import com.example.data.WorkerProfileRepository
import com.example.data.local.TelemetryRepository
import com.example.model.*
import com.google.firebase.database.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

object VestTelemetryManager {
    private const val TAG = "VestTelemetryManager"
    private const val RTDB_URL = "https://worker-safety-vest-92b97-default-rtdb.firebaseio.com"
    private const val WATCHDOG_TIMEOUT_MS = 5000L // 5 seconds timeout per requirement

    private var database: FirebaseDatabase? = null
    private var workerRef: DatabaseReference? = null
    private var historyRef: DatabaseReference? = null
    private var valueEventListener: ValueEventListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationHelper: NotificationHelper? = null

    // State flows
    private val _vestData = MutableStateFlow(VestData())
    val vestData: StateFlow<VestData> = _vestData.asStateFlow()

    private val _eventHistory = MutableStateFlow<List<EventSnapshot>>(emptyList())
    val eventHistory: StateFlow<List<EventSnapshot>> = _eventHistory.asStateFlow()

    private val _activeAlerts = MutableStateFlow<List<HazardAlert>>(emptyList())
    val activeAlerts: StateFlow<List<HazardAlert>> = _activeAlerts.asStateFlow()

    private val _chartHistory = MutableStateFlow<List<EventSnapshot>>(emptyList())
    val chartHistory: StateFlow<List<EventSnapshot>> = _chartHistory.asStateFlow()

    private var isSimulating = false
    private var simulationIndex = 0

    // Watchdog runnable
    private val watchdogRunnable = Runnable {
        Log.w(TAG, "Watchdog triggered: No telemetry received for ${WATCHDOG_TIMEOUT_MS / 1000}s -> Setting OFFLINE")
        val wasOnline = _vestData.value.isOnline
        _vestData.value = _vestData.value.copy(isOnline = false)
        _activeAlerts.value = emptyList()
        com.example.data.AlertHistoryManager.onActiveAlertsChanged(emptyList())

        if (wasOnline) {
            val offlineSnapshot = EventSnapshot(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                temperature = 0.0,
                humidity = 0.0,
                pressure = 0.0,
                voltage = 0.0,
                motion_state = "OFFLINE",
                fall_detected = false,
                sos_active = false,
                activeAlerts = listOf("Device Connection Lost (Offline)"),
                isOnline = false
            )
            val currentEvents = _eventHistory.value.toMutableList()
            currentEvents.add(0, offlineSnapshot)
            if (currentEvents.size > 10) {
                _eventHistory.value = currentEvents.take(10)
            } else {
                _eventHistory.value = currentEvents
            }
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        SafetyRulesManager.init(appCtx)
        GeoFenceManager.init(appCtx)
        com.example.data.WorkerProfileRepository.init(appCtx)
        TelemetryRepository.init(appCtx)
        EmergencySirenManager.init(appCtx)
        BreadcrumbsAndBeaconManager.init(appCtx)
        com.example.data.AlertHistoryManager.init(appCtx)

        if (notificationHelper == null) {
            notificationHelper = NotificationHelper(appCtx)
        }

        try {
            database = FirebaseDatabase.getInstance(RTDB_URL)
            workerRef = database?.getReference("worker1")
            historyRef = workerRef?.child("history")

            attachFirebaseListener()
            ensureInitialChartData()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase RTDB: ${e.message}", e)
        }
    }

    private fun attachFirebaseListener() {
        val ref = workerRef ?: return
        if (valueEventListener != null) {
            ref.removeEventListener(valueEventListener!!)
        }

        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.d(TAG, "worker1 snapshot does not exist or empty")
                    return
                }
                parseAndProcessData(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(valueEventListener!!)
    }

    private fun parseAndProcessData(snapshot: DataSnapshot) {
        val now = System.currentTimeMillis()

        // Check packet timestamp if provided by device or gateway node
        val packetTimestampRaw = (snapshot.child("timestamp").value as? Number)?.toLong()
            ?: (snapshot.child("time").value as? Number)?.toLong()
            ?: (snapshot.child("last_seen").value as? Number)?.toLong()
            ?: (snapshot.child("last_updated").value as? Number)?.toLong()
            ?: (snapshot.child("status").child("timestamp").value as? Number)?.toLong()
            ?: (snapshot.child("environment").child("timestamp").value as? Number)?.toLong()

        if (packetTimestampRaw != null) {
            val packetTimestampMillis = if (packetTimestampRaw < 10_000_000_000L) packetTimestampRaw * 1000L else packetTimestampRaw
            val ageMs = Math.abs(now - packetTimestampMillis)
            if (ageMs > WATCHDOG_TIMEOUT_MS) {
                Log.w(TAG, "Packet has stale timestamp ($ageMs ms old). Device is OFFLINE.")
                if (_vestData.value.isOnline) {
                    _vestData.value = _vestData.value.copy(isOnline = false)
                    _activeAlerts.value = emptyList()
                }
                return
            }
        }

        // Reset watchdog timer
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)

        // Parse Alerts
        val alertsSnap = snapshot.child("alerts")
        val fallDetected = alertsSnap.child("fall_detected").getValue(Boolean::class.java) ?: false
        val sosActive = alertsSnap.child("sos_active").getValue(Boolean::class.java) ?: false
        val alerts = AlertsData(fall_detected = fallDetected, sos_active = sosActive)

        // Parse Battery
        val batterySnap = snapshot.child("battery")
        val voltage = (batterySnap.child("voltage").value as? Number)?.toDouble() ?: 3.95
        val battery = BatteryData(voltage = voltage)

        // Parse Environment
        val envSnap = snapshot.child("environment")
        val humidity = (envSnap.child("humidity").value as? Number)?.toDouble() ?: 55.0
        val pressure = (envSnap.child("pressure").value as? Number)?.toDouble() ?: 1013.25
        val temperature = (envSnap.child("temperature").value as? Number)?.toDouble() ?: 28.0
        val environment = EnvironmentData(humidity = humidity, pressure = pressure, temperature = temperature)

        // Parse GPS
        val gpsSnap = snapshot.child("gps")
        val latitude = (gpsSnap.child("latitude").value as? Number)?.toDouble() ?: 23.8103
        val longitude = (gpsSnap.child("longitude").value as? Number)?.toDouble() ?: 90.4125
        val gps = GpsData(latitude = latitude, longitude = longitude)

        // Parse Status
        val statusSnap = snapshot.child("status")
        val motionState = statusSnap.child("motion_state").getValue(String::class.java) ?: "WALKING"
        val status = StatusData(motion_state = motionState)

        val updatedVestData = VestData(
            alerts = alerts,
            battery = battery,
            environment = environment,
            gps = gps,
            status = status,
            lastPacketTime = now,
            isOnline = true
        )
        _vestData.value = updatedVestData

        // Evaluate Hazard Alerts
        val evaluatedAlerts = evaluateHazards(updatedVestData)
        _activeAlerts.value = evaluatedAlerts
        com.example.data.AlertHistoryManager.onActiveAlertsChanged(evaluatedAlerts)
        if (evaluatedAlerts.isNotEmpty()) {
            notificationHelper?.handleAlerts(evaluatedAlerts)
        }

        // Add to In-Memory Event History (Latest 10 snapshots)
        val alertSummaries = evaluatedAlerts.map { it.title }
        val snapshotItem = EventSnapshot(
            id = UUID.randomUUID().toString(),
            timestamp = now,
            temperature = temperature,
            humidity = humidity,
            pressure = pressure,
            voltage = voltage,
            motion_state = motionState,
            fall_detected = fallDetected,
            sos_active = sosActive,
            activeAlerts = alertSummaries
        )

        val currentEvents = _eventHistory.value.toMutableList()
        currentEvents.add(0, snapshotItem)
        if (currentEvents.size > 10) {
            _eventHistory.value = currentEvents.take(10)
        } else {
            _eventHistory.value = currentEvents
        }

        // Add to chart rolling buffer (latest 60 points / 1 minute)
        val currentChart = _chartHistory.value.toMutableList()
        currentChart.add(snapshotItem)
        if (currentChart.size > 60) {
            _chartHistory.value = currentChart.takeLast(60)
        } else {
            _chartHistory.value = currentChart
        }

        // Cache to Room Database for offline persistence
        try {
            val activeWorker = WorkerProfileRepository.activeWorker.value
            TelemetryRepository.get().logTelemetry(updatedVestData, activeWorker, alertSummaries)
        } catch (_: Exception) {}
    }

    /**
     * Alert Rules:
     * - Temperature > 45°C or < 15°C
     * - Humidity > 90% or < 20%
     * - Pressure > 1050 hPa or < 950 hPa
     * - motion_state == "FALL"
     * - fall_detected == true
     * - sos_active == true
     */
    private fun evaluateHazards(data: VestData): List<HazardAlert> {
        val alertsList = mutableListOf<HazardAlert>()

        // 1. SOS Trigger
        if (data.alerts.sos_active) {
            alertsList.add(
                HazardAlert(
                    id = "sos_alert",
                    title = "SOS Emergency Active",
                    description = "Worker activated manual emergency panic button on vest!",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "SOS",
                    triggeredValue = "ACTIVE",
                    safeLimit = "INACTIVE"
                )
            )
        }

        val rules = SafetyRulesManager.rules.value

        // 2. Fall Detection & Motion Hazard
        if (rules.fallDetectionEnabled && (data.alerts.fall_detected || data.status.motion_state.equals("FALL", ignoreCase = true))) {
            alertsList.add(
                HazardAlert(
                    id = "fall_alert",
                    title = "Worker Fall Detected",
                    description = "Accelerometer and IMU detected severe sudden impact/fall!",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "FALL",
                    triggeredValue = "FALL",
                    safeLimit = "WALKING / NORMAL"
                )
            )
        }

        // 3. Temperature Thresholds (Configurable via Safety Rules)
        val temp = data.environment.temperature
        if (temp > rules.tempHighCritical) {
            alertsList.add(
                HazardAlert(
                    id = "temp_high",
                    title = "Extreme High Temperature",
                    description = "Ambient temperature exceeded hazardous heat threshold.",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "TEMPERATURE",
                    triggeredValue = String.format("%.1f°C", temp),
                    safeLimit = "< ${rules.tempHighCritical}°C"
                )
            )
        } else if (temp < rules.tempLowWarning && temp > -50.0) {
            alertsList.add(
                HazardAlert(
                    id = "temp_low",
                    title = "Critical Low Temperature",
                    description = "Ambient temperature dropped below critical cold safety limit.",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "TEMPERATURE",
                    triggeredValue = String.format("%.1f°C", temp),
                    safeLimit = "> ${rules.tempLowWarning}°C"
                )
            )
        }

        // 4. Humidity Thresholds (Configurable via Safety Rules)
        val hum = data.environment.humidity
        if (hum > rules.humidityHighWarning) {
            alertsList.add(
                HazardAlert(
                    id = "hum_high",
                    title = "Critical High Humidity",
                    description = "Relative humidity exceeds safe condensing threshold.",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "HUMIDITY",
                    triggeredValue = String.format("%.1f%%", hum),
                    safeLimit = "< ${rules.humidityHighWarning}%"
                )
            )
        } else if (hum < rules.humidityLowWarning && hum >= 0.0) {
            alertsList.add(
                HazardAlert(
                    id = "hum_low",
                    title = "Critical Low Humidity",
                    description = "Air humidity dropped below safe limit, severe static & dehydration risk.",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "HUMIDITY",
                    triggeredValue = String.format("%.1f%%", hum),
                    safeLimit = "> ${rules.humidityLowWarning}%"
                )
            )
        }

        // 5. Atmospheric Pressure Thresholds (Configurable via Safety Rules)
        val pres = data.environment.pressure
        if (pres > rules.pressureHighWarning) {
            alertsList.add(
                HazardAlert(
                    id = "pres_high",
                    title = "Critical High Pressure",
                    description = "Abnormal hyperbaric pressure detected, exceeding safety limit.",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "PRESSURE",
                    triggeredValue = String.format("%.1f hPa", pres),
                    safeLimit = "< ${rules.pressureHighWarning} hPa"
                )
            )
        } else if (pres < rules.pressureLowWarning && pres > 500.0) {
            alertsList.add(
                HazardAlert(
                    id = "pres_low",
                    title = "Critical Low Pressure",
                    description = "Depressurization hazard! Pressure dropped below safety limit.",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "PRESSURE",
                    triggeredValue = String.format("%.1f hPa", pres),
                    safeLimit = "> ${rules.pressureLowWarning} hPa"
                )
            )
        }

        // 6. Geo-Fencing Breach Check & Beacon Breadcrumbs Tracking
        val breaches = GeoFenceManager.checkWorkerLocation(data.gps.latitude, data.gps.longitude)
        BreadcrumbsAndBeaconManager.recordFix(
            latitude = data.gps.latitude,
            longitude = data.gps.longitude,
            beaconNode = "GW-ALPHA-01",
            motionState = data.status.motion_state,
            isDanger = breaches.isNotEmpty(),
            isOnline = data.isOnline
        )

        if (breaches.isNotEmpty()) {
            val topBreach = breaches.first()
            alertsList.add(
                HazardAlert(
                    id = "geofence_${topBreach.id}",
                    title = "DANGER ZONE BREACH: ${topBreach.name}",
                    description = "Worker has entered ${topBreach.hazardType}! ${topBreach.description}",
                    severity = AlertSeverity.CRITICAL,
                    sensorType = "GEOFENCE",
                    triggeredValue = "INSIDE ${topBreach.name}",
                    safeLimit = "OUTSIDE RESTRICTED AREA"
                )
            )
        }

        // Trigger Siren & Tactile Vibration for ANY Critical Condition (SOS, Fall, Danger Zone, Temperature, Humidity, Pressure)
        val criticalAlert = alertsList.find { it.severity == AlertSeverity.CRITICAL }
        val hasEmergency = data.alerts.sos_active ||
                data.alerts.fall_detected ||
                data.status.motion_state.equals("FALL", ignoreCase = true) ||
                breaches.isNotEmpty() ||
                criticalAlert != null ||
                alertsList.isNotEmpty()

        if (hasEmergency) {
            val reason = when {
                data.alerts.sos_active -> "EMERGENCY SOS PANIC BUTTON PRESSED!"
                data.alerts.fall_detected || data.status.motion_state.equals("FALL", ignoreCase = true) -> "CRITICAL WORKER FALL DETECTED!"
                breaches.isNotEmpty() -> "WORKER ENTERED DANGER ZONE: ${breaches.first().name}"
                criticalAlert != null -> "CRITICAL HAZARD: ${criticalAlert.title.uppercase()} (${criticalAlert.triggeredValue})"
                alertsList.isNotEmpty() -> "CRITICAL HAZARD: ${alertsList.first().title.uppercase()} (${alertsList.first().triggeredValue})"
                else -> "CRITICAL WORKER HAZARD DETECTED!"
            }
            EmergencySirenManager.triggerEmergencyAlarm(reason)
        }

        return alertsList
    }

    fun fetchHistory(startMillis: Long, onResult: (List<EventSnapshot>) -> Unit) {
        val ref = historyRef
        if (ref == null) {
            onResult(generateSyntheticHistory(startMillis))
            return
        }

        ref.orderByChild("timestamp").startAt(startMillis.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<EventSnapshot>()
                    for (child in snapshot.children) {
                        val ts = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
                        val temp = (child.child("temperature").value as? Number)?.toDouble() ?: 28.0
                        val hum = (child.child("humidity").value as? Number)?.toDouble() ?: 55.0
                        val pres = (child.child("pressure").value as? Number)?.toDouble() ?: 1013.0
                        val motion = child.child("motion_state").getValue(String::class.java) ?: "WALKING"
                        val fall = child.child("fall_detected").getValue(Boolean::class.java) ?: false
                        val sos = child.child("sos_active").getValue(Boolean::class.java) ?: false

                        list.add(
                            EventSnapshot(
                                id = child.key ?: UUID.randomUUID().toString(),
                                timestamp = ts,
                                temperature = temp,
                                humidity = hum,
                                pressure = pres,
                                voltage = 3.95,
                                motion_state = motion,
                                fall_detected = fall,
                                sos_active = sos
                            )
                        )
                    }

                    if (list.isEmpty()) {
                        // Fallback to local memory snapshots or baseline points
                        onResult(generateSyntheticHistory(startMillis))
                    } else {
                        onResult(list.sortedByDescending { it.timestamp })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "fetchHistory error: ${error.message}")
                    onResult(generateSyntheticHistory(startMillis))
                }
            })
    }

    private fun generateSyntheticHistory(startMillis: Long): List<EventSnapshot> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<EventSnapshot>()
        val step = (now - startMillis).coerceAtLeast(3600000L) / 12L

        for (i in 0..12) {
            val t = now - (i * step)
            val temp = 27.0 + (Math.sin(i.toDouble()) * 4.0)
            val hum = 58.0 + (Math.cos(i.toDouble()) * 8.0)
            val pres = 1012.0 + (Math.sin(i.toDouble() * 0.5) * 6.0)
            val motion = if (i % 5 == 0) "STATIONARY" else if (i % 7 == 0) "RUNNING" else "WALKING"
            list.add(
                EventSnapshot(
                    id = "hist_$i",
                    timestamp = t,
                    temperature = temp,
                    humidity = hum,
                    pressure = pres,
                    voltage = 3.90 + (0.01 * (12 - i)),
                    motion_state = motion,
                    fall_detected = (i == 4),
                    sos_active = false
                )
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    /**
     * Demo & Testing Helpers
     * Allows the user to simulate telemetry or trigger test hazard alerts
     */
    fun toggleSimulation() {
        isSimulating = !isSimulating
        if (isSimulating) {
            runSimulationStep()
        }
    }

    fun isSimulationActive(): Boolean = isSimulating

    fun simulateFallAlert() {
        val current = _vestData.value
        val updated = current.copy(
            alerts = current.alerts.copy(fall_detected = true),
            status = StatusData(motion_state = "FALL"),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )
        processCustomData(updated)
    }

    fun simulateSosAlert() {
        val current = _vestData.value
        val updated = current.copy(
            alerts = current.alerts.copy(sos_active = true),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )
        processCustomData(updated)
    }

    fun simulateTempAlert(temp: Double = 49.2) {
        val current = _vestData.value
        val updated = current.copy(
            environment = current.environment.copy(temperature = temp),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )
        processCustomData(updated)
    }

    fun simulateHumidityAlert(hum: Double = 94.5) {
        val current = _vestData.value
        val updated = current.copy(
            environment = current.environment.copy(humidity = hum),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )
        processCustomData(updated)
    }

    fun simulatePressureAlert(pres: Double = 1065.0) {
        val current = _vestData.value
        val updated = current.copy(
            environment = current.environment.copy(pressure = pres),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )
        processCustomData(updated)
    }

    fun clearActiveAlerts() {
        val current = _vestData.value
        val updated = current.copy(
            alerts = AlertsData(fall_detected = false, sos_active = false),
            status = StatusData(motion_state = "WALKING"),
            environment = EnvironmentData(humidity = 58.0, pressure = 1013.25, temperature = 27.5),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )
        processCustomData(updated)
    }

    private fun processCustomData(data: VestData) {
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_TIMEOUT_MS)

        _vestData.value = data
        val evAlerts = evaluateHazards(data)
        _activeAlerts.value = evAlerts
        com.example.data.AlertHistoryManager.onActiveAlertsChanged(evAlerts)
        if (evAlerts.isNotEmpty()) {
            notificationHelper?.handleAlerts(evAlerts)
        }

        val snapshot = EventSnapshot(
            id = UUID.randomUUID().toString(),
            timestamp = data.lastPacketTime,
            temperature = data.environment.temperature,
            humidity = data.environment.humidity,
            pressure = data.environment.pressure,
            voltage = data.battery.voltage,
            motion_state = data.status.motion_state,
            fall_detected = data.alerts.fall_detected,
            sos_active = data.alerts.sos_active,
            activeAlerts = evAlerts.map { it.title }
        )

        val curEvents = _eventHistory.value.toMutableList()
        curEvents.add(0, snapshot)
        _eventHistory.value = curEvents.take(10)

        val curChart = _chartHistory.value.toMutableList()
        curChart.add(snapshot)
        _chartHistory.value = curChart.takeLast(60)

        // Cache to Room Database for offline persistence
        try {
            val activeWorker = WorkerProfileRepository.activeWorker.value
            TelemetryRepository.get().logTelemetry(data, activeWorker, evAlerts.map { it.title })
        } catch (_: Exception) {}
    }

    private fun runSimulationStep() {
        if (!isSimulating) return
        simulationIndex++

        val temp = 27.5 + (Math.sin(simulationIndex * 0.3) * 3.5)
        val hum = 55.0 + (Math.cos(simulationIndex * 0.2) * 10.0)
        val pres = 1012.0 + (Math.sin(simulationIndex * 0.1) * 4.0)
        val volt = 3.90 + (Math.sin(simulationIndex * 0.05) * 0.15)
        val lat = 23.8103 + (Math.sin(simulationIndex * 0.05) * 0.001)
        val lng = 90.4125 + (Math.cos(simulationIndex * 0.05) * 0.001)
        val motion = if (simulationIndex % 8 == 0) "RUNNING" else if (simulationIndex % 5 == 0) "STATIONARY" else "WALKING"

        val simulatedData = VestData(
            alerts = AlertsData(fall_detected = false, sos_active = false),
            battery = BatteryData(voltage = volt),
            environment = EnvironmentData(humidity = hum, pressure = pres, temperature = temp),
            gps = GpsData(latitude = lat, longitude = lng),
            status = StatusData(motion_state = motion),
            lastPacketTime = System.currentTimeMillis(),
            isOnline = true
        )

        processCustomData(simulatedData)
        mainHandler.postDelayed({ runSimulationStep() }, 2000L)
    }

    /**
     * Seeds initial 30 seconds of rolling telemetry if chart history is empty,
     * ensuring immediate real-time sparkline visibility.
     */
    fun ensureInitialChartData() {
        if (_chartHistory.value.size >= 10) return
        val now = System.currentTimeMillis()
        val current = _vestData.value
        val list = mutableListOf<EventSnapshot>()
        for (i in 30 downTo 0) {
            val t = now - (i * 1000L)
            val temp = current.environment.temperature + (Math.sin(i.toDouble() * 0.2) * 0.3)
            val hum = current.environment.humidity + (Math.cos(i.toDouble() * 0.15) * 0.6)
            val pres = current.environment.pressure + (Math.sin(i.toDouble() * 0.1) * 0.2)
            val volt = current.battery.voltage + (Math.sin(i.toDouble() * 0.05) * 0.01)
            list.add(
                EventSnapshot(
                    id = "seed_$i",
                    timestamp = t,
                    temperature = Math.round(temp * 10.0) / 10.0,
                    humidity = Math.round(hum * 10.0) / 10.0,
                    pressure = Math.round(pres * 10.0) / 10.0,
                    voltage = Math.round(volt * 100.0) / 100.0,
                    motion_state = current.status.motion_state,
                    fall_detected = current.alerts.fall_detected,
                    sos_active = current.alerts.sos_active,
                    activeAlerts = emptyList(),
                    isOnline = current.isOnline
                )
            )
        }
        _chartHistory.value = list
    }

    /**
     * Records a live 1-second interval sample to continuously advance the real-time graph.
     * Applies subtle natural sensor drift if between hardware LoRa packets.
     */
    fun recordLiveTick() {
        val now = System.currentTimeMillis()
        val current = _vestData.value

        // Micro-jitter to simulate live ADC sensor fluctuation between packet updates
        val tempJitter = ((Math.random() - 0.5) * 0.08)
        val humJitter = ((Math.random() - 0.5) * 0.15)
        val presJitter = ((Math.random() - 0.5) * 0.05)
        val voltJitter = ((Math.random() - 0.5) * 0.004)

        val updatedTemp = Math.round((current.environment.temperature + tempJitter) * 10.0) / 10.0
        val updatedHum = Math.round((current.environment.humidity + humJitter) * 10.0) / 10.0
        val updatedPres = Math.round((current.environment.pressure + presJitter) * 10.0) / 10.0
        val updatedVolt = Math.round((current.battery.voltage + voltJitter) * 100.0) / 100.0

        val snap = EventSnapshot(
            id = UUID.randomUUID().toString(),
            timestamp = now,
            temperature = updatedTemp,
            humidity = updatedHum,
            pressure = updatedPres,
            voltage = updatedVolt,
            motion_state = current.status.motion_state,
            fall_detected = current.alerts.fall_detected,
            sos_active = current.alerts.sos_active,
            activeAlerts = _activeAlerts.value.map { it.title },
            isOnline = current.isOnline
        )

        val curChart = _chartHistory.value.toMutableList()
        curChart.add(snap)
        if (curChart.size > 60) {
            _chartHistory.value = curChart.takeLast(60)
        } else {
            _chartHistory.value = curChart
        }
    }
}
