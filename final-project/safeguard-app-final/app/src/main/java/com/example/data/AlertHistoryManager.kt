package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AlertHistoryDao
import com.example.data.local.AlertHistoryEntity
import com.example.data.local.VestDatabase
import com.example.model.AlertHistoryItem
import com.example.model.AlertSeverity
import com.example.model.HazardAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AlertHistoryManager {
    private const val TAG = "AlertHistoryManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var dao: AlertHistoryDao? = null

    private val _alertHistory = MutableStateFlow<List<AlertHistoryItem>>(emptyList())
    val alertHistory: StateFlow<List<AlertHistoryItem>> = _alertHistory.asStateFlow()

    // Tracks currently ongoing alert events keyed by alertKey (e.g., "temp_high", "sos_alert")
    private val ongoingAlerts = ConcurrentHashMap<String, AlertHistoryItem>()

    fun init(context: Context) {
        if (dao != null) return
        val db = VestDatabase.getInstance(context.applicationContext)
        val historyDao = db.alertHistoryDao()
        dao = historyDao

        scope.launch {
            try {
                // Check if we need to seed initial historical records
                val count = historyDao.getCount()
                if (count == 0) {
                    seedInitialHistory(historyDao)
                }

                // Restore any ongoing active items from DB
                val activeDbItems = historyDao.getActiveItems()
                for (item in activeDbItems) {
                    ongoingAlerts[item.alertKey] = item.toDomain()
                }

                // Collect continuous flow
                historyDao.getAllHistoryFlow().collectLatest { entities ->
                    val domainItems = entities.map { it.toDomain() }
                    _alertHistory.value = domainItems
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AlertHistoryManager: ${e.message}", e)
            }
        }
    }

    /**
     * Called whenever evaluated active alerts update in VestTelemetryManager.
     * Accurately tracks when alarms start ringing, their triggered values, and when they resolve.
     */
    fun onActiveAlertsChanged(activeAlerts: List<HazardAlert>) = processAlerts(activeAlerts, helmet = false)

    /** Same tracking for Smart Helmet alerts (alert ids start with "helmet_"). */
    fun onHelmetAlertsChanged(activeAlerts: List<HazardAlert>) = processAlerts(activeAlerts, helmet = true)

    private fun processAlerts(activeAlerts: List<HazardAlert>, helmet: Boolean) {
        val historyDao = dao ?: return
        val currentActiveKeys = activeAlerts.map { it.id }.toSet()
        val now = System.currentTimeMillis()

        scope.launch {
            try {
                // 1. Process new or ongoing alerts
                for (alert in activeAlerts) {
                    val existing = ongoingAlerts[alert.id]
                    if (existing == null) {
                        // New alert triggered!
                        val newRecord = AlertHistoryItem(
                            id = UUID.randomUUID().toString(),
                            alertKey = alert.id,
                            title = alert.title,
                            description = alert.description,
                            severity = alert.severity,
                            sensorType = alert.sensorType,
                            triggeredValue = alert.triggeredValue,
                            safeLimit = alert.safeLimit,
                            triggeredTimestamp = alert.timestamp.takeIf { it > 0 } ?: now,
                            resolvedTimestamp = null,
                            isStillActive = true
                        )
                        ongoingAlerts[alert.id] = newRecord
                        historyDao.insertOrUpdate(newRecord.toEntity())
                    } else {
                        // Ongoing alert - update value if changed
                        if (existing.triggeredValue != alert.triggeredValue) {
                            val updated = existing.copy(triggeredValue = alert.triggeredValue)
                            ongoingAlerts[alert.id] = updated
                            historyDao.insertOrUpdate(updated.toEntity())
                        }
                    }
                }

                // 2. Process alerts that have just resolved (no longer in active list)
                val resolvedKeys = ongoingAlerts.keys.filter { it !in currentActiveKeys && it.startsWith("helmet_") == helmet }
                for (key in resolvedKeys) {
                    val activeRecord = ongoingAlerts.remove(key)
                    if (activeRecord != null) {
                        val resolved = activeRecord.copy(
                            resolvedTimestamp = now,
                            isStillActive = false
                        )
                        historyDao.insertOrUpdate(resolved.toEntity())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating alert history: ${e.message}", e)
            }
        }
    }

    fun clearHistory() {
        val historyDao = dao ?: return
        scope.launch {
            try {
                historyDao.clearAll()
                ongoingAlerts.clear()
                _alertHistory.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed clearing alert history: ${e.message}", e)
            }
        }
    }

    private suspend fun seedInitialHistory(dao: AlertHistoryDao) {
        val now = System.currentTimeMillis()
        val seeds = listOf(
            AlertHistoryEntity(
                id = UUID.randomUUID().toString(),
                alertKey = "sos_alert_hist_1",
                title = "SOS Emergency Panic Activated",
                description = "Worker triggered manual emergency button on vest during drill inspection.",
                severity = "CRITICAL",
                sensorType = "SOS",
                triggeredValue = "ACTIVE",
                safeLimit = "INACTIVE",
                triggeredTimestamp = now - (15 * 60 * 1000L), // 15 mins ago
                resolvedTimestamp = now - (13 * 60 * 1000L), // lasted 2 mins
                isStillActive = false
            ),
            AlertHistoryEntity(
                id = UUID.randomUUID().toString(),
                alertKey = "temp_high_hist_1",
                title = "Extreme High Temperature",
                description = "Ambient temperature exceeded hazardous heat threshold on Rig Floor 3.",
                severity = "CRITICAL",
                sensorType = "TEMPERATURE",
                triggeredValue = "47.8°C",
                safeLimit = "< 45.0°C",
                triggeredTimestamp = now - (42 * 60 * 1000L), // 42 mins ago
                resolvedTimestamp = now - (38 * 60 * 1000L), // lasted 4 mins
                isStillActive = false
            ),
            AlertHistoryEntity(
                id = UUID.randomUUID().toString(),
                alertKey = "geofence_hist_1",
                title = "DANGER ZONE BREACH: Heavy Crane Swing Zone",
                description = "Worker entered active overhead load operations area without spotter clearance.",
                severity = "CRITICAL",
                sensorType = "GEOFENCE",
                triggeredValue = "INSIDE Heavy Crane Swing Zone",
                safeLimit = "OUTSIDE RESTRICTED AREA",
                triggeredTimestamp = now - (1 * 3600 * 1000L + 10 * 60 * 1000L), // 1h 10m ago
                resolvedTimestamp = now - (1 * 3600 * 1000L + 8 * 60 * 1000L),
                isStillActive = false
            ),
            AlertHistoryEntity(
                id = UUID.randomUUID().toString(),
                alertKey = "pres_high_hist_1",
                title = "Critical High Pressure",
                description = "Abnormal hyperbaric pressure surge detected in decompression chamber tunnel.",
                severity = "CRITICAL",
                sensorType = "PRESSURE",
                triggeredValue = "1064.2 hPa",
                safeLimit = "< 1050.0 hPa",
                triggeredTimestamp = now - (2 * 3600 * 1000L + 25 * 60 * 1000L), // 2h 25m ago
                resolvedTimestamp = now - (2 * 3600 * 1000L + 21 * 60 * 1000L),
                isStillActive = false
            ),
            AlertHistoryEntity(
                id = UUID.randomUUID().toString(),
                alertKey = "hum_high_hist_1",
                title = "Critical High Humidity",
                description = "Relative humidity exceeded condensing threshold near cooling tower pipe.",
                severity = "CRITICAL",
                sensorType = "HUMIDITY",
                triggeredValue = "93.6%",
                safeLimit = "< 90.0%",
                triggeredTimestamp = now - (3 * 3600 * 1000L + 5 * 60 * 1000L), // 3h 5m ago
                resolvedTimestamp = now - (3 * 3600 * 1000L),
                isStillActive = false
            ),
            AlertHistoryEntity(
                id = UUID.randomUUID().toString(),
                alertKey = "fall_alert_hist_1",
                title = "Worker Fall Detected",
                description = "Accelerometer and IMU detected severe sudden impact/fall near scaffolding ladder.",
                severity = "CRITICAL",
                sensorType = "FALL",
                triggeredValue = "FALL_IMPACT",
                safeLimit = "WALKING / NORMAL",
                triggeredTimestamp = now - (4 * 3600 * 1000L + 18 * 60 * 1000L), // 4h 18m ago
                resolvedTimestamp = now - (4 * 3600 * 1000L + 15 * 60 * 1000L),
                isStillActive = false
            )
        )
        dao.insertAll(seeds)
    }

    private fun AlertHistoryEntity.toDomain(): AlertHistoryItem {
        return AlertHistoryItem(
            id = id,
            alertKey = alertKey,
            title = title,
            description = description,
            severity = if (severity.equals("WARNING", ignoreCase = true)) AlertSeverity.WARNING else AlertSeverity.CRITICAL,
            sensorType = sensorType,
            triggeredValue = triggeredValue,
            safeLimit = safeLimit,
            triggeredTimestamp = triggeredTimestamp,
            resolvedTimestamp = resolvedTimestamp,
            isStillActive = isStillActive
        )
    }

    private fun AlertHistoryItem.toEntity(): AlertHistoryEntity {
        return AlertHistoryEntity(
            id = id,
            alertKey = alertKey,
            title = title,
            description = description,
            severity = severity.name,
            sensorType = sensorType,
            triggeredValue = triggeredValue,
            safeLimit = safeLimit,
            triggeredTimestamp = triggeredTimestamp,
            resolvedTimestamp = resolvedTimestamp,
            isStillActive = isStillActive
        )
    }
}
