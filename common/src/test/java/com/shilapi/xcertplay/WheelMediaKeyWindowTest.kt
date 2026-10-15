package com.shilapi.xcertplay

import android.content.res.Configuration
import com.shilapi.xcertplay.airplay.AirPlayKnobState

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
import com.shilapi.xcertplay.orchestration.CarPlayCallGuard
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
    private val sessionToken = Any()

    @Before fun setUp() {
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSharedPreferences("diplay_foreground_wheel_keys", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        // A session has to exist: outside one the wheel keeps the car's own action.
        controller = mock(CarPlayController::class.java)
        `when`(controller.sendMediaButton(anyInt(), any(), any())).thenReturn(true)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(sessionToken)
        `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
        CarPlayMediaKeys.attach(host, controller)
        field("controller", controller)
        field("activeAirPlaySession", mock(AirPlaySession::class.java).also {
            `when`(it.mainScreenSessionToken()).thenReturn(sessionToken)
        })
    }

    @After fun tearDown() {
        host.getSharedPreferences("diplay_foreground_wheel_keys", Context.MODE_PRIVATE).edit().clear().commit()
        CarPlayCallGuard.end(this)
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
        verify(controller).sendMediaButton(eq(CarPlayMediaButton.NEXT), any(), any())
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true)))
        verify(controller).sendMediaButton(eq(CarPlayMediaButton.PREVIOUS), any(), any())
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
        verify(controller, times(1)).sendMediaButton(eq(CarPlayMediaButton.NEXT), any(), any())
    }

    @Test fun bluetoothAvrcpKeepsPriorityAcrossAllSoftwareRoutes() {
        AirPlayPersistence.saveCarBluetoothAudio(host, true)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        val callback = ReflectionHelpers.getField<CarPlayMediaCallback>(CarPlayMediaKeys, "callback")
        assertFalse(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON)
            .putExtra(Intent.EXTRA_KEY_EVENT, key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true))))
        callback.onSkipToNext()
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun theKeysKeepTheCarsOwnActionDuringACall() {
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(inCall(host))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun iapCallsBlockEveryMediaAndSiriRouteWithNormalAndroidAudio() {
        val session = Any()
        CarPlayCallGuard.start(this, session)
        val callback = ReflectionHelpers.getField<CarPlayMediaCallback>(CarPlayMediaKeys, "callback")
        for (status in 1..5) {
            CarPlayCallGuard.accept(this, session, Iap2Messages.buildRaw(0x4155) {
                u8(2, status)
                string(4, "call")
            })
            assertEquals(AudioManager.MODE_NORMAL, host.getSystemService(AudioManager::class.java).mode)
            assertTrue("iAP2 status=$status", inCall(host))
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
            assertFalse(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON)
                .putExtra(Intent.EXTRA_KEY_EVENT, key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true))))
            callback.onSkipToNext()
            assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
            assertFalse(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        }
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
        verify(controller, never()).requestSiri()
        verify(controller, never()).requestSiri(any(), any())
        CarPlayCallGuard.accept(this, session, Iap2Messages.buildRaw(0x4155) {
            u8(2, 0)
            string(4, "call")
        })
        assertFalse(inCall(host))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
    }

    @Test fun theKeysKeepTheCarsOwnActionWithoutASession() {
        field("activeAirPlaySession", null)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun playPauseIsLeftAlone() {
        // The wheel key service and BYD itself both own this key; the window must not take it.
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true)))
        assertFalse(host.dispatchKeyEvent(key(CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE, true)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun interruptedWindowPressConsumesItsReleaseWithoutSendingAgain() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        field("activeAirPlaySession", null)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        verify(controller, times(1)).sendMediaButton(eq(CarPlayMediaButton.NEXT), any(), any())
    }

    @Test fun aFailedSendDoesNotSwallowAnotherRoutesWorkingFallback() {
        `when`(controller.sendMediaButton(anyInt(), any(), any())).thenReturn(false, true)
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, false)))
        assertTrue(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        verify(controller, times(2)).sendMediaButton(eq(CarPlayMediaButton.NEXT), any(), any())
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
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
        verify(controller, never()).requestSiri()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun callsBlockMediaSessionPrivateMediaAndPrivateSiri() {
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        val callback = ReflectionHelpers.getField<CarPlayMediaCallback>(CarPlayMediaKeys, "callback")
        callback.onSkipToNext()
        assertFalse(CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, controller) { true })
        assertFalse(CarPlayMediaKeys.onExternalSiriKey(controller) { true })
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
        verify(controller, never()).requestSiri()
        verify(controller, never()).requestSiri(any(), any())
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
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun ownedLegacySiriReleaseIsConsumedButDoesNotInterruptANewCall() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
        verify(controller, never()).requestSiri()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun legacySiriPressCannotTransferToAReplacementSession() {
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, true)))
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, false)))
        verify(controller, never()).requestSiri()
        verify(controller, never()).requestSiri(any(), any())
    }

    private fun field(name: String, value: Any?) = host.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(host, value)

    private fun key(code: Int, down: Boolean, repeat: Int = 0) =
        KeyEvent(1_000, 1_000, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            code, repeat, 0, -1, 0)
    @Test fun importedCustomPlayPauseSettingCannotCaptureANonMediaKey() {
        val prefs = host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        assertFalse(WheelZoomSettings.mediaPlayPause(host))
        prefs.edit().putBoolean("media_play_pause", true)
            .putString("key_MEDIA_PLAY_PAUSE", WheelKey(KeyEvent.KEYCODE_F9, 0, "?").encode()).commit()
        val before = prefs.all.toMap()
        assertFalse(WheelZoomSettings.mediaPlayPause(host))
        assertFalse(WheelZoomSettings.isMediaPlayPauseKey(host, WheelKey(KeyEvent.KEYCODE_F9, 0, "?")))
        for (mode in listOf(AudioManager.MODE_NORMAL, AudioManager.MODE_IN_CALL)) {
            host.getSystemService(AudioManager::class.java).mode = mode
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true, repeat = 1)))
            assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        }
        field("activeAirPlaySession", null)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        verify(controller, never()).sendMediaButton(anyInt())
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
        assertEquals(before, prefs.all)
    }

    @Test fun tvSystemAndExistingTransportKeysAreReservedFromCustomAssignments() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE,
            CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_PLAY_PAUSE_DILINK3,
            CarPlayMediaButton.KEYCODE_BYD_CARPLAY_VOICE, CarPlayMediaButton.KEYCODE_BYD_CARPLAY_VOICE_LONG)) {
            assertTrue("reserved key $code", CarPlayRemoteKeys.isProtectedNavigationKey(code))
        }
        assertFalse(CarPlayRemoteKeys.isProtectedNavigationKey(KeyEvent.KEYCODE_F9))
    }

    @Test fun importedMediaRemapLeavesTvKnobAndForegroundTrackKeysUnchanged() {
        host.resources.configuration.touchscreen = Configuration.TOUCHSCREEN_NOTOUCH
        `when`(controller.sendKnob(AirPlayKnobState(select = true), false)).thenReturn(true)
        `when`(controller.sendKnob(AirPlayKnobState(), false)).thenReturn(true)
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit()
            .putBoolean("media_play_pause", true)
            .putString("key_MEDIA_PLAY_PAUSE", WheelKey(KeyEvent.KEYCODE_DPAD_CENTER, 0, "?").encode()).commit()
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_DPAD_CENTER, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_DPAD_CENTER, false)))
        verify(controller).sendKnob(AirPlayKnobState(select = true), false)
        verify(controller).sendKnob(AirPlayKnobState(), false)
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, true)))
        verify(controller).sendMediaButton(eq(CarPlayMediaButton.NEXT), any(), any())
        verify(controller).sendMediaButton(eq(CarPlayMediaButton.PREVIOUS), any(), any())
        verify(controller, never()).sendMediaButton(eq(CarPlayMediaButton.PLAY_PAUSE), any(), any())
    }

    private fun enableForegroundMedia() {
        ForegroundWheelSettings.setMediaPlayPause(host, true)
        ForegroundWheelSettings.assign(host, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE,
            WheelKey(KeyEvent.KEYCODE_F9, 0, "?"))
    }

    @Test fun foregroundOptInSendsOnePlayPauseForAnEntirePhysicalPress() {
        enableForegroundMedia()
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true, repeat = 1)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        verify(controller, times(1)).sendMediaButton(eq(CarPlayMediaButton.PLAY_PAUSE), any(), any())
        assertFalse(WheelKeyService.connected())
        assertFalse(WheelKeyService.wanted(host))
    }

    @Test fun optedInMediaYieldsToCallsMissingSessionsAndBluetoothAvrcp() {
        enableForegroundMedia()
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        AirPlayPersistence.saveCarBluetoothAudio(host, true)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        AirPlayPersistence.saveCarBluetoothAudio(host, false)
        field("activeAirPlaySession", null)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun mediaOwnedReleaseSurvivesDisableAndCallWithoutASecondAction() {
        enableForegroundMedia()
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        ForegroundWheelSettings.setMediaPlayPause(host, false)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        field("activeAirPlaySession", null)
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true, repeat = 1)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        verify(controller, times(1)).sendMediaButton(eq(CarPlayMediaButton.PLAY_PAUSE), any(), any())
    }

    @Test fun aForgedNewForegroundAssignmentCannotHijackTvOrTransportKeys() {
        ForegroundWheelSettings.setMediaPlayPause(host, true)
        val prefs = host.getSharedPreferences("diplay_foreground_wheel_keys", Context.MODE_PRIVATE)
        for (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            prefs.edit().putString("key_MEDIA_PLAY_PAUSE", WheelKey(code, 0, "?").encode()).commit()
            assertFalse(ForegroundWheelSettings.isMediaPlayPauseKey(host, WheelKey(code, 0, "?")))
        }
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun queuedCustomMediaRechecksCallBluetoothAndSettingsRevision() {
        enableForegroundMedia()
        val queued = mutableListOf<() -> Boolean>()
        doAnswer { invocation ->
            queued += invocation.getArgument<() -> Boolean>(2)
            true
        }.`when`(controller).sendMediaButton(anyInt(), any(), any())
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertEquals(1, queued.size)
        val allowed = queued.single()
        assertTrue(allowed())
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        assertFalse(allowed())
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        assertTrue(allowed())
        AirPlayPersistence.saveCarBluetoothAudio(host, true)
        assertFalse(allowed())
        AirPlayPersistence.saveCarBluetoothAudio(host, false)
        ForegroundWheelSettings.setMediaPlayPause(host, false)
        ForegroundWheelSettings.setMediaPlayPause(host, true)
        assertFalse(allowed())
    }

    @Test fun queuedCustomMediaCannotRunAfterTheHostPauses() {
        enableForegroundMedia()
        var allowed: (() -> Boolean)? = null
        doAnswer { invocation ->
            allowed = invocation.getArgument<() -> Boolean>(2)
            true
        }.`when`(controller).sendMediaButton(anyInt(), any(), any())
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertTrue(allowed?.invoke() == true)
        host.javaClass.getDeclaredMethod("onPause").apply { isAccessible = true }.invoke(host)
        assertFalse(allowed?.invoke() == true)
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
    }

    @Test fun customMediaCannotCaptureWhenControllerAndActivityPrimaryStreamsDiffer() {
        enableForegroundMedia()
        field("activeAirPlaySession", mock(AirPlaySession::class.java).also {
            `when`(it.mainScreenSessionToken()).thenReturn(Any())
        })
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun customMediaCannotReacquireANewTokenBetweenHostCaptureAndDispatch() {
        enableForegroundMedia()
        // The host captures the first token, then the controller's primary stream changes before
        // CarPlayMediaKeys checks the caller's ownership and reads its own command token.
        `when`(controller.activeAirPlaySessionToken()).thenReturn(sessionToken, Any())
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        verify(controller, never()).sendMediaButton(anyInt(), any(), any())
    }

    @Test fun queuedCustomMediaRequiresBothPrimaryStreamsToKeepTheCapturedToken() {
        enableForegroundMedia()
        val session = host.javaClass.getDeclaredField("activeAirPlaySession")
            .apply { isAccessible = true }.get(host) as AirPlaySession
        var allowed: (() -> Boolean)? = null
        doAnswer { invocation ->
            assertSame(sessionToken, invocation.getArgument<Any>(1))
            allowed = invocation.getArgument<() -> Boolean>(2)
            true
        }.`when`(controller).sendMediaButton(anyInt(), any(), any())
        assertTrue(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertTrue(allowed?.invoke() == true)
        val replacement = Any()
        `when`(controller.activeAirPlaySessionToken()).thenReturn(replacement)
        assertFalse(allowed?.invoke() == true)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(sessionToken)
        `when`(session.mainScreenSessionToken()).thenReturn(replacement)
        assertFalse(allowed?.invoke() == true)
        // Even when both owners agree again, the old key press cannot transfer to their new stream.
        `when`(controller.activeAirPlaySessionToken()).thenReturn(replacement)
        assertFalse(allowed?.invoke() == true)
    }

}
