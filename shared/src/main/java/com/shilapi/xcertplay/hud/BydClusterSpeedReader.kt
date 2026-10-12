@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import kotlin.math.roundToInt

/** Compatibility API only. This product contains no live vendor backend. */
object BydClusterSpeedReader {
    fun observe(context: Context, changed: (Int?) -> Unit): () -> Unit = {}
    internal fun kmh(metersPerSecond: Double?): Int? = metersPerSecond
        ?.takeIf { it.isFinite() && it >= 0 && it * 3.6 <= 300 }
        ?.let { (it * 3.6).roundToInt() }
}
