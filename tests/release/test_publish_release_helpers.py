#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "publish-release.py"


def load_module():
    spec = importlib.util.spec_from_file_location("publish_release", SCRIPT)
    assert spec is not None
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def test_default_asset_path_and_name():
    mod = load_module()
    assert mod.default_asset_name("1.4.47") == "patches-1.4.47.mpp"
    assert str(mod.default_asset_path("1.4.47")).endswith("patches/build/libs/patches-1.4.47.mpp")


def test_dirty_file_classification():
    mod = load_module()
    files = [
        "README.md",
        "gradle.properties",
        "patches-bundle.json",
        "patches-list.json",
        "scripts/publish-release.py",
    ]
    assert mod.changed_metadata_files(files) == [
        "README.md",
        "gradle.properties",
        "patches-bundle.json",
        "patches-list.json",
    ]
    assert mod.unexpected_changed_files(files) == ["scripts/publish-release.py"]


def test_readme_current_release_table_parser():
    mod = load_module()
    readme = """# Repo

## Current release

| Field | Value |
|---|---|
| Version | `1.4.47` |
| Release tag | `v1.4.47` |
| Asset | `patches-1.4.47.mpp` |
| SHA256 | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.47/patches-1.4.47.mpp` |

## Historical notes

Release `1.4.46` was earlier.
"""
    rows = mod.parse_readme_current_release_table(readme)
    assert rows["Version"] == "1.4.47"
    assert rows["Release tag"] == "v1.4.47"
    assert rows["Asset"] == "patches-1.4.47.mpp"
    assert "1.4.46" not in mod.readme_current_release_section(readme)


if __name__ == "__main__":
    test_default_asset_path_and_name()
    test_dirty_file_classification()
    test_readme_current_release_table_parser()
