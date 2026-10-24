package com.example.data.local

import android.content.Context
import com.example.model.EventSnapshot
import com.example.model.VestData
import com.example.model.WorkerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TelemetryRepository private constructor(context: Context) {
    private val dao = VestDatabase.getInstance(context).telemetryDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allLogs: Flow<List<TelemetryEntity>> = dao.getAllLogsFlow()
    val emergencyLogs: Flow<List<TelemetryEntity>> = dao.getEmergencyLogsFlow()

    fun logTelemetry(data: VestData, profile: WorkerProfile, activeAlertTitles: List<String>) {
        scope.launch {
            try {
                val entity = TelemetryEntity(
                    timestamp = System.currentTimeMillis(),
                    workerId = profile.workerId,
                    workerName = profile.workerName,
                    temperature = data.environment.temperature,
                    humidity = data.environment.humidity,
                    pressure = data.environment.pressure,
                    voltage = data.battery.voltage,
                    latitude = data.gps.latitude,
                    longitude = data.gps.longitude,
                    motionState = data.status.motion_state,
                    fallDetected = data.alerts.fall_detected,
                    sosActive = data.alerts.sos_active,
                    isOnline = data.isOnline,
                    alertsSummary = activeAlertTitles.joinToString(", ")
                )
                dao.insertLog(entity)

                // Purge older than 30 days periodically
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 86400000L)
                dao.purgeOldLogs(thirtyDaysAgo)
            } catch (_: Exception) {}
        }
    }

    suspend fun getLogsSince(since: Long): List<TelemetryEntity> {
        return dao.getLogsSince(since)
    }

    companion object {
        @Volatile
        private var INSTANCE: TelemetryRepository? = null

        fun init(context: Context): TelemetryRepository {
            return INSTANCE ?: synchronized(this) {
                val repo = TelemetryRepository(context)
                INSTANCE = repo
                repo
            }
        }

        fun get(): TelemetryRepository {
            return checkNotNull(INSTANCE) { "TelemetryRepository must be initialized first!" }
        }
    }
}
