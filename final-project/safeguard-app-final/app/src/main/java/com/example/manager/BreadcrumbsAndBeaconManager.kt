package com.example.manager

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class BeaconBreadcrumb(
    val id: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val beaconNode: String = "GW-ALPHA-01",
    val motionState: String = "NORMAL",
    val isDanger: Boolean = false
)

data class LastKnownLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val beaconNode: String = "GW-ALPHA-01",
    val motionState: String = "NORMAL",
    val isOffline: Boolean = false
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("hh:mm:ss a, dd MMM", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun getTimeAgo(currentTime: Long = System.currentTimeMillis()): String {
        val diffSec = maxOf(0L, (currentTime - timestamp) / 1000L)
        return when {
            diffSec < 5 -> "Just now"
            diffSec < 60 -> "${diffSec}s ago"
            diffSec < 3600 -> "${diffSec / 60}m ${diffSec % 60}s ago"
            else -> "${diffSec / 3600}h ${(diffSec % 3600) / 60}m ago"
        }
    }
}

object BreadcrumbsAndBeaconManager : TextToSpeech.OnInitListener {
    private const val TAG = "BreadcrumbsManager"
    private const val PREFS_NAME = "beacon_breadcrumbs_prefs"
    private const val KEY_TRAIL_JSON = "key_trail_json"
    private const val KEY_LAST_KNOWN_JSON = "key_last_known_json"
    private const val MAX_BREADCRUMBS = 150

    private val _breadcrumbs = MutableStateFlow<List<BeaconBreadcrumb>>(emptyList())
    val breadcrumbs: StateFlow<List<BeaconBreadcrumb>> = _breadcrumbs.asStateFlow()

    private val _lastKnownLocation = MutableStateFlow<LastKnownLocation?>(null)
    val lastKnownLocation: StateFlow<LastKnownLocation?> = _lastKnownLocation.asStateFlow()

    private val _isTrailVisible = MutableStateFlow(true)
    val isTrailVisible: StateFlow<Boolean> = _isTrailVisible.asStateFlow()

    private val _isVoiceAlertEnabled = MutableStateFlow(true)
    val isVoiceAlertEnabled: StateFlow<Boolean> = _isVoiceAlertEnabled.asStateFlow()

    private var sharedPrefs: SharedPreferences? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var lastSpokenMessage = ""
    private var lastSpokenTime = 0L
    private val SPEECH_COOLDOWN_MS = 22000L // 22 seconds repeat cooldown

    fun init(context: Context) {
        val appContext = context.applicationContext
        val prefs = PrefsManager.getInstance(appContext)
        _isVoiceAlertEnabled.value = prefs.isDangerVoiceAlertEnabled
        _isTrailVisible.value = prefs.isBreadcrumbsTrailVisible

        sharedPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()

        // Initialize Android TextToSpeech for realistic Danger Zone Auto-Voice Alerts
        try {
            textToSpeech = TextToSpeech(appContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS Init failed: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                textToSpeech?.setPitch(1.05f)
                textToSpeech?.setSpeechRate(0.95f)
                Log.i(TAG, "TextToSpeech initialized successfully for Danger Zone Auto-Alerts")
            }
        }
    }

