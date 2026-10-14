package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.util.concurrent.ExecutorService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** AUTO app appearance follows retained system day/night state, including reconnects with no video. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class WaitingScreenThemeTest {
    private lateinit var activity: CarPlayHostActivity

    @Before fun setUp() {
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        activity.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        AirPlayPersistence.saveAppAppearance(activity, AppAppearance.AUTO)
    }

    @After fun tearDown() {
        (field("mainHandler") as Handler).removeCallbacksAndMessages(null)
        (field("teardownExecutor") as ExecutorService).shutdownNow()
        (field("airPlayCommandExecutor") as ExecutorService).shutdownNow()
        activity.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        AppAppearanceRuntime.resetForTest()
    }

    @Test fun startupAtNightPaintsDarkBeforeShowingTheWaitingPanel() {
        build(night = true)
        assertPalette(night = true)
        assertEquals(View.VISIBLE, panel().visibility)
    }

    @Test fun startupByDayPaintsTheLightWaitingPanel() {
        build(night = false)
        assertPalette(night = false)
    }

    @Test fun openWaitingPanelRepaintsForCallbacksAndPollingWithoutRestarting() {
        build(night = true)
        val original = panel()
        refresh(Configuration.UI_MODE_NIGHT_NO, ThemeModeDiagnostics.Source.CALLBACK)
        assertPalette(night = false)
        refresh(Configuration.UI_MODE_NIGHT_YES, ThemeModeDiagnostics.Source.POLL)
        assertPalette(night = true)
        assertSame(original, panel())
        assertEquals(0, field("restartGeneration"))
    }

    @Test fun disconnectAtNightShowsTheDarkPanelAgainAfterDaytimeVideo() {
        build(night = false)
        stream(active = true)
        assertEquals(View.GONE, panel().visibility)
        refresh(Configuration.UI_MODE_NIGHT_YES, ThemeModeDiagnostics.Source.POLL)
        stream(active = false)
        assertEquals(View.VISIBLE, panel().visibility)
        assertPalette(night = true)
        // Repeated reconnect/disconnect transitions must retain the latest palette.
        stream(active = true)
        stream(active = false)
        assertPalette(night = true)
    }

    @Test fun anUndefinedSystemSignalRetainsDarkInsteadOfFlashingLight() {
        build(night = true)
        refresh(Configuration.UI_MODE_TYPE_CAR, ThemeModeDiagnostics.Source.WINDOW_FOCUS)
        assertPalette(night = true)
    }

    private fun build(night: Boolean) {
        // The fixture bypasses private accessory bootstrap; mirror onCreate's state snapshot.
        setField("darkMode", night)
        setField("appNight", activity.resolveAppNightNow(night))
        setField("lastConfiguration", Configuration().apply {
            uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        activity.javaClass.getDeclaredMethod("buildContentView").apply { isAccessible = true }.invoke(activity)
    }

    private fun refresh(mode: Int, source: ThemeModeDiagnostics.Source) {
        activity.javaClass.getDeclaredMethod("refreshConfiguration", Configuration::class.java, ThemeModeDiagnostics.Source::class.java)
            .apply { isAccessible = true }.invoke(activity, Configuration().apply { uiMode = mode }, source)
    }

    private fun stream(active: Boolean) {
        activity.javaClass.getDeclaredMethod("onScreenStreamStateChanged",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, 0, 110, active)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertPalette(night: Boolean) {
        val colors = WaitingScreenColors.of(night)
        assertEquals(colors.background, (panel().background as ColorDrawable).color)
        assertEquals(colors.text, (field("stageStatusView") as TextView).currentTextColor)
        val labels = views(panel()).filterIsInstance<TextView>().toList()
        assertEquals(colors.text, labels.single { it.text == activity.getString(R.string.diplay) }.currentTextColor)
        assertEquals(colors.secondary, labels.single {
            it.text == activity.javaClass.getDeclaredMethod("settingsGestureHintText").apply { isAccessible = true }.invoke(activity)
        }.currentTextColor)
    }

    private fun panel() = field("connectionPanel") as View
    private fun field(name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun setField(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
}
