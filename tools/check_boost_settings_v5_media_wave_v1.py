#!/usr/bin/env python3
"""Static contract for the complete hidden Settings V5 Media wave."""

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
WAVE_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-media-wave-v1.json"
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "48b2bda96c2b2b9917f326c89848dee3c1448f3a5fc02efe8b1549155448a3b9"
EXPECTED_CAPTURE_V5_SHA = "4c8e081069d7444938c3ff5ad0e451bb3b16183592114a6b8adf77cd1208d3f7"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
EXPECTED_ARCHIVE_SHA = "12e3a8ac2604dec833bbb3d223316e1afc8eddd1aebd40e9518bd6f42c0618ca"
EXPECTED_BASE_APK_SHA = "a68c22d632a5dd1f446c3759a171c7dbb9edab2afc3c2f87e39323f198606742"
EXPECTED_SOURCE_MPP_SHA = "765bfd3c15487e5021f9e7979cd1aabee600363217beda9a381ff29545fb6280"

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
assert wave["wave"] == "Media"
assert wave["binding_capture"] == {
    "archive_sha256": EXPECTED_ARCHIVE_SHA,
    "base_apk_sha256": EXPECTED_BASE_APK_SHA,
    "source_mpp_sha256": EXPECTED_SOURCE_MPP_SHA,
    "v5_contract_sha256": EXPECTED_CAPTURE_V5_SHA,
}
assert wave["target"] == {
    "legacy_routes": 0,
    "max_controls_per_leaf": 8,
    "placeholder_pages": 0,
    "screen_nodes": 18,
    "visible_by_default": False,
    "visible_items": 36,
    "withheld_items": 0,
}

media_screens = [screen for screen in v5["screens"] if screen["root"] == "Media"]
media_items = [item for item in v5["items"] if item["root"] == "Media"]
assert len(media_screens) == 18
assert len(media_items) == 36
assert all(item["v5_visibility"] == "VISIBLE" for item in media_items)

expected_pages = {entry["page_id"]: entry for entry in wave["screens"]}
expected_keys_by_page = {page_id: set() for page_id in expected_pages}
for item in wave["items"]:
    expected_keys_by_page[item["page_id"]].add(item["key"])
assert len(expected_pages) == 18
assert sum(len(keys) for keys in expected_keys_by_page.values()) == 36
assert max(len(keys) for keys in expected_keys_by_page.values()) == 8

source_paths = [SETTINGS / name for name in wave["source_files"]]
assert all(path.is_file() for path in source_paths)
source = {path.name: path.read_text(encoding="utf-8") for path in source_paths}
all_text = "\n".join(source.values())

registry = source["MorpheSettingsV5Registry.java"]
navigation = source["MorpheSettingsV5Navigation.java"]
hub = source["MorpheSettingsV5MediaFragment.java"]
leaf = source["MorpheSettingsV5MediaLeafFragment.java"]
metadata = source["MorpheSettingsV5MediaMetadata.java"]
downloads = source["MorpheSettingsV5MediaDownloadFoldersFragment.java"]
engine = source["MorpheSettingsV5XmlPreferenceFragment.java"]
search = source["MorpheSettingsV5Search.java"]

for token in FORBIDDEN:
    assert token not in all_text, token

assert "V5_VISIBLE_BY_DEFAULT = false" in registry
assert "V5_VISIBLE_BY_DEFAULT = true" not in registry
assert "MORPHE_BOOST_SETTINGS_V5_MEDIA_WAVE_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ISSUE121_V1" in navigation
assert "MORPHE_BOOST_SETTINGS_V5_MEDIA_FRAGMENT_ISSUE121_V1" in hub
assert "MORPHE_BOOST_SETTINGS_V5_MEDIA_LEAF_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_MEDIA_METADATA_ISSUE121_V1" in metadata
assert "MORPHE_BOOST_SETTINGS_V5_MEDIA_DOWNLOAD_FOLDERS_ISSUE121_V1" in downloads
assert "MORPHE_BOOST_SETTINGS_V5_MEDIA_DOWNLOAD_BINDINGS_ISSUE121_V1" in downloads
assert "MORPHE_BOOST_SETTINGS_V5_XML_PREFERENCE_ENGINE_ISSUE121_V1" in engine
assert "MORPHE_BOOST_SETTINGS_V5_SEARCH_MEDIA_WAVE_ISSUE121_V1" in search

