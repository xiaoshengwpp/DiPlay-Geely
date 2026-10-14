"""Capability-gated 0.2.18 integration; BYD, ADB and Telecom remain withheld."""
from pathlib import Path
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

    def test_geely_wheel_bridge_requires_explicit_capability_before_binding(self):
        text = (COMMON / 'GeelyWheelKeys.kt').read_text()
        start = text.split('fun start(context: Context, controller: CarPlayController) {', 1)[1]
        start = start.split('fun stop()', 1)[0]
        self.assertLess(start.index('if (!GeelyInputSettings.enabled(context))'),
                        start.index('context.applicationContext'))
        bind = text.split('private fun bind(session: Session) {', 1)[1]
        bind = bind.split('internal fun serviceUsable', 1)[0]
        self.assertLess(bind.index('if (!current(session)) return'), bind.index('bindService('))
        self.assertLess(bind.index('if (!serviceUsable(session.context, intent))'), bind.index('bindService('))
        for required in ('Intent().setComponent(ComponentName(INPUT_PACKAGE, INPUT_CLASS))',
                         'context.packageManager.resolveService(intent, 0)',
                         'GeelyCapability.inputServiceUsable(',
                         'context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED',
                         'service.interfaceDescriptor == INPUT_DESCRIPTOR'):
            self.assertIn(required, text)
        for forbidden in ('LocalAdb(', 'AdbKeys', 'Settings.Secure', 'settings put secure', 'Runtime.getRuntime('):
            self.assertNotIn(forbidden, text)

    def test_geely_manual_input_is_default_off_and_does_not_bypass_service_safety(self):
        settings = (COMMON / 'GeelyInputSettings.kt').read_text()
        self.assertIn('getBoolean("manual_enabled", false)', settings)
        self.assertIn('GeelyVendorPolicy.geelyInput &&', settings)
        self.assertIn('GeelyCapability.inputAllowed(GeelyHeadUnit.detected, manualEnabled(context))', settings)
        capability = (SHARED / 'network/GeelyCapability.kt').read_text()
        for required in ('product.equals("geely_fx11", ignoreCase = true) &&',
                         'model.equals("CS11_A4", ignoreCase = true) &&',
                         'manufacturer.equals("DesaySV", ignoreCase = true) && sdkInt == 30',
                         'knownHardware || manualEnabled',
                         'resolved && enabled && applicationEnabled && exported && permissionGranted'):
            self.assertIn(required, capability)
        detection = (SHARED / 'network/GeelyHeadUnit.kt').read_text()
        self.assertIn('GeelyCapability.knownHardware(Build.PRODUCT, Build.MODEL, Build.MANUFACTURER, Build.VERSION.SDK_INT)', detection)
        self.assertIn('.getOrDefault(false)', detection)
        for forbidden in ('contains("geely")', 'contains("lynk")', 'identifiers.any'):
            self.assertNotIn(forbidden, detection)

    def test_wheel_stop_invalidates_callbacks_and_releases_listener_and_binding(self):
        text = (COMMON / 'GeelyWheelKeys.kt').read_text()
        stop = text.split('fun stop() {', 1)[1].split('private fun current(', 1)[0]
        self.assertLess(stop.index('active = null'), stop.index('handler.post'))
        self.assertIn('generation += 1', stop)
        self.assertIn('handler::removeCallbacks', stop)
        for required in ('generation == connection.session.generation',
                         'connection.session.connection === connection',
                         'manager.unregisterListener(listener, connection.session.context.packageName)',
                         'connection.session.context.unbindService(connection)',
                         'if (!current(connection) || !connection.subscribed || keyCode !in connection.codes) return',
                         'if (session.retryCount >= MAX_RETRIES)',
                         'retry = result.exceptionOrNull() !is SecurityException',
                         'if (AirPlayPersistence.loadCarBluetoothAudio(connection.session.context)) return'):
            self.assertIn(required, text)

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

    def test_foreground_media_keys_require_an_active_session_and_yield_calls_and_bluetooth(self):
        host = (COMMON / 'CarPlayHostActivity.kt').read_text()
        for required in ('foregroundMediaKeys.consumeContinuation(event)',
                         'foregroundMediaKeys.dispatch(event,',
                         'sessionActive = activeAirPlaySession != null',
                         'inCall = inCall(this)',
                         'bluetoothAudio = AirPlayPersistence.loadCarBluetoothAudio(this)',
                         'CarPlayMediaKeys.onForegroundMediaKey(mediaPress, controller)'):
            self.assertIn(required, host)
        media = (COMMON / 'CarPlayMediaKeys.kt').read_text()
        for required in ('CarPlayMediaButton.forCarPlayWindow(event.keyCode, sessionActive, inCall)',
                         'active.activeAirPlaySessionToken() ?: return rejected("no-session")',
                         'if (inCall(context) || AirPlayPersistence.loadCarBluetoothAudio(context)) return rejected("call-or-avrcp")',
                         'if (active !== expectedController || !stillCurrent()) return rejected("stale-owner")',
                         'mediaDispatch.shouldSend(token, index, route,',
                         'if (consumeContinuation(event)) return true',
                         'event.repeatCount != 0 || bluetoothAudio'):
            self.assertIn(required, media)

    def test_language_defaults_migration_and_script_precedence_are_preserved(self):
        # Assert the reviewed behavior rather than pinning unrelated whitespace/comments to a blob.
        text = (COMMON / 'AppLocale.kt').read_text()
        for required in ('if (locales.isEmpty) return SYSTEM',
                         '.getString(KEY_LANGUAGE, SYSTEM)?.takeIf { it in ALL } ?: SYSTEM',
                         'if (prefs.getBoolean(KEY_MIGRATED, false)) return',
                         'if (manager.applicationLocales.isEmpty && previous != null)',
                         'locale(language)?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()',
                         'val locale = locale(preference(context)) ?: return context',
                         'return when (selected.script)',
                         '"Hant" -> TRADITIONAL_CHINESE',
                         '"Hans" -> SIMPLIFIED_CHINESE',
                         'else -> if (selected.country in setOf("TW", "HK", "MO"))'):
            self.assertIn(required, text)
        self.assertNotIn('getString(KEY_LANGUAGE, SIMPLIFIED_CHINESE)', text)
        self.assertLess(text.index('return when (selected.script)'), text.index('selected.country'))

if __name__ == '__main__':
    unittest.main()
