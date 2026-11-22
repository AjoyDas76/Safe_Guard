package com.example.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.R
import com.example.data.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

data class SirenToneOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val rawResId: Int,
    val tag: String = "SIREN"
)

object EmergencySirenManager {
    private const val TAG = "EmergencySirenManager"

    val AVAILABLE_SIREN_TONES = listOf(
        SirenToneOption(
            id = "industrial_wail",
            name = "Industrial Wail",
            subtitle = "Deep sweeping industrial horn wail (Factory Standard)",
            rawResId = R.raw.siren_industrial,
            tag = "DEFAULT"
        ),
        SirenToneOption(
            id = "two_tone_hilo",
            name = "Two-Tone Hi-Lo",
            subtitle = "Rapid alternating high-low European emergency horn",
            rawResId = R.raw.siren_hilo,
            tag = "HIGH-LOW"
        ),
        SirenToneOption(
            id = "air_raid",
            name = "Air Raid Evacuation",
            subtitle = "Heavy low-frequency nuclear plant warning horn",
            rawResId = R.raw.siren_airraid,
            tag = "EVAC"
        ),
        SirenToneOption(
            id = "nuclear_pulse",
            name = "Pulsing Strobe",
            subtitle = "High-intensity intermittent emergency strobe alarm",
            rawResId = R.raw.siren_pulsing,
            tag = "PULSE"
        ),
        SirenToneOption(
            id = "electronic_chirp",
            name = "Electronic Hazard",
            subtitle = "Fast rising electronic high-urgency frequency chirp",
            rawResId = R.raw.siren_electronic,
            tag = "RAPID"
        )
    )

    private val _isAlarmActive = MutableStateFlow(false)
    val isAlarmActive: StateFlow<Boolean> = _isAlarmActive.asStateFlow()

    private val _currentEmergencyReason = MutableStateFlow("")
    val currentEmergencyReason: StateFlow<String> = _currentEmergencyReason.asStateFlow()

    private val _selectedToneId = MutableStateFlow("industrial_wail")
    val selectedToneId: StateFlow<String> = _selectedToneId.asStateFlow()

