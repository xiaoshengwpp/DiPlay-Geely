package com.shilapi.xcertplay

import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.hud.GeelyVendorPolicy
import com.shilapi.xcertplay.hud.BydAdbAccess
import com.shilapi.xcertplay.hud.BydFieldProbeResult
import com.shilapi.xcertplay.hud.BydFieldSource
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.hud.BydReadAddress
import com.shilapi.xcertplay.hud.BydVehicleCapabilities
import com.shilapi.xcertplay.hud.BydVehicleField
import com.shilapi.xcertplay.hud.BydVehicleFieldStore
import com.shilapi.xcertplay.hud.BydVehicleProbeOutcome
import com.shilapi.xcertplay.transport.VehicleGear
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.transport.VehicleStatusProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
@LooperMode(LooperMode.Mode.PAUSED)
class BydVehicleDataSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var controller: ActivityController<DiPlayActivity>? = null
    private val activity get() = requireNotNull(controller).get()
    private lateinit var backend: FakeVehicleSettingsBackend
    private var reconnects = 0
    private lateinit var permissionsBefore: Map<UsbPermissionSetup.Permission, Boolean>
    private lateinit var hotspotPermissionsBefore: Map<CarHotspotSetup.Permission, Boolean>

    @Before fun setUp() {
        backend = FakeVehicleSettingsBackend()
        BydVehicleSettingsBackendProvider.current = backend
        shadowOf(context.packageManager).removePackage("com.byd.amapservice")
        for (name in listOf("diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot", "xcertplay_airplay")) {
            context.getSharedPreferences(name, 0).edit().clear().commit()
        }
        AppLocale.save(context, AppLocale.SYSTEM)
        permissionsBefore = UsbPermissionSetup.snapshot(context)
        hotspotPermissionsBefore = CarHotspotSetup.Permission.entries.associateWith { it.granted(context) }
        BydVehicleFieldStore.clearMemoryForTests()
    }

    @After fun tearDown() {
        CarPlayBackgroundSession.clear()
        controller?.pause()?.stop()?.destroy()
        BydVehicleSettingsBackendProvider.reset()
        BydVehicleFieldStore.clearMemoryForTests()
        shadowOf(context.packageManager).removePackage("com.byd.amapservice")
    }

    @Test fun defaultModeShowsTheDiLink5SettingsBehindTheAdvancedButton() {
        if (!GeelyVendorPolicy.bydAdb) {
            openSettings()
            val before = vehiclePreferences()
            assertGeelyVehiclePage()
            val direct = LinearLayout(activity)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "advancedVehicleData",
                ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, direct))
            assertEquals(0, direct.childCount)
            assertGeelyRefused(before)
            return
        }
        openSettings()

        assertFalse(BydOutputSettings.navigationAvailable(activity))
        assertTrue(texts().any { it.text == activity.getString(R.string.advanced_vehicle_data) })
        assertFalse(switches().any { it.contentDescription == activity.getString(R.string.car_battery_for_the_iphone) })
        assertFalse(switches().any { it.contentDescription == activity.getString(R.string.wheel_speed_for_tunnels) })
        assertFalse(switches().any { it.contentDescription == activity.getString(R.string.cluster_song) })
        assertFalse(switches().any { it.contentDescription == activity.getString(R.string.navigation_on_hud_and_instrument_cluster) })

        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        assertTrue(texts().any { it.text == activity.getString(R.string.advanced_vehicle_data_description) })
        assertTrue(texts().any { it.text.toString().contains(activity.getString(R.string.vehicle_data_mode_default)) })
        assertTrue(texts().any { it.text == activity.getString(R.string.check_adb_access) })
        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.car_battery_for_the_iphone) })
        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.wheel_speed_for_tunnels) })
        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.video_while_parked) })
    }

    @Test fun successfulLiveProbeRevealsOnlySupportedSettings() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            ReflectionHelpers.setField(activity, "bydVehicleAdvancedExpanded", true)
            ReflectionHelpers.setField(activity, "adbAccessState", BydAdbAccess.State.READY)
            ReflectionHelpers.setField(activity, "vehicleProbeOutcome",
                BydVehicleProbeOutcome(BydAdbAccess.State.READY, supportedCapabilities()))
            invoke("render")
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        BydOutputSettings.setLegacyVehicleProbe(context, true)
        openSettings()
        ReflectionHelpers.setField(activity, "bydVehicleAdvancedExpanded", true)
        ReflectionHelpers.setField(activity, "adbAccessState", BydAdbAccess.State.READY)
        ReflectionHelpers.setField(activity, "vehicleProbeOutcome",
            BydVehicleProbeOutcome(BydAdbAccess.State.READY, supportedCapabilities()))
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")

        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.car_battery_for_the_iphone) })
        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.wheel_speed_for_tunnels) })
        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.video_while_parked) })
    }

    @Test fun savedProbeAndEnabledSwitchSurviveAFreshActivityWithoutReprobing() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            assertGeelyVehiclePage()
            requireNotNull(controller).pause().stop().destroy()
            controller = null
            BydVehicleFieldStore.clearMemoryForTests()
            openSettings()
            invoke("scheduleAutomaticVehicleValidation")
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        BydOutputSettings.setWheelSpeedToIphone(context, true)
        backend.checkResult = readableStatus()
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        assertTrue(vehicleSwitch(R.string.wheel_speed_for_tunnels).isChecked)
        requireNotNull(controller).pause().stop().destroy()
        controller = null
        BydVehicleFieldStore.clearMemoryForTests()
        // Work the first activity left queued would be ignored; count only the fresh one's.
        backend.tasks.clear()
        openSettings()
        shadowOf(Looper.getMainLooper()).idle()
        backend.runAll("diplay-byd13-auto-validate")
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        assertTrue(texts().any { it.text == activity.getString(R.string.hide_advanced_vehicle_data) })
        assertTrue(vehicleSwitch(R.string.wheel_speed_for_tunnels).isChecked)
        // The fresh activity validated the saved fields by reading them, not by probing again.
        assertEquals(1, backend.checkCalls)
        assertEquals(0, backend.probeCalls)
    }

    @Test fun theHotspotCardCannotExposeFieldsRejectedByTheLegacyProbe() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey(), without = setOf(BydVehicleField.GEAR))
            val before = openForgedGeelySettings(saved)
            AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
            val hotspot = LinearLayout(activity)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "renderBydAdbControls",
                ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, hotspot),
                ReflectionHelpers.ClassParameter.from(LocalAdb.Access::class.java, LocalAdb.Access.READY))
            assertEquals(0, hotspot.childCount)
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey(), without = setOf(BydVehicleField.GEAR))
        BydVehicleFieldStore.save(context, saved)
        BydOutputSettings.setLegacyVehicleProbe(context, true)
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()
        val hotspot = LinearLayout(activity)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "renderBydAdbControls",
            ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, hotspot),
            ReflectionHelpers.ClassParameter.from(LocalAdb.Access::class.java, LocalAdb.Access.READY))
        val page = descendants(activity.window.decorView).filterIsInstance<ScrollView>().first()
            .getChildAt(0) as LinearLayout
        page.addView(hotspot)

        assertEquals(1, switches().count { it.contentDescription == activity.getString(R.string.car_battery_for_the_iphone) })
        assertEquals(0, switches().count { it.contentDescription == activity.getString(R.string.wheel_speed_for_tunnels) })
        assertEquals(0, switches().count { it.contentDescription == activity.getString(R.string.video_while_parked) })
        assertTrue(BydOutputSettings.legacyVehicleProbe(context))
        assertEquals(saved, BydVehicleFieldStore.load(context))
    }

    @Test fun automaticValidationWaitsForHotspotAuthorizationThenResumes() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkResult = readableStatus()
            ReflectionHelpers.setField(activity, "adbSwitchChangePending", true)
            invoke("scheduleAutomaticVehicleValidation")
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyRefused(before)
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "automaticVehicleValidationPending"))
            ReflectionHelpers.setField(activity, "adbSwitchChangePending", false)
            // Even a stale queued-validation flag cannot schedule work after authorization finishes.
            ReflectionHelpers.setField(activity, "automaticVehicleValidationPending", true)
            invoke("runPendingAutomaticVehicleValidation")
            invoke("validateSavedVehicleConfigurationAutomatically")
            assertGeelyRefused(before)
            return
        }
        val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey())
        BydVehicleFieldStore.save(context, saved)
        BydOutputSettings.setLegacyVehicleProbe(context, true)
        backend.checkResult = readableStatus()
        controller = Robolectric.buildActivity(
            DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "settings"),
        )
        // setup() can drain the validation posted by onCreate; establish authorization first.
        ReflectionHelpers.setField(activity, "adbSwitchChangePending", true)
        requireNotNull(controller).setup()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(backend.tasks.none { it.first == "diplay-byd13-auto-validate" })
        assertEquals(0, backend.checkCalls)
        assertEquals(0, backend.probeCalls)
        assertTrue(ReflectionHelpers.getField<Boolean>(activity, "automaticVehicleValidationPending"))

        ReflectionHelpers.setField(activity, "adbSwitchChangePending", false)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "runPendingAutomaticVehicleValidation")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, backend.tasks.count { it.first == "diplay-byd13-auto-validate" })
        backend.runAll("diplay-byd13-auto-validate")
        assertEquals(1, backend.checkCalls)
        assertEquals(0, backend.probeCalls)
        assertEquals(saved, BydVehicleFieldStore.load(context))
    }

    @Test fun temporaryAdbFailureDoesNotHideSavedFunctionsOrClearSwitches() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.ADB_OFF)
            invoke("validateSavedVehicleConfigurationAutomatically")
            invokeBoolean("checkAdbState", true)
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        BydOutputSettings.setVideoWhileParked(context, true)
        backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.ADB_OFF)
        openSettings()
        shadowOf(Looper.getMainLooper()).idle()
        backend.runAll("diplay-byd13-auto-validate")
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        assertTrue(vehicleSwitch(R.string.video_while_parked).isChecked)
        assertTrue(texts().any { it.text == activity.getString(R.string.probe_vehicle_data_again) })
    }

    @Test fun clusterSongSwitchStaysInTheBydNavigationSectionOnly() {
        if (!GeelyVendorPolicy.bydAdb) {
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.byd.amapservice" })
            val before = openForgedGeelySettings()
            openCategory(R.string.settings_navigation)
            assertFalse(BydOutputSettings.navigationAvailable(activity))
            assertFalse(texts().any { it.text == activity.getString(R.string.byd_navigation) })
            assertEquals(0, switches().count { it.contentDescription == activity.getString(R.string.cluster_song) })
            openCategory(R.string.settings_advanced)
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.byd.amapservice" })
        openSettings()
        openCategory(R.string.settings_navigation)

        assertTrue(texts().any { it.text == activity.getString(R.string.byd_navigation) })
        assertEquals(1, switches().count { it.contentDescription == activity.getString(R.string.cluster_song) })
        openCategory(R.string.settings_advanced)
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()
        assertEquals(0, switches().count { it.contentDescription == activity.getString(R.string.cluster_song) })
    }

    @Test fun withoutBydNavigationTheClusterSongSwitchIsUnderAdvancedVehicleData() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            val card = LinearLayout(activity)
            ReflectionHelpers.callInstanceMethod<Unit>(activity, "clusterSongSwitch",
                ReflectionHelpers.ClassParameter.from(LinearLayout::class.java, card))
            assertEquals(0, card.childCount)
            BydOutputSettings.setClusterSong(context, true)
            assertFalse(BydOutputSettings.clusterSong(context))
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        openSettings()
        assertFalse(texts().any { it.text == activity.getString(R.string.byd_navigation) })
        assertFalse(switches().any { it.contentDescription == activity.getString(R.string.cluster_song) })

        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()
        vehicleSwitch(R.string.cluster_song).performClick()

        assertTrue(BydOutputSettings.clusterSong(context))
    }

    @Test fun scheduledValidationCannotLeaveAUserProbeStuck() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkStateResult = BydAdbAccess.State.READY
            backend.probeResult = BydVehicleProbeOutcome(BydAdbAccess.State.READY, supportedCapabilities())
            invokeProbe()
            invoke("scheduleAutomaticVehicleValidation")
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
        )
        backend.checkResult = readableStatus()
        openSettings()

        invokeProbe()
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "scheduleAutomaticVehicleValidation")
        shadowOf(Looper.getMainLooper()).idle()
        backend.run("diplay-byd13-probe")
        backend.runAll("diplay-byd13-auto-validate")

        assertFalse(ReflectionHelpers.getField(activity, "vehicleProbeAuthorizationInProgress"))
        assertFalse(ReflectionHelpers.getField(activity, "vehicleProbeInProgress"))
        assertFalse(ReflectionHelpers.getField(activity, "automaticVehicleValidationInProgress"))
    }

    @Test fun scheduledValidationCannotLeaveAnAdbCheckStuck() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkResult = readableStatus()
            invokeBoolean("checkAdbState", true)
            invoke("scheduleAutomaticVehicleValidation")
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.checkResult = readableStatus()
        openSettings()

        ReflectionHelpers.callInstanceMethod<Unit>(activity, "checkAdbState",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
        shadowOf(Looper.getMainLooper()).idle()
        backend.run("diplay-adb-state")
        backend.runAll("diplay-byd13-auto-validate")

        assertFalse(ReflectionHelpers.getField(activity, "adbCheckInProgress"))
        assertFalse(ReflectionHelpers.getField(activity, "automaticVehicleValidationInProgress"))
    }

    @Test fun aNewIntentDoesNotCancelAUserProbe() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            invokeProbe()
            returnFromCarPlay()
            invokeProbe()
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
        )
        backend.checkResult = readableStatus()
        openSettings()

        invokeProbe()
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onNewIntent",
            ReflectionHelpers.ClassParameter.from(
                Intent::class.java,
                Intent(context, DiPlayActivity::class.java).putExtra("page", "settings"),
            ))
        shadowOf(Looper.getMainLooper()).idle()
        backend.run("diplay-byd13-probe")
        backend.runAll("diplay-byd13-auto-validate")

        assertFalse(ReflectionHelpers.getField(activity, "vehicleProbeAuthorizationInProgress"))
        assertFalse(ReflectionHelpers.getField(activity, "vehicleProbeInProgress"))
    }

    @Test fun lostFieldsNeedExplicitReplacement() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey())
            val before = openForgedGeelySettings(saved)
            val candidate = supportedCapabilities(saved.firmwareKey, without = setOf(BydVehicleField.GEAR))
            injectReplacement(saved, candidate)
            invoke("replaceSavedVehicleDataAnyway")
            assertEquals(candidate, ReflectionHelpers.getField<BydVehicleCapabilities?>(activity, "pendingVehicleReplacement"))
            assertFalse(texts().any { it.text == activity.getString(R.string.replace_saved_vehicle_data_anyway) })
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        BydVehicleFieldStore.save(context, supportedCapabilities(currentKey))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(currentKey, without = setOf(BydVehicleField.GEAR)),
        )
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        invokeProbe()
        backend.run("diplay-byd13-probe")

        assertTrue(BydVehicleFieldStore.load(context)!!.gearSupported)
        assertTrue(texts().any { it.text.toString().contains(activity.getString(R.string.vehicle_field_gear)) })
        texts().single { it.text == activity.getString(R.string.replace_saved_vehicle_data_anyway) }.performClick()
        assertFalse(BydVehicleFieldStore.load(context)!!.gearSupported)
    }

    @Test fun aDestroyedUserProbeCannotSaveOrPublishItsLateCandidate() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.onProbe = { error("Geely must not enter a vehicle probe") }
            invokeProbe()
            val destroyed = activity
            requireNotNull(controller).pause().stop().destroy()
            controller = null
            ReflectionHelpers.callInstanceMethod<Unit>(destroyed, "probeVehicleData",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
            assertGeelyRefused(before)
            return
        }
        val saved = preparePublishingProbe()
        backend.onProbe = {
            requireNotNull(controller).pause().stop().destroy()
            controller = null
        }

        invokeProbe()
        backend.run("diplay-byd13-probe")

        assertEquals(1, backend.probeCalls)
        assertEquals(saved, BydVehicleFieldStore.load(context))
        assertEquals(25.0, publishedBatteryPercent(), 0.0)
    }

    @Test fun aModeChangeBeforeTheUserProbeReturnsKeepsThePreviousSnapshot() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            invokeProbe()
            selectLegacyMode(false)
            selectLegacyMode(true)
            assertFalse(BydOutputSettings.legacyVehicleProbe(context))
            assertGeelyRefused(before)
            return
        }
        val saved = preparePublishingProbe()
        backend.onProbe = { BydOutputSettings.setLegacyVehicleProbe(context, false) }

        invokeProbe()
        backend.run("diplay-byd13-probe")

        assertEquals(1, backend.probeCalls)
        assertFalse(BydOutputSettings.legacyVehicleProbe(context))
        assertEquals(saved, BydVehicleFieldStore.load(context))
        assertEquals(25.0, publishedBatteryPercent(), 0.0)
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "vehicleProbeInProgress"))
    }

    @Test fun aPausedUserProbeStillSavesAndPublishesItsAcceptedCandidate() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            invokeProbe()
            requireNotNull(controller).pause().stop()
            invokeProbe()
            invoke("validateSavedVehicleConfigurationAutomatically")
            assertGeelyRefused(before)
            requireNotNull(controller).restart().start().resume()
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        preparePublishingProbe()
        backend.onProbe = { requireNotNull(controller).pause().stop() }

        invokeProbe()
        backend.run("diplay-byd13-probe")

        assertEquals(backend.probeResult.capabilities, BydVehicleFieldStore.load(context))
        assertEquals(90.0, publishedBatteryPercent(), 0.0)
        requireNotNull(controller).destroy()
        controller = null
    }

    @Test fun selectingDefaultAgainCannotBeOverriddenByALateLegacySelection() {
        if (!GeelyVendorPolicy.bydAdb) {
            openSettings()
            val before = vehiclePreferences()
            selectLegacyMode(true)
            selectLegacyMode(false)
            selectLegacyMode(true)
            assertNull(BydVehicleFieldStore.load(context))
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
        )
        openSettings()
        backend.onProbe = { selectLegacyMode(false) }

        selectLegacyMode(true)
        backend.run("diplay-byd13-probe")

        assertFalse(BydOutputSettings.legacyVehicleProbe(context))
        assertEquals(null, BydVehicleFieldStore.load(context))
        assertFalse(ReflectionHelpers.getField<Boolean>(activity, "vehicleProbeInProgress"))
    }

    private fun preparePublishingProbe(): BydVehicleCapabilities {
        val capabilities = supportedCapabilities(BydVehicleFieldStore.firmwareKey())
        val saved = capabilities.copy(fields = capabilities.fields + (BydVehicleField.SOC to
            capabilities.result(BydVehicleField.SOC).copy(value = 25.0)))
        BydOutputSettings.setLegacyVehicleProbe(context, true)
        BydVehicleFieldStore.save(context, saved)
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            saved.copy(fields = saved.fields + (BydVehicleField.SOC to
                saved.result(BydVehicleField.SOC).copy(value = 90.0))),
        )
        openSettings()
        return saved
    }

    private fun publishedBatteryPercent(): Double {
        // Read the provider without starting its real ADB polling executor.
        val provider = Class.forName("com.shilapi.xcertplay.hud.BydBatteryStatus")
            .getField("INSTANCE").get(null) as VehicleStatusProvider
        return requireNotNull(provider.snapshot()).batteryPercent
    }

    @Test fun aPassingValidationKeepsTheOfferToReplaceSavedData() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey())
            val before = openForgedGeelySettings(saved)
            val candidate = supportedCapabilities(saved.firmwareKey, without = setOf(BydVehicleField.GEAR))
            injectReplacement(saved, candidate)
            backend.checkResult = readableStatus()
            returnFromCarPlay()
            invoke("validateSavedVehicleConfigurationAutomatically")
            assertEquals(candidate, ReflectionHelpers.getField<BydVehicleCapabilities?>(activity, "pendingVehicleReplacement"))
            assertFalse(texts().any { it.text == activity.getString(R.string.replace_saved_vehicle_data_anyway) })
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        BydVehicleFieldStore.save(context, supportedCapabilities(currentKey))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(currentKey, without = setOf(BydVehicleField.GEAR)),
        )
        backend.checkResult = readableStatus()
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()
        invokeProbe()
        backend.run("diplay-byd13-probe")

        // With no switch on, the check after a return from CarPlay passes without reading the lost gear.
        returnFromCarPlay()
        backend.runAll("diplay-byd13-auto-validate")

        assertTrue(texts().any { it.text == activity.getString(R.string.replace_saved_vehicle_data_anyway) })
    }

    @Test fun replaceAnywayKeepsTheAdbStateAndASnapshotSavedSince() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey())
            openForgedGeelySettings(saved)
            injectReplacement(saved, supportedCapabilities(saved.firmwareKey, without = setOf(BydVehicleField.GEAR)))
            forgeVehiclePreferences(saved.copy(detectedAtMillis = 42))
            val before = vehiclePreferences()
            ReflectionHelpers.setField(activity, "adbAccessState", BydAdbAccess.State.ADB_OFF)
            invoke("replaceSavedVehicleDataAnyway")
            assertEquals(BydAdbAccess.State.ADB_OFF, ReflectionHelpers.getField<BydAdbAccess.State?>(activity, "adbAccessState"))
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        BydVehicleFieldStore.save(context, supportedCapabilities(currentKey))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(currentKey, without = setOf(BydVehicleField.GEAR)),
        )
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()
        invokeProbe()
        backend.run("diplay-byd13-probe")

        // Meanwhile ADB was turned off, and another probe saved its result.
        backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.ADB_OFF)
        returnFromCarPlay()
        backend.runAll("diplay-byd13-auto-validate")
        val savedSince = supportedCapabilities(currentKey).copy(detectedAtMillis = 42)
        BydVehicleFieldStore.save(context, savedSince)
        texts().single { it.text == activity.getString(R.string.replace_saved_vehicle_data_anyway) }.performClick()

        assertEquals(savedSince, BydVehicleFieldStore.load(context))
        assertTrue(texts().any { it.text == activity.getString(R.string.adb_off) })
        assertTrue(texts().any {
            it.text == activity.getString(R.string.vehicle_probe_failed,
                activity.getString(R.string.vehicle_probe_snapshot_changed))
        })
    }

    @Test fun aFailedReplacementIsShownInsteadOfCrashing() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey())
            val before = openForgedGeelySettings(saved)
            injectReplacement(saved, supportedCapabilities("another firmware", without = setOf(BydVehicleField.GEAR)))
            invoke("replaceSavedVehicleDataAnyway")
            assertNull(ReflectionHelpers.getField<BydVehicleProbeOutcome?>(activity, "vehicleProbeOutcome"))
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        BydVehicleFieldStore.save(context, supportedCapabilities(currentKey))
        backend.checkStateResult = BydAdbAccess.State.READY
        // The store refuses to write another firmware's probe, standing in for a failed write.
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities("another firmware", without = setOf(BydVehicleField.GEAR)),
        )
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()
        invokeProbe()
        backend.run("diplay-byd13-probe")

        texts().single { it.text == activity.getString(R.string.replace_saved_vehicle_data_anyway) }.performClick()

        assertTrue(BydVehicleFieldStore.load(context)!!.gearSupported)
        assertTrue(texts().any {
            it.text == activity.getString(R.string.vehicle_probe_failed, "probe belongs to another firmware")
        })
    }

    @Test fun anAdbCheckAloneDoesNotStartAVehicleValidation() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkResult = readableStatus()
            for (mayAsk in listOf(false, true)) invokeBoolean("checkAdbState", mayAsk)
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.checkResult = readableStatus()
        openSettings()
        shadowOf(Looper.getMainLooper()).idle()
        backend.runAll("diplay-byd13-auto-validate")

        // As when Dashboard song or Dashboard map is turned on.
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "checkAdbState",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
        backend.run("diplay-adb-state")

        assertEquals(1, backend.checkCalls)
        assertFalse(backend.tasks.any { it.first == "diplay-byd13-auto-validate" })
    }

    @Test fun aSwitchTurnedOnDuringAValidationIsValidatedAfterIt() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            invoke("validateSavedVehicleConfigurationAutomatically")
            for (enabled in listOf(true, false)) invokeBoolean("onVehicleDataSettingChanged", enabled)
            invoke("runPendingAutomaticVehicleValidation")
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "vehicleDataReconnectPending"))
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        backend.checkResult = readableStatus()
        openSettings()
        shadowOf(Looper.getMainLooper()).idle()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        // The first validation has not finished reading when wheel speed is turned on.
        vehicleSwitch(R.string.wheel_speed_for_tunnels).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        backend.runAll("diplay-byd13-auto-validate")

        assertEquals(2, backend.checkCalls)
    }

    @Test fun aValidationCancelledBeforeItsSecondCheckReadsNothingMore() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.READY)
            backend.onCheck = { error("Even the first validation read is forbidden for Geely") }
            invoke("validateSavedVehicleConfigurationAutomatically")
            ReflectionHelpers.callInstanceMethod<Any?>(activity, "cancelAutomaticVehicleValidationForUserOperation",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, false))
            invoke("runPendingAutomaticVehicleValidation")
            assertGeelyRefused(before)
            return
        }
        BydVehicleFieldStore.save(context, supportedCapabilities(BydVehicleFieldStore.firmwareKey()))
        BydOutputSettings.setWheelSpeedToIphone(context, true)
        backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.READY)
        openSettings()
        shadowOf(Looper.getMainLooper()).idle()
        // A user operation cancels the validation once its first check finds the fields unreadable.
        backend.onCheck = {
            backend.onCheck = null
            ReflectionHelpers.callInstanceMethod<Any?>(activity, "cancelAutomaticVehicleValidationForUserOperation",
                ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, false))
        }
        backend.run("diplay-byd13-auto-validate")

        assertEquals(1, backend.checkCalls)
        assertEquals(0, backend.probeCalls)
    }

    @Test fun oneTimeAdbApprovalExplainsAlwaysAllow() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkStateResult = BydAdbAccess.State.READY
            backend.probeResult = BydVehicleProbeOutcome(BydAdbAccess.State.NOT_APPROVED)
            invokeProbe()
            assertFalse(texts().any { it.text == activity.getString(R.string.adb_checking_may_ask) })
            assertFalse(texts().any { it.text.toString().contains(activity.getString(R.string.vehicle_probe_allowed_once)) })
            assertGeelyRefused(before)
            return
        }
        BydOutputSettings.setLegacyVehicleProbe(context, true)
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(BydAdbAccess.State.NOT_APPROVED)
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        invokeProbe()
        assertTrue(texts().any { it.text == activity.getString(R.string.adb_checking_may_ask) })
        backend.run("diplay-byd13-probe")

        assertEquals(2, backend.probeCalls)
        assertTrue(texts().any { it.text.toString().contains(activity.getString(R.string.vehicle_probe_allowed_once)) })
    }

    @Test fun aProbeRefusedRightAfterApprovalIsRetriedOnce() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkStateResult = BydAdbAccess.State.READY
            val refused = BydVehicleProbeOutcome(BydAdbAccess.State.NOT_APPROVED)
            backend.queuedProbeResults += refused
            backend.probeResult = BydVehicleProbeOutcome(BydAdbAccess.State.READY, supportedCapabilities())
            invokeProbe()
            assertEquals(listOf(refused), backend.queuedProbeResults.toList())
            assertGeelyRefused(before)
            return
        }
        BydOutputSettings.setLegacyVehicleProbe(context, true)
        backend.checkStateResult = BydAdbAccess.State.READY
        // adbd saves an "Always allow" key just after approving, so the probe's first connection can be early.
        backend.queuedProbeResults += BydVehicleProbeOutcome(BydAdbAccess.State.NOT_APPROVED)
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
        )
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        invokeProbe()
        backend.run("diplay-byd13-probe")

        assertEquals(2, backend.probeCalls)
        assertTrue(BydVehicleFieldStore.load(context)!!.motionSupported)
        assertFalse(texts().any { it.text.toString().contains(activity.getString(R.string.vehicle_probe_allowed_once)) })
    }

    @Test fun failedLegacySelectionKeepsTheDefaultMode() {
        if (!GeelyVendorPolicy.bydAdb) {
            openSettings()
            val before = vehiclePreferences()
            backend.checkStateResult = BydAdbAccess.State.ADB_OFF
            selectLegacyMode(true)
            assertFalse(BydOutputSettings.legacyVehicleProbe(context))
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        backend.checkStateResult = BydAdbAccess.State.ADB_OFF
        openSettings()
        ReflectionHelpers.setField(activity, "bydVehicleAdvancedExpanded", true)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")

        selectLegacyMode(true)
        backend.run("diplay-byd13-probe")

        assertFalse(BydOutputSettings.legacyVehicleProbe(context))
        assertEquals(null, BydVehicleFieldStore.load(context))
        assertTrue(texts().any { it.text.toString().contains(activity.getString(R.string.vehicle_data_mode_default)) })
    }

    @Test fun successfulLegacySelectionPersistsAndSwitchingBackKeepsTheProbe() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkStateResult = BydAdbAccess.State.READY
            backend.probeResult = BydVehicleProbeOutcome(BydAdbAccess.State.READY, supportedCapabilities())
            selectLegacyMode(true)
            selectLegacyMode(false)
            assertFalse(BydOutputSettings.legacyVehicleProbe(context))
            assertGeelyRefused(before)
            return
        }
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
        )
        openSettings()
        ReflectionHelpers.setField(activity, "bydVehicleAdvancedExpanded", true)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")

        selectLegacyMode(true)
        backend.run("diplay-byd13-probe")

        assertTrue(BydOutputSettings.legacyVehicleProbe(context))
        assertTrue(BydVehicleFieldStore.load(context)!!.motionSupported)

        selectLegacyMode(false)

        assertFalse(BydOutputSettings.legacyVehicleProbe(context))
        assertTrue(BydVehicleFieldStore.load(context)!!.motionSupported)
    }

    @Test fun defaultModeShowsUnreadableDataAndReconnectsOnlyOnceItIsReadable() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            connectCarPlay()
            ReflectionHelpers.setField(activity, "vehicleDataReconnectPending", true)
            backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.READY)
            invokeBoolean("checkAdbState", true)
            assertGeelyRefused(before)
            backend.checkResult = readableStatus()
            invokeBoolean("checkAdbState", true)
            invokeBoolean("checkAdbState", true)
            assertTrue(ReflectionHelpers.getField<Boolean>(activity, "vehicleDataReconnectPending"))
            assertGeelyRefused(before)
            return
        }
        backend.checkResult = BydAdbAccess.Status(BydAdbAccess.State.READY)
        openSettings()
        connectCarPlay()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        vehicleSwitch(R.string.car_battery_for_the_iphone).performClick()
        backend.run("diplay-adb-state")

        assertTrue(BydOutputSettings.batteryToIphone(context))
        assertTrue(texts().any { it.text == activity.getString(R.string.adb_battery_unreadable) })
        assertEquals(0, reconnects)

        // Approval or a fix on the car later: the next check applies the waiting switch once.
        backend.checkResult = readableStatus()
        texts().single { it.text == activity.getString(R.string.check_adb_access) }.performClick()
        backend.run("diplay-adb-state")

        assertTrue(texts().any { it.text == activity.getString(R.string.adb_battery_reading, 75, 450) })
        assertEquals(1, reconnects)

        texts().single { it.text == activity.getString(R.string.check_adb_access) }.performClick()
        backend.run("diplay-adb-state")
        assertEquals(1, reconnects)
    }

    @Test fun switchingToLegacyReconnectsOnlyWhenAVehicleDataSwitchIsOn() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            connectCarPlay()
            selectLegacyMode(true)
            selectLegacyMode(false)
            for (enabled in listOf(true, false)) invokeBoolean("onVehicleDataSettingChanged", enabled)
            selectLegacyMode(true)
            assertGeelyRefused(before)
            return
        }
        backend.checkStateResult = BydAdbAccess.State.READY
        backend.probeResult = BydVehicleProbeOutcome(
            BydAdbAccess.State.READY,
            supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
        )
        openSettings()
        connectCarPlay()

        selectLegacyMode(true)
        backend.run("diplay-byd13-probe")
        assertTrue(BydOutputSettings.legacyVehicleProbe(context))
        assertEquals(0, reconnects)

        selectLegacyMode(false)
        BydOutputSettings.setWheelSpeedToIphone(context, true)
        selectLegacyMode(true)
        assertEquals(1, reconnects)
    }

    @Test fun modeChoiceIsDisabledWhileAnAdbCheckRuns() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            backend.checkResult = readableStatus()
            invokeBoolean("checkAdbState", true)
            assertFalse(ReflectionHelpers.getField<Boolean>(activity, "adbCheckInProgress"))
            assertFalse(texts().any { it.text.startsWith(activity.getString(R.string.vehicle_data_mode) + " · ") })
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        backend.checkResult = readableStatus()
        openSettings()
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        texts().single { it.text == activity.getString(R.string.check_adb_access) }.performClick()
        assertFalse(modeChoice().isEnabled)

        backend.run("diplay-adb-state")
        assertTrue(modeChoice().isEnabled)
    }

    @Test fun passingAutomaticValidationClearsAnOldProbeError() {
        if (!GeelyVendorPolicy.bydAdb) {
            val before = openForgedGeelySettings()
            val old = BydVehicleProbeOutcome(BydAdbAccess.State.READY, error = "old error")
            ReflectionHelpers.setField(activity, "vehicleProbeOutcome", old)
            backend.checkResult = readableStatus()
            invoke("validateSavedVehicleConfigurationAutomatically")
            invoke("render")
            assertEquals(old, ReflectionHelpers.getField<BydVehicleProbeOutcome?>(activity, "vehicleProbeOutcome"))
            assertFalse(texts().any { it.text.toString().contains("old error") })
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        BydVehicleFieldStore.save(context, supportedCapabilities(currentKey))
        BydOutputSettings.setVideoWhileParked(context, true)
        backend.checkResult = readableStatus()
        openSettings()
        ReflectionHelpers.setField(
            activity,
            "vehicleProbeOutcome",
            BydVehicleProbeOutcome(BydAdbAccess.State.READY, error = "old error"),
        )
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        shadowOf(Looper.getMainLooper()).idle()
        backend.run("diplay-byd13-auto-validate")

        assertEquals(null, ReflectionHelpers.getField<BydVehicleProbeOutcome?>(activity, "vehicleProbeOutcome"))
        assertFalse(texts().any { it.text.toString().contains("old error") })
    }

    @Test fun anOlderProbeOutcomeCannotHideANewerSavedSnapshot() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey()).copy(detectedAtMillis = 200)
            val before = openForgedGeelySettings(saved)
            ReflectionHelpers.setField(activity, "vehicleProbeOutcome", BydVehicleProbeOutcome(BydAdbAccess.State.READY,
                supportedCapabilities(saved.firmwareKey, without = setOf(BydVehicleField.GEAR)).copy(detectedAtMillis = 100)))
            invoke("render")
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        val saved = supportedCapabilities(currentKey).copy(detectedAtMillis = 200)
        val older = supportedCapabilities(
            currentKey,
            without = setOf(BydVehicleField.GEAR),
        ).copy(detectedAtMillis = 100)
        BydVehicleFieldStore.save(context, saved)
        openSettings()
        ReflectionHelpers.setField(
            activity,
            "vehicleProbeOutcome",
            BydVehicleProbeOutcome(BydAdbAccess.State.READY, older),
        )
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.video_while_parked) })
    }

    @Test fun theSavedSnapshotIsShownWhateverTheClockSays() {
        if (!GeelyVendorPolicy.bydAdb) {
            val saved = supportedCapabilities(BydVehicleFieldStore.firmwareKey()).copy(detectedAtMillis = 100)
            val before = openForgedGeelySettings(saved)
            ReflectionHelpers.setField(activity, "vehicleProbeOutcome", BydVehicleProbeOutcome(BydAdbAccess.State.READY,
                supportedCapabilities(saved.firmwareKey, without = setOf(BydVehicleField.GEAR)).copy(detectedAtMillis = 200)))
            invoke("render")
            assertGeelyVehiclePage()
            assertGeelyRefused(before)
            return
        }
        val currentKey = BydVehicleFieldStore.firmwareKey()
        BydVehicleFieldStore.save(context, supportedCapabilities(currentKey).copy(detectedAtMillis = 100))
        openSettings()
        // An older result stamped later, as after the clock was set back.
        ReflectionHelpers.setField(
            activity,
            "vehicleProbeOutcome",
            BydVehicleProbeOutcome(
                BydAdbAccess.State.READY,
                supportedCapabilities(currentKey, without = setOf(BydVehicleField.GEAR)).copy(detectedAtMillis = 200),
            ),
        )
        texts().single { it.text == activity.getString(R.string.advanced_vehicle_data) }.performClick()

        assertTrue(switches().any { it.contentDescription == activity.getString(R.string.video_while_parked) })
    }

    @Test fun samePageRenderKeepsScrollAndPageChangeStartsAtTop() {
        openSettings()
        val first = layoutRoot()
        first.scrollTo(0, 400)
        val savedY = first.scrollY
        assertTrue(savedY > 0)

        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        val second = layoutRoot()
        assertEquals(savedY, second.scrollY)

        ReflectionHelpers.setField(activity, "settingsCategory", SettingsCategory.ABOUT)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        val third = layoutRoot()
        assertEquals(0, third.scrollY)
    }

    @Test fun rendersBeforeTheNextLayoutKeepTheScroll() {
        openSettings()
        val first = layoutRoot()
        first.scrollTo(0, 400)
        val savedY = first.scrollY
        assertTrue(savedY > 0)

        // A result arriving behind CarPlay: a stopped window dispatches pre-draw but skips layout.
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        activity.window.decorView.viewTreeObserver.dispatchOnPreDraw()
        // Back from CarPlay: onNewIntent and onResume both render before the next frame.
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "render")

        assertEquals(savedY, layoutRoot().scrollY)
    }

    /** Forge an upstream snapshot directly: the public Geely setters intentionally refuse it. */
    private fun forgeVehiclePreferences(capabilities: BydVehicleCapabilities) {
        context.getSharedPreferences("diplay_byd_outputs", 0).edit()
            .putBoolean("legacy_vehicle_probe", true).putBoolean("battery_to_iphone", true)
            .putBoolean("wheel_speed_to_iphone", true).putBoolean("video_while_parked", true)
            .putBoolean("cluster_song", true).putBoolean("navigation_enabled", true).commit()
        val fields = context.getSharedPreferences("diplay_byd_vehicle_fields", 0).edit().clear()
            .putInt("schema", 1).putString("firmware", capabilities.firmwareKey)
            .putBoolean("catalog", capabilities.catalogAvailable)
            .putLong("detected_at", capabilities.detectedAtMillis)
        for (field in BydVehicleField.entries) {
            val name = field.name.lowercase()
            val result = capabilities.result(field)
            fields.putBoolean("${name}_supported", result.supported)
            result.address?.let {
                fields.putInt("${name}_device", it.device).putInt("${name}_fid", it.fid)
                    .putString("${name}_source", it.source.name)
            }
        }
        fields.commit()
        BydVehicleFieldStore.clearMemoryForTests()
    }

    private fun openForgedGeelySettings(
        capabilities: BydVehicleCapabilities = supportedCapabilities(BydVehicleFieldStore.firmwareKey()),
    ): Map<String, Map<String, *>> {
        forgeVehiclePreferences(capabilities)
        val before = vehiclePreferences()
        openSettings()
        assertEquals(before, vehiclePreferences())
        return before
    }

    private fun vehiclePreferences(): Map<String, Map<String, *>> = listOf(
        "diplay_byd_outputs", "diplay_byd_vehicle_fields", "diplay_car_hotspot",
    ).associateWith { context.getSharedPreferences(it, 0).all.toMap() }

    private fun assertGeelyVehiclePage() {
        assertTrue(texts().any { it.text == activity.getString(R.string.byd_integration_disabled_geely) })
        for (id in listOf(R.string.advanced_vehicle_data, R.string.hide_advanced_vehicle_data,
            R.string.check_adb_access, R.string.probe_vehicle_data_again)) {
            assertFalse(texts().any { it.text == activity.getString(id) })
        }
        for (id in listOf(R.string.car_battery_for_the_iphone, R.string.wheel_speed_for_tunnels,
            R.string.video_while_parked, R.string.cluster_song, R.string.bt_suspend_during_carplay)) {
            assertFalse(switches().any { it.contentDescription == activity.getString(id) })
        }
    }

    private fun assertGeelyRefused(before: Map<String, Map<String, *>>) {
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(GeelyVendorPolicy.bydAdb)
        assertTrue(backend.tasks.isEmpty())
        assertEquals(0, backend.executeCalls)
        assertEquals(0, backend.checkCalls)
        assertEquals(0, backend.checkStateCalls)
        assertEquals(0, backend.probeCalls)
        assertEquals(0, reconnects)
        assertEquals(before, vehiclePreferences())
        assertEquals(permissionsBefore, UsbPermissionSetup.snapshot(context))
        assertEquals(hotspotPermissionsBefore,
            CarHotspotSetup.Permission.entries.associateWith { it.granted(context) })
        assertFalse(BydOutputSettings.legacyVehicleProbe(context))
        assertFalse(BydOutputSettings.batteryToIphone(context))
        assertFalse(BydOutputSettings.wheelSpeedToIphone(context))
        assertFalse(BydOutputSettings.videoWhileParked(context))
        assertNull(BydVehicleFieldStore.load(context))
        val provider = Class.forName("com.shilapi.xcertplay.hud.BydBatteryStatus")
            .getField("INSTANCE").get(null) as VehicleStatusProvider
        assertNull(provider.snapshot())
        controller?.get()?.let {
            assertNull(shadowOf(it).nextStartedActivity)
            assertFalse(ReflectionHelpers.getField<Boolean>(it, "vehicleProbeAuthorizationInProgress"))
            assertFalse(ReflectionHelpers.getField<Boolean>(it, "vehicleProbeInProgress"))
            assertFalse(ReflectionHelpers.getField<Boolean>(it, "adbCheckInProgress"))
            assertFalse(ReflectionHelpers.getField<Boolean>(it, "automaticVehicleValidationInProgress"))
        }
    }

    private fun injectReplacement(expected: BydVehicleCapabilities, candidate: BydVehicleCapabilities) {
        ReflectionHelpers.setField(activity, "pendingVehicleReplacementExpected", expected)
        ReflectionHelpers.setField(activity, "pendingVehicleReplacement", candidate)
        ReflectionHelpers.setField(activity, "pendingVehicleLostFields", setOf(BydVehicleField.GEAR))
    }

    private fun invoke(method: String) = ReflectionHelpers.callInstanceMethod<Unit>(activity, method)

    private fun invokeBoolean(method: String, value: Boolean) = ReflectionHelpers.callInstanceMethod<Unit>(activity, method,
        ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, value))

    private fun openSettings() {
        controller = Robolectric.buildActivity(
            DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "settings"),
        ).setup()
        openCategory(R.string.settings_advanced)
    }

    private fun openCategory(title: Int) {
        val description = activity.getString(R.string.settings_open_category, activity.getString(title))
        var target = descendants(activity.window.decorView).firstOrNull { it.contentDescription == description }
        if (target == null) {
            activity.onBackPressedDispatcher.onBackPressed()
            target = descendants(activity.window.decorView).first { it.contentDescription == description }
        }
        target.performClick()
    }

    private fun switches(): Sequence<Switch> = descendants(activity.window.decorView).filterIsInstance<Switch>()

    private fun texts(): Sequence<TextView> = descendants(activity.window.decorView).filterIsInstance<TextView>()

    private fun vehicleSwitch(title: Int): Switch = switches()
        .single { it.contentDescription == activity.getString(title) }

    private fun invokeProbe() {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "probeVehicleData",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
    }

    /** Three-finger gesture or Back to DiPlay: the settings page comes back through a new intent. */
    private fun returnFromCarPlay() {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onNewIntent",
            ReflectionHelpers.ClassParameter.from(
                Intent::class.java,
                Intent(context, DiPlayActivity::class.java).putExtra("page", "settings"),
            ))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun selectLegacyMode(enabled: Boolean) {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "selectVehicleDataMode",
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, enabled))
    }

    private fun modeChoice(): TextView = texts()
        .single { it.text.startsWith(activity.getString(R.string.vehicle_data_mode) + " · ") }

    /** As in LocationReportingSettingsTest: a connected host without transports; counts reconnects. */
    private fun connectCarPlay() {
        AirPlayPersistence.saveWirelessEnabled(context, false)
        ReflectionHelpers.setField(activity, "setupError", null)
        val stop: ((() -> Unit) -> Unit) = { completion ->
            reconnects++
            completion()
        }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)
    }

    private fun readableStatus() = BydAdbAccess.Status(
        state = BydAdbAccess.State.READY,
        batteryPercent = 75.0,
        rangeKm = 450,
        speedKmh = 0.0,
        gear = VehicleGear.PARK,
    )

    private fun layoutRoot(): ScrollView {
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, 1080, 600)
        return descendants(decor).filterIsInstance<ScrollView>().first()
    }

    private fun supportedCapabilities(
        firmwareKey: String = "test",
        without: Set<BydVehicleField> = emptySet(),
    ) = BydVehicleCapabilities(
        fields = BydVehicleField.entries.associateWith { field ->
            val value = when (field) {
                BydVehicleField.SPEED -> 0.0
                BydVehicleField.GEAR -> 1.0
                BydVehicleField.SOC -> 75.0
                BydVehicleField.RANGE -> 450.0
                BydVehicleField.REMAINING_KWH -> 60.0
                BydVehicleField.BMS_STATE -> 0.0
            }
            if (field in without) BydFieldProbeResult(field, supported = false, address = null)
            else BydFieldProbeResult(
                    field,
                    supported = true,
                    address = BydReadAddress(1000, field.ordinal + 1,
                        if (field in setOf(BydVehicleField.SPEED, BydVehicleField.SOC, BydVehicleField.REMAINING_KWH)) 7 else 5,
                        BydFieldSource.FIRMWARE),
                    value = value,
                )
        },
        catalogAvailable = true,
        firmwareKey = firmwareKey,
    )

    private class FakeVehicleSettingsBackend : BydVehicleSettingsBackend {
        var checkResult = BydAdbAccess.Status(BydAdbAccess.State.ADB_OFF)
        var checkStateResult = BydAdbAccess.State.ADB_OFF
        var probeResult = BydVehicleProbeOutcome(BydAdbAccess.State.ADB_OFF)
        /** Returned by the next probes before [probeResult]. */
        val queuedProbeResults = ArrayDeque<BydVehicleProbeOutcome>()
        var onCheck: (() -> Unit)? = null
        var onProbe: (() -> Unit)? = null
        var executeCalls = 0
        var checkStateCalls = 0
        var checkCalls = 0
        var probeCalls = 0
        val tasks = mutableListOf<Pair<String, () -> Unit>>()

        override fun check(context: android.content.Context, mayAsk: Boolean): BydAdbAccess.Status {
            checkCalls++
            onCheck?.invoke()
            return checkResult
        }

        override fun checkState(context: android.content.Context, mayAsk: Boolean): BydAdbAccess.State {
            checkStateCalls++
            return checkStateResult
        }

        override fun probe(context: android.content.Context, persist: Boolean): BydVehicleProbeOutcome {
            probeCalls++
            onProbe?.invoke()
            return queuedProbeResults.removeFirstOrNull() ?: probeResult
        }

        override fun execute(name: String, block: () -> Unit) {
            executeCalls++
            tasks += name to block
        }

        fun run(name: String) {
            val index = tasks.indexOfFirst { it.first == name }
            check(index >= 0) { "No queued task named $name; queued=${tasks.map { it.first }}" }
            tasks.removeAt(index).second.invoke()
            shadowOf(Looper.getMainLooper()).idle()
        }

        fun runAll(name: String) {
            while (tasks.any { it.first == name }) run(name)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
