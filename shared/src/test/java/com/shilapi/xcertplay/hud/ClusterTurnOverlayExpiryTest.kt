package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterTurnOverlayExpiryTest {
    @Test fun pureSilentRouteExpiresWithoutAnotherPhonePacket() = withRoute { route, advance ->
        assertEquals(150, route.currentApple()!!.distanceMeters)
        advance(120_000_000_000L)
        assertNull(route.currentApple())
    }

    @Test fun pureEmptyManeuverListExpiresAfterItsGracePeriod() = withRoute { route, advance ->
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x0d))
        advance(8_000_000_000L)
        assertNull(route.currentApple())
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x0d, 0, 7))
        assertEquals(150, route.currentApple()!!.distanceMeters)
    }

    @Test fun pureNoRoutePacketsDoNotExtendRetainedGuidanceOrReplaceItsRoad() = withRoute { route, advance ->
        val before = route.currentApple()
        advance(119_000_000_000L)
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x01, 0) + tlv(0x03, 88))
        assertEquals(before, route.currentApple())
        advance(120_000_000_000L)
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x01, 0))
        assertNull(route.currentApple())
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x01, 2))
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x0a, 0, 0, 0, 5))
        assertNull(route.currentApple())
    }

    @Test fun geelyCannotPublishEvenAnInjectedCachedRouteOrListener() {
        val type = BydNavigationOutputs.javaClass
        val route = type.getDeclaredField("overlayRoute").apply { isAccessible = true }
            .get(BydNavigationOutputs) as BydHudRouteState
        val listener = type.getDeclaredField("overlayListener").apply { isAccessible = true }
        val originalListener = listener.get(BydNavigationOutputs)
        route.clear()
        seed(route)
        try {
            listener.set(BydNavigationOutputs, { _: ClusterTurnGuidance? -> error("Geely must not publish cluster guidance") })
            BydNavigationOutputs.setTurnOverlayListener { error("Geely must not register cluster listeners") }
            repeat(3) {
                BydNavigationOutputs.refreshTurnOverlay()
                BydNavigationOutputs.endNow(preserveTurnOverlay = true)
                BydNavigationOutputs.endNow()
            }
            assertNull(type.getDeclaredMethod("currentOverlay").apply { isAccessible = true }.invoke(BydNavigationOutputs))
        } finally {
            listener.set(BydNavigationOutputs, originalListener)
            route.clear()
        }
    }

    private fun withRoute(test: (BydHudRouteState, (Long) -> Unit) -> Unit) {
        var now = 0L
        val route = BydHudRouteState(nanoTime = { now }, staleRouteNs = 120_000_000_000L,
            emptyListHideNs = 8_000_000_000L, keepAcrossNoRoute = true)
        seed(route)
        test(route) { now = it }
    }

    private fun seed(route: BydHudRouteState) {
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE, tlv(0x01, 0, 7) + tlv(0x03, 1))
        route.accept(BydHudRouteState.ROUTE_GUIDANCE_UPDATE,
            tlv(0x01, 1) + tlv(0x0a, 0, 0, 0, 150) + tlv(0x0d, 0, 7))
    }
    private fun tlv(type: Int, vararg bytes: Int): ByteArray =
        byteArrayOf(0, (bytes.size + 4).toByte(), (type ushr 8).toByte(), type.toByte()) + bytes.map(Int::toByte).toByteArray()
}
