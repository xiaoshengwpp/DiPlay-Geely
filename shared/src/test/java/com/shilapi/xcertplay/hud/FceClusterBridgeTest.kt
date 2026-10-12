package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class FceClusterBridgeTest {
    private val writes = mutableListOf<Pair<Int, Int>>()
    private lateinit var app: Context

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        // Simulate preferences imported from upstream; setters correctly refuse these in this fork.
        app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE).edit()
            .putBoolean("navigation_enabled", true).putBoolean("fce_cluster_guidance_experimental", true).commit()
        FceClusterBridge.resetForTest({ id, value -> writes += id to value; true }, app)
    }

    @After
    fun tearDown() {
        FceClusterBridge.resetForTest({ _, _ -> true }, null)
        app.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun maneuver(index: Int, type: Int, side: Int = 0) = FceClusterBridge.onFrame(
        Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, index), tlv(0x03, type), tlv(0x08, side))),
    )

    private fun route(state: Int, distance: Int, current: Int? = null) = FceClusterBridge.onFrame(
        Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlvs(
            tlv(0x01, state),
            tlv(0x0a, distance ushr 24 and 0xff, distance ushr 16 and 0xff, distance ushr 8 and 0xff, distance and 0xff),
            *listOfNotNull(current?.let { tlv(0x0d, 0, it) }).toTypedArray(),
        )),
    )

    private val status = FceClusterProperties.NAVI_STATUS
    private val icon = FceClusterProperties.ICON
    private val icon2 = FceClusterProperties.ICON_SET_2
    private val distance = FceClusterProperties.DISTANCE

    @Test
    fun `disabled preference writes nothing`() {
        BydOutputSettings.setFceCluster(app, false)
        maneuver(index = 2, type = 1)
        route(state = 1, distance = 178, current = 2)

        assertEquals(emptyList<Pair<Int, Int>>(), writes)
    }

    @Test
    fun `carplay maneuver cannot reach the cluster even with forged opt-in`() {
        maneuver(index = 2, type = 1)
        route(state = 1, distance = 178, current = 2)

        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `next maneuver and distance changes remain silent`() {
        maneuver(index = 0, type = 1)
        maneuver(index = 1, type = 30)
        route(state = 1, distance = 300, current = 0)

        route(state = 1, distance = 250, current = 0)
        route(state = 1, distance = 1530, current = 1)

        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `arrival does not issue vendor cleanup writes`() {
        maneuver(index = 0, type = 12)
        route(state = 1, distance = 40, current = 0)

        route(state = 2, distance = 0)

        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `session end does not issue vendor cleanup writes`() {
        maneuver(index = 0, type = 2)
        route(state = 1, distance = 90, current = 0)

        FceClusterBridge.clear()

        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    private fun raw(vararg bytes: Int) = FceClusterBridge.onFrame(
        Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, ByteArray(bytes.size) { bytes[it].toByte() }),
    )

    // The two RouteGuidanceUpdate frames an older iPhone sent with Google Maps: a source name and
    // "no route guidance", never a state or a maneuver.
    private fun googleMapsSource() = raw(
        0x00, 0x10, 0x00, 0x13, 'G'.code, 'o'.code, 'o'.code, 'g'.code, 'l'.code, 'e'.code, ' '.code,
        'M'.code, 'a'.code, 'p'.code, 's'.code, 0x00,
        0x00, 0x05, 0x00, 0x14, 0x00,
    )

    private fun emptySource() = raw(0x00, 0x05, 0x00, 0x13, 0x00)

    @Test
    fun `navigation source without maneuvers cannot unlock the vendor view`() {
        googleMapsSource()
        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `empty source name issues no close writes`() {
        googleMapsSource()
        emptySource()
        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `empty source before any navigation writes nothing`() {
        emptySource()
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `active route without maneuver cannot unlock vendor view`() {
        route(state = 1, distance = 0)
        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `route after session end remains disabled`() {
        maneuver(index = 0, type = 1)
        route(state = 1, distance = 200, current = 0)
        FceClusterBridge.clear()

        route(state = 5, distance = 0) // rerouting, maneuvers not resent yet
        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `no route state with source name remains silent`() {
        googleMapsSource()
        route(state = 0, distance = 0)
        assertTrue("No vendor write may escape this product boundary", writes.isEmpty())
    }

    @Test
    fun `disabled setting sends nothing`() {
        BydOutputSettings.setEnabled(app, false)
        maneuver(index = 0, type = 1)
        route(state = 1, distance = 100, current = 0)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun `frames before initialize are ignored`() {
        FceClusterBridge.resetForTest({ id, value -> writes += id to value; true }, null)
        maneuver(index = 0, type = 1)
        route(state = 1, distance = 100, current = 0)
        assertTrue(writes.isEmpty())
    }

    private fun tlv(type: Int, vararg value: Int): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8)
        write(length)
        write(type ushr 8)
        write(type)
        value.forEach(::write)
    }.toByteArray()

    private fun tlvs(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        values.forEach(::write)
    }.toByteArray()
}
