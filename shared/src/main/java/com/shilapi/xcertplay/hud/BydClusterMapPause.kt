@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context

/** Compatibility API only. This product contains no live vendor backend. */
internal object BydClusterMapPause {
    private val shell by lazy { Unit }
    @Volatile var clusterMapShown = false
    @Volatile var streamControl: ((Boolean) -> Unit)? = null
    @Volatile internal var readMode: (Context) -> BydClusterNaviMode? = { null }
    @Volatile var lastNaviMode: BydClusterNaviMode? = null
        private set
    @Volatile var onNaviMode: ((BydClusterNaviMode?) -> Unit)? = null
    fun initialize(appContext: Context) = Unit
    private fun tick() = Unit
}
