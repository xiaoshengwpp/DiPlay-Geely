package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.shilapi.xcertplay.host.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-w2667dp-h1333dp")
class GeelyHomeUiTest {
    private lateinit var controller: ActivityController<DiPlayActivity>
    private lateinit var activity: DiPlayActivity

    @Before fun setUp() {
        CarPlayBackgroundSession.clear()
        controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        activity = controller.get()
    }

    @After fun tearDown() {
        controller.pause().stop().destroy()
        CarPlayBackgroundSession.clear()
    }

    @Test fun fullSizeMultiWindowRetainsTheExistingGeelyHome() {
        shadowOf(activity).setInMultiWindowMode(true)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any {
            it.text == activity.getString(R.string.a_familiar_drive)
        })
    }

    @Test fun missingImagePickerShowsAMessageAndKeepsTheCarButtonCard() {
        ReflectionHelpers.setField(activity, "iconPicker", object : ActivityResultLauncher<String>() {
            override fun launch(input: String, options: ActivityOptionsCompat?) {
                throw ActivityNotFoundException("No image picker")
            }
            override fun unregister() = Unit
            override fun getContract() = ActivityResultContracts.GetContent()
        })
        ReflectionHelpers.setField(activity, "iconDocumentPicker", object : ActivityResultLauncher<Array<String>>() {
            override fun launch(input: Array<String>, options: ActivityOptionsCompat?) {
                throw ActivityNotFoundException("No document picker")
            }
            override fun unregister() = Unit
            override fun getContract() = ActivityResultContracts.OpenDocument()
        })
        val page = settings()
        views(page).filterIsInstance<Button>().single { it.text == activity.getString(R.string.choose_image) }.performClick()
        assertEquals(activity.getString(R.string.this_head_unit_has_no_image_picker), ShadowToast.getTextOfLatestToast())
        assertTrue(views(page).filterIsInstance<TextView>().any {
            it.text == activity.getString(R.string.car_button_description)
        })
    }

    @Test fun savingAndCancellingTheCarButtonNameUseTheSameExistingPreference() {
        AirPlayPersistence.saveOemLabel(activity, "Geely original")
        val page = settings()
        fun openName(): AlertDialog {
            views(page).filterIsInstance<Button>().single {
                it.text.startsWith(activity.getString(R.string.car_button_name) + " · ")
            }.performClick()
            return ShadowAlertDialog.getLatestAlertDialog()
        }
        val cancelled = openName()
        views(cancelled.window!!.decorView).filterIsInstance<EditText>().single().setText("Do not save")
        cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(cancelled.isShowing)
        assertEquals("Geely original", AirPlayPersistence.loadOemLabel(activity))
        val saved = openName()
        views(saved.window!!.decorView).filterIsInstance<EditText>().single().setText("Geely custom")
        saved.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        // AlertDialog delivers the button callback through its main-thread Handler.
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(saved.isShowing)
        assertEquals("Geely custom", AirPlayPersistence.loadOemLabel(activity))
        assertTrue(views(page).filterIsInstance<Button>().any { it.text.endsWith(" · Geely custom") })
        assertFalse(CarPlayBackgroundSession.hasSession())
    }

    private fun settings(): LinearLayout = LinearLayout(activity).also { page ->
        activity.javaClass.getDeclaredMethod("vehicleSettings", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, page)
    }
    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
    }
}
