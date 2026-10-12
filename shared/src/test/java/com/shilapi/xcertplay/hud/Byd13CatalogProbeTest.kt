package com.shilapi.xcertplay.hud

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class Byd13CatalogProbeTest {
    @Test fun parsesController13FirmwareSymbolsAndDevices() {
        val catalog = BydFirmwareCatalog.parse(
            """
            ${Byd13CatalogProbeMain.HEADER}
            BYDAutoConstants.BYDAUTO_DEVICE_SPEED=1013
            BYDAutoConstants.BYDAUTO_DEVICE_GEARBOX=1011
            BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
            BYDAutoConstants.BYDAUTO_DEVICE_POWER=1005
            BYDAutoConstants.BYDAUTO_DEVICE_CHARGING=1009
            Speed.SPEED_AUTO_SPEED=-1176502256
            Gearbox.GEARBOX_AUTO_MODE_TYPE=555745336
            Statistic.STATISTIC_ELEC_PERCENTAGE=1033543720
            Statistic.STATISTIC_ELEC_DRIVING_RANGE=1033543766
            Power.POWER_BATTERY_REMAIN_ELECTRICITY=1320165408
            Charging.CHARGING_BATTERRY_DEVICE_STATE=1231032336
            """.trimIndent()
        )!!

        assertEquals(-1176502256, catalog.fid(BydVehicleField.SPEED))
        assertEquals(1033543720, catalog.fid(BydVehicleField.SOC))
        assertEquals(1231032336, catalog.fid(BydVehicleField.BMS_STATE))
        assertEquals(1013, catalog.device(BydVehicleField.SPEED))
        assertEquals(1009, catalog.device(BydVehicleField.BMS_STATE))
    }

    @Test fun rejectsOutputWithoutProbeHeader() {
        assertEquals(null, BydFirmwareCatalog.parse("Speed.SPEED_AUTO_SPEED=-1176502256"))
    }

    @Test fun geelyCannotRunCatalogOrFallbackShellProbes() {
        for (catalog in listOf(null, "", Byd13CatalogProbeMain.HEADER)) {
            for (sdk in listOf(25, 29, 33)) {
                val batch = BydVehicleCapabilityProbe.probeFields(catalog, sdk,
                    { error("No BYD probe is authorized") }, "geely-test")
                assertFalse(batch.complete)
                assertEquals(0, batch.transportReplies)
                assertEquals(0, batch.repliedFields)
                assertTrue(batch.capabilities.fields.isEmpty())
                assertFalse(batch.capabilities.catalogAvailable)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydVehicleFieldStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("diplay_byd_vehicle_fields", Context.MODE_PRIVATE)

    @Before fun setUp() { prefs.edit().clear().commit(); BydVehicleFieldStore.clearMemoryForTests() }
    @After fun tearDown() = setUp()

    @Test fun forgedPersistedCapabilitiesAreNeverLoaded() {
        prefs.edit().putInt("schema", 1).putString("firmware", BydVehicleFieldStore.firmwareKey())
            .putBoolean("catalog", true).putBoolean("gear_supported", true)
            .putInt("gear_device", 1011).putInt("gear_fid", 555745336).commit()
        assertNull(BydVehicleFieldStore.load(app))
        assertTrue("The disabled product must not erase a historical journal", prefs.getBoolean("gear_supported", false))
    }

    @Test fun forgedInMemoryCapabilitiesAreNeverReturned() {
        val field = BydVehicleFieldStore.javaClass.getDeclaredField("memory").apply { isAccessible = true }
        try {
            field.set(BydVehicleFieldStore, capabilities())
            assertNull(BydVehicleFieldStore.load(app))
        } finally { BydVehicleFieldStore.clearMemoryForTests() }
    }

    @Test fun saveAndReplacementDoNotChangePreferencesOrAdvertiseSuccess() {
        prefs.edit().putString("marker", "retain").commit()
        val before = prefs.all
        val candidate = capabilities()
        BydVehicleFieldStore.save(app, candidate)
        assertFalse(BydVehicleFieldStore.replaceAutomatically(app, null, candidate).saved)
        assertFalse(BydVehicleFieldStore.replaceAnyway(app, null, candidate))
        assertEquals(before, prefs.all)
        assertNull(BydVehicleFieldStore.load(app))
    }

    @Test fun evenACompleteForgedProbeCannotPublishOrPersist() {
        val batch = BydVehicleCapabilityProbe.ProbeBatch(capabilities(), 6, 6, 0)
        for (persist in listOf(false, true)) {
            val outcome = BydVehicleCapabilityProbe.finishProbe(app, BydAdbAccess.State.READY, batch, persist)
            assertEquals(BydAdbAccess.State.DISABLED_FOR_VEHICLE, outcome.access)
            assertNull(outcome.capabilities)
        }
        assertTrue(prefs.all.isEmpty())
        assertNull(BydBatteryStatus.snapshot())
    }

    private fun capabilities() = BydVehicleCapabilities(
        BydVehicleField.entries.associateWith { field ->
            BydFieldProbeResult(field, true, BydVehicleFieldStore.defaultAddress(field), 1.0)
        }, true, BydVehicleFieldStore.firmwareKey(),
    )
}
