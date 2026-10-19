"""Source-only supplements to real-facade Robolectric tests. No APK/device/network access."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
CODE = ROOT / 'common/src/main/java/com/shilapi/xcertplay'
ANDROID = '{http://schemas.android.com/apk/res/android}'

class Geely0216UiBoundaryTest(unittest.TestCase):
    def source(self, name):
        return (CODE / name).read_text()

    def test_exported_components_cannot_reactivate_hardware_or_map_or_video(self):
        app = ET.parse(ROOT / 'common/src/main/AndroidManifest.xml').getroot().find('application')
        for name in ['AdbClusterActivity', 'CarPlayVideoActivity', 'MapEmbedService', 'WheelKeyService', 'UsbAutoConfirmService']:
            with self.subTest(component=name):
                component = next(e for e in app if e.get(ANDROID + 'name', '').endswith('.' + name))
                self.assertEqual('false', component.get(ANDROID + 'enabled'))
                self.assertEqual('false', component.get(ANDROID + 'exported'))
        host = next(e for e in app if e.get(ANDROID + 'name', '').endswith('.CarPlayHostActivity'))
        self.assertEqual('fullSensor', host.get(ANDROID + 'screenOrientation'))

    def test_product_labels_remain_identifiably_geely(self):
        expected = {'values': 'DiPlay Geely', 'values-zh-rCN': 'DiPlay 星越 L', 'values-zh-rTW': 'DiPlay Geely'}
        for folder, label in expected.items():
            values = ET.parse(ROOT / 'mobile/src/main/res' / folder / 'strings.xml').getroot()
            self.assertEqual(label, next(e.text for e in values if e.get('name') == 'app_name'))

    def test_location_prompts_only_follow_explicit_setting_change(self):
        host = self.source('CarPlayHostActivity.kt')
        self.assertEqual(2, host.count('requestLocationPermission()'))  # declaration + explicit toggle
        toggle = host.split('private fun onLocationReportingChanged(checked: Boolean) {')[1].split('private fun ', 1)[0]
        self.assertIn('requestLocationPermission()', toggle)
        self.assertNotIn('val locationReady', host)
        self.assertIn('locationInformationEnabled = effectiveLocationReportingEnabled()', host)
        self.assertIn('locationReportingEnabled = effectiveLocationReportingEnabled()', host)
        prefs = self.source('AirPlayPersistence.kt')
        self.assertIn('.getBoolean(KEY_LOCATION_REPORTING_ENABLED, false)', prefs)

    def test_locale_default_does_not_remove_upstream_enforcement_or_saved_choices(self):
        source = self.source('AppLocale.kt')
        self.assertEqual(2, source.count('getString(KEY_LANGUAGE, SIMPLIFIED_CHINESE)'))
        self.assertNotIn('getString(KEY_LANGUAGE, SYSTEM)', source)
        self.assertIn('fun enforce(context: Context): Boolean', source)
        self.assertIn('if (prefs.getBoolean(KEY_MIGRATED, false)) return', source)
        self.assertIn('if (manager.applicationLocales.isEmpty && previous != null)', source)
        self.assertIn('locale(language)?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()', source)
        self.assertIn('.getInt(KEY, 100).takeIf { it in CHOICES } ?: 100', self.source('InterfaceSize.kt'))

    def test_navigation_override_is_nullable_and_explicit_zero_is_preserved(self):
        source = self.source('AirPlayPersistence.kt')
        self.assertIn('fun loadNavigationAudioChannel(context: Context): Int?', source)
        self.assertIn('if (!prefs.contains(KEY_NAVIGATION_AUDIO_CHANNEL)) return null', source)
        self.assertIn('fun saveNavigationAudioChannel(context: Context, channel: Int?)', source)
        self.assertIn('if (channel == null) editor.remove(KEY_NAVIGATION_AUDIO_CHANNEL)', source)
        ui = self.source('DiPlayActivity.kt')
        self.assertIn('NavigationChannelSelection(current)', ui)
        self.assertIn('if (selection.changed) onApply(selection.value)', ui)

    def test_upstream_updates_fail_before_network_or_file_creation(self):
        source = self.source('update/UpdateClient.kt')
        body = source.split('internal fun download(url: String, destination: File, onProgress: (Long, Long?) -> Unit) {', 1)[1]
        self.assertTrue(body.lstrip().startswith('if (!GeelyVendorPolicy.upstreamApkUpdates) throw IOException'))
        ui = self.source('DiPlayActivity.kt')
        for signature in ['private fun checkForUpdates() {', 'private fun downloadUpdate() {', 'private fun installUpdate() {', 'private fun installApk(file: File) {']:
            self.assertTrue(ui.split(signature, 1)[1].lstrip().startswith('if (!GeelyVendorPolicy.upstreamApkUpdates)'))
        manifest = (ROOT / 'common/src/main/AndroidManifest.xml').read_text()
        self.assertNotIn('android.permission.REQUEST_INSTALL_PACKAGES', manifest)

    def test_disabled_vendor_and_cached_update_controls_are_not_advertised(self):
        ui = self.source('DiPlayActivity.kt')
        self.assertIn('if (GeelyVendorPolicy.bydAdb && Build.VERSION.SDK_INT == 32)', ui)
        self.assertIn('availableUpdate = if (GeelyVendorPolicy.upstreamApkUpdates)', ui)
        self.assertIn('availableUpdate?.takeIf { GeelyVendorPolicy.upstreamApkUpdates }', ui)
        direct = ui.split('private fun openAvailableUpdate(release: UpdateRelease) {', 1)[1]
        self.assertTrue(direct.lstrip().startswith('if (!GeelyVendorPolicy.upstreamApkUpdates) return'))

    def test_new_automatic_mutators_have_guards_before_context_and_shell(self):
        entries = [
            ('WheelKeyService.kt', 'fun restoreIfNeeded(context: Context) {', 'bydAdb'),
            ('WheelKeyService.kt', 'override fun onServiceConnected() {', 'bydAdb'),
            ('WheelKeyService.kt', 'override fun onKeyEvent(event: KeyEvent): Boolean {', 'bydAdb'),
            ('UsbPermissionSetup.kt', 'fun run(): Result {', 'bydAdb'),
            ('UsbAutoConfirmService.kt', 'override fun onAccessibilityEvent(event: AccessibilityEvent?) {', 'bydAdb'),
            ('CarPlayHostActivity.kt', 'private fun startSidePanelTyres() {', 'bydTyres'),
            ('DiPlayActivity.kt', 'private fun showAmbientConfiguration() {', 'bydAmbientLamp'),
            ('DiPlayActivity.kt', 'private fun validateSavedVehicleConfigurationAutomatically() {', 'bydAdb'),
            ('DiPlayActivity.kt', 'private fun startCarHotspotOnLaunch() {', 'bydAdb'),
            ('CarPlayCallKeys.kt', 'fun install(context: Context) {', 'bydAdb'),
            ('DiPlayActivity.kt', 'private fun showClusterAccessSetup() {', 'bydCluster'),
            ('DiPlayActivity.kt', 'private fun openClusterSafeAreaEditor() {', 'bydCluster'),
            ('HotspotJoinControls.kt', 'private fun run(action: HotspotJoinRepair.Action) {', 'bydAdb'),
        ]
        for name, signature, capability in entries:
            with self.subTest(file=name, signature=signature):
                body = self.source(name).split(signature, 1)[1].lstrip()
                self.assertTrue(body.startswith(f'if (!GeelyVendorPolicy.{capability}) return'))

    def test_video_refusal_is_safe_through_direct_lifecycle_and_loader_calls(self):
        source = self.source('CarPlayVideoActivity.kt')
        create = source.split('override fun onCreate(savedInstanceState: Bundle?) {', 1)[1]
        self.assertLess(create.index('if (!GeelyVendorPolicy.videoWhileParked)'), create.index('ExoPlayer.Builder'))
        self.assertIn('if (::player.isInitialized) player.release()', source)
        self.assertIn('if (!GeelyVendorPolicy.videoWhileParked || !::player.isInitialized) return null', source)
        video = self.source('CarPlayVideo.kt')
        body = video.split('fun resolveOnIphone(url: String): LoadedUrl? {', 1)[1].lstrip()
        self.assertTrue(body.startswith('if (!GeelyVendorPolicy.videoWhileParked) return null'))
        self.assertIn('private val main by lazy', video)
        self.assertIn('private val sender by lazy', video)

    def test_debug_hud_components_and_receiver_are_disabled(self):
        app = ET.parse(ROOT / 'mobile/src/debug/AndroidManifest.xml').getroot().find('application')
        for name in ['StarterBridgeReceiver', 'StandaloneHudDemoActivity']:
            component = next(e for e in app if e.get(ANDROID + 'name', '').endswith('.' + name))
            self.assertEqual('false', component.get(ANDROID + 'enabled'))
            self.assertEqual('false', component.get(ANDROID + 'exported'))
        receiver = (ROOT / 'mobile/src/debug/java/com/shilapi/xcertplay/hud/StarterBridgeReceiver.kt').read_text()
        self.assertIn('if (!GeelyVendorPolicy.bydDiagnostics) return', receiver)

if __name__ == '__main__':
    unittest.main()
