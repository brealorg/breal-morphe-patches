#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "verify-remote-release.py"


def load_module():
    spec = importlib.util.spec_from_file_location("verify_remote_release", SCRIPT)
    assert spec is not None
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def test_parse_repo_slug_accepts_github_ssh_and_https():
    mod = load_module()
    assert mod.parse_repo_slug("git@github.com:brealorg/breal-morphe-patches.git") == (
        "brealorg",
        "breal-morphe-patches",
    )
    assert mod.parse_repo_slug("https://github.com/brealorg/breal-morphe-patches.git") == (
        "brealorg",
        "breal-morphe-patches",
    )


def test_expected_asset_and_download_url():
    mod = load_module()
    assert mod.expected_asset_name("1.4.46") == "patches-1.4.46.mpp"
    assert mod.expected_download_url(
        "brealorg",
        "breal-morphe-patches",
        "v1.4.46",
        "patches-1.4.46.mpp",
    ) == "https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.46/patches-1.4.46.mpp"


def test_readme_current_release_table_and_sha_resolution():
    mod = load_module()
    readme = """# Repo

## Current release

| Field | Value |
|---|---|
| Version | `1.4.46` |
| Release tag | `v1.4.46` |
| Asset | `patches-1.4.46.mpp` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.46/patches-1.4.46.mpp` |
| SHA256 | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |

## Verification

Release `1.4.45` is historical.
"""

    rows = mod.parse_readme_current_release_table(readme)
    assert rows["Version"] == "1.4.46"
    assert rows["Release tag"] == "v1.4.46"
    assert rows["Asset"] == "patches-1.4.46.mpp"
    assert mod.readme_expected_sha(readme) == "a" * 64
    assert "1.4.45" not in mod.readme_current_release_section(readme)


if __name__ == "__main__":
    test_parse_repo_slug_accepts_github_ssh_and_https()
    test_expected_asset_and_download_url()
    test_readme_current_release_table_and_sha_resolution()
