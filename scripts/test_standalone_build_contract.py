#!/usr/bin/env python3
"""Check non-secret build wiring without running Gradle or accessing key material."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class StandaloneBuildContractTest(unittest.TestCase):
    def test_source_only_mode_is_opt_in_and_disables_app_debug_signing(self):
        script = (ROOT / "scripts/source-only.init.gradle").read_text()
        self.assertIn("plugins.withId('com.android.application')", script)
        self.assertIn("android.buildTypes.getByName('debug').signingConfig = null", script)
        self.assertIn("throw new GradleException", script)
        for name in ("DIPLAY_AUTH_ASSETS_DIR", "ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
                     "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"):
            self.assertIn(name, script)

    def test_official_release_entry_requires_complete_external_inputs(self):
        build = (ROOT / "mobile/build.gradle.kts").read_text()
        self.assertIn('dependsOn(verifyStandaloneAuthentication, verifyExistingReleaseSigningInputs)', build)
        self.assertIn('setOf("preReleaseBuild", "validateSigningRelease", "packageRelease")', build)
        self.assertIn('notCompatibleWithConfigurationCache(', build)
        self.assertIn('keystore.isFile && keystore.length() > 0', build)
        self.assertIn('!keystore.toPath().startsWith(source)', build)
        self.assertIn('!directory.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())', build)
        self.assertNotIn('assembleStandaloneRelease', build)

    def test_external_authentication_directory_has_strict_two_file_allowlist(self):
        build = (ROOT / "mobile/build.gradle.kts").read_text()
        self.assertIn('listOf("identity.pk8", "certificate.p7b")', build)
        self.assertIn('externalAssets?.files.orEmpty().all { it.canonicalFile in allowed }', build)
        self.assertIn('!Files.isSymbolicLink(input.toPath())', build)
        self.assertIn('paths.noneMatch { Files.isSymbolicLink(it) }', build)

    def test_public_ci_is_unsigned_and_never_publishes_an_apk(self):
        workflow = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("-I scripts/source-only.init.gradle", workflow)
        self.assertIn("contents: read", workflow)
        self.assertNotIn("secrets.", workflow)
        self.assertNotIn("assembleStandalone", workflow)
        self.assertNotIn(".apk", workflow)

    def test_wiring_has_no_credential_creation_import_or_output(self):
        wiring = (ROOT / "mobile/build.gradle.kts").read_text()
        wiring += (ROOT / "scripts/source-only.init.gradle").read_text()
        for forbidden in ("keytool", "generateKeyPair", "readBytes", "writeBytes", "GITHUB_ENV",
                          "base64", "println(", "logger."):
            self.assertNotIn(forbidden, wiring)


if __name__ == "__main__":
    unittest.main()
