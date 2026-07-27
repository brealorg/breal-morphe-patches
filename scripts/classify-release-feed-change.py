#!/usr/bin/env python3
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path
from typing import Iterable

MODE_CODE_ONLY = "CODE_ONLY"
MODE_FULL_RELEASE = "FULL_RELEASE"

RELEASE_METADATA_PATHS = (
    "CHANGELOG.md",
    "README.md",
    "gradle.properties",
    "patches-bundle.json",
    "patches-list.json",
)
_RELEASE_METADATA_SET = frozenset(RELEASE_METADATA_PATHS)


def normalize_changed_paths(paths: Iterable[str]) -> tuple[str, ...]:
    return tuple(
        sorted(
            {
                path.strip()
                for path in paths
                if path is not None and path.strip()
            }
        )
    )


def classify_changed_paths(
    paths: Iterable[str],
) -> tuple[str, tuple[str, ...], tuple[str, ...]]:
    changed = normalize_changed_paths(paths)
    metadata = tuple(
        path for path in changed if path in _RELEASE_METADATA_SET
    )
    mode = MODE_FULL_RELEASE if metadata else MODE_CODE_ONLY
    return mode, changed, metadata


def require_commit(repo: Path, revision: str) -> str:
    result = subprocess.run(
        [
            "git",
            "-C",
            str(repo),
            "rev-parse",
            "--verify",
            f"{revision}^{{commit}}",
        ],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(
            f"could not resolve commit {revision!r}: {detail}"
        )
    return result.stdout.strip()


def git_changed_paths(
    repo: Path,
    base: str,
    head: str = "HEAD",
) -> tuple[str, ...]:
    require_commit(repo, base)
    require_commit(repo, head)

    result = subprocess.run(
        [
            "git",
            "-C",
            str(repo),
            "diff",
            "--name-only",
            "--no-renames",
            "--diff-filter=ACDMRTUXB",
            base,
            head,
            "--",
        ],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(
            f"git diff failed for {base!r}..{head!r}: {detail}"
        )
    return normalize_changed_paths(result.stdout.splitlines())


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Classify whether release-feed CI must enforce the committed "
            "release MPP SHA or may use the code-only SHA exception."
        )
    )
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--repo", default=".")
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    changed = git_changed_paths(repo, args.base, args.head)
    mode, changed, metadata = classify_changed_paths(changed)

    print(f"RELEASE_FEED_BASE_SHA={require_commit(repo, args.base)}")
    print(f"RELEASE_FEED_HEAD_SHA={require_commit(repo, args.head)}")
    print(f"CHANGED_FILE_COUNT={len(changed)}")
    print(
        "CHANGED_FILES="
        + (",".join(changed) if changed else "NONE")
    )
    print(
        "RELEASE_METADATA_FILES="
        + (",".join(metadata) if metadata else "NONE")
    )
    print(
        "RELEASE_METADATA_CHANGED="
        + ("YES" if metadata else "NO")
    )
    print(f"RELEASE_FEED_GATE_MODE={mode}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"FAIL={error}")
        raise SystemExit(1)
