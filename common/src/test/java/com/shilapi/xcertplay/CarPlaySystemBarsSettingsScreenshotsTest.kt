package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One native-graphics sandbox for all screenshots, separate from the large host settings suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarPlaySystemBarsSettingsScreenshotsTest {
    @Test fun fullscreenSectionRemainsUsableAtLargeFontInCompactFullAndRtl() {
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        try {
            for ((name, widthDp, tag) in listOf(
                Triple("compact-ja", 320, "ja-JP"),
                Triple("full-en", 1000, "en-US"),
                Triple("compact-ar", 320, "ar-SA"),
            )) {
                val locale = Locale.forLanguageTag(tag)
                val config = Configuration(activity.resources.configuration).apply {
                    fontScale = 1.5f
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
                val section = CarPlayHostActivity::class.java.getDeclaredMethod("buildFullscreenSection")
                    .apply { isAccessible = true }.invoke(activity) as ViewGroup
                val labels = descendants(section).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                assertTrue(labels.any { it == activity.getString(R.string.temporary_system_bars) })
                // Overlay rows render the title; their description is exposed on the switch.
                val temporaryBarsSwitch = descendants(section).filterIsInstance<Switch>().single {
                    it.contentDescription == activity.getString(R.string.temporary_system_bars_description)
                }
                val enabledField = CarPlayHostActivity::class.java.getDeclaredField("temporarySystemBarsEnabled")
                    .apply { isAccessible = true }
                assertFalse(temporaryBarsSwitch.isChecked)
                assertFalse(enabledField.getBoolean(activity))
                temporaryBarsSwitch.isChecked = true
                assertTrue(enabledField.getBoolean(activity))
                temporaryBarsSwitch.isChecked = false
                assertFalse(enabledField.getBoolean(activity))
                val width = (widthDp * activity.resources.displayMetrics.density).toInt()
                section.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                section.layout(0, 0, width, section.measuredHeight)
                descendants(section).filterIsInstance<TextView>().filter { it.text.isNotEmpty() }.forEach { label ->
                    val layout = requireNotNull(label.layout)
                    assertTrue("Label must fit $name: ${label.text}", layout.getLineBottom(layout.lineCount - 1) <=
                        label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                }
                val bitmap = Bitmap.createBitmap(width, section.measuredHeight, Bitmap.Config.ARGB_8888)
                try {
                    section.draw(Canvas(bitmap))
                    val folder = File("build/reports/system-bars-ui").apply { mkdirs() }
                    FileOutputStream(File(folder, "$name.png")).use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            activity.finish()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
