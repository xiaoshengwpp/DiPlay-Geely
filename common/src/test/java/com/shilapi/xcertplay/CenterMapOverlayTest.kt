package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import android.view.Surface
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "sw720dp-w1280dp-h720dp-hdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class CenterMapOverlayTest {
    private lateinit var context: Context
    private var taps = 0
    private val surfaces = mutableListOf<Surface?>()
    private val geometry get() = context.getSharedPreferences("diplay_center_map", Context.MODE_PRIVATE)

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        CenterMapOverlay.hide()
        CenterMapOverlay.requestShow = null
        ShadowSettings.setCanDrawOverlays(true)
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("cluster_map_enabled", true)
            .putBoolean("center_map_overlay", true).commit()
        geometry.edit().clear().putInt("width", 640).putInt("x", 500).putInt("y", 300).commit()
    }

    @After fun cleanup() {
        CenterMapOverlay.requestShow = null
        CenterMapOverlay.onDiPlayScreenShown()
        ShadowSettings.setCanDrawOverlays(false)
    }

    private fun assertRejected(aspect: Double) {
        val saved = geometry.all.toMap()
        assertFalse(CenterMapOverlay.show(context, aspect, { surfaces += it }, { taps++ }))
        assertFalse(CenterMapOverlay.shown)
        assertNull(CenterMapOverlay.javaClass.getDeclaredField("root")
            .apply { isAccessible = true }.get(CenterMapOverlay))
        assertEquals(saved, geometry.all)
        assertTrue(surfaces.isEmpty())
        assertEquals(0, taps)
    }

    @Test fun grantedAndroidOverlayPermissionCannotEnableTheGeelyMapCard() {
        assertTrue(android.provider.Settings.canDrawOverlays(context))
        assertFalse(CenterMapOverlay.permitted(context))
        assertFalse(AirPlayPersistence.loadCenterMapOverlay(context))
        assertRejected(MapMirrors.PHYSICAL_STREAM_ASPECT)
    }

    @Test fun deniedAndroidOverlayPermissionAlsoLeavesStateAndCallbacksUntouched() {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(CenterMapOverlay.permitted(context))
        assertRejected(MapMirrors.PHYSICAL_STREAM_ASPECT)
    }

    @Test fun virtualAspectCannotCreateACardOrRewriteSavedGeometry() {
        geometry.edit().clear().putInt("width", 720).commit()
        assertRejected(MapMirrors.VIRTUAL_STREAM_ASPECT)
    }

    @Test fun changedAspectCannotAdaptPersistedWidthAcrossRepeatedShows() {
        geometry.edit().putInt("width", 1000)
            .putLong("aspect", java.lang.Double.doubleToRawLongBits(MapMirrors.PHYSICAL_STREAM_ASPECT)).commit()
        repeat(2) {
            assertRejected(MapMirrors.VIRTUAL_STREAM_ASPECT)
            CenterMapOverlay.hide()
        }
        assertEquals(1000, geometry.getInt("width", 0))
    }

    @Test fun invalidAspectsCannotAllocateAWindowOrChangePreferences() {
        for (aspect in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertRejected(aspect)
    }

    @Test fun schedulingRepeatedRequestsCannotInvokeTheShowCallback() {
        var requests = 0
        CenterMapOverlay.requestShow = { requests++ }
        repeat(3) { CenterMapOverlay.scheduleShow() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(0, requests)
        assertFalse(CenterMapOverlay.shown)
        assertTrue(surfaces.isEmpty())
    }

    @Test fun repeatedHideAndForegroundTransitionsCannotPublishASurfaceOrLaunchCarPlay() {
        repeat(3) {
            assertRejected(MapMirrors.PHYSICAL_STREAM_ASPECT)
            CenterMapOverlay.hide()
            CenterMapOverlay.onDiPlayScreenShown()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(surfaces.isEmpty())
        assertEquals(0, taps)
        assertFalse(CenterMapOverlay.shown)
    }
}
