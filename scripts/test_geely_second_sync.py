"""Source-only checks for the second 2026-10-14 upstream integration."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / 'common/src/main/java/com/shilapi/xcertplay'
SHARED = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'

class SecondUpstreamSyncTest(unittest.TestCase):
    def test_navigation_attenuation_is_neutral_by_default_and_live_only(self):
        persistence = (COMMON / 'AirPlayPersistence.kt').read_text()
        self.assertIn('.getInt(KEY_NAVIGATION_AUDIO_ATTENUATION, 0)', persistence)
        activity = (COMMON / 'DiPlayActivity.kt').read_text()
        save = activity.split('gainSlider.onSave = { db ->', 1)[1].split('card.addView(gainSlider)', 1)[0]
        self.assertIn('saveNavigationAudioAttenuationDb(this, db)', save)
        self.assertIn('snapshot()?.sink?.setNavigationAttenuationDb(db)', save)
        self.assertNotIn('reconnect', save.lower())

    def test_hotspot_permission_and_scan_paths_stay_inactive(self):
        activity = (COMMON / 'DiPlayActivity.kt').read_text()
        self.assertNotIn('openWriteSettingsAccess', activity)
        self.assertNotIn('loadWifiDirectScanPause', activity)
        self.assertNotIn('saveWifiDirectScanPause', activity)
        self.assertNotIn('hotspotStartupResult == CarHotspotTethering.Result.PERMISSION_REQUIRED', activity)
        adapter = (SHARED / 'network/PlatformTetheringStart.kt').read_text()
        self.assertIn('fun supported(sdk: Int = 0): Boolean = false', adapter)
        self.assertIn('throw UnsupportedOperationException', adapter)
        self.assertNotIn('exemptHiddenApis', adapter)
        scan = (SHARED / 'network/WifiScanPause.kt').read_text()
        self.assertIn('fun pause() = Unit', scan)
        self.assertIn('fun close() = Unit', scan)

    def test_no_placeholder_phone_name_before_selection(self):
        activity = (COMMON / 'DiPlayActivity.kt').read_text()
        self.assertIn('if (DiPlayPreferences.phoneAddress(this) == null) getString(R.string.choose_iphone)', activity)
        self.assertEqual(3, activity.count('button(chooseIphoneTitle()'))

    def test_backup_schema_does_not_gain_network_or_permission_flags(self):
        backup = (COMMON / 'backup/SettingsBackupCodec.kt').read_text()
        self.assertIn('Map<String, Map<String, ValueType>>', backup)
        self.assertNotIn('wifi_direct_scan_pause', backup)
        self.assertNotIn('Policy.All', backup)

    def test_only_explicitly_growing_choices_escape_fixed_height_normalization(self):
        activity = (COMMON / 'DiPlayActivity.kt').read_text()
        self.assertIn('var growForText = false', activity)
        self.assertIn('button.growForText = true', activity)
        self.assertIn('child is SettingButton && child.growForText', activity)
        test = (ROOT / 'common/src/test/java/com/shilapi/xcertplay/GeelyMusicBufferSettingsUiTest.kt').read_text()
        self.assertIn('ReflectionHelpers.setField(activity, "page", "settings")', test)
        self.assertIn('"ja" to 320', test)
        self.assertIn('"ar" to 320', test)

    def test_new_ui_screenshots_are_uploaded_by_existing_report_path(self):
        tests = (ROOT / 'common/src/test/java/com/shilapi/xcertplay/AdaptiveSettingsUiTest.kt').read_text()
        self.assertIn('build/reports/album-artwork-ui/navigation-audio/', tests)
        self.assertIn('capture.draw(Canvas(bitmap))', tests)
        self.assertIn('viewport.contains(rectangle)', tests)
        self.assertIn('listOf(title, value, slider, description)', tests)
        for variant in ('compact-ja', 'expanded-en', 'compact-ar-rtl'):
            self.assertIn('"' + variant + '"', tests)

if __name__ == '__main__':
    unittest.main()
