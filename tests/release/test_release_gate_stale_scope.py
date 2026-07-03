#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RELEASE_GATE = ROOT / "scripts" / "release-gate.py"


def load_release_gate():
    spec = importlib.util.spec_from_file_location("release_gate", RELEASE_GATE)
    assert spec is not None
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def test_stale_scope_ignores_readme_historical_sections_and_bundle_description():
    gate = load_release_gate()

    readme = """# Breal Morphe Patches

## Current release

| Field | Value |
|---|---|
| Version | `1.4.46` |
| Release tag | `v1.4.46` |
| Asset | `patches-1.4.46.mpp` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.46/patches-1.4.46.mpp` |

## Verification

Release `1.4.45` is prepared and locally verified with:
- Release tag `morphe-patches-45`.
- Expected release asset:
`patches-1.4.45.mpp`
"""

    bundle = {
        "version": "1.4.46",
        "download_url": "https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.46/patches-1.4.46.mpp",
        "signature_download_url": "https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.46/patches-1.4.46.mpp.asc",
        "description": "## [1.4.46](https://github.com/brealorg/breal-morphe-patches/compare/v1.4.45...v1.4.46) (2026-07-03)",
    }

    readme_scope = gate.readme_current_release_section(readme)
    bundle_scope = gate.bundle_active_release_fields(bundle)

    assert "1.4.46" in readme_scope
    assert "v1.4.46" in readme_scope
    assert "patches-1.4.46.mpp" in readme_scope

    assert "1.4.45" not in readme_scope
    assert "morphe-patches-45" not in readme_scope
    assert "patches-1.4.45.mpp" not in readme_scope

    assert "1.4.46" in bundle_scope
    assert "v1.4.46" in bundle_scope
    assert "patches-1.4.46.mpp" in bundle_scope

    assert "v1.4.45...v1.4.46" not in bundle_scope
