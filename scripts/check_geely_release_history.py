#!/usr/bin/env python3
"""Read public release metadata only; reject existing or non-monotonic Geely tags."""
import argparse
import json
from pathlib import Path
import re

from validate_geely_apk import APPLICATION_ID, normalize_fingerprint


def release_order(tag):
    match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)-geely\.([1-9]\d{0,2})", tag)
    if not match:
        raise ValueError("Invalid Geely release tag")
    return tuple(map(int, match.groups()))


def validate_history(metadata, pages):
    candidate = release_order(metadata["tag"])
    previous = []
    for page in pages:
        for release in page:
            tag = release.get("tag_name", "")
            if tag == metadata["tag"]:
                raise ValueError("This release tag already exists, including drafts")
            if "-geely." in tag:
                order = release_order(tag)
                if order >= candidate:
                    raise ValueError("Geely releases must move forward; never replace or downgrade a release")
                # An incomplete draft reserves its tag, but is not an installed-update baseline.
                if not release.get("draft", False):
                    previous.append((order, tag))
    return max(previous)[1] if previous else ""


def validate_previous_manifest(current, previous, expected_signer):
    if previous["applicationId"] != APPLICATION_ID:
        raise ValueError("Previous Geely release uses a different application ID")
    if not isinstance(previous["versionCode"], int) or previous["versionCode"] >= current["versionCode"]:
        raise ValueError("APK version code must increase from the previous public release")
    if normalize_fingerprint(previous["signerCertificateSha256"]) != normalize_fingerprint(expected_signer):
        raise ValueError("Keep the same Android signing identity across Geely releases")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version-metadata", required=True, type=Path)
    parser.add_argument("--history", required=True, type=Path)
    parser.add_argument("--previous-tag-output", required=True, type=Path)
    args = parser.parse_args()
    previous = validate_history(json.loads(args.version_metadata.read_text()), json.loads(args.history.read_text()))
    args.previous_tag_output.write_text(previous + "\n")
    print("Geely release history is compatible with a new version.")


if __name__ == "__main__":
    main()
