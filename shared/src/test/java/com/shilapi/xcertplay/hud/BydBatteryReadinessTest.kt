package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.withVehicleStatusFrom
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydBatteryReadinessTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val reading = BydBatteryReading(25.0, 150, 25.1, false)
    private val identification = Iap2IdentificationConfig(
        name = "test", modelIdentifier = "test", manufacturer = "test", serialNumber = "test",
        firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3,
        vehicleStatusEnabled = true,
    )

    @Test
    fun geelyProfileDoesNotReadOrPublishBydBatteryStatus() {
        val unavailable = BydAdbAccess.readStatus(context) { error("No BYD shell read is allowed") }
        assertEquals(BydAdbAccess.State.DISABLED_FOR_VEHICLE, unavailable.state)
        assertNull(unavailable.batteryPercent)
        BydBatteryStatus.accept(context, reading)
        BydBatteryStatus.start(context)
        assertNull(BydBatteryStatus.snapshot())
        assertFalse(identification.withVehicleStatusFrom(BydBatteryStatus).vehicleStatusEnabled)
    }

    @Test
    fun staleReadingBecomesUsableAgainOnlyAfterFreshDataIsPublished() {
        var clock = 0L
        val cache = BydBatteryCache { clock }
        cache.accept(reading)
        clock += 181_000
        assertNull(cache.snapshot(20))
        cache.accept(BydBatteryReading(10.0, 60, 10.0, false))
        val fresh = cache.snapshot(20)!!
        assertEquals(10.0, fresh.batteryPercent, 0.001)
        assertEquals(100_400L, fresh.maxChargeWh)
    }

}
