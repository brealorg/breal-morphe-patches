#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path


def fail(msg: str) -> None:
    raise SystemExit(f"FAIL: {msg}")


def replace_table_value(text: str, key: str, value: str) -> str:
    pattern = re.compile(rf"^\| {re.escape(key)} \| `[^`]*` \|$", re.MULTILINE)
    replacement = f"| {key} | `{value}` |"
    new_text, count = pattern.subn(replacement, text, count=1)
    if count != 1:
        fail(f"could not replace README Current release table value for {key!r}")
    return new_text


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: update-readme-current-release.py VERSION")

    version = sys.argv[1].removeprefix("v")
    repo = os.environ.get("GITHUB_REPOSITORY", "brealorg/breal-morphe-patches")
    tag = f"v{version}"
    asset = f"patches-{version}.mpp"
    mpp = Path("patches/build/libs") / asset
    readme = Path("README.md")
    bundle = Path("patches-bundle.json")

    if not mpp.exists():
        fail(f"MPP does not exist: {mpp}")

    sha = hashlib.sha256(mpp.read_bytes()).hexdigest()
    download_url = f"https://github.com/{repo}/releases/download/{tag}/{asset}"

    text = readme.read_text()
    text = replace_table_value(text, "Version", version)
    text = replace_table_value(text, "Release tag", tag)
    text = replace_table_value(text, "Asset", asset)
    text = replace_table_value(text, "SHA256", sha)
    text = replace_table_value(text, "Download URL", download_url)

    text, sha_count = re.subn(r"SHA256: `[^`]*`", f"SHA256: `{sha}`", text, count=1)
    if sha_count != 1:
        fail("could not replace standalone SHA256 line")

    readme.write_text(text)

    if bundle.exists():
        data = json.loads(bundle.read_text())
        expected = {
            "version": version,
            "download_url": download_url,
            "signature_download_url": f"{download_url}.asc",
        }
        for key, value in expected.items():
            if data.get(key) != value:
                fail(f"patches-bundle.json {key}={data.get(key)!r}, expected {value!r}")

    print(f"README_CURRENT_RELEASE_UPDATED version={version} tag={tag} asset={asset} sha256={sha}")


if __name__ == "__main__":
    main()
