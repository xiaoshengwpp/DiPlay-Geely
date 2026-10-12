@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Compatibility API only. This product contains no live vendor backend. */
internal object FceClusterBridge {
    private var context: Context? = null
    private var tickStarted = false
    fun available(): Boolean = false
    fun initialize(appContext: Context) = Unit
    fun onFrame(frame: Iap2Frame) = Unit
    fun clear() = Unit
    private fun tick() = Unit
    private fun refreshLocked() = Unit
    internal fun resetForTest(writer: FcePropertyWriter, appContext: Context?) = Unit
}
