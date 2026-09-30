#!/usr/bin/env python3
"""Validate an owner-built APK without extracting or printing identity contents.

Only ZIP entry metadata, Android tooling's public output, and the APK checksum
enter the public manifest. This command never supplies credentials or publishes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

APPLICATION_ID = "io.github.xiaoshengwpp.diplay.geely"
REQUIRED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64"}
REQUIRED_LIBRARIES = {"libxcertplay_i2c.so", "liblocal_hotspot_radio.so"}
AUTH_ENTRIES = {
    "assets/offline-mfi/identity.pk8",
    "assets/offline-mfi/certificate.p7b",
}
CREDENTIAL_SUFFIXES = {".pk8", ".p7b", ".key", ".pem", ".p12", ".pfx", ".jks", ".keystore"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def normalize_fingerprint(value):
    value = value.replace(":", "").lower()
    require(re.fullmatch(r"[0-9a-f]{64}", value), "Expected signer must be a public SHA-256 certificate fingerprint")
    return value


def check_badging(text, metadata):
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", text, re.M)
    require(match is not None, "APK package metadata is missing")
    package, code, name = match.groups()
    require(package == APPLICATION_ID, "APK application ID does not match the normal Geely release")
    require(int(code) == metadata["versionCode"] and name == metadata["versionName"], "APK version does not match the release metadata")
    require("application-debuggable" not in text, "Debuggable APK cannot be released")
    require(re.search(r"^sdkVersion:'28'$", text, re.M), "Unexpected APK minimum SDK")
    require(re.search(r"^targetSdkVersion:'37'$", text, re.M), "Unexpected APK target SDK")


def check_signature(text, expected_fingerprint):
    expected = normalize_fingerprint(expected_fingerprint)
    require(re.search(r"^Number of signers: 1$", text, re.M), "APK must have exactly one signer")
    fingerprints = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", text, re.M)
    require(len(fingerprints) == 1 and normalize_fingerprint(fingerprints[0]) == expected, "APK signing certificate does not match the expected release identity")
    require(re.search(r"^Verified using v[23](?:\.1)? scheme .*: true$", text, re.M), "APK requires a verified v2 or v3 signing scheme")
    require("CN=Android Debug" not in text, "Android debug signing is not a stable release identity")
    return expected


def check_archive(apk):
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()  # Never read or extract the authentication files.
        names = [entry.filename for entry in entries]
        require(len(names) == len(set(names)), "APK contains duplicate ZIP entries")
        indexed = {entry.filename: entry for entry in entries}
        require(all(name in indexed and indexed[name].file_size > 0 for name in AUTH_ENTRIES), "APK is missing nonempty standalone authentication assets")
        for entry in entries:
            name = entry.filename
            require(not name.startswith("/") and ".." not in Path(name).parts, "APK contains an unsafe ZIP path")
            if entry.is_dir():
                continue
            if "offline-mfi" in Path(name).parts or Path(name).suffix.lower() in CREDENTIAL_SUFFIXES:
                require(name in AUTH_ENTRIES, "APK contains an unexpected credential container")
        abis = {name.split("/")[1] for name in names if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name)}
        require(abis == REQUIRED_ABIS, "APK native architecture set does not match the release contract")
        for abi in REQUIRED_ABIS:
            require(all(indexed.get(f"lib/{abi}/{lib}") is not None and indexed[f"lib/{abi}/{lib}"].file_size > 0 for lib in REQUIRED_LIBRARIES), "APK is missing a required nonempty native library")
        return sorted(abis)


def public_tool_output(command):
    result = subprocess.run(command, capture_output=True, text=True, check=False)
    # A failed external command may include unexpected data; do not echo its output.
    require(result.returncode == 0, f"{Path(command[0]).name} verification failed; inspect locally without publishing private diagnostics")
    return result.stdout


def validate(apk, metadata, expected_fingerprint, aapt, apksigner, source_sha):
    require(re.fullmatch(r"[0-9a-f]{40}", source_sha), "Expected a full source commit SHA")
    require(metadata["tag"] == "v" + metadata["versionName"], "Release tag and version name disagree")
    check_badging(public_tool_output([aapt, "dump", "badging", str(apk)]), metadata)
    fingerprint = check_signature(public_tool_output([apksigner, "verify", "--verbose", "--print-certs", str(apk)]), expected_fingerprint)
    abis = check_archive(apk)
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {
        "applicationId": APPLICATION_ID,
        "versionName": metadata["versionName"],
        "versionCode": metadata["versionCode"],
        "tag": metadata["tag"],
        "sourceCommit": source_sha,
        "apk": apk.name,
        "apkSha256": digest,
        "signerCertificateSha256": fingerprint,
        "nativeAbis": abis,
        "standaloneAuthenticationPresent": True,
        "physicalCarAndIPhoneValidation": "not_performed_by_release_workflow",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--version-metadata", required=True, type=Path)
    parser.add_argument("--signer-sha256", required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        manifest = validate(args.apk, json.loads(args.version_metadata.read_text()), args.signer_sha256, args.aapt, args.apksigner, args.source_sha)
        args.output.write_text(json.dumps(manifest, indent=2) + "\n")
    except (ValueError, KeyError, OSError, zipfile.BadZipFile):
        # Avoid printing raw exceptions, filenames, or tool output on a private build.
        print("Release APK validation failed; no release should be published.", file=sys.stderr)
        return 1
    print("Release APK metadata, signature, native libraries and authentication presence verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
