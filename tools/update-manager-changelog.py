#!/usr/bin/env python3
"""Write a Morphe Manager-compatible CHANGELOG.md entry.

The Manager update badge parser expects release entries inside the
MORPHE_MANAGER_CHANGELOG block and scoped bullets like:

## [1.4.57](https://github.com/owner/repo/compare/morphe-patches-56...morphe-patches-57) (2026-07-07)

* **Boost for Reddit:** Release summary.

This helper updates that block deterministically and removes malformed duplicate
entries for the same version outside the Manager block.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import re
import subprocess
import sys
from pathlib import Path


START_MARKER = "<!-- MORPHE_MANAGER_CHANGELOG_START -->"
END_MARKER = "<!-- MORPHE_MANAGER_CHANGELOG_END -->"


def normalize_version(value: str) -> str:
    value = value.strip()
    return value[1:] if value.startswith("v") else value


def version_patch(value: str) -> str:
    normalized = normalize_version(value)
    parts = normalized.split(".")
    if len(parts) != 3 or not all(part.isdigit() for part in parts):
        raise ValueError(f"version must be semver x.y.z, got {value!r}")
    return parts[2]


def default_tag(version: str) -> str:
    return f"morphe-patches-{version_patch(version)}"


def detect_repo_slug(repo_root: Path) -> str:
    try:
        remote = subprocess.check_output(
            ["git", "config", "--get", "remote.origin.url"],
            cwd=repo_root,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except Exception:
        return "brealorg/breal-morphe-patches"

    if remote.endswith(".git"):
        remote = remote[:-4]

    if remote.startswith("git@github.com:"):
        return remote.removeprefix("git@github.com:")
    if "github.com/" in remote:
        return remote.split("github.com/", 1)[1].strip("/")
    return "brealorg/breal-morphe-patches"


def is_version_heading(line: str, version: str) -> bool:
    normalized = re.escape(normalize_version(version))
    return bool(
        re.match(
            rf"^##\s+(?:\[)?v?{normalized}(?:\])?(?:\(|\s|$)",
            line,
        )
    )


def strip_version_sections(lines: list[str], version: str) -> list[str]:
    """Remove all sections whose heading is the target version.

    This is line-based on purpose; it does not use a cross-document regex that
    can accidentally consume the Manager markers.
    """
    out: list[str] = []
    i = 0
    while i < len(lines):
        if is_version_heading(lines[i], version):
            i += 1
            while (
                i < len(lines)
                and not lines[i].startswith("## ")
                and START_MARKER not in lines[i]
                and END_MARKER not in lines[i]
            ):
                i += 1
            continue
        out.append(lines[i])
        i += 1
    return out


def update_changelog(
    changelog: Path,
    version: str,
    tag: str,
    previous_tag: str,
    scope: str,
    texts: list[str],
    date: str,
    repo_slug: str,
) -> str:
    if not texts:
        raise ValueError("at least one --text line is required")

    raw = changelog.read_text()
    lines = raw.splitlines(keepends=True)

    try:
        start_i = next(i for i, line in enumerate(lines) if START_MARKER in line)
        end_i = next(i for i, line in enumerate(lines) if END_MARKER in line)
    except StopIteration as exc:
        raise ValueError("CHANGELOG.md is missing Morphe Manager changelog markers") from exc

    if start_i >= end_i:
        raise ValueError("CHANGELOG.md Manager changelog markers are in the wrong order")

    pre = strip_version_sections(lines[:start_i], version)
    body = strip_version_sections(lines[start_i + 1 : end_i], version)
    post = strip_version_sections(lines[end_i:], version)

    repo_url = f"https://github.com/{repo_slug}"
    summary = " ".join(text.strip() for text in texts if text.strip())
    if not summary:
        raise ValueError("empty changelog text after trimming")

    entry = [
        f"## [{normalize_version(version)}]({repo_url}/compare/{previous_tag}...{tag}) ({date})\n",
        "\n",
        f"* **{scope}:** {summary}\n",
        "\n",
    ]

    while pre and pre[-1].strip() == "":
        pre.pop()

    new_lines = pre + ["\n", START_MARKER + "\n"] + entry + body + post
    text = "".join(new_lines)
    text = re.sub(r"(?m)^# Changelog\n{3,}", "# Changelog\n\n", text)

    changelog.write_text(text)
    return text


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Write a Manager-compatible CHANGELOG.md entry inside the Morphe Manager block."
    )
    parser.add_argument("--repo-root", default=".", help="Repository root. Defaults to current directory.")
    parser.add_argument("--changelog", default=None, help="CHANGELOG.md path. Defaults to repo root CHANGELOG.md.")
    parser.add_argument("--version", required=True, help="Release version, e.g. 1.4.57.")
    parser.add_argument("--tag", default=None, help="Release tag. Defaults to morphe-patches-<patch>.")
    parser.add_argument("--previous-tag", required=True, help="Previous release tag used in compare URL.")
    parser.add_argument("--scope", default="Boost for Reddit", help="Manager changelog scope.")
    parser.add_argument("--text", action="append", required=True, help="Changelog text. Can be repeated.")
    parser.add_argument("--date", default=None, help="Release date YYYY-MM-DD. Defaults to today.")
    parser.add_argument("--repo", default=None, help="GitHub owner/repo. Defaults to remote.origin.url.")
    parser.add_argument("--dry-run", action="store_true", help="Print updated content without writing.")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    changelog = Path(args.changelog).resolve() if args.changelog else repo_root / "CHANGELOG.md"
    version = normalize_version(args.version)
    tag = args.tag or default_tag(version)
    date = args.date or _dt.date.today().isoformat()
    repo_slug = args.repo or detect_repo_slug(repo_root)

    if args.dry_run:
        original = changelog.read_text()
        import tempfile

        with tempfile.NamedTemporaryFile("w+", delete=False) as tmp:
            tmp.write(original)
            tmp_path = Path(tmp.name)
        try:
            updated = update_changelog(
                tmp_path,
                version=version,
                tag=tag,
                previous_tag=args.previous_tag,
                scope=args.scope,
                texts=args.text,
                date=date,
                repo_slug=repo_slug,
            )
            print(updated, end="")
        finally:
            tmp_path.unlink(missing_ok=True)
        return 0

    update_changelog(
        changelog,
        version=version,
        tag=tag,
        previous_tag=args.previous_tag,
        scope=args.scope,
        texts=args.text,
        date=date,
        repo_slug=repo_slug,
    )

    print(f"MANAGER_CHANGELOG_VERSION={version}")
    print(f"MANAGER_CHANGELOG_TAG={tag}")
    print(f"MANAGER_CHANGELOG_PREVIOUS_TAG={args.previous_tag}")
    print(f"MANAGER_CHANGELOG_SCOPE={args.scope}")
    print(f"RESULT=MORPHE_MANAGER_CHANGELOG_UPDATE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
