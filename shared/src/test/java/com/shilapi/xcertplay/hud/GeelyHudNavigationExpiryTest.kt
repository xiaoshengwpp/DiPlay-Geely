package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test

/** The Android adapter polls CarPlayGlance, which uses this existing transport-neutral decoder. */
class GeelyHudNavigationExpiryTest {
    @Test fun pollingSnapshotsDoesNotRefreshTheThirtySecondSourceDeadline() {
        var nanos = 0L
        val route = BydHudRouteState(nanoTime = { nanos })
        route.accept(0x5202, tlv(1, 0, 7) + tlv(3, 1))
        route.accept(0x5201, tlv(1, 1) + tlv(10, 0, 0, 0, 150) + tlv(13, 0, 7))
        repeat(120) {
            nanos = it * 250_000_000L
            assertNotNull(route.currentApple())
        }
        nanos = 30_000_000_000L
        assertNull(route.currentApple())
        nanos += 1_000_000_000L
        assertNull(route.currentApple())
        route.accept(0x5201, tlv(10, 0, 0, 0, 100))
        assertEquals(100, route.currentApple()!!.distanceMeters)
    }

    @Test fun disconnectAndRouteEndCannotLeakThePreviousInstruction() {
        val route = BydHudRouteState()
        route.accept(0x5202, tlv(1, 0, 7) + tlv(3, 1))
        route.accept(0x5201, tlv(1, 1) + tlv(13, 0, 7))
        assertNotNull(route.currentApple())
        route.clear()
        assertNull(route.currentApple())
        route.accept(0x5201, tlv(1, 1) + tlv(13, 0, 7))
        assertNull(route.currentApple())
    }

    private fun tlv(id: Int, vararg bytes: Int): ByteArray =
        byteArrayOf(0, (bytes.size + 4).toByte(), 0, id.toByte()) + bytes.map { it.toByte() }.toByteArray()
}
