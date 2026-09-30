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
        assertFalse(GeelyVendorPolicy.bydBattery)
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
        BydBatteryStatus.accept(rejectingContext, BydBatteryReading(25.0, 150, 25.1, false))
        assertNull(BydBatteryStatus.snapshot())
    }
}
