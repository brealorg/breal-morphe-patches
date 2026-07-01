#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path


DEFAULT_RAW_JSON = "https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json"


def run_capture(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, check=False, text=True, capture_output=True)


def command_exists(name: str) -> bool:
    result = run_capture(["bash", "-lc", f"command -v {name} >/dev/null 2>&1"])
    return result.returncode == 0


def mpp_name_for(version: str) -> str:
    return f"patches-{version}.mpp"


def check_mpp(mpp: Path, errors: list[str]) -> None:
    if not mpp.exists():
        errors.append(f"local MPP does not exist: {mpp}")
        return

    try:
        with zipfile.ZipFile(mpp) as z:
            names = set(z.namelist())
            if "classes.dex" not in names:
                errors.append("local MPP missing classes.dex")
            if "extensions/boostforreddit.mpe" not in names:
                errors.append("local MPP missing extensions/boostforreddit.mpe")
    except zipfile.BadZipFile:
        errors.append(f"local MPP is not a valid zip file: {mpp}")


def local_tag_exists(tag: str) -> bool:
    result = run_capture(["git", "rev-parse", "--verify", "--quiet", f"refs/tags/{tag}"])
    return result.returncode == 0


def remote_tag_exists(tag: str, remote: str) -> bool:
    result = run_capture(["git", "ls-remote", "--tags", remote, tag])
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"git ls-remote failed for {remote}")
    return bool(result.stdout.strip())


def github_release_exists(tag: str) -> bool | None:
    if not command_exists("gh"):
        return None

    result = run_capture(["gh", "release", "view", tag, "--json", "tagName,url,publishedAt"])
    return result.returncode == 0


def fetch_json(url: str) -> dict:
    with urllib.request.urlopen(url, timeout=20) as response:
        raw = response.read().decode("utf-8")
    return json.loads(raw)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify that a local release candidate is held and not exposed to end users."
    )
    parser.add_argument("--version", required=True, help="Held local version, e.g. 1.4.x")
    parser.add_argument("--tag", required=True, help="Held local tag that must not exist yet")
    parser.add_argument(
        "--mpp",
        help="Path to local MPP. Defaults to patches/build/libs/patches-<version>.mpp.",
    )
    parser.add_argument(
        "--remote",
        default="origin",
        help="Git remote to check for tags. Defaults to origin.",
    )
    parser.add_argument(
        "--raw-json",
        default=DEFAULT_RAW_JSON,
        help="Raw Manager patches-bundle.json URL.",
    )
    parser.add_argument(
        "--allow-remote-unavailable",
        action="store_true",
        help="Warn instead of failing if raw Manager metadata cannot be fetched.",
    )
    parser.add_argument(
        "--skip-gh-release-check",
        action="store_true",
        help="Do not check GitHub release existence with gh.",
    )
    args = parser.parse_args()

    version = args.version
    tag = args.tag
    mpp = Path(args.mpp) if args.mpp else Path("patches") / "build" / "libs" / mpp_name_for(version)
    expected_mpp_name = mpp_name_for(version)

    errors: list[str] = []
    warnings: list[str] = []

    print("===== release hold gate =====")
    print("version:", version)
    print("tag:", tag)
    print("mpp:", mpp)
    print("raw_json:", args.raw_json)

    print()
    print("===== local MPP gate =====")
    check_mpp(mpp, errors)
    if mpp.exists():
        print("local MPP:", mpp)
        print("local MPP size:", mpp.stat().st_size)
    if not any("local MPP" in error for error in errors):
        print("local MPP required entries: OK")

    print()
    print("===== tag/release exposure gate =====")
    if local_tag_exists(tag):
        errors.append(f"local tag exists unexpectedly: {tag}")
    else:
        print(f"local tag absent: OK ({tag})")

    try:
        if remote_tag_exists(tag, args.remote):
            errors.append(f"remote tag exists unexpectedly on {args.remote}: {tag}")
        else:
            print(f"remote tag absent: OK ({tag})")
    except RuntimeError as exc:
        errors.append(f"could not check remote tag: {exc}")

    if args.skip_gh_release_check:
        print("GitHub release check: SKIPPED")
    else:
        gh_exists = github_release_exists(tag)
        if gh_exists is None:
            warnings.append("gh is not available; GitHub release check skipped")
            print("GitHub release check: SKIPPED, gh unavailable")
        elif gh_exists:
            errors.append(f"GitHub release exists unexpectedly: {tag}")
        else:
            print(f"GitHub release absent: OK ({tag})")

    print()
    print("===== raw Manager metadata exposure gate =====")
    try:
        remote_json = fetch_json(args.raw_json)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        message = f"could not fetch/parse raw Manager metadata: {exc}"
        if args.allow_remote_unavailable:
            warnings.append(message)
            print("WARN:", message)
        else:
            errors.append(message)
            remote_json = {}
    else:
        remote_version = str(remote_json.get("version", ""))
        download_url = str(remote_json.get("download_url", ""))
        print(json.dumps(remote_json, indent=2, ensure_ascii=False))
        print("remote_version:", remote_version)
        print("remote_download_url:", download_url)

        if remote_version == version:
            errors.append(f"raw Manager metadata already exposes held version: {version}")

        exposure_needles = [tag, version, expected_mpp_name]
        exposed = [needle for needle in exposure_needles if needle and needle in download_url]
        if exposed:
            errors.append(f"raw Manager download_url exposes held candidate values: {exposed}")

        if not errors:
            print("raw Manager metadata exposure: OK")

    if warnings:
        print()
        print("HOLD GATE WARNINGS")
        for warning in warnings:
            print(f" - {warning}")

    if errors:
        print()
        print("HOLD GATE FAILED")
        for error in errors:
            print(f" - {error}")
        return 1

    print()
    print("HOLD GATE OK")
    print("PUBLISH_STATUS=HELD_NOT_PUBLISHED")
    print("END_USER_RELEASE=NO")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
