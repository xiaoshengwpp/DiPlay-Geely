@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
object BydOemClusterNavi {
    internal const val STOCK_MAP = "com.byd.automap"
    internal const val STOCK_MAP_CLUSTER_ACTIVITY = "$STOCK_MAP.extra.MeterActivity"
    private val shell by lazy { Unit }
    private val worker by lazy { Unit }
    fun applicable(context: Context): Boolean = false
    fun holdForLaunch(context: Context, lease: String, current: () -> Boolean): Boolean = false
    fun release(context: Context, lease: String? = null) = Unit
    fun restoreIfNeeded(context: Context) = Unit
    internal fun command(target: OemClusterHoldSession.Target, state: Int): String {
        val operation = when (state) {
            0 -> "default-state"
            1 -> "enable"
            2 -> "disable"
            3 -> "disable-user"
            4 -> "disable-until-used"
            else -> error("Invalid OEM component state")
        }
        val component = if (target == OemClusterHoldSession.Target.PACKAGE) STOCK_MAP
            else "$STOCK_MAP/$STOCK_MAP_CLUSTER_ACTIVITY"
        return "pm $operation --user 0 $component"
    }

}
