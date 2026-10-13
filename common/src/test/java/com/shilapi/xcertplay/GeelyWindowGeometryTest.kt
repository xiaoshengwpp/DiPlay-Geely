package com.shilapi.xcertplay

import com.shilapi.xcertplay.media.CarPlayVideoLayout
import org.junit.Assert.*
import org.junit.Test

class GeelyWindowGeometryTest {
    @Test fun insetsAreSubtractedFromTheActualWindowRatherThanSummedDisplays() {
        val full = GeelyWindowGeometry(1920, 1080, 0, 48, 0, 72)
        val split = full.copy(width = 720)
        assertEquals(1920, full.contentWidth)
        assertEquals(960, full.contentHeight)
        assertEquals(720, split.contentWidth)
        val layout = CarPlayVideoLayout.fit(1920, 960, split.contentWidth, split.contentHeight)
        assertEquals(720f, layout.width, 0.001f)
        assertEquals(360f, layout.height, 0.001f)
        assertFalse(layout.contains(360f, 20f))
        assertTrue(layout.contains(360f, 480f))
    }

    @Test fun portraitAndLandscapePreserveCanvasAspectAndTouchContentBounds() {
        for (window in listOf(1920 to 960, 960 to 1920, 720 to 960, 2560 to 1440)) {
            val fitted = CarPlayVideoLayout.fit(1920, 960, window.first, window.second)
            assertEquals(2f, fitted.width / fitted.height, 0.0001f)
            assertTrue(fitted.left >= 0f && fitted.top >= 0f)
            assertTrue(fitted.left + fitted.width <= window.first + 0.001f)
            assertTrue(fitted.top + fitted.height <= window.second + 0.001f)
            assertTrue(fitted.contains(fitted.left, fitted.top))
            assertTrue(fitted.contains(fitted.left + fitted.width, fitted.top + fitted.height))
            assertFalse(fitted.contains(fitted.left - 1, fitted.top + fitted.height / 2))
        }
    }

    @Test fun hiddenBarsAndRestoredBarsUseTheCurrentInsetValues() {
        val withBars = GeelyWindowGeometry(1920, 1080, 0, 48, 0, 72)
        val hidden = withBars.copy(insetTop = 0, insetBottom = 0)
        assertEquals(960, withBars.contentHeight)
        assertEquals(1080, hidden.contentHeight)
        assertEquals(withBars, hidden.copy(insetTop = 48, insetBottom = 72))
    }

    @Test fun sideInsetsAndCutoutsAreAccountedFor() {
        val window = GeelyWindowGeometry(1920, 1080, 80, 24, 120, 40)
        assertEquals(1720, window.contentWidth)
        assertEquals(1016, window.contentHeight)
    }

    @Test fun invalidTransientMeasurementsStayBoundedWithoutOverflow() {
        assertEquals(0, GeelyWindowGeometry(-1, -1, 0, 0, 0, 0).contentWidth)
        assertEquals(0, GeelyWindowGeometry(1, 1, Int.MAX_VALUE, Int.MAX_VALUE,
            Int.MAX_VALUE, Int.MAX_VALUE).contentHeight)
        assertEquals(20, GeelyWindowGeometry(20, 30, -10, -10, -10, -10).contentWidth)
    }

    @Test fun returningFromSplitScreenRestoresTheOriginalContentBounds() {
        CarPlayVideoLayout.fit(1920, 960, 700, 960)
        assertEquals(CarPlayVideoLayout(0f, 0f, 1920f, 960f), CarPlayVideoLayout.fit(1920, 960, 1920, 960))
    }
}
