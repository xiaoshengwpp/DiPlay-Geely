#!/usr/bin/env python3
"""Structural regression checks supplement the real-facade Kotlin tests.

Keep all hardware-facing entry points fail-closed, including debug-only code that
is outside the shared/common JVM test modules. This never launches Android code.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
HUD = "shared/src/main/java/com/shilapi/xcertplay/hud/"
COMMON = "common/src/main/java/com/shilapi/xcertplay/"


class GeelyVendorBoundaryTest(unittest.TestCase):
    def test_hardware_entry_points_check_policy_first(self):
        entries = [
            (HUD + "BydNavigationOutputs.kt", "fun onAppOpened(context: Context) {", "bydNavigation"),
            (HUD + "BydNavigationOutputs.kt", "fun start(context: Context) {", "bydNavigation"),
            (HUD + "BydNavigationOutputs.kt", "internal fun onFrame(frame: Iap2Frame) {", "bydNavigation"),
            (HUD + "BydNavigationOutputs.kt", "fun endNow() {", "bydNavigation"),
            (HUD + "BydNavigationOutputs.kt", "fun clearClusterStreamControl(control: (Boolean) -> Unit) {", "bydCluster"),
            (HUD + "BydAdbAccess.kt", "fun check(context: Context, mayAsk: Boolean): Status {", "bydAdb"),
            (HUD + "BydAdbAccess.kt", "internal fun readStatus(context: Context, shell: (String) -> String?): Status {", "bydAdb"),
            (HUD + "BydAdbShell.kt", "fun run(context: Context, command: String): String? {", "bydAdb"),
            (HUD + "BydBattery.kt", "fun start(appContext: Context) {", "bydBattery"),
            (HUD + "BydBattery.kt", "override fun snapshot(): VehicleStatusSnapshot? {", "bydBattery"),
            (HUD + "BydBattery.kt", "fun accept(appContext: Context, reading: BydBatteryReading) {", "bydBattery"),
            (HUD + "BydClusterMapPause.kt", "fun initialize(appContext: Context) {", "bydCluster"),
            (HUD + "BydStarterBridge.kt", "fun initialize(context: Context) {", "bydDiagnostics"),
            (HUD + "BydStarterBridge.kt", "fun configure(context: Context, secret: String) {", "bydDiagnostics"),
            (HUD + "BydStarterBridge.kt", "fun demonstrate(context: Context) {", "bydDiagnostics"),
            (HUD + "BydStarterBridge.kt", "private fun tick() {", "bydDiagnostics"),
            (COMMON + "CarPlayHostActivity.kt", "private fun onClusterSurface(surface: Surface?) {", "bydCluster"),
            (COMMON + "ClusterMapPresentation.kt", "fun findDisplay(context: Context, theme: DiLink51ClusterLayout.Theme = DiLink51ClusterLayout.theme(context)): Display? {", "bydCluster"),
            ("mobile/src/debug/java/com/shilapi/xcertplay/hud/StandaloneHudDemoActivity.kt", "private fun transmit(packet: String) {", "bydDiagnostics"),
        ]
        for filename, signature, capability in entries:
            with self.subTest(filename=filename, signature=signature):
                source = (ROOT / filename).read_text()
                self.assertEqual(source.count(signature), 1)
                body = source.split(signature, 1)[1].lstrip()
                self.assertTrue(body.startswith(f"if (!GeelyVendorPolicy.{capability}) return"))

    def test_controller_and_service_still_use_the_guarded_facade(self):
        controller = (ROOT / "shared/src/main/java/com/shilapi/xcertplay/orchestration/CarPlayController.kt").read_text()
        for call, count in {"BydNavigationOutputs.start(": 2, "BydNavigationOutputs.endNow(": 2,
                            "BydNavigationOutputs.onFrame(": 1, "BydNavigationOutputs.setClusterStreamControl(": 1,
                            "BydNavigationOutputs.clearClusterStreamControl(": 1}.items():
            self.assertEqual(controller.count(call), count, call)
        self.assertIn("BydNavigationOutputs.onFrame(frame)\n        synchronized(playbackStatus)", controller)
        self.assertIn("BydNavigationOutputs.onAppOpened(applicationContext)", (ROOT / (COMMON + "DiPlayActivity.kt")).read_text())
        self.assertIn("BydNavigationOutputs.endNow()", (ROOT / (COMMON + "DiPlaySessionService.kt")).read_text())
        # Both real ADB construction sites remain behind the entry guards above.
        constructors = []
        for path in (ROOT / "shared/src/main/java").rglob("*.kt"):
            if "LocalAdb(AdbKeys.load(context))" in path.read_text():
                constructors.append(path.name)
        self.assertEqual(sorted(constructors), ["BydAdbAccess.kt", "BydAdbShell.kt"])

    def test_debug_manifest_cannot_export_or_enable_byd_diagnostics(self):
        root = ET.parse(ROOT / "mobile/src/debug/AndroidManifest.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        self.assertNotIn("android.permission.BYDAUTO_INSTRUMENT_COMMON",
                         [e.get(android + "name") for e in root.findall("uses-permission")])
        app = root.find("application")
        for tag, name in [("receiver", "StarterBridgeReceiver"), ("activity", "StandaloneHudDemoActivity")]:
            found = [e for e in app.findall(tag) if e.get(android + "name", "").endswith(name)]
            self.assertEqual(len(found), 1)
            self.assertEqual(found[0].get(android + "enabled"), "false")
            self.assertEqual(found[0].get(android + "exported"), "false")


if __name__ == "__main__":
    unittest.main()
