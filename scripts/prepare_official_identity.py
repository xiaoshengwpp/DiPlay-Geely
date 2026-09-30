#!/usr/bin/env python3
"""Owner-run provisioning from a pinned official APK; never invoked by ordinary CI.

Uses the public-APK provisioning approach documented by serein-morii/DiPlay-CN.
No credential values are included in this source or printed by this command.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import stat
import sys
import zipfile

from geely_version import read_version

ROOT = Path(__file__).resolve().parents[1]
NAMES = ("identity.pk8", "certificate.p7b")
MAX_ENTRY_BYTES = 1024 * 1024


def validate_metadata(metadata, version):
    if set(metadata) != {"upstreamVersionName", "url", "sha256"}:
        raise ValueError("Unexpected official APK metadata fields")
    upstream = version["upstreamVersionName"]
    expected_url = f"https://github.com/shihabal3amri/DiPlay/releases/download/v{upstream}/DiPlay-{upstream}.apk"
    if metadata["upstreamVersionName"] != upstream or metadata["url"] != expected_url:
        raise ValueError("Official APK must match the reviewed upstream version and repository")
    if not isinstance(metadata["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", metadata["sha256"]):
        raise ValueError("A pinned SHA-256 digest is required")
    return metadata


def provision(apk, destination, expected_digest, owner_approved=False):
    if not owner_approved:
        raise ValueError("Owner-controlled provisioning acknowledgement is required")
    apk, destination = Path(apk), Path(destination)
    with apk.open("rb") as stream:
        actual_digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual_digest != expected_digest:
        raise ValueError("Official APK SHA-256 mismatch; no identity files were written")
    resolved = destination.resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise ValueError("Runtime identity must remain outside the source tree")
    # Never reuse an existing directory, follow an output symlink, or replace files.
    if destination.exists() or destination.is_symlink():
        raise ValueError("Provisioning destination must be new")
    with zipfile.ZipFile(apk) as archive:
        selected = []
        for name in NAMES:
            path = f"assets/offline-mfi/{name}"
            entries = [info for info in archive.infolist() if info.filename == path]
            if len(entries) != 1:
                raise ValueError("Official APK must contain exactly one of each identity entry")
            info = entries[0]
            if info.is_dir() or info.flag_bits & 1 or stat.S_ISLNK(info.external_attr >> 16):
                raise ValueError("Unsupported identity ZIP entry")
            if not 0 < info.file_size <= MAX_ENTRY_BYTES:
                raise ValueError("Invalid identity entry size")
            selected.append((name, info))
        destination.mkdir(mode=0o700, parents=False)
        try:
            output = destination / "offline-mfi"
            output.mkdir(mode=0o700)
            for name, info in selected:
                # Read only these exact entries after full archive hash verification.
                payload = archive.read(info)
                target = output / name
                with target.open("xb") as stream:
                    stream.write(payload)
                target.chmod(0o600)
        except Exception:
            shutil.rmtree(destination)
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metadata", type=Path, default=ROOT / "gradle/official-apk.json")
    parser.add_argument("--check-metadata", action="store_true")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--destination", type=Path)
    parser.add_argument("--owner-approved-provisioning", action="store_true")
    args = parser.parse_args()
    try:
        metadata = validate_metadata(json.loads(args.metadata.read_text()), read_version())
        if args.check_metadata:
            print(json.dumps(metadata, sort_keys=True))
            return
        if not args.apk or not args.destination:
            raise ValueError("Both APK path and destination are required")
        provision(args.apk, args.destination, metadata["sha256"], args.owner_approved_provisioning)
        print("Pinned official runtime identity prepared for this owner-controlled build only")
    except Exception:
        print("Official identity preparation failed; no credential values were printed", file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
