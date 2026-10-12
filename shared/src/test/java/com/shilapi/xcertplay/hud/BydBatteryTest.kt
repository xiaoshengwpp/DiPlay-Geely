package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test

/** The Geely product keeps pure conversion coverage but cannot read a BYD traction battery. */
class BydBatteryTest {
    @Test fun geelyDoesNotProbeEvenAValidBydProtocol() {
        val commands = mutableListOf<String>()
        for (protocol in listOf("CAN", "CANFD", "UNKNOWN", "")) {
            assertNull(BydBattery.read { commands += it; protocol })
        }
        assertTrue(commands.isEmpty())
    }

    @Test fun geelyDoesNotInvokeAnUnavailableOrThrowingShell() {
        repeat(3) { assertNull(BydBattery.read { error("No BYD read is authorized") }) }
    }

    @Test fun pureCanSnapshotDoesNotInventUnverifiedEnergy() {
        val reading = BydBatteryReading(51.0, 36, null, true, BydBatteryProtocol.CAN)
        assertNull(BydBattery.fullKwh(reading))
        val snapshot = BydBattery.snapshot(reading, 20, null)
        assertNull(snapshot.currentChargeWh)
        assertNull(snapshot.maxChargeWh)
        assertEquals(51.0, snapshot.batteryPercent, 0.001)
        assertEquals(36, snapshot.rangeKm)
        assertTrue(snapshot.charging)
    }

    @Test
    fun theIphoneGetsWattHoursAndAWarningAtTheThreshold() {
        val reading = BydBatteryReading(percent = 25.0, rangeKm = 150, remainingKwh = 25.1, charging = false, protocol = BydBatteryProtocol.CANFD)

        val above = BydBattery.snapshot(reading, lowPercent = 20, fullKwh = null)
        assertFalse(above.rangeWarning)
        assertEquals(25_100L, above.currentChargeWh)
        assertEquals(100_400L, above.maxChargeWh)
        assertEquals(600, above.maxRangeKm)

        assertTrue(BydBattery.snapshot(reading, lowPercent = 25, fullKwh = null).rangeWarning)
    }

    @Test
    fun aLowChargeKeepsTheEarlierFullChargeEstimate() {
        val high = BydBatteryReading(percent = 80.0, rangeKm = 480, remainingKwh = 80.0, charging = false, protocol = BydBatteryProtocol.CANFD)
        val low = BydBatteryReading(percent = 5.0, rangeKm = 30, remainingKwh = 5.6, charging = false, protocol = BydBatteryProtocol.CANFD)

        assertNull(BydBattery.fullKwh(low))
        assertEquals(100_000L, BydBattery.snapshot(low, lowPercent = 20, fullKwh = BydBattery.fullKwh(high)).maxChargeWh)
    }
}
