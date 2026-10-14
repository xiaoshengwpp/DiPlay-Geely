package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/** Upstream's automatic-hotspot permission/retry flow must not activate the Geely backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25, 29, 33], qualifiers = "en-w1000dp-h700dp-land",
    shadows = [HomeHotspotAccessTest.WritePermission::class])
class HomeHotspotAccessTest {
    @Test fun staleSettingsAndSystemAccessNeverStartHotspotOrOfferAPermissionGrant() {
        val context: Context = RuntimeEnvironment.getApplication()
        AppLocale.save(context, AppLocale.SYSTEM)
        CarPlayBackgroundSession.clear()
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        CarHotspotSettings.setEnabled(context, true)
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.carsettings"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.byd.carsettings"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        WritePermission.calls = 0
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "home")).setup()
        val home = controller.get()
        try {
            assertFalse(CarHotspotSettings.enabled(home))
            assertFalse(CarHotspotTethering.appStartSupported())
            assertNull(ReflectionHelpers.getField<CarHotspotTethering.Result?>(home, "hotspotStartupResult"))
            // Even a stale permission-required result must not cause a retry after returning to Home.
            ReflectionHelpers.setField(home, "hotspotStartupResult", CarHotspotTethering.Result.PERMISSION_REQUIRED)
            repeat(3) {
                controller.pause().stop().restart().start().resume()
                shadowOf(Looper.getMainLooper()).idle()
                ReflectionHelpers.callInstanceMethod<Unit>(home, "startCarHotspotOnLaunch")
                val shown = descendants(home.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }.toList()
                assertFalse(home.getString(R.string.settings_write_settings_allow) in shown)
                assertEquals(CarHotspotTethering.Result.PERMISSION_REQUIRED,
                    ReflectionHelpers.getField<CarHotspotTethering.Result?>(home, "hotspotStartupResult"))
            }
            assertEquals(0, WritePermission.calls)
            assertNull(shadowOf(home).nextStartedActivity)
            assertTrue(context.getSharedPreferences("diplay_car_hotspot", 0).getBoolean("auto_enable", false))
        } finally {
            controller.pause().stop().destroy()
            context.getSharedPreferences("diplay_car_hotspot", 0).edit().clear().commit()
            context.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
            shadowOf(context.packageManager).removePackage("com.byd.carsettings")
            CarPlayBackgroundSession.clear()
            AppAppearanceRuntime.resetForTest()
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    @Implements(Settings.System::class)
    class WritePermission {
        companion object {
            var calls = 0
            @JvmStatic @Implementation fun canWrite(context: Context): Boolean {
                calls++
                return true
            }
        }
    }
}
