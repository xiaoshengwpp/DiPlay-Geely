package com.shilapi.xcertplay

import android.app.Activity
import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class GeelyHudProjectionLifecycleTest {
    private lateinit var activity: Activity
    private lateinit var projection: GeelyHudProjection
    private var displayId = -1

    @Before fun setup() {
        CarPlayGlance.listener = null
        CarPlayGlance.setConnected(true)
        CarPlayGlance.setConnected(false)
        GeelyHudSelection.clear()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        displayId = ShadowDisplayManager.addDisplay("w1280dp-h480dp-mdpi", 5)
        shadowOf(activity.getSystemService(DisplayManager::class.java).getDisplay(displayId))
            .setFlags(Display.FLAG_PRESENTATION)
        projection = GeelyHudProjection(activity)
        projection.start()
    }

    @After fun cleanup() {
        projection.stop()
        ShadowDisplayManager.removeDisplay(displayId)
        CarPlayGlance.setConnected(false)
        activity.finish()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun guidance() {
        CarPlayGlance.setConnected(true)
        CarPlayGlance.onFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 7); u8(3, 2); string(4, "Road") })
        CarPlayGlance.onFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 1); u32(0x0a, 150); u16List(0x0d, listOf(7)) })
    }

    private fun refresh() = projection.onDisplayChanged(displayId)
    private fun presentation(): Presentation? = ReflectionHelpers.getField(projection, "presentation")

    private fun selectAndShow(): Presentation {
        guidance()
        assertTrue(GeelyHudSelection.select(activity, displayId))
        refresh()
        return requireNotNull(presentation()).also { assertTrue(it.isShowing) }
    }

    @Test fun selectionCreatesPresentationAndHostStopRevokesIt() {
        val shown = selectAndShow()
        projection.stop()
        assertFalse(shown.isShowing)
        assertNull(presentation())
        assertNull(GeelyHudSelection.selectedDisplayId())
        projection.start()
        assertNull(presentation())
    }

    @Test fun unexpectedDismissalRequiresSelectionAgain() {
        selectAndShow().dismiss()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(GeelyHudSelection.selectedDisplayId())
        refresh()
        assertNull(presentation())
    }

    @Test fun rapidReconnectDismissesWithoutRenderingTheNextSessionsGuidance() {
        val shown = selectAndShow()
        CarPlayGlance.setConnected(false)
        guidance()
        refresh()
        assertFalse(shown.isShowing)
        assertNull(presentation())
        assertNull(GeelyHudSelection.selectedDisplayId())
    }

    @Test fun routeEndClearsPresentationWhileKeepingCurrentSessionSelection() {
        val shown = selectAndShow()
        CarPlayGlance.onFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 2) })
        refresh()
        assertFalse(shown.isShowing)
        assertNull(presentation())
        assertEquals(displayId, GeelyHudSelection.selectedDisplayId())
    }

    @Test fun removalRevokesSelectionWithoutFallingBack() {
        val shown = selectAndShow()
        projection.onDisplayRemoved(displayId)
        assertFalse(shown.isShowing)
        assertNull(GeelyHudSelection.selectedDisplayId())
        refresh()
        assertNull(presentation())
    }
}
