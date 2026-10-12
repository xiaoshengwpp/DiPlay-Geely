#!/usr/bin/env python3
"""Owner-invoked packaging checks. No network, key generation, or automatic publishing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import zipfile

LOCK = {
    "source_repository": "xiaoshengwpp/DiPlay-Geely",
    "source_commit": "dca9e36b555cf6ebba041f8abbe72aaed0aab227",
    "upstream_source_commit": "e8d23603ccf5aa9c32ad5c999656c5fb492c83e6",
    "official_apk_url": "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.18/DiPlay-0.2.18.apk",
    "official_apk_bytes": 18170831,
    "official_apk_sha256": "e241457b36b21fe75b06b5682b4dcfc4fbc25cf75b1855c7a4d055129635ce8e",
    "application_id": "io.github.xiaoshengwpp.diplay.geely",
    "version_name": "0.2.18-geely.1",
    "version_code": "37001",
    "abis": ["arm64-v8a", "armeabi-v7a", "x86_64"],
}
ASSETS = ("assets/offline-mfi/identity.pk8", "assets/offline-mfi/certificate.p7b")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def no_symlinks(path):
    path = Path(path).absolute()
    require(all(not p.is_symlink() for p in (path, *path.parents)), "Symlink paths are forbidden")
    return path


def external(path, source):
    path = no_symlinks(path)
    require(not path.resolve().is_relative_to(Path(source).resolve()), "Input must be outside source tree")
    return path


def sha256(path):
    with open(path, "rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def checked_entries(archive):
    entries = archive.infolist()
    names = [item.filename for item in entries]
    require(len(names) == len(set(names)), "Duplicate ZIP entries")
    for item in entries:
        name = item.filename
        parts = name.rstrip("/").split("/")
        require(not name.startswith("/") and "\\" not in name and
                all(p not in ("", ".", "..") for p in parts) and
                not any(":" in p for p in parts), "Unsafe ZIP path")
        mode = item.external_attr >> 16
        require(not stat.S_ISLNK(mode), "ZIP symlinks forbidden")
    return {item.filename: item for item in entries}


def identity_entries(archive):
    entries = checked_entries(archive)
    relevant = {n for n in entries if n.startswith("assets/offline-mfi/") and not n.endswith("/")}
    require(relevant == set(ASSETS), "Expected exactly two runtime identity assets")
    for name in ASSETS:
        item = entries[name]
        require(0 < item.file_size <= 1024 * 1024 and not item.flag_bits & 1,
                "Invalid identity ZIP entry")
    return entries


def extract(apk, destination, source):
    """Only invoked by owner. Tests substitute the lock with inert text ZIP fixtures."""
    apk = no_symlinks(apk)
    require(apk.is_file() and apk.stat().st_size == LOCK["official_apk_bytes"], "Official APK size mismatch")
    require(sha256(apk) == LOCK["official_apk_sha256"], "Official APK SHA-256 mismatch")
    destination = external(destination, source)
    require(not destination.exists(), "Destination must not already exist")
    # Validate entire ZIP metadata before writing either selected entry.
    with zipfile.ZipFile(apk) as archive:
        identity_entries(archive)
        payloads = {name: archive.read(name) for name in ASSETS}
    destination.mkdir(mode=0o700, parents=True)
    (destination / "offline-mfi").mkdir(mode=0o700)
    for name, data in payloads.items():
        target = destination / name.removeprefix("assets/")
        with target.open("xb") as stream:
            os.chmod(target, 0o600)
            stream.write(data)
    validate_directory(destination, source)


def validate_directory(directory, source):
    directory = external(directory, source)
    require(directory.is_dir(), "Missing runtime input directory")
    expected = {"offline-mfi", "offline-mfi/identity.pk8", "offline-mfi/certificate.p7b"}
    paths = list(directory.rglob("*"))
    require({p.relative_to(directory).as_posix() for p in paths} == expected, "Unexpected runtime input entries")
    for path in paths:
        no_symlinks(path)
    require((directory / "offline-mfi").is_dir(), "Invalid runtime directory")
    require(all((directory / n.removeprefix("assets/")).is_file() and
                (directory / n.removeprefix("assets/")).stat().st_size > 0 for n in ASSETS), "Missing runtime input files")


def parse_public_metadata(badging, signatures):
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    require(package and package.groups() == (LOCK["application_id"], LOCK["version_code"], LOCK["version_name"]), "APK package/version mismatch")
    fingerprints = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", signatures, re.M)
    require(len(fingerprints) == 1, "Expected one verified APK signing certificate")
    return fingerprints[0].lower()


def check_source(source):
    source = no_symlinks(source)
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    require(commit == LOCK["source_commit"], "Source commit mismatch")
    require(not subprocess.check_output(["git", "-C", str(source), "status", "--porcelain"], text=True).strip(), "Source checkout is modified")


def verify(apk, source, runtime_dir, aapt, apksigner, output):
    apk = no_symlinks(apk)
    check_source(source)
    validate_directory(runtime_dir, source)
    with zipfile.ZipFile(apk) as archive:
        entries = identity_entries(archive)
        for name in ASSETS:
            expected = Path(runtime_dir) / name.removeprefix("assets/")
            require(archive.read(name) == expected.read_bytes(), "Packaged runtime asset mismatch")
        abis = sorted({n.split("/")[1] for n in entries if n.startswith("lib/") and n.endswith(".so")})
        require(abis == LOCK["abis"], "APK ABI mismatch")
        for name in entries:
            lower = name.lower()
            require(name in ASSETS or not lower.endswith((".pk8", ".p7b", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key")), "Unexpected credential file in APK")
    # Capture tool output; emit only the validated public certificate fingerprint.
    badging = subprocess.check_output([aapt, "dump", "badging", str(apk)], text=True, stderr=subprocess.PIPE)
    signatures = subprocess.check_output([apksigner, "verify", "--print-certs", str(apk)], text=True, stderr=subprocess.PIPE)
    fingerprint = parse_public_metadata(badging, signatures)
    output = no_symlinks(output)
    require(not output.exists(), "Output directory must not exist")
    output.mkdir(mode=0o700, parents=True)
    import shutil
    target = output / "DiPlay-0.2.18-geely.1.apk"
    shutil.copyfile(apk, target)
    checksum = sha256(target)
    (output / "SHA256SUMS.txt").write_text(f"{checksum}  {target.name}\n")
    manifest = dict(LOCK, apk_sha256=checksum, signing_certificate_sha256=fingerprint,
                    contains_experimental_identity=True, vehicle_tested=False,
                    redistribution_suitability="unresolved")
    (output / "build-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    source_parser = sub.add_parser("check-source")
    source_parser.add_argument("--source", required=True)
    extract_parser = sub.add_parser("extract")
    extract_parser.add_argument("--apk", required=True)
    extract_parser.add_argument("--destination", required=True)
    extract_parser.add_argument("--source", required=True)
    verify_parser = sub.add_parser("verify")
    for option in ("apk", "source", "runtime-dir", "aapt", "apksigner", "output"):
        verify_parser.add_argument("--" + option, required=True)
    args = vars(parser.parse_args())
    command = args.pop("command")
    try:
        {"extract": extract, "verify": verify, "check-source": check_source}[command](**args)
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError):
        # Do not print caught exceptions: tool output/path names may contain private data.
        parser.exit(1, "Owner release validation failed; no artifact is authorized for upload.\n")


if __name__ == "__main__":
    main()
