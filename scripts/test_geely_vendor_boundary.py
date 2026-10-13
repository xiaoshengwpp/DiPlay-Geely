#!/usr/bin/env python3
"""Geely adapter availability and disabled OEM product boundaries; JVM suites test runtime effects."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
S = ROOT / 'shared/src/main/java/com/shilapi/xcertplay'
A = '{http://schemas.android.com/apk/res/android}'
class GeelyBoundaryContract(unittest.TestCase):
    def test_unsupported_vendor_policy_is_immutable_and_cannot_enable_saved_features(self):
        text = (S / 'hud/GeelyVendorPolicy.kt').read_text()
        for field in ('bydNavigation','bydCluster','bydClusterSong','launcherMapSharing',
                      'bydBattery','bydWheelSpeed','videoWhileParked','bydAdb',
                      'bydDiagnostics','bydTyres','bydAmbientLamp','upstreamApkUpdates',
                      'phoneAudioRoute','fceCluster'):
            self.assertRegex(text, rf'\bval {field}: Boolean = false\b')
            self.assertNotRegex(text, rf'\bvar {field}\b')
    def test_compiled_geely_adapters_are_available_without_claiming_runtime_support(self):
        text = (S / 'hud/GeelyVendorPolicy.kt').read_text()
        for field in ('geelyInput', 'geelyMicrophoneOverride', 'foregroundMediaKeys'):
            self.assertRegex(text, rf'\bval {field}: Boolean = true\b')
            self.assertNotRegex(text, rf'\bvar {field}\b')
    def test_map_video_and_automatic_accessibility_are_disabled(self):
        app = ET.parse(ROOT/'common/src/main/AndroidManifest.xml').getroot().find('application')
        for short in ('MapEmbedService','CarPlayVideoActivity','WheelKeyService','UsbAutoConfirmService'):
            matches = [e for e in app if e.get(A+'name','').endswith('.'+short)]
            self.assertEqual(len(matches),1,short)
            self.assertEqual(matches[0].get(A+'enabled'),'false',short)
            self.assertEqual(matches[0].get(A+'exported'),'false',short)
    def test_controller_sanitizes_forged_video_and_cluster_config(self):
        text=(S/'orchestration/CarPlayController.kt').read_text()
        self.assertIn('videoInCar = GeelyVendorPolicy.videoWhileParked && airPlayConfig.videoInCar',text)
        self.assertIn('cluster = if (GeelyVendorPolicy.bydCluster) airPlayConfig.cluster else null',text)
        self.assertIn('field = if (GeelyVendorPolicy.videoWhileParked) value else null',text)
    def test_local_adb_is_disabled_before_network_or_key_storage(self):
        text=(S/'adb/LocalAdb.kt').read_text()
        for signature in (
            'fun connect(mayAsk: Boolean): Access = Access.UNSUPPORTED',
            'fun shell(command: String): String? = null',
            'fun shell(command: String, readTimeoutMillis: Int): String? = null',
            'fun openShell(command: String): InteractiveShell? = null',
        ):
            self.assertIn(signature, text)
        for forbidden in ('Socket(', 'InetSocketAddress', 'AdbKeys.load(', 'AdbKeys.sign(',
                          'AdbKeys.publicKeyMessage(', 'getInputStream(', 'getOutputStream(',
                          'private val key:', 'private val host:', 'private val port:',
                          'fun handshake(', 'fun awaitApproval('):
            self.assertNotIn(forbidden, text)
        keys=(S/'adb/AdbKeys.kt').read_text().split('fun load(context: Context): KeyPair {',1)[1]
        self.assertTrue(keys.lstrip().startswith('check(GeelyVendorPolicy.bydAdb)'))
    def test_actual_audio_renderer_passes_the_saved_legacy_stream(self):
        text=(S/'media/AndroidMediaSink.kt').read_text().split('private fun mappedSelection(): AudioChannelSelection {',1)[1]
        self.assertIn('navigationStreamType = navigationStreamType,',text.split('private fun audioAttributesFor',1)[0])
        self.assertIn('AudioChannelMapper.outputStreamOverride',(S/'media/AndroidMediaSink.kt').read_text())
    def test_exact_latest_source_version(self):
        from geely_version import read_version
        data=read_version()
        self.assertEqual((data['upstreamVersionName'],data['upstreamVersionCode'],data['versionName'],data['versionCode']),
                         ('0.2.18',37,'0.2.18-geely.2',37002))
if __name__=='__main__':unittest.main()
