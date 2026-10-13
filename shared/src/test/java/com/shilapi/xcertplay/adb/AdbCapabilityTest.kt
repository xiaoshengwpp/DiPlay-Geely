package com.shilapi.xcertplay.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reporting a developer setting must never imply a connection, authorization or usable transport. */
class AdbCapabilityTest {
    @Test fun developerSettingHasThreeDistinctReadOnlyStates() {
        assertEquals(AdbCapability.State.AVAILABLE, AdbCapability.snapshot(true).developerSetting)
        assertEquals(AdbCapability.State.UNAVAILABLE, AdbCapability.snapshot(false).developerSetting)
        assertEquals(AdbCapability.State.UNKNOWN, AdbCapability.snapshot(null).developerSetting)
    }

    @Test fun everyDeveloperSettingLeavesLocalTransportUnimplementedAndOwnerConnectionUnknown() {
        for (setting in listOf(true, false, null)) {
            val snapshot = AdbCapability.snapshot(setting)
            assertEquals(AdbCapability.State.NOT_IMPLEMENTED, snapshot.localTransport)
            assertEquals(AdbCapability.State.UNKNOWN, snapshot.ownerComputerTransport)
            assertTrue(snapshot.report().contains("A setting is not a connection or authorization"))
            assertTrue(snapshot.report().contains("No shell commands, keys, pairing, permission grants or system-setting writes"))
        }
    }

    @Test fun evidenceCatalogContainsOnlyTheFixedReadOnlyCommands() {
        assertEquals(listOf(
            "adb devices -l",
            "adb shell getprop ro.product.model",
            "adb shell getprop ro.product.device",
            "adb shell getprop ro.build.version.release",
            "adb shell dumpsys display",
            "adb shell dumpsys window displays",
        ), AdbCapability.readOnlyEvidenceCommands)
    }
}
