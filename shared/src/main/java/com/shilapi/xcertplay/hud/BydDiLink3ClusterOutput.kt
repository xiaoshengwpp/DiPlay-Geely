@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
internal object BydDiLink3ClusterOutput {
    private val shell by lazy { Unit }
    private val worker by lazy { Unit }
    fun restoreIfNeeded(appContext: Context) = Unit
    fun setDesired(appContext: Context, mapShown: Boolean, guidanceActive: Boolean) = Unit
    fun prepareDisplay(appContext: Context, displayPresent: () -> Boolean) = Unit
    private fun applyLatest() = Unit
    internal fun adbClusterRouteSelected(context: Context): Boolean = false
}
