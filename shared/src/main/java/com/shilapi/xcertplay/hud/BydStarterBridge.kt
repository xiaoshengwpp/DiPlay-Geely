@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
object BydStarterBridge {
    @Synchronized fun initialize(context: Context) = Unit
    @Synchronized fun configure(context: Context, secret: String) = Unit
    fun update(icon: Int, exit: Int, distance: Int) = Unit
    fun clear(cancelDemo: Boolean = true) = Unit
    fun demonstrate(context: Context) = Unit
    private fun tick() = Unit
}
