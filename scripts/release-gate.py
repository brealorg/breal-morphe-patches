#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path


FORBIDDEN_DESCRIPTION_FRAGMENTS = [
    "Morphe patch bundle",
    "Latest in",
    "Also includes",
    "previous fixes",
    "Clean APKs",
    "Clean APKs must be supplied separately",
]

MAX_MANAGER_DESCRIPTION_LENGTH = 700



def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def parse_gradle_version(path: Path) -> str:
    text = read(path)
    match = re.search(r"(?m)^\s*version\s*=\s*([^\s#]+)\s*$", text)
    if not match:
        raise SystemExit("Could not find version = ... in gradle.properties")
    return match.group(1)


def default_tag_for(version: str) -> str:
    release_number = version.split(".")[-1]
    return f"morphe-patches-{release_number}"


def read_readme_sha(readme: str) -> str | None:
    match = re.search(r"(?ms)^SHA256:\s*`?([0-9a-f]{64})`?", readme)
    if match:
        return match.group(1)
    return None


def git_staged_files() -> list[str]:
    try:
        result = subprocess.run(
            ["git", "diff", "--cached", "--name-only"],
            check=False,
            text=True,
            capture_output=True,
        )
    except FileNotFoundError:
        return []

    if result.returncode != 0:
        return []

    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def readme_current_release_section(readme: str) -> str:
    """Return only the active README Current release section for stale checks.

    Historical README sections, release notes, and verification notes may
    legitimately mention previous release versions/tags/assets. Stale checks
    should therefore only scan the active release table area.
    """

    match = re.search(r"(?ms)^## Current release\n.*?(?=^##\s|\Z)", readme)
    if match:
        return match.group(0)
    return ""


