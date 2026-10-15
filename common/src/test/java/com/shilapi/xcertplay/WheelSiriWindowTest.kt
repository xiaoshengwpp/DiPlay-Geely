package com.shilapi.xcertplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Handler
import android.view.Display
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE, qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
class WheelSiriWindowTest {
    private lateinit var host: CarPlayHostActivity
    private lateinit var settings: DiPlayActivity
    private lateinit var controller: CarPlayController
    private var sessionToken: Any = Any()

    @Before fun setUp() {
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        AppLocale.save(org.robolectric.RuntimeEnvironment.getApplication(), "en")
        host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        settings = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSharedPreferences("diplay_foreground_wheel_keys", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        WheelZoomSettings.setSiriKey(host, true)
        WheelZoomSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit()
            .putBoolean("siri_key", true).commit()
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        controller = mock(CarPlayController::class.java)
        `when`(controller.requestSiri()).thenReturn(true)
        `when`(controller.requestSiri(any(), any())).thenReturn(true)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(sessionToken)
        field("controller", controller)
        field("activeAirPlaySession", phoneSession())
    }

    @After fun tearDown() {
        host.getSharedPreferences("diplay_foreground_wheel_keys", Context.MODE_PRIVATE).edit().clear().commit()
        for (name in listOf("teardownExecutor", "airPlayCommandExecutor")) {
            (host.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(host) as ExecutorService).shutdownNow()
        }
        for ((activity, name) in listOf(host to "mainHandler", settings to "handler")) {
            (activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(activity) as Handler).removeCallbacksAndMessages(null)
        }
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun forgedAssignedKeyNeverCapturesItsReleaseAcrossCallSessionAndSettingChanges() {
        val prefs = host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, true)))
        WheelZoomSettings.setSiriKey(host, false)
        field("activeAirPlaySession", null)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, true, repeat = 1)))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, false)))
        assertEquals(before, prefs.all)
        verify(controller, never()).requestSiri()
    }

    @Test fun anAssignedStandardVoiceKeyDoesNotAlsoInvokeTheOldReleasePath() {
        val code = KeyEvent.KEYCODE_VOICE_ASSIST
        WheelZoomSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(code, 0, "?"))
        assertTrue(host.dispatchKeyEvent(key(code, true)))
        assertTrue(host.dispatchKeyEvent(key(code, false)))
        verify(controller, times(1)).requestSiri()
    }

    @Test fun unassignedExistingVoiceKeysKeepTheirReleaseBehavior() {
        WheelZoomSettings.setSiriKey(host, false)
        for (code in listOf(KeyEvent.KEYCODE_VOICE_ASSIST,
            CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_VOICE,
            CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG)) {
            assertTrue(host.dispatchKeyEvent(key(code, true)))
            assertTrue(host.dispatchKeyEvent(key(code, false)))
        }
        verify(controller, times(3)).requestSiri()
    }

    @Test fun bydLongPressPreferenceDefaultsOffAndCannotBeEnabledInTheGeelyProduct() {
        val prefs = host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        assertFalse(WheelZoomSettings.siriLongPressOnly(host))
        val before = prefs.all.toMap()
        WheelZoomSettings.setSiriLongPressOnly(host, true)
        assertFalse(WheelZoomSettings.siriLongPressOnly(host))
        assertEquals(before, prefs.all)
    }

    @Test fun forgedBydLongPressPreferenceCannotSwallowExistingGeelyWindowActions() {
        val prefs = host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("siri_long_press_only", true).commit()
        val before = prefs.all.toMap()
        assertFalse(WheelZoomSettings.siriLongPressOnly(host))
        for (code in listOf(KeyEvent.KEYCODE_VOICE_ASSIST,
            CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_VOICE,
            CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_VOICE_LONG)) {
            assertTrue(host.dispatchKeyEvent(key(code, true)))
            assertTrue(host.dispatchKeyEvent(key(code, false)))
        }
        verify(controller, times(3)).requestSiri()
        assertEquals(before, prefs.all)
    }

    @Test fun disabledBydSourceCannotSuppressTheGenericWindowVoiceKey() {
        val session = host.javaClass.getDeclaredField("activeAirPlaySession")
            .apply { isAccessible = true }.get(host)!!
        assertTrue(VoiceKeyPair.shared.sendCarPlayKey(android.os.SystemClock.uptimeMillis(), session) { true })
        for (code in listOf(KeyEvent.KEYCODE_VOICE_ASSIST, CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_VOICE)) {
            assertTrue(host.dispatchKeyEvent(key(code, true)))
            assertTrue(host.dispatchKeyEvent(key(code, false)))
        }
        verify(controller, times(2)).requestSiri()
    }

    @Test fun anUnownedReleaseCannotOpenSiriAfterAConnectionStarts() {
        field("activeAirPlaySession", null)
        val code = CarPlayMediaButton.KEYCODE_BYD_AUTO_MEDIA_VOICE
        assertFalse(host.dispatchKeyEvent(key(code, true)))
        field("activeAirPlaySession", phoneSession())
        assertFalse(host.dispatchKeyEvent(key(code, false)))
        verify(controller, never()).requestSiri()
    }

    @Test fun assigningALegacyVoiceKeyBetweenDownAndUpKeepsTheOriginalAction() {
        val code = KeyEvent.KEYCODE_VOICE_ASSIST
        WheelZoomSettings.setSiriKey(host, false)
        assertTrue(host.dispatchKeyEvent(key(code, true)))
        WheelZoomSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(code, 0, "?"))
        WheelZoomSettings.setSiriKey(host, true)
        assertTrue(host.dispatchKeyEvent(key(code, false)))
        verify(controller, times(1)).requestSiri()
    }

    @Test fun forgedVendorCallStateCannotEnableAssignedWheelSiri() {
        val audio = host.getSystemService(AudioManager::class.java)
        val prefs = host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        BydCarPlayCall.onFrame(Iap2Messages.buildRaw(CarPlayCallState.CALL_STATE_UPDATE) {
            u8(2, 4); string(4, "active-call")
        })
        assertEquals(AudioManager.MODE_NORMAL, audio.mode)
        assertFalse(inCall(host))
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, true)))
        BydCarPlayCall.end()
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, false)))
        verify(controller, never()).requestSiri()
        assertEquals(before, prefs.all)

        // Native Android call detection remains independent of disabled vendor frames.
        for (mode in listOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)) {
            audio.mode = mode
            assertTrue(inCall(host))
        }
        audio.mode = AudioManager.MODE_NORMAL
        assertFalse(inCall(host))
    }

    @Test fun windowLearningCannotCaptureKeysOrChangeForgedSiriPreferences() {
        var learned = 0
        val prefs = settings.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        learn { learned++ }
        assertFalse(settings.dispatchKeyEvent(key(KeyEvent.KEYCODE_F7, true)))
        assertFalse(settings.dispatchKeyEvent(key(KeyEvent.KEYCODE_F7, true, repeat = 1)))
        assertFalse(settings.dispatchKeyEvent(key(KeyEvent.KEYCODE_F7, false)))
        assertEquals(0, learned)
        assertEquals(before, prefs.all)
        assertEquals(KeyEvent.KEYCODE_F6, WheelZoomSettings.key(settings, WheelZoomSettings.Role.SIRI)?.code)
    }

    @Test fun callCancelsWindowLearningWithoutSavingTheCallKey() {
        var learned = 0
        learn { learned++ }
        settings.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        settings.dispatchKeyEvent(key(KeyEvent.KEYCODE_F7, true))
        settings.dispatchKeyEvent(key(KeyEvent.KEYCODE_F7, false))
        assertEquals(0, learned)
        assertEquals(KeyEvent.KEYCODE_F6, WheelZoomSettings.key(settings, WheelZoomSettings.Role.SIRI)?.code)
    }

    @Test fun forgedBydPackageAndFeaturePreferencesCannotExposeWheelServiceSetup() {
        settings.setTheme(android.R.style.Theme_Material_NoActionBar)
        val prefs = settings.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", true).putBoolean("joystick", true).putBoolean("siri_key", true).commit()
        org.robolectric.Shadows.shadowOf(settings.packageManager)
            .installPackage(android.content.pm.PackageInfo().apply { packageName = "com.byd.amapservice" })
        val before = prefs.all.toMap()
        val controls = wheelControls()
        assertNoWheelServiceControls(controls)
        assertGenericControlsOff(controls)
        assertEquals(before, prefs.all)
    }

    @Test fun savedJoystickAssignmentsRemainStoredWithoutExposingGeelyWheelControls() {
        settings.setTheme(android.R.style.Theme_Material_NoActionBar)
        val prefs = settings.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("joystick", true).commit()
        val joystickKey = WheelKey(KeyEvent.KEYCODE_F8, 0, "external-wheel")
        WheelZoomSettings.assign(settings, WheelZoomSettings.Role.JOYSTICK, joystickKey)
        AirPlayPersistence.saveClusterMapEnabled(settings, false)
        val before = prefs.all.toMap()
        assertFalse(CarHotspotSetup.isBydHeadUnit(settings))
        val controls = wheelControls()
        assertNoWheelServiceControls(controls)
        assertGenericControlsOff(controls)
        assertFalse(WheelZoomSettings.joystick(settings))
        assertEquals(joystickKey, WheelZoomSettings.key(settings, WheelZoomSettings.Role.JOYSTICK))
        assertEquals(before, prefs.all)
    }

    @Test fun aPreviouslySupportedClusterDisplayCannotExposeGeelyWheelControls() {
        settings.setTheme(android.R.style.Theme_Material_NoActionBar)
        AirPlayPersistence.saveClusterMapEnabled(settings, true)
        val prefs = settings.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        val displayId = ShadowDisplayManager.addDisplay("w1920dp-h720dp-mdpi", 5)
        shadowOf(settings.getSystemService(DisplayManager::class.java).getDisplay(displayId)).apply {
            setName(DiLink4ClusterDisplay.NAME)
            setFlags(Display.FLAG_PRESENTATION)
        }
        try {
            assertFalse(CarHotspotSetup.isBydHeadUnit(settings))
            assertNull(ClusterMapPresentation.findDisplay(settings))
            val controls = wheelControls()
            assertNoWheelServiceControls(controls)
            assertGenericControlsOff(controls)
            assertFalse(WheelZoomSettings.enabled(settings))
            assertEquals(before, prefs.all)
        } finally {
            ShadowDisplayManager.removeDisplay(displayId)
        }
    }

    private fun wheelControls() = LinearLayout(settings).also { controls ->
        settings.javaClass.getDeclaredMethod("wheelKeysSettings", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(settings, controls)
    }

    private fun assertNoWheelServiceControls(controls: View) {
        val labels = views(controls).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        for (id in listOf(R.string.wheel_keys_service_off, R.string.wheel_keys_enable_adb,
            R.string.wheel_keys_open_settings)) {
            assertEquals(settings.getString(id), 0, labels.count { it == settings.getString(id) })
        }
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }

    private fun learn(done: (WheelKey) -> Unit) {
        settings.javaClass.getDeclaredMethod("learnInWindow", WheelZoomSettings.Role::class.java,
            kotlin.jvm.functions.Function0::class.java, kotlin.jvm.functions.Function1::class.java,
            kotlin.jvm.functions.Function1::class.java).apply { isAccessible = true }
            .invoke(settings, WheelZoomSettings.Role.SIRI, {}, { _: WheelZoomSettings.Role -> }, done)
    }

    private fun field(name: String, value: Any?) = host.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(host, value)

    private fun key(code: Int, down: Boolean, repeat: Int = 0) =
        KeyEvent(1_000, 1_000, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
            code, repeat, 0, -1, 0)
    @Test fun importedDoublePressOptionsCannotActivateEitherInputRoute() {
        val prefs = host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("siri_double_press", true).putInt("siri_trigger_delay", 0).commit()
        val before = prefs.all.toMap()
        val service = Robolectric.buildService(WheelKeyService::class.java).create().get()
        var backendReads = 0
        service.session = { backendReads++; Any() }
        service.requestSiri = { fail("disabled service must not request Siri"); false }
        service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        try {
            for (time in listOf(1_000L, 1_450L)) {
                for (down in listOf(true, false)) {
                    val event = key(KeyEvent.KEYCODE_F6, down, time = time)
                    assertFalse(service.javaClass.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
                        .apply { isAccessible = true }.invoke(service, event) as Boolean)
                    assertFalse(WheelKeyService.observedDoubleSiri(event))
                    assertFalse(host.dispatchKeyEvent(event))
                }
            }
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
            assertEquals(0, backendReads)
            assertFalse(WheelKeyService.connected())
            assertFalse(WheelZoomSettings.siriDoublePress(host))
            verify(controller, never()).requestSiri()
            verify(controller, never()).requestSiri(any(), any())
            assertEquals(before, prefs.all)
        } finally {
            service.onDestroy()
        }
    }

    @Test fun importedMediaSettingCannotStartWindowLearningOrCaptureReservedKeys() {
        val prefs = settings.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("media_play_pause", true).commit()
        val before = prefs.all.toMap()
        var learned = 0
        val accepted = settings.javaClass.getDeclaredMethod("learnInWindow", WheelZoomSettings.Role::class.java,
            kotlin.jvm.functions.Function0::class.java, kotlin.jvm.functions.Function1::class.java,
            kotlin.jvm.functions.Function1::class.java).apply { isAccessible = true }
            .invoke(settings, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE, {},
                { _: WheelZoomSettings.Role -> fail("disabled learning must not check conflicts") },
                { _: WheelKey -> learned++ }) as Boolean
        assertFalse(accepted)
        for (code in listOf(KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_DPAD_CENTER)) {
            assertFalse(settings.dispatchKeyEvent(key(code, true)))
            assertFalse(settings.dispatchKeyEvent(key(code, true, repeat = 1)))
            assertFalse(settings.dispatchKeyEvent(key(code, false)))
        }
        assertEquals(0, learned)
        assertEquals(before, prefs.all)
        assertGenericControlsOff(wheelControls())
    }

    @Test fun forgedPendingMediaLearningRemainsInertBeforeReservedKeyHandling() {
        val prefs = settings.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("media_play_pause", true).commit()
        val before = prefs.all.toMap()
        val cancelled: () -> Unit = { fail("disabled dispatch must not touch learning state") }
        val refused: (WheelZoomSettings.Role) -> Unit = { fail("disabled dispatch must not check conflicts") }
        val learned: (WheelKey) -> Unit = { fail("disabled dispatch must not save an assignment") }
        val pendingType = settings.javaClass.declaredClasses.single { it.simpleName == "WindowKeyLearning" }
        val pending = pendingType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(WheelZoomSettings.Role.MEDIA_PLAY_PAUSE, cancelled, refused, learned)
        val learningField = settings.javaClass.getDeclaredField("windowLearning").apply { isAccessible = true }
        learningField.set(settings, pending)
        for (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_F9)) {
            assertFalse(settings.dispatchKeyEvent(key(code, true)))
            assertFalse(settings.dispatchKeyEvent(key(code, true, repeat = 1)))
            assertFalse(settings.dispatchKeyEvent(key(code, false)))
        }
        assertSame(pending, learningField.get(settings))
        assertEquals(before, prefs.all)
    }

    private fun assertGenericControlsOff(controls: View) {
        assertNoWheelServiceControls(controls)
        assertFalse(ForegroundWheelSettings.mediaPlayPause(settings))
        assertFalse(ForegroundWheelSettings.siriDoublePress(settings))
        assertTrue(views(controls).filterIsInstance<Switch>().all { !it.isChecked })
    }

    private fun enableForegroundSiri(code: Int = KeyEvent.KEYCODE_F6) {
        ForegroundWheelSettings.setSiriDoublePress(host, true)
        ForegroundWheelSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(code, 0, "?"))
    }

    private fun observedPress(time: Long, code: Int = KeyEvent.KEYCODE_F6, device: Int = -1) {
        assertFalse(host.dispatchKeyEvent(key(code, true, time = time, device = device)))
        assertFalse(host.dispatchKeyEvent(key(code, false, time = time + 100, device = device)))
    }

    private fun idleSiriDelay() = shadowOf(android.os.Looper.getMainLooper())
        .idleFor(java.time.Duration.ofMillis(300))

    @Test fun foregroundDoubleSiriObservesBothPressesAndWaitsForTheSecondRelease() {
        enableForegroundSiri()
        observedPress(1_000)
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, true, time = 1_450)))
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
        assertFalse(host.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, false, time = 1_550)))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(299))
        verify(controller, never()).requestSiri(any(), any())
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1))
        verify(controller, times(1)).requestSiri(same(sessionToken), any())
        verify(controller, never()).requestSiri()
        assertFalse(WheelKeyService.connected())
        assertFalse(WheelKeyService.wanted(host))
    }

    @Test fun foregroundDoubleVoiceKeyDoesNotAlsoRunTheLegacyReleasePath() {
        enableForegroundSiri(KeyEvent.KEYCODE_VOICE_ASSIST)
        observedPress(1_000, KeyEvent.KEYCODE_VOICE_ASSIST)
        observedPress(1_450, KeyEvent.KEYCODE_VOICE_ASSIST)
        idleSiriDelay()
        verify(controller, times(1)).requestSiri(same(sessionToken), any())
        verify(controller, never()).requestSiri()
    }

    @Test fun aFirstPressCannotTransferToAnotherAirPlaySession() {
        enableForegroundSiri()
        observedPress(1_000)
        field("activeAirPlaySession", phoneSession())
        observedPress(1_450)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
        observedPress(1_900)
        idleSiriDelay()
        verify(controller, times(1)).requestSiri(same(sessionToken), any())
    }

    @Test fun aFirstPressCannotTransferToARenegotiatedPrimaryStream() {
        enableForegroundSiri()
        observedPress(1_000)
        sessionToken = Any()
        `when`(controller.activeAirPlaySessionToken()).thenReturn(sessionToken)
        val current = host.javaClass.getDeclaredField("activeAirPlaySession").apply { isAccessible = true }.get(host) as AirPlaySession
        `when`(current.mainScreenSessionToken()).thenReturn(sessionToken)
        observedPress(1_450)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
        observedPress(1_900)
        idleSiriDelay()
        verify(controller, times(1)).requestSiri(same(sessionToken), any())
    }

    @Test fun firstPressesOnDifferentPhysicalDevicesCannotFormAPair() {
        enableForegroundSiri()
        observedPress(1_000, device = 91)
        observedPress(1_450, device = 92)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun aChangedAndRestoredAssignmentCannotReviveTheFirstPress() {
        enableForegroundSiri()
        observedPress(1_000)
        ForegroundWheelSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F7, 0, "?"))
        ForegroundWheelSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        observedPress(1_450)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun aSecondPressDuringACallCannotOpenSiriAfterTheCallEnds() {
        enableForegroundSiri()
        observedPress(1_000)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        observedPress(1_450)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun aConfirmedPairIsCancelledWhenACallStartsBeforeTheDelay() {
        enableForegroundSiri()
        observedPress(1_000)
        observedPress(1_450)
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun pausingTheHostCancelsAConfirmedPair() {
        enableForegroundSiri()
        observedPress(1_000)
        observedPress(1_450)
        host.javaClass.getDeclaredMethod("onPause").apply { isAccessible = true }.invoke(host)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun destroyingTheHostCancelsAConfirmedPair() {
        enableForegroundSiri()
        observedPress(1_000)
        observedPress(1_450)
        host.javaClass.getDeclaredMethod("onDestroy").apply { isAccessible = true }.invoke(host)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun queuedSiriRechecksTokenCallSettingsAndLifecycleOwnership() {
        enableForegroundSiri()
        val queued = mutableListOf<() -> Boolean>()
        doAnswer { invocation ->
            queued += invocation.getArgument<() -> Boolean>(1)
            true
        }.`when`(controller).requestSiri(any(), any())
        observedPress(1_000)
        observedPress(1_450)
        idleSiriDelay()
        assertEquals(1, queued.size)
        val allowed = queued.single()
        assertTrue(allowed())
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        assertFalse(allowed())
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        assertTrue(allowed())
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        assertFalse(allowed())
        `when`(controller.activeAirPlaySessionToken()).thenReturn(sessionToken)
        ForegroundWheelSettings.setSiriDoublePress(host, false)
        ForegroundWheelSettings.setSiriDoublePress(host, true)
        assertFalse(allowed())
        host.javaClass.getDeclaredMethod("onPause").apply { isAccessible = true }.invoke(host)
        assertFalse(allowed())
    }

    private fun learnForeground(activity: DiPlayActivity, role: WheelZoomSettings.Role,
        cancelled: () -> Unit = {}, refused: (WheelZoomSettings.Role) -> Unit = {},
        done: (WheelKey) -> Unit): Boolean =
        DiPlayActivity::class.java.getDeclaredMethod("learnForegroundKeyInWindow", WheelZoomSettings.Role::class.java,
            kotlin.jvm.functions.Function0::class.java, kotlin.jvm.functions.Function1::class.java,
            kotlin.jvm.functions.Function1::class.java).apply { isAccessible = true }
            .invoke(activity, role, cancelled, refused, done) as Boolean

    @Test fun foregroundLearningRequiresOptInAndFocusThenCapturesOnlyOnePress() {
        val focused = spy(settings)
        doReturn(true).`when`(focused).hasWindowFocus()
        var learned: WheelKey? = null
        assertFalse(learnForeground(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE) { learned = it })
        ForegroundWheelSettings.setMediaPlayPause(focused, true)
        doReturn(false).`when`(focused).hasWindowFocus()
        assertFalse(learnForeground(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE) { learned = it })
        doReturn(true).`when`(focused).hasWindowFocus()
        assertTrue(learnForeground(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE) { learned = it })
        assertTrue(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertTrue(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true, repeat = 1)))
        assertTrue(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        assertEquals(WheelKey(KeyEvent.KEYCODE_F9, 0, "?"), learned)
        assertEquals(learned, ForegroundWheelSettings.key(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE))
        assertNull(WheelZoomSettings.key(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE))
        assertFalse(WheelKeyService.connected())
    }

    @Test fun foregroundLearningPassesReservedKeysAndRejectsAnActiveAssignmentConflict() {
        val focused = spy(settings)
        doReturn(true).`when`(focused).hasWindowFocus()
        ForegroundWheelSettings.setMediaPlayPause(focused, true)
        ForegroundWheelSettings.setSiriDoublePress(focused, true)
        ForegroundWheelSettings.assign(focused, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        var learned = 0
        var refused: WheelZoomSettings.Role? = null
        assertTrue(learnForeground(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE,
            refused = { refused = it }) { learned++ })
        assertFalse(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_DPAD_CENTER, true)))
        assertFalse(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_DPAD_CENTER, false)))
        assertTrue(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, true)))
        assertTrue(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F6, false)))
        assertEquals(WheelZoomSettings.Role.SIRI, refused)
        assertEquals(0, learned)
        assertNull(ForegroundWheelSettings.key(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE))
    }

    @Test fun foregroundLearningCancelsOnLostFocusBeforeAnyAssignment() {
        val focused = spy(settings)
        doReturn(true).`when`(focused).hasWindowFocus()
        ForegroundWheelSettings.setMediaPlayPause(focused, true)
        var cancelled = 0
        var learned = 0
        assertTrue(learnForeground(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE,
            cancelled = { cancelled++ }) { learned++ })
        doReturn(false).`when`(focused).hasWindowFocus()
        focused.onWindowFocusChanged(false)
        assertFalse(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, true)))
        assertFalse(focused.dispatchKeyEvent(key(KeyEvent.KEYCODE_F9, false)))
        assertEquals(1, cancelled)
        assertEquals(0, learned)
        assertNull(ForegroundWheelSettings.key(focused, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE))
    }

    private fun phoneSession(token: Any = sessionToken): AirPlaySession = mock(AirPlaySession::class.java).also {
        `when`(it.mainScreenSessionToken()).thenReturn(token)
    }

    @Test fun controllerAndActivityPrimaryStreamsMustAgreeBeforeAFirstPress() {
        enableForegroundSiri()
        field("activeAirPlaySession", phoneSession(Any()))
        observedPress(1_000)
        observedPress(1_450)
        idleSiriDelay()
        verify(controller, never()).requestSiri(any(), any())
    }

    @Test fun losingWindowFocusCancelsADoublePressAndItsQueuedCommand() {
        enableForegroundSiri()
        var allowed: (() -> Boolean)? = null
        doAnswer { invocation ->
            allowed = invocation.getArgument<() -> Boolean>(1)
            true
        }.`when`(controller).requestSiri(any(), any())
        observedPress(1_000)
        observedPress(1_450)
        idleSiriDelay()
        assertTrue(allowed?.invoke() == true)
        host.onWindowFocusChanged(false)
        assertFalse(allowed?.invoke() == true)
    }

}
