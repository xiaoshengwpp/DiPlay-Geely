package com.shilapi.xcertplay

import android.os.Looper
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Diagnostics supplement the existing Geely ownership/session guards; they never bypass them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MediaKeyReportTest {
    private val zone = TimeZone.getDefault()
    private val context = RuntimeEnvironment.getApplication()
    private val owners = mutableListOf<CarPlayController>()

    @Before fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
        WheelKeyLog.reset()
    }

    @After fun tearDown() {
        owners.forEach(CarPlayMediaKeys::detach)
        shadowOf(Looper.getMainLooper()).idle()
        AirPlayPersistence.saveCarBluetoothAudio(context, false)
        TimeZone.setDefault(zone)
        WheelKeyLog.reset()
    }

    private fun controller(session: Boolean = true): CarPlayController =
        mock(CarPlayController::class.java).also {
            owners += it
            `when`(it.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
            `when`(it.activeAirPlaySessionToken()).thenReturn(if (session) Any() else null)
            `when`(it.sendMediaButton(anyInt(), any(), any())).thenReturn(true)
        }

    private fun reportContains(text: String) = WheelKeyLog.report(bound = false).any { text in it }

    @Test fun aMediaKeyWithoutAControllerIsReportedAsNotSent() {
        val expected = controller()
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, expected) { true })
        assertTrue(reportContains("media key geely-wheel -> CarPlay ${CarPlayMediaButton.NEXT} sent=false reason=no-controller"))
        verify(expected, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun aMediaKeyWithoutASessionRemainsBlockedAndReported() {
        val expected = controller(session = false)
        CarPlayMediaKeys.attach(context, expected)
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, expected) { true })
        assertTrue(reportContains("sent=false reason=no-session"))
        verify(expected, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun aStaleOwnerCannotSendEvenWhenTheNewControllerHasASession() {
        val old = controller()
        val current = controller()
        CarPlayMediaKeys.attach(context, current)
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, old) { true })
        assertTrue(reportContains("sent=false reason=stale-owner"))
        verify(current, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun avrCpStillBlocksPrivateMediaDispatch() {
        val expected = controller()
        CarPlayMediaKeys.attach(context, expected)
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, expected) { true })
        assertTrue(reportContains("sent=false reason=call-or-avrcp"))
        verify(expected, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun acceptedOwnedMediaInputIsReportedWithoutChangingTheDispatchSignature() {
        val expected = controller()
        CarPlayMediaKeys.attach(context, expected)
        assertTrue(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, expected) { true })
        assertTrue(reportContains("media key geely-wheel -> CarPlay ${CarPlayMediaButton.NEXT} sent=true"))
        verify(expected).sendMediaButton(anyInt(), any(), any())
    }
}
