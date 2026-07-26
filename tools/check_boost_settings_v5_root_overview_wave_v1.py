#!/usr/bin/env python3
"""Static contract for Settings V5 root overview semantic V4."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    / "boostforreddit/settings"
)
PATCH_SOURCE = (
    ROOT
    / "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    / "boostforreddit/misc/settings/BoostMorpheSettingsSkeletonPatch.kt"
)
WAVE_CONTRACT = (
    ROOT / "tools/contracts/boost-settings-v5-root-overview-wave-v1.json"
)
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "68712a6fd73aebda4e5a24a013517191e4abbecc1eb8b7b46d6280546b93d5b7"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"

PAGE_PATTERN = re.compile(
    r'new\s+V5PageSpec\s*\(\s*'
    r'"(?P<page_id>v5/[^"]+)"\s*,\s*'
    r'"(?P<renderer>[^"]+)"\s*,\s*'
    r'new\s+String\s*\[\]\s*\{(?P<keys>.*?)\}\s*\)',
    re.S,
)
KEY_PATTERN = re.compile(r'"([^"]+)"')


def bound_json(path: Path, expected_sha: str) -> dict:
    raw = path.read_bytes()
    actual = hashlib.sha256(raw).hexdigest()
    assert actual == expected_sha, (path, actual, expected_sha)
    return json.loads(raw.decode("utf-8"))


wave = bound_json(WAVE_CONTRACT, EXPECTED_WAVE_SHA)
v5 = bound_json(V5_CONTRACT, EXPECTED_V5_SHA)

assert wave["schema"] == 1
assert wave["issue"] == 121
assert wave["wave"] == "root_overview_morphe_flatten_and_classic_fallback"
assert wave["root"] == "Morphe"
assert wave["semantic_revision"] == {
    "revision": "V4",
    "removed_page_id": "v5/morphe/patch_features",
    "removed_path": "Morphe / Patch features",
    "result": "Morphe renders its 12 configurable feature references directly",
}
assert wave["v5_contract"] == {
    "path": "tools/contracts/boost-settings-v5-completeness-v2.json",
    "sha256": EXPECTED_V5_SHA,
}
assert wave["targets"] == {
    "classic_fallback_count": 1,
    "configurable_feature_reference_count": 12,
    "root_destination_count": 7,
    "root_group_count": 1,
    "root_overview_shell_count": 1,
    "screen_count": 1,
    "task_page_count": 0,
    "visible_item_count": 0,
    "withheld_item_count": 0,
}
assert wave["activation_state"] == {
    "activation_performed": False,
    "root_classic_fallback_location": "root_only",
    "v5_visible_by_default": False,
}

assert [screen for screen in v5["screens"] if screen["root"] == "Morphe"] == [{
    "child_count": 0,
    "control_count": 0,
    "depth": 0,
    "max_children_contract": "",
    "path": "Morphe",
    "role": "root_group",
    "root": "Morphe",
    "task_page": "",
    "title": "Morphe",
}]
assert not [item for item in v5["items"] if item["root"] == "Morphe"]
assert v5["morphe_configurable_feature_reference_count"] == 12
assert v5["screen_node_count"] == 105
assert v5["screen_edge_count"] == 98

root_fragment_path = SETTINGS / "MorpheSettingsV5RootFragment.java"
morphe_fragment_path = SETTINGS / "MorpheSettingsV5MorpheFragment.java"
retired_fragment_path = SETTINGS / "MorpheSettingsV5PatchFeaturesFragment.java"
registry_path = SETTINGS / "MorpheSettingsV5Registry.java"
search_path = SETTINGS / "MorpheSettingsV5Search.java"
v4_entry_path = SETTINGS / "MorpheSettingsV4.java"

for path in (
    root_fragment_path,
    morphe_fragment_path,
    registry_path,
    search_path,
    v4_entry_path,
    PATCH_SOURCE,
):
    assert path.is_file(), path
assert not retired_fragment_path.exists(), retired_fragment_path

root_fragment = root_fragment_path.read_text(encoding="utf-8")
morphe_fragment = morphe_fragment_path.read_text(encoding="utf-8")
registry = registry_path.read_text(encoding="utf-8")
search = search_path.read_text(encoding="utf-8")
v4_entry = v4_entry_path.read_text(encoding="utf-8")
patch_source = PATCH_SOURCE.read_text(encoding="utf-8")

for marker in (
    "MORPHE_BOOST_SETTINGS_V5_ROOT_OVERVIEW_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_COMPLETE_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_ROOT_HIDDEN_WAVE_ISSUE121_V1",
):
    assert marker in root_fragment, marker
assert root_fragment.count("MORPHE_V5_ROOT_CLASSIC_FALLBACK") == 1
assert root_fragment.count("Open classic Boost settings") >= 2
assert "SettingsActivityCompat$HeaderFragment" in root_fragment
assert "MorpheSettingsV5Search.openGlobalSearch(this)" in root_fragment
assert "MorpheSettingsV5Registry.childrenFor(PAGE_ROOT)" in root_fragment
assert "PAGE_MORPHE" not in root_fragment
assert "buildMorpheGroup" not in root_fragment
assert "v5/morphe/patch_features" not in root_fragment

for marker in (
    "MORPHE_BOOST_SETTINGS_V5_MORPHE_CONTROLS_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_MORPHE_CONFIGURABLE_REFERENCE_COUNT_12_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_MORPHE_ROOT_FLATTEN_ISSUE121_V1",
):
    assert marker in morphe_fragment, marker
assert '"morphe_boost_settings_skeleton"' in morphe_fragment
assert "setPreferencesFromResource(resourceId, rootKey)" in morphe_fragment
assert 'activity.setTitle("Morphe")' in morphe_fragment
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in morphe_fragment

assert "static void openGlobalSearch(Fragment host)" in search
assert "private static void openGlobalSearch(Fragment host)" not in search

actual_pages = {}
for match in PAGE_PATTERN.finditer(registry):
    actual_pages[match.group("page_id")] = {
        "renderer": match.group("renderer"),
        "keys": KEY_PATTERN.findall(match.group("keys")),
    }
assert actual_pages["v5/morphe"] == {
    "renderer": "MorpheSettingsV5MorpheFragment",
    "keys": [],
}
assert "v5/morphe/patch_features" not in actual_pages
assert "MorpheSettingsV5PatchFeaturesFragment" not in registry
assert "MORPHE_BOOST_SETTINGS_V5_MORPHE_ROOT_FLATTEN_ISSUE121_V1" in registry

expected_root_destinations = [
    "v5/morphe",
    "v5/appearance",
    "v5/reading_and_interaction",
    "v5/navigation",
    "v5/media",
    "v5/notifications_and_account",
    "v5/data_and_app",
]
assert [entry["page_id"] for entry in wave["root_destinations"]] == (
    expected_root_destinations
)
root_children_match = re.search(
    r'case "v5/root":\s*return new String\[\]\{(?P<children>.*?)\};',
    registry,
    re.S,
)
assert root_children_match
assert KEY_PATTERN.findall(root_children_match.group("children")) == (
    expected_root_destinations
)
assert 'return new String[]{"v5/morphe/patch_features"};' not in registry
assert re.search(r'V5_VISIBLE_BY_DEFAULT\s*=\s*false', registry)

# Normal Settings remains on V4 until the separate activation gate.
assert '"MorpheSettingsV4Fragment"' in v4_entry
assert "MorpheSettingsV5RootFragment" not in v4_entry

reference_keys = wave["configurable_feature_reference_keys"]
assert len(reference_keys) == 12
assert len(set(reference_keys)) == 12
for key in reference_keys:
    assert f'android:key="{key}"' in patch_source, key

with tempfile.TemporaryDirectory(
    prefix="morphe-v5-root-overview-v4-audit-"
) as temp:
    output_dir = Path(temp)
    completed = subprocess.run(
        ["python3", str(AUDIT), "--output-dir", str(output_dir)],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=True,
    )
    report = json.loads(
        (output_dir / "settings-v5-implementation-audit.json").read_text(
            encoding="utf-8"
        )
    )
    assert report["status"] == "INCOMPLETE"
    assert report["current"]["v5_source_file_count"] == 26
    assert report["current"]["implemented_target_page_count"] == 105
    assert report["current"]["implemented_visible_item_count"] == 247
    assert report["current"]["accounted_withheld_item_count"] == 1
    assert report["current"]["visible_state"] == "FALSE"
    assert report["current"]["root_overview_marker_present"] is True
    assert report["current"]["completeness_marker_present"] is True
    assert report["violations"]["root_fallback_count"] == 1
    assert report["violations"]["classic_route_violations"] == []
    assert report["violations"]["missing_page_ids"] == []
    assert report["violations"]["extra_page_ids"] == []
    morphe = next(
        entry for entry in report["root_progress"]
        if entry["root"] == "Morphe"
    )
    assert morphe == {
        "accounted_withheld_items": 0,
        "expected_pages": 1,
        "expected_visible_items": 0,
        "expected_withheld_items": 0,
        "implemented_pages": 1,
        "implemented_visible_items": 0,
        "root": "Morphe",
    }
    for expected in (
        "V5_SOURCE_FILE_COUNT=26",
        "V5_SCREEN_NODE_COVERAGE=105/105",
        "V5_VISIBLE_ITEM_COVERAGE=247/247",
        "V5_WITHHELD_ITEM_ACCOUNTING=1/1",
        "V5_CANONICAL_ACCOUNTING=248/248",
        "V5_RUNTIME_ROOT_COVERAGE=0/7",
        "V5_VISIBLE_STATE=FALSE",
        "V5_PLACEHOLDER_PAGE_COUNT=0",
        "V5_CLASSIC_ROUTE_VIOLATION_COUNT=0",
        "V5_ROOT_CLASSIC_FALLBACK_COUNT=1",
        "AUDIT_STATUS=INCOMPLETE",
    ):
        assert expected in completed.stdout, expected

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("SEMANTIC_REVISION=V4")
print("V5_ROOT_OVERVIEW_SHELL_COVERAGE=1/1")
print("V5_MORPHE_SCREEN_COVERAGE=1/1")
print("V5_MORPHE_WRAPPER_REMOVAL_COUNT=1")
print("V5_ROOT_DESTINATION_COVERAGE=7/7")
print("V5_MORPHE_CONFIGURABLE_REFERENCE_COUNT=12/12")
print("V5_ROOT_CLASSIC_FALLBACK_COUNT=1/1")
print("V5_GLOBAL_SCREEN_COVERAGE=105/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=247/247")
print("V5_WITHHELD_ITEM_ACCOUNTING=1/1")
print("V5_CANONICAL_ACCOUNTING=248/248")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("GLOBAL_SEARCH_LAUNCHER=PASS")
print("ROOT_CLASSIC_FALLBACK_LOCATION=PASS")
print("ACTIVATION_PERFORMED=NO")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_ROOT_OVERVIEW_SEMANTIC_V4_CONTRACT_PASS")
