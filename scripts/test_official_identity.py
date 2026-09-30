#!/usr/bin/env python3
"""Offline tests use clearly synthetic bytes, never a real APK or private key."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

from geely_version import read_version
from prepare_official_identity import MAX_ENTRY_BYTES, NAMES, ROOT, provision, validate_metadata


class OfficialIdentityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "synthetic.zip"
        self.output = self.root / "output"

    def archive(self, entries=None):
        entries = entries if entries is not None else [(f"assets/offline-mfi/{name}", b"SYNTHETIC-NOT-A-KEY") for name in NAMES]
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apk, "w") as archive:
                for name, data in entries:
                    archive.writestr(name, data)
        return hashlib.sha256(self.apk.read_bytes()).hexdigest()

    def test_metadata_matches_current_upstream(self):
        metadata = json.loads((ROOT / "gradle/official-apk.json").read_text())
        self.assertEqual(validate_metadata(metadata, read_version()), metadata)
        for field, value in (("url", "https://example.org/package.apk"), ("sha256", ""),
                             ("upstreamVersionName", "99.0.0")):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_metadata(dict(metadata, **{field: value}), read_version())

    def test_synthetic_success_writes_only_exact_names(self):
        digest = self.archive()
        provision(self.apk, self.output, digest, owner_approved=True)
        self.assertEqual(sorted(p.name for p in (self.output / "offline-mfi").iterdir()), sorted(NAMES))
        for name in NAMES:
            target = self.output / "offline-mfi" / name
            self.assertEqual(target.read_bytes(), b"SYNTHETIC-NOT-A-KEY")
            self.assertEqual(target.stat().st_mode & 0o777, 0o600)

    def test_acknowledgement_and_digest_fail_before_writes(self):
        digest = self.archive()
        with self.assertRaises(ValueError):
            provision(self.apk, self.output, digest)
        with self.assertRaises(ValueError):
            provision(self.apk, self.output, "0" * 64, owner_approved=True)
        self.assertFalse(self.output.exists())

    def test_duplicate_missing_empty_oversized_rejected_before_writes(self):
        normal = [(f"assets/offline-mfi/{name}", b"SYNTHETIC") for name in NAMES]
        cases = [normal[:1], normal + normal[:1],
                 [(normal[0][0], b""), normal[1]],
                 [(normal[0][0], b"X" * (MAX_ENTRY_BYTES + 1)), normal[1]]]
        for entries in cases:
            with self.subTest(lengths=[len(data) for _, data in entries]):
                digest = self.archive(entries)
                with self.assertRaises(ValueError):
                    provision(self.apk, self.output, digest, owner_approved=True)
                self.assertFalse(self.output.exists())

    def test_existing_output_and_source_tree_are_rejected(self):
        digest = self.archive()
        self.output.mkdir()
        marker = self.output / "keep.txt"
        marker.write_text("keep")
        with self.assertRaises(ValueError):
            provision(self.apk, self.output, digest, owner_approved=True)
        self.assertEqual(marker.read_text(), "keep")
        with self.assertRaises(ValueError):
            provision(self.apk, ROOT / "never-created-synthetic-output", digest, owner_approved=True)

    def test_owner_workflow_is_only_real_provisioning_entry(self):
        workflow = (ROOT / ".github/workflows/release-geely.yml").read_text()
        ci = (ROOT / ".github/workflows/android.yml").read_text()
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("--owner-approved-provisioning", workflow)
        self.assertIn("--proto-redir '=https'", workflow)
        self.assertIn("--check-metadata", workflow)
        for old_secret in ("DIPLAY_AUTH_IDENTITY_BASE64", "DIPLAY_AUTH_CERTIFICATE_BASE64"):
            self.assertNotIn(old_secret, workflow)
        for sensitive in ("--owner-approved-provisioning", "curl ", "secrets."):
            self.assertNotIn(sensitive, ci)


if __name__ == "__main__":
    unittest.main()
