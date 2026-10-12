package com.shilapi.xcertplay.hud

import org.junit.Assert.*
import org.junit.Test

class BydTyresTest {
    private fun parcel(value: Int) = "Result: Parcel(00000000 %08x '........')".format(value)

    @Test fun geelyNeverReadsTyrePressureOrTemperature() {
        repeat(3) { assertNull(BydTyres.read { error("No BYD tyre shell is authorized") }) }
    }

    @Test fun purePressureValidationRetainsItsBounds() {
        assertNull(BydTyres.plausiblePressure(49))
        assertEquals(50, BydTyres.plausiblePressure(50))
        assertEquals(232, BydTyres.plausiblePressure(232))
        assertEquals(600, BydTyres.plausiblePressure(600))
        assertNull(BydTyres.plausiblePressure(601))
        assertNull(BydTyres.plausiblePressure(65535))
    }

    @Test fun pureTemperatureValidationRetainsItsBounds() {
        assertNull(BydTyres.plausibleTemperature(-51))
        assertEquals(-50, BydTyres.plausibleTemperature(-50))
        assertEquals(20, BydTyres.plausibleTemperature(20))
        assertEquals(150, BydTyres.plausibleTemperature(150))
        assertNull(BydTyres.plausibleTemperature(151))
    }

    @Test fun aShellWithoutAutoserviceIsToldApartFromAnUnavailableShell() {
        assertTrue(BydTyres.noService("Service autoservice does not exist."))
        assertFalse(BydTyres.noService(parcel(232)))
        assertFalse(BydTyres.noService(null))
    }
}
