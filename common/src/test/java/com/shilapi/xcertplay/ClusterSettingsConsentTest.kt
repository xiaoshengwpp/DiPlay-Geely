package com.shilapi.xcertplay

import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.LinearLayout
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], qualifiers = "en-w600dp-h700dp", manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ClusterSettingsConsentTest {
    private lateinit var screen: DiPlayActivity
    private lateinit var activity: ActivityController<DiPlayActivity>
    private lateinit var session: CarPlayController
    private val routeOwner = Any()
    private val events = mutableListOf<String>()

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
        AppLocale.save(app, AppLocale.ENGLISH)
        // These existing sessions have already answered the optional notification prompt.
        app.getSharedPreferences("diplay", 0).edit().putBoolean("notification_asked", true).commit()
        PendingReconnect.clear()
        CarPlayBackgroundSession.clear()
        ClusterActivityOutput.stopForSettings()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java)
        screen = activity.get()
        screen.setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup().visible()
        ReflectionHelpers.setField(screen, "setupError", null)
        ReflectionHelpers.setField(screen, "page", "settings")
        ReflectionHelpers.setField(screen, "settingsCategory", SettingsCategory.ADVANCED)
        AirPlayPersistence.saveWirelessEnabled(screen, false)
        session = mock(CarPlayController::class.java)
        CarPlayBackgroundSession.store(session, mock(AndroidMediaSink::class.java), 800, 480,
            routeOwner, CarPlaySessionDisplay(800, 480, Surface.ROTATION_0, false, false, 800, 480)) { done ->
            events += "stop-session"
            ClusterActivityOutput.stop(routeOwner)
            CarPlayBackgroundSession.clear(session)
            done()
        }
        CarPlayBackgroundSession.active = true
        ClusterActivityOutput.bind(routeOwner, 0) { events += "release-cluster-surface" }
        assertTrue("A disabled cluster bind must not publish a surface", events.isEmpty())
        render()
    }

    @After fun tearDown() {
        ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
        CarPlayBackgroundSession.clear()
        ClusterActivityOutput.stop(routeOwner)
        PendingReconnect.clear()
        activity.pause().stop().destroy()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
    }

    @Test fun absentAdbRoutingControlCannotInterruptTheCurrentSession() {
        assertClusterControlsAbsent()
        assertFalse(AirPlayPersistence.loadAdbClusterEnabled(screen))
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(screen))
        assertCurrentSessionUnchanged()
    }

    @Test fun forgedClusterMapPreferenceDoesNotExposeAControlOrQueueReconnect() {
        screen.getSharedPreferences("xcertplay_airplay", 0).edit()
            .putBoolean("cluster_map_enabled", true).commit()
        render()
        assertClusterControlsAbsent()
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(screen))
        assertCurrentSessionUnchanged()
    }

    @Test fun forgedAdbRoutingPreferenceDoesNotExposeAConsentOrReconnectPath() {
        screen.getSharedPreferences("xcertplay_airplay", 0).edit()
            .putBoolean("cluster_map_enabled", true)
            .putBoolean("adb_cluster_activity_enabled", true).commit()
        render()
        assertClusterControlsAbsent()
        assertFalse(AirPlayPersistence.loadAdbClusterEnabled(screen))
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(screen))
        assertCurrentSessionUnchanged()
    }

    @Test fun revisitingEverySettingsCategoryCannotReleaseTheSessionForDisabledClusterFeatures() {
        for (category in SettingsCategory.entries) {
            ReflectionHelpers.setField(screen, "settingsCategory", category)
            render()
            assertClusterControlsAbsent()
            assertCurrentSessionUnchanged()
        }
    }

    @Test fun directLegacySetupAndCalibrationEntryPointsCannotOpenDialogsOrReconnect() {
        val previousDialog = ShadowAlertDialog.getLatestAlertDialog()
        ReflectionHelpers.callInstanceMethod<Unit>(screen, "showClusterAccessSetup")
        ReflectionHelpers.callInstanceMethod<Unit>(screen, "openClusterSafeAreaEditor")
        val card = LinearLayout(screen)
        screen.javaClass.getDeclaredMethod("clusterSafeAreaControls", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(screen, card)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, card.childCount)
        assertSame(previousDialog, ShadowAlertDialog.getLatestAlertDialog())
        assertNull(ClusterActivityOutput.previewRect)
        assertFalse(ClusterActivityOutput.launchPending)
        assertCurrentSessionUnchanged()
    }

    private fun assertClusterControlsAbsent() {
        val forbidden = setOf(screen.getString(R.string.adb_cluster_activity_mode),
            screen.getString(R.string.carplay_map_on_instrument_cluster_experimental))
        assertTrue(views(screen.window.decorView).filterIsInstance<Switch>()
            .none { it.contentDescription?.toString() in forbidden })
    }

    private fun assertCurrentSessionUnchanged() {
        assertTrue(events.isEmpty())
        assertSame(session, CarPlayBackgroundSession.snapshot()?.controller)
        assertTrue(CarPlayBackgroundSession.active)
        assertFalse(PendingReconnect.isPending(session))
        assertNull(shadowOf(screen).nextStartedActivity)
    }

    private fun render() = ReflectionHelpers.callInstanceMethod<Unit>(screen, "render")

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
}
