#!/usr/bin/env python3
"""Offline checks use synthetic ZIP metadata and public tool-output fixtures only."""
import io
from pathlib import Path
import unittest
import zipfile

from check_geely_release_history import validate_history, validate_previous_manifest
from validate_geely_apk import (
    APPLICATION_ID, AUTH_ENTRIES, REQUIRED_ABIS, REQUIRED_LIBRARIES,
    check_archive, check_badging, check_signature, normalize_fingerprint,
)

ROOT = Path(__file__).resolve().parents[1]
METADATA = {'versionName': '0.2.7-geely.2', 'versionCode': 26002, 'tag': 'v0.2.7-geely.2'}
FINGERPRINT = 'ab' * 32
BADGING = f"package: name='{APPLICATION_ID}' versionCode='26002' versionName='0.2.7-geely.2' platformBuildVersionName='16'\nsdkVersion:'28'\ntargetSdkVersion:'37'\n"
SIGNATURE = f"Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\nNumber of signers: 1\nSigner #1 certificate DN: CN=Geely Release\nSigner #1 certificate SHA-256 digest: {FINGERPRINT}\n"


def synthetic_archive(omit=None, extra=None):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as archive:
        for name in AUTH_ENTRIES:
            if name != omit:
                archive.writestr(name, b'synthetic-test-placeholder-not-a-key')
        for abi in REQUIRED_ABIS:
            for library in REQUIRED_LIBRARIES:
                name = f'lib/{abi}/{library}'
                if name != omit:
                    archive.writestr(name, b'synthetic-test-placeholder-not-native-code')
        for name, content in (extra or {}).items():
            archive.writestr(name, content)
    buffer.seek(0)
    return buffer


class ApkContractTest(unittest.TestCase):
    def test_public_metadata_and_signature_match(self):
        check_badging(BADGING, METADATA)
        self.assertEqual(check_signature(SIGNATURE, FINGERPRINT.upper()), FINGERPRINT)
        self.assertEqual(normalize_fingerprint(':'.join(['AB'] * 32)), FINGERPRINT)
        self.assertEqual(check_archive(synthetic_archive()), sorted(REQUIRED_ABIS))

    def test_wrong_package_debug_variant_version_and_sdk_rejected(self):
        for badging in (BADGING.replace(APPLICATION_ID, 'com.shihab.diplay'),
                        BADGING.replace(APPLICATION_ID, APPLICATION_ID + '.hudtest'),
                        BADGING.replace("versionCode='26002'", "versionCode='26001'"),
                        BADGING.replace('0.2.7-geely.2', '0.2.7-geely.1'),
                        BADGING + 'application-debuggable\n',
                        BADGING.replace("sdkVersion:'28'", "sdkVersion:'27'"),
                        BADGING.replace("targetSdkVersion:'37'", "targetSdkVersion:'36'")):
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                check_badging(badging, METADATA)

    def test_unexpected_signer_debug_key_and_weak_signature_rejected(self):
        for signature in (SIGNATURE.replace(FINGERPRINT, 'cd' * 32),
                          SIGNATURE.replace('Number of signers: 1', 'Number of signers: 2'),
                          SIGNATURE.replace('CN=Geely Release', 'CN=Android Debug'),
                          SIGNATURE.replace(': true', ': false'),
                          SIGNATURE.replace('v2 scheme', 'v1 scheme')):
            with self.subTest(signature=signature), self.assertRaises(ValueError):
                check_signature(signature, FINGERPRINT)
        for invalid in ('', 'ab', '$GITHUB_TOKEN', 'ab' * 33):
            with self.assertRaises(ValueError):
                normalize_fingerprint(invalid)

    def test_authentication_presence_only_no_contents_read(self):
        for name in AUTH_ENTRIES:
            with self.assertRaises(ValueError):
                check_archive(synthetic_archive(omit=name))
            with self.assertRaises(ValueError):
                check_archive(synthetic_archive(omit=name, extra={name: b''}))
        with self.assertRaises(ValueError):
            check_archive(synthetic_archive(extra={'assets/offline-mfi/extra.txt': b'not allowed'}))
        source = (ROOT / 'scripts/validate_geely_apk.py').read_text()
        for forbidden in ('archive.read(', 'archive.open(', 'extract(', 'extractall('):
            self.assertNotIn(forbidden, source)

    def test_missing_arm_libraries_extra_abi_and_keystore_rejected(self):
        for abi in REQUIRED_ABIS:
            with self.assertRaises(ValueError):
                check_archive(synthetic_archive(omit=f'lib/{abi}/libxcertplay_i2c.so'))
        for extra in ({'lib/x86/libxcertplay_i2c.so': b'placeholder'},
                      {'assets/signing.jks': b'placeholder'},
                      {'assets/another.pk8': b'placeholder'},
                      {'../bad': b'placeholder'}):
            with self.assertRaises(ValueError):
                check_archive(synthetic_archive(extra=extra))


