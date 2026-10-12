@file:Suppress("UNUSED_PARAMETER", "unused")

package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.transport.EvChargingConnectors

/** Compatibility API only. This product contains no live vendor backend. */
object BydOutputSettings {
    const val DEFAULT_LOW_CHARGE_PERCENT = 20
    val lowChargePresets = listOf(10, 15, 20, 25, 30)
    fun enabled(context: Context): Boolean = false
    fun setEnabled(context: Context, enabled: Boolean) = Unit
    fun clusterStreamPause(context: Context): Boolean = false
    fun setClusterStreamPause(context: Context, enabled: Boolean) = Unit
    fun batteryToIphone(context: Context): Boolean = false
    fun setBatteryToIphone(context: Context, enabled: Boolean) = Unit
    fun batteryToIphoneActive(context: Context): Boolean = false
    fun chargingConnectors(context: Context): EvChargingConnectors = EvChargingConnectors.CCS2_TYPE2
    fun setChargingConnectors(context: Context, connectors: EvChargingConnectors) = Unit
    fun wheelSpeedToIphone(context: Context): Boolean = false
    fun setWheelSpeedToIphone(context: Context, enabled: Boolean) = Unit
    fun wheelSpeedToIphoneActive(context: Context): Boolean = false
    fun videoWhileParked(context: Context): Boolean = false
    fun setVideoWhileParked(context: Context, enabled: Boolean) = Unit
    fun clusterSong(context: Context): Boolean = false
    fun setClusterSong(context: Context, enabled: Boolean) = Unit
    fun carPlayCalls(context: Context): Boolean = false
    fun setCarPlayCalls(context: Context, enabled: Boolean) = Unit
    fun carPlayCallControls(context: Context): Boolean = false
    fun setCarPlayCallControls(context: Context, enabled: Boolean) = Unit
    fun phoneAudioRoute(context: Context): Boolean = false
    fun setPhoneAudioRoute(context: Context, enabled: Boolean) = Unit
    fun phoneAudioRouteApps(context: Context): Boolean = false
    fun setPhoneAudioRouteApps(context: Context, enabled: Boolean) = Unit
    fun clusterSongOnChange(context: Context): Boolean = false
    fun setClusterSongOnChange(context: Context, enabled: Boolean) = Unit
    fun videoWhileParkedActive(context: Context): Boolean = false
    fun legacyVehicleProbe(context: Context): Boolean = false
    fun setLegacyVehicleProbe(context: Context, enabled: Boolean) = Unit
    fun hudSong(context: Context): Boolean = false
    fun setHudSong(context: Context, enabled: Boolean) = Unit
    fun oemClusterHold(context: Context): BydOemClusterHold = BydOemClusterHold.OFF
    fun setOemClusterHold(context: Context, hold: BydOemClusterHold) = Unit
    fun lowChargePercent(context: Context): Int = DEFAULT_LOW_CHARGE_PERCENT
    fun setLowChargePercent(context: Context, percent: Int) = Unit
    fun standaloneHudAvailable(context: Context): Boolean = false
    fun standaloneHudDiagnosticReport(context: Context): String = "BYD HUD disabled for ${GeelyVendorPolicy.PROFILE}"
    fun navigationAvailable(context: Context): Boolean = false
    fun fceClusterAvailable(): Boolean = false
    fun fceCluster(context: Context): Boolean = false
    fun setFceCluster(context: Context, enabled: Boolean) = Unit
    fun available(context: Context): Boolean = false
}
