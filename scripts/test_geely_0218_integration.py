"""Source contracts for the exact 0.2.18 integration and its withheld hardware paths."""
from pathlib import Path
import hashlib
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMMON = ROOT / 'common/src/main/java/com/shilapi/xcertplay'
SHARED = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'

class Geely0218IntegrationTest(unittest.TestCase):
    def test_upstream_is_integrated_beyond_version_metadata(self):
        for name in ('transport/UsbConfigurationSelection.kt', 'transport/BluetoothLinkCheck.kt',
                     'media/VideoDecodeQueue.kt', 'airplay/FeedbackPositionMonitor.kt'):
            self.assertTrue((SHARED / name).is_file(), name)
        for name in ('UsbChargingCurrent.kt', 'backup/SettingsBackupCodec.kt', 'InitialAlbumRecovery.kt'):
            self.assertTrue((COMMON / name).is_file(), name)

    def test_geely_wheel_bridge_has_no_retained_context_or_vendor_binding(self):
        text = (COMMON / 'GeelyWheelKeys.kt').read_text()
        self.assertIn('fun start(context: Context) = Unit', text)
        self.assertIn('fun stop() = Unit', text)
        for forbidden in ('bindService(', 'registerListener(', 'postDelayed(', 'private var appContext'):
            self.assertNotIn(forbidden, text)

    def test_vpn_fallback_cannot_evaluate_a_default_credential_argument(self):
        text = (COMMON / 'VpnPreConsent.kt').read_text()
        self.assertIn('adb: LocalAdb? = null', text)
        self.assertIn('LocalAdb.Access.UNSUPPORTED', text)
        for forbidden in ('AdbKeys', 'appops', '.connect(', '.shell('):
            self.assertNotIn(forbidden, text)
        host = (COMMON / 'CarPlayHostActivity.kt').read_text()
        body = host.split('private fun preConsentVpnOverAdb() {', 1)[1].lstrip()
        self.assertTrue(body.startswith('if (!GeelyVendorPolicy.bydAdb) return'))

    def test_wheel_accessibility_mutator_implementation_is_removed(self):
        text = (COMMON / 'WheelKeyService.kt').read_text()
        for forbidden in ('AdbKeys', 'LocalAdb(', 'settings put secure', 'checkedShell(', 'applyServiceSettingsLocked('):
            self.assertNotIn(forbidden, text)
        self.assertIn('LocalAdb.Access = LocalAdb.Access.UNSUPPORTED', text)
        self.assertIn('shouldContinue: () -> Boolean = { true }): Boolean = false', text)

    def test_common_disabled_adb_facades_have_no_backend_implementation(self):
        for name in ('AdbClusterRouter.kt', 'CarHotspotSetup.kt', 'UsbPermissionSetup.kt',
                     'WheelKeyService.kt', 'VpnPreConsent.kt', 'DiPlayActivity.kt'):
            text = (COMMON / name).read_text()
            for forbidden in ('AdbKeys.load', 'LocalAdb(', 'settings put secure', 'appops set'):
                self.assertNotIn(forbidden, text, name)
        permission = (COMMON / 'UsbPermissionSetup.kt').read_text()
        self.assertIn('fun manualCommand(packageName: String): String = ":"', permission)

    def test_usb_auto_confirmation_implementation_is_absent(self):
        source = (COMMON / 'UsbAutoConfirmService.kt').read_text()
        for forbidden in ('rootInActiveWindow', 'performAction(', 'ACTION_CLICK', 'startActivity(', 'Settings.Secure'):
            self.assertNotIn(forbidden, source)
        self.assertIn('fun isEnabled(context: Context): Boolean = false', source)
        self.assertIn('fun openSettings(context: Context): Boolean = false', source)

    def test_new_background_update_entry_points_are_fail_closed(self):
        text = (COMMON / 'update/BackgroundUpdateWorker.kt').read_text()
        for signature in ('override fun doWork(): Result {', 'fun schedule(context: Context) {'):
            body = text.split(signature, 1)[1].lstrip()
            self.assertTrue(body.startswith('if (!GeelyVendorPolicy.upstreamApkUpdates) return'))
        ui = (COMMON / 'DiPlayActivity.kt').read_text()
        body = ui.split('private fun startInstaller(file: File, publicUri: Uri?) {', 1)[1].lstrip()
        self.assertTrue(body.startswith('if (!GeelyVendorPolicy.upstreamApkUpdates) return'))

    def test_unverified_window_bridge_does_not_compete_with_media_session(self):
        host = (COMMON / 'CarPlayHostActivity.kt').read_text()
        self.assertIn('if (GeelyVendorPolicy.foregroundMediaKeys && downOrUp && mediaPress != null)', host)
        policy = (SHARED / 'hud/GeelyVendorPolicy.kt').read_text()
        self.assertIn('val foregroundMediaKeys: Boolean = false', policy)

    def test_language_implementation_is_the_verified_fork_blob(self):
        # AppLocale did not change in official 0.2.16 -> 0.2.18. Keep the fork migration intact.
        data = (COMMON / 'AppLocale.kt').read_bytes()
        expected = 'dc18198b2f4621fce18cd9cb74db8f4d75810089'
        actual = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        self.assertEqual(expected, actual)

if __name__ == '__main__':
    unittest.main()
