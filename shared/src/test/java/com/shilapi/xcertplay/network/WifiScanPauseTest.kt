package com.shilapi.xcertplay.network

import android.content.Context
import android.content.ContextWrapper
import com.shilapi.xcertplay.adb.LocalAdb
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WifiScanPauseTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun geelyNeverAcquiresOrReleasesVendorWifiScanLeases() {
        val control = object : WifiScanPauseControl {
            override fun acquire(app: Context, owner: Any, current: () -> Boolean, log: (String) -> Unit) {
                error("Geely must not acquire a vendor Wi-Fi scan lease")
            }
            override fun release(app: Context, owner: Any, log: (String) -> Unit) {
                error("Geely must not restore vendor Wi-Fi scan settings")
            }
        }
        val pause = WifiScanPause(context, { error("Disabled lease must not log activity") }, control)
        repeat(3) { pause.pause(); pause.close(); pause.pause() }
    }

    @Test
    fun disabledDefaultControlNeverTouchesContextEvenWhenRecoveryIsRequested() {
        val context = ContextWrapper(null)
        val pause = WifiScanPause(context, { error("Disabled control must not log activity") })
        repeat(3) {
            pause.pause()
            pause.close()
            WifiScanPause.restoreIfNeeded(context)
        }
    }

    @Test
    fun noBackendOrAndroidReleaseCanEnableTheVendorWifiScanRoute() {
        for (backend in WirelessHotspotBackend.entries) {
            for (sdk in 24..36) assertFalse("$backend on API $sdk", WifiScanPause.eligible(backend, sdk))
        }
    }

    @Test
    fun disabledCommandTemplatesNeverLaunchShellHelpers() {
        for (apk in listOf("/data/app/~~a/com.shihab.diplay-b/base.apk", "/data/it's/base.apk")) {
            assertEquals("", WifiScanPause.command(apk, enabled = false))
            assertEquals("", WifiScanPause.command(apk, enabled = true))
        }
    }

    @Test
    fun readsOnlyTheHelperProtocolLine() {
        for (result in WifiScanSwitchResult.entries) {
            assertEquals(result, WifiScanPause.parse("WARNING: linker noise\nDIPLAY_WIFI_SCAN_V1|${result.name}\n"))
        }
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.parse(null))
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.parse(""))
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.parse("Error: Could not find class"))
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.parse("DIPLAY_WIFI_SCAN_V1|PAUSED_MAYBE"))
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.parse("Result: Parcel(00000000    '....')"))
    }

    @Test
    fun aMissingReplyReportsWhatAdbSaid() {
        assertEquals(WifiScanSwitchResult.ADB_NOT_APPROVED, WifiScanPause.unanswered(LocalAdb.Access.NOT_APPROVED))
        assertEquals(WifiScanSwitchResult.ADB_OFF, WifiScanPause.unanswered(LocalAdb.Access.UNREACHABLE))
        assertEquals(WifiScanSwitchResult.ADB_PAIRING_ONLY, WifiScanPause.unanswered(LocalAdb.Access.UNSUPPORTED))
        // A connected shell may have run the helper before the link dropped.
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.unanswered(LocalAdb.Access.READY))
        assertEquals(WifiScanSwitchResult.UNKNOWN, WifiScanPause.unanswered(null))
    }
}
