package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import android.widget.LinearLayout
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Importing legacy vendor preferences cannot opt into the new foreground-only controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WheelMediaSettingsScreenshotsTest {
    @Test fun legacyFlagsDoNotEnableGenericControlsAtLargeFontsInThreeLayouts() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        val prefs = activity.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("media_play_pause", true)
            .putBoolean("siri_key", true).putBoolean("siri_double_press", true).commit()
        val foreground = activity.getSharedPreferences("diplay_foreground_wheel_keys", Context.MODE_PRIVATE)
        foreground.edit().clear().commit()
        val before = prefs.all.toMap()
        try {
            for ((widthDp, language) in listOf(320 to "ja-JP", 960 to "en-US", 320 to "ar-SA")) {
                val locale = Locale.forLanguageTag(language)
                val config = Configuration(activity.resources.configuration).apply {
                    fontScale = 1.5f
                    screenWidthDp = widthDp
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
                val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                DiPlayActivity::class.java.getDeclaredMethod("wheelKeysSettings", LinearLayout::class.java)
                    .apply { isAccessible = true }.invoke(activity, row)
                assertTrue("Generic controls: $widthDp dp, $language", row.childCount > 0)
                assertTrue(allViews(row).filterIsInstance<Switch>().all { !it.isChecked })
                val labels = allViews(row).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                for (id in listOf(R.string.wheel_keys_service_off, R.string.wheel_keys_enable_adb,
                    R.string.wheel_keys_open_settings, R.string.wheel_joystick)) {
                    assertFalse(labels.contains(activity.getString(id)))
                }
                assertFalse(ForegroundWheelSettings.mediaPlayPause(activity))
                assertFalse(ForegroundWheelSettings.siriDoublePress(activity))
                assertFalse(WheelZoomSettings.mediaPlayPause(activity))
                assertFalse(WheelZoomSettings.siriDoublePress(activity))
                assertFalse(WheelKeyService.wanted(activity))
                assertEquals(before, prefs.all)
            }
        } finally {
            activity.finish()
            prefs.edit().clear().commit()
            foreground.edit().clear().commit()
        }
    }
    private fun allViews(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(allViews(view.getChildAt(i)))
    }
}
