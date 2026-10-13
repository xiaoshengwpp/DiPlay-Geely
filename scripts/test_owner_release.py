"""Tests use ONLY inert text. They never download, generate or load genuine keys."""
import hashlib
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import owner_release as r


class OwnerReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / "source"
        self.source.mkdir()
        (self.source / "gradle").mkdir()
        (self.source / "gradle/geely-version.properties").write_text(
            "upstreamVersionName=0.2.18\nupstreamVersionCode=37\ngeelyRevision=2\n")
        self.apk = self.root / "inert.zip"

    def fixture(self, extra=()):
        with zipfile.ZipFile(self.apk, "w") as archive:
            for name in r.ASSETS:
                archive.writestr(name, "INERT SYNTHETIC TEXT, NOT A KEY OR CERTIFICATE")
            for name, content in extra:
                archive.writestr(name, content)
        return dict(r.LOCK, official_apk_bytes=self.apk.stat().st_size,
                    official_apk_sha256=hashlib.sha256(self.apk.read_bytes()).hexdigest())

    def extract(self, lock, destination=None):
        with patch.object(r, "LOCK", lock):
            r.extract(self.apk, destination or self.root / "auth", self.source)

    def test_source_version_matches_before_sensitive_steps(self):
        with patch.object(r.subprocess, "check_output", side_effect=[r.LOCK["source_commit"], ""]):
            r.check_source(self.source)

    def test_source_old_version_fails_before_sensitive_steps(self):
        path = self.source / "gradle/geely-version.properties"
        path.write_text(path.read_text().replace("geelyRevision=2", "geelyRevision=1"))
        with patch.object(r.subprocess, "check_output", side_effect=[r.LOCK["source_commit"], ""]):
            with self.assertRaisesRegex(ValueError, "Source version mismatch"):
                r.check_source(self.source)

    def test_source_missing_version_fails_before_sensitive_steps(self):
        (self.source / "gradle/geely-version.properties").unlink()
        with patch.object(r.subprocess, "check_output", side_effect=[r.LOCK["source_commit"], ""]):
            with self.assertRaises(FileNotFoundError):
                r.check_source(self.source)

    def test_source_wrong_upstream_fails_before_sensitive_steps(self):
        path = self.source / "gradle/geely-version.properties"
        path.write_text(path.read_text().replace("0.2.18", "0.2.19"))
        with patch.object(r.subprocess, "check_output", side_effect=[r.LOCK["source_commit"], ""]):
            with self.assertRaisesRegex(ValueError, "Source version mismatch"):
                r.check_source(self.source)

    def test_inert_success_exact_two_files(self):
        self.extract(self.fixture())
        r.validate_directory(self.root / "auth", self.source)
        self.assertEqual((self.root / "auth/offline-mfi/identity.pk8").stat().st_mode & 0o777, 0o600)

    def test_wrong_hash_fails_without_output(self):
        lock = self.fixture()
        lock["official_apk_sha256"] = "0" * 64
        with self.assertRaises(ValueError): self.extract(lock)
        self.assertFalse((self.root / "auth").exists())

    def test_wrong_size(self):
        lock = self.fixture(); lock["official_apk_bytes"] += 1
        with self.assertRaises(ValueError): self.extract(lock)

    def test_source_destination_rejected(self):
        with self.assertRaises(ValueError): self.extract(self.fixture(), self.source / "auth")

    def test_existing_destination_rejected(self):
        (self.root / "auth").mkdir()
        with self.assertRaises(ValueError): self.extract(self.fixture())

    def test_path_traversal_rejected_before_write(self):
        for path in ("../escape", "/absolute", "assets/../bad", "assets\\bad", "C:/bad"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                self.extract(self.fixture([(path, "INERT")]))
        self.assertFalse((self.root / "auth").exists())

    def test_extra_identity_rejected(self):
        with self.assertRaises(ValueError):
            self.extract(self.fixture([("assets/offline-mfi/extra.txt", "INERT")]))

    def test_duplicate_rejected(self):
        import warnings
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            lock = self.fixture([(r.ASSETS[0], "INERT")])
        with self.assertRaises(ValueError): self.extract(lock)

    def test_zip_symlink_rejected(self):
        entry = zipfile.ZipInfo("link")
        entry.create_system = 3; entry.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaises(ValueError): self.extract(self.fixture([(entry, "target")]))

    def test_parent_symlink_rejected(self):
        (self.root / "link").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError): self.extract(self.fixture(), self.root / "link/auth")

    def test_extra_input_and_input_symlink_rejected(self):
        self.extract(self.fixture())
        extra = self.root / "auth/extra"; extra.write_text("INERT")
        with self.assertRaises(ValueError): r.validate_directory(self.root / "auth", self.source)
        extra.unlink()
        target = self.root / "auth/offline-mfi/identity.pk8"; target.unlink(); target.symlink_to(self.apk)
        with self.assertRaises(ValueError): r.validate_directory(self.root / "auth", self.source)

    def test_public_metadata(self):
        badging = "package: name='io.github.xiaoshengwpp.diplay.geely' versionCode='37002' versionName='0.2.18-geely.2'"
        signatures = "Signer #1 certificate SHA-256 digest: " + "a" * 64
        self.assertEqual(r.parse_public_metadata(badging, signatures), "a" * 64)
        for bad, sig in ((badging.replace("37002", "37"), signatures), (badging, ""), (badging, signatures + "\n" + signatures)):
            with self.assertRaises(ValueError): r.parse_public_metadata(bad, sig)

    def verify_fixture(self, extra=(), commit=None, dirty="", badging=None, mismatch=False):
        self.fixture([(f"lib/{abi}/libINERT.so", "INERT NOT A LIBRARY") for abi in r.LOCK["abis"]] + list(extra))
        runtime = self.root / "runtime"
        (runtime / "offline-mfi").mkdir(parents=True)
        for name in r.ASSETS:
            (runtime / name.removeprefix("assets/")).write_text("MISMATCH INERT TEXT" if mismatch else "INERT SYNTHETIC TEXT, NOT A KEY OR CERTIFICATE")
        calls = [commit or r.LOCK["source_commit"], dirty,
                 badging or "package: name='io.github.xiaoshengwpp.diplay.geely' versionCode='37002' versionName='0.2.18-geely.2'",
                 "Signer #1 certificate SHA-256 digest: " + "a" * 64]
        with patch.object(r.subprocess, "check_output", side_effect=calls):
            r.verify(self.apk, self.source, runtime, "mock-aapt", "mock-apksigner", self.root / "delivery")

    def test_inert_verification_emits_exact_three_files(self):
        import json
        self.verify_fixture()
        output = self.root / "delivery"
        self.assertEqual({p.name for p in output.iterdir()}, {"DiPlay-0.2.18-geely.2.apk", "SHA256SUMS.txt", "build-manifest.json"})
        manifest = json.loads((output / "build-manifest.json").read_text())
        self.assertEqual(manifest["signing_certificate_sha256"], "a" * 64)
        self.assertFalse(manifest["vehicle_tested"])
        self.assertNotIn("INERT", (output / "build-manifest.json").read_text())

    def test_verification_rejects_mismatched_runtime_asset(self):
        with self.assertRaises(ValueError): self.verify_fixture(mismatch=True)
        self.assertFalse((self.root / "delivery").exists())

    def test_verification_rejects_wrong_commit(self):
        with self.assertRaises(ValueError): self.verify_fixture(commit="0" * 40)
        self.assertFalse((self.root / "delivery").exists())

    def test_verification_rejects_dirty_source(self):
        with self.assertRaises(ValueError): self.verify_fixture(dirty=" M build.gradle.kts")

    def test_verification_rejects_extra_abi(self):
        with self.assertRaises(ValueError): self.verify_fixture(extra=[("lib/x86/libINERT.so", "INERT")])

    def test_verification_rejects_loose_signing_container(self):
        with self.assertRaises(ValueError): self.verify_fixture(extra=[("assets/inert.jks", "INERT NOT A KEYSTORE")])

    def test_enabled_workflow_matches_reviewed_template(self):
        root = Path(__file__).resolve().parents[1]
        template = (root / "docs/templates/owner-release.yml.example").read_text()
        enabled = (root / ".github/workflows/owner-release.yml").read_text()
        self.assertEqual(template.splitlines()[1:], enabled.splitlines()[1:])
        self.assertEqual(enabled.count("workflow_dispatch:"), 1)
        self.assertIn("on:\n  workflow_dispatch:\n", enabled)
        self.assertEqual(enabled.count("default: false"), 2)
        self.assertIn("github.actor == 'xiaoshengwpp'", enabled)
        self.assertIn("github.ref == 'refs/heads/main'", enabled)
        self.assertIn("github.triggering_actor == 'xiaoshengwpp'", enabled)
        self.assertIn(r.LOCK["source_commit"], enabled)
        self.assertIn(r.LOCK["official_apk_url"], enabled)
        for automatic_event in ("push:", "pull_request:", "pull_request_target:", "schedule:", "workflow_run:", "repository_dispatch:"):
            self.assertNotIn(automatic_event, enabled)

    def test_workflow_checks_source_before_sensitive_steps(self):
        text = (Path(__file__).resolve().parents[1] / ".github/workflows/owner-release.yml").read_text()
        self.assertLess(text.index("check-source --source build-source"),
                        text.index("Owner-selected identity import and signed build"))
        self.assertIn(f"owner-DiPlay-{r.LOCK['version_name']}", text)
        self.assertIn(f"owner-delivery/DiPlay-{r.LOCK['version_name']}.apk", text)
        self.assertIn("retention-days: 1", text)
        self.assertIn("if-no-files-found: error", text)
        self.assertIn("persist-credentials: false", text)
        self.assertNotIn("contents: write", text)
        self.assertNotIn("actions/cache", text)
        self.assertNotIn("source_ref:", text)

    def test_template_is_inactive_and_manual_only(self):
        text = (Path(__file__).resolve().parents[1] / "docs/templates/owner-release.yml.example").read_text()
        self.assertIn("workflow_dispatch:", text)
        self.assertNotIn("  push:", text); self.assertNotIn("  pull_request:", text)
        self.assertIn("github.triggering_actor == 'xiaoshengwpp'", text)
        self.assertIn("contents: read", text)
        self.assertNotIn("contents: write", text)
        self.assertIn(":mobile:assembleRelease", text)
        self.assertNotIn(":mobile:assembleStandaloneRelease", text)


if __name__ == "__main__":
    unittest.main()

