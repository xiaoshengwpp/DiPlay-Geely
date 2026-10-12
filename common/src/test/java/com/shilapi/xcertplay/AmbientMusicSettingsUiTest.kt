package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import com.shilapi.xcertplay.host.R
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29], qualifiers = "en", manifest = Config.NONE)
class AmbientMusicSettingsUiTest {
    private lateinit var activity: DiPlayActivity

    @Before fun setUp() {
        RuntimeEnvironment.getApplication().getSharedPreferences("ambient_music", 0).edit().clear().commit()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
    }

    @Test fun geelyDoesNotOpenAmbientControlsOrCheckAccess() {
        activity.ambientSupportCheck = { fail("Geely must not probe ambient hardware"); CompletableFuture.completedFuture(false) }
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "showAmbientConfiguration")
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(AmbientMusicSettings.load(activity).enabled)
    }

    @Test fun forgedAmbientPreferenceDoesNotOpenControlsOrProbe() {
        var checks = 0
        activity.ambientSupportCheck = { checks++; CompletableFuture.completedFuture(false) }
        activity.getSharedPreferences("ambient_music", 0).edit().putBoolean("enabled", true).commit()
        val before = activity.getSharedPreferences("ambient_music", 0).all
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "showAmbientConfiguration")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, checks)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertEquals(before, activity.getSharedPreferences("ambient_music", 0).all)
    }

    @Test fun unrequestedSupportCompletionCannotEnableAmbientControl() {
        val pending = CompletableFuture<Boolean>()
        var checks = 0
        activity.ambientSupportCheck = { checks++; pending }
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "showAmbientConfiguration")
        pending.complete(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, checks)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertFalse(AmbientMusicSettings.load(activity).enabled)
    }

    private fun open(): AlertDialog {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "showAmbientConfiguration")
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    private fun enableSwitch(dialog: AlertDialog): Switch = descendants(dialog.window!!.decorView)
        .filterIsInstance<Switch>().single { it.contentDescription == activity.getString(R.string.settings_ambient_enable) }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
}
