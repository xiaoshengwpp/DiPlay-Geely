#!/usr/bin/env python3
"""Source safety boundaries; Kotlin/Robolectric tests cover behavior when the SDK is available."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / "common/src/main/java/com/shilapi/xcertplay"


class GeelyDisplaySourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.capabilities = (COMMON / "GeelyDisplayCapabilities.kt").read_text()
        cls.policy = (COMMON / "GeelyDisplayPolicy.kt").read_text()
        cls.hud = (COMMON / "GeelyHudProjection.kt").read_text()
        cls.host = (COMMON / "CarPlayHostActivity.kt").read_text()

    def test_enumeration_uses_public_read_only_android_interfaces(self):
        self.assertIn("manager.displays", self.capabilities)
        self.assertIn("DISPLAY_CATEGORY_PRESENTATION", self.capabilities)
        self.assertIn("Display.FLAG_PRIVATE == 0", self.capabilities)
        for forbidden in ("createVirtualDisplay", "getDeclaredMethod", "setDisplay", "Settings.Secure",
                          "Runtime.getRuntime", "ProcessBuilder", "adb ", "service call"):
            self.assertNotIn(forbidden, self.capabilities)

    def test_roles_and_oem_limitations_are_explicit(self):
        self.assertIn('get() = "unconfirmed"', self.policy)
        self.assertIn("no verified Geely IPC/service contract, permission model, or screen-role mapping", self.capabilities)
        self.assertIn("physical visibility is unverified", self.capabilities)

    def test_hud_never_asks_for_overlay_or_privileged_access(self):
        for forbidden in ("TYPE_APPLICATION_OVERLAY", "canDrawOverlays", "ACTION_MANAGE_OVERLAY_PERMISSION",
                          "startActivity", "sendBroadcast", "getDeclaredMethod", "BydNavigationOutputs",
                          "BydHudProtocol", "ProjectionVehicleReader"):
            self.assertNotIn(forbidden, self.hud)
        self.assertIn(": Presentation(context, display)", self.hud)
        self.assertIn("FLAG_NOT_TOUCHABLE", self.hud)
        self.assertIn("FLAG_NOT_FOCUSABLE", self.hud)

    def test_selection_is_session_only_and_not_name_or_id_inference(self):
        for forbidden in ("getSharedPreferences", 'contains("hud"', "saveGeelyHud", "findDisplay"):
            self.assertNotIn(forbidden, self.hud)
        self.assertIn("it == previous && it.eligibleForManualHud", self.policy)
        self.assertIn("fun removed(displayId: Int)", self.policy)
        self.assertIn("else if (sawConnected) clear()", self.policy)

    def test_navigation_polling_does_not_take_the_widget_listener(self):
        self.assertIn("CarPlayGlance.snapshot()", self.hud)
        self.assertNotRegex(self.hud, r"CarPlayGlance\.listener\s*=")
        self.assertIn("main.postDelayed(refreshTask, 250L)", self.hud)
        self.assertIn("previous?.render(null)", self.hud)
        self.assertNotIn("value.song", self.hud)
        self.assertNotIn("value.playing", self.hud)

    def test_foreground_lifecycle_starts_and_cleans_up_both_readers(self):
        for hook in ("geelyWindowDiagnostics.start()", "geelyHudProjection.start()",
                     "geelyWindowDiagnostics.stop()", "geelyHudProjection.stop()"):
            self.assertIn(hook, self.host)
        self.assertIn("unregisterDisplayListener(this)", self.hud)
        self.assertIn("main.removeCallbacks(refreshTask)", self.hud)

    def test_window_measurements_never_sum_physical_screens(self):
        self.assertIn("GeelyWindowGeometry(decor.width, decor.height", self.capabilities)
        self.assertIn("ViewCompat.getRootWindowInsets(decor)", self.capabilities)
        self.assertNotRegex(self.capabilities, r"\.sum(?:Of)?\s*\(")
        self.assertNotIn("setOnApplyWindowInsetsListener", self.capabilities)


if __name__ == "__main__":
    unittest.main()