actual_pages = {}
for match in PAGE_PATTERN.finditer(registry):
    page_id = match.group("page_id")
    assert page_id not in actual_pages
    actual_pages[page_id] = {
        "renderer": match.group("renderer"),
        "keys": set(KEY_PATTERN.findall(match.group("keys"))),
    }

media_actual = {
    page_id: value
    for page_id, value in actual_pages.items()
    if page_id.startswith("v5/media")
}
assert set(media_actual) == set(expected_pages)
for page_id, page in media_actual.items():
    assert page["renderer"] == expected_pages[page_id]["renderer"]
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
for resource in (
    "pref_views_v2",
    "pref_media_v2",
    "pref_general_v2",
    "pref_links_v2",
    "pref_data_v2",
    "morphe_boost_settings_skeleton",
):
    assert f'"{resource}"' in leaf

assert "PreferenceManager.getDefaultSharedPreferences" in engine
assert "XmlResourceParser" in engine
assert "seenKeys.add(control.key)" in engine
assert "showFilterEditor(control)" in engine
assert '"FilterPreference".equals(control.tag)' in engine
assert '"PreviewSizePreference".equals(control.tag)' in engine
assert '"PreviewAlignmentPreference".equals(control.tag)' in engine
assert '"MediaTapActionPreference".equals(control.tag)' in engine

assert "Intent.ACTION_OPEN_DOCUMENT_TREE" in downloads
assert "takePersistableUriPermission" in downloads
assert '"pref_download_folder_default"' in downloads
assert '"pref_download_folder_img"' in downloads
assert '"pref_download_folder_mp4"' in downloads
assert '"pref_download_folder_gif"' in downloads
assert '"pref_download_folder_per_subreddit"' in downloads
for method in ('"D6"', '"d0"', '"O"', '"E6"', '"l2"'):
    assert method in downloads
assert '"sb.a"' in downloads
assert '"f"' in downloads
assert '"g"' in downloads
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in downloads
assert "MorpheSettingsV5Search.handleMenuItem(this, item)" in downloads

assert "MorpheSettingsV5MediaMetadata.titleFor(" in search
assert "MorpheSettingsV5MediaMetadata.searchSummaryFor(" in search
assert "MorpheSettingsV5Navigation.openPage(host, entry.pageId)" in search
assert "activity.startActivity(intent)" in navigation

for key, dependency in wave["dependencies"].items():
    assert f'"{key}"' in registry
    assert f'"{dependency}"' in registry

for category, keys in wave["special_handlers"].items():
    assert keys, category
    for key in keys:
        assert f'"{key}"' in all_text, (category, key)

with tempfile.TemporaryDirectory(prefix="morphe-v5-media-audit-") as temp:
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
    assert report["current"]["v5_source_file_count"] >= 13
    assert report["current"]["implemented_target_page_count"] >= 66
    assert report["current"]["implemented_visible_item_count"] >= 162
    assert report["current"]["visible_state"] == "FALSE"

    violations = report["violations"]
    assert violations["placeholder_count"] == 0
    assert violations["classic_route_violations"] == []
    assert violations["duplicate_page_ids"] == []
    assert violations["duplicate_visible_keys"] == []
    assert violations["page_key_mismatches"] == []
    assert violations["missing_renderer_classes"] == []

    media = next(
        row for row in report["root_progress"]
        if row["root"] == "Media"
    )
    assert media == {
        "root": "Media",
        "implemented_pages": 18,
        "expected_pages": 18,
        "implemented_visible_items": 36,
        "expected_visible_items": 36,
        "accounted_withheld_items": 0,
        "expected_withheld_items": 0,
    }

    assert "V5_SCREEN_NODE_COVERAGE=" in completed.stdout
    assert "V5_VISIBLE_ITEM_COVERAGE=" in completed.stdout
    assert "V5_CANONICAL_ACCOUNTING=" in completed.stdout
    assert "V5_VISIBLE_STATE=FALSE" in completed.stdout

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("V5_MEDIA_SCREEN_COVERAGE=18/18")
print("V5_MEDIA_VISIBLE_ITEM_COVERAGE=36/36")
print("V5_GLOBAL_SCREEN_COVERAGE=66/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=162/247")
print("V5_CANONICAL_ACCOUNTING=162/248")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("SPECIAL_HANDLER_ACCOUNTING=PASS")
print("DEPENDENCY_ACCOUNTING=PASS")
print("GLOBAL_SEARCH_ROUTE=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_MEDIA_WAVE_V1_CONTRACT_PASS")
