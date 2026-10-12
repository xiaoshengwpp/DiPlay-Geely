package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.os.Looper
import com.shilapi.xcertplay.airplay.SafeAreaRect
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, shadows = [ClusterCalibrationRouteTest.Router::class])
@LooperMode(LooperMode.Mode.PAUSED)
internal class ClusterCalibrationRouteTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val rect = SafeAreaRect(300, 100, 1500, 620)

    @Before fun reset() {
        ClusterActivityOutput.stopForSettings()
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("cluster_map_enabled", true)
            .putBoolean("adb_cluster_activity_enabled", true).commit()
        Router.launches = 0
    }

    @After fun cleanup() { ClusterActivityOutput.stopForSettings() }

    private fun preview(owner: Any): Activity {
        val host = Robolectric.buildActivity(Activity::class.java).get()
        ClusterActivityOutput.beginSafeAreaPreview(owner, rect, host)
        return host
    }

    private fun assertNoRoute() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertEquals("Calibration must not invoke ADB or alter the stock map", 0, Router.launches)
        assertNull(ClusterActivityOutput.previewRect)
        assertNull(ClusterActivityOutput.activity.get())
        assertFalse(ClusterActivityOutput.launchPending)
        assertFalse(ClusterActivityOutput.hasConfirmedRoute())
        assertFalse(ClusterActivityOutput.streamActive)
        assertEquals(-1, ClusterActivityOutput.mainTaskId)
    }

    @Test fun editorCannotLaunchBeforeCarPlayEvenWithForgedPreferences() {
        val owner = Any()
        preview(owner)
        assertNoRoute()
        ClusterActivityOutput.endSafeAreaPreview(owner)
        assertNoRoute()
    }

    @Test fun dismissingAnUnadmittedPreviewCannotFinishAnUnrelatedWindow() {
        val owner = Any()
        preview(owner)
        val window = Robolectric.buildActivity(AdbClusterActivity::class.java).get()
        assertFalse(ClusterActivityOutput.confirm(window, "forged-preview-ticket", 7))
        ClusterActivityOutput.endSafeAreaPreview(owner)
        assertFalse(window.isFinishing)
        assertNoRoute()
    }

    @Test fun playbackCannotAdoptOrStartARejectedCalibrationPreview() {
        val editor = Any()
        val host = preview(editor)
        val playback = Any()
        ClusterActivityOutput.bind(playback, 42) { fail("A disabled route must not publish a surface") }
        ClusterActivityOutput.setStreamActive(true)
        ClusterActivityOutput.ensure(host)
        ClusterActivityOutput.endSafeAreaPreview(editor)
        assertNoRoute()
        ClusterActivityOutput.stop(playback)
        assertNoRoute()
    }

    @Test fun repeatedEditorsAndStaleUpdatesCannotCreateAReplacementPreview() {
        val first = Any()
        preview(first)
        val second = Any()
        preview(second)
        ClusterActivityOutput.updateSafeAreaPreview(first, SafeAreaRect(0, 0, 1920, 720))
        ClusterActivityOutput.endSafeAreaPreview(first)
        assertNoRoute()
        ClusterActivityOutput.updateSafeAreaPreview(second, rect)
        ClusterActivityOutput.endSafeAreaPreview(second)
        assertNoRoute()
    }

    @Implements(AdbClusterRouter::class, isInAndroidSdk = false)
    class Router {
        @Implementation fun launch(context: Context, token: String, holdStockMap: Boolean,
            prepare: (Int) -> Boolean): AdbClusterRouter.Result {
            launches++
            return AdbClusterRouter.Result(false, "Unexpected Geely route")
        }
        companion object { var launches = 0 }
    }
}
