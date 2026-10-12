@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
internal class BydStandaloneHudOutput private constructor(context: Context) {
    fun update(icon: Int, exit: Int, distanceMeters: Int, road: String) = Unit
    fun showText(text: String) = Unit
    fun clear() = Unit
    companion object {
        @Volatile var syntheticHold = false
        fun create(context: Context): BydStandaloneHudOutput? = null
        fun diagnostics(context: Context): String = "BYD HUD disabled for ${GeelyVendorPolicy.PROFILE}"
        fun available(context: Context): Boolean = false
    }
}
