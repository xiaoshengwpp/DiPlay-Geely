package com.shilapi.xcertplay

import com.shilapi.xcertplay.hud.GeelyVendorPolicy

import android.content.Context

/** Opt-in key routing shares the existing wheel accessibility service and authorization. */
object NavigationWheelSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE)
    fun enabled(context: Context): Boolean = GeelyVendorPolicy.bydAdb && prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        if (!GeelyVendorPolicy.bydAdb) return
        prefs(context).edit().putBoolean("enabled", enabled).apply()
        WheelKeyService.settingsChanged()
        if (enabled) WheelKeyService.restoreIfNeeded(context)
    }
    internal fun recordAuthorization(context: Context) {
        if (!GeelyVendorPolicy.bydAdb) return
        prefs(context).edit().putBoolean("authorized", true).apply()
    }
    internal fun restoreAllowed(context: Context): Boolean =
        enabled(context) && prefs(context).getBoolean("authorized", false)
}
