package com.shilapi.xcertplay.network

import org.junit.Assert.*
import org.junit.Test

class GeelyCapabilityTest {
    @Test fun automaticDetectionRequiresEveryKnownHardwareIdentifierAndAndroid11() {
        assertTrue(GeelyCapability.knownHardware("geely_fx11", "CS11_A4", "DesaySV", 30))
        assertTrue(GeelyCapability.knownHardware("GEELY_FX11", "cs11_a4", "desaysv", 30))
        val matches = listOf("geely_fx11", "CS11_A4", "DesaySV")
        for (index in matches.indices) {
            for (unknown in listOf(null, "", "geely", "lynk", "unknown", matches[index] + "_other")) {
                val ids = matches.mapIndexed { i, value -> if (i == index) unknown else value }
                assertFalse("identifier $index=$unknown", GeelyCapability.knownHardware(ids[0], ids[1], ids[2], 30))
            }
        }
        for (sdk in listOf(25, 28, 29, 31, 33, 37)) {
            assertFalse(GeelyCapability.knownHardware("geely_fx11", "CS11_A4", "DesaySV", sdk))
        }
    }

    @Test fun unknownHardwareRequiresExplicitManualOptIn() {
        assertFalse(GeelyCapability.inputAllowed(knownHardware = false, manualEnabled = false))
        assertTrue(GeelyCapability.inputAllowed(knownHardware = false, manualEnabled = true))
        assertTrue(GeelyCapability.inputAllowed(knownHardware = true, manualEnabled = false))
    }

    @Test fun consentCannotBypassAnyMissingServiceCapability() {
        for (mask in 0 until 32) {
            assertEquals("capability mask=$mask", mask == 31, GeelyCapability.inputServiceUsable(
                resolved = mask and 1 != 0,
                enabled = mask and 2 != 0,
                applicationEnabled = mask and 4 != 0,
                exported = mask and 8 != 0,
                permissionGranted = mask and 16 != 0,
            ))
        }
    }
}
