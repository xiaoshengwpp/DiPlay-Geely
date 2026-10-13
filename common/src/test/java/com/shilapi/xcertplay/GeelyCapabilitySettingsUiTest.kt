package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeelyCapabilitySettingsUiTest {
    @Test fun experimentalControlsHaveOneAdvancedDestination() {
        assertEquals(listOf(SettingsCategory.ADVANCED), SettingsInformationArchitecture.sectionsByCategory
            .filterValues { SettingsSection.GEELY_CAPABILITIES in it }.keys.toList())
    }

    @Test fun compactFullAndRtlLargeTextCardsFitTheirWindow() {
        for ((locale, width) in listOf("en" to 600, "en" to 1280, "ar" to 600)) {
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h960dp-mdpi")
            val app = RuntimeEnvironment.getApplication()
            val previous = Configuration(app.resources.configuration)
            app.resources.updateConfiguration(Configuration(previous).apply { fontScale = 1.5f }, app.resources.displayMetrics)
            val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
            activity.setTheme(android.R.style.Theme_Material_NoActionBar)
            val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            try {
                ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.GEELY_CAPABILITIES))
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
                    ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
                content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                content.layout(0, 0, width, content.measuredHeight)
                val texts = descendants(content).filterIsInstance<TextView>().toList()
                assertTrue(texts.any { it.text.toString().contains(activity.getString(R.string.settings_geely_capabilities)) })
                assertTrue(texts.any { it.text.toString().contains(activity.getString(R.string.settings_geely_siri_source)) })
                assertTrue(texts.any { it.text.toString().contains(activity.getString(R.string.settings_geely_hud_display)) })
                assertTrue(content.height > 0)
                descendants(content).forEach { assertTrue("Control width must fit its window", it.width <= width) }
                val bitmap = Bitmap.createBitmap(width, content.height, Bitmap.Config.ARGB_8888)
                content.draw(Canvas(bitmap))
                val target = File("build/reports/album-artwork-ui/geely-capabilities-$locale-$width-large-font.png")
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

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
