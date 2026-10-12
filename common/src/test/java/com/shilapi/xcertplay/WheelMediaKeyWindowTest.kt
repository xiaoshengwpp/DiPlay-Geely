package com.shilapi.xcertplay

import android.content.Intent
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.hud.BydCarPlayCall
import com.shilapi.xcertplay.hud.CarPlayCallState
import com.shilapi.xcertplay.iap2.message.Iap2Messages
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
 * The unverified upstream foreground bridge is disabled in this Geely product.
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
        field("controller", controller)
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
    }

    @After fun tearDown() {
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (host.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(host) as ExecutorService).shutdownNow()
        }
        (host.javaClass.getDeclaredField("mainHandler").apply { isAccessible = true }.get(host) as Handler)
            .removeCallbacksAndMessages(null)
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun unverifiedWindowBridgeLeavesTrackKeysToTheMediaSession() {
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        verify(controller, never()).sendMediaButton(CarPlayMediaButton.NEXT)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true)))
        verify(controller, never()).sendMediaButton(CarPlayMediaButton.PREVIOUS)
    }

    @Test fun windowNeverDuplicatesMediaSessionDownRepeatOrUp() {
        // BYD sends a tap as an instant down/up pair, and a hold as repeats. Either way CarPlay must
        // see one press, and the release must not reach BYD's own player as an unpaired key.
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true, repeat = 1)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        verify(controller, never()).sendMediaButton(anyInt())
        val callback = CarPlayMediaCallback { index, _ -> controller.sendMediaButton(index); Unit }
        for (event in listOf(key(KeyEvent.KEYCODE_MEDIA_NEXT, true),
            key(KeyEvent.KEYCODE_MEDIA_NEXT, true, repeat = 1), key(KeyEvent.KEYCODE_MEDIA_NEXT, false))) {
            callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event))
        }
        verify(controller, times(1)).sendMediaButton(CarPlayMediaButton.NEXT)
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

    @Test fun interruptedWindowPressDoesNotCreateAnUnpairedCarPlayAction() {
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        field("activeAirPlaySession", null)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        `when`(controller.sendMediaButton(anyInt())).thenReturn(false)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        verify(controller, never()).sendMediaButton(anyInt())
    }

    private fun field(name: String, value: Any?) = host.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(host, value)

    private fun key(code: Int, down: Boolean, repeat: Int = 0) =
        KeyEvent(1_000, 1_000, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            code, repeat, 0, -1, 0)
}
