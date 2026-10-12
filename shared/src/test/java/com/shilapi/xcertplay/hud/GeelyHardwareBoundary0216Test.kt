package com.shilapi.xcertplay.hud

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.EvChargingConnectors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GeelyHardwareBoundary0216Test {
    private val rejectingContext = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = error("vendor context accessed")
        override fun getPackageManager(): PackageManager = error("vendor packages probed")
        override fun getPackageName(): String = error("vendor package checked")
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = error("vendor preferences or keys read")
        override fun getSystemService(name: String): Any = error("vendor service accessed")
    }

    @Test fun directNavigationBridgesDoNotProbeBindWriteOrStartWorkers() {
        val route = Iap2Frame(BydHudRouteState.ROUTE_GUIDANCE_UPDATE, byteArrayOf())
        repeat(3) {
            BydHudBridge.initialize(rejectingContext)
            BydHudBridge.onFrame(route)
            BydHudBridge.clearNow()
            BydClusterBridge.initialize(rejectingContext)
            BydClusterBridge.onFrame(route)
            BydClusterBridge.setMapShown(true)
            BydClusterBridge.prepareProjectionDisplay(rejectingContext)
            BydClusterBridge.clear()
            BydStandaloneNavigationBridge.initialize(rejectingContext)
            BydStandaloneNavigationBridge.onFrame(route)
            BydStandaloneNavigationBridge.clear()
            BydFactoryNavigationOutput(rejectingContext).also { it.update(1, 0, 500); it.clear() }
            assertFalse(BydStandaloneHudOutput.available(rejectingContext))
            assertNull(BydStandaloneHudOutput.create(rejectingContext))
            assertTrue(BydStandaloneHudOutput.diagnostics(rejectingContext).contains("disabled"))
        }
        assertLazyNotInitialized(BydHudBridge, "callbacks")
        for (bridge in listOf(BydHudBridge, BydClusterBridge, BydStandaloneNavigationBridge)) {
            val field = bridge.javaClass.getDeclaredField("context").apply { isAccessible = true }
            assertNull(field.get(bridge))
            invokeNoArgs(bridge, "tick")
        }
    }

    @Test fun newClusterApisDoNotInvokeCallbacksOrTouchRecoveryJournals() {
        repeat(3) {
            BydNavigationOutputs.setClusterNaviModeListener { error("mode callback invoked") }
            BydNavigationOutputs.setTurnOverlayListener { error("overlay callback invoked") }
            BydNavigationOutputs.refreshTurnOverlay()
            BydNavigationOutputs.dashboardNote("forged", 1)
            BydNavigationOutputs.clusterSongOnChangeChanged()
            BydNavigationOutputs.carPlayCallsChanged(true)
            BydNavigationOutputs.carPlaySessionStarted()
            assertNull(BydNavigationOutputs.carPlayCall())
            assertNull(BydNavigationOutputs.clusterNaviMode())
            BydNavigationOutputs.endNow(preserveTurnOverlay = true)
            assertFalse(BydOemClusterNavi.applicable(rejectingContext))
            assertFalse(BydOemClusterNavi.holdForLaunch(rejectingContext, "forged") { error("lease callback invoked") })
            BydOemClusterNavi.release(rejectingContext)
            BydOemClusterNavi.restoreIfNeeded(rejectingContext)
            BydDiLink3ClusterOutput.restoreIfNeeded(rejectingContext)
            BydDiLink3ClusterOutput.setDesired(rejectingContext, true, true)
            BydDiLink3ClusterOutput.prepareDisplay(rejectingContext) { error("display callback invoked") }
            assertFalse(BydDiLink3ClusterOutput.adbClusterRouteSelected(rejectingContext))
        }
        for (worker in listOf(BydOemClusterNavi, BydDiLink3ClusterOutput)) {
            assertLazyNotInitialized(worker, "shell")
            assertLazyNotInitialized(worker, "worker")
        }
        BydClusterMapPause.streamControl = { error("stream callback invoked") }
        BydClusterMapPause.onNaviMode = { error("mode callback invoked") }
        BydClusterMapPause.clusterMapShown = true
        try { invokeNoArgs(BydClusterMapPause, "tick") } finally {
            BydClusterMapPause.streamControl = null
            BydClusterMapPause.onNaviMode = null
            BydClusterMapPause.clusterMapShown = false
        }
        assertLazyNotInitialized(BydClusterMapPause, "shell")
    }

    @Test fun disabledSettingsShortCircuitAllReadersAndSetters() {
        for (getter in booleanSettings) assertFalse(getter(rejectingContext))
        BydOutputSettings.setEnabled(rejectingContext, true)
        BydOutputSettings.setClusterStreamPause(rejectingContext, true)
        BydOutputSettings.setBatteryToIphone(rejectingContext, true)
        BydOutputSettings.setWheelSpeedToIphone(rejectingContext, true)
        BydOutputSettings.setVideoWhileParked(rejectingContext, true)
        BydOutputSettings.setClusterSong(rejectingContext, true)
        BydOutputSettings.setCarPlayCalls(rejectingContext, true)
        BydOutputSettings.setCarPlayCallControls(rejectingContext, true)
        BydOutputSettings.setClusterSongOnChange(rejectingContext, true)
        BydOutputSettings.setHudSong(rejectingContext, true)
        BydOutputSettings.setLegacyVehicleProbe(rejectingContext, true)
        BydOutputSettings.setOemClusterHold(rejectingContext, BydOemClusterHold.PACKAGE)
        BydOutputSettings.setLowChargePercent(rejectingContext, 99)
        BydOutputSettings.setChargingConnectors(rejectingContext, EvChargingConnectors.CCS2_TYPE2)
        assertEquals(BydOemClusterHold.OFF, BydOutputSettings.oemClusterHold(rejectingContext))
        assertEquals(BydOutputSettings.DEFAULT_LOW_CHARGE_PERCENT, BydOutputSettings.lowChargePercent(rejectingContext))
        assertEquals(EvChargingConnectors.CCS2_TYPE2, BydOutputSettings.chargingConnectors(rejectingContext))
    }

    @Test fun forgedPreferencesCannotReenableAnyVendorCapability() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE)
        val keys = listOf("navigation_enabled", "cluster_stream_pause", "battery_to_iphone", "wheel_speed_to_iphone",
            "video_while_parked", "cluster_song", "carplay_calls", "carplay_call_controls_experimental",
            "cluster_song_on_change", "hud_song", "legacy_vehicle_probe", "oem_cluster_freeze")
        preferences.edit().also { edit -> keys.forEach { edit.putBoolean(it, true) } }
            .putString("oem_cluster_hold", "PACKAGE").commit()
        try {
            for (getter in booleanSettings) assertFalse(getter(context))
            assertEquals(BydOemClusterHold.OFF, BydOutputSettings.oemClusterHold(context))
            assertNull(BydNavigationOutputs.parked(context))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun tyreAndBatteryReadsCannotReachShellOrPublishStaleData() {
        assertNull(BydTyres.read { error("tyre shell invoked") })
        assertNull(BydBattery.read { error("battery shell invoked") })
        assertNull(BydBattery.read(rejectingContext) { error("battery shell invoked") })
        assertNull(BydBatteryStatus.read(rejectingContext) { error("battery reader invoked") })
        val reading = BydBatteryReading(50.0, 200, 25.0, false)
        BydBatteryStatus.publishProbeReading(rejectingContext, reading)
        BydBatteryStatus.start(rejectingContext)
        invokeNoArgs(BydBatteryStatus, "poll")
        assertNull(BydBatteryStatus.snapshot())
        BydTyreStatus.start(rejectingContext)
        BydTyreStatus.stop()
        val latest = BydTyreStatus.javaClass.getDeclaredField("latest").apply { isAccessible = true }
        try {
            latest.set(BydTyreStatus, BydTyreReading(List(4) { 250 }, List(4) { 20 }))
            assertNull(BydTyreStatus.latest())
        } finally { latest.set(BydTyreStatus, null) }
        for (source in listOf(BydBatteryStatus, BydTyreStatus)) {
            assertLazyNotInitialized(source, "shell")
            assertLazyNotInitialized(source, "executor")
        }
    }

    @Test fun diagnosticProbesCannotAdvertiseOrPersistHardwareCapabilities() {
        val disabled = BydAdbAccess.State.DISABLED_FOR_VEHICLE
        assertEquals(disabled, BydAdbAccess.checkState(rejectingContext, true))
        val status = BydAdbAccess.check(rejectingContext, true)
        assertFalse(status.dashboardShowsMap)
        assertNull(status.speedKmh)
        assertNull(status.gear)
        assertEquals(disabled, BydVehicleCapabilityProbe.probe(rejectingContext).access)
        val batch = BydVehicleCapabilityProbe.probeFields("forged", 29, { error("probe shell invoked") }, "forged")
        assertFalse(batch.complete)
        assertEquals(0, batch.transportReplies)
        assertTrue(batch.capabilities.fields.isEmpty())
        assertEquals(disabled, BydVehicleCapabilityProbe.finishProbe(rejectingContext, BydAdbAccess.State.READY, batch, true).access)
        val forged = BydVehicleCapabilities(BydVehicleField.entries.associateWith { field ->
            BydFieldProbeResult(field, true, BydVehicleFieldStore.defaultAddress(field), 1.0)
        }, true, "forged")
        BydVehicleFieldStore.save(rejectingContext, forged)
        assertNull(BydVehicleFieldStore.load(rejectingContext))
        assertFalse(BydVehicleFieldStore.replaceAutomatically(rejectingContext, null, forged).saved)
        assertFalse(BydVehicleFieldStore.replaceAnyway(rejectingContext, null, forged))
        BydVehicleCapabilityProbe.publishBatteryReading(rejectingContext, forged)
        Byd13CatalogProbeMain.main(emptyArray())
    }

    private fun assertLazyNotInitialized(target: Any, name: String) {
        val field = target.javaClass.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
        assertFalse("${target.javaClass.simpleName} $name initialized", (field.get(target) as Lazy<*>).isInitialized())
    }
    private fun invokeNoArgs(target: Any, name: String) {
        target.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(target)
    }
    private val booleanSettings = listOf<(Context) -> Boolean>(
        BydOutputSettings::enabled, BydOutputSettings::clusterStreamPause,
        BydOutputSettings::batteryToIphone, BydOutputSettings::batteryToIphoneActive,
        BydOutputSettings::wheelSpeedToIphone, BydOutputSettings::wheelSpeedToIphoneActive,
        BydOutputSettings::videoWhileParked, BydOutputSettings::videoWhileParkedActive,
        BydOutputSettings::clusterSong, BydOutputSettings::clusterSongOnChange, BydOutputSettings::hudSong,
        BydOutputSettings::carPlayCalls, BydOutputSettings::carPlayCallControls,
        BydOutputSettings::legacyVehicleProbe, BydOutputSettings::standaloneHudAvailable,
        BydOutputSettings::navigationAvailable, BydOutputSettings::available,
    )
}
