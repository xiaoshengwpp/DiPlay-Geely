package com.shilapi.xcertplay.hud

import android.content.Context

/**
 * Whether the car is in P, read through the adb shell (autoservice binder) where apps would need a
 * BYD signature. The id is BYD SDK 1.0.5's gearbox device: 1 P, 2 R, 3 N, 4 D.
 */
internal object BydParkedState {
    const val GEAR = "service call autoservice 5 i32 1011 i32 555745336"
    private const val PARK = 1

    private val shell by lazy { Unit }
    fun parked(context: Context): Boolean? = null

    fun parked(output: String?): Boolean? = BydParcel.value(output)?.takeIf { it in 1..6 }?.let { it == PARK }
}
