package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

enum class RefreshRatePreference(val label: String, val description: String) {
    MAX_ULTRA("Ultra / Max FPS", "Automatically locks to the highest refresh rate your device supports (e.g., 90Hz, 120Hz, 144Hz)"),
    STANDARD_60("Standard (60 Hz)", "Targets 60Hz to balance smoothness and battery life"),
    SYSTEM_AUTO("System Default", "Lets Android OS control the display refresh rate dynamically")
}

data class DeviceDisplayInfo(
    val activeHz: Int,
    val maxSupportedHz: Int,
    val supportedHzOptions: List<Int>
)

object DisplayRefreshRateHelper {

    private const val PREFS_NAME = "taskpulse_display_prefs"
    private const val KEY_REFRESH_PREF = "refresh_rate_preference"

    fun loadPreference(context: Context): RefreshRatePreference {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_REFRESH_PREF, RefreshRatePreference.MAX_ULTRA.name)
        return try {
            RefreshRatePreference.valueOf(saved ?: RefreshRatePreference.MAX_ULTRA.name)
        } catch (_: Exception) {
            RefreshRatePreference.MAX_ULTRA
        }
    }

    fun savePreference(context: Context, pref: RefreshRatePreference) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REFRESH_PREF, pref.name)
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun resolveDisplay(activity: Activity): Display? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            activity.windowManager?.defaultDisplay
        }
    }

    fun getDeviceDisplayInfo(activity: Activity): DeviceDisplayInfo {
        val display = resolveDisplay(activity)
            ?: return DeviceDisplayInfo(activeHz = 60, maxSupportedHz = 60, supportedHzOptions = listOf(60))

        val currentMode = display.mode
        val activeHz = currentMode.refreshRate.roundToInt().coerceAtLeast(60)
        val sameResModes = display.supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }.ifEmpty { display.supportedModes.toList() }

        val hzList = sameResModes
            .map { it.refreshRate.roundToInt() }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .ifEmpty { listOf(activeHz) }

        val maxHz = hzList.maxOrNull() ?: activeHz
        return DeviceDisplayInfo(
            activeHz = activeHz,
            maxSupportedHz = maxHz,
            supportedHzOptions = hzList
        )
    }

    fun selectBestModeId(
        modes: List<Display.Mode>,
        currentWidth: Int,
        currentHeight: Int,
        preference: RefreshRatePreference
    ): Int {
        if (preference == RefreshRatePreference.SYSTEM_AUTO || modes.isEmpty()) return 0
        val sameResModes = modes.filter {
            it.physicalWidth == currentWidth && it.physicalHeight == currentHeight
        }.ifEmpty { modes }

        return when (preference) {
            RefreshRatePreference.MAX_ULTRA -> {
                sameResModes.maxByOrNull { it.refreshRate }?.modeId ?: 0
            }
            RefreshRatePreference.STANDARD_60 -> {
                sameResModes.minByOrNull { abs(it.refreshRate - 60f) }?.modeId ?: 0
            }
            RefreshRatePreference.SYSTEM_AUTO -> 0
        }
    }

    fun applyRefreshRate(activity: Activity, preference: RefreshRatePreference): DeviceDisplayInfo {
        val display = resolveDisplay(activity)
        if (display != null) {
            val currentMode = display.mode
            val targetModeId = selectBestModeId(
                modes = display.supportedModes.toList(),
                currentWidth = currentMode.physicalWidth,
                currentHeight = currentMode.physicalHeight,
                preference = preference
            )
            val attrs = activity.window.attributes
            if (attrs.preferredDisplayModeId != targetModeId) {
                attrs.preferredDisplayModeId = targetModeId
                activity.window.attributes = attrs
            }
        }
        return getDeviceDisplayInfo(activity)
    }
}
