package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AmbientColorSource
import com.shilapi.xcertplay.media.AmbientMusicSettings
import java.util.concurrent.CompletableFuture
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29], qualifiers = "en", manifest = Config.NONE)
class AmbientSettingsRedesignTest {
    private lateinit var activity: DiPlayActivity

    @Before fun setUp() {
        RuntimeEnvironment.getApplication().getSharedPreferences("ambient_music", 0).edit().clear().commit()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.ambientSupportCheck = { CompletableFuture.completedFuture(true) }
    }

    private fun text(id: Int, vararg args: Any) = activity.getString(id, *args)

    @Test fun offStateOnlyPromisesTheCarKeepsItsOwnSettings() {
        val lines = AmbientBehaviorText.lines(activity, AmbientMusicSettings.Values(enabled = false))
        assertEquals(listOf(text(R.string.settings_ambient_behavior_off)), lines)
    }

    @Test fun followingMusicInOneAreaSaysTheOtherZoneStaysAtTheLowestLevel() {
        val lines = AmbientBehaviorText.lines(activity, AmbientMusicSettings.Values(enabled = true, music = true, brightness = 4, area = 1))
        assertEquals(
            listOf(
                text(R.string.settings_ambient_behavior_follow, text(R.string.settings_ambient_front), 4),
                text(R.string.settings_ambient_behavior_idle),
                text(R.string.settings_ambient_behavior_other_zone),
                text(R.string.settings_ambient_behavior_restore),
            ),
            lines,
        )
    }

    @Test fun allZonesAndFixedColorsHaveNoOtherZoneLine() {
        val lines = AmbientBehaviorText.lines(activity, AmbientMusicSettings.Values(enabled = true, music = false, brightness = 2, area = 3))
        assertEquals(text(R.string.settings_ambient_behavior_fixed, text(R.string.settings_ambient_all), 2), lines.first())
        assertFalse(lines.contains(text(R.string.settings_ambient_behavior_other_zone)))
    }

    @Test fun levelZeroHoldsEveryZoneAtTheLowestLevel() {
        val lines = AmbientBehaviorText.lines(activity, AmbientMusicSettings.Values(enabled = true, music = true, brightness = 0, area = 2))
        assertEquals(listOf(text(R.string.settings_ambient_behavior_lowest), text(R.string.settings_ambient_behavior_restore)), lines)
    }

    @Test fun swatchGridKeepsAtLeastOneColorSelected() {
        var emptied = 0
        val changes = mutableListOf<Set<Int>>()
        val grid = AmbientSwatchGrid(activity, 0xff00ff, setOf(1), single = false, onChanged = { changes += it }, onLastDeselected = { emptied++ })
        swatch(grid, 1).performClick()
        assertEquals(1, emptied)
        assertEquals(setOf(1), grid.selection)
        swatch(grid, 9).performClick()
        assertEquals(setOf(1, 9), grid.selection)
        swatch(grid, 1).performClick()
        assertEquals(setOf(9), grid.selection)
        assertEquals(listOf(setOf(1, 9), setOf(9)), changes)
    }

    @Test fun singleSelectionReplacesTheColorAndAnnouncesSelection() {
        val grid = AmbientSwatchGrid(activity, 0xff00ff, setOf(4), single = true, onChanged = {}, onLastDeselected = {})
        swatch(grid, 20).performClick()
        assertEquals(setOf(20), grid.selection)
        assertEquals(text(R.string.settings_ambient_swatch_selected, 20), swatch(grid, 20).contentDescription)
        assertEquals(text(R.string.settings_ambient_swatch, 4), swatch(grid, 4).contentDescription)
    }

    @Test fun swatchGridWrapsInsideNarrowWidthsWithoutClipping() {
        val grid = AmbientSwatchGrid(activity, 0xff00ff, setOf(1), single = false, onChanged = {}, onLastDeselected = {})
        val width = (300 * activity.resources.displayMetrics.density).toInt()
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        grid.layout(0, 0, width, grid.measuredHeight)
        val children = (0 until grid.childCount).map { grid.getChildAt(it) }
        assertEquals(31, children.size)
        assertTrue("every swatch must fit the width", children.all { it.right <= width && it.left >= 0 })
        assertTrue("narrow widths must use several rows", children.map { it.top }.distinct().size > 1)
        assertTrue(children.all { it.bottom <= grid.measuredHeight })
    }

    @Test fun disabledGridIgnoresTaps() {
        val grid = AmbientSwatchGrid(activity, 0xff00ff, setOf(1), single = false, onChanged = { fail("disabled") }, onLastDeselected = {})
        grid.setInteractive(false)
        swatch(grid, 5).performClick()
        assertEquals(setOf(1), grid.selection)
    }

    @Test fun geelyDoesNotOpenTheBydAmbientDialog() {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "showAmbientConfiguration")
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(AmbientMusicSettings.load(activity).enabled)
    }

    private fun swatch(grid: ViewGroup, index: Int): View =
        descendants(grid).first { it.contentDescription?.toString()?.let { d -> d == text(R.string.settings_ambient_swatch, index) || d == text(R.string.settings_ambient_swatch_selected, index) } == true }

    private fun swatches(dialog: AlertDialog): List<View> = descendants(dialog.window!!.decorView).filter {
        it.contentDescription?.toString()?.let { d -> Regex("Color \\d+(, selected)?").matches(d) } == true
    }

    private fun texts(dialog: AlertDialog): List<String> =
        descendants(dialog.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }

    private fun switchWith(dialog: AlertDialog, id: Int): Switch = descendants(dialog.window!!.decorView)
        .filterIsInstance<Switch>().single { it.contentDescription == text(id) }

    private fun enableSwitch(dialog: AlertDialog) = switchWith(dialog, R.string.settings_ambient_enable)
    private fun musicSwitch(dialog: AlertDialog) = switchWith(dialog, R.string.settings_ambient_music)
    private fun albumSwitch(dialog: AlertDialog) = switchWith(dialog, R.string.settings_ambient_album)

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