class ReleaseHistoryTest(unittest.TestCase):
    def test_first_and_next_release(self):
        self.assertEqual(validate_history(METADATA, [[]]), '')
        previous = [[{'tag_name': 'v0.2.7'}, {'tag_name': 'v0.2.7-geely.1'}]]
        self.assertEqual(validate_history(METADATA, previous), 'v0.2.7-geely.1')

    def test_incomplete_draft_is_reserved_but_not_previous_published_baseline(self):
        current = dict(METADATA, tag='v0.2.7-geely.3', versionName='0.2.7-geely.3', versionCode=26003)
        history = [[{'tag_name': 'v0.2.7-geely.1', 'draft': False}, {'tag_name': 'v0.2.7-geely.2', 'draft': True}]]
        self.assertEqual(validate_history(current, history), 'v0.2.7-geely.1')

    def test_previous_package_signer_and_code_stay_update_compatible(self):
        previous = {'applicationId': APPLICATION_ID, 'versionCode': 26001, 'signerCertificateSha256': FINGERPRINT}
        validate_previous_manifest(METADATA, previous, FINGERPRINT)
        for field, value in (('applicationId', 'com.shihab.diplay'), ('versionCode', 26002), ('versionCode', '26001'), ('signerCertificateSha256', 'cd' * 32)):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_previous_manifest(METADATA, dict(previous, **{field: value}), FINGERPRINT)

    def test_existing_draft_newer_and_malformed_geely_tags_rejected(self):
        for release in ({'tag_name': METADATA['tag']},
                        {'tag_name': METADATA['tag'], 'draft': True},
                        {'tag_name': 'v0.2.8-geely.1'},
                        {'tag_name': 'v0.2.7-geely.3'},
                        {'tag_name': 'v0.2.7-geely.1000'}):
            with self.assertRaises(ValueError):
                validate_history(METADATA, [[release]])


class WorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / '.github/workflows/release-geely.yml').read_text()

    def test_owner_only_manual_main_protected_environment(self):
        self.assertIn('workflow_dispatch:', self.workflow)
        for trigger in ('\n  push:', '\n  pull_request:', '\n  schedule:', '\n  workflow_run:'):
            self.assertNotIn(trigger, self.workflow)
        for guard in ("github.repository == 'xiaoshengwpp/DiPlay-Geely'", 'github.actor == github.repository_owner',
                      'github.triggering_actor == github.repository_owner', "github.ref == 'refs/heads/main'",
                      'environment: geely-release', "vars.GEELY_RELEASE_ENABLED", 'PUBLISH-EXTRACTABLE-IDENTITY',
                      'test "$EXPECTED_SOURCE_SHA" = "$GITHUB_SHA"', 'cancel-in-progress: false'):
            self.assertIn(guard, self.workflow)

    def test_runner_context_is_not_used_in_job_level_env(self):
        # GitHub evaluates job env before a runner is available. YAML parsing
        # alone cannot catch this Actions expression-context error.
        job_env = self.workflow.split('    env:\n', 1)[1].split('    steps:', 1)[0]
        self.assertNotIn('${{ runner.', job_env)
        self.assertIn("printf 'GRADLE_USER_HOME=%s/geely-gradle\\n'", self.workflow)
        self.assertIn("printf 'DIST=%s/geely-dist\\n'", self.workflow)
        self.assertLess(self.workflow.index('name: Initialize runner-local paths'),
                        self.workflow.index('name: Require explicit owner activation'))

    def test_no_credential_generation_caches_automatic_artifact_uploads_or_overwrites(self):
        for forbidden in ('keytool', 'generateKeyPair', 'build-beta.py', 'actions/cache', 'setup-gradle',
                          'upload-artifact', 'download-artifact', 'gh secret set', '--clobber', '--force',
                          'release delete', 'git push', '--method PATCH', 'set -x', '--debug', '--scan'):
            self.assertNotIn(forbidden, self.workflow)
        self.assertIn('persist-credentials: false', self.workflow)
        self.assertIn('--no-daemon --no-configuration-cache --no-build-cache :mobile:assembleStandaloneRelease', self.workflow)
        self.assertIn('if: always()', self.workflow)
        self.assertIn('rm -rf "$RUNNER_TEMP"/geely-private.*', self.workflow)
        self.assertIn('unset SIGNING_KEYSTORE_BASE64', self.workflow)

    def test_release_is_signed_validated_and_normal(self):
        self.assertLess(self.workflow.index('scripts/validate_geely_apk.py'), self.workflow.index('gh release create'))
        self.assertIn('scripts/geely_version.py --tag "$RELEASE_TAG"', self.workflow)
        self.assertIn('--previous "$RUNNER_TEMP/geely-previous.properties"', self.workflow)
        self.assertIn('--method POST "repos/$GITHUB_REPOSITORY/git/refs"', self.workflow)
        self.assertIn('--verify-tag --draft --prerelease=false', self.workflow)
        self.assertIn('--draft=false --prerelease=false --latest', self.workflow)
        self.assertIn("assets[path.name]['digest'] == digest", self.workflow)
        self.assertIn('for stage in draft published; do', self.workflow)
        self.assertIn('--pattern release-manifest.json', self.workflow)
        self.assertIn('validate_previous_manifest(', self.workflow)
        self.assertIn('--gradle-metadata mobile/build/outputs/geely/version.json', self.workflow)
        self.assertIn('Physical Xingyue L/head-unit compatibility and iPhone pairing have not been established', self.workflow)

    def test_existing_source_only_ci_is_unchanged_in_purpose(self):
        ci = (ROOT / '.github/workflows/android.yml').read_text()
        self.assertNotIn('secrets.', ci)
        self.assertNotIn('assembleStandalone', ci)
        self.assertNotIn('release create', ci)
        self.assertIn('-I scripts/source-only.init.gradle', ci)
        self.assertIn('contents: read', ci)
        self.assertIn('Validate GitHub Actions expression contexts', ci)
        self.assertIn('"$tool_dir/actionlint" -shellcheck= -pyflakes=', ci)


if __name__ == '__main__':
    unittest.main()
