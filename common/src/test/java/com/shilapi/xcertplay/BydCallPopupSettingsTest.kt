package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en", manifest = Config.NONE)
class BydCallPopupSettingsTest {
    @Test fun popupControlIsAbsentWithoutChangingEitherSavedChoice() {
        for (saved in listOf(false, true)) assertPopupUnavailable(saved)
    }

    @Test @Config(qualifiers = "en-w320dp-h800dp-xhdpi")
    fun compactLargeFontMediaSettingsDoNotExposeDisabledPopup() = assertPopupUnavailable(true)

    @Test @Config(qualifiers = "ar-ldrtl-w600dp-h800dp-xhdpi")
    fun fullWidthArabicMediaSettingsDoNotExposeDisabledPopup() = assertPopupUnavailable(true)

    private fun assertPopupUnavailable(saved: Boolean) {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        val original = android.content.res.Configuration(activity.resources.configuration)
        activity.resources.updateConfiguration(android.content.res.Configuration(original).apply { fontScale = 1.6f }, activity.resources.displayMetrics)
        val prefs = activity.getSharedPreferences("xcertplay_airplay", 0)
        prefs.edit().clear().putBoolean("hide_byd_call_popup", saved).commit()
        val before = prefs.all.toMap()
        PendingReconnect.clear()
        ReflectionHelpers.setField(activity, "settingsSectionFilter", setOf(SettingsSection.ADVANCED_MEDIA))
        repeat(2) {
            val content = LinearLayout(activity)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "allSettingsSections",
                ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, content))
            val controls = switches(content)
            assertFalse(controls.any { it.contentDescription == activity.getString(R.string.settings_hide_byd_call_popup) })
            assertTrue("Ordinary media settings remain available", controls.any { it.contentDescription == activity.getString(R.string.efficient_video) })
            assertEquals(before, prefs.all)
            assertNull("No reconnect marker may be created",
                ReflectionHelpers.getField<java.lang.ref.WeakReference<Any>>(PendingReconnect, "session").get())
        }
        activity.finish()
        activity.resources.updateConfiguration(original, activity.resources.displayMetrics)
    }

    private fun switches(view: View): List<Switch> = when (view) {
        is Switch -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { switches(view.getChildAt(it)) }
        else -> emptyList()
    }
}
