@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import android.bluetooth.BluetoothAdapter

/** Compatibility API only. This product contains no live vendor backend. */
object BydBluetoothSuspend {
    private val shell by lazy { Unit }
    private val worker by lazy { Unit }
    private var lease: BluetoothSuspendLease? = null
    private var pending: Any? = null
    /** Preserve upstream callback signatures without invoking them or initializing vendor work. */
    fun suspend(
        context: Context,
        owner: Any,
        delayMillis: Long = 10_000L,
        log: (String) -> Unit = {},
        stillActive: () -> Boolean = { true },
    ) = Unit
    fun resume(context: Context, owner: Any, log: (String) -> Unit = {}) = Unit
    fun onAppOpened(context: Context) = Unit
    // Normal CarPlay Bluetooth bootstrap independently verifies the adapter.
    fun resumeAndWait(context: Context, adapter: BluetoothAdapter?, timeoutMillis: Long = 5_000L): Boolean = true
    fun isSuspendedByUs(context: Context): Boolean = false
    private fun radioEnabled(context: Context): Boolean? = null
    private fun awaitRadio(context: Context, enabled: Boolean): Boolean = false
}
