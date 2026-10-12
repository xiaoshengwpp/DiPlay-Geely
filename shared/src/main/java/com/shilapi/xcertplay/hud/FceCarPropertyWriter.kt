@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.os.IBinder

/** Compatibility API only. This product contains no live vendor backend. */
internal class FceCarPropertyWriter : FcePropertyWriter {
    private var binder: IBinder? = null
    override fun setInt(propertyId: Int, value: Int): Boolean = false
    private fun lookup(): IBinder? = null
    companion object {
        fun isAvailable(): Boolean = false
    }
}
