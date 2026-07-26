#!/usr/bin/env python3
"""Static contract for the hidden Settings V5 Data & app wave."""

from __future__ import annotations

import collections
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
WAVE_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-data-app-wave-v1.json"
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "045d0982e7b81d320049ff3a067c3cebda1bd1825047f6253259976ba56d095a"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
EXPECTED_BINDING_ARCHIVE_SHA = "3b5f170322d956db8c0898943d0532aab871f7546cf041c9611ea749b42f5a3d"
EXPECTED_SPECIAL_ARCHIVE_SHA = "d2fe10c466530ef6168af9108728f8d3fcf4cdbe81d68f855dd730cb7bac41d7"
EXPECTED_BASE_APK_SHA = "a68c22d632a5dd1f446c3759a171c7dbb9edab2afc3c2f87e39323f198606742"
EXPECTED_SOURCE_MPP_SHA = "600904a3d9639e198adcf9bab6d17ab6cbb3fd07e3a53694fc756b85d1be9934"

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
assert wave["root"] == "Data & app"
assert wave["binding_capture"] == {
    "archive_sha256": EXPECTED_BINDING_ARCHIVE_SHA,
    "base_apk_sha256": EXPECTED_BASE_APK_SHA,
    "source_mpp_sha256": EXPECTED_SOURCE_MPP_SHA,
    "special_handler_archive_sha256": EXPECTED_SPECIAL_ARCHIVE_SHA,
    "v5_contract_sha256": EXPECTED_V5_SHA,
}
assert wave["targets"] == {
    "action_count": 15,
    "dependency_count": 0,
    "screen_count": 18,
    "special_preference_count": 1,
    "stored_setting_count": 22,
    "visible_item_count": 38,
    "zero_control_route_count": 1,
}

screens = [screen for screen in v5["screens"] if screen["root"] == "Data & app"]
items = [item for item in v5["items"] if item["root"] == "Data & app"]
assert len(screens) == 18
assert len(items) == 38
assert all(item["v5_visibility"] == "VISIBLE" for item in items)
assert collections.Counter(item["logical_kind"] for item in items) == {
    "stored_setting": 22,
    "action": 15,
    "special_preference": 1,
}
assert collections.Counter(item["page"] for item in items) == {
    "About & support": 14,
    "App behavior & compatibility": 14,
    "Settings experience": 1,
    "Storage & bandwidth": 9,
}
assert not [item for item in items if item.get("dependency")]

expected_pages = {entry["page_id"]: entry for entry in wave["screens"]}
expected_keys_by_page = {page_id: set() for page_id in expected_pages}
for item in wave["items"]:
    expected_keys_by_page[item["page_id"]].add(item["key"])
assert len(expected_pages) == 18
assert sum(len(keys) for keys in expected_keys_by_page.values()) == 38
assert expected_pages["v5/data_and_app/backup_and_restore"] == {
    "child_count": 0,
    "control_count": 0,
    "page_id": "v5/data_and_app/backup_and_restore",
    "path": "Data & app / Backup & restore",
    "renderer": "MorpheSettingsV5BackupRestoreFragment",
    "role": "task_page",
    "title": "Backup & restore",
}

source_files = (
    "MorpheSettingsV5Registry.java",
    "MorpheSettingsV5Search.java",
    "MorpheSettingsV5XmlPreferenceFragment.java",
    "MorpheSettingsV5DataAppFragment.java",
    "MorpheSettingsV5DataAppLeafFragment.java",
    "MorpheSettingsV5DataAppMetadata.java",
    "MorpheSettingsV5BackupRestoreFragment.java",
)
source_paths = [SETTINGS / name for name in source_files]
assert all(path.is_file() for path in source_paths)
source = {path.name: path.read_text(encoding="utf-8") for path in source_paths}
all_text = "\n".join(source.values())

registry = source["MorpheSettingsV5Registry.java"]
search = source["MorpheSettingsV5Search.java"]
engine = source["MorpheSettingsV5XmlPreferenceFragment.java"]
hub = source["MorpheSettingsV5DataAppFragment.java"]
leaf = source["MorpheSettingsV5DataAppLeafFragment.java"]
metadata = source["MorpheSettingsV5DataAppMetadata.java"]
backup = source["MorpheSettingsV5BackupRestoreFragment.java"]

for token in FORBIDDEN:
    assert token not in all_text, token

markers = (
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_WAVE_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_SEARCH_DATA_APP_WAVE_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_SPECIAL_HANDLERS_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_FRAGMENT_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_HIDDEN_WAVE_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_LEAF_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_BINDINGS_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_HANDLER_BINDINGS_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_DATA_APP_METADATA_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_BACKUP_RESTORE_ROUTE_ISSUE121_V1",
    "MORPHE_BOOST_SETTINGS_V5_BACKUP_RESTORE_ZERO_CONTROLS_ISSUE121_V1",
)
for marker in markers:
    assert marker in all_text, marker

actual_pages = {}
for match in PAGE_PATTERN.finditer(registry):
    page_id = match.group("page_id")
    assert page_id not in actual_pages
    actual_pages[page_id] = {
        "renderer": match.group("renderer"),
        "keys": set(KEY_PATTERN.findall(match.group("keys"))),
    }
data_actual = {
    page_id: value
    for page_id, value in actual_pages.items()
    if page_id.startswith("v5/data_and_app")
}
assert set(data_actual) == set(expected_pages)
for page_id, page in data_actual.items():
    assert page["renderer"] == expected_pages[page_id]["renderer"]
    assert page["keys"] == expected_keys_by_page[page_id], (
        page_id,
        sorted(page["keys"]),
        sorted(expected_keys_by_page[page_id]),
    )

