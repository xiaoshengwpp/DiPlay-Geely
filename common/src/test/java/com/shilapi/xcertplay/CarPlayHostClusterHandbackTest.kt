package com.shilapi.xcertplay

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

// Geely must never acquire or hand back the unsupported BYD cluster, even after an upstream
// cluster timeout, a reconnect, or a stale preference imported from another build.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CarPlayHostClusterHandbackTest {
    private lateinit var activity: CarPlayHostActivity

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.getSharedPreferences("xcertplay_airplay", 0).edit().remove("cluster_map_enabled").commit()
    }

    @After fun tearDown() {
        activity.getSharedPreferences("xcertplay_airplay", 0).edit().remove("cluster_map_enabled").commit()
    }

    @Test fun savedEnableCannotAcquireOrHandBackTheCluster() {
        val prefs = activity.getSharedPreferences("xcertplay_airplay", 0)
        val before = prefs.all
        AirPlayPersistence.saveClusterMapEnabled(activity, true)
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(activity))
        assertEquals(before, prefs.all)
        clusterStream(true)
        clusterStream(false)
        idle(14)
        assertFalse(released())
        idle(1)
        assertFalse(released())
    }

    @Test fun aQuickReconnectNeverAcquiresTheDisabledCluster() {
        clusterStream(true)
        clusterStream(false)
        idle(5)
        clusterStream(true)
        idle(30)
        assertFalse(released())
    }

    @Test fun staleEnabledPreferenceCannotReleaseOrReacquireTheCluster() {
        val prefs = activity.getSharedPreferences("xcertplay_airplay", 0)
        prefs.edit().putBoolean("cluster_map_enabled", true).commit()
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(activity))
        clusterStream(true)
        clusterStream(false)
        idle(15)
        assertFalse(released())
        clusterStream(true)
        assertFalse(released())
    }

    @Test fun nothingIsReleasedWhenTheClusterMapIsOff() {
        AirPlayPersistence.saveClusterMapEnabled(activity, false)
        clusterStream(true)
        clusterStream(false)
        idle(15)
        assertFalse(released())
    }

    private fun clusterStream(active: Boolean) {
        activity.javaClass.getDeclaredMethod("onScreenStreamStateChanged",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 0, SCREEN_TYPE_ALT, active)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idle(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    private fun released(): Boolean = activity.javaClass.getDeclaredField("clusterReleasedWhileIdle")
        .apply { isAccessible = true }.getBoolean(activity)

    private companion object {
        /** The instrument-cluster screen stream (CarPlayHostActivity.SCREEN_TYPE_ALT). */
        const val SCREEN_TYPE_ALT = 111
    }
}