    private val _previewingToneId = MutableStateFlow<String?>(null)
    val previewingToneId: StateFlow<String?> = _previewingToneId.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var previewPlayer: MediaPlayer? = null
    private var activeAudioTrack: AudioTrack? = null
    private var alarmJob: Job? = null
    private var appContext: Context? = null
    private var lastSilencedTime = 0L
    private var lastSilencedReason = ""

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = PrefsManager.getInstance(context)
        _selectedToneId.value = prefs.selectedSirenToneId
    }

    /**
     * Selects and persistently saves the preferred emergency siren tone
     */
    fun selectTone(context: Context, toneId: String) {
        _selectedToneId.value = toneId
        PrefsManager.getInstance(context).selectedSirenToneId = toneId
        Log.i(TAG, "Selected emergency siren tone updated: $toneId")
    }

    /**
     * Plays a brief audio preview of the chosen siren tone in Settings
     */
    fun previewTone(context: Context, toneId: String) {
        if (_isAlarmActive.value) return // Don't interrupt real emergency alarm
        stopPreview()

        val option = AVAILABLE_SIREN_TONES.find { it.id == toneId } ?: AVAILABLE_SIREN_TONES[0]
        try {
            val mp = MediaPlayer.create(
                context,
                option.rawResId,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioManager.AUDIO_SESSION_ID_GENERATE
            ) ?: MediaPlayer.create(context, option.rawResId)

            if (mp != null) {
                previewPlayer = mp
                _previewingToneId.value = toneId
                mp.isLooping = true
                mp.setVolume(1.0f, 1.0f)
                mp.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Preview playback failed for $toneId: ${e.message}")
            stopPreview()
        }
    }

    /**
     * Stops preview audio if playing
     */
    fun stopPreview() {
        _previewingToneId.value = null
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
            previewPlayer = null
        } catch (_: Exception) {}
    }

    /**
     * Triggers a continuous emergency loud siren and pulsed vibration
     * for Fall Detected, SOS button press, Danger Zone breach, or critical hazard.
     * Uses the app's dedicated selected industrial siren tone.
     */
    fun triggerEmergencyAlarm(reason: String) {
        if (_isAlarmActive.value) return // already sounding

        // If the supervisor recently silenced the alarm for the exact same reason, respect cooldown for 15 seconds
        val now = System.currentTimeMillis()
        if (reason == lastSilencedReason && (now - lastSilencedTime) < 15000L) {
            return
        }

        stopPreview()
        _isAlarmActive.value = true
        _currentEmergencyReason.value = reason
        Log.w(TAG, "EMERGENCY INDUSTRIAL SIREN ACTIVATED: $reason")

        val ctx = appContext ?: return
        val prefs = PrefsManager.getInstance(ctx)

        alarmJob?.cancel()
        alarmJob = CoroutineScope(Dispatchers.Default).launch {
            // Setup continuous tactile vibration
            startVibration(ctx)

            if (!prefs.isAudioAlertsEnabled) {
                Log.i(TAG, "Audio alerts disabled in user settings, suppressing audible siren")
                return@launch
            }

            // Determine active siren tone
            val activeToneId = _selectedToneId.value
            val toneOption = AVAILABLE_SIREN_TONES.find { it.id == activeToneId } ?: AVAILABLE_SIREN_TONES[0]

            // Setup continuous audible siren loop from embedded app resource
            var playerStarted = false
            try {
                val mp = MediaPlayer.create(
                    ctx,
                    toneOption.rawResId,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                ) ?: MediaPlayer.create(ctx, toneOption.rawResId)

                if (mp != null) {
                    mediaPlayer = mp
                    mp.isLooping = true
                    mp.setVolume(1.0f, 1.0f)
                    mp.start()
                    playerStarted = true
                    Log.i(TAG, "Industrial siren playing tone '${toneOption.name}' via embedded MediaPlayer")
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPlayer siren failed: ${e.message}")
            }

            // Software AudioTrack synthesizer fallback if MediaPlayer is unavailable
            if (!playerStarted && isActive && _isAlarmActive.value) {
                Log.i(TAG, "Starting software AudioTrack emergency siren fallback")
                startAudioTrackSiren(this)
            }
        }
    }

    /**
     * Synthesizes an authentic industrial wailing siren directly in PCM audio
     * ensuring 100% device-independent siren output without relying on external system ringtones.
     */
    private fun startAudioTrackSiren(scope: CoroutineScope) {
        val sampleRate = 22050
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 4)

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
            null
        } ?: return

        activeAudioTrack = audioTrack

        try {
            audioTrack.play()
            val chunkSamples = 1024
            val buffer = ShortArray(chunkSamples)
            var phase = 0.0
            var t = 0.0
            val cycleDuration = 1.6 // Siren wail modulation cycle in seconds
            val fMin = 650.0        // Low frequency of wail
            val fMax = 1350.0       // High frequency of wail

            while (scope.isActive && _isAlarmActive.value) {
                for (i in 0 until chunkSamples) {
                    val lfo = 0.5 * (1.0 - cos(2.0 * Math.PI * t / cycleDuration))
                    val freq = fMin + (fMax - fMin) * lfo
                    phase += 2.0 * Math.PI * freq / sampleRate
                    if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI

                    // Harmonic overtones for piercing horn siren timbre
                    val raw = sin(phase) + 0.35 * sin(2.0 * phase) + 0.20 * sin(3.0 * phase) + 0.10 * sin(4.0 * phase)
                    val saturated = tanh(1.25 * raw)
                    buffer[i] = (saturated * 28000.0).toInt().coerceIn(-32767, 32767).toShort()

                    t += 1.0 / sampleRate
                    if (t > cycleDuration) t -= cycleDuration
                }
                audioTrack.write(buffer, 0, chunkSamples)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback error: ${e.message}")
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
            if (activeAudioTrack === audioTrack) {
                activeAudioTrack = null
            }
        }
    }

    /**
     * Stop and silence the siren & vibration (Supervisor Acknowledged)
     */
    fun silenceAlarm() {
        Log.i(TAG, "Emergency siren silenced by user.")
        lastSilencedTime = System.currentTimeMillis()
        lastSilencedReason = _currentEmergencyReason.value
        _isAlarmActive.value = false
        _currentEmergencyReason.value = ""
        stopPreview()

        alarmJob?.cancel()
        alarmJob = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
            activeAudioTrack = null
        } catch (_: Exception) {}

        appContext?.let { ctx ->
            stopVibration(ctx)
        }
    }

    private fun startVibration(context: Context) {
        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.cancel()
            }
        } catch (_: Exception) {}
    }
}
