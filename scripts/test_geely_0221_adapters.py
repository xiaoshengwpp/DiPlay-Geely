#!/usr/bin/env python3
"""The 0.2.21 import must not restore vehicle hotspot or BYD mutation backends."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'

class Geely0221AdaptersTest(unittest.TestCase):
    def test_hotspot_compatibility_has_no_live_platform_or_shell_implementation(self):
        for name in ('CarHotspotConfigRead.kt', 'CarHotspotConfigWrite.kt'):
            text = (SHARED / 'network' / name).read_text()
            for token in ('AdbKeys', 'LocalAdb(', 'CarHotspotStartMain', 'getSystemService(',
                          'getMethod(', '.invoke(', 'WifiManager', '.shell(', 'Thread(', 'Executors.'):
                self.assertNotIn(token, text, name)
        read = (SHARED / 'network/CarHotspotConfigRead.kt').read_text()
        write = (SHARED / 'network/CarHotspotConfigWrite.kt').read_text()
        self.assertIn('Outcome(reason = Reason.UNSUPPORTED_SDK)', read)
        self.assertIn('fun supported(sdk: Int = Build.VERSION.SDK_INT): Boolean = false', write)
        self.assertIn('WriteOutcome(Result.UNSUPPORTED_SDK)', write)
        self.assertFalse((SHARED / 'network/CarHotspotStartMain.kt').exists())

    def test_stale_hotspot_opt_in_and_ui_are_gated(self):
        settings = (SHARED / 'network/CarHotspotSettings.kt').read_text()
        self.assertIn('fun configWrite(context: Context): Boolean = GeelyVendorPolicy.bydAdb &&', settings)
        ui = (ROOT / 'common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt').read_text()
        for name in ('importCarHotspotConfig', 'writeCarHotspotConfigNow'):
            self.assertIn('private fun ' + name + '() {\n        if (!GeelyVendorPolicy.bydAdb) return', ui)
        for line in ui.splitlines():
            if 'if (' in line and 'CarHotspotConfigWrite.supported()' in line:
                self.assertIn('GeelyVendorPolicy.bydAdb &&', line)

    def test_rhythm_compatibility_has_no_service_or_transport_backends(self):
        text = (SHARED / 'hud/BydCarMusicRhythm.kt').read_text()
        for token in ('AdbKeys', 'LocalAdb', 'Class.forName(', '.invoke(', 'Binder(',
                      'Executors.', 'getSystemService(', 'System.exit(', 'System.`in`', 'app_process'):
            self.assertNotIn(token, text)
        for signature in ('fun setPlaying(context: Context, value: Boolean) = Unit',
                          'fun sendBands(context: Context, bands: IntArray) = Unit',
                          'CompletableFuture.completedFuture(false)',
                          'fun main(args: Array<String>) = Unit',
                          'fun send(bands: IntArray): Int = -1'):
            self.assertIn(signature, text)

if __name__ == '__main__':
    unittest.main()
