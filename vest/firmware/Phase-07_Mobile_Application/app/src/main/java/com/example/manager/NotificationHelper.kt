package com.example.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PrefsManager
import com.example.model.HazardAlert

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = PrefsManager.getInstance(context)
    private var lastNotifiedTime = 0L
    private var lastAlertSignature = ""

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vest Hazard Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts and hazard warnings from Smart Safety Vest"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun handleAlerts(alerts: List<HazardAlert>) {
        if (alerts.isEmpty()) return

        val now = System.currentTimeMillis()
        val signature = alerts.joinToString { "${it.sensorType}:${it.title}" }

        // Throttle to avoid sound spamming if same alert repeats within 10s
        if (signature == lastAlertSignature && (now - lastNotifiedTime) < 10000L) {
            return
        }
        lastNotifiedTime = now
        lastAlertSignature = signature

        val topAlert = alerts.first()

        // Audio & Haptics
        if (prefs.isAudioAlertsEnabled) {
            triggerAudioAlert()
        }

        // Notification
        if (prefs.isNotificationsEnabled) {
            postAlertNotification(topAlert, alerts.size)
        }
    }

    private fun postAlertNotification(topAlert: HazardAlert, totalCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (totalCount > 1) {
            "⚠️ HAZARD ALERT: ${topAlert.title} (+$totalCount alerts)"
        } else {
            "⚠️ HAZARD ALERT: ${topAlert.title}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(topAlert.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${topAlert.description}\nReading: ${topAlert.triggeredValue} (Limit: ${topAlert.safeLimit})"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ permission may not be granted yet
        }
    }

    private fun triggerAudioAlert() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
        } catch (_: Exception) {
            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, alertUri)
                r.play()
            } catch (_: Exception) {}
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(longArrayOf(0, 300, 150, 300), -1)
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL_ID = "vest_hazard_channel"
        const val NOTIFICATION_ID = 911
    }
}
