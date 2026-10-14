package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeelyMusicBufferSettingsUiTest {
    @Test fun musicBuffersHaveOneAudioDestination() {
        assertEquals(listOf(SettingsCategory.AUDIO), SettingsInformationArchitecture.sectionsByCategory
            .filterValues { SettingsSection.AUDIO_ROUTING in it }.keys.toList())
    }

    @Test fun compactFullAndRtlLargeTextCardsFitTheirWindow() {
        for ((locale, width) in listOf("en" to 600, "en" to 1280, "ar" to 600, "ja" to 320, "ar" to 320)) {
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h960dp-mdpi")
            val app = RuntimeEnvironment.getApplication()
            val previous = Configuration(app.resources.configuration)
            app.resources.updateConfiguration(Configuration(previous).apply { fontScale = 1.5f }, app.resources.displayMetrics)
            val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
            activity.setTheme(android.R.style.Theme_Material_NoActionBar)
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            try {
                // Exercise the same normalization as the real Settings page.
                ReflectionHelpers.setField(activity, "page", "settings")
                ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.AUDIO_ROUTING))
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
                    ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
                content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                content.layout(0, 0, width, content.measuredHeight)
                val texts = descendants(content).filterIsInstance<TextView>().toList()
                assertTrue(texts.any { it.text.toString().contains(activity.getString(R.string.audio_routing)) })
                assertTrue(texts.any { it.text.toString().contains(activity.getString(R.string.settings_music_buffer_wireless)) })
                assertTrue(texts.any { it.text.toString().contains(activity.getString(R.string.settings_music_buffer_wired)) })
                assertTrue(texts.any { it.text.toString() == activity.getString(R.string.settings_navigation_attenuation) })
                val navigation = descendants(content).filterIsInstance<android.widget.SeekBar>().single()
                assertEquals(20, navigation.max)
                assertEquals(20, navigation.progress)
                assertTrue(content.height > 0)
                texts.filterIsInstance<Button>().forEach { button ->
                    val layout = requireNotNull(button.layout)
                    assertTrue("Choice label must not clip at large font scale: ${button.text}",
                        layout.getLineBottom(layout.lineCount - 1) <=
                            button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                }
                descendants(content).forEach { assertTrue("Control width must fit its window", it.width <= width) }
                val bitmap = Bitmap.createBitmap(width, content.height, Bitmap.Config.ARGB_8888)
                content.draw(Canvas(bitmap))
                val target = File("build/reports/album-artwork-ui/geely-music-buffers-$locale-$width-large-font.png")
                target.parentFile.mkdirs()
                target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
            } finally {
                activity.finish()
                app.resources.updateConfiguration(previous, app.resources.displayMetrics)
                RuntimeEnvironment.setQualifiers("en")
            }
        }
    }

    @Test fun cancellingAndReopeningABufferChoicePreservesBothTransportsUntilSave() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        val prefs = activity.getSharedPreferences("xcertplay_airplay", 0)
        prefs.edit().clear().commit()
        CarPlayBackgroundSession.clear()
        try {
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.AUDIO_ROUTING))
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
                ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
            val wired = descendants(content).filterIsInstance<Button>().single {
                it.text.startsWith(activity.getString(R.string.settings_music_buffer_wired) + " · ")
            }
            wired.performClick()
            var dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(null, 0, 0)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(activity, wireless = false))
            assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(activity, wireless = true))
            wired.performClick()
            dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(activity.getString(R.string.save), dialog.getButton(AlertDialog.BUTTON_POSITIVE).text)
            dialog.listView.performItemClick(null, 0, 0)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(50, AirPlayPersistence.loadMediaBufferMillis(activity, wireless = false))
            assertEquals(300, AirPlayPersistence.loadMediaBufferMillis(activity, wireless = true))
            assertFalse(CarPlayBackgroundSession.hasSession())
        } finally {
            prefs.edit().clear().commit()
            CarPlayBackgroundSession.clear()
            activity.finish()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
