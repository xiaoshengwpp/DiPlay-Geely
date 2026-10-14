"""Source-only boundaries for the third 2026-10-14 upstream integration."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / 'common/src/main/java/com/shilapi/xcertplay'
SHARED = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'

class ThirdUpstreamSyncTest(unittest.TestCase):
    def test_clock_trim_requires_music_not_loudspeaker_calls(self):
        policy = (SHARED / 'media/MediaClockTrim.kt').read_text()
        self.assertIn('selection.channel == AudioChannel.MEDIA && selection.contentType == AudioContentType.MUSIC', policy)
        sink = (SHARED / 'media/AndroidMediaSink.kt').read_text()
        self.assertIn('clockTrim = MediaClockTrim.forSelection(selection, format.sampleRate,', sink)
        self.assertIn('if (playbackStarted) trimClock(track)', sink)
        self.assertIn('const val MAX_TRIM = 0.015', policy)
        self.assertIn('const val UPDATE_NS = 1_000_000_000L', policy)

    def test_clock_failure_diagnostics_cannot_break_pcm_writes(self):
        sink = (SHARED / 'media/AndroidMediaSink.kt').read_text()
        trim = sink.split('    private fun trimClock(', 1)[1].split('    private fun maintainPlaybackBuffer', 1)[0]
        self.assertIn('runCatching { track.setPlaybackRate(rate) }', trim)
        self.assertIn('clockTrim = null', trim)
        self.assertIn('runCatching { report(', trim)

    def test_media_diagnostics_preserve_rejection_semantics(self):
        keys = (COMMON / 'CarPlayMediaKeys.kt').read_text()
        reject = keys.split('fun rejected(reason: String): Boolean {', 1)[1].split('}', 1)[0]
        self.assertIn('WheelKeyLog.note(', reject)
        self.assertIn('return false', reject)
        self.assertIn('if (sent) mediaDispatch.record(token, index, route,', keys)
        self.assertIn('active.sendMediaButton(index, token)', keys)
        self.assertIn('controller === active && stillCurrent() && !inCall(context)', keys)

    def test_hotspot_escape_is_manual_and_does_not_enable_automation(self):
        settings = (SHARED / 'network/CarHotspotSettings.kt').read_text()
        self.assertIn('GeelyVendorPolicy.bydAdb && prefs(context).getBoolean("auto_enable", false)', settings)
        self.assertIn('return state == false && !offReportIgnored(context)', settings)
        activity = (COMMON / 'DiPlayActivity.kt').read_text()
        dialog = activity.split('private fun carHotspotOffDialog()', 1)[1].split('\n    }', 1)[0]
        self.assertLess(dialog.index('setNeutralButton'), dialog.index('setOffReportIgnored(this, true)'))
        self.assertNotIn('CarHotspotTethering.enable', dialog)
        self.assertIn('.setNegativeButton(getString(R.string.cancel), null)', dialog)
        backup = (COMMON / 'backup/SettingsBackupCodec.kt').read_text()
        self.assertNotIn('ignore_off_report', backup)

    def test_byd_long_press_preference_stays_product_gated(self):
        activity = (COMMON / 'DiPlayActivity.kt').read_text()
        section = activity.split('private fun wheelKeysSettings(', 1)[1].split('private fun wheelMapZoomAvailable', 1)[0]
        self.assertIn('if (!GeelyVendorPolicy.bydAdb) return', section)
        self.assertIn('if (GeelyVendorPolicy.bydAdb && (byd || WheelZoomSettings.siriLongPressOnly(this)))', section)

if __name__ == '__main__':
    unittest.main()
