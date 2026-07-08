#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_gradle_version(path: Path) -> str:
    match = re.search(r"(?m)^\s*version\s*=\s*([^\s#]+)\s*$", read(path))
    if not match:
        raise SystemExit("Could not parse version from gradle.properties")
    return match.group(1)


def default_tag_for(version: str) -> str:
    return f"morphe-patches-{version.split('.')[-1]}"


def mpp_name_for(version: str) -> str:
    return f"patches-{version}.mpp"


def readme_table_sha(text: str) -> str | None:
    match = re.search(r"(?m)^\| SHA256 \| `([0-9a-f]{64})` \|$", text)
    return match.group(1) if match else None


def update_current_release_sha(text: str, sha: str) -> str:
    text, count = re.subn(
        r"(?m)^(\| SHA256 \| `)[0-9a-f]{64}(` \|)$",
        rf"\g<1>{sha}\2",
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Could not update README Current release SHA256 table row")

    text, count = re.subn(
        r"(?m)^(SHA256:\s*`)[0-9a-f]{64}(`\s*)$",
        rf"\g<1>{sha}\2",
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Could not update README standalone SHA256 line")

    return text


def replace_verification(text: str, version: str, tag: str, asset: str, sha: str) -> str:
    block = (
        "## Verification\n\n"
        f"Release `{version}` is prepared and locally verified with:\n\n"
        f"- Release tag `{tag}`.\n"
        "- Local built MPP SHA256 matching README.\n"
        f"`{sha}`\n"
        f"- `patches-bundle.json` returning version `{version}`.\n"
        f"- `patches-bundle.json` pointing to the `{tag}` asset.\n"
        "- Expected release asset:\n"
        f"`{asset}`\n"
        f"- `{sha}  {asset}`\n\n"
    )
    text, count = re.subn(
        r"(?ms)^## Verification\n\n.*?(?=^## Development notes)",
        block,
        text,
        count=1,
    )
    if count != 1:
        raise SystemExit("Could not update README Verification section")
    return text


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bind README release SHA to the exact local MPP artifact intended for publication."
    )
    parser.add_argument("--version", default=None)
    parser.add_argument("--tag", default=None)
    parser.add_argument("--mpp", default=None)
    parser.add_argument("--readme", default="README.md")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    root = Path.cwd()
    version = args.version or parse_gradle_version(root / "gradle.properties")
    tag = args.tag or default_tag_for(version)
    asset = mpp_name_for(version)
    mpp = Path(args.mpp) if args.mpp else root / "patches" / "build" / "libs" / asset
    readme_path = Path(args.readme)

    if not mpp.exists():
        raise SystemExit(f"MPP does not exist: {mpp}")
    if not readme_path.exists():
        raise SystemExit(f"README does not exist: {readme_path}")

    actual_sha = sha256_file(mpp)
    text = read(readme_path)
    current_sha = readme_table_sha(text)

    print(f"BIND_RELEASE_VERSION={version}")
    print(f"BIND_RELEASE_TAG={tag}")
    print(f"BIND_RELEASE_MPP={mpp}")
    print(f"BIND_RELEASE_MPP_SHA={actual_sha}")
    print(f"BIND_RELEASE_README_SHA={current_sha}")

    if args.check:
        if current_sha != actual_sha:
            print("RESULT=MORPHE_BIND_RELEASE_ARTIFACT_SHA_CHECK_FAIL")
            return 1
        print("RESULT=MORPHE_BIND_RELEASE_ARTIFACT_SHA_CHECK_OK")
        return 0

    text = update_current_release_sha(text, actual_sha)
    text = replace_verification(text, version, tag, asset, actual_sha)
    write(readme_path, text)

    print("RESULT=MORPHE_BIND_RELEASE_ARTIFACT_SHA_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
