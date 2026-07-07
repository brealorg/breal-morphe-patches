#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path


ROOT = Path.cwd()


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(cmd: list[str]) -> None:
    print("+", " ".join(cmd))
    subprocess.run(cmd, check=True)


def parse_gradle_version(path: Path) -> str:
    text = read(path)
    match = re.search(r"(?m)^\s*version\s*=\s*([^\s#]+)\s*$", text)
    if not match:
        raise SystemExit("Could not find version = ... in gradle.properties")
    return match.group(1)


def default_tag_for(version: str) -> str:
    release_number = version.split(".")[-1]
    return f"morphe-patches-{release_number}"


def mpp_name_for(version: str) -> str:
    return f"patches-{version}.mpp"


def normalize_changelog_line(line: str) -> str:
    line = line.strip()
    if line.startswith("- "):
        line = line[2:].strip()
    return line


def replace_gradle_version(text: str, version: str) -> str:
    new, count = re.subn(
        r"(?m)^(\s*version\s*=\s*)[^\s#]+(\s*)$",
        rf"\g<1>{version}\2",
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Could not replace version in gradle.properties")
    return new


def set_readme_heading_value(text: str, heading: str, value: str) -> str:
    lines = text.splitlines()

    for i, line in enumerate(lines):
        if line.strip() == heading:
            j = i + 1
            while j < len(lines) and lines[j].strip() == "":
                j += 1

            replacement = f"`{value}`"

            if j < len(lines):
                lines[j] = replacement
            else:
                lines.append("")
                lines.append(replacement)

            return "\n".join(lines) + "\n"

    raise SystemExit(f"Could not find README heading: {heading}")


def set_readme_sha(text: str, sha: str) -> str:
    new, count = re.subn(
        r"(?m)^SHA256:\s*`?[0-9a-f]{64}`?\s*$",
        f"SHA256: `{sha}`",
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Could not replace standalone README SHA256 line")
    return new


def replace_readme_table_value(text: str, label: str, value: str) -> str:
    pattern = rf"(?m)^(\| {re.escape(label)} \| `)[^`]*(` \|)$"
    new, count = re.subn(pattern, rf"\g<1>{value}\2", text, count=1)
    if count != 1:
        raise SystemExit(f"Could not replace README Current release table row: {label}")
    return new


def replace_readme_verification(text: str, version: str, tag: str, mpp_name: str, sha: str) -> str:
    block = (
        "## Verification\n\n"
        f"Release `{version}` is prepared and locally verified with:\n\n"
        f"- Release tag `{tag}`.\n"
        "- Local built MPP SHA256 matching README.\n"
        f"`{sha}`\n"
        f"- `patches-bundle.json` returning version `{version}`.\n"
        f"- `patches-bundle.json` pointing to the `{tag}` asset.\n"
        "- Expected release asset:\n"
        f"`{mpp_name}`\n"
        f"- `{sha}  {mpp_name}`\n\n"
    )
    new, count = re.subn(
        r"(?ms)^## Verification\n\n.*?(?=^## Development notes)",
        block,
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Could not replace README Verification section")
    return new


def update_description(version: str, tag: str, changelog_lines: list[str]) -> str:
    description_lines: list[str] = []
    seen: set[str] = set()

    for item in changelog_lines:
        normalized = normalize_changelog_line(item)
        if not normalized or normalized in seen:
            continue

        description_lines.append(normalized)
        seen.add(normalized)

    if not description_lines:
        description_lines.append("Bug Fixes\n\n• Metadata-only release.")

    return "\n".join(description_lines).rstrip() + "\n"

def update_bundle_json(path: Path, version: str, tag: str, changelog_lines: list[str]) -> None:
    data = json.loads(read(path))

    mpp_name = mpp_name_for(version)
    data["created_at"] = datetime.now().replace(microsecond=0).isoformat()
    data["version"] = version
    download_url = f"https://github.com/brealorg/breal-morphe-patches/releases/download/{tag}/{mpp_name}"
    data["download_url"] = download_url
    data["signature_download_url"] = f"{download_url}.asc"
    data["description"] = update_description(version, tag, changelog_lines)

    write(path, json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def update_readme(path: Path, version: str, tag: str, sha: str | None = None) -> None:
    mpp_name = mpp_name_for(version)
    download_url = f"https://github.com/brealorg/breal-morphe-patches/releases/download/{tag}/{mpp_name}"
    text = read(path)

    text = replace_readme_table_value(text, "Version", version)
    text = replace_readme_table_value(text, "Release tag", tag)
    text = replace_readme_table_value(text, "Asset", mpp_name)
    text = replace_readme_table_value(text, "Download URL", download_url)

    if sha is not None:
        text = replace_readme_table_value(text, "SHA256", sha)
        text = set_readme_sha(text, sha)
        text = replace_readme_verification(text, version, tag, mpp_name, sha)

    write(path, text)


def build_mpp(version: str) -> Path:
    run(["./gradlew", "clean", ":patches:buildAndroid", "--no-daemon"])

    mpp = ROOT / "patches" / "build" / "libs" / mpp_name_for(version)
    if not mpp.exists():
        raise SystemExit(f"Expected MPP was not built: {mpp}")

    return mpp


def run_release_gate(
    version: str,
    tag: str,
    changelog_lines: list[str],
    markers: list[str],
    stale_values: list[str],
) -> None:
    cmd = [
        sys.executable,
        "scripts/release-gate.py",
        "--version",
        version,
        "--tag",
        tag,
    ]

    for line in changelog_lines:
        normalized = normalize_changelog_line(line)
        if normalized:
            cmd.extend(["--require-description", normalized])

    for marker in markers:
        cmd.extend(["--marker", marker])

    for stale in stale_values:
        cmd.extend(["--stale", stale])

    run(cmd)



def previous_release_tag_for(version: str) -> str:
    normalized = version.strip().removeprefix("v")
    parts = normalized.split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        raise SystemExit(f"Cannot infer previous release tag from non-semver version: {version!r}")

    previous_patch = int(parts[2]) - 1
    if previous_patch < 0:
        raise SystemExit(f"Cannot infer previous release tag from version: {version!r}")

    return f"morphe-patches-{previous_patch}"


def run_manager_changelog_update(
    repo_root: Path,
    version: str,
    tag: str,
    changelog_lines: list[str],
) -> None:
    if not changelog_lines:
        return

    updater = repo_root / "tools" / "update-manager-changelog.py"
    if not updater.exists():
        raise SystemExit(f"Missing Manager changelog updater: {updater}")

    command = [
        sys.executable,
        str(updater),
        "--repo-root",
        str(repo_root),
        "--version",
        version,
        "--tag",
        tag,
        "--previous-tag",
        previous_release_tag_for(version),
        "--scope",
        "Boost for Reddit",
    ]

    for line in changelog_lines:
        command.extend(["--text", line])

    run(command)

def print_summary(version: str, tag: str, mpp: Path | None, sha: str | None) -> None:
    print()
    print("===== prepare-release summary =====")
    print("version:", version)
    print("tag:", tag)
    print("asset:", mpp_name_for(version))
    if mpp is not None:
        print("mpp:", mpp)
    if sha is not None:
        print("sha256:", sha)
    print()
    print("Next manual steps:")
    print("  git --no-pager diff -- gradle.properties patches-bundle.json patches-list.json README.md")
    print("  git add gradle.properties patches-bundle.json patches-list.json README.md")
    print("  git commit -m \"Prepare patch bundle release <N>\"")
    print("  git tag -a <tag> -m <tag>")
    print("  git push origin main")
    print("  git push origin <tag>")
    print("  gh release create <tag> patches/build/libs/<asset> --title <tag> --notes-file <notes-file>")
    print("  make verify-remote VERSION=<version> TAG=<tag>")


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare Morphe patch bundle release metadata and artifact.")
    parser.add_argument("--version", required=True, help="New release version, e.g. 1.4.23")
    parser.add_argument("--tag", help="Release tag. Defaults to morphe-patches-<patch version>.")
    parser.add_argument("--changelog", action="append", default=[], help="Manager-facing release description text. Can be repeated.")
    parser.add_argument("--marker", action="append", default=[], help="Marker to require in extensions/boostforreddit.mpe. Can be repeated.")
    parser.add_argument("--stale", action="append", default=[], help="Old value that must not remain in release metadata. Can be repeated.")
    parser.add_argument("--allow-empty-changelog", action="store_true", help="Allow release without adding a changelog line.")
    parser.add_argument("--skip-build", action="store_true", help="Do not run Gradle; use existing patches/build/libs artifact.")
    parser.add_argument("--skip-gate", action="store_true", help="Do not run scripts/release-gate.py after preparing.")
    parser.add_argument("--dry-run", action="store_true", help="Print intended values without changing files or building.")
    args = parser.parse_args()

    gradle_path = ROOT / "gradle.properties"
    bundle_path = ROOT / "patches-bundle.json"
    readme_path = ROOT / "README.md"

    old_version = parse_gradle_version(gradle_path)
    old_tag = default_tag_for(old_version)
    old_mpp_name = mpp_name_for(old_version)

    version = args.version
    tag = args.tag or default_tag_for(version)

    changelog_lines = [normalize_changelog_line(line) for line in args.changelog if normalize_changelog_line(line)]

    if not changelog_lines and not args.allow_empty_changelog:
        raise SystemExit("At least one --changelog line is required, unless --allow-empty-changelog is used.")

    stale_values = list(args.stale)
    if not stale_values and old_version != version:
        stale_values = [
            old_version,
            old_tag,
            old_mpp_name,
        ]

    print("===== prepare-release plan =====")
    print("old version:", old_version)
    print("new version:", version)
    print("tag:", tag)
    print("asset:", mpp_name_for(version))
    print("stale checks:", ", ".join(stale_values) if stale_values else "(none)")
    print("markers:", ", ".join(args.marker) if args.marker else "(none)")
    print("changelog:")
    for line in changelog_lines:
        print(f" - {line}")

    if args.dry_run:
        print()
        print("DRY RUN: no files changed, no build run.")
        return 0

    gradle_text = replace_gradle_version(read(gradle_path), version)
    write(gradle_path, gradle_text)

    update_bundle_json(bundle_path, version, tag, changelog_lines)
    run_manager_changelog_update(Path.cwd(), version, tag, changelog_lines)
    run(["./tools/check-patches-list-feed.sh", "--write", version])
    run([
        sys.executable,
        ".github/scripts/generate_patches_readme.py",
        "brealorg/breal-morphe-patches",
        "main",
        "patches-list.json",
        "README.md",
    ])
    update_readme(readme_path, version, tag, sha=None)

    if args.skip_build:
        mpp = ROOT / "patches" / "build" / "libs" / mpp_name_for(version)
        if not mpp.exists():
            raise SystemExit(f"--skip-build was used, but MPP does not exist: {mpp}")
    else:
        mpp = build_mpp(version)

    sha = sha256_file(mpp)
    update_readme(readme_path, version, tag, sha=sha)

    if not args.skip_gate:
        run_release_gate(version, tag, changelog_lines, args.marker, stale_values)

    print_summary(version, tag, mpp, sha)
    return 0



def run_manager_update_changelog_guard() -> None:
    """Fail release prep if CHANGELOG.md cannot drive Morphe Manager update badges."""
    import subprocess
    import sys
    from pathlib import Path

    repo_root = Path(__file__).resolve().parent.parent
    guard = repo_root / "tools" / "check-manager-update-changelog.py"
    subprocess.run(
        [sys.executable, str(guard), "--repo-root", str(repo_root)],
        check=True,
    )

if __name__ == "__main__":
    rc = main()
    if rc is None or rc == 0:
        run_manager_update_changelog_guard()
    raise SystemExit(rc)
