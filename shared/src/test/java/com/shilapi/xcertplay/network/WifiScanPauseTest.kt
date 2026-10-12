package com.shilapi.xcertplay.network

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val ok = "Result: Parcel(00000000    '....')"

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
    fun noBackendOrAndroidReleaseCanEnableTheVendorWifiScanRoute() {
        for (backend in WirelessHotspotBackend.entries) {
            for (sdk in listOf(25, 28, 29, 30, 33)) assertFalse(WifiScanPause.eligible(backend, sdk))
        }
    }

    @Test
    fun acceptsOnlyAVoidReplyWithoutException() {
        assertTrue(WifiScanPause.accepted(ok))
        assertFalse(WifiScanPause.accepted("Result: Parcel(ffffffec 00000000 '........')"))
        assertFalse(WifiScanPause.accepted("service: Service wifi does not exist"))
        assertFalse(WifiScanPause.accepted(null))
    }

    @Test
    fun readsTheTransactionCodeFromTheFramework() {
        val expected = Class.forName("android.net.wifi.IWifiManager\$Stub")
            .getDeclaredField("TRANSACTION_enableWifiConnectivityManager")
            .apply { isAccessible = true }
            .getInt(null)
        assertEquals(expected, WifiScanPause.enableConnectivityManagerTransaction())
    }

    @Test
    @Config(sdk = [30])
    fun skipsReleasesAfterAndroid10() {
        assertNull(WifiScanPause.enableConnectivityManagerTransaction())
    }
}
