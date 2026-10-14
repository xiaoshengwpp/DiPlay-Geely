package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File
import android.os.Looper
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers

/**
 * Some firmware reports the car hotspot off while it is on (Geely Galaxy E8, #604). The "Car hotspot is off"
 * dialog's Connect button must then connect, not open the same dialog again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-w1000dp-h700dp-land", shadows = [CarHotspotOffConnectTest.HotspotState::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarHotspotOffConnectTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val opened = mutableListOf<DiPlayActivity>()

    @Before fun setUp() {
        clearSettings()
        HotspotState.on = false
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotSsid(context, "Car 5G")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "12345678")
    }

    @After fun tearDown() {
        opened.forEach { runCatching { it.finish() } }
        AppAppearanceRuntime.resetForTest()
        HotspotState.on = false
        clearSettings()
    }

    private fun clearSettings() {
        for (name in listOf("diplay", "xcertplay_airplay", "diplay_car_hotspot")) {
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
    }

    private fun openHome(): DiPlayActivity = Robolectric.buildActivity(DiPlayActivity::class.java,
        Intent(context, DiPlayActivity::class.java).putExtra("page", "home")).setup().get().also { opened += it }

    private fun carHotspotOff(home: DiPlayActivity): Boolean = ReflectionHelpers.callInstanceMethod(home, "carHotspotOff")

    private fun offDialogs(home: DiPlayActivity) = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>()
        .filter { shadowOf(it).title == home.getString(R.string.car_hotspot_is_off) }

    @Test fun connectInTheOffDialogConnectsInsteadOfAskingAgain() {
        val home = openHome()
        // Robolectric has no MFi setup; a real head unit gets this far.
        ReflectionHelpers.setField(home, "setupError", null)
        assertTrue(carHotspotOff(home))
        ReflectionHelpers.callInstanceMethod<Unit>(home, "connect", ReflectionHelpers.ClassParameter.from(Boolean::class.java, true))
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(home.getString(R.string.car_hotspot_is_off), shadowOf(dialog).title)

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // On main the tap opened this dialog a second time, and every later tap again.
        assertEquals(1, offDialogs(home).size)
        assertTrue(CarHotspotSettings.offReportIgnored(context))
        assertFalse(carHotspotOff(home))
    }

    @Test fun cancelDoesNotAuthorizeIgnoringTheReportAndReopeningStillWarns() {
        val home = openHome()
        ReflectionHelpers.callInstanceMethod<Unit>(home, "carHotspotOffDialog")
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(CarHotspotSettings.offReportIgnored(context))
        assertTrue(carHotspotOff(home))
        ReflectionHelpers.callInstanceMethod<Unit>(home, "carHotspotOffDialog")
        val reopened = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(home.getString(R.string.car_hotspot_is_off), shadowOf(reopened).title)
        reopened.cancel() // Back/outside dismissal has the same cancellation path.
        assertFalse(CarHotspotSettings.offReportIgnored(context))
        assertTrue(carHotspotOff(home))
    }

    @Test fun staleAutomaticHotspotChoiceCannotSuppressTheManualWarning() {
        CarHotspotSettings.setEnabled(context, true)
        val home = openHome()
        assertFalse(CarHotspotSettings.enabled(context))
        assertTrue(carHotspotOff(home))
        assertFalse(CarHotspotSettings.offReportIgnored(context))
    }

    @Test fun aHeadUnitWhoseReportComesRightWarnsAgain() {
        CarHotspotSettings.setOffReportIgnored(context, true)
        val home = openHome()
        assertFalse(carHotspotOff(home))
        // The hotspot reads on once: the report works on this head unit after all.
        HotspotState.on = true
        assertFalse(carHotspotOff(home))
        assertFalse(CarHotspotSettings.offReportIgnored(context))
        HotspotState.on = false
        assertTrue(carHotspotOff(home))
    }

    @Test fun compactFullAndArabicDialogScreenshotsAtLargeFont() {
        for ((locale, width) in listOf("en" to 600, "en" to 1000, "ar" to 600)) {
            RuntimeEnvironment.setQualifiers("$locale-w${width}dp-h700dp-mdpi")
            val previous = Configuration(context.resources.configuration)
            context.resources.updateConfiguration(Configuration(previous).apply { fontScale = 1.5f }, context.resources.displayMetrics)
            val home = openHome()
            try {
                ReflectionHelpers.callInstanceMethod<Unit>(home, "carHotspotOffDialog")
                val dialog = ShadowAlertDialog.getLatestAlertDialog()
                shadowOf(Looper.getMainLooper()).idle()
                val decor = requireNotNull(dialog.window).decorView
                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                decor.layout(0, 0, width, decor.measuredHeight)
                assertTrue(decor.height > 0)
                for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE)) {
                    val button = dialog.getButton(which)
                    assertTrue(button.isShown)
                    assertTrue(button.width > 0)
                    assertTrue(button.width <= width)
                }
                val bitmap = Bitmap.createBitmap(width, decor.height, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap))
                val target = File("build/reports/album-artwork-ui/car-hotspot-off-$locale-$width-large-font.png")
                target.parentFile.mkdirs()
                target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                bitmap.recycle()
                dialog.cancel()
                assertFalse(CarHotspotSettings.offReportIgnored(context))
            } finally {
                home.finish()
                context.resources.updateConfiguration(previous, context.resources.displayMetrics)
                RuntimeEnvironment.setQualifiers("en-w1000dp-h700dp-land")
            }
        }
    }

    /** Whether the car hotspot is on, as Android's Wi-Fi state reports it. */
    @Implements(className = "com.shilapi.xcertplay.network.CarHotspotStatus", isInAndroidSdk = false)
    class HotspotState {
        @Implementation fun isEnabled(context: Context): Boolean? = on

        companion object {
            @Volatile var on: Boolean? = false
        }
    }
}
