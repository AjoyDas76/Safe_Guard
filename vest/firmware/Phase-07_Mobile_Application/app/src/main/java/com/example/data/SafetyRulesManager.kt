package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.SafetyRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SafetyRulesManager {
    private const val PREFS_NAME = "safety_rules_prefs"
    private const val KEY_TEMP_HIGH = "key_temp_high"
    private const val KEY_TEMP_LOW = "key_temp_low"
    private const val KEY_PRES_HIGH = "key_pres_high"
    private const val KEY_PRES_LOW = "key_pres_low"
    private const val KEY_HUM_HIGH = "key_hum_high"
    private const val KEY_HUM_LOW = "key_hum_low"
    private const val KEY_FALL_ENABLED = "key_fall_enabled"
    private const val KEY_IMMOBILITY_TIMEOUT = "key_immobility_timeout"
    private const val KEY_SOS_BUZZER = "key_sos_buzzer"
    private const val KEY_LAST_UPDATED = "key_last_updated"
    private const val KEY_H_HR_MIN = "key_helmet_hr_min"
    private const val KEY_H_HR_MAX = "key_helmet_hr_max"
    private const val KEY_H_SPO2_MIN = "key_helmet_spo2_min"
    private const val KEY_H_GAS_MARGIN = "key_helmet_gas_margin"
    private const val KEY_H_OBST_CM = "key_helmet_obstacle_cm"

    private val defaultRules = SafetyRules()

    private val _rules = MutableStateFlow(defaultRules)
    val rules: StateFlow<SafetyRules> = _rules.asStateFlow()

    private var sharedPrefs: SharedPreferences? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs = prefs

        val loaded = SafetyRules(
            tempHighCritical = prefs.getFloat(KEY_TEMP_HIGH, 45.0f).toDouble(),
            tempLowWarning = prefs.getFloat(KEY_TEMP_LOW, 15.0f).toDouble(),
            pressureHighWarning = prefs.getFloat(KEY_PRES_HIGH, 1050.0f).toDouble(),
            pressureLowWarning = prefs.getFloat(KEY_PRES_LOW, 950.0f).toDouble(),
            humidityHighWarning = prefs.getFloat(KEY_HUM_HIGH, 90.0f).toDouble(),
            humidityLowWarning = prefs.getFloat(KEY_HUM_LOW, 20.0f).toDouble(),
            fallDetectionEnabled = prefs.getBoolean(KEY_FALL_ENABLED, true),
            immobilityTimeoutSec = prefs.getInt(KEY_IMMOBILITY_TIMEOUT, 60),
            sosBuzzerEnabled = prefs.getBoolean(KEY_SOS_BUZZER, true),
            helmetHrMin = prefs.getInt(KEY_H_HR_MIN, 50),
            helmetHrMax = prefs.getInt(KEY_H_HR_MAX, 120),
            helmetSpo2Min = prefs.getInt(KEY_H_SPO2_MIN, 90),
            helmetGasMargin = prefs.getInt(KEY_H_GAS_MARGIN, 150),
            helmetObstacleCm = prefs.getInt(KEY_H_OBST_CM, 50),
            lastUpdated = prefs.getLong(KEY_LAST_UPDATED, System.currentTimeMillis())
        )
        _rules.value = loaded
    }

    fun updateRules(newRules: SafetyRules) {
        _rules.value = newRules.copy(lastUpdated = System.currentTimeMillis())
        sharedPrefs?.edit()?.apply {
            putFloat(KEY_TEMP_HIGH, newRules.tempHighCritical.toFloat())
            putFloat(KEY_TEMP_LOW, newRules.tempLowWarning.toFloat())
            putFloat(KEY_PRES_HIGH, newRules.pressureHighWarning.toFloat())
            putFloat(KEY_PRES_LOW, newRules.pressureLowWarning.toFloat())
            putFloat(KEY_HUM_HIGH, newRules.humidityHighWarning.toFloat())
            putFloat(KEY_HUM_LOW, newRules.humidityLowWarning.toFloat())
            putBoolean(KEY_FALL_ENABLED, newRules.fallDetectionEnabled)
            putInt(KEY_IMMOBILITY_TIMEOUT, newRules.immobilityTimeoutSec)
            putBoolean(KEY_SOS_BUZZER, newRules.sosBuzzerEnabled)
            putInt(KEY_H_HR_MIN, newRules.helmetHrMin)
            putInt(KEY_H_HR_MAX, newRules.helmetHrMax)
            putInt(KEY_H_SPO2_MIN, newRules.helmetSpo2Min)
            putInt(KEY_H_GAS_MARGIN, newRules.helmetGasMargin)
            putInt(KEY_H_OBST_CM, newRules.helmetObstacleCm)
            putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            apply()
        }
    }

    fun resetToOshaDefaults(): SafetyRules {
        val osha = SafetyRules(
            tempHighCritical = 45.0,
            tempLowWarning = 15.0,
            pressureHighWarning = 1050.0,
            pressureLowWarning = 950.0,
            humidityHighWarning = 90.0,
            humidityLowWarning = 20.0,
            fallDetectionEnabled = true,
            immobilityTimeoutSec = 60,
            sosBuzzerEnabled = true,
            lastUpdated = System.currentTimeMillis()
        )
        updateRules(osha)
        return osha
    }
}
