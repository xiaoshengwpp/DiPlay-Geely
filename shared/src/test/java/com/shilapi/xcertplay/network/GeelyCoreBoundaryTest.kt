package com.shilapi.xcertplay.network

import android.content.Context
import android.content.ContextWrapper
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.KeyPair

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class GeelyCoreBoundaryTest {
    @Test fun localAdbNeverConnectsOrOpensStreams() {
        LocalAdb(KeyPair(null, null), "invalid.invalid", 1).use { adb ->
            assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(true))
            assertEquals(LocalAdb.Access.UNSUPPORTED, adb.connect(false))
            assertNull(adb.shell("must not run"))
            assertNull(adb.shell("must not run", 1))
            assertNull(adb.openShell("must not run"))
        }
    }
    @Test fun adbKeyLoadStopsBeforeAccessingStorage() {
        assertThrows(IllegalStateException::class.java) { AdbKeys.load(ContextWrapper(null)) }
    }
    @Test fun forgedAutomaticCarHotspotPreferenceCannotMutateSystem() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("diplay_car_hotspot", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_enable", true).commit()
        assertFalse(CarHotspotSettings.enabled(context))
        assertFalse(CarHotspotSettings.shouldEnable(context, true, WirelessHotspotMode.MANUAL))
        assertFalse(CarHotspotSettings.visible(true, LocalAdb.Access.READY))
        assertEquals(CarHotspotTethering.Result.PERMISSION_REQUIRED,
            CarHotspotTethering.enable(ContextWrapper(null), { false }, log = { fail("must not log or mutate") }))
    }
    @Test fun hotspotRepairStopsBeforeAnyContextOrAdbAccess() {
        val repair = HotspotJoinRepair()
        for (action in HotspotJoinRepair.Action.entries) {
            assertEquals(HotspotJoinRepair.Code.UNSUPPORTED,
                repair.run(ContextWrapper(null), action).code)
        }
        repair.cancel()
        HotspotJoinRepairMain.main(emptyArray())
    }
    @Test fun wifiScanPauseNeverAcquiresOrReleasesHardwareLease() {
        val context = RuntimeEnvironment.getApplication() as Context
        val control = object : WifiScanPauseControl {
            override fun acquire(app: Context, owner: Any, current: () -> Boolean, log: (String) -> Unit) = fail("acquire")
            override fun release(app: Context, owner: Any, log: (String) -> Unit) = fail("release")
        }
        val pause = WifiScanPause(context, {}, control)
        pause.pause(); pause.close()
        WifiScanPause.restoreIfNeeded(ContextWrapper(null))
        for (backend in WirelessHotspotBackend.entries) assertFalse(WifiScanPause.eligible(backend, 29))
    }
}
