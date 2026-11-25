#!/usr/bin/env python3
"""Verify and copy a JDBC bundle to a new NiFi-local directory; never edit flows."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile


def safe_file(root, relative):
    path = PurePosixPath(relative)
    if path.is_absolute() or ".." in path.parts or "\\" in relative:
        raise ValueError("Unsafe bundle path: " + relative)
    result = root.joinpath(*path.parts).resolve()
    if root.resolve() not in result.parents:
        raise ValueError("Path escapes bundle: " + relative)
    return result


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def verify(root, manifest):
    for relative, item in manifest["files"].items():
        path = safe_file(root, relative)
        if not path.is_file() or digest(path) != item["sha256"]:
            raise ValueError("Missing or changed file: " + relative)


def write_mappings(root, manifest):
    # All paths describe the NiFi host filesystem, never the platform backend host.
    lines = ["# NiFi host JDBC Driver Locations; apply selected entries to the node configuration."]
    with (root / "jdbc-driver-profiles.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["profile", "driverClass", "driverLocations", "note"])
        for key, profile in sorted(manifest["profiles"].items()):
            locations = ",".join(str(safe_file(root, p)) for p in profile["files"])
            lines.append(key + "=" + locations)
            writer.writerow([key, profile["class"], locations, profile.get("note", "")])
    (root / "jdbc-driver-locations.conf").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("nifi_home", nargs="?", help="Existing NiFi installation directory")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", manifest["bundleId"]):
        raise ValueError("Unsafe bundle ID")
    verify(source, manifest)
    if args.verify_only:
        print("SHA-256 OK: {} files".format(len(manifest["files"])))
        return
    if not args.nifi_home:
        parser.error("nifi_home is required unless --verify-only is used")
    home = Path(args.nifi_home).resolve(strict=True)
    if not (home / "conf/nifi.properties").is_file():
        raise ValueError("Not a NiFi installation: conf/nifi.properties is missing")
    # A comma is the NiFi list separator, so it cannot occur inside these filenames.
    if any(character in str(home) for character in (",", "\n", "\r")):
        raise ValueError("NiFi path cannot contain a comma or newline")
    parent = home / "drivers"
    parent.mkdir(exist_ok=True)
    destination = parent / manifest["bundleId"]
    if parent.resolve() != home / "drivers" or destination.is_symlink():
        raise ValueError("Refusing a symbolic driver destination")
    if destination.exists():
        installed = json.loads((destination / "manifest.json").read_text(encoding="utf-8"))
        if installed != manifest:
            raise ValueError("Different bundle already exists; no files overwritten")
        verify(destination, manifest)
        write_mappings(destination, manifest)
    else:
        stage = Path(tempfile.mkdtemp(prefix=".jdbc-stage-", dir=str(parent)))
        try:
            for relative in manifest["files"]:
                target = safe_file(stage, relative)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(safe_file(source, relative), target)
                target.chmod(0o644)
            shutil.copy2(source / "manifest.json", stage / "manifest.json")
            verify(stage, manifest)
            # Generate mappings for the final path rather than the temporary staging path.
            os.rename(str(stage), str(destination))
            stage = None
            write_mappings(destination, manifest)
        finally:
            if stage is not None and stage.exists():
                # This directory was created here, below the validated drivers directory.
                shutil.rmtree(str(stage))
    print("Installed and verified: " + str(destination))
    print("Driver classes and absolute paths: " + str(destination / "jdbc-driver-profiles.csv"))
    print("Apply the selected Driver Class Name and Driver Locations to the connection pool.")
    print("No NiFi configuration, flows, queues or services were changed or restarted.")


if __name__ == "__main__":
    main()
