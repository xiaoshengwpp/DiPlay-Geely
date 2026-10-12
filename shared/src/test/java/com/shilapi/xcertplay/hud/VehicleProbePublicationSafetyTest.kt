package com.shilapi.xcertplay.hud

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class VehicleProbePublicationSafetyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        context.getSharedPreferences("diplay_byd_vehicle_fields", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("diplay_byd_outputs", Context.MODE_PRIVATE).edit().clear().commit()
        BydVehicleFieldStore.clearMemoryForTests()
        BydBatteryStatus.read(context) { null }
    }

    @After fun cleanup() = reset()

    @Test fun forgedCandidatesCannotPublishAnyBatteryReading() {
        for (hasGear in listOf(false, true)) {
            val candidate = capabilities(90.0, hasGear)
            val outcome = BydVehicleCapabilityProbe.finishProbe(context, BydAdbAccess.State.READY,
                BydVehicleCapabilityProbe.ProbeBatch(candidate, 6, 6, 0), persist = false)
            assertEquals(BydAdbAccess.State.DISABLED_FOR_VEHICLE, outcome.access)
            assertFalse(BydVehicleFieldStore.replaceAutomatically(context, null, candidate).saved)
            assertFalse(BydVehicleFieldStore.replaceAnyway(context, null, candidate))
            BydBatteryStatus.publishProbeReading(context, BydBatteryReading(90.0, 150, 20.0, false))
            assertNull(BydVehicleFieldStore.load(context))
            assertNull(BydBatteryStatus.snapshot())
        }
    }

    private fun capabilities(percent: Double, hasGear: Boolean): BydVehicleCapabilities = BydVehicleCapabilities(
        fields = BydVehicleField.entries.associateWith { field ->
            val supported = field != BydVehicleField.GEAR || hasGear
            BydFieldProbeResult(field, supported,
                if (supported) BydVehicleFieldStore.defaultAddress(field) else null,
                when (field) {
                    BydVehicleField.SOC -> percent
                    BydVehicleField.RANGE -> 150.0
                    BydVehicleField.REMAINING_KWH -> 20.0
                    else -> 1.0
                })
        }, catalogAvailable = true, firmwareKey = BydVehicleFieldStore.firmwareKey())
}
