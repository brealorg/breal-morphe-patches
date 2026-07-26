#!/usr/bin/env python3
"""Static contract for the complete hidden Settings V5 Reading wave."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings"
WAVE_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-reading-wave-v1.json"
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "de76707761088cfbe7e72503e3c2152c97a8685128651dce4a27cf70ee19322a"
EXPECTED_CAPTURE_V5_SHA = "4c8e081069d7444938c3ff5ad0e451bb3b16183592114a6b8adf77cd1208d3f7"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
EXPECTED_ARCHIVE_SHA = "3a6fd1ae8faef9ba369e9162926e6b4a054c81a3e194bcd502150d04ed50e594"
EXPECTED_BASE_APK_SHA = "a68c22d632a5dd1f446c3759a171c7dbb9edab2afc3c2f87e39323f198606742"
EXPECTED_SOURCE_MPP_SHA = "a860dc7e6845b23fe557d4c8aeb06c02486b91f49b5d75d8dd852f2e18123a0f"

PAGE_PATTERN = re.compile(
    r'new\s+V5PageSpec\s*\(\s*'
    r'"(?P<page_id>v5/[^"]+)"\s*,\s*'
    r'"(?P<renderer>[^"]+)"\s*,\s*'
    r'new\s+String\s*\[\]\s*\{(?P<keys>.*?)\}\s*\)',
    re.S,
)
KEY_PATTERN = re.compile(r'"([^"]+)"')
FORBIDDEN = (
    "SettingsActivityCompat$",
    "PreferenceFragmentAdvancedCompat",
    "MorpheSettingsV4NativePages",
    "MorpheSettingsV4AppearanceFragment",
    "MorpheSettingsV4PostViewsFragment",
    "MorpheSettingsV4FontsFragment",
    "MorpheSettingsV4DownloadsFragment",
    "MorpheSettingsV4NavigationDrawerHubFragment",
    "MorpheSettingsFragment.class.getName()",
    "MORPHE_V5_PLACEHOLDER_PAGE",
)


def bound_json(path: Path, expected_sha: str) -> dict:
    raw = path.read_bytes()
    actual = hashlib.sha256(raw).hexdigest()
    assert actual == expected_sha, (path, actual, expected_sha)
    return json.loads(raw.decode("utf-8"))


wave = bound_json(WAVE_CONTRACT, EXPECTED_WAVE_SHA)
v5 = bound_json(V5_CONTRACT, EXPECTED_V5_SHA)
assert wave["schema"] == 1
assert wave["issue"] == 121
assert wave["wave"] == "Reading & interaction"
assert wave["binding_capture"] == {
    "archive_sha256": EXPECTED_ARCHIVE_SHA,
    "base_apk_sha256": EXPECTED_BASE_APK_SHA,
    "source_mpp_sha256": EXPECTED_SOURCE_MPP_SHA,
    "v5_contract_sha256": EXPECTED_CAPTURE_V5_SHA,
}
assert wave["target"] == {
    "legacy_routes": 0,
    "max_controls_per_leaf": 12,
    "placeholder_pages": 0,
    "screen_nodes": 26,
    "visible_by_default": False,
    "visible_items": 87,
    "withheld_items": 0,
}

reading_screens = [s for s in v5["screens"] if s["root"] == "Reading & interaction"]
reading_items = [i for i in v5["items"] if i["root"] == "Reading & interaction"]
assert len(reading_screens) == 26
assert len(reading_items) == 87
assert all(item["v5_visibility"] == "VISIBLE" for item in reading_items)

expected_pages = {entry["page_id"]: entry for entry in wave["screens"]}
expected_keys_by_page = {page_id: set() for page_id in expected_pages}
for item in wave["items"]:
    expected_keys_by_page[item["page_id"]].add(item["key"])
assert len(expected_pages) == 26
assert sum(len(keys) for keys in expected_keys_by_page.values()) == 87
assert max(len(keys) for keys in expected_keys_by_page.values()) == 12

source_paths = [SETTINGS / name for name in wave["source_files"]]
assert all(path.is_file() for path in source_paths)
source = {path.name: path.read_text(encoding="utf-8") for path in source_paths}
all_text = "\n".join(source.values())
registry = source["MorpheSettingsV5Registry.java"]
navigation = source["MorpheSettingsV5Navigation.java"]
hub = source["MorpheSettingsV5ReadingFragment.java"]
leaf = source["MorpheSettingsV5ReadingLeafFragment.java"]
metadata = source["MorpheSettingsV5ReadingMetadata.java"]
engine = source["MorpheSettingsV5XmlPreferenceFragment.java"]
search = source["MorpheSettingsV5Search.java"]

for token in FORBIDDEN:
    assert token not in all_text, token

assert "V5_VISIBLE_BY_DEFAULT = false" in registry
assert "V5_VISIBLE_BY_DEFAULT = true" not in registry
assert "MORPHE_BOOST_SETTINGS_V5_READING_WAVE_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ISSUE121_V1" in navigation
assert "MORPHE_BOOST_SETTINGS_V5_READING_FRAGMENT_ISSUE121_V1" in hub
assert "MORPHE_BOOST_SETTINGS_V5_READING_LEAF_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_READING_METADATA_ISSUE121_V1" in metadata
assert "MORPHE_BOOST_SETTINGS_V5_XML_PREFERENCE_ENGINE_ISSUE121_V1" in engine
assert "MORPHE_BOOST_SETTINGS_V5_SEARCH_MULTI_WAVE_ISSUE121_V1" in search

actual_pages = {}
for match in PAGE_PATTERN.finditer(registry):
    page_id = match.group("page_id")
    assert page_id not in actual_pages
    actual_pages[page_id] = {
        "renderer": match.group("renderer"),
        "keys": set(KEY_PATTERN.findall(match.group("keys"))),
    }
reading_actual = {
    pid: value for pid, value in actual_pages.items()
    if pid.startswith("v5/reading_and_interaction")
}
assert set(reading_actual) == set(expected_pages)
for page_id, page in reading_actual.items():
    expected_renderer = (
        "MorpheSettingsV5ReadingLeafFragment"
        if expected_pages[page_id]["role"] == "leaf_section"
        else "MorpheSettingsV5ReadingFragment"
    )
    assert page["renderer"] == expected_renderer
    assert page["keys"] == expected_keys_by_page[page_id], (
        page_id,
        sorted(page["keys"]),
        sorted(expected_keys_by_page[page_id]),
    )

assert "MorpheSettingsV5Registry.requirePage(" in hub
assert "MorpheSettingsV5Registry.childrenFor(" in hub
assert "MorpheSettingsV5Navigation.openPage(this, targetPageId)" in hub
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in hub
assert "MorpheSettingsV5Registry.requirePage(pageId)" in leaf
assert "page.containsKey(key)" in leaf
assert '"pref_comments_v2"' in leaf
assert '"morphe_boost_settings_skeleton"' in leaf
assert "PreferenceManager.getDefaultSharedPreferences" in engine
assert "XmlResourceParser" in engine
assert "seenKeys.add(control.key)" in engine
assert "MorpheSettingsV14Ui.standardListRow" in engine
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in engine
assert "showFilterEditor(control)" in engine
assert "showColorPatternEditor()" in engine
assert "confirmDelete(control)" in engine
assert "showSynccitEditor()" in engine
assert "showSavedSortsEditor()" in engine
assert "showSavedSearchesEditor()" in engine
assert "showFieldSearchHelp()" in engine
assert 'invokeActivityHelper("t0"' in engine
assert 'invokeActivityHelper("P"' in engine
assert 'invokeActivityHelper("n0"' in engine
assert "MorpheSettingsV5ReadingMetadata.titleFor(" in search
assert "MorpheSettingsV5Navigation.openPage(host, entry.pageId)" in search
assert "activity.startActivity(intent)" in navigation

for key, dependency in wave["dependencies"].items():
    assert f'"{key}"' in registry
    assert f'"{dependency}"' in registry

for category, keys in wave["special_handlers"].items():
    assert keys, category
    for key in keys:
        assert f'"{key}"' in all_text, (category, key)

with tempfile.TemporaryDirectory(prefix="morphe-v5-reading-audit-") as temp:
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
    assert report["current"]["v5_source_file_count"] >= 9
    assert report["current"]["implemented_target_page_count"] >= 48
    assert report["current"]["implemented_visible_item_count"] >= 126
    assert report["current"]["visible_state"] == "FALSE"
    violations = report["violations"]
    assert violations["placeholder_count"] == 0
    assert violations["classic_route_violations"] == []
    assert violations["duplicate_page_ids"] == []
    assert violations["duplicate_visible_keys"] == []
    assert violations["page_key_mismatches"] == []
    assert violations["missing_renderer_classes"] == []
    reading = next(
        row for row in report["root_progress"]
        if row["root"] == "Reading & interaction"
    )
    assert reading == {
        "root": "Reading & interaction",
        "implemented_pages": 26,
        "expected_pages": 26,
        "implemented_visible_items": 87,
        "expected_visible_items": 87,
        "accounted_withheld_items": 0,
        "expected_withheld_items": 0,
    }
    assert "V5_SCREEN_NODE_COVERAGE=" in completed.stdout
    assert "V5_VISIBLE_ITEM_COVERAGE=" in completed.stdout
    assert "V5_CANONICAL_ACCOUNTING=" in completed.stdout
    assert "V5_VISIBLE_STATE=FALSE" in completed.stdout

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("V5_READING_SCREEN_COVERAGE=26/26")
print("V5_READING_VISIBLE_ITEM_COVERAGE=87/87")
print("V5_GLOBAL_SCREEN_COVERAGE=48/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=126/247")
print("V5_CANONICAL_ACCOUNTING=126/248")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("SPECIAL_HANDLER_ACCOUNTING=PASS")
print("DEPENDENCY_ACCOUNTING=PASS")
print("GLOBAL_SEARCH_ROUTE=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_READING_WAVE_V1_CONTRACT_PASS")
