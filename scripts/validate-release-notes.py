#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REQUIRED_SECTIONS = (
    "### Changes",
    "### User impact",
    "### Validation",
)

GENERIC_ONLY_FRAGMENTS = (
    "Final local release gate passed before publish.",
    "README SHA is aligned to the published MPP.",
    "Assets:",
    "Validation:",
)

FORBIDDEN_GENERIC_TITLES = {
    "morphe-patches",
    "release notes",
}

MIN_LENGTH = 450


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_body(path: Path) -> str:
    if not path.exists():
        fail(f"release notes file does not exist: {path}")
    body = path.read_text(encoding="utf-8").strip()
    if not body:
        fail(f"release notes file is empty: {path}")
    return body


def concrete_bullets(body: str) -> list[str]:
    bullets: list[str] = []
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped.startswith(("-", "*", "•")):
            continue
        normalized = stripped.lstrip("-*•").strip()
        if not normalized:
            continue
        if any(fragment in normalized for fragment in GENERIC_ONLY_FRAGMENTS):
            continue
        bullets.append(normalized)
    return bullets


def has_app_heading(body: str) -> bool:
    headings = re.findall(r"(?m)^###\s+(.+?)\s*$", body)
    for heading in headings:
        normalized = heading.strip().lower()
        if normalized in {"changes", "user impact", "validation"}:
            continue
        if normalized in FORBIDDEN_GENERIC_TITLES:
            continue
        return True
    return False


def validate(
    body: str,
    *,
    version: str,
    tag: str,
    asset: str,
    sha256: str,
    require_sha: bool,
) -> list[str]:
    errors: list[str] = []

    if len(body) < MIN_LENGTH:
        errors.append(f"release notes are too short: {len(body)} chars, expected at least {MIN_LENGTH}")

    title = f"Morphe patch bundle {version}"
    if title not in body:
        errors.append(f"release notes missing title text: {title!r}")

    if not has_app_heading(body):
        errors.append("release notes missing app/area heading, for example '### Boost for Reddit'")

    for section in REQUIRED_SECTIONS:
        if section not in body:
            errors.append(f"release notes missing required section: {section}")

    bullets = concrete_bullets(body)
    if not bullets:
        errors.append("release notes missing at least one concrete non-validation change bullet")

    if tag not in body:
        errors.append(f"release notes missing release tag: {tag}")

    if asset not in body:
        errors.append(f"release notes missing asset name: {asset}")

    if require_sha:
        if not re.fullmatch(r"[0-9a-f]{64}", sha256):
            errors.append(f"invalid expected sha256 argument: {sha256!r}")
        elif sha256 not in body:
            errors.append("release notes missing expected MPP SHA256")

    validation_pos = body.find("### Validation")
    changes_pos = body.find("### Changes")
    if validation_pos != -1 and changes_pos != -1 and validation_pos < changes_pos:
        errors.append("Validation section appears before Changes; release notes look validation-first/generic")

    lowered = body.lower()
    if "assets:" in lowered and "### changes" not in lowered:
        errors.append("release notes look assets-only and lack Changes section")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate human-readable GitHub release notes for Morphe releases.")
    parser.add_argument("--notes-file", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--asset", required=True)
    parser.add_argument("--sha256", default="")
    parser.add_argument("--require-sha", action="store_true")
    args = parser.parse_args()

    body = read_body(Path(args.notes_file))
    errors = validate(
        body,
        version=args.version,
        tag=args.tag,
        asset=args.asset,
        sha256=args.sha256,
        require_sha=args.require_sha,
    )

    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1

    print("RELEASE_NOTES_OK")
    print(f"chars={len(body)}")
    print(f"version={args.version}")
    print(f"tag={args.tag}")
    print(f"asset={args.asset}")
    if args.sha256:
        print(f"sha256={args.sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
