package com.shilapi.xcertplay

import android.graphics.SurfaceTexture
import android.view.Surface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterActivityOutputTest {
    @Before fun reset() { ClusterActivityOutput.stopForSettings() }
    @After fun cleanup() { ClusterActivityOutput.stopForSettings() }

    @Test fun forgedLaunchTicketCannotAdmitAnyDisplayAndStopInvalidatesIt() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit()
            .putBoolean("cluster_map_enabled", true)
            .putBoolean("adb_cluster_activity_enabled", true).commit()
        val owner = Any()
        val events = mutableListOf<Surface?>()
        ClusterActivityOutput.bind(owner, 4) { events += it }
        val token = "01234567-89ab-cdef-0123-456789abcdef"
        ClusterActivityOutput::class.java.getDeclaredField("launchToken")
            .apply { isAccessible = true }.set(ClusterActivityOutput, token)
        ClusterActivityOutput::class.java.getDeclaredField("expectedDisplay")
            .apply { isAccessible = true }.setInt(ClusterActivityOutput, 7)
        val activity = org.robolectric.Robolectric.buildActivity(AdbClusterActivity::class.java).get()
        assertFalse(ClusterActivityOutput.confirm(activity, "wrong", 7))
        assertFalse(ClusterActivityOutput.confirm(activity, token, 0))
        assertFalse(ClusterActivityOutput.confirm(activity, token, 8))
        assertFalse(ClusterActivityOutput.confirm(activity, token, 7))
        assertFalse(ClusterActivityOutput.acceptsToken(token))
        assertFalse(ClusterActivityOutput.hasConfirmedRoute())
        assertNull(ClusterActivityOutput.activity.get())
        assertTrue(events.isEmpty())
        assertEquals(-1, ClusterActivityOutput.mainTaskId)
        var restored = false
        ClusterActivityOutput.completeLaunch(token, current = true, accepted = false) { restored = true }
        assertTrue("A rejected stale launch still releases its old lease", restored)
        ClusterActivityOutput.stopForSettings()
        assertFalse(ClusterActivityOutput.acceptsToken(token))
        assertFalse(ClusterActivityOutput.confirm(activity, token, 7))
        assertFalse(ClusterActivityOutput.hasConfirmedRoute())
    }

    @Test fun theRouteSettlesWhenTheWindowConfirmsOrTheShellRejectsTheLaunch() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveAdbClusterEnabled(app, true)
        val owner = Any()
        ClusterActivityOutput.bind(owner, 4) { }
        val token = "01234567-89ab-cdef-0123-456789abcdef"
        fun admit(display: Int) {
            ClusterActivityOutput::class.java.getDeclaredField("launchToken")
                .apply { isAccessible = true }.set(ClusterActivityOutput, token)
            ClusterActivityOutput::class.java.getDeclaredField("expectedDisplay")
                .apply { isAccessible = true }.setInt(ClusterActivityOutput, display)
        }
        val activity = org.robolectric.Robolectric.buildActivity(AdbClusterActivity::class.java).get()
        try {
            assertFalse(ClusterActivityOutput.routeSettling())
            ClusterActivityOutput.launchPending = true
            assertTrue(ClusterActivityOutput.routeSettling())
            admit(display = 1)
            ClusterActivityOutput.completeLaunch(token, current = true, accepted = true) { }
            // The shell accepted the launch; the window has not reported its display yet.
            assertTrue(ClusterActivityOutput.routeSettling())
            assertTrue(ClusterActivityOutput.confirm(activity, token, 1))
            assertFalse(ClusterActivityOutput.routeSettling())

            ClusterActivityOutput.stopForSettings()
            ClusterActivityOutput.bind(owner, 4) { }
            admit(display = 1)
            ClusterActivityOutput.launchPending = true
            ClusterActivityOutput.completeLaunch(token, current = true, accepted = false) { }
            assertFalse(ClusterActivityOutput.routeSettling())
        } finally { ClusterActivityOutput.stop(owner) }
    }

    @Test fun rejectedShellLaunchInvalidatesAdmissionBeforeRestoringOemState() {
        val owner = Any()
        ClusterActivityOutput.bind(owner, 4) { }
        val token = "01234567-89ab-cdef-0123-456789abcdef"
        ClusterActivityOutput::class.java.getDeclaredField("launchToken")
            .apply { isAccessible = true }.set(ClusterActivityOutput, token)
        var restored = false
        ClusterActivityOutput.completeLaunch(token, current = true, accepted = false) {
            assertFalse(ClusterActivityOutput.acceptsToken(token))
            restored = true
        }
        assertTrue(restored)
        ClusterActivityOutput.stop(owner)
    }

    @Test fun previewAdmissionAndStaleEditorUpdatesCannotCreateCalibrationState() {
        val first = Any()
        val second = Any()
        val a = com.shilapi.xcertplay.airplay.SafeAreaRect(300, 100, 1500, 620)
        val b = com.shilapi.xcertplay.airplay.SafeAreaRect(200, 80, 1600, 640)
        ClusterActivityOutput.beginSafeAreaPreview(first, a)
        ClusterActivityOutput.updateSafeAreaPreview(first, b)
        assertNull(ClusterActivityOutput.previewRect)
        ClusterActivityOutput.beginSafeAreaPreview(second, a)
        ClusterActivityOutput.updateSafeAreaPreview(first, b)
        ClusterActivityOutput.endSafeAreaPreview(first)
        assertNull(ClusterActivityOutput.previewRect)
        ClusterActivityOutput.endSafeAreaPreview(second)
        assertNull(ClusterActivityOutput.previewRect)
        assertFalse(ClusterActivityOutput.launchPending)
        assertFalse(ClusterActivityOutput.hasConfirmedRoute())
    }

    @Test fun inertOverlayModelKeepsItsValuesUntilExplicitGlobalCleanup() {
        val host = Any()
        val newerHost = Any()
        val guidance = com.shilapi.xcertplay.hud.ClusterTurnGuidance(2, 0, 80, "Road",
            remainingMeters = 4200L, remainingSeconds = 630L)
        ClusterActivityOutput.bind(host, 4) { fail("Geely must not publish a cluster surface") }
        // The model setter remains available to standalone rendering helpers; it creates no route.
        ClusterActivityOutput.setTurnCard(guidance, 20, 50, 70, 60, true)
        ClusterActivityOutput.bind(newerHost, 5) { fail("Geely must not publish a cluster surface") }
        ClusterActivityOutput.stop(host)
        assertEquals(guidance, ClusterActivityOutput.guidance)
        assertEquals(20, ClusterActivityOutput.cardX)
        assertEquals(50, ClusterActivityOutput.cardY)
        assertEquals(70, ClusterActivityOutput.cardSize)
        assertEquals(60, ClusterActivityOutput.cardOpacity)
        assertTrue(ClusterActivityOutput.cardNight)
        ClusterActivityOutput.stop(newerHost)
        assertEquals(guidance, ClusterActivityOutput.guidance)
        assertFalse(ClusterActivityOutput.hasConfirmedRoute())
        assertFalse(ClusterActivityOutput.streamActive)
        ClusterActivityOutput.stopForSettings()
        assertNull(ClusterActivityOutput.guidance)
        assertFalse(ClusterActivityOutput.streamActive)
    }

    @Test fun repeatedSurfaceAdmissionAndStaleDetachCannotPublishOrOwnASurface() {
        val host = Any()
        val oldActivity = Any()
        val newActivity = Any()
        val firstTexture = SurfaceTexture(0)
        val secondTexture = SurfaceTexture(0)
        val first = Surface(firstTexture)
        val second = Surface(secondTexture)
        val events = mutableListOf<Surface?>()
        try {
            ClusterActivityOutput.bind(host, 4) { events.add(it) }
            ClusterActivityOutput.attach(oldActivity, first)
            ClusterActivityOutput.attach(newActivity, second)
            ClusterActivityOutput.detach(oldActivity, first)
            assertNull(ClusterActivityOutput.surface)
            assertTrue(events.isEmpty())
            val newHost = Any()
            ClusterActivityOutput.bind(newHost, 5) { events.add(it) }
            ClusterActivityOutput.stop(host)
            ClusterActivityOutput.detach(newActivity, second)
            ClusterActivityOutput.stop(newHost)
            assertNull(ClusterActivityOutput.surface)
            assertTrue(events.isEmpty())
            assertEquals(-1, ClusterActivityOutput.mainTaskId)
            assertTrue("Rejected output retains ownership of its own surface", first.isValid)
            assertTrue(second.isValid)
        } finally {
            first.release(); second.release()
            firstTexture.release(); secondTexture.release()
        }
    }

    @Test fun forgedLegacyPreferenceCannotConfirmPresentOrRetryAnOutput() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay", 0).edit()
            .putBoolean("cluster_map_enabled", true)
            .putBoolean("platform21_cluster_enabled", true).commit()
        val owner = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).get()
        ClusterActivityOutput.bind(owner, 4) { fail("Geely must not publish a cluster surface") }
        val token = "01234567-89ab-cdef-0123-456789abcdef"
        ClusterActivityOutput::class.java.getDeclaredField("launchToken")
            .apply { isAccessible = true }.set(ClusterActivityOutput, token)
        ClusterActivityOutput::class.java.getDeclaredField("expectedDisplay")
            .apply { isAccessible = true }.setInt(ClusterActivityOutput, 7)
        val window = org.robolectric.Robolectric.buildActivity(AdbClusterActivity::class.java).get()
        val stale = org.robolectric.Robolectric.buildActivity(AdbClusterActivity::class.java).get()
        val presented = ClusterActivityOutput::class.java.getDeclaredField("legacyPresented")
            .apply { isAccessible = true }
        val texture = SurfaceTexture(0)
        val output = Surface(texture)
        try {
            assertFalse(ClusterActivityOutput.confirm(window, token, 7))
            ClusterActivityOutput.attach(window, output)
            ClusterActivityOutput.presented(window)
            ClusterActivityOutput.setStreamActive(true)
            ClusterActivityOutput.presented(stale)
            ClusterActivityOutput.presented(window)
            assertFalse(presented.getBoolean(ClusterActivityOutput))
            assertFalse(ClusterActivityOutput.streamActive)
            assertNull(ClusterActivityOutput.surface)
            ClusterActivityOutput.ensure(owner)
            ClusterActivityOutput.retry(force = true)
            assertFalse(ClusterActivityOutput.launchPending)
            assertFalse(ClusterActivityOutput.confirm(window, token, 7))
            assertFalse(presented.getBoolean(ClusterActivityOutput))
            assertFalse(ClusterActivityOutput.acceptsToken(token))
            assertTrue(output.isValid)
        } finally {
            ClusterActivityOutput.stop(owner)
            output.release(); texture.release()
        }
    }
}
