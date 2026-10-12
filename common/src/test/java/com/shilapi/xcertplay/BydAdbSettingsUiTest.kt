package com.shilapi.xcertplay

import android.content.Context
import android.os.Looper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.BydAdbAccess
import com.shilapi.xcertplay.hud.BydVehicleProbeOutcome
import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.hud.BydVehicleCapabilities
import com.shilapi.xcertplay.hud.BydVehicleFieldStore
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en", manifest = Config.NONE)
class BydAdbSettingsUiTest {
    private lateinit var activity: DiPlayActivity
    private lateinit var controls: LinearLayout
    private val backendCalls = mutableListOf<String>()
    private lateinit var permissionsBefore: Map<UsbPermissionSetup.Permission, Boolean>
    private lateinit var hotspotPermissionsBefore: Map<CarHotspotSetup.Permission, Boolean>

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot", "xcertplay_airplay")) {
            app.getSharedPreferences(name, 0).edit().clear().commit()
        }
        AppLocale.save(app, AppLocale.SYSTEM)
        backendCalls.clear()
        BydVehicleSettingsBackendProvider.current = object : BydVehicleSettingsBackend {
            override fun check(context: Context, mayAsk: Boolean): BydAdbAccess.Status {
                backendCalls += "check"
                return BydAdbAccess.Status(BydAdbAccess.State.READY)
            }
            override fun checkState(context: Context, mayAsk: Boolean): BydAdbAccess.State {
                backendCalls += "checkState"
                return BydAdbAccess.State.READY
            }
            override fun probe(context: Context, persist: Boolean): BydVehicleProbeOutcome {
                backendCalls += "probe"
                return BydVehicleProbeOutcome(BydAdbAccess.State.READY)
            }
            override fun execute(name: String, block: () -> Unit) { backendCalls += name }
        }
        BydVehicleFieldStore.clearMemoryForTests()
        shadowOf(app.packageManager).removePackage("com.byd.amapservice")
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = "com.byd.carsettings"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.byd.carsettings"
                flags = ApplicationInfo.FLAG_SYSTEM
            }
        })
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        AirPlayPersistence.saveWirelessHotspotMode(activity, WirelessHotspotMode.MANUAL)
        controls = LinearLayout(activity).apply { visibility = View.GONE }
        permissionsBefore = UsbPermissionSetup.snapshot(activity)
        hotspotPermissionsBefore = CarHotspotSetup.Permission.entries.associateWith { it.granted(activity) }
        assertEquals(GeelyVendorPolicy.bydAdb, CarHotspotSetup.isBydHeadUnit(activity))
        assertFalse(BydOutputSettings.navigationAvailable(activity))
    }

    @After fun tearDown() {
        BydVehicleSettingsBackendProvider.reset()
        BydVehicleFieldStore.clearMemoryForTests()
        CarPlayBackgroundSession.clear()
        PendingReconnect.clear()
    }

    @Test fun hotspotAndVehicleSettingsHaveOneOwnerWithoutNavigationServices() {
        if (!GeelyVendorPolicy.bydAdb) {
            render(LocalAdb.Access.READY)
            assertGeelyControlsRefused(advancedVehicleData())
            val entry = LinearLayout(activity)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "advancedVehicleDataSettings",
                ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, entry))
            assertEquals(listOf(activity.getString(R.string.byd_integration_disabled_geely)), labels(entry))
            assertEquals(0, switches(entry).size)
            assertFalse(CarHotspotSettings.enabled(activity))
            return
        }
        render(LocalAdb.Access.READY)
        val advanced = advancedVehicleData()
        val page = LinearLayout(activity).apply { addView(controls); addView(advanced) }
        assertEquals(View.VISIBLE, controls.visibility)
        assertEquals(1, switches(controls).size)
        assertFalse(CarHotspotSettings.enabled(activity))
        for (id in listOf(R.string.car_battery_for_the_iphone, R.string.wheel_speed_for_tunnels,
            R.string.video_while_parked, R.string.auto_car_hotspot_title)) {
            assertEquals(1, switches(page).count { it.contentDescription == activity.getString(id) })
        }
        assertEquals(1, labels(page).count { it == activity.getString(R.string.check_adb_access) })
        assertTrue(labels(advanced).any { it.contains(activity.getString(R.string.vehicle_data_mode_default)) })
    }

    @Test fun unapprovedHotspotStillShowsItsOptInAndVehicleSettingsRemainAvailable() {
        if (!GeelyVendorPolicy.bydAdb) {
            render(LocalAdb.Access.NOT_APPROVED)
            assertGeelyControlsRefused(advancedVehicleData())
            assertFalse(labels(controls).contains(activity.getString(R.string.adb_not_approved)))
            var permissionOperations = 0
            activity.usbPermissionOperationFactory = {
                permissionOperations++
                error("Geely must not construct a vehicle permission operation")
            }
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "autoApplyPermissions")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, permissionOperations)
            assertGeelyControlsRefused(advancedVehicleData())
            return
        }
        render(LocalAdb.Access.NOT_APPROVED)
        assertEquals(View.VISIBLE, controls.visibility)
        assertTrue(labels(controls).contains(activity.getString(R.string.adb_not_approved)))
        assertEquals(1, switches(controls).size)
        assertTrue(labels(advancedVehicleData()).contains(activity.getString(R.string.check_adb_access)))
    }

    @Test fun changingHotspotModeHidesOnlyHotspotControlsAndPreservesTheSavedChoice() {
        if (!GeelyVendorPolicy.bydAdb) {
            forgeVehiclePreferences()
            AirPlayPersistence.saveAutoStartOnBoot(activity, true)
            for (mode in listOf(WirelessHotspotMode.MANUAL, WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.MANUAL)) {
                AirPlayPersistence.saveWirelessHotspotMode(activity, mode)
                val before = preferences()
                render(LocalAdb.Access.READY)
                assertGeelyControlsRefused(advancedVehicleData())
                assertEquals(before, preferences())
                assertFalse(CarHotspotSettings.enabled(activity))
                assertFalse(AirPlayPersistence.loadBtSuspendDuringCarplay(activity))
                assertTrue(AirPlayPersistence.loadAutoStartOnBoot(activity))
            }
            return
        }
        CarHotspotSettings.setEnabled(activity, true)
        AirPlayPersistence.saveAutoStartOnBoot(activity, true)
        for (mode in listOf(WirelessHotspotMode.MANUAL, WirelessHotspotMode.WIFI_P2P, WirelessHotspotMode.MANUAL)) {
            AirPlayPersistence.saveWirelessHotspotMode(activity, mode)
            render(LocalAdb.Access.READY)
            assertEquals(if (mode == WirelessHotspotMode.MANUAL) View.VISIBLE else View.GONE, controls.visibility)
            assertEquals(mode == WirelessHotspotMode.MANUAL, labels(controls).contains(activity.getString(R.string.auto_car_hotspot_title)))
            val vehicleSwitches = switches(advancedVehicleData())
            assertEquals(setOf(
                R.string.car_battery_for_the_iphone, R.string.wheel_speed_for_tunnels,
                R.string.video_while_parked, R.string.bt_suspend_during_carplay,
            ).map { activity.getString(it) }.toSet(), vehicleSwitches.map { it.contentDescription.toString() }.toSet())
            assertFalse(vehicleSwitches.single {
                it.contentDescription == activity.getString(R.string.bt_suspend_during_carplay)
            }.isChecked)
            assertFalse(AirPlayPersistence.loadBtSuspendDuringCarplay(activity))
            assertTrue(CarHotspotSettings.enabled(activity))
            assertTrue(AirPlayPersistence.loadAutoStartOnBoot(activity))
        }
    }

    @Test fun bluetoothPauseRemainsAvailableWhenSavedProbeHasNoBatteryCapability() {
        if (!GeelyVendorPolicy.bydAdb) {
            forgeVehiclePreferences()
            AirPlayPersistence.saveBtSuspendDelaySeconds(activity, 15)
            val before = preferences()
            assertGeelyControlsRefused(advancedVehicleData())
            ReflectionHelpers.setField(activity, "adbSwitchChangePending", true)
            assertGeelyControlsRefused(advancedVehicleData())
            assertEquals(before, preferences())
            assertFalse(AirPlayPersistence.loadBtSuspendDuringCarplay(activity))
            assertEquals(15, AirPlayPersistence.loadBtSuspendDelaySeconds(activity))
            return
        }
        BydVehicleFieldStore.save(activity, BydVehicleCapabilities(
            fields = emptyMap(), catalogAvailable = false, firmwareKey = BydVehicleFieldStore.firmwareKey(),
        ))
        BydOutputSettings.setLegacyVehicleProbe(activity, true)
        AirPlayPersistence.saveBtSuspendDuringCarplay(activity, true)
        AirPlayPersistence.saveBtSuspendDelaySeconds(activity, 15)

        val advanced = advancedVehicleData()
        val pause = switches(advanced).single {
            it.contentDescription == activity.getString(R.string.bt_suspend_during_carplay)
        }
        assertTrue(pause.isChecked)
        assertTrue(pause.isEnabled)
        assertFalse(switches(advanced).any {
            it.contentDescription == activity.getString(R.string.car_battery_for_the_iphone)
        })
        assertTrue(labels(advanced).any { it.contains(activity.getString(R.string.bt_suspend_delay)) })
        assertTrue(labels(advanced).any { it.contains(activity.getString(R.string.bt_suspend_delay_option, 15)) })
        assertTrue(AirPlayPersistence.loadBtSuspendDuringCarplay(activity))
        assertEquals(15, AirPlayPersistence.loadBtSuspendDelaySeconds(activity))

        ReflectionHelpers.setField(activity, "adbSwitchChangePending", true)
        assertFalse(switches(advancedVehicleData()).single {
            it.contentDescription == activity.getString(R.string.bt_suspend_during_carplay)
        }.isEnabled)
    }

    @Test fun bluetoothPauseChangesApplyAtTheNextConnectionWithoutDroppingTheSession() {
        if (!GeelyVendorPolicy.bydAdb) {
            forgeVehiclePreferences()
            val before = preferences()
            val session = mock(CarPlayController::class.java)
            var stops = 0
            CarPlayBackgroundSession.store(session, mock(AndroidMediaSink::class.java), 800, 480, Any(),
                CarPlaySessionDisplay(800, 480, Surface.ROTATION_0, false, false, 800, 480)) { stops++ }
            CarPlayBackgroundSession.active = true
            PendingReconnect.clear()
            assertGeelyControlsRefused(advancedVehicleData())
            for (enabled in listOf(true, false)) {
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "onVehicleDataSettingChanged",
                    ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, enabled))
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before, preferences())
            assertFalse(PendingReconnect.isPending(session))
            assertSame(session, CarPlayBackgroundSession.snapshot()?.controller)
            assertEquals(0, stops)
            assertGeelyControlsRefused(advancedVehicleData())
            return
        }
        AirPlayPersistence.saveBtSuspendDuringCarplay(activity, true)
        val session = mock(CarPlayController::class.java)
        var stops = 0
        CarPlayBackgroundSession.store(session, mock(AndroidMediaSink::class.java), 800, 480, Any(),
            CarPlaySessionDisplay(800, 480, Surface.ROTATION_0, false, false, 800, 480)) { stops++ }
        CarPlayBackgroundSession.active = true
        try {
            PendingReconnect.clear()
            switches(advancedVehicleData()).single {
                it.contentDescription == activity.getString(R.string.bt_suspend_during_carplay)
            }.performClick()
            assertFalse(AirPlayPersistence.loadBtSuspendDuringCarplay(activity))
            assertTrue(PendingReconnect.isPending(session))
            assertSame(session, CarPlayBackgroundSession.snapshot()?.controller)
            assertEquals(0, stops)
        } finally {
            CarPlayBackgroundSession.clear()
            PendingReconnect.clear()
        }
    }

    @Test fun unavailableHotspotAdbDoesNotHideTheVehicleModeOrItsSavedSwitches() {
        if (!GeelyVendorPolicy.bydAdb) {
            forgeVehiclePreferences()
            val before = preferences()
            for (access in LocalAdb.Access.entries) {
                render(LocalAdb.Access.READY)
                render(access)
                assertGeelyControlsRefused(advancedVehicleData())
                assertNull(ReflectionHelpers.getField<TextView?>(activity, "adbStatus"))
            }
            assertEquals(before, preferences())
            assertFalse(BydOutputSettings.batteryToIphone(activity))
            return
        }
        BydOutputSettings.setBatteryToIphone(activity, true)
        for (access in listOf(LocalAdb.Access.UNREACHABLE, LocalAdb.Access.UNSUPPORTED)) {
            render(LocalAdb.Access.READY)
            render(access)
            assertEquals(View.GONE, controls.visibility)
            assertEquals(0, controls.childCount)
            assertNull(ReflectionHelpers.getField<TextView?>(activity, "adbStatus"))
            assertTrue(switches(advancedVehicleData()).single {
                it.contentDescription == activity.getString(R.string.car_battery_for_the_iphone)
            }.isChecked)
        }
    }

    @Test fun hotspotAuthorizationDisablesVehicleChoicesWithoutChangingPreferences() {
        if (!GeelyVendorPolicy.bydAdb) {
            forgeVehiclePreferences()
            val before = preferences()
            for (pending in listOf(true, false)) {
                ReflectionHelpers.setField(activity, "adbSwitchChangePending", pending)
                render(LocalAdb.Access.READY)
                assertGeelyControlsRefused(advancedVehicleData())
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "checkAdbState",
                    ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
                ReflectionHelpers.callInstanceMethod<Unit>(activity, "probeVehicleData",
                    ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            }
            assertEquals(before, preferences())
            assertFalse(BydOutputSettings.legacyVehicleProbe(activity))
            assertFalse(BydOutputSettings.batteryToIphone(activity))
            assertGeelyControlsRefused(advancedVehicleData())
            return
        }
        ReflectionHelpers.setField(activity, "adbSwitchChangePending", true)
        val advanced = advancedVehicleData()
        assertTrue(switches(advanced).all { !it.isEnabled })
        assertFalse(descendants(advanced).filterIsInstance<TextView>().single {
            it.text.startsWith(activity.getString(R.string.vehicle_data_mode) + " · ")
        }.isEnabled)
        assertFalse(BydOutputSettings.legacyVehicleProbe(activity))
        assertFalse(BydOutputSettings.batteryToIphone(activity))
    }

    private fun forgeVehiclePreferences() {
        activity.getSharedPreferences("diplay_byd_outputs", 0).edit()
            .putBoolean("battery_to_iphone", true).putBoolean("wheel_speed_to_iphone", true)
            .putBoolean("video_while_parked", true).putBoolean("legacy_vehicle_probe", true).commit()
        activity.getSharedPreferences("diplay_car_hotspot", 0).edit().putBoolean("auto_enable", true).commit()
        activity.getSharedPreferences("xcertplay_airplay", 0).edit()
            .putBoolean("bt_suspend_during_carplay", true).commit()
    }

    private fun preferences(): Map<String, Map<String, *>> = listOf(
        "diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot", "xcertplay_airplay",
    ).associateWith { activity.getSharedPreferences(it, 0).all.toMap() }

    private fun assertGeelyControlsRefused(advanced: LinearLayout) {
        assertFalse(GeelyVendorPolicy.bydAdb)
        assertEquals(View.GONE, controls.visibility)
        assertEquals(0, controls.childCount)
        assertEquals(0, advanced.childCount)
        assertTrue(backendCalls.isEmpty())
        assertEquals(permissionsBefore, UsbPermissionSetup.snapshot(activity))
        assertEquals(hotspotPermissionsBefore,
            CarHotspotSetup.Permission.entries.associateWith { it.granted(activity) })
    }

    private fun advancedVehicleData() = LinearLayout(activity).also {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "advancedVehicleData",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, it))
    }
    private fun render(access: LocalAdb.Access) {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "renderBydAdbControls",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, controls),
            ReflectionHelpers.ClassParameter.from(LocalAdb.Access::class.java, access))
    }
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    private fun labels(view: View) = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }
    private fun switches(view: View) = descendants(view).filterIsInstance<Switch>()
}
