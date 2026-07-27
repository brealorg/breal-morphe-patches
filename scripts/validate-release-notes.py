#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

STANDARD_HEADINGS = {
    "changes",
    "user impact",
    "validation",
}
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
MIN_FULL_LENGTH = 450
MIN_HUMAN_LENGTH = 80


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


def exact_heading_positions(body: str, heading: str) -> list[int]:
    pattern = re.compile(rf"(?m)^{re.escape(heading)}\s*$")
    return [match.start() for match in pattern.finditer(body)]


def app_heading_positions(body: str) -> list[int]:
    positions: list[int] = []
    for match in re.finditer(r"(?m)^###\s+(.+?)\s*$", body):
        normalized = match.group(1).strip().lower()
        if normalized in STANDARD_HEADINGS:
            continue
        if normalized in FORBIDDEN_GENERIC_TITLES:
            continue
        positions.append(match.start())
    return positions


def section_body(body: str, heading: str) -> str:
    positions = exact_heading_positions(body, heading)
    if len(positions) != 1:
        return ""
    start = positions[0] + len(heading)
    following = re.search(r"(?m)^###\s+", body[start:])
    end = start + following.start() if following else len(body)
    return body[start:end].strip()


def validate(
    body: str,
    *,
    version: str,
    tag: str,
    asset: str,
    sha256: str,
    require_sha: bool,
    human_input_only: bool = False,
) -> list[str]:
    errors: list[str] = []
    minimum = MIN_HUMAN_LENGTH if human_input_only else MIN_FULL_LENGTH

    if len(body) < minimum:
        errors.append(
            f"release notes are too short: {len(body)} chars, "
            f"expected at least {minimum}"
        )

    expected_title = f"# Morphe patch bundle {version}"
    if len(exact_heading_positions(body, expected_title)) != 1:
        errors.append(f"release notes missing exact title heading: {expected_title!r}")

    apps = app_heading_positions(body)
    if not apps:
        errors.append(
            "release notes missing app/area heading, "
            "for example '### Boost for Reddit'"
        )

    required = ["### Changes", "### User impact"]
    if not human_input_only:
        required.append("### Validation")

    for heading in required:
        positions = exact_heading_positions(body, heading)
        if len(positions) != 1:
            errors.append(
                f"release notes require exactly one exact section heading: {heading}"
            )

    for wrong in ("## Changes", "## User impact", "## Validation"):
        if exact_heading_positions(body, wrong):
            errors.append(
                f"release notes must use a level-three heading instead of {wrong!r}"
            )

    changes_positions = exact_heading_positions(body, "### Changes")
    impact_positions = exact_heading_positions(body, "### User impact")
    validation_positions = exact_heading_positions(body, "### Validation")

    if apps and changes_positions and apps[0] > changes_positions[0]:
        errors.append("app/area heading must appear before Changes")
    if changes_positions and impact_positions:
        if changes_positions[0] > impact_positions[0]:
            errors.append("Changes must appear before User impact")
    if not human_input_only and impact_positions and validation_positions:
        if impact_positions[0] > validation_positions[0]:
            errors.append("User impact must appear before Validation")

    changes_body = section_body(body, "### Changes")
    if changes_positions and not concrete_bullets(changes_body):
        errors.append(
            "Changes section must contain at least one concrete change bullet"
        )

    impact_body = section_body(body, "### User impact")
    if impact_positions and not impact_body:
        errors.append("User impact section must not be empty")

    if not human_input_only:
        if not tag:
            errors.append("expected release tag argument is empty")
        elif tag not in body:
            errors.append(f"release notes missing release tag: {tag}")

        if not asset:
            errors.append("expected release asset argument is empty")
        elif asset not in body:
            errors.append(f"release notes missing asset name: {asset}")

        if require_sha:
            if not re.fullmatch(r"[0-9a-f]{64}", sha256):
                errors.append(f"invalid expected sha256 argument: {sha256!r}")
            elif sha256 not in body:
                errors.append("release notes missing expected MPP SHA256")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate human-readable GitHub release notes for Morphe releases."
    )
    parser.add_argument("--notes-file", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", default="")
    parser.add_argument("--asset", default="")
    parser.add_argument("--sha256", default="")
    parser.add_argument("--require-sha", action="store_true")
    parser.add_argument(
        "--human-input-only",
        action="store_true",
        help=(
            "Validate the human-authored title/app/Changes/User impact portion "
            "before build, signing, and final Validation metadata are added."
        ),
    )
    args = parser.parse_args()

    body = read_body(Path(args.notes_file))
    errors = validate(
        body,
        version=args.version,
        tag=args.tag,
        asset=args.asset,
        sha256=args.sha256,
        require_sha=args.require_sha,
        human_input_only=args.human_input_only,
    )

    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1

    print("RELEASE_NOTES_OK")
    print(f"mode={'human' if args.human_input_only else 'full'}")
    print(f"chars={len(body)}")
    print(f"version={args.version}")
    if args.tag:
        print(f"tag={args.tag}")
    if args.asset:
        print(f"asset={args.asset}")
    if args.sha256:
        print(f"sha256={args.sha256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