def bundle_active_release_fields(bundle: dict) -> str:
    """Return only active bundle fields that define the current release asset.

    The Manager-facing description can contain generated compare ranges or
    historical wording. Description quality is guarded separately by the
    Manager description checks.
    """

    active_keys = (
        "version",
        "download_url",
        "signature_download_url",
    )
    return "\n".join(str(bundle.get(key, "")) for key in active_keys)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate a Morphe patch bundle release before publishing.")
    parser.add_argument("--version", help="Expected bundle version, e.g. 1.4.22. Defaults to gradle.properties.")
    parser.add_argument("--tag", help="Expected release tag. Defaults to morphe-patches-<patch version>.")
    parser.add_argument("--mpp", help="Path to built .mpp. Defaults to patches/build/libs/patches-<version>.mpp.")
    parser.add_argument("--require-description", action="append", default=[], help="Text that must exist in patches-bundle.json description.")
    parser.add_argument("--marker", action="append", default=[], help="Binary/text marker that must exist in extensions/boostforreddit.mpe.")
    parser.add_argument("--stale", action="append", default=[], help="Old value that must not exist in release metadata files.")
    parser.add_argument("--skip-staged-check", action="store_true", help="Do not check for staged build artifacts.")
    args = parser.parse_args()

    root = Path.cwd()

    gradle_path = root / "gradle.properties"
    bundle_path = root / "patches-bundle.json"
    readme_path = root / "README.md"

    errors: list[str] = []

    def req(condition: bool, message: str) -> None:
        if not condition:
            errors.append(message)

    version = args.version or parse_gradle_version(gradle_path)
    tag = args.tag or default_tag_for(version)
    mpp_name = f"patches-{version}.mpp"
    mpp_path = Path(args.mpp) if args.mpp else root / "patches" / "build" / "libs" / mpp_name
    expected_url = f"https://github.com/brealorg/breal-morphe-patches/releases/download/{tag}/{mpp_name}"

    gradle = read(gradle_path)
    readme = read(readme_path)

    try:
        bundle = json.loads(read(bundle_path))
    except Exception as e:
        errors.append(f"patches-bundle.json is not valid JSON: {e}")
        bundle = {}

    req(f"version = {version}" in gradle, f"gradle.properties does not contain: version = {version}")

    bundle_version = str(bundle.get("version") or "")
    accepted_bundle_versions = {version, f"v{version}"}
    req(
        bundle_version in accepted_bundle_versions,
        f"patches-bundle.json version is {bundle.get('version')!r}, expected {version!r} or {f'v{version}'!r}",
    )
    req(bundle.get("download_url") == expected_url, "patches-bundle.json download_url does not match expected release URL")

    description = bundle.get("description", "")
    for required in args.require_description:
        req(required in description, f"patches-bundle.json description missing required text: {required!r}")

    for forbidden in FORBIDDEN_DESCRIPTION_FRAGMENTS:
        req(forbidden not in description, f"patches-bundle.json description contains forbidden Manager boilerplate: {forbidden!r}")

    req(
        len(description) <= MAX_MANAGER_DESCRIPTION_LENGTH,
        f"patches-bundle.json description is too long for Manager-facing text: {len(description)} characters",
    )

    req(version in readme, f"README.md missing version {version}")
    req(tag in readme, f"README.md missing tag {tag}")
    req(mpp_name in readme, f"README.md missing asset name {mpp_name}")

    boost_heading = "### Boost for Reddit"
    next_imgur_heading = "### Imgur selected media sharing"
    if boost_heading in readme and next_imgur_heading in readme:
        boost_section = readme.split(boost_heading, 1)[1].split(next_imgur_heading, 1)[0]
        boost_release_marker = f"Included in `{version}`:"
        generated_patch_source_markers = (
            f"**Patch source version:** `v{version}`",
            f"**Patch source version:** `{version}`",
        )
        req(
            boost_release_marker in boost_section
            or any(marker in readme for marker in generated_patch_source_markers),
            "README missing current release marker: expected "
            f"{boost_release_marker} in Boost for Reddit section or generated patch source version v{version}",
        )
    else:
        errors.append("README.md missing expected Boost for Reddit / Imgur section headings")

    req(mpp_path.exists(), f"MPP does not exist: {mpp_path}")

    if mpp_path.exists():
        actual_sha = sha256_file(mpp_path)
        readme_sha = read_readme_sha(readme)

        req(readme_sha == actual_sha, f"README SHA does not match built MPP. README={readme_sha}, actual={actual_sha}")

        try:
            with zipfile.ZipFile(mpp_path) as z:
                names = set(z.namelist())

                dex_entries = sorted(name for name in names if Path(name).name.endswith(".dex"))
                req(bool(dex_entries), "MPP has no dex entries")
                req(any(Path(name).name == "classes.dex" for name in dex_entries), "MPP missing classes.dex")

                boost_ext = "extensions/boostforreddit.mpe"
                req(boost_ext in names, f"MPP missing {boost_ext}")

                if boost_ext in names:
                    data = z.read(boost_ext)
                    for marker in args.marker:
                        req(marker.encode("utf-8") in data, f"{boost_ext} missing marker: {marker!r}")

                print("MPP:", mpp_path)
                print("MPP SHA256:", actual_sha)
                print("DEX entries:", ", ".join(dex_entries))
                print("Boost extension:", "OK" if boost_ext in names else "MISSING")

        except zipfile.BadZipFile:
            errors.append(f"MPP is not a valid zip file: {mpp_path}")
        except Exception as e:
            errors.append(f"Could not inspect MPP: {e}")

    stale_scopes = [
        ("gradle.properties", gradle),
        ("patches-bundle.json active release fields", bundle_active_release_fields(bundle)),
        ("README.md Current release section", readme_current_release_section(readme)),
    ]
    for stale in args.stale:
        for scope_name, scope_text in stale_scopes:
            if stale in scope_text:
                errors.append(f"stale value {stale!r} still present in {scope_name}")

    if not args.skip_staged_check:
        staged = git_staged_files()
        bad_staged = [
            path for path in staged
            if path.endswith(".mpp")
            or path.startswith("patches/build/")
            or "/build/" in path
        ]
        req(not bad_staged, f"build artifacts are staged: {bad_staged}")

    if errors:
        print("RELEASE GATE FAILED")
        for error in errors:
            print(f" - {error}")
        return 1

    print("RELEASE GATE OK")
    print("Version:", version)
    print("Tag:", tag)
    print("Expected URL:", expected_url)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
