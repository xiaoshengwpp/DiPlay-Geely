"""Source contracts for the reviewed 2026-10-15 upstream integration."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / 'common/src/main/java/com/shilapi/xcertplay'
SHARED = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'

class October15SyncTest(unittest.TestCase):
    def test_foreground_consent_has_its_own_namespace(self):
        source = (COMMON / 'WheelKeyService.kt').read_text()
        foreground = source.split('object ForegroundWheelSettings', 1)[1]
        self.assertIn('diplay_foreground_wheel_keys', foreground)
        self.assertNotIn('diplay_wheel_map_zoom', foreground)
        self.assertIn('false)', foreground)
        self.assertIn('fun revision(context: Context)', foreground)
        backup = (COMMON / 'backup/SettingsBackupCodec.kt').read_text()
        self.assertNotIn('diplay_foreground_wheel_keys', backup)

    def test_global_service_stays_disabled(self):
        source = (COMMON / 'WheelKeyService.kt').read_text()
        service = source.split('override fun onServiceConnected()', 1)[1].split('override fun onUnbind', 1)[0]
        self.assertLess(service.index('if (!GeelyVendorPolicy.bydAdb) return'), service.index('running = this'))
        self.assertIn('fun mediaPlayPause(context: Context): Boolean = GeelyVendorPolicy.bydAdb &&', source)
        self.assertIn('fun siriKey(context: Context): Boolean = GeelyVendorPolicy.bydAdb &&', source)

    def test_foreground_gesture_retains_first_press_and_execution_owners(self):
        source = (COMMON / 'CarPlayHostActivity.kt').read_text()
        for required in ['firstDoubleSiri', 'sessionToken: Any', 'settingsRevision: Long',
                         'pending.settingsRevision == ForegroundWheelSettings.revision(this)',
                         'pending.controller.requestSiri(pending.sessionToken)',
                         'activeController?.activeAirPlaySessionToken() !== pending.sessionToken']:
            self.assertIn(required, source)
        pause = source.split('override fun onPause()', 1)[1].split('override fun onStop()', 1)[0]
        self.assertIn('cancelAssignedDoubleSiri()', pause)
        self.assertIn('if (!hasFocus) cancelAssignedDoubleSiri()', source)

    def test_queued_media_keeps_revision_avrcp_and_lifecycle_fences(self):
        source = (COMMON / 'CarPlayHostActivity.kt').read_text()
        route = source.split('val remappedMedia =', 1)[1].split('if (remappedMedia', 1)[0]
        self.assertIn('AirPlayPersistence.loadCarBluetoothAudio(this)', route)
        self.assertIn('CarPlayMediaKeys.onExternalMediaKey', route)
        self.assertIn('revision == ForegroundWheelSettings.revision(this)', route)
        self.assertIn('generation == doubleSiriGeneration', route)

    def test_resume_does_not_cross_new_focus_event_or_request(self):
        source = (COMMON / 'CarPlayMediaKeys.kt').read_text()
        resume = source.split('private fun onIphonePlaying', 1)[1].split('private fun onNowPlayingChanged', 1)[0]
        self.assertIn('focusOwner to focusEventRevision', resume)
        self.assertIn('focusOwner === owner && focusEventRevision == revision', resume)

    def test_native_preview_cancel_can_restore_current_mask(self):
        source = (COMMON / 'CarPlayHostActivity.kt').read_text()
        self.assertIn('else restoreCurrentIphoneCornerMask()', source)
        restore = source.split('private fun restoreCurrentIphoneCornerMask()', 1)[1].split('/** The UI owns', 1)[0]
        self.assertIn('controller?.activeIphoneCornerMask()', restore)
        self.assertIn('if (activeAirPlaySession === session)', restore)

    def test_hotspot_warning_only_resets_confirmed_missing_network(self):
        source = (SHARED / 'orchestration/CarPlayController.kt').read_text()
        self.assertIn('pastOffReport && (failure as? WirelessStartupException)?.reason == WirelessStartupFailure.HOTSPOT_NOT_READY', source)
        settings = (SHARED / 'network/CarHotspotSettings.kt').read_text()
        self.assertIn('if (state != false || !offReportIgnored(context)) return false', settings)

    def test_usb_diagnostic_has_no_serial_identifier(self):
        source = (SHARED / 'transport/IphoneUsbHost.kt').read_text()
        listing = source.split('fun listedDevices()', 1)[1].split('@Throws', 1)[0]
        self.assertIn('map(Ch341UsbHost::describe).sorted()', listing)
        self.assertNotIn('serialNumber', listing)

if __name__ == '__main__':
    unittest.main()
