"""Geely USB source-boundary regressions for the upstream 0.2.20 synchronization."""
from pathlib import Path
import re
import unittest

SHARED = Path(__file__).resolve().parents[3]
MAIN = SHARED / "src/main/java/com/shilapi/xcertplay"


class GeelyUsbReconnectSourceTest(unittest.TestCase):
    def source(self, name):
        return (MAIN / name).read_text()

    def function(self, source, start, end):
        return source[source.index(start):source.index(end, source.index(start))]

    def test_privileged_usb_grant_helper_and_keep_rule_are_absent(self):
        self.assertFalse((MAIN / "transport/IphoneUsbAdbGrant.kt").exists())
        self.assertFalse((MAIN / "transport/UsbPermissionActivityProbe.kt").exists())
        for name in ("transport/IphoneUsbHost.kt", "orchestration/CarPlayController.kt"):
            source = self.source(name)
            for forbidden in ("IphoneUsbAdbGrant", "IphoneUsbGrantTool", "UsbPermissionActivityProbe",
                              "AdbRequired", "allowAdbFallback", "afterMissingUiAdbFailure", "pendingUsbAdbGrants"):
                self.assertNotIn(forbidden, source)
        self.assertNotIn("IphoneUsbGrantTool", (SHARED / "ambient-consumer-rules.pro").read_text())

    def test_usb_permission_retains_only_android_permission_flow(self):
        source = self.function(self.source("transport/IphoneUsbHost.kt"),
                               "    fun requestPermission(", "    /** Returns null for unrelated broadcasts")
        self.assertIn("requireConfiguredDevice(device)", source)
        self.assertIn("usbManager.hasPermission(device)", source)
        self.assertIn("usbManager.requestPermission(device, permissionPendingIntent())", source)
        for forbidden in ("shell(", "AdbKeys", "Class.forName", "getActivityInfo", "grantDevicePermission"):
            self.assertNotIn(forbidden, source)

    def test_forced_configuration_claims_are_dropped_by_close_not_release(self):
        source = self.function(self.source("transport/IphoneUsbHost.kt"),
                               "    private fun selectConfiguration(", "    /** The iPhone's current configuration")
        code = re.sub(r"//[^\n]*", "", source)
        self.assertIn("current.claimInterface(interfaces.getValue(id), true)", code)
        self.assertIn("current.close()", code)
        self.assertIn("current = usbManager.openDevice(device)", code)
        self.assertNotIn("releaseInterface(", code)
        self.assertIn("if (current !== connection) current.close()", code)

    def test_old_mode_transition_cannot_reopen_a_newer_enumeration(self):
        source = self.function(self.source("orchestration/CarPlayController.kt"),
                               "    private fun beginReenumeration(", "    private fun onIphoneAttached(")
        for fence in ("mainHandler.post", "closed || phase != Phase.REENUMERATION",
                      "availabilityGeneration != availabilityPollGeneration.get()",
                      "permissionGeneration != permissionPollGeneration",
                      "transitionAttempt != reenumerationAttempts"):
            self.assertIn(fence, source)
        self.assertLess(source.index("return@post"), source.index("openDataPaths(device)"))

    def test_wired_memory_and_route_frames_keep_the_control_session_fence(self):
        source = self.source("orchestration/CarPlayController.kt")
        self.assertIn("runStack(result.session, ncm, device.deviceName, generation)", source)
        self.assertIn("ncm: NcmUsbBridge, deviceName: String, generation: Int", source)
        self.assertIn("onIncoming = { frame -> onRouteFrame(frame) { this.csm === csm } }", source)
        self.assertIn("!closed && this.csm === csm && generation == availabilityPollGeneration.get() && message.startsWith(START_SESSION_PROGRESS)", source)
        self.assertIn("WiredSessionMemory.started(deviceName)", source)
        self.assertNotIn("wiredDeviceName", source)

    def test_wired_watchdog_cleanup_and_timeout_are_owned_by_one_control_run(self):
        source = self.function(self.source("orchestration/CarPlayController.kt"),
                               "    private fun runStack(", "    private fun pairNewRecord(")
        self.assertIn("var ownedStartWatchdog: WiredStartWatchdog? = null", source)
        self.assertIn("ownedStartWatchdog = startWatchdog", source)
        self.assertIn("wiredStartWatchdog === startWatchdog && this.csm === csm", source)
        self.assertIn("generation == availabilityPollGeneration.get()", source)
        self.assertIn("ownedStartWatchdog?.terminate()", source)
        self.assertNotIn("wiredStartWatchdog?.terminate()", source)

    def test_closed_usb_open_result_is_disposed_before_ncm_is_opened(self):
        source = self.function(self.source("orchestration/CarPlayController.kt"),
                               "    private fun openDataPaths(", "    private fun openNcm(")
        self.assertIn("!closed && phase == Phase.DATAPATHS", source)
        self.assertIn("val generation = availabilityPollGeneration.incrementAndGet()", source)
        self.assertIn("generation == availabilityPollGeneration.get()", source)
        self.assertGreaterEqual(source.count("if (!isCurrentOpen())"), 2)
        self.assertIn("if (generation == availabilityPollGeneration.get()) fail(error)", source)
        self.assertIn("Iap2SessionResult.Failed -> if (isCurrentOpen()) fail(result.error)", source)
        self.assertIn("if (result is IphoneUsbHost.Iap2SessionResult.Connected) result.session.close()", source)
        self.assertLess(source.index("return@openIap2UsbSessionAsync"), source.index("openNcm(device)"))


if __name__ == "__main__":
    unittest.main()
