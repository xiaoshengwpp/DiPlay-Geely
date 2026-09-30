#!/usr/bin/env python3
"""Version arithmetic, monotonic release transitions and non-secret app wiring."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

from geely_version import MAX_VERSION_CODE, ROOT, parse_version, read_version, validate_transition


def version(name="0.2.7", code=26, revision=1):
    return parse_version(f"upstreamVersionName={name}\nupstreamVersionCode={code}\ngeelyRevision={revision}\n")


class GeelyVersionTest(unittest.TestCase):
    def test_metadata_preserves_upstream_base(self):
        metadata = version()
        self.assertEqual(metadata, {
            "upstreamVersionName": "0.2.7", "upstreamVersionCode": 26, "geelyRevision": 1,
            "versionName": "0.2.7-geely.1", "versionCode": 26001, "tag": "v0.2.7-geely.1",
        })

    def test_adaptation_revisions_increase_without_changing_upstream(self):
        previous = version()
        for revision in range(2, 1000):
            current = version(revision=revision)
            validate_transition(previous, current)
            self.assertEqual(current["versionCode"], 26000 + revision)
            previous = current

    def test_new_upstream_clears_all_previous_revision_codes(self):
        previous = version(revision=999)
        current = version("0.2.8", 27, 1)
        validate_transition(previous, current)
        self.assertGreater(current["versionCode"], previous["versionCode"])
        self.assertEqual(current["versionName"], "0.2.8-geely.1")

    def test_version_comparison_is_numeric(self):
        validate_transition(version("0.2.9", 28, 999), version("0.2.10", 29, 1))
        validate_transition(version("0.9.99", 30, 999), version("0.10.0", 31, 1))

    def test_bad_transitions_fail_closed(self):
        pairs = [
            (version(), version()),
            (version(revision=2), version()),
            (version(), version(code=27, revision=2)),
            (version(), version("0.2.8", 26, 2)),
            (version(), version("0.2.8", 27, 2)),
            (version(), version("0.2.6", 27, 1)),
            (version(), version("0.2.8", 25, 1)),
        ]
        for previous, current in pairs:
            with self.subTest(previous=previous, current=current), self.assertRaises(ValueError):
                validate_transition(previous, current)

    def test_bounds_and_overflow(self):
        self.assertLessEqual(version(code=2_099_999, revision=999)["versionCode"], MAX_VERSION_CODE)
        for code, revision in [(0, 1), (-1, 1), (26, 0), (26, -1), (26, 1000),
                               (2_100_000, 1), (2**63, 1), (26, 2**63)]:
            with self.subTest(code=code, revision=revision), self.assertRaises(ValueError):
                version(code=code, revision=revision)

    def test_rejects_ambiguous_or_malformed_properties(self):
        valid = "upstreamVersionName=0.2.7\nupstreamVersionCode=26\ngeelyRevision=1\n"
        bad = [valid + "geelyRevision=2\n", valid + "unknown=1\n", valid.replace("geelyRevision=1\n", ""),
               valid.replace("=26", "=026"), valid.replace("=26", "=+26"), valid.replace("=26", "=26.0"),
               valid.replace("=1", "=01"), valid.replace("=1", "=١"), valid.replace("=1", "=1 # inline"),
               valid.replace("Code=26", "Code =26"), valid.replace("=26", "=26=27")]
        for name in ("v0.2.7", "0.2", "0.2.7-geely.1", "0.02.7", "0.2.7+local", "0.2.7-beta.1"):
            bad.append(valid.replace("0.2.7", name))
        for source in bad:
            with self.subTest(source=source), self.assertRaises(ValueError):
                parse_version(source)
        self.assertEqual(parse_version("# comment\n\n" + valid), version())

    def test_cli_checks_exact_tag_and_gradle_metadata(self):
        script = ROOT / "scripts/geely_version.py"
        current = read_version()
        good = subprocess.run([sys.executable, str(script), "--tag", current["tag"]], capture_output=True, text=True)
        self.assertEqual(good.returncode, 0, good.stderr)
        self.assertEqual(json.loads(good.stdout), current)
        bad = subprocess.run([sys.executable, str(script), "--tag", "v" + current["upstreamVersionName"]], capture_output=True, text=True)
        self.assertNotEqual(bad.returncode, 0)
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "version.json"
            metadata_path.write_text(json.dumps(read_version()))
            args = [sys.executable, str(script), "--gradle-metadata", str(metadata_path)]
            self.assertEqual(subprocess.run(args, capture_output=True).returncode, 0)
            metadata_path.write_text("{}")
            self.assertNotEqual(subprocess.run(args, capture_output=True).returncode, 0)

    def test_android_consumes_version_policy_and_unique_application_id(self):
        build = (ROOT / "mobile/build.gradle.kts").read_text()
        self.assertIn('apply(from = rootProject.file("gradle/geely-version.gradle.kts"))', build)
        self.assertIn("versionCode = geelyVersionCode", build)
        self.assertIn("versionName = geelyVersionName", build)
        self.assertIn('applicationId = "io.github.xiaoshengwpp.diplay.geely"', build)
        self.assertIn('namespace = "com.shilapi.xcertplay"', build)
        self.assertIn('applicationIdSuffix = ".hudtest"', build)

    def test_launcher_labels_distinguish_geely(self):
        for locale, expected in [("values", "DiPlay Geely"), ("values-zh-rCN", "DiPlay 星越 L")]:
            root = ET.parse(ROOT / f"mobile/src/main/res/{locale}/strings.xml").getroot()
            self.assertEqual(root.find("string[@name='app_name']").text, expected)

    def test_usb_permission_intents_are_scoped_to_runtime_package(self):
        transport = ROOT / "shared/src/main/java/com/shilapi/xcertplay/transport"
        for name in ("IphoneUsbHost.kt", "Ch341UsbHost.kt"):
            source = (transport / name).read_text()
            self.assertIn('"${context.packageName}.', source)
            self.assertIn("Intent(permissionAction).setPackage(appContext.packageName)", source)
        manifest = ET.parse(ROOT / "common/src/main/AndroidManifest.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        service = next(node for node in manifest.findall("application/service")
                       if node.get(android + "name").endswith("DiPlaySessionService"))
        self.assertEqual(service.get(android + "exported"), "false")
        source = (ROOT / "common/src/main/java/com/shilapi/xcertplay/DiPlaySessionService.kt").read_text()
        self.assertIn("Intent(this, DiPlaySessionService::class.java).setAction(ACTION_STOP)", source)


if __name__ == "__main__":
    unittest.main()
