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

    @Before fun setUp() {
        BydCarPlayCall.end()
        CarPlayBackgroundSession.clear()
        AppLocale.save(org.robolectric.RuntimeEnvironment.getApplication(), "en")
        host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        settings = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit().clear().commit()
        host.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        WheelZoomSettings.setSiriKey(host, true)
        WheelZoomSettings.assign(host, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "?"))
        host.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit()
            .putBoolean("siri_key", true).commit()
        host.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_NORMAL
        controller = mock(CarPlayController::class.java)
        `when`(controller.requestSiri()).thenReturn(true)
        field("controller", controller)
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
    }

    @After fun tearDown() {
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
        field("activeAirPlaySession", mock(AirPlaySession::class.java))
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
        assertEquals(0, controls.childCount)
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
        assertEquals(0, controls.childCount)
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
            assertEquals(0, controls.childCount)
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
}
