package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import com.shilapi.xcertplay.update.UpdateClient
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Test actual 0.2.16 entry points, including forged preferences and direct lifecycle calls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyLatestBoundaryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("disabled boundary accessed application")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("disabled boundary accessed preferences")
        override fun getSystemService(name: String): Any = error("disabled boundary accessed a service")
        override fun startActivity(intent: Intent) = error("disabled boundary launched an activity")
    }

    @Test fun forgedMapAndBluetoothPreferencesCannotEnableRoutes() {
        context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit()
            .putBoolean("cluster_map_enabled", true)
            .putBoolean("adb_cluster_activity_enabled", true)
            .putBoolean("platform21_cluster_enabled", true)
            .putInt("cluster_small_window_mode", 2)
            .putBoolean("center_map_overlay", true)
            .putBoolean("launcher_map_sharing", true)
            .putBoolean("bt_suspend_during_carplay", true).commit()
        assertFalse(AirPlayPersistence.loadClusterMapEnabled(context))
        assertFalse(AirPlayPersistence.loadAdbClusterEnabled(context))
        assertFalse(AirPlayPersistence.loadLegacyClusterEnabled(context))
        assertEquals(0, AirPlayPersistence.loadClusterSmallWindowMode(context))
        assertFalse(AirPlayPersistence.loadCenterMapOverlay(context))
        assertFalse(AirPlayPersistence.loadLauncherMapSharing(context))
        assertFalse(AirPlayPersistence.loadBtSuspendDuringCarplay(context))
        assertFalse(AdbClusterRouter.enabled(rejectingContext))
        assertNull(ClusterMapPresentation.findDisplay(rejectingContext, DiLink51ClusterLayout.Theme.entries.first()))
        assertFalse(CenterMapOverlay.permitted(rejectingContext))
        assertFalse(AdbClusterRouter.launch(rejectingContext, "not-a-token") { error("route prepared") }.success)
        assertNull(AdbClusterRouter.verify(rejectingContext, 123))
    }

    @Test fun forgedWheelConsentCannotRestoreOrConsumeKeys() {
        context.getSharedPreferences("diplay_wheel_map_zoom", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putBoolean("joystick", true).putBoolean("siri_key", true).commit()
        context.getSharedPreferences("diplay_navigation_wheel", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", true).putBoolean("authorized", true).commit()
        assertFalse(WheelZoomSettings.anyEnabled(context))
        assertFalse(NavigationWheelSettings.enabled(context))
        assertFalse(NavigationWheelSettings.restoreAllowed(context))
        CarPlayCallKeys.install(rejectingContext)
        assertFalse(WheelKeyService.wanted(rejectingContext))
        assertFalse(WheelKeyService.needsRestore(rejectingContext))
        WheelKeyService.restoreIfNeeded(rejectingContext)
        assertEquals(LocalAdb.Access.UNSUPPORTED, WheelKeyService.enableOverAdb(rejectingContext))
        assertFalse(WheelKeyService.applyServiceSettings(rejectingContext, { error("shell called") }))
        val service = WheelKeyService()
        service.javaClass.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
        assertFalse(WheelKeyService.connected())
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
        assertEquals(false, service.javaClass.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
            .apply { isAccessible = true }.invoke(service, event))
    }

    @Test fun usbAutomationCannotInspectOrClickAWindowOrCreateAnAdbClient() {
        UsbAutoConfirmService().onAccessibilityEvent(AccessibilityEvent.obtain())
        assertFalse(UsbAutoConfirmService.isEnabled(rejectingContext))
        assertFalse(UsbAutoConfirmService.openSettings(rejectingContext))
        val result = UsbPermissionSetup.Operation(rejectingContext,
            factory = { error("ADB client constructed") }, granted = { error("permission inspected") }).run()
        assertFalse(result.complete)
        assertEquals(LocalAdb.Access.UNSUPPORTED, result.access)
        assertTrue(result.verified.isEmpty())
    }

    @Test fun hotspotEntryPointsNeverCreateKeysOrChangePermissions() {
        assertFalse(CarHotspotSetup.isBydHeadUnit(rejectingContext))
        assertFalse(CarHotspotSetup.shouldStartOnLaunch(rejectingContext, false))
        assertEquals(LocalAdb.Access.UNSUPPORTED, CarHotspotSetup.check(rejectingContext))
        assertEquals(LocalAdb.Access.UNSUPPORTED,
            CarHotspotSetup.grant(rejectingContext, CarHotspotSetup.Permission.entries))
    }

    @Test fun directMapServiceAndClusterActivityEntryStayInert() {
        val service = Robolectric.buildService(MapEmbedService::class.java).create()
        assertNull(service.get().onBind(Intent("com.shihab.diplay.action.EMBED_MAP")))
        service.destroy()
        val activity = Robolectric.buildActivity(AdbClusterActivity::class.java).create()
        assertTrue(activity.get().isFinishing)
        assertFalse(ClusterActivityOutput.hasConfirmedRoute())
        activity.start().resume().pause().stop().destroy()
    }

    @Test fun officialUpdaterCannotCreateFilesOrFetchAnApk() {
        assertFalse(GeelyVendorPolicy.upstreamApkUpdates)
        val destination = File(context.cacheDir, "must-not-be-created/update.apk")
        try {
            UpdateClient.download("https://example.invalid/must-not-fetch.apk", destination) { _, _ -> error("progress delivered") }
            fail("upstream APK download accepted")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("disabled"))
        }
        assertFalse(destination.parentFile!!.exists())
    }

    @Test fun freshUiKeepsOneHundredPercentAndExplicitAutoStillWorks() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(100, InterfaceSize.preference(context))
        val screen = Configuration(context.resources.configuration).apply {
            smallestScreenWidthDp = 1333
            screenWidthDp = 2667
            screenHeightDp = 1333
        }
        assertNull(InterfaceSize.override(screen, InterfaceSize.preference(context)))
        InterfaceSize.save(context, InterfaceSize.AUTO)
        assertEquals(InterfaceSize.AUTO, InterfaceSize.preference(context))
        assertNotNull(InterfaceSize.override(screen, InterfaceSize.preference(context)))
    }

    @Test fun freshLanguageIsSimplifiedChineseAndSavedSystemFollowIsPreserved() {
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(AppLocale.SIMPLIFIED_CHINESE, AppLocale.preference(context))
        AppLocale.save(context, AppLocale.SYSTEM)
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
        assertSame(context, AppLocale.wrap(context))
        AppLocale.save(context, AppLocale.ENGLISH)
        assertEquals(AppLocale.ENGLISH, AppLocale.preference(context))
    }

    @Test @Config(sdk = [33]) fun android13ChineseDefaultMigratesOnlyOnce() {
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
        context.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        manager.applicationLocales = android.os.LocaleList.getEmptyLocaleList()
        assertEquals(AppLocale.SIMPLIFIED_CHINESE, AppLocale.preference(context))
        assertEquals("zh-CN", manager.applicationLocales.toLanguageTags())
        AppLocale.save(context, AppLocale.SYSTEM)
        AppLocale.wrap(context)
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(context))
        assertTrue(manager.applicationLocales.isEmpty)
    }
}
