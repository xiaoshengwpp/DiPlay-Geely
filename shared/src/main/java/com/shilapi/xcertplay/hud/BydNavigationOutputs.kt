package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** Nonblocking boundary between phone control messages and vendor services. */
object BydNavigationOutputs {
    /** Recover a journaled interrupted output when the app opens, even before a phone reconnects. */
    fun onAppOpened(context: Context) {
        if (!GeelyVendorPolicy.bydNavigation) return
        if (BydStandaloneHudOutput.available(context)) start(context)
        // Read the battery early, so a reading is ready when CarPlay identifies (see batteryStatus).
        if (BydOutputSettings.batteryToIphone(context)) BydBatteryStatus.start(context)
    }
    fun setDiagnosticHold(hold: Boolean) { if (GeelyVendorPolicy.bydDiagnostics) BydStandaloneHudOutput.syntheticHold = hold }
    @Volatile private var useStandalone = false
    private val standalone by lazy { NavigationOutputWorker("diplay-standalone-output", BydStandaloneNavigationBridge::clear) }
    private val hud by lazy { NavigationOutputWorker("diplay-hud-output", BydHudBridge::clear) }
    private val cluster by lazy { NavigationOutputWorker("diplay-cluster-output", BydClusterBridge::clear) }

    /** The host reports whether its CarPlay map window is on the cluster (see [BydClusterMapPause]). */
    fun setClusterMapShown(shown: Boolean) { if (GeelyVendorPolicy.bydCluster) BydClusterMapPause.clusterMapShown = shown }

    /** The running CarPlay session: told every second whether the cluster currently shows the map. */
    fun setClusterStreamControl(control: (Boolean) -> Unit) { if (GeelyVendorPolicy.bydCluster) BydClusterMapPause.streamControl = control }

    fun clearClusterStreamControl(control: (Boolean) -> Unit) {
        if (!GeelyVendorPolicy.bydCluster) return
        if (BydClusterMapPause.streamControl == control) BydClusterMapPause.streamControl = null
    }

    /**
     * The car's battery for the iPhone's vehicle status; starts reading it over adb. The electric
     * vehicle is declared only once a reading is there (see withVehicleStatusFrom).
     */
    fun batteryStatus(context: Context): com.shilapi.xcertplay.transport.VehicleStatusProvider =
        if (GeelyVendorPolicy.bydBattery) BydBatteryStatus.also { it.start(context) }
        else com.shilapi.xcertplay.transport.VehicleStatusProvider { null }

    fun start(context: Context) {
        if (!GeelyVendorPolicy.bydNavigation) return
        val app = context.applicationContext
        useStandalone = BydStandaloneHudOutput.available(app)
        if (useStandalone) standalone.start { BydStandaloneNavigationBridge.initialize(app) }
        else {
            hud.start { BydHudBridge.initialize(app) }
            cluster.start { BydClusterBridge.initialize(app) }
        }
        BydClusterMapPause.initialize(app)
    }

    internal fun onFrame(frame: Iap2Frame) {
        if (!GeelyVendorPolicy.bydNavigation) return
        if (frame.messageId != BydHudRouteState.ROUTE_GUIDANCE_UPDATE &&
            frame.messageId != BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE) return
        val owned = frame // Iap2Frame is immutable and defensively copies its payload.
        if (useStandalone) standalone.submit { BydStandaloneNavigationBridge.onFrame(owned) }
        else {
            hud.submit { BydHudBridge.onFrame(owned) }
            cluster.submit { BydClusterBridge.onFrame(owned) }
        }
    }

    /** Best effort while alive; Android does not guarantee callbacks before force-stop. */
    fun endNow() {
        if (!GeelyVendorPolicy.bydNavigation) return
        standalone.clear(); hud.clear(); cluster.clear()
    }
}
