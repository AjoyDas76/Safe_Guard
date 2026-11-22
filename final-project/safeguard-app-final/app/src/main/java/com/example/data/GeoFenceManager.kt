package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.example.manager.BreadcrumbsAndBeaconManager
import com.example.model.DangerZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class NearestDangerInfo(
    val zone: DangerZone,
    val distanceToEdgeMeters: Float,
    val distanceToCenterMeters: Float,
    val isInside: Boolean,
    val isApproaching: Boolean
)

object GeoFenceManager {
    private const val PREFS_NAME = "danger_zones_prefs"
    private const val KEY_ZONES_JSON = "key_zones_json"

    private val defaultZones = listOf(
        DangerZone(
            id = "zone_gas_01",
            name = "Zone Alpha - Methane / Gas Plant",
            hazardType = "Toxic Gas Risk",
            centerLat = 23.8115,
            centerLng = 90.4138,
            radiusMeters = 180.0,
            description = "High concentration flammable gases. Spark-proof equipment required."
        ),
        DangerZone(
            id = "zone_crane_02",
            name = "Zone Beta - Heavy Crane Lift Area",
            hazardType = "Overhead Crane Hazard",
            centerLat = 23.8090,
            centerLng = 90.4110,
            radiusMeters = 140.0,
            description = "Suspended loads operating. Strict helmet and distance mandate."
        ),
        DangerZone(
            id = "zone_voltage_03",
            name = "Zone Gamma - Substation 33kV",
            hazardType = "High Voltage Arc Flash",
            centerLat = 23.8128,
            centerLng = 90.4102,
            radiusMeters = 120.0,
            description = "Live high voltage transformers. Unauthorized entry prohibited."
        )
    )

    private val _dangerZones = MutableStateFlow<List<DangerZone>>(defaultZones)
    val dangerZones: StateFlow<List<DangerZone>> = _dangerZones.asStateFlow()

    private val _breachedZones = MutableStateFlow<List<DangerZone>>(emptyList())
    val breachedZones: StateFlow<List<DangerZone>> = _breachedZones.asStateFlow()

    private val _nearestDangerZone = MutableStateFlow<NearestDangerInfo?>(null)
    val nearestDangerZone: StateFlow<NearestDangerInfo?> = _nearestDangerZone.asStateFlow()

    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs = prefs

        val jsonString = prefs.getString(KEY_ZONES_JSON, null)
        if (!jsonString.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonString)
                val loadedList = mutableListOf<DangerZone>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedList.add(
                        DangerZone(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            hazardType = obj.getString("hazardType"),
                            centerLat = obj.getDouble("centerLat"),
                            centerLng = obj.getDouble("centerLng"),
                            radiusMeters = obj.getDouble("radiusMeters"),
                            description = obj.optString("description", "Restricted area")
                        )
                    )
                }
                if (loadedList.isNotEmpty()) {
                    _dangerZones.value = loadedList
                }
            } catch (_: Exception) {
                _dangerZones.value = defaultZones
            }
        }
    }

    private fun saveToPrefs() {
        val prefs = sharedPrefs ?: return
        try {
            val array = JSONArray()
            _dangerZones.value.forEach { zone ->
                val obj = JSONObject().apply {
                    put("id", zone.id)
                    put("name", zone.name)
                    put("hazardType", zone.hazardType)
                    put("centerLat", zone.centerLat)
                    put("centerLng", zone.centerLng)
                    put("radiusMeters", zone.radiusMeters)
                    put("description", zone.description)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_ZONES_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun checkWorkerLocation(lat: Double, lng: Double): List<DangerZone> {
        if (lat == 0.0 || lng == 0.0) {
            _breachedZones.value = emptyList()
            _nearestDangerZone.value = null
            return emptyList()
        }

        val breaches = mutableListOf<DangerZone>()
        val results = FloatArray(1)
        var closestInfo: NearestDangerInfo? = null
        var minDistanceToEdge = Float.MAX_VALUE

        for (zone in _dangerZones.value) {
            Location.distanceBetween(lat, lng, zone.centerLat, zone.centerLng, results)
            val distanceCenter = results[0]
            val distanceToEdge = distanceCenter - zone.radiusMeters.toFloat()
            val isInside = distanceCenter <= zone.radiusMeters
            val isApproaching = !isInside && distanceToEdge <= 60.0f

            if (isInside) {
                breaches.add(zone)
            }

            if (distanceToEdge < minDistanceToEdge) {
                minDistanceToEdge = distanceToEdge
                closestInfo = NearestDangerInfo(
                    zone = zone,
                    distanceToEdgeMeters = maxOf(0f, distanceToEdge),
                    distanceToCenterMeters = distanceCenter,
                    isInside = isInside,
                    isApproaching = isApproaching
                )
            }
        }

        _breachedZones.value = breaches
        _nearestDangerZone.value = closestInfo

        // Trigger Danger Zone Auto-Voice Alerts via TTS
        if (breaches.isNotEmpty()) {
            val topBreach = breaches.first()
            BreadcrumbsAndBeaconManager.speakDangerBreach(topBreach.name, topBreach.hazardType)
        } else if (closestInfo != null && closestInfo.isApproaching) {
            BreadcrumbsAndBeaconManager.speakDangerProximity(closestInfo.zone.name, closestInfo.distanceToEdgeMeters.toInt())
        }

        return breaches
    }

    fun addCustomZone(zone: DangerZone) {
        val current = _dangerZones.value.toMutableList()
        current.add(zone)
        _dangerZones.value = current
        saveToPrefs()
    }

    fun removeZone(zoneId: String) {
        _dangerZones.value = _dangerZones.value.filter { it.id != zoneId }
        saveToPrefs()
    }

    fun resetToDefaults() {
        _dangerZones.value = defaultZones
        saveToPrefs()
    }
}

