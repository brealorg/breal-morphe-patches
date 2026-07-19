#!/usr/bin/env python3
"""Compatibility entry point for protected-main release publication.

All release reconciliation and mutations live in scripts/releasectl.py.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Publish an already merged Morphe release through releasectl.py."
    )
    parser.add_argument("--version", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--notes-file", required=True)
    parser.add_argument("--remote", default="origin")
    parser.add_argument("--signing-identity", required=True)
    parser.add_argument(
        "--release-commit",
        help="Exact merged main commit. Defaults to the current HEAD.",
    )
    args = parser.parse_args()

    root = Path(__file__).resolve().parent.parent
    release_commit = args.release_commit
    if release_commit is None:
        completed = subprocess.run(
            ("git", "rev-parse", "HEAD"),
            cwd=root,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        release_commit = completed.stdout.strip()

    command = (
        sys.executable,
        str(root / "scripts" / "releasectl.py"),
        "publish",
        "--repo",
        str(root),
        "--remote",
        args.remote,
        "--version",
        args.version,
        "--tag",
        args.tag,
        "--release-notes-file",
        args.notes_file,
        "--protected-main-commit",
        release_commit,
        "--signing-identity",
        args.signing_identity,
    )
    os.execv(sys.executable, command)
    return 127


if __name__ == "__main__":
    raise SystemExit(main())
