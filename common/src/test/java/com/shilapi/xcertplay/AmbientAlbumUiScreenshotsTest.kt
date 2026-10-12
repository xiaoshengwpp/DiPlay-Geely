package com.shilapi.xcertplay

import android.content.res.Configuration
import android.os.Looper
import java.util.concurrent.CompletableFuture
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

/** The upstream screenshot subject is a BYD control dialog, unavailable in this product. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
class AmbientAlbumUiScreenshotsTest {
    @After fun resetQualifiers() = RuntimeEnvironment.setQualifiers("en")

    @Test fun ambientDialogIsUnavailableAtCompactAndFullWidths() {
        for ((width, fontScale) in listOf(600 to 1.3f, 1280 to 1.5f)) {
            assertNoDialogOrSideEffects(width, fontScale, false)
        }
    }

    @Test fun repeatedDialogRequestsCannotReactivateStaleAlbumLightingConsent() {
        for ((width, fontScale) in listOf(600 to 1.3f, 1280 to 1.5f)) {
            assertNoDialogOrSideEffects(width, fontScale, true)
        }
    }

    private fun assertNoDialogOrSideEffects(width: Int, fontScale: Float, staleEnabled: Boolean) {
        RuntimeEnvironment.setQualifiers("en-w${width}dp-h960dp-mdpi")
        val app = RuntimeEnvironment.getApplication()
        val original = Configuration(app.resources.configuration)
        app.resources.updateConfiguration(Configuration(original).apply { this.fontScale = fontScale }, app.resources.displayMetrics)
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        val prefs = activity.getSharedPreferences("ambient_music", 0)
        prefs.edit().clear().putBoolean("enabled", staleEnabled).putString("colorSource", "ALBUM").commit()
        val before = prefs.all.toMap()
        val previousDialog = ShadowAlertDialog.getLatestAlertDialog()
        var checks = 0
        activity.ambientSupportCheck = { checks++; CompletableFuture.completedFuture(true) }
        try {
            repeat(3) {
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "showAmbientConfiguration")
                shadowOf(Looper.getMainLooper()).idle()
                assertSame("No vendor dialog may be created", previousDialog, ShadowAlertDialog.getLatestAlertDialog())
                assertEquals("No saved setting may be changed", before, prefs.all)
                assertEquals("No hardware support probe may run", 0, checks)
            }
        } finally {
            activity.finish()
            app.resources.updateConfiguration(original, app.resources.displayMetrics)
        }
    }
}
