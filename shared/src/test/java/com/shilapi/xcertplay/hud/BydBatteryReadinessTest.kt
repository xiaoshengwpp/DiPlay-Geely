package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.VehicleGear
import com.shilapi.xcertplay.transport.withVehicleStatusFrom
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BydBatteryReadinessTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val reading = BydBatteryReading(25.0, 150, 25.1, false, BydBatteryProtocol.CANFD)
    private val identification = Iap2IdentificationConfig(
        name = "test", modelIdentifier = "test", manufacturer = "test", serialNumber = "test",
        firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3,
        vehicleStatusEnabled = true,
    )

    @Test fun geelySettingsCheckCannotPublishBatteryOrMotionReadiness() {
        val status = BydAdbAccess.readStatus(context) { error("No BYD shell read is authorized") }
        assertEquals(BydAdbAccess.State.DISABLED_FOR_VEHICLE, status.state)
        assertNull(status.batteryPercent)
        assertNull(status.speedKmh)
        assertNull(status.gear)
        assertFalse(status.dashboardShowsMap)
        assertFalse(identification.withVehicleStatusFrom(BydBatteryStatus).vehicleStatusEnabled)
    }

    @Test fun reconnectAndForgedProbeDataCannotMakeTheProviderReady() {
        repeat(3) {
            BydBatteryStatus.publishProbeReading(context, reading)
            BydBatteryStatus.start(context)
            ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
            assertNull(BydBatteryStatus.snapshot())
            assertFalse(identification.withVehicleStatusFrom(BydBatteryStatus).vehicleStatusEnabled)
        }
    }

    @Test
    fun staleReadingBecomesUsableAgainOnlyAfterFreshDataIsPublished() {
        var clock = 0L
        val cache = BydBatteryCache { clock }
        cache.accept(reading)
        clock += 181_000
        assertNull(cache.snapshot(20))
        cache.accept(BydBatteryReading(10.0, 60, 10.0, false, BydBatteryProtocol.CANFD))
        val fresh = cache.snapshot(20)!!
        assertEquals(10.0, fresh.batteryPercent, 0.001)
        assertEquals(100_400L, fresh.maxChargeWh)
    }

    @Test
    fun protocolChangesCannotReuseThePreviousCapacity() {
        val cache = BydBatteryCache { 0 }
        cache.accept(reading)
        assertEquals(100_400L, cache.snapshot(20)!!.maxChargeWh)
        cache.accept(BydBatteryReading(51.0, 36, null, true, BydBatteryProtocol.CAN))
        val can = cache.snapshot(20)!!
        assertEquals(51.0, can.batteryPercent, 0.001)
        assertNull(can.currentChargeWh)
        assertNull(can.maxChargeWh)
        cache.accept(BydBatteryReading(10.0, 60, 5.0, false, BydBatteryProtocol.CANFD))
        assertEquals(50_000L, cache.snapshot(20)!!.maxChargeWh)
    }

    @Test
    fun failedSampleClearsTheReadingAndCapacity() {
        val cache = BydBatteryCache { 0 }
        cache.accept(reading)
        cache.accept(null)
        assertNull(cache.snapshot(20))
        cache.accept(BydBatteryReading(10.0, 60, 5.0, false, BydBatteryProtocol.CANFD))
        assertEquals(50_000L, cache.snapshot(20)!!.maxChargeWh)
    }
}
