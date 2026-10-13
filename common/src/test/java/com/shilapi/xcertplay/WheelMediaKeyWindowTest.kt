package com.shilapi.xcertplay

import android.content.Intent
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.hud.BydCarPlayCall
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.robolectric.util.ReflectionHelpers
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Standard foreground track keys work independently of the optional vendor-private bridge.
 *
 * This is the route that has to work when the wheel key service cannot be turned on — a head unit
 * with no ADB has no route to it — so the tests here drive `dispatchKeyEvent` directly rather than
 * going through an accessibility service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WheelMediaKeyWindowTest {
    private lateinit var host: CarPlayHostActivity
    private lateinit var controller: CarPlayController

    @Before fun setUp() {
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        // A session has to exist: outside one the wheel keeps the car's own action.
        controller = mock(CarPlayController::class.java)
        `when`(controller.sendMediaButton(anyInt())).thenReturn(true)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        CarPlayMediaKeys.attach(host, controller)
        field("controller", controller)
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
    }

    @After fun tearDown() {
        CarPlayMediaKeys.detach(controller)
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (host.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(host) as ExecutorService).shutdownNow()
        }
        (host.javaClass.getDeclaredField("mainHandler").apply { isAccessible = true }.get(host) as Handler)
            .removeCallbacksAndMessages(null)
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun foregroundClaimsStandardTrackKeys() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true)))
        verify(controller).sendMediaButton(CarPlayMediaButton.PREVIOUS)
    }

    @Test fun windowAndMediaSessionEchoSendOnlyOnce() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true, repeat = 1)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        val callback = ReflectionHelpers.getField<CarPlayMediaCallback>(CarPlayMediaKeys, "callback")
        for (event in listOf(key(KeyEvent.KEYCODE_MEDIA_NEXT, true),
            key(KeyEvent.KEYCODE_MEDIA_NEXT, true, repeat = 1), key(KeyEvent.KEYCODE_MEDIA_NEXT, false))) {
            callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event))
        }
        assertTrue(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        verify(controller, times(1)).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun bluetoothAvrcpKeepsPriorityAcrossAllSoftwareRoutes() {
        AirPlayPersistence.saveCarBluetoothAudio(host, true)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        val callback = ReflectionHelpers.getField<CarPlayMediaCallback>(CarPlayMediaKeys, "callback")
        assertFalse(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON)
            .putExtra(Intent.EXTRA_KEY_EVENT, key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true))))
        callback.onSkipToNext()
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        verify(controller, never()).sendMediaButton(anyInt())
    }

    @Test fun theKeysKeepTheCarsOwnActionDuringACall() {
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(inCall(host))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        verify(controller, never()).sendMediaButton(anyInt())
    }

    @Test fun theKeysKeepTheCarsOwnActionWithoutASession() {
        field("activeAirPlaySession", null)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        verify(controller, never()).sendMediaButton(anyInt())
    }

    @Test fun playPauseIsLeftAlone() {
        // The wheel key service and BYD itself both own this key; the window must not take it.
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true)))
        assertFalse(host.dispatchKeyEvent(key(CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE, true)))
        verify(controller, never()).sendMediaButton(anyInt())
    }

    @Test fun interruptedWindowPressConsumesItsReleaseWithoutSendingAgain() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        field("activeAirPlaySession", null)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        verify(controller, times(1)).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun aFailedSendDoesNotSwallowAnotherRoutesWorkingFallback() {
        `when`(controller.sendMediaButton(anyInt())).thenReturn(false, true)
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        assertTrue(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        verify(controller, times(2)).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun staleOrInactiveControllersCannotReceiveExternalMediaOrSiri() {
        val stale = mock(CarPlayController::class.java)
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, stale) { true })
        assertFalse(CarPlayMediaKeys.onExternalSiriKey(stale) { true })
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { false })
        assertFalse(CarPlayMediaKeys.onExternalSiriKey(controller) { false })
        `when`(controller.activeAirPlaySessionToken()).thenReturn(null)
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        assertFalse(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).requestSiri()
    }

    @Test fun callsBlockMediaSessionPrivateMediaAndPrivateSiri() {
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        val callback = ReflectionHelpers.getField<CarPlayMediaCallback>(CarPlayMediaKeys, "callback")
        callback.onSkipToNext()
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        assertFalse(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).requestSiri()
    }

    @Test fun legacySiriDoesNotClaimNewPressesOutsideSessionOrDuringCall() {
        field("activeAirPlaySession", null)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
        verify(controller, never()).requestSiri()
    }

    @Test fun ownedLegacySiriReleaseIsConsumedButDoesNotInterruptANewCall() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
        verify(controller, never()).requestSiri()
    }

    @Test fun legacySiriPressCannotTransferToAReplacementSession() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
        verify(controller, never()).requestSiri()
    }

    private fun field(name: String, value: Any?) = host.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(host, value)

    private fun key(code: Int, down: Boolean, repeat: Int = 0) =
        KeyEvent(1_000, 1_000, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            code, repeat, 0, -1, 0)
}
