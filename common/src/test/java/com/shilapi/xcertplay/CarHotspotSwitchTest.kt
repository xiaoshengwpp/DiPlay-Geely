package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydAdbAccess
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.hud.BydVehicleFieldStore
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE, shadows = [CarHotspotSwitchTest.Grant::class,
    CarHotspotAdbGrantTest.WritePermission::class, CarHotspotSwitchTest.StatusCheck::class])
class CarHotspotSwitchTest {
    private lateinit var activity: DiPlayActivity
    private lateinit var controls: LinearLayout
    private var hotspotBeforeResume: Map<String, *> = emptyMap()

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot", "xcertplay_airplay", "diplay")) {
            app.getSharedPreferences(name, 0).edit().clear().commit()
        }
        AppLocale.save(app, AppLocale.SYSTEM)
        BydVehicleFieldStore.clearMemoryForTests()
        CarPlayBackgroundSession.clear()
        CarHotspotAdbGrantTest.WritePermission.allowed = false
        ShadowSettings.setCanDrawOverlays(false)
        Grant.entered = CountDownLatch(1)
        Grant.release = CountDownLatch(1)
        Grant.worker = null
        Grant.requested.clear()
        Grant.fail = false
        StatusCheck.workers.clear()
        Grant.allowed = CarHotspotSetup.Permission.entries.toSet()
        Grant.access = LocalAdb.Access.READY
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveAutoStartOnBoot(activity, false)
        controls = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("renderBydAdbControls", LinearLayout::class.java,
            LocalAdb.Access::class.java).apply { isAccessible = true }.invoke(activity, controls, LocalAdb.Access.READY)
        activity.setContentView(controls)
    }

    @After fun tearDown() {
        Grant.release.countDown()
        Grant.worker?.join(3_000)
        StatusCheck.workers.forEach { it.join(3_000) }
        shadowOf(Looper.getMainLooper()).idle()
        CarPlayBackgroundSession.clear()
        BydVehicleFieldStore.clearMemoryForTests()
    }

    @Test fun directEnableRequestDoesNotAuthorizePermissionsOrSave() {
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        assertFalse(CarHotspotSettings.enabled(activity))
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        assertNull(ShadowToast.getLatestToast())
    }

    @Test fun adbApprovalCannotOverrideGeelyPolicy() {
        Grant.access = LocalAdb.Access.NOT_APPROVED
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        assertFalse(CarHotspotSettings.enabled(activity))
    }

    @Test fun adbSuccessWithoutActualPermissionDoesNotEnableTheSwitch() {
        Grant.allowed = emptySet()
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        assertFalse(CarHotspotSettings.enabled(activity))
    }

    @Test fun existingPermissionDoesNotOverrideGeelyPolicy() {
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        assertFalse(CarHotspotSettings.enabled(activity))
        assertTrue(CarHotspotSetup.Permission.HOTSPOT.granted(activity))
    }

    @Test fun bootPreferenceCannotAuthorizeEitherHotspotPermission() {
        AirPlayPersistence.saveAutoStartOnBoot(activity, true)
        requestDenied(CarHotspotSetup.Permission.HOTSPOT, CarHotspotSetup.Permission.BOOT_LAUNCH)
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertFalse(CarHotspotSettings.enabled(activity))
        assertFalse(CarHotspotSetup.Permission.BOOT_LAUNCH.granted(activity))
    }

    @Test fun incompleteBootPermissionKeepsTheHotspotSwitchOff() {
        AirPlayPersistence.saveAutoStartOnBoot(activity, true)
        Grant.allowed = setOf(CarHotspotSetup.Permission.HOTSPOT)
        requestDenied(CarHotspotSetup.Permission.HOTSPOT, CarHotspotSetup.Permission.BOOT_LAUNCH)
        assertFalse(CarHotspotSetup.Permission.HOTSPOT.granted(activity))
        assertFalse(CarHotspotSetup.Permission.BOOT_LAUNCH.granted(activity))
    }

    @Test fun hotspotPreferenceCannotAuthorizeBootPermission() {
        CarHotspotSettings.setEnabled(activity, true)
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        requestDenied(CarHotspotSetup.Permission.BOOT_LAUNCH)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertFalse(CarHotspotSetup.Permission.BOOT_LAUNCH.granted(activity))
    }

    @Test fun aFailedBootGrantPreservesHotspotAndDoesNotEnableBootLaunch() {
        CarHotspotSettings.setEnabled(activity, true)
        Grant.allowed = emptySet()
        val before = activity.getSharedPreferences("diplay_car_hotspot", 0).all
        requestDenied(CarHotspotSetup.Permission.BOOT_LAUNCH)
        assertEquals(before, activity.getSharedPreferences("diplay_car_hotspot", 0).all)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
    }

    @Test fun turningTheSwitchOffDoesNotRequestOrRevokePermissions() {
        CarHotspotSettings.setEnabled(activity, true)
        CarHotspotAdbGrantTest.WritePermission.allowed = true
        refreshControls()
        assertTrue(switches(controls).isEmpty())
        CarHotspotSettings.setEnabled(activity, false)
        assertFalse(CarHotspotSettings.enabled(activity))
        assertTrue(CarHotspotSetup.Permission.HOTSPOT.granted(activity))
        assertTrue(Grant.requested.isEmpty())
    }

    @Test fun vehicleControlsAreNotDuplicatedInTheHotspotPermissionCard() {
        assertTrue(switches(controls).isEmpty())
        assertFalse(BydOutputSettings.batteryToIphone(activity))
        assertFalse(BydOutputSettings.wheelSpeedToIphone(activity))
        assertFalse(BydOutputSettings.videoWhileParked(activity))
        assertTrue(Grant.requested.isEmpty())
    }

    @Test fun anOutstandingVehicleCheckPreventsASecondAuthorizationFlow() {
        for (pending in listOf(true, false)) {
            DiPlayActivity::class.java.getDeclaredField("adbCheckInProgress").apply {
                isAccessible = true
            }.setBoolean(activity, pending)
            refreshControls()
            requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        }
        assertFalse(CarHotspotSettings.enabled(activity))
    }

    @Test fun deniedRequestDoesNotChangeTheCurrentPage() {
        Grant.fail = true
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        assertFalse(CarHotspotSettings.enabled(activity))
        assertSame(controls, activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    }

    @Test fun returningFromApprovalBeforeCompletionKeepsThePage() {
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        prepareResume()
        lifecycle("onPause")
        lifecycle("onResume")
        assertSettingsPageWithoutAuthorization()
        assertTrue(StatusCheck.workers.isEmpty())
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        lifecycle("onPause")
    }

    @Test fun returningFromApprovalAfterCompletionKeepsThePage() {
        prepareResume()
        lifecycle("onPause")
        requestDenied(CarHotspotSetup.Permission.HOTSPOT)
        lifecycle("onResume")
        assertSettingsPageWithoutAuthorization()
        assertTrue(StatusCheck.workers.isEmpty())
        assertNull(Grant.worker)
        lifecycle("onPause")
    }

    /** Resume may redraw the same page; view identity is not navigation state. */
    private fun assertSettingsPageWithoutAuthorization() {
        val page = DiPlayActivity::class.java.getDeclaredField("page").apply { isAccessible = true }.get(activity)
        assertEquals("settings", page)
        assertNotNull(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        assertFalse(CarHotspotSettings.enabled(activity))
        assertEquals(hotspotBeforeResume, activity.getSharedPreferences("diplay_car_hotspot", 0).all)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(activity))
        assertFalse(CarPlayBackgroundSession.hasSession())
        assertEquals(AppLocale.SYSTEM, AppLocale.preference(activity))
        assertFalse(activity.isChangingConfigurations)
        assertTrue(Grant.requested.isEmpty())
        assertNull(Grant.worker)
    }

    /** A stale UI listener is denied before permission work or the preference-save callback. */
    private fun requestDenied(vararg permissions: CarHotspotSetup.Permission) {
        assertTrue(switches(controls).isEmpty())
        var saved = false
        val save = { saved = true }
        DiPlayActivity::class.java.getDeclaredMethod("requestAdbSwitchChange", List::class.java, kotlin.jvm.functions.Function0::class.java)
            .apply { isAccessible = true }.invoke(activity, permissions.toList(), save)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(saved)
        assertTrue(Grant.requested.isEmpty())
        assertNull(Grant.worker)
        assertEquals(1L, Grant.entered.count)
    }

    private fun lifecycle(name: String) {
        DiPlayActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(activity)
    }

    private fun prepareResume() {
        // This focused fixture intentionally skips onCreate; initialize the locale snapshot it owns.
        // Otherwise the old forced-Chinese default caused an unrelated recreate/early return and
        // accidentally hid the legitimate same-page redraw later in onResume.
        DiPlayActivity::class.java.getDeclaredField("languagePreferenceAtCreate").apply {
            isAccessible = true
        }.set(activity, AppLocale.preference(activity))
        hotspotBeforeResume = activity.getSharedPreferences("diplay_car_hotspot", 0).all.toMap()

        for ((name, value) in mapOf("initialLaunch" to false, "page" to "settings")) {
            DiPlayActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
        }
    }

    @Implements(BydAdbAccess::class, isInAndroidSdk = false)
    class StatusCheck {
        @Implementation fun check(context: Context, mayAsk: Boolean): BydAdbAccess.Status {
            workers.add(Thread.currentThread())
            return BydAdbAccess.Status(BydAdbAccess.State.READY, batteryPercent = 74.0, rangeKm = 48)
        }
        companion object { val workers = CopyOnWriteArrayList<Thread>() }
    }

    private fun awaitGrant() { assertTrue(Grant.entered.await(3, TimeUnit.SECONDS)) }

    private fun completeGrant() {
        Grant.release.countDown()
        Grant.worker!!.join(3_000)
        assertFalse(Grant.worker!!.isAlive)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun hotspotSwitch(): Switch = switchFor(R.string.auto_car_hotspot_title)

    private fun switchFor(title: Int): Switch = switches(controls).single {
        it.contentDescription == activity.getString(title)
    }

    private fun refreshControls() {
        DiPlayActivity::class.java.getDeclaredMethod("updateAdbSwitches").apply {
            isAccessible = true
        }.invoke(activity)
    }

    private fun bootSwitch(): Switch {
        switches(controls).firstOrNull {
            it.contentDescription == activity.getString(R.string.open_after_the_car_starts)
        }?.let { return it }
        val bootControls = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("connectionSettings", LinearLayout::class.java).apply {
            isAccessible = true
        }.invoke(activity, bootControls)
        controls.addView(bootControls)
        return switches(controls).single { it.contentDescription == activity.getString(R.string.open_after_the_car_starts) }
    }

    private fun switches(view: View): List<Switch> = buildList {
        if (view is Switch) add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(switches(view.getChildAt(i)))
    }

    @Implements(CarHotspotSetup::class, isInAndroidSdk = false)
    internal class Grant {
        @Implementation fun check(context: Context, adb: LocalAdb): LocalAdb.Access = LocalAdb.Access.UNREACHABLE

        @Implementation fun grant(context: Context, permissions: List<CarHotspotSetup.Permission>, adb: LocalAdb): LocalAdb.Access {
            worker = Thread.currentThread()
            requested.addAll(permissions)
            entered.countDown()
            check(release.await(3, TimeUnit.SECONDS))
            check(!fail) { "ADB connection failed" }
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (permission !in allowed) break
                    when (permission) {
                        CarHotspotSetup.Permission.HOTSPOT -> CarHotspotAdbGrantTest.WritePermission.allowed = true
                        CarHotspotSetup.Permission.BOOT_LAUNCH -> ShadowSettings.setCanDrawOverlays(true)
                    }
                }
            }
            return access
        }

        companion object {
            lateinit var entered: CountDownLatch
            lateinit var release: CountDownLatch
            lateinit var allowed: Set<CarHotspotSetup.Permission>
            lateinit var access: LocalAdb.Access
            val requested = CopyOnWriteArrayList<CarHotspotSetup.Permission>()
            @Volatile var worker: Thread? = null
            @Volatile var fail = false
        }
    }
}
