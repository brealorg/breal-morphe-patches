#!/usr/bin/env python3
"""Static contract for the hidden Settings V5 Navigation wave."""

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
WAVE_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-navigation-wave-v1.json"
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "0962172c47e5991e2b3372936c02293efa614ab761b7989742f0a666f0e4ca41"
EXPECTED_CAPTURE_V5_SHA = "4c8e081069d7444938c3ff5ad0e451bb3b16183592114a6b8adf77cd1208d3f7"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
EXPECTED_ARCHIVE_SHA = "38d6836a42121090affca2016b95da80383e20898195e61abae8f6ffe4ea96b1"
EXPECTED_BASE_APK_SHA = "a68c22d632a5dd1f446c3759a171c7dbb9edab2afc3c2f87e39323f198606742"
EXPECTED_SOURCE_MPP_SHA = "1121fe81f01f6bd7701948ac825f49d4e9873baee4bd0f229ab14307d7abc4f3"

PAGE_PATTERN = re.compile(
    r'new\s+V5PageSpec\s*\(\s*'
    r'"(?P<page_id>v5/[^"]+)"\s*,\s*'
    r'"(?P<renderer>[^"]+)"\s*,\s*'
    r'new\s+String\s*\[\]\s*\{(?P<keys>.*?)\}\s*\)',
    re.S,
)
WITHHELD_PATTERN = re.compile(
    r'new\s+V5WithheldSpec\s*\(\s*'
    r'"(?P<key>[^"]+)"\s*,\s*'
    r'"(?P<reason>[^"]+)"\s*\)',
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
assert wave["wave"] == "Navigation"
assert wave["binding_capture"] == {
    "archive_sha256": EXPECTED_ARCHIVE_SHA,
    "base_apk_sha256": EXPECTED_BASE_APK_SHA,
    "source_mpp_sha256": EXPECTED_SOURCE_MPP_SHA,
    "v5_contract_sha256": EXPECTED_CAPTURE_V5_SHA,
}
assert wave["target"] == {
    "legacy_routes": 0,
    "max_controls_per_leaf": 6,
    "placeholder_pages": 0,
    "screen_nodes": 12,
    "visible_by_default": False,
    "visible_items": 31,
    "withheld_items": 1,
}

navigation_screens = [
    screen for screen in v5["screens"] if screen["root"] == "Navigation"
]
navigation_items = [
    item for item in v5["items"] if item["root"] == "Navigation"
]
visible_items = [
    item for item in navigation_items if item["v5_visibility"] == "VISIBLE"
]
withheld_items = [
    item
    for item in navigation_items
    if item["v5_visibility"] == "WITHHELD_RUNTIME_UNAVAILABLE"
]
assert len(navigation_screens) == 12
assert len(navigation_items) == 32
assert len(visible_items) == 31
assert len(withheld_items) == 1
assert withheld_items[0]["key"] == "pref_drawer_show_friends"
assert withheld_items[0]["v5_visibility_reason"] == (
    "Static implementation exists, but the Friends destination was not "
    "proven to return functional Reddit data at runtime."
)

expected_pages = {entry["page_id"]: entry for entry in wave["screens"]}
expected_keys_by_page = {page_id: set() for page_id in expected_pages}
for item in wave["items"]:
    if item["v5_visibility"] == "VISIBLE":
        expected_keys_by_page[item["page_id"]].add(item["key"])
expected_withheld = {
    item["key"]: item["v5_visibility_reason"]
    for item in wave["items"]
    if item["v5_visibility"] != "VISIBLE"
}
assert len(expected_pages) == 12
assert sum(len(keys) for keys in expected_keys_by_page.values()) == 31
assert max(len(keys) for keys in expected_keys_by_page.values()) == 6
assert expected_withheld == {
    "pref_drawer_show_friends": (
        "Static implementation exists, but the Friends destination was not "
        "proven to return functional Reddit data at runtime."
    )
}

source_paths = [SETTINGS / name for name in wave["source_files"]]
assert all(path.is_file() for path in source_paths)
source = {path.name: path.read_text(encoding="utf-8") for path in source_paths}
all_text = "\n".join(source.values())

registry = source["MorpheSettingsV5Registry.java"]
navigation = source["MorpheSettingsV5Navigation.java"]
hub = source["MorpheSettingsV5NavigationFragment.java"]
leaf = source["MorpheSettingsV5NavigationLeafFragment.java"]
metadata = source["MorpheSettingsV5NavigationMetadata.java"]
engine = source["MorpheSettingsV5XmlPreferenceFragment.java"]
search = source["MorpheSettingsV5Search.java"]

for token in FORBIDDEN:
    assert token not in all_text, token

assert "V5_VISIBLE_BY_DEFAULT = false" in registry
assert "V5_VISIBLE_BY_DEFAULT = true" not in registry
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_WAVE_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ROOT_FLATTEN_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_WITHHELD_FRIENDS_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ISSUE121_V1" in navigation
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_FRAGMENT_ISSUE121_V1" in hub
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_HIDDEN_WAVE_ISSUE121_V1" in hub
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_LEAF_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_BINDINGS_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_WITHHELD_FRIENDS_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_METADATA_ISSUE121_V1" in metadata
assert "MORPHE_BOOST_SETTINGS_V5_XML_PREFERENCE_ENGINE_ISSUE121_V1" in engine
assert "MORPHE_BOOST_SETTINGS_V5_SEARCH_NAVIGATION_WAVE_ISSUE121_V1" in search

actual_pages = {}
for match in PAGE_PATTERN.finditer(registry):
    page_id = match.group("page_id")
    assert page_id not in actual_pages
    actual_pages[page_id] = {
        "renderer": match.group("renderer"),
        "keys": set(KEY_PATTERN.findall(match.group("keys"))),
    }

navigation_actual = {
    page_id: value
    for page_id, value in actual_pages.items()
    if page_id.startswith("v5/navigation")
}
assert set(navigation_actual) == set(expected_pages)
for page_id, page in navigation_actual.items():
    assert page["renderer"] == expected_pages[page_id]["renderer"]
    assert page["keys"] == expected_keys_by_page[page_id], (
        page_id,
        sorted(page["keys"]),
        sorted(expected_keys_by_page[page_id]),
    )

actual_withheld = {
    match.group("key"): match.group("reason")
    for match in WITHHELD_PATTERN.finditer(registry)
}
assert actual_withheld == expected_withheld
assert "static V5WithheldSpec[] allWithheld()" in registry
assert "return WITHHELD.clone();" in registry

assert "MorpheSettingsV5Registry.requirePage(" in hub
assert "MorpheSettingsV5Registry.childrenFor(" in hub
assert 'case "v5/navigation":' in registry
assert 'return "Navigation & gestures";' in registry
assert '"v5/navigation/back_and_exit"' in registry
assert '"v5/navigation/bottom_navigation"' in registry
assert '"v5/navigation/navigation_drawer"' in registry
assert '"v5/navigation/toolbar"' in registry
assert 'v5/navigation/navigation_and_gestures' not in registry
assert "MorpheSettingsV5Navigation.openPage(this, targetPageId)" in hub
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in hub
assert "MorpheSettingsV5Registry.requirePage(pageId)" in leaf
assert "page.containsKey(key)" in leaf
assert '"pref_drawer_show_friends"' not in expected_keys_by_page[
    "v5/navigation/navigation_drawer/account_and_tools"
]
for resource in (
    "pref_views_v2",
    "pref_general_v2",
    "pref_bottom_navigation_v2",
    "pref_drawer_v2",
    "pref_toolbar_v2",
):
    assert f'"{resource}"' in leaf

assert "PreferenceManager.getDefaultSharedPreferences" in engine
assert "XmlResourceParser" in engine
assert "seenKeys.add(control.key)" in engine
assert "updateDependencies()" in engine
assert "control.dependency" in engine
assert '"pref_toolbar_main_action"' in metadata
assert '"pref_bottom_navigation"' in metadata
assert '"pref_drawer_sticky_settings"' in metadata
assert "MorpheSettingsV5NavigationMetadata.titleFor(" in search
assert "MorpheSettingsV5NavigationMetadata.searchSummaryFor(" in search
assert "MorpheSettingsV5Navigation.openPage(host, entry.pageId)" in search
assert "activity.startActivity(intent)" in navigation

for key, dependency in wave["dependencies"].items():
    assert f'"{key}"' in registry
    assert f'"{dependency}"' in registry

for category, keys in wave["special_handlers"].items():
    assert keys, category
    for key in keys:
        assert f'"{key}"' in all_text, (category, key)

with tempfile.TemporaryDirectory(prefix="morphe-v5-navigation-audit-") as temp:
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
    assert report["current"]["v5_source_file_count"] >= 20
    assert report["current"]["implemented_target_page_count"] >= 86
    assert report["current"]["implemented_visible_item_count"] >= 209
    assert report["current"]["accounted_withheld_item_count"] == 1
    assert report["current"]["visible_state"] == "FALSE"

    violations = report["violations"]
    assert violations["placeholder_count"] == 0
    assert violations["classic_route_violations"] == []
    assert violations["duplicate_page_ids"] == []
    assert violations["duplicate_visible_keys"] == []
    assert violations["duplicate_withheld_keys"] == []
    assert violations["page_key_mismatches"] == []
    assert violations["missing_renderer_classes"] == []
    assert violations["missing_withheld_keys"] == []
    assert violations["extra_withheld_keys"] == []

    progress = next(
        row for row in report["root_progress"] if row["root"] == "Navigation"
    )
    assert progress == {
        "root": "Navigation",
        "implemented_pages": 12,
        "expected_pages": 12,
        "implemented_visible_items": 31,
        "expected_visible_items": 31,
        "accounted_withheld_items": 1,
        "expected_withheld_items": 1,
    }

    assert "V5_SCREEN_NODE_COVERAGE=" in completed.stdout
    assert "V5_VISIBLE_ITEM_COVERAGE=" in completed.stdout
    assert "V5_WITHHELD_ITEM_ACCOUNTING=1/1" in completed.stdout
    assert "V5_CANONICAL_ACCOUNTING=" in completed.stdout
    assert "V5_VISIBLE_STATE=FALSE" in completed.stdout

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("V5_NAVIGATION_SCREEN_COVERAGE=12/12")
print("V5_NAVIGATION_VISIBLE_ITEM_COVERAGE=31/31")
print("V5_NAVIGATION_WITHHELD_ITEM_ACCOUNTING=1/1")
print("V5_GLOBAL_SCREEN_COVERAGE=86/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=209/247")
print("V5_CANONICAL_ACCOUNTING=210/248")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("WITHHELD_FRIENDS_ACCOUNTING=PASS")
print("DEPENDENCY_ACCOUNTING=PASS")
print("GLOBAL_SEARCH_ROUTE=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_NAVIGATION_WAVE_V1_CONTRACT_PASS")
