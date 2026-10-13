package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import com.shilapi.xcertplay.network.GeelyCapability
import com.shilapi.xcertplay.network.GeelyHeadUnit

/** Manual compatibility opt-in for unverified head units, applied at the next CarPlay connection. */
object GeelyInputSettings {
    private fun prefs(context: Context) =
        context.getSharedPreferences("diplay_geely_input", Context.MODE_PRIVATE)

    fun manualEnabled(context: Context): Boolean = prefs(context).getBoolean("manual_enabled", false)

    fun setManualEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("manual_enabled", enabled).apply()
    }

    fun enabled(context: Context): Boolean = GeelyVendorPolicy.geelyInput &&
        GeelyCapability.inputAllowed(GeelyHeadUnit.detected, manualEnabled(context))
}
