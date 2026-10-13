#!/usr/bin/env python3
"""Unsupported BYD, ADB and Telecom backends stay absent beside gated Geely capabilities."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
S = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'

class GeelyNoopBackendsTest(unittest.TestCase):
    def test_no_shared_source_loads_adb_credentials_or_constructs_a_live_client(self):
        for path in S.rglob('*'):
            if path.suffix not in ('.kt', '.java'):
                continue
            text = path.read_text()
            self.assertNotIn('AdbKeys.load(', text, str(path))
            # The compatibility class declaration is allowed; callers must not construct it.
            without_declaration = re.sub(r'\bclass\s+LocalAdb\s*\(', 'class LocalAdbDeclaration(', text)
            self.assertNotRegex(without_declaration, r'\bLocalAdb\s*\(', str(path))

    def test_hud_and_telecom_ship_no_platform_transport_or_vehicle_mutation_implementation(self):
        forbidden = (
            'Socket(', 'InetSocketAddress(', 'Class.forName(', '.transact(', 'Parcel.obtain(',
            '.sendBroadcast(', '.bindService(', '.getSystemService(', '.getSharedPreferences(',
            'registerPhoneAccount(', '.placeCall(', '.setActive(', '.registerAudioRecordingCallback(',
            '.registerAudioPlaybackCallback(', 'Runtime.getRuntime(', 'ProcessBuilder(',
            'RandomAccessFile(', '.writeText(', '.delete(', '.renameTo(', 'System.exit(',
        )
        for folder in ('hud', 'telecom'):
            for path in (S / folder).glob('*'):
                if path.suffix not in ('.kt', '.java'):
                    continue
                text = path.read_text()
                for token in forbidden:
                    self.assertNotIn(token, text, f'{path.name}: {token}')

    def test_disabled_shell_and_ambient_interfaces_are_unconditional(self):
        shell = (S/'hud/BydAdbShell.kt').read_text()
        self.assertIn('fun run(context: Context, command: String): String? = null', shell)
        self.assertIn('fun run(context: Context, command: String, readTimeoutMillis: Int?): String? = null', shell)
        ambient = (S/'hud/BydAmbientLightClient.kt').read_text()
        for name in ('read', 'begin', 'apply', 'stop', 'stopAtMinimum', 'cancelGeneration'):
            self.assertRegex(ambient, rf'fun {name}\([^\n]*\): CompletableFuture<[^\n]+> = disabledFuture\(\)')
        for token in ('LocalAdb', 'SecureRandom', 'exchangeBounded(', 'getApplicationContext(', 'ThreadPool'):
            self.assertNotIn(token, ambient)
        worker = (S/'hud/BydAmbientLightWorker.kt').read_text()
        self.assertIn('println("error byd-ambient-lamp-disabled")', worker)
        self.assertIn('private fun runOwned(token: String) = Unit', worker)
        self.assertIn('init { error("byd-ambient-lamp-disabled") }', worker)
        for token in ('BYDAutoDeviceManager', 'getInstance', 'setIntArray', 'holdAtMinimum', 'awaitReadback', 'System.in'):
            self.assertNotIn(token, worker)

    def test_fce_and_phone_live_entry_points_have_fixed_disabled_results(self):
        fce = (S/'hud/FceCarPropertyWriter.kt').read_text()
        self.assertIn('fun setInt(propertyId: Int, value: Int): Boolean = false', fce)
        self.assertIn('fun isAvailable(): Boolean = false', fce)
        self.assertIn('fun lookup(): IBinder? = null', fce)
        bridge = (S/'hud/FceClusterBridge.kt').read_text()
        for signature in ('fun initialize(appContext: Context) = Unit', 'fun onFrame(frame: Iap2Frame) = Unit',
                          'fun clear() = Unit', 'fun tick() = Unit', 'fun refreshLocked() = Unit'):
            self.assertIn(signature, bridge)
        route = (S/'telecom/PhoneAudioRoute.kt').read_text().split('object PhoneAudioRoute {', 1)[1]
        for signature in ('fun attach(context: Context) = Unit', 'fun carPlayCall(active: Boolean) = Unit',
                          'fun settingsChanged(context: Context) = Unit', 'fun carPlayCallsUseCarAudio(): Boolean = false'):
            self.assertIn(signature, route)
        backend = (S/'telecom/TelecomPhoneAudioBackend.kt').read_text()
        self.assertIn('override fun start(): Boolean = false', backend)
        self.assertIn('private fun place(): Boolean = false', backend)
        self.assertIn('Connection.createFailedConnection(', backend)
        self.assertNotIn('PhoneAudioConnection()', backend)

    def test_vendor_settings_cannot_read_write_or_discover_a_saved_opt_in(self):
        text = (S/'hud/BydOutputSettings.kt').read_text()
        self.assertNotIn('prefs(', text)
        self.assertNotIn('packageManager', text)
        for name in ('enabled', 'phoneAudioRoute', 'phoneAudioRouteApps', 'fceCluster', 'available', 'navigationAvailable'):
            self.assertIn(f'fun {name}(context: Context): Boolean = false', text)
        for name in ('setPhoneAudioRoute', 'setPhoneAudioRouteApps', 'setFceCluster'):
            self.assertIn(f'fun {name}(context: Context, enabled: Boolean) = Unit', text)

    def test_adb_diagnostic_model_cannot_probe_or_authorize_a_transport(self):
        text = (S/'adb/AdbCapability.kt').read_text()
        for required in ('fun snapshot(developerSettingEnabled: Boolean?): Snapshot',
                         'val localTransport: State = State.NOT_IMPLEMENTED',
                         'val ownerComputerTransport: State = State.UNKNOWN',
                         'A setting is not a connection or authorization'):
            self.assertIn(required, text)
        for forbidden in ('import android.', 'LocalAdb', 'AdbKeys', 'Runtime.getRuntime(',
                          'ProcessBuilder(', 'Socket(', 'getSystemService(',
                          'getSharedPreferences(', 'Settings.Global', 'Settings.Secure',
                          'settings put', 'pm grant', 'appops set', 'adb connect', 'adb pair'):
            self.assertNotIn(forbidden, text)

    def test_no_oem_phone_backend_is_reported_as_active_or_constructed(self):
        text = (S/'telecom/PhoneAudioRoute.kt').read_text().split('object PhoneAudioRoute {', 1)[1]
        self.assertIn('fun capability(): Capability = Capability.NO_VERIFIED_GEELY_BACKEND', text)
        self.assertIn('fun carPlayCallsUseCarAudio(): Boolean = false', text)
        for forbidden in ('PhoneAudioRouteController(', 'TelecomPhoneAudioBackend(', 'VoipCallWatcher(',
                          'Executors.', 'getSystemService(', 'registerPhoneAccount('):
            self.assertNotIn(forbidden, text)

    def test_disabled_mutating_helper_entry_points_are_inert(self):
        expectations = {
            'Byd13CatalogProbeMain.kt': 'fun main(args: Array<String>) = Unit',
            'BydClusterSong.kt': 'fun main(args: Array<String>) = Unit',
            'BydCallPopupTool.java': 'public static void main(String[] args) { }',
        }
        for name, signature in expectations.items():
            self.assertIn(signature, (S/'hud'/name).read_text())
        popup = (S/'hud/BydCallPopupGuard.kt').read_text()
        self.assertIn('fun exchange(command: String): String = "CANCELLED"', popup)
        self.assertNotIn('shell(', popup)
        self.assertNotIn('app_process', popup)
        ownership = (S/'hud/CarPlayCallWatchOwnership.kt').read_text()
        self.assertIn('fun claim(path: String, action: (File) -> Unit) = Unit', ownership)
        self.assertNotIn('action(', ownership)
        self.assertNotIn('locked(', ownership)

    def test_telecom_service_stays_unexported_disabled_and_unprivileged(self):
        a = '{http://schemas.android.com/apk/res/android}'
        manifest = ET.parse(ROOT/'shared/src/main/AndroidManifest.xml').getroot()
        permissions = {node.get(a+'name') for node in manifest.findall('uses-permission')}
        self.assertNotIn('android.permission.MANAGE_OWN_CALLS', permissions)
        service = next(node for node in manifest.find('application').findall('service')
                       if node.get(a+'name', '').endswith('.PhoneAudioConnectionService'))
        self.assertEqual('false', service.get(a+'enabled'))
        self.assertEqual('false', service.get(a+'exported'))

    def test_pure_codecs_models_and_negative_test_suites_are_retained(self):
        for relative in ('hud/BydAmbientLightPolicy.kt', 'hud/BydStandalonePackets.kt', 'hud/BydHudRouteState.kt',
                         'hud/FceClusterOutput.kt', 'adb/AdbPacket.kt'):
            self.assertTrue((S/relative).is_file(), relative)
        worker = (S/'hud/BydAmbientLightWorker.kt').read_text()
        for signature in ('fun reply(', 'fun parse(', 'fun snapshotForBegin(', 'fun readLine('):
            self.assertIn(signature, worker)
        tests = ROOT/'shared/src/test/java/com/shilapi/xcertplay'
        for relative in ('hud/GeelyVendorBoundaryTest.kt', 'hud/GeelyHardwareBoundary0216Test.kt',
                         'hud/GeelyAmbientAndCallBoundaryTest.kt', 'hud/GeelyHardwareBoundary0218Test.kt',
                         'telecom/PhoneAudioRouteControllerTest.kt', 'hud/FceClusterOutputTest.kt'):
            self.assertTrue((tests/relative).is_file(), relative)
            self.assertNotIn('@Ignore', (tests/relative).read_text())

if __name__ == '__main__':
    unittest.main()
