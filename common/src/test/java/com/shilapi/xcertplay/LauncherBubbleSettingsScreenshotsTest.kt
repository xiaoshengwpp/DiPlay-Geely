package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import com.shilapi.xcertplay.host.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherBubbleSettingsScreenshotsTest {
    @Test fun bubbleHasOnlyAnAdvancedDestination() {
        assertEquals(listOf(SettingsCategory.ADVANCED), SettingsInformationArchitecture.sectionsByCategory
            .filterValues { SettingsSection.LAUNCHER_BUBBLE in it }.keys.toList())
    }

    @Test fun compactFullAndRtlCardsFitAtLargeFontScale() {
        for ((locale, widthDp) in listOf("en" to 320, "en" to 960, "ar" to 320, "it" to 320, "zh" to 320)) {
            RuntimeEnvironment.setQualifiers("$locale-w${widthDp}dp-h960dp-mdpi")
            val app = RuntimeEnvironment.getApplication()
            AppLocale.save(app, locale)
            val previous = Configuration(app.resources.configuration)
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(Configuration(previous).apply { fontScale = 1.5f }, app.resources.displayMetrics)
            val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get().apply {
                setTheme(android.R.style.Theme_Material_NoActionBar)
            }
            try {
                AirPlayPersistence.saveLauncherBubble(activity, false)
                var view = content(activity)
                assertFalse(descendants(view).filterIsInstance<Switch>().single().isChecked)
                // Render the expanded opt-in UI without opening Android permissions or an overlay.
                AirPlayPersistence.saveLauncherBubble(activity, true)
                view = content(activity)
                assertTrue(descendants(view).filterIsInstance<Switch>().single().isChecked)
                assertTrue(descendants(view).filterIsInstance<Button>().any {
                    it.text.startsWith(activity.getString(R.string.settings_launcher_bubble_size) + " · ")
                })
                val width = (widthDp * activity.resources.displayMetrics.density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                view.layout(0, 0, width, view.measuredHeight)
                assertTrue(view.height > 0)
                descendants(view).forEach { assertTrue("Control fits $locale/$widthDp", it.width <= width) }
                descendants(view).filterIsInstance<Button>().forEach { button ->
                    val layout = requireNotNull(button.layout)
                    assertTrue("Choice must not clip: ${button.text}", layout.getLineBottom(layout.lineCount - 1) <=
                        button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                }
                val bitmap = Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    val file = File("build/reports/launcher-bubble-ui/$locale-$widthDp-large-font.png")
                    file.parentFile.mkdirs()
                    file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
            } finally {
                AirPlayPersistence.saveLauncherBubble(activity, false)
                activity.finish()
                @Suppress("DEPRECATION")
                app.resources.updateConfiguration(previous, app.resources.displayMetrics)
                RuntimeEnvironment.setQualifiers("en")
            }
        }
    }

    private fun content(activity: DiPlayActivity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        ReflectionHelpers.setField(activity, "page", "settings")
        ReflectionHelpers.setField(activity, "settingsCategory", SettingsCategory.ADVANCED)
        ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.LAUNCHER_BUBBLE))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, this))
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
