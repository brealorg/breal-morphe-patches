#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path

VERSION_RE = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
ISSUE_RE = re.compile(r"^[0-9]+$")


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require_nonempty(value: str, label: str) -> str:
    normalized = value.strip()
    if not normalized:
        fail(f"{label} must not be empty")
    if "\x00" in normalized:
        fail(f"{label} must not contain NUL")
    return normalized


def normalize_app_area(value: str) -> str:
    normalized = require_nonempty(value, "app area")
    if "\n" in normalized or "\r" in normalized:
        fail("app area must be a single line")
    if normalized.startswith("#"):
        fail("app area must not include a Markdown heading prefix")
    return normalized


def normalize_changes(value: str) -> list[str]:
    normalized = require_nonempty(value, "changes")
    items: list[str] = []
    for raw_line in normalized.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith(("-", "*", "•")):
            line = line.lstrip("-*•").strip()
        if line:
            items.append(f"- {line}")
    if not items:
        fail("changes must contain at least one concrete item")
    return items


def compose_release_notes(
    *,
    version: str,
    app_area: str,
    changes: str,
    user_impact: str,
    issue_number: str = "",
) -> str:
    if not VERSION_RE.fullmatch(version):
        fail(f"invalid semantic version: {version!r}")

    app_heading = normalize_app_area(app_area)
    change_lines = normalize_changes(changes)
    impact = require_nonempty(user_impact, "user impact")

    issue = issue_number.strip()
    if issue and not ISSUE_RE.fullmatch(issue):
        fail("issue number must contain digits only")

    lines = [
        f"# Morphe patch bundle {version}",
        "",
        f"### {app_heading}",
        "",
        "### Changes",
        "",
        *change_lines,
        "",
        "### User impact",
        "",
        impact,
    ]
    if issue:
        lines.extend(("", f"Issue: #{issue}."))
    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Compose canonical human-readable Morphe release notes without "
            "requiring callers to provide Markdown headings."
        )
    )
    parser.add_argument("--version", required=True)
    parser.add_argument("--app-area", required=True)
    parser.add_argument("--changes", required=True)
    parser.add_argument("--user-impact", required=True)
    parser.add_argument("--issue-number", default="")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    body = compose_release_notes(
        version=args.version,
        app_area=args.app_area,
        changes=args.changes,
        user_impact=args.user_impact,
        issue_number=args.issue_number,
    )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(body, encoding="utf-8")

    print("RELEASE_NOTES_COMPOSED=PASS")
    print(f"RELEASE_NOTES_FILE={output}")
    print(f"RELEASE_NOTES_VERSION={args.version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
