"""Source-only restoration guards; no network or private inputs."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReviewRestoreContractTest(unittest.TestCase):
    def test_public_credential_scan_captures_paths_before_execution(self):
        text = (ROOT / 'mobile/build.gradle.kts').read_text()
        task = text.split('val rejectBundledCredentials by tasks.registering {', 1)[1].split('tasks.named("preBuild")', 1)[0]
        setup, action = task.split('    doLast {', 1)
        self.assertIn('val inputPath = localAuthenticationInputPath', setup)
        self.assertIn('val sourceRoot = rootProject.projectDir.canonicalFile', setup)
        self.assertNotIn('rootProject', action)
        self.assertNotIn('localAuthenticationInputPath', action)
        self.assertIn('inputPath?.let', action)
        self.assertIn('sourceRoot.toPath()', action)
        self.assertIn('check(unexpected.isEmpty())', action)
        self.assertIn('Files.isSymbolicLink', action)
        self.assertIn('externalAssets?.files.orEmpty().all', action)
        self.assertIn('dependsOn(rejectBundledCredentials)', text)

    def test_adb_credential_load_has_no_storage_or_key_generation(self):
        text = (ROOT / 'shared/src/main/java/com/shilapi/xcertplay/adb/AdbKeys.kt').read_text()
        for forbidden in ('java.io.File', 'KeyPairGenerator', 'KeyFactory',
                          'readBytes', 'writeBytes', 'noBackupFilesDir', 'mkdirs',
                          'adbkey.private', 'adbkey.public', 'PKCS8EncodedKeySpec'):
            self.assertNotIn(forbidden, text)
        self.assertIn('fun load(context: Context): KeyPair', text)
        self.assertIn('error("ADB credential loading is unavailable in the Geely build")', text)

    def test_fork_cannot_deploy_upstream_pages(self):
        text = (ROOT / '.github/workflows/pages.yml').read_text()
        self.assertIn("if: github.repository == 'shihabal3amri/DiPlay'", text)

    def test_preserve_geely_issue_routing_and_privacy(self):
        text = (ROOT / '.github/ISSUE_TEMPLATE/config.yml').read_text()
        self.assertIn('xiaoshengwpp/DiPlay-Geely', text)
        self.assertTrue((ROOT / '.github/ISSUE_TEMPLATE/compatibility.yml').is_file())
        self.assertIn('VIN', (ROOT / '.github/ISSUE_TEMPLATE/bug.yml').read_text())

    def test_preserve_debug_identity_without_byd_visibility(self):
        text = (ROOT / 'mobile/src/debug/AndroidManifest.xml').read_text()
        self.assertIn('DiPlay Geely Dev', text)
        self.assertNotIn('com.byd.clusterdebug', text)
        self.assertNotIn('android:exported="true"', text)

    def test_legacy_extraction_release_path_is_not_restored(self):
        for name in ('.github/workflows/release-geely.yml',
                     'scripts/prepare_official_identity.py',
                     'gradle/official-apk.json'):
            self.assertFalse((ROOT / name).exists(), name)

    def test_review_ci_does_not_publish_or_use_secrets(self):
        text = (ROOT / '.github/workflows/android.yml').read_text()
        self.assertIn('contents: read', text)
        self.assertIn('-I scripts/source-only.init.gradle', text)
        for forbidden in ('secrets.', 'contents: write', 'assembleRelease', '.apk'):
            self.assertNotIn(forbidden, text)


if __name__ == '__main__':
    unittest.main()
