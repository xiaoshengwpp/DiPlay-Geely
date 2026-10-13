package com.shilapi.xcertplay.media

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the existing mapper, with no alternate multi-display coordinate implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyWindowTouchRegressionTest {
    @Test fun resizeRotateAndSystemBarChangesKeepTouchCoordinatesInTheVideoContent() {
        for ((width, height) in listOf(1920 to 1080, 1920 to 960, 700 to 960, 960 to 1920, 1920 to 960)) {
            val layout = CarPlayVideoLayout.fit(1920, 960, width, height)
            for ((x, y) in listOf(0f to 0f, 0.25f to 0.75f, 1f to 1f)) {
                val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN,
                    layout.left + layout.width * x, layout.top + layout.height * y, 0)
                try {
                    val contact = CarPlayTouchMapper.contacts(event, layout).single()
                    assertEquals(x.toDouble(), contact.x, 0.00001)
                    assertEquals(y.toDouble(), contact.y, 0.00001)
                    assertTrue(contact.down)
                } finally { event.recycle() }
            }
        }
    }

    @Test fun cancelAfterWindowResizeReleasesTheTouchEvenOutsideTheNewContent() {
        val layout = CarPlayVideoLayout.fit(1920, 960, 700, 960)
        val event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_CANCEL, 1919f, 959f, 0)
        try {
            val contact = CarPlayTouchMapper.contacts(event, layout).single()
            assertFalse(contact.down)
            assertEquals(1.0, contact.x, 0.00001)
            assertEquals(1.0, contact.y, 0.00001)
        } finally { event.recycle() }
    }
}
