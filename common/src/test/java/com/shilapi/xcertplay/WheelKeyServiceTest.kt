package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import android.view.KeyEvent
import android.widget.Button
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.hud.BydCarPlayCall
import com.shilapi.xcertplay.hud.CarPlayCallState
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
class WheelKeyServiceTest {
    private lateinit var service: WheelKeyService
    private var route: Any? = "visible-phone-one-map"
    private var learned = 0
    private var backendReads = 0

    @Before fun setUp() {
        service = Robolectric.buildService(WheelKeyService::class.java).create().get()
        service.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE).edit().clear().commit()
        service.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit().clear().commit()
        service.mapRoute = { backendReads++; route }
        service.session = { backendReads++; phone }
        service.routeActive = { backendReads++; routeStarted }
        service.knob = { knobs.add(it) }
        service.requestSiri = { siriRequests++; true }
        WheelZoomSettings.setEnabled(service, true)
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.MODE, WheelKey(KeyEvent.KEYCODE_F1, 0, "?"))
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.ZOOM_IN, WheelKey(KeyEvent.KEYCODE_F2, 0, "?"))
        // Simulate settings left by another product: getters and runtime entry points must still refuse them.
        service.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putBoolean("joystick", true).putBoolean("siri_key", true).commit()
        connect()
        assertFalse(WheelKeyService.connected())
        assertTrue(service.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE).all.isEmpty())
        service.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
    }

    @After fun tearDown() {
        assertEquals("Geely must not query any wheel backend", 0, backendReads)
        assertTrue("Geely must not send knob input", knobs.isEmpty())
        assertEquals("Geely must not request Siri from accessibility", 0, siriRequests)
        assertEquals("Geely must not learn a key", 0, learned)
        service.onDestroy()
        BydOutputSettings.setCarPlayCallControls(service, false)
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
    }

    private val knobs = mutableListOf<com.shilapi.xcertplay.airplay.AirPlayKnobState>()
    private var phone: Any? = "phone-one"
    private var routeStarted = false

    private fun joystickSetUp() {
        service.session = { backendReads++; phone }
        service.knob = { knobs.add(it) }
        service.routeActive = { backendReads++; routeStarted }
        WheelZoomSettings.setJoystick(service, true)
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.JOYSTICK, WheelKey(KeyEvent.KEYCODE_F4, 0, "?"))
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.NEXT, WheelKey(KeyEvent.KEYCODE_F5, 0, "?"))
    }

    private fun press(code: Int): Pair<Boolean, Boolean> = key(code, true) to key(code, false)

    private fun connect() {
        service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
    }

    private fun key(code: Int, down: Boolean, repeat: Int = 0, time: Long = 0): Boolean {
        val prefs = service.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val handled = service.javaClass.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
            .apply { isAccessible = true }.invoke(service,
                KeyEvent(time, time, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, repeat, 0, -1, 0),
            ) as Boolean
        assertEquals("Key dispatch must preserve stored preferences", before, prefs.all)
        return handled
    }

    private fun attemptZoom() {
        assertFalse(key(KeyEvent.KEYCODE_F1, true))
        assertFalse(key(KeyEvent.KEYCODE_F1, false))
    }

    private fun learn(cancelled: () -> Unit = {}): Boolean =
        WheelKeyService.learn(WheelZoomSettings.Role.MODE, cancelled) { _, _ -> learned++ }

    @Test fun disabledSwitchCannotStartLearningOrMutateForgedPreferences() {
        val assign = Button(service).apply { text = "Press a key" }
        assertFalse(learn { assign.text = "Assign" })
        val prefs = service.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        WheelZoomSettings.setEnabled(service, false)
        assertEquals(before, prefs.all)
        assertEquals("Press a key", assign.text.toString())
        assertEquals(false to false, press(KeyEvent.KEYCODE_F3))
        assertEquals(KeyEvent.KEYCODE_F1, WheelZoomSettings.key(service, WheelZoomSettings.Role.MODE)?.code)
    }

    @Test fun cancelAndTimeoutDoNotLeaveTheNextKeyCaptured() {
        var cancelled = 0
        assertFalse(learn { cancelled++ })
        WheelKeyService.cancelLearning()
        assertEquals(false to false, press(KeyEvent.KEYCODE_F3))
        assertFalse(learn { cancelled++ })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WheelKeyService.LEARNING_TIMEOUT_MILLIS))
        assertEquals(0, cancelled)
        assertFalse(WheelKeyService.isLearning())
        assertEquals(false to false, press(KeyEvent.KEYCODE_F3))
    }

    @Test fun rejectedLearningNeverOwnsRepeatsOrReleaseAfterCancellationAndDisable() {
        assertFalse(learn())
        assertFalse(key(KeyEvent.KEYCODE_F3, true))
        WheelKeyService.cancelLearning()
        WheelZoomSettings.setEnabled(service, false)
        assertFalse(key(KeyEvent.KEYCODE_F3, true, repeat = 1))
        assertFalse(key(KeyEvent.KEYCODE_F3, false))
        assertEquals(false to false, press(KeyEvent.KEYCODE_F3))
        assertEquals(KeyEvent.KEYCODE_F1, WheelZoomSettings.key(service, WheelZoomSettings.Role.MODE)?.code)
    }

    @Test fun unbindingAndRebindingCannotEnableLearning() {
        var cancelled = 0
        assertFalse(learn { cancelled++ })
        service.onUnbind(Intent())
        assertFalse(WheelKeyService.connected())
        connect()
        assertFalse(WheelKeyService.connected())
        assertEquals(0, cancelled)
        assertEquals(false to false, press(KeyEvent.KEYCODE_F3))
    }

    @Test fun pollingAndReconnectionCannotActivateZoom() {
        attemptZoom()
        route = null
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        route = "visible-phone-two-map"
        assertFalse(key(KeyEvent.KEYCODE_F2, true))
        assertFalse(key(KeyEvent.KEYCODE_F2, false))
    }

    @Test fun disablingAndReenablingNeverCapturesAHeldRelease() {
        attemptZoom()
        assertFalse(key(KeyEvent.KEYCODE_F2, true))
        WheelZoomSettings.setEnabled(service, false)
        assertFalse(key(KeyEvent.KEYCODE_F2, false))
        WheelZoomSettings.setEnabled(service, true)
        assertFalse(WheelZoomSettings.enabled(service))
        assertEquals(false to false, press(KeyEvent.KEYCODE_F2))
    }

    @Test fun callChangesNeverEnableKeyCaptureOrLearning() {
        attemptZoom()
        val audio = service.getSystemService(AudioManager::class.java)
        for (mode in listOf(AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_NORMAL, AudioManager.MODE_RINGTONE)) {
            audio.mode = mode
            assertEquals(false to false, press(KeyEvent.KEYCODE_F2))
            assertFalse(learn())
            assertEquals(false to false, press(KeyEvent.KEYCODE_F3))
        }
    }

    @Test fun joystickModeAndMovementKeysNeverDriveTheKnob() {
        joystickSetUp()
        for (code in listOf(KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5,
            KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F4)) {
            assertEquals(false to false, press(code))
        }
        attemptZoom()
        assertTrue(knobs.isEmpty())
    }

    @Test fun idleAndRouteChangesCannotActivateJoystick() {
        joystickSetUp()
        press(KeyEvent.KEYCODE_F4)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WheelZoomSettings.JOYSTICK_IDLE_MILLIS))
        assertEquals(false to false, press(KeyEvent.KEYCODE_F5))
        press(KeyEvent.KEYCODE_F4)
        routeStarted = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_100))
        assertEquals(false to false, press(KeyEvent.KEYCODE_F5))
        assertTrue(knobs.isEmpty())
    }

    @Test fun joystickCannotStartWithoutAutoOffOrWhenThePhoneChanges() {
        joystickSetUp()
        WheelZoomSettings.setJoystickAutoOff(service, false)
        assertEquals(false to false, press(KeyEvent.KEYCODE_F4))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(WheelZoomSettings.JOYSTICK_IDLE_MILLIS * 2))
        assertEquals(false to false, press(KeyEvent.KEYCODE_F5))
        phone = "phone-two"
        assertEquals(false to false, press(KeyEvent.KEYCODE_F5))
        assertEquals(false to false, press(KeyEvent.KEYCODE_F4))
        WheelZoomSettings.setJoystick(service, false)
        assertEquals(false to false, press(KeyEvent.KEYCODE_F5))
        assertTrue(knobs.isEmpty())
    }

    @Test fun learningCannotTakeTheJoystickKey() {
        joystickSetUp()
        WheelZoomSettings.setEnabled(service, false)
        assertFalse(learn())
        assertEquals(false to false, press(KeyEvent.KEYCODE_F4))
        assertEquals(KeyEvent.KEYCODE_F1, WheelZoomSettings.key(service, WheelZoomSettings.Role.MODE)?.code)
        assertTrue(knobs.isEmpty())
    }

    @Test fun experimentalVoiceKeysRemainWithTheCarEvenAfterOptIn() {
        service.session = { backendReads++; "active-phone" }
        for (enabled in listOf(false, true, false)) {
            BydOutputSettings.setCarPlayCallControls(service, enabled)
            assertFalse(BydOutputSettings.carPlayCallControls(service))
            assertEquals(false to false, press(327))
            assertEquals(false to false, press(328))
        }
    }

    @Test
    fun theAllowedListKeepsOtherServicesAndRebindsAListedButStoppedService() {
        val ours = "com.shihab.diplay/com.shilapi.xcertplay.WheelKeyService"
        val car = "com.byd.airconditioning/.gesture.AcGestureService:com.android.systemui/.custom.StatusBarAccessibilityService"
        assertEquals(null to "$car:$ours", WheelKeyService.allowedServices("$car\n", ours))
        assertEquals(car to "$car:$ours", WheelKeyService.allowedServices("$ours:$car", ours))
        assertEquals("" to ours, WheelKeyService.allowedServices(ours, ours))
        for (empty in listOf("", "null", " \n")) assertEquals(null to ours, WheelKeyService.allowedServices(empty, ours))
        assertNull(WheelKeyService.allowedServices(null, ours))
    }

    private var siriRequests = 0

    private fun siriSetUp() {
        WheelZoomSettings.setEnabled(service, false)
        WheelZoomSettings.setSiriKey(service, true)
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        service.session = { backendReads++; phone }
        service.requestSiri = { siriRequests++; true }
    }

    @Test fun aHeldAssignedSiriKeyNeverOpensSiri() {
        siriSetUp()
        for (time in 0L..500L step 100) {
            assertFalse(key(KeyEvent.KEYCODE_F6, true, time = time))
            assertFalse(key(KeyEvent.KEYCODE_F6, false, time = time))
        }
        assertFalse(key(KeyEvent.KEYCODE_F6, true, time = 2_000))
        assertFalse(key(KeyEvent.KEYCODE_F6, false, time = 2_000))
        assertEquals(0, siriRequests)
    }

    @Test fun siriKeyKeepsTheCarsActionWithoutCarPlayInACallOrWhenOff() {
        siriSetUp()
        phone = null
        assertFalse(key(KeyEvent.KEYCODE_F6, true))
        assertFalse(key(KeyEvent.KEYCODE_F6, false))
        phone = "phone-one"
        service.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        assertFalse(key(KeyEvent.KEYCODE_F6, true))
        assertFalse(key(KeyEvent.KEYCODE_F6, false))
        service.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        WheelZoomSettings.setSiriKey(service, false)
        assertFalse(key(KeyEvent.KEYCODE_F6, true))
        assertFalse(key(KeyEvent.KEYCODE_F6, false))
        WheelZoomSettings.setEnabled(service, true) // the stored Siri key must not come back through the zoom roles
        assertFalse(key(KeyEvent.KEYCODE_F6, true))
        assertFalse(key(KeyEvent.KEYCODE_F6, false))
        assertEquals(0, siriRequests)
    }

    @Test fun theSiriKeyCannotBeLearnedEvenWithForgedSiriPreferences() {
        siriSetUp()
        assertFalse(WheelKeyService.learn(WheelZoomSettings.Role.SIRI) { _, _ -> learned++ })
        assertEquals(false to false, press(KeyEvent.KEYCODE_F7))
        assertEquals(KeyEvent.KEYCODE_F6, WheelZoomSettings.key(service, WheelZoomSettings.Role.SIRI)?.code)
    }

    @Test fun theSiriKeyDoesNotMatchWhenItsStoredSettingIsForged() {
        siriSetUp()
        val assigned = WheelKey(KeyEvent.KEYCODE_F6, 0, "?")
        assertFalse(WheelZoomSettings.isSiriKey(service, assigned))
        assertFalse(WheelZoomSettings.isSiriKey(service, WheelKey(KeyEvent.KEYCODE_F6, 1, "?")))
        WheelZoomSettings.setSiriKey(service, false)
        assertFalse(WheelZoomSettings.isSiriKey(service, assigned))
    }

    @Test fun siriLearningIsRejectedBeforeConflictCallbacks() {
        WheelZoomSettings.setSiriKey(service, true)
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        var taken: WheelZoomSettings.Role? = null
        assertFalse(WheelKeyService.learn(WheelZoomSettings.Role.SIRI, refused = { taken = it }) { _, _ -> learned++ })
        assertEquals(false to false, press(KeyEvent.KEYCODE_F1))
        assertNull(taken)
        assertEquals(KeyEvent.KEYCODE_F6, WheelZoomSettings.key(service, WheelZoomSettings.Role.SIRI)?.code)
    }

    @Test fun zoomLearningIsRejectedBeforeConflictCallbacks() {
        WheelZoomSettings.setSiriKey(service, true)
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        var taken: WheelZoomSettings.Role? = null
        assertFalse(WheelKeyService.learn(WheelZoomSettings.Role.MODE, refused = { taken = it }) { _, _ -> learned++ })
        assertEquals(false to false, press(KeyEvent.KEYCODE_F6))
        assertNull(taken)
        assertEquals(KeyEvent.KEYCODE_F1, WheelZoomSettings.key(service, WheelZoomSettings.Role.MODE)?.code)
    }

    @Test fun forgedSiriAndZoomSettingsNeverBecomeActiveConflictRoles() {
        val mode = WheelKey(KeyEvent.KEYCODE_F1, 0, "?")
        assertNull(WheelZoomSettings.conflict(service, WheelZoomSettings.Role.ZOOM_IN, mode))
        WheelZoomSettings.setSiriKey(service, true)
        assertNull(WheelZoomSettings.conflict(service, WheelZoomSettings.Role.SIRI, mode))
        WheelZoomSettings.setEnabled(service, false)
        assertNull(WheelZoomSettings.conflict(service, WheelZoomSettings.Role.SIRI, mode))
        assertFalse(WheelZoomSettings.anyEnabled(service))
    }

    @Test fun overlappingSiriAndZoomAssignmentsCannotCaptureTheKey() {
        siriSetUp()
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F1, 0, "?"))
        assertFalse(key(KeyEvent.KEYCODE_F1, true, time = 0))
        assertFalse(key(KeyEvent.KEYCODE_F1, false, time = 0))
        WheelZoomSettings.setEnabled(service, true)
        assertFalse(key(KeyEvent.KEYCODE_F1, true, time = 1_000))
        assertFalse(key(KeyEvent.KEYCODE_F1, false, time = 1_000))
        assertEquals(0, siriRequests)
    }

    @Test fun aHeldSiriCallKeyCannotEndTheCallThatArrivesBeforeItsRelease() {
        siriSetUp()
        val controller = mock(CarPlayController::class.java)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        CarPlayBackgroundSession.store(controller, mock(AndroidMediaSink::class.java), 1, 1,
            this, mock(CarPlaySessionDisplay::class.java)) { it() }
        BydOutputSettings.setCarPlayCallControls(service, true)
        WheelZoomSettings.assign(service, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_ENDCALL, 0, "?"))
        assertFalse(key(KeyEvent.KEYCODE_ENDCALL, true))
        assertEquals(0, siriRequests)
        BydCarPlayCall.onFrame(Iap2Messages.buildRaw(CarPlayCallState.CALL_STATE_UPDATE) {
            u8(2, 4); string(4, "new-call")
        })
        assertFalse(key(KeyEvent.KEYCODE_ENDCALL, false))
        verify(controller, never()).endCall()
        assertFalse(key(KeyEvent.KEYCODE_ENDCALL, true))
        assertFalse(key(KeyEvent.KEYCODE_ENDCALL, false))
        verify(controller, never()).endCall()
        assertEquals(0, siriRequests)
    }
    @Test
    @Config(sdk = [25, 29])
    fun forgedNavigationPreferencesCannotCaptureKeysOrChangeAnyStream() {
        WheelZoomSettings.setEnabled(service, false)
        service.session = { backendReads++; "phone" }
        var playback = com.shilapi.xcertplay.media.NavigationPlaybackSnapshot(true, AudioManager.STREAM_NOTIFICATION)
        service.navigationPlayback = { backendReads++; playback }
        val audio = service.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 2, 0)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 3, 0)
        val prefs = service.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE)
        // The disabled default must pass the initial wheel press through.
        fun event(action: Int, time: Long, repeat: Int = 0) = KeyEvent(time, time, action,
            KeyEvent.KEYCODE_VOLUME_UP, repeat)
        fun dispatch(event: KeyEvent): Boolean = service.javaClass
            .getDeclaredMethod("onKeyEvent", KeyEvent::class.java).apply { isAccessible = true }
            .invoke(service, event) as Boolean
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 1)))
        prefs.edit().putBoolean("enabled", true).commit()
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 2)))
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
        assertEquals(3, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        playback = com.shilapi.xcertplay.media.NavigationPlaybackSnapshot(false, null)
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 2, 1)))
        assertFalse(dispatch(event(KeyEvent.ACTION_UP, 2)))
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 3)))
        playback = com.shilapi.xcertplay.media.NavigationPlaybackSnapshot(true, AudioManager.STREAM_MUSIC)
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 4)))
        playback = com.shilapi.xcertplay.media.NavigationPlaybackSnapshot(true, null)
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 5)))
        playback = com.shilapi.xcertplay.media.NavigationPlaybackSnapshot(true, AudioManager.STREAM_NOTIFICATION, true)
        assertFalse(dispatch(event(KeyEvent.ACTION_DOWN, 6)))
        assertEquals(3, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

}
