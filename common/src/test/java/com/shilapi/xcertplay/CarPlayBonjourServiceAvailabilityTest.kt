package com.shilapi.xcertplay

import android.content.Context
import android.net.wifi.WifiManager
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.network.CarPlayBonjour
import java.io.IOException
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Single-interface Geely discovery must not depend on the optional Android NSD service. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayBonjourServiceAvailabilityTest {
    private val context = mock(Context::class.java)
    private val wifi = mock(WifiManager::class.java)
    private val lock = mock(WifiManager.MulticastLock::class.java)
    private val address = InetAddress.getByName("192.168.49.1")

    @Before fun setup() {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSystemService(WifiManager::class.java)).thenReturn(wifi)
        `when`(wifi.createMulticastLock("carplay-bonjour")).thenReturn(lock)
        `when`(lock.isHeld).thenReturn(true)
    }

    @Test fun interfaceMdnsStartsAndClosesWithoutLookingUpNsd() {
        // JmDNS is an implementation dependency of shared. Reflect only in this test rather
        // than broadening production dependency exposure for a mock of its factory.
        @Suppress("UNCHECKED_CAST")
        val dnsClass = Class.forName("javax.jmdns.JmDNS") as Class<Any>
        val create = dnsClass.getMethod("create", InetAddress::class.java, String::class.java)
        val dns = mock(dnsClass)
        mockStatic(dnsClass).use { factory ->
            factory.`when`<Any> { create.invoke(null, address, "carplay-020000000002") }.thenReturn(dns)
            bonjour(true).use { it.start() }
            factory.verify { create.invoke(null, address, "carplay-020000000002") }
            factory.verifyNoMoreInteractions()
            val methods = mockingDetails(dns).invocations.map { it.method.name }
            assertEquals(1, methods.count { it == "addServiceListener" })
            assertEquals(1, methods.count { it == "registerService" })
            assertEquals(1, methods.count { it == "close" })
        }
        verify(context, never()).getSystemService(Context.NSD_SERVICE)
        verify(lock).acquire()
        verify(lock).release()
    }

    @Test fun nativeDiscoveryReportsMissingNsdAndReleasesTheLock() {
        bonjour(false).use {
            try {
                it.start()
                fail("Missing Android NSD must fail native discovery")
            } catch (error: IOException) {
                assertEquals("Android NSD service is unavailable", error.message)
            }
        }
        verify(context).getSystemService(Context.NSD_SERVICE)
        verify(lock).acquire()
        verify(lock).release()
    }

    private fun bonjour(interfaceMdns: Boolean) = CarPlayBonjour(
        context,
        AirPlayConfig("test", "02:00:00:00:00:02", "", "1", AirPlayDisplayConfig(800, 480)),
        AirPlayIdentity(ByteArray(32), ByteArray(32), "test"),
        advertisedHost = address.hostAddress,
        useInterfaceMdns = interfaceMdns,
    )
}
