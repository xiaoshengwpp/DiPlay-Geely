@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Compatibility API only. This product contains no live vendor backend. */
internal object BydHudBridge {
    private var context: Context? = null
    private val callbacks by lazy { Unit }
    private var senderStarted = false
    fun clearNow() = Unit
    fun initialize(appContext: Context) = Unit
    fun onFrame(frame: Iap2Frame) = Unit
    fun clear() = Unit
    private fun tick() = Unit
}
