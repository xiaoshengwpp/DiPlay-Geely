package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.setup.DiLinkGeneration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** The imported DiLink route controls must stay unavailable in the Geely product, even with forged state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
class DiLink3ClusterRouteSettingsTest {
    private lateinit var controller: ActivityController<DiPlayActivity>
    private lateinit var activity: DiPlayActivity
    private lateinit var page: LinearLayout

    @Before fun setUp() {
        controller = Robolectric.buildActivity(DiPlayActivity::class.java)
        activity = controller.get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        CarPlayBackgroundSession.clear()
        controller.setup().visible()
    }

    @After fun tearDown() {
        controller.pause().stop().destroy()
        CarPlayBackgroundSession.clear()
    }

    @Test fun forgedDiLink3RoutesStayDisabledAndOfferNoAuthorizationControls() {
        DiLinkGeneration.save(activity, DiLinkGeneration.DILINK_3)
        val prefs = activity.getSharedPreferences("xcertplay_airplay", 0)
        prefs.edit().putBoolean("cluster_map_enabled", true)
            .putBoolean("platform21_cluster_enabled", true)
            .putBoolean("adb_cluster_activity_enabled", true).commit()
        renderAdvanced()

        assertRoutesDisabledAndControlsAbsent()
        // The raw values really exist: getters and UI must reject them, not rely on clean preferences.
        assertTrue(prefs.getBoolean("platform21_cluster_enabled", false))
        assertTrue(prefs.getBoolean("adb_cluster_activity_enabled", false))
    }

    @Test fun diLink3RouteSettersAreNoOpsAndOfferNeitherSwitch() {
        DiLinkGeneration.save(activity, DiLinkGeneration.DILINK_3)
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        AirPlayPersistence.saveLegacyClusterEnabled(activity, true)
        AirPlayPersistence.saveAdbClusterEnabled(activity, true)
        renderAdvanced()

        assertRoutesDisabledAndControlsAbsent()
        val prefs = activity.getSharedPreferences("xcertplay_airplay", 0)
        assertFalse(prefs.contains("cluster_map_enabled"))
        assertFalse(prefs.contains("platform21_cluster_enabled"))
        assertFalse(prefs.contains("adb_cluster_activity_enabled"))
    }

    @Test fun selectingDiLink4CannotExposeEitherRouteEvenWithForgedPreferences() {
        DiLinkGeneration.save(activity, DiLinkGeneration.DILINK_4)
        for (forged in listOf(false, true)) {
            activity.getSharedPreferences("xcertplay_airplay", 0).edit()
                .putBoolean("cluster_map_enabled", forged)
                .putBoolean("platform21_cluster_enabled", forged)
                .putBoolean("adb_cluster_activity_enabled", forged).commit()
            renderAdvanced()
            assertRoutesDisabledAndControlsAbsent()
        }
    }

    private fun assertRoutesDisabledAndControlsAbsent() {
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(activity))
        assertFalse(AirPlayPersistence.loadDiLink4ClusterRouteEnabled(activity))
        assertFalse(AirPlayPersistence.loadLegacyClusterEnabled(activity))
        assertFalse(AirPlayPersistence.loadAdbClusterEnabled(activity))
        for (id in listOf(R.string.platform21_cluster_mode, R.string.adb_cluster_activity_mode,
                R.string.setup_cluster_route_conflict, R.string.setup_cluster_route_turn_off,
                R.string.adb_cluster_authorize, R.string.adb_cluster_open)) {
            assertFalse("Geely must not offer ${activity.getString(id)}", hasText(id))
        }
    }

    private fun renderAdvanced() {
        ReflectionHelpers.setField(activity, "page", "settings")
        ReflectionHelpers.setField(activity, "settingsCategory", SettingsCategory.ADVANCED)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        page = ReflectionHelpers.getField<ScrollView>(activity, "rootScroll").getChildAt(0) as LinearLayout
    }

    private fun hasText(id: Int) = views(page).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(id) }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
}