assert 'case "v5/data_and_app":' in registry
for child in (
    "v5/data_and_app/about_and_support",
    "v5/data_and_app/app_behavior_and_compatibility",
    "v5/data_and_app/backup_and_restore",
    "v5/data_and_app/settings_experience",
    "v5/data_and_app/storage_and_bandwidth",
):
    assert f'"{child}"' in registry
assert "MorpheSettingsV5Registry.requirePage(pageId)" in hub
assert "MorpheSettingsV5Registry.childrenFor(pageId)" in hub
assert "MorpheSettingsV5Navigation.openPage(this, targetPageId)" in hub
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in hub

assert "MorpheSettingsV5Registry.requirePage(pageId)" in leaf
assert "page.containsKey(key)" in leaf
assert "includeHiddenControl" in leaf
for resource in (
    "pref_headers_v2",
    "pref_about_v2",
    "pref_misc_v2",
    "pref_data_v2",
    "morphe_boost_settings_skeleton",
):
    assert f'"{resource}"' in leaf

for item in wave["items"]:
    assert f'case "{item["key"]}":' in metadata, item["key"]
assert "MorpheSettingsV5DataAppMetadata.titleFor(" in search
assert "MorpheSettingsV5DataAppMetadata.searchSummaryFor(" in search
assert "MorpheSettingsV5Navigation.openPage(host, entry.pageId)" in search

assert re.search(
    r'"remove_ads"\.equals\(key\).*?invokeActivityHelper\("y",\s*Context\.class,\s*requireContext\(\)\)',
    engine,
    re.S,
)
assert re.search(
    r'"buy_pro"\.equals\(key\).*?invokeActivityHelper\("x0",\s*Context\.class,\s*requireContext\(\)\)',
    engine,
    re.S,
)
assert re.search(
    r'"pref_download_folders"\.equals\(key\).*?invokeActivityHelper\("S",\s*Context\.class,\s*requireContext\(\)\)',
    engine,
    re.S,
)
assert '"qb.a"' in engine and '"a"' in engine and "clearMediaCache()" in engine
assert '"qb.b"' in engine and '"d"' in engine and '"pref_cache_max_size"' in engine
assert '"pref_gdpr_revoke".equals(key)' in engine
assert "confirmGdprRevocation()" in engine
assert "RevokeGDPRConstentPreferenceDialogFragmentCompat" in engine
assert 'getDeclaredMethod(\n                    "checkConsent"' in engine
assert "method.setAccessible(true)" in engine
assert "dataVideoQualityControlEnabled" in engine
for key in (
    "pref_video_quality",
    "pref_video_quality_min",
    "pref_video_quality_max",
    "pref_reduce_mobile",
    "pref_reduce_wifi",
):
    assert f'"{key}"' in engine
assert "Intent.ACTION_VIEW" in engine and 'Uri.parse("mailto:" + address' in engine

assert "com.rubenmayayo.reddit.BackupActivity" in backup
assert "startActivity(intent)" in backup
assert "page.keys.length != 0" in backup
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in backup

for category, values in wave["special_handlers"].items():
    assert values, category
    for value in values:
        assert value in all_text, (category, value)

with tempfile.TemporaryDirectory(prefix="morphe-v5-data-app-audit-") as temp:
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
    assert report["current"]["v5_source_file_count"] >= 24
    assert report["current"]["implemented_target_page_count"] >= 104
    assert report["current"]["implemented_visible_item_count"] == 247
    assert report["current"]["accounted_withheld_item_count"] == 1
    assert report["current"]["visible_state"] == "FALSE"
    root = next(
        entry for entry in report["root_progress"]
        if entry["root"] == "Data & app"
    )
    assert root == {
        "accounted_withheld_items": 0,
        "expected_pages": 18,
        "expected_visible_items": 38,
        "expected_withheld_items": 0,
        "implemented_pages": 18,
        "implemented_visible_items": 38,
        "root": "Data & app",
    }
    for expected in (
        "V5_SOURCE_FILE_COUNT=",
        "V5_SCREEN_NODE_COVERAGE=",
        "V5_VISIBLE_ITEM_COVERAGE=247/247",
        "V5_WITHHELD_ITEM_ACCOUNTING=1/1",
        "V5_CANONICAL_ACCOUNTING=248/248",
        "V5_VISIBLE_STATE=FALSE",
        "V5_PLACEHOLDER_PAGE_COUNT=0",
        "V5_CLASSIC_ROUTE_VIOLATION_COUNT=0",
        "AUDIT_STATUS=INCOMPLETE",
    ):
        assert expected in completed.stdout, expected

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("V5_DATA_APP_SCREEN_COVERAGE=18/18")
print("V5_DATA_APP_VISIBLE_ITEM_COVERAGE=38/38")
print("V5_GLOBAL_SCREEN_COVERAGE=104/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=247/247")
print("V5_WITHHELD_ITEM_ACCOUNTING=1/1")
print("V5_CANONICAL_ACCOUNTING=248/248")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("BACKUP_RESTORE_ZERO_CONTROL_ROUTE=PASS")
print("PURCHASE_ADS_HANDLER_ACCOUNTING=PASS")
print("GDPR_HANDLER_ACCOUNTING=PASS")
print("STORAGE_HANDLER_ACCOUNTING=PASS")
print("VIDEO_QUALITY_STATE_ACCOUNTING=PASS")
print("GLOBAL_SEARCH_ROUTE=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_DATA_APP_WAVE_V1_CONTRACT_PASS")
