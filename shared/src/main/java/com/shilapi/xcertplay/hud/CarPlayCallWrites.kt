@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud


/** Compatibility API only. This product contains no live vendor backend. */
internal class CarPlayCallWrite(val label: String, val call: Int, val idle: Int)
internal object CarPlayCallWrites {
    enum class Result { DONE, MISSING, REFUSED }
    fun apply(writes: List<CarPlayCallWrite>, write: (step: CarPlayCallWrite, value: Int, undo: Boolean) -> Result,
        name: () -> Result): Boolean = false
}
