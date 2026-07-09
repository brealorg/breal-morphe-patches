#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


VERSION_HEADING_RE = re.compile(
    r"^#{1,3}\s+(?:\S+\s+)?(?:\[([^]]+)]\([^)]*\)|([^\s\[(]+))\s+\((\d{4}-\d{2}-\d{2})\)",
    re.IGNORECASE,
)
SCOPE_RE = re.compile(r"^\s*\*\s+\*\*(.+?):\*\*")


@dataclass(frozen=True)
class ChangelogEntry:
    version: str
    date: str
    scopes: tuple[str, ...]


def normalize_version(version: object) -> str:
    return str(version).strip().removeprefix("v")


def version_tuple(version: object) -> tuple[int, int, int]:
    normalized = normalize_version(version)
    parts: list[int] = []
    for part in re.split(r"[.-]", normalized):
        if part.isdigit():
            parts.append(int(part))
        else:
            break
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts[:3])


def is_newer(old: object, new: object) -> bool:
    return version_tuple(new) > version_tuple(old)


def scope_matches(scope: str, app_scope: str) -> bool:
    """Manager app scope must match Compatibility.name exactly."""
    return scope == app_scope


def parse_changelog(text: str) -> list[ChangelogEntry]:
    entries: list[ChangelogEntry] = []
    current_version: str | None = None
    current_date: str | None = None
    content: list[str] = []

    def flush() -> None:
        nonlocal current_version, current_date, content
        if current_version is None or current_date is None:
            return
        scopes: list[str] = []
        for line in content:
            match = SCOPE_RE.match(line)
            if match:
                scopes.append(match.group(1).strip())
        entries.append(
            ChangelogEntry(
                version=normalize_version(current_version),
                date=current_date,
                scopes=tuple(scopes),
            )
        )

    for line in text.splitlines():
        match = VERSION_HEADING_RE.search(line)
        if match:
            flush()
            current_version = (match.group(1) or match.group(2)).strip()
            current_date = match.group(3)
            content = []
        elif current_version is not None:
            content.append(line)

    flush()
    return entries


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify Morphe Manager-compatible changelog scopes for app update detection."
    )
    parser.add_argument("--repo-root", default=".", help="Repository root. Defaults to current directory.")
    parser.add_argument("--bundle", default=None, help="Path to patches-bundle.json.")
    parser.add_argument("--changelog", default=None, help="Path to CHANGELOG.md.")
    parser.add_argument("--scope", default="Boost for Reddit", help="Required Manager changelog app scope.")
    parser.add_argument(
        "--installed-version",
        default=None,
        help="Optional installed bundle version to replay update badge decision against.",
    )
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    bundle_path = Path(args.bundle).resolve() if args.bundle else repo_root / "patches-bundle.json"
    changelog_path = Path(args.changelog).resolve() if args.changelog else repo_root / "CHANGELOG.md"

    errors: list[str] = []

    try:
        bundle = json.loads(bundle_path.read_text())
    except Exception as exc:
        print(f"FAIL: could not read/parse bundle: {bundle_path}: {exc}")
        return 1

    try:
        changelog_text = changelog_path.read_text(errors="ignore")
    except Exception as exc:
        print(f"FAIL: could not read changelog: {changelog_path}: {exc}")
        return 1

    raw_version = str(bundle.get("version", "")).strip()
    version = normalize_version(raw_version)
    created_at = str(bundle.get("created_at", "")).strip()
    download_url = str(bundle.get("download_url", "")).strip()
    signature_url = str(bundle.get("signature_download_url", "")).strip()

    print(f"MANAGER_CHANGELOG_GUARD_bundle={bundle_path}")
    print(f"MANAGER_CHANGELOG_GUARD_changelog={changelog_path}")
    print(f"MANAGER_CHANGELOG_GUARD_raw_version={raw_version}")
    print(f"MANAGER_CHANGELOG_GUARD_normalized_version={version}")
    print(f"MANAGER_CHANGELOG_GUARD_created_at={created_at}")
    print(f"MANAGER_CHANGELOG_GUARD_required_scope={args.scope}")

    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        errors.append(f"bundle version is not semver after normalization: {raw_version!r}")

    if raw_version.startswith("v"):
        errors.append(f"bundle version must be bare semver for Manager source metadata: {raw_version!r}")

    if created_at.endswith("Z"):
        errors.append(f"created_at must be LocalDateTime-like without Z: {created_at!r}")

    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}", created_at):
        errors.append(f"created_at shape is not yyyy-MM-ddTHH:mm:ss: {created_at!r}")

    if f"patches-{version}.mpp" not in download_url:
        errors.append(f"download_url does not point to patches-{version}.mpp: {download_url!r}")

    if not signature_url.endswith(f"patches-{version}.mpp.asc"):
        errors.append(f"signature_download_url does not point to patches-{version}.mpp.asc: {signature_url!r}")

    if "**Morphe Causality Test:**" in changelog_text:
        errors.append("CHANGELOG.md still contains temporary Morphe Causality Test scope")

    entries = parse_changelog(changelog_text)
    current_entries = [entry for entry in entries if normalize_version(entry.version) == version]
    current_scope_match = any(
        any(scope_matches(scope, args.scope) for scope in entry.scopes)
        for entry in current_entries
    )

    print(f"MANAGER_CHANGELOG_GUARD_parsed_entries={len(entries)}")
    print(
        "MANAGER_CHANGELOG_GUARD_current_entry_scopes="
        + "|".join(",".join(entry.scopes) for entry in current_entries)
    )
    print(f"MANAGER_CHANGELOG_GUARD_current_scope_match={current_scope_match}")

    if not current_entries:
        errors.append(f"CHANGELOG.md has no Manager-parsable heading for current version {version}")

    if not current_scope_match:
        errors.append(
            f"CHANGELOG.md current version {version} has no scoped bullet matching **{args.scope}:**"
        )

    if args.installed_version:
        newer_entries = [entry for entry in entries if is_newer(args.installed_version, entry.version)]
        replay_match = any(
            any(scope_matches(scope, args.scope) for scope in entry.scopes)
            for entry in newer_entries
        )
        version_newer = is_newer(args.installed_version, version)
        update_badge_model = version_newer and replay_match

        print(f"MANAGER_CHANGELOG_GUARD_installed_version={args.installed_version}")
        print("MANAGER_CHANGELOG_GUARD_newer_entries=" + ",".join(entry.version for entry in newer_entries))
        print(f"MANAGER_CHANGELOG_GUARD_replay_scope_match={replay_match}")
        print(f"MANAGER_CHANGELOG_GUARD_replay_update_badge={update_badge_model}")

        if version_newer and not replay_match:
            errors.append(
                f"installed-version replay says {version} > {args.installed_version}, "
                f"but no newer **{args.scope}:** scoped changelog entry exists"
            )

    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        print("RESULT=MORPHE_MANAGER_CHANGELOG_GUARD_FAIL")
        return 1

    print("RESULT=MORPHE_MANAGER_CHANGELOG_GUARD_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
