@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.VehicleStatusProvider
import com.shilapi.xcertplay.transport.VehicleSpeedSource
import com.shilapi.xcertplay.transport.VehicleSpeedReading

/** Compatibility API only. This product contains no live vendor backend. */
object BydNavigationOutputs {
    private val standalone by lazy { Unit }
    private val hud by lazy { Unit }
    private val cluster by lazy { Unit }
    private val fceCluster by lazy { Unit }
    private val overlayRoute = BydHudRouteState(staleRouteNs = 120_000_000_000L, emptyListHideNs = 8_000_000_000L, keepAcrossNoRoute = true)
    private var overlayListener: ((ClusterTurnGuidance?) -> Unit)? = null
    fun onAppOpened(context: Context) = Unit
    fun setDiagnosticHold(hold: Boolean) = Unit
    fun setClusterMapShown(shown: Boolean) = Unit
    fun setClusterStreamControl(control: (Boolean) -> Unit) = Unit
    fun clusterNaviMode(): BydClusterNaviMode? = null
    fun setClusterNaviModeListener(listener: ((BydClusterNaviMode?) -> Unit)?) = Unit
    fun clearClusterStreamControl(control: (Boolean) -> Unit) = Unit
    fun batteryStatus(context: Context): VehicleStatusProvider = VehicleStatusProvider { null }
    fun wheelSpeed(context: Context): VehicleSpeedSource = disabledWheelSpeed
    private val disabledWheelSpeed = object : VehicleSpeedSource {
        override fun start() = Unit
        override fun stop() = Unit
        override fun drain(): VehicleSpeedReading? = null
    }
    fun parked(context: Context): Boolean? = null
    fun start(context: Context) = Unit
    internal fun onFrame(frame: Iap2Frame) = Unit
    fun setTurnOverlayListener(listener: ((ClusterTurnGuidance?) -> Unit)?) = Unit
    fun refreshTurnOverlay() = Unit
    private fun currentOverlay(): ClusterTurnGuidance? = null
    fun clusterSongChanged(enabled: Boolean) = Unit
    fun carPlayCallsChanged(enabled: Boolean) = Unit
    fun carPlaySessionStarted() = Unit
    fun carPlayCall(): CarPlayCallCard? = null
    fun clusterSongOnChangeChanged() = Unit
    fun dashboardNote(text: String, source: Int? = null) = Unit
    fun endNow(preserveTurnOverlay: Boolean = false) = Unit
}