    /**
     * Speaks emergency voice instructions directly into worker's audio/headset
     */
    fun speakVoiceAlert(announcement: String, forceImmediate: Boolean = false) {
        if (!_isVoiceAlertEnabled.value || !isTtsReady) return
        val now = System.currentTimeMillis()
        if (!forceImmediate && announcement == lastSpokenMessage && (now - lastSpokenTime) < SPEECH_COOLDOWN_MS) {
            return // Prevent annoying audio spam
        }

        lastSpokenMessage = announcement
        lastSpokenTime = now
        try {
            textToSpeech?.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, "DANGER_VOICE_ALERT")
            Log.i(TAG, "Spoke Voice Alert: $announcement")
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error: ${e.message}")
        }
    }

    fun speakDangerBreach(zoneName: String, hazardType: String) {
        val msg = "Warning! Danger zone breach detected. You have entered $zoneName. $hazardType. Evacuate immediately!"
        speakVoiceAlert(msg, forceImmediate = true)
    }

    fun speakDangerProximity(zoneName: String, distanceMeters: Int) {
        val msg = "Caution: Approaching restricted perimeter $zoneName. $distanceMeters meters away."
        speakVoiceAlert(msg, forceImmediate = false)
    }

    /**
     * Records telemetry fix, generates beacon breadcrumbs trail, and persists Last Known Location
     */
    fun recordFix(
        latitude: Double,
        longitude: Double,
        beaconNode: String,
        motionState: String,
        isDanger: Boolean,
        isOnline: Boolean
    ) {
        if (latitude == 0.0 || longitude == 0.0) return

        val now = System.currentTimeMillis()
        val lkl = LastKnownLocation(
            latitude = latitude,
            longitude = longitude,
            timestamp = now,
            beaconNode = if (beaconNode.isNotBlank()) beaconNode else "GW-ALPHA-01",
            motionState = if (motionState.isNotBlank()) motionState else "NORMAL",
            isOffline = !isOnline
        )
        _lastKnownLocation.value = lkl

        // Filter breadcrumbs: only add if moved > 3 meters or > 8 seconds elapsed
        val currentList = _breadcrumbs.value
        val shouldAdd = if (currentList.isEmpty()) {
            true
        } else {
            val lastPoint = currentList.last()
            val dist = FloatArray(1)
            Location.distanceBetween(lastPoint.latitude, lastPoint.longitude, latitude, longitude, dist)
            dist[0] >= 3.0f || (now - lastPoint.timestamp) >= 8000L || (lastPoint.isDanger != isDanger)
        }

        if (shouldAdd) {
            val updated = currentList.toMutableList()
            updated.add(
                BeaconBreadcrumb(
                    id = now,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = now,
                    beaconNode = lkl.beaconNode,
                    motionState = lkl.motionState,
                    isDanger = isDanger
                )
            )
            if (updated.size > MAX_BREADCRUMBS) {
                updated.removeAt(0)
            }
            _breadcrumbs.value = updated
            saveToPrefs()
        }
    }

    fun toggleTrailVisibility(context: Context): Boolean {
        val newState = !_isTrailVisible.value
        _isTrailVisible.value = newState
        PrefsManager.getInstance(context).isBreadcrumbsTrailVisible = newState
        return newState
    }

    fun toggleVoiceAlert(context: Context): Boolean {
        val newState = !_isVoiceAlertEnabled.value
        _isVoiceAlertEnabled.value = newState
        PrefsManager.getInstance(context).isDangerVoiceAlertEnabled = newState
        if (!newState) {
            textToSpeech?.stop()
        }
        return newState
    }

    fun clearBreadcrumbs(context: Context? = null) {
        clearTrail()
    }

    fun clearTrail() {
        _breadcrumbs.value = emptyList()
        val prefs = sharedPrefs ?: return
        prefs.edit().remove(KEY_TRAIL_JSON).apply()
        Log.i(TAG, "Beacon breadcrumbs trail cleared")
    }

    private fun saveToPrefs() {
        val prefs = sharedPrefs ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Save breadcrumbs
                val array = JSONArray()
                _breadcrumbs.value.takeLast(100).forEach { b ->
                    val obj = JSONObject().apply {
                        put("lat", b.latitude)
                        put("lng", b.longitude)
                        put("ts", b.timestamp)
                        put("beacon", b.beaconNode)
                        put("motion", b.motionState)
                        put("danger", b.isDanger)
                    }
                    array.put(obj)
                }

                // Save last known location
                val lkl = _lastKnownLocation.value
                val lklObj = if (lkl != null) {
                    JSONObject().apply {
                        put("lat", lkl.latitude)
                        put("lng", lkl.longitude)
                        put("ts", lkl.timestamp)
                        put("beacon", lkl.beaconNode)
                        put("motion", lkl.motionState)
                        put("offline", lkl.isOffline)
                    }
                } else null

                prefs.edit()
                    .putString(KEY_TRAIL_JSON, array.toString())
                    .apply {
                        if (lklObj != null) putString(KEY_LAST_KNOWN_JSON, lklObj.toString())
                    }
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving breadcrumbs: ${e.message}")
            }
        }
    }

    private fun loadFromPrefs() {
        val prefs = sharedPrefs ?: return
        try {
            // Load Last Known Location
            val lklStr = prefs.getString(KEY_LAST_KNOWN_JSON, null)
            if (!lklStr.isNullOrEmpty()) {
                val obj = JSONObject(lklStr)
                _lastKnownLocation.value = LastKnownLocation(
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng"),
                    timestamp = obj.getLong("ts"),
                    beaconNode = obj.optString("beacon", "GW-ALPHA-01"),
                    motionState = obj.optString("motion", "NORMAL"),
                    isOffline = obj.optBoolean("offline", true)
                )
            }

            // Load Breadcrumbs
            val trailStr = prefs.getString(KEY_TRAIL_JSON, null)
            if (!trailStr.isNullOrEmpty()) {
                val array = JSONArray(trailStr)
                val list = mutableListOf<BeaconBreadcrumb>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BeaconBreadcrumb(
                            id = obj.optLong("ts", System.currentTimeMillis()),
                            latitude = obj.getDouble("lat"),
                            longitude = obj.getDouble("lng"),
                            timestamp = obj.getLong("ts"),
                            beaconNode = obj.optString("beacon", "GW-ALPHA-01"),
                            motionState = obj.optString("motion", "NORMAL"),
                            isDanger = obj.optBoolean("danger", false)
                        )
                    )
                }
                _breadcrumbs.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading breadcrumbs: ${e.message}")
        }
    }
}
