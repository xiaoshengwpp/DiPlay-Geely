#!/usr/bin/env python3
"""Read non-secret Geely release metadata; never open APKs or signing inputs."""
import argparse
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = ROOT / "gradle/geely-version.properties"
KEYS = {"upstreamVersionName", "upstreamVersionCode", "geelyRevision"}
UPSTREAM_VERSION = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
MAX_VERSION_CODE = 2_100_000_000
REVISION_RADIX = 1000


def parse_version(text):
    """Parse the intentionally small, strict properties format also used by Gradle."""
    properties = {}
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([A-Za-z][A-Za-z0-9]*)=([^\s=]+)", line)
        if not match:
            raise ValueError(f"Invalid Geely version property on line {number}")
        key, value = match.groups()
        if key in properties:
            raise ValueError(f"Duplicate Geely version property: {key}")
        properties[key] = value
    if set(properties) != KEYS:
        raise ValueError("Expected only upstreamVersionName, upstreamVersionCode and geelyRevision")
    name = properties["upstreamVersionName"]
    if not UPSTREAM_VERSION.fullmatch(name):
        raise ValueError("upstreamVersionName must be the original upstream numeric major.minor.patch version")
    for key in ("upstreamVersionCode", "geelyRevision"):
        if not re.fullmatch(r"[1-9][0-9]*", properties[key]):
            raise ValueError(f"{key} must be a canonical positive integer")
    code = int(properties["upstreamVersionCode"])
    revision = int(properties["geelyRevision"])
    if not 1 <= revision < REVISION_RADIX:
        raise ValueError("geelyRevision must be between 1 and 999")
    version_code = code * REVISION_RADIX + revision
    if version_code > MAX_VERSION_CODE:
        raise ValueError("Geely versionCode exceeds the 2100000000 limit; review the versioning policy")
    version_name = f"{name}-geely.{revision}"
    return {
        "upstreamVersionName": name,
        "upstreamVersionCode": code,
        "geelyRevision": revision,
        "versionName": version_name,
        "versionCode": version_code,
        "tag": f"v{version_name}",
    }


def read_version(path=VERSION_FILE):
    return parse_version(Path(path).read_text(encoding="utf-8"))


def validate_transition(previous, current):
    """Refuse reuse/downgrades and an upstream change without revision reset."""
    if current["versionCode"] <= previous["versionCode"]:
        raise ValueError("A new release must increase versionCode; never reuse a published version")
    if current["upstreamVersionName"] == previous["upstreamVersionName"]:
        if current["upstreamVersionCode"] != previous["upstreamVersionCode"]:
            raise ValueError("Keep the upstream versionCode unchanged for the same upstream version")
        if current["geelyRevision"] <= previous["geelyRevision"]:
            raise ValueError("Increase geelyRevision for a new adaptation of the same upstream version")
    else:
        old_name = tuple(map(int, previous["upstreamVersionName"].split(".")))
        new_name = tuple(map(int, current["upstreamVersionName"].split(".")))
        if new_name <= old_name or current["upstreamVersionCode"] <= previous["upstreamVersionCode"]:
            raise ValueError("An upstream update must increase both upstream versionName and versionCode")
        if current["geelyRevision"] != 1:
            raise ValueError("Reset geelyRevision to 1 for a new upstream version")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--properties", type=Path, default=VERSION_FILE)
    parser.add_argument("--tag", help="Require an exact match with the requested release tag")
    parser.add_argument("--previous", type=Path, help="Properties from the last published Geely release")
    parser.add_argument("--gradle-metadata", type=Path, help="Compare with writeGeelyVersionMetadata output")
    args = parser.parse_args(argv)
    try:
        metadata = read_version(args.properties)
        if args.tag is not None and args.tag != metadata["tag"]:
            raise ValueError(f"Release tag must be {metadata['tag']}")
        if args.previous is not None:
            validate_transition(read_version(args.previous), metadata)
        if args.gradle_metadata is not None:
            actual = json.loads(args.gradle_metadata.read_text(encoding="utf-8"))
            if actual != metadata:
                raise ValueError("Gradle and release metadata do not match")
    except (ValueError, OSError) as error:
        parser.exit(1, f"Geely version check failed: {error}\n")
    print(json.dumps(metadata, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
