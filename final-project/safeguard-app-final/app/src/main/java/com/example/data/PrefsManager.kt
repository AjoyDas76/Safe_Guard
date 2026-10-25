package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vest_command_prefs", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var isAudioAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_ALERTS, value).apply()

    var isNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()

    var selectedSirenToneId: String
        get() = prefs.getString(KEY_SIREN_TONE, "industrial_wail") ?: "industrial_wail"
        set(value) = prefs.edit().putString(KEY_SIREN_TONE, value).apply()

    var isDangerVoiceAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_DANGER_VOICE_ALERT, true)
        set(value) = prefs.edit().putBoolean(KEY_DANGER_VOICE_ALERT, value).apply()

    var isBreadcrumbsTrailVisible: Boolean
        get() = prefs.getBoolean(KEY_TRAIL_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_TRAIL_VISIBLE, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGGED_IN, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .putString(KEY_USER_EMAIL, "")
            .apply()
    }

    companion object {
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val KEY_AUDIO_ALERTS = "key_audio_alerts"
        private const val KEY_NOTIFICATIONS = "key_notifications"
        private const val KEY_SIREN_TONE = "key_siren_tone"
        private const val KEY_DANGER_VOICE_ALERT = "key_danger_voice_alert"
        private const val KEY_TRAIL_VISIBLE = "key_trail_visible"
        private const val KEY_LOGGED_IN = "key_logged_in"
        private const val KEY_USER_EMAIL = "key_user_email"

        @Volatile
        private var instance: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return instance ?: synchronized(this) {
                instance ?: PrefsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
