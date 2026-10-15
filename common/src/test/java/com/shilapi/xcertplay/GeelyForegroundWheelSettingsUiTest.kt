package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

/** Generic window keys are separate opt-ins; the OEM service and private Geely bridge stay separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeelyForegroundWheelSettingsUiTest {
    @Before fun clearPreferences() {
        val context = RuntimeEnvironment.getApplication()
        for (name in listOf("diplay_foreground_wheel_keys", "diplay_wheel_map_zoom", "diplay_geely_input")) {
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
        CarPlayBackgroundSession.clear()
    }

    @Test fun genericKeysHaveOneVehicleDestination() {
        assertEquals(listOf(SettingsCategory.VEHICLE), SettingsInformationArchitecture.sectionsByCategory
            .filterValues { SettingsSection.WHEEL_KEYS in it }.keys.toList())
    }

    @Test fun importedLegacyFlagsLeaveOnlyFreshForegroundOptInsVisibleAndOff() {
        val activity = activity()
        val legacy = activity.getSharedPreferences("diplay_wheel_map_zoom", 0)
        legacy.edit().putBoolean("siri_key", true).putBoolean("siri_double_press", true)
            .putBoolean("media_play_pause", true).putBoolean("siri_long_press_only", true)
            .putBoolean("joystick", true).putBoolean("enabled", true).commit()
        val before = legacy.all.toMap()
        try {
            val view = content(activity)
            assertEquals(2, descendants(view).filterIsInstance<Switch>().count())
            assertTrue(descendants(view).filterIsInstance<Switch>().none { it.isChecked })
            assertFalse(ForegroundWheelSettings.siriDoublePress(activity))
            assertFalse(ForegroundWheelSettings.mediaPlayPause(activity))
            assertNull(ForegroundWheelSettings.key(activity, WheelZoomSettings.Role.SIRI))
            assertNull(ForegroundWheelSettings.key(activity, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE))
            assertForegroundOnly(activity, view)
            assertEquals(before, legacy.all)
            assertFalse(WheelKeyService.wanted(activity))
        } finally { activity.finish() }
    }

    @Test fun togglesChangeOnlyTheNewForegroundPreferencesAndRemainReversible() {
        val activity = activity()
        GeelyInputSettings.setManualEnabled(activity, true)
        val privateBridge = activity.getSharedPreferences("diplay_geely_input", 0).all.toMap()
        try {
            fun setToggle(title: Int, enabled: Boolean) {
                descendants(content(activity)).filterIsInstance<Switch>()
                    .single { it.contentDescription == activity.getString(title) }.isChecked = enabled
            }
            setToggle(R.string.wheel_siri_double_press, true)
            assertTrue(ForegroundWheelSettings.siriDoublePress(activity))
            setToggle(R.string.wheel_media_play_pause, true)
            assertTrue(ForegroundWheelSettings.mediaPlayPause(activity))
            assertFalse(WheelZoomSettings.siriKey(activity))
            assertFalse(WheelZoomSettings.mediaPlayPause(activity))
            assertFalse(WheelKeyService.wanted(activity))
            assertEquals(privateBridge, activity.getSharedPreferences("diplay_geely_input", 0).all)
            assertTrue(activity.getSharedPreferences("diplay_wheel_map_zoom", 0).all.isEmpty())
            setToggle(R.string.wheel_siri_double_press, false)
            setToggle(R.string.wheel_media_play_pause, false)
            assertFalse(ForegroundWheelSettings.siriDoublePress(activity))
            assertFalse(ForegroundWheelSettings.mediaPlayPause(activity))
            assertFalse(CarPlayBackgroundSession.hasSession())
        } finally { activity.finish() }
    }

    @Test fun cancellingAndSavingTimingChoiceUseOnlyTheForegroundSetting() {
        val activity = activity()
        ForegroundWheelSettings.setSiriDoublePress(activity, true)
        try {
            val choice = descendants(content(activity)).filterIsInstance<Button>().single {
                it.text.startsWith(activity.getString(R.string.wheel_siri_double_press_interval) + " · ")
            }
            choice.performClick()
            var dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(null, 0, 0)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(WheelZoomSettings.DEFAULT_SIRI_DOUBLE_PRESS_INTERVAL_MILLIS,
                ForegroundWheelSettings.siriDoublePressIntervalMillis(activity))
            choice.performClick()
            dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(null, 0, 0)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(WheelZoomSettings.SIRI_DOUBLE_PRESS_INTERVALS.first(),
                ForegroundWheelSettings.siriDoublePressIntervalMillis(activity))
            assertTrue(activity.getSharedPreferences("diplay_wheel_map_zoom", 0).all.isEmpty())
        } finally { activity.finish() }
    }

    @Test fun compactFullRtlAndHighDensityCardsFitAtLargeFontScale() {
        for ((locale, widthDp, density) in listOf(
            Triple("en", 320, "mdpi"), Triple("en", 960, "mdpi"),
            Triple("ar", 320, "mdpi"), Triple("ja", 320, "xhdpi"),
        )) {
            RuntimeEnvironment.setQualifiers("$locale-w${widthDp}dp-h960dp-$density")
            val app = RuntimeEnvironment.getApplication()
            val previous = Configuration(app.resources.configuration)
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(Configuration(previous).apply { fontScale = 1.5f }, app.resources.displayMetrics)
            val activity = activity()
            try {
                ForegroundWheelSettings.setSiriDoublePress(activity, true)
                ForegroundWheelSettings.setMediaPlayPause(activity, true)
                ForegroundWheelSettings.assign(activity, WheelZoomSettings.Role.SIRI, WheelKey(KeyEvent.KEYCODE_F6, 0, "keyboard"))
                ForegroundWheelSettings.assign(activity, WheelZoomSettings.Role.MEDIA_PLAY_PAUSE, WheelKey(KeyEvent.KEYCODE_F7, 0, "keyboard"))
                val view = content(activity)
                val width = (widthDp * activity.resources.displayMetrics.density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                view.layout(0, 0, width, view.measuredHeight)
                assertForegroundOnly(activity, view)
                assertTrue(view.height > 0)
                descendants(view).forEach { assertTrue("Control fits $locale/$widthDp/$density", it.width <= width) }
                descendants(view).filterIsInstance<Button>().forEach { button ->
                    val layout = requireNotNull(button.layout)
                    assertTrue("Choice must not clip: ${button.text}", layout.getLineBottom(layout.lineCount - 1) <=
                        button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                }
                val bitmap = Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val file = File("build/reports/foreground-wheel-ui/$locale-$widthDp-$density-large-font.png")
                    file.parentFile.mkdirs()
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
            } finally {
                activity.finish()
                @Suppress("DEPRECATION")
                app.resources.updateConfiguration(previous, app.resources.displayMetrics)
                RuntimeEnvironment.setQualifiers("en")
            }
        }
    }

    private fun activity() = Robolectric.buildActivity(DiPlayActivity::class.java).get().apply {
        setTheme(android.R.style.Theme_Material_NoActionBar)
    }

    private fun content(activity: DiPlayActivity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        ReflectionHelpers.setField(activity, "page", "settings")
        ReflectionHelpers.setField(activity, "settingsCategory", SettingsCategory.VEHICLE)
        ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.WHEEL_KEYS))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, this))
    }

    private fun assertForegroundOnly(activity: DiPlayActivity, view: View) {
        val labels = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }.toList()
        for (id in listOf(R.string.settings_wheel_foreground_only, R.string.wheel_siri_double_press,
            R.string.wheel_media_play_pause)) assertTrue(activity.getString(id), activity.getString(id) in labels)
        for (id in listOf(R.string.wheel_keys_service_on, R.string.wheel_keys_service_off,
            R.string.wheel_keys_enable_adb, R.string.wheel_keys_open_settings, R.string.wheel_map_zoom,
            R.string.wheel_joystick, R.string.wheel_siri_long_press_only, R.string.wheel_siri_key,
            R.string.wheel_siri_key_description, R.string.wheel_siri_double_press_description,
            R.string.wheel_media_play_pause_description)) assertFalse(activity.getString(id), activity.getString(id) in labels)
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
