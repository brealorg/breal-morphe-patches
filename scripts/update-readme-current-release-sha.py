#!/usr/bin/env python3
"""Update README current-release SHA entries for a Morphe patch bundle.

This helper is intentionally narrow: it only rewrites SHA256 values that are in
README regions tied to the supplied version/tag/asset. It is used by release
finalization and metadata-only repair scripts so ad-hoc regexes do not diverge.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


SHA_RE = re.compile(r"\b[0-9a-f]{64}\b")


def replace_sha_in_window(text: str, start: int, end: int, new_sha: str) -> tuple[str, int]:
    window = text[start:end]
    matches = list(SHA_RE.finditer(window))
    if not matches:
        return text, 0

    replacements = 0
    for match in reversed(matches):
        abs_start = start + match.start()
        abs_end = start + match.end()
        old_sha = text[abs_start:abs_end]
        if old_sha == new_sha:
            continue
        text = text[:abs_start] + new_sha + text[abs_end:]
        replacements += 1
    return text, replacements


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--asset", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--readme", default="README.md")
    args = parser.parse_args()

    if not re.fullmatch(r"[0-9a-f]{64}", args.sha256):
        print(f"FAIL: invalid sha256: {args.sha256}", file=sys.stderr)
        return 1

    readme = Path(args.readme)
    if not readme.exists():
        print(f"FAIL: README missing: {readme}", file=sys.stderr)
        return 1

    text = readme.read_text()
    original = text
    replacements = 0

    lines = text.splitlines(keepends=True)
    offsets: list[int] = []
    pos = 0
    for line in lines:
        offsets.append(pos)
        pos += len(line)

    token_lines: set[int] = set()
    tokens = (args.version, args.tag, args.asset)
    for idx, line in enumerate(lines):
        if any(token in line for token in tokens):
            token_lines.add(idx)

    if not token_lines:
        print("FAIL: no README lines matched version/tag/asset", file=sys.stderr)
        return 1

    # Table/current-release blocks: rewrite SHA lines in a local line window.
    windows: list[tuple[int, int]] = []
    for idx in sorted(token_lines):
        start_line = max(0, idx - 8)
        end_line = min(len(lines), idx + 18)
        start = offsets[start_line]
        end = offsets[end_line] if end_line < len(lines) else len(text)
        windows.append((start, end))

    # Known local-release validation section: rewrite a broader window around
    # "Release `<version>`" because README contains repeated SHA forms there.
    release_token = f"Release `{args.version}`"
    search_from = 0
    while True:
        hit = text.find(release_token, search_from)
        if hit < 0:
            break
        windows.append((max(0, hit - 600), min(len(text), hit + 2200)))
        search_from = hit + len(release_token)

    # Deduplicate approximate windows.
    merged: list[tuple[int, int]] = []
    for start, end in sorted(windows):
        if not merged or start > merged[-1][1]:
            merged.append((start, end))
        else:
            old_start, old_end = merged[-1]
            merged[-1] = (old_start, max(old_end, end))

    for start, end in reversed(merged):
        text, count = replace_sha_in_window(text, start, end, args.sha256)
        replacements += count

    if args.sha256 not in text:
        print("FAIL: new SHA not present after README update", file=sys.stderr)
        return 1

    readme.write_text(text)

    print(f"README={readme}")
    print(f"VERSION={args.version}")
    print(f"TAG={args.tag}")
    print(f"ASSET={args.asset}")
    print(f"SHA256={args.sha256}")
    print(f"REPLACEMENTS={replacements}")
    print(f"CHANGED={text != original}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
