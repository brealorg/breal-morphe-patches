#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "publish-release.py"
WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"
PREPARE = ROOT / "scripts" / "prepare-release.py"

EXPECTED_METADATA_FILES = (
    "CHANGELOG.md",
    "README.md",
    "gradle.properties",
    "patches-bundle.json",
    "patches-list.json",
)


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
        "CHANGELOG.md",
        "README.md",
        "gradle.properties",
        "patches-bundle.json",
        "patches-list.json",
        "scripts/publish-release.py",
    ]
    assert mod.changed_metadata_files(files) == [
        "CHANGELOG.md",
        "README.md",
        "gradle.properties",
        "patches-bundle.json",
        "patches-list.json",
    ]
    assert mod.unexpected_changed_files(files) == ["scripts/publish-release.py"]


def test_release_metadata_contract_is_aligned():
    mod = load_module()
    workflow = WORKFLOW.read_text(encoding="utf-8")
    prepare = PREPARE.read_text(encoding="utf-8")
    metadata = " ".join(EXPECTED_METADATA_FILES)

    assert mod.ALLOWED_PREPARED_METADATA_FILES == set(EXPECTED_METADATA_FILES)
    assert f"git diff --check -- {metadata}" in workflow
    assert f"git add {metadata}" in workflow
    assert f"git --no-pager diff -- {metadata}" in workflow
    assert f"git --no-pager diff -- {metadata}" in prepare
    assert f"git add {metadata}" in prepare
    assert (
        "grep -Ev '^(CHANGELOG\\.md|README\\.md|gradle\\.properties|"
        "patches-bundle\\.json|patches-list\\.json)$'"
    ) in workflow


def test_push_refs_is_one_atomic_transaction():
    mod = load_module()
    calls = []
    mod.run = lambda command: calls.append(command)

    mod.push_refs(
        "origin",
        "main",
        ["dev", "main"],
        "morphe-patches-83",
        dry_run=False,
    )

    assert calls == [[
        "git",
        "push",
        "--atomic",
        "origin",
        "HEAD:refs/heads/main",
        "HEAD:refs/heads/dev",
        "refs/tags/morphe-patches-83:refs/tags/morphe-patches-83",
    ]]


def test_workflow_and_manual_hint_use_atomic_release_push():
    workflow = WORKFLOW.read_text(encoding="utf-8")
    prepare = PREPARE.read_text(encoding="utf-8")

    assert "git push --atomic origin" in workflow
    assert '"HEAD:refs/heads/main"' in workflow
    assert '"HEAD:refs/heads/dev"' in workflow
    assert '"refs/tags/$TAG:refs/tags/$TAG"' in workflow
    assert "git push origin HEAD:main" not in workflow
    assert "git push origin HEAD:dev" not in workflow
    assert "git push origin \"$TAG\"" not in workflow
    assert (
        "git push --atomic origin HEAD:refs/heads/main HEAD:refs/heads/dev "
        "refs/tags/<tag>:refs/tags/<tag>"
    ) in prepare


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
    test_release_metadata_contract_is_aligned()
    test_push_refs_is_one_atomic_transaction()
    test_workflow_and_manual_hint_use_atomic_release_push()
    test_readme_current_release_table_parser()
