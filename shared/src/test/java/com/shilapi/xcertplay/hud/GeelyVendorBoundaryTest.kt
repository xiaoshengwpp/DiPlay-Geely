package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyVendorBoundaryTest {
    // Any attempt to discover services, read old preferences or load ADB keys fails
    // the test. No real context, transport, vehicle or authentication input is used.
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("vendor context accessed")
        override fun getPackageManager(): PackageManager = error("vendor packages probed")
        override fun getPackageName(): String = error("vendor package checked")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("vendor preferences or keys read")
        override fun getSystemService(name: String): Any = error("vendor service accessed")
    }

    @Test fun experimentalProfileHasNoBydCapabilities() {
        assertEquals("geely-experimental", GeelyVendorPolicy.PROFILE)
        assertFalse(GeelyVendorPolicy.bydNavigation)
        assertFalse(GeelyVendorPolicy.bydCluster)
        assertFalse(GeelyVendorPolicy.bydTyres)
        assertFalse(GeelyVendorPolicy.bydAmbientLamp)
        assertFalse(GeelyVendorPolicy.upstreamApkUpdates)
        assertFalse(GeelyVendorPolicy.bydBattery)
        assertFalse(GeelyVendorPolicy.bydWheelSpeed)
        assertFalse(GeelyVendorPolicy.videoWhileParked)
        assertFalse(GeelyVendorPolicy.bydAdb)
        assertFalse(GeelyVendorPolicy.bydDiagnostics)
    }

    @Test fun realLifecycleFacadeDoesNotTouchVendorContextOrClusterCallbacks() {
        val callback: (Boolean) -> Unit = { error("cluster callback invoked") }
        repeat(3) {
            BydNavigationOutputs.onAppOpened(rejectingContext)
            BydNavigationOutputs.start(rejectingContext)
            BydNavigationOutputs.setClusterStreamControl(callback)
            BydNavigationOutputs.setClusterMapShown(true)
            BydNavigationOutputs.setDiagnosticHold(true)
            BydNavigationOutputs.onFrame(Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, byteArrayOf()))
            BydNavigationOutputs.onFrame(Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE, byteArrayOf()))
            assertNull(BydNavigationOutputs.batteryStatus(rejectingContext).snapshot())
            BydNavigationOutputs.endNow()
            BydNavigationOutputs.clearClusterStreamControl(callback)
        }
        // Guards plus lazy worker creation must also cover reconnect and cleanup.
        for (name in listOf("standalone", "hud", "cluster")) {
            val field = BydNavigationOutputs::class.java.getDeclaredField(name + "\$delegate")
            field.isAccessible = true
            assertFalse("$name worker initialized", (field.get(BydNavigationOutputs) as Lazy<*>).isInitialized())
        }
    }

    @Test fun wheelSpeedFacadeAndDirectEntryCannotReadContextOrStartHardware() {
        val poll = BydWheelSpeedSource::class.java.getDeclaredMethod("poll").apply { isAccessible = true }
        repeat(3) {
            val facade = BydNavigationOutputs.wheelSpeed(rejectingContext)
            facade.start()
            assertNull(facade.drain())
            facade.stop()
            BydWheelSpeedSource.attach(rejectingContext)
            BydWheelSpeedSource.start()
            poll.invoke(BydWheelSpeedSource)
            assertNull(BydWheelSpeedSource.drain())
            BydWheelSpeedSource.stop()
            assertNull(BydNavigationOutputs.parked(rejectingContext))
            assertNull(BydParkedState.parked(rejectingContext))
        }
        for (name in listOf("context", "executor")) {
            val field = BydWheelSpeedSource::class.java.getDeclaredField(name).apply { isAccessible = true }
            assertNull("wheel-speed $name initialized", field.get(BydWheelSpeedSource))
        }
        for (target in listOf(BydWheelSpeedSource, BydParkedState)) {
            val field = target.javaClass.getDeclaredField("shell\$delegate").apply { isAccessible = true }
            assertFalse("ADB shell initialized", (field.get(target) as Lazy<*>).isInitialized())
        }
    }

    @Test fun disabledPreferencesShortCircuitBeforeContextAccess() {
        assertFalse(BydOutputSettings.wheelSpeedToIphone(rejectingContext))
        assertFalse(BydOutputSettings.videoWhileParked(rejectingContext))
    }

    @Test fun staleWheelSpeedSamplesCannotEscapeDisabledDrain() {
        val type = BydWheelSpeedSource::class.java
        val gear = type.getDeclaredField("gear").apply { isAccessible = true }
        val samples = type.getDeclaredField("samples").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val buffered = samples.get(BydWheelSpeedSource) as MutableList<com.shilapi.xcertplay.transport.VehicleSpeedSample>
        try {
            gear.set(BydWheelSpeedSource, com.shilapi.xcertplay.transport.VehicleGear.PARK)
            buffered += com.shilapi.xcertplay.transport.VehicleSpeedSample(1L, 0.0)
            assertNull(BydWheelSpeedSource.drain())
            assertEquals("disabled drain must not consume stale samples", 1, buffered.size)
        } finally {
            buffered.clear()
            gear.set(BydWheelSpeedSource, null)
        }
    }

    @Test fun adbAndDebugEntryPointsCannotLoadKeysOrReachTransports() {
        for (mayAsk in listOf(false, true)) {
            assertEquals(BydAdbAccess.State.DISABLED_FOR_VEHICLE, BydAdbAccess.check(rejectingContext, mayAsk).state)
        }
        assertEquals(BydAdbAccess.State.DISABLED_FOR_VEHICLE,
            BydAdbAccess.readStatus(rejectingContext) { error("shell command invoked") }.state)
        assertNull(BydAdbShell("test").run(rejectingContext, "must-not-run"))
        BydStarterBridge.initialize(rejectingContext)
        BydStarterBridge.configure(rejectingContext, "not-a-token")
        BydStarterBridge.demonstrate(rejectingContext)
        BydClusterMapPause.initialize(rejectingContext)
        BydBatteryStatus.start(rejectingContext)
        BydBatteryStatus.publishProbeReading(rejectingContext, BydBatteryReading(25.0, 150, 25.1, false))
        assertNull(BydBatteryStatus.snapshot())
    }
    @Test fun clusterSongDirectEntriesCannotReadContextWriteOrStartWorkers() {
        assertFalse(GeelyVendorPolicy.bydClusterSong)
        assertFalse(GeelyVendorPolicy.launcherMapSharing)
        assertFalse(BydOutputSettings.clusterSong(rejectingContext))
        BydOutputSettings.setClusterSong(rejectingContext, true)
        BydClusterSong.attach(rejectingContext)
        BydClusterSong.onFrame(Iap2Frame(ClusterSongState.NOW_PLAYING_UPDATE, byteArrayOf()))
        BydClusterSong.settingChanged(true)
        BydNavigationOutputs.clusterSongChanged(true)
        BydClusterSong.end()
        BydClusterSongTool.main(arrayOf("11", "1", "forged"))
        BydClusterSongTool::class.java.getDeclaredMethod("write", Array<String>::class.java)
            .apply { isAccessible = true }.invoke(BydClusterSongTool, arrayOf("11", "1", "forged"))
        for (name in listOf("shell", "writer")) {
            val field = BydClusterSong::class.java.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
            assertFalse("song $name initialized", (field.get(BydClusterSong) as Lazy<*>).isInitialized())
        }
    }

}
