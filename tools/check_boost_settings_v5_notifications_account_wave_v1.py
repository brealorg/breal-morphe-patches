#!/usr/bin/env python3
"""Static contract for hidden Settings V5 Notifications & account wave."""

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
WAVE_CONTRACT = (
    ROOT
    / "tools/contracts/boost-settings-v5-notifications-account-wave-v1.json"
)
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "c0ad548fd5ec6bc1feb484c3e8562c98c82e39afae0fcc8ae2241580a85846d6"
EXPECTED_CAPTURE_V5_SHA = "4c8e081069d7444938c3ff5ad0e451bb3b16183592114a6b8adf77cd1208d3f7"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
EXPECTED_ARCHIVE_SHA = "38fe3cb8f3a276cbc15dcc4aa3d564c357c09060765a778ff3c773cf251a0c3b"
EXPECTED_BASE_APK_SHA = "a68c22d632a5dd1f446c3759a171c7dbb9edab2afc3c2f87e39323f198606742"
EXPECTED_SOURCE_MPP_SHA = "f5912189364f1c672c25a18fdda04fda99fdcd61edb6aebbadf2cedb0e086158"

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
    "PreferenceFragmentAccountCompat",
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
assert wave["wave"] == "Notifications & account"
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
    "screen_nodes": 8,
    "visible_by_default": False,
    "visible_items": 16,
    "withheld_items": 0,
}

root_name = "Notifications & account"
target_screens = [screen for screen in v5["screens"] if screen["root"] == root_name]
target_items = [item for item in v5["items"] if item["root"] == root_name]
assert len(target_screens) == 8
assert len(target_items) == 16
assert all(item["v5_visibility"] == "VISIBLE" for item in target_items)

expected_pages = {entry["page_id"]: entry for entry in wave["screens"]}
expected_keys_by_page = {page_id: set() for page_id in expected_pages}
for item in wave["items"]:
    expected_keys_by_page[item["page_id"]].add(item["key"])
assert len(expected_pages) == 8
assert sum(len(keys) for keys in expected_keys_by_page.values()) == 16
assert max(len(keys) for keys in expected_keys_by_page.values()) == 8

account_page = expected_pages["v5/notifications_and_account/reddit_account"]
assert account_page == {
    "child_count": 0,
    "control_count": 0,
    "page_id": "v5/notifications_and_account/reddit_account",
    "renderer": "MorpheSettingsV5RedditAccountFragment",
    "role": "task_page",
    "title": "Reddit account",
}

source_paths = [SETTINGS / name for name in wave["source_files"]]
assert all(path.is_file() for path in source_paths)
source = {path.name: path.read_text(encoding="utf-8") for path in source_paths}
all_text = "\n".join(source.values())

registry = source["MorpheSettingsV5Registry.java"]
navigation = source["MorpheSettingsV5Navigation.java"]
hub = source["MorpheSettingsV5NotificationsAccountFragment.java"]
leaf = source["MorpheSettingsV5NotificationsAccountLeafFragment.java"]
metadata = source["MorpheSettingsV5NotificationsAccountMetadata.java"]
account = source["MorpheSettingsV5RedditAccountFragment.java"]
engine = source["MorpheSettingsV5XmlPreferenceFragment.java"]
search = source["MorpheSettingsV5Search.java"]

for token in FORBIDDEN:
    assert token not in all_text, token

assert "V5_VISIBLE_BY_DEFAULT = false" in registry
assert "V5_VISIBLE_BY_DEFAULT = true" not in registry
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_WAVE_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ISSUE121_V1" in navigation
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_FRAGMENT_ISSUE121_V1" in hub
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_HIDDEN_WAVE_ISSUE121_V1" in hub
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_LEAF_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_BINDINGS_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_HIDDEN_HISTORY_ISSUE121_V1" in leaf
assert "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_METADATA_ISSUE121_V1" in metadata
assert "MORPHE_BOOST_SETTINGS_V5_REDDIT_ACCOUNT_ISSUE121_V1" in account
assert "MORPHE_BOOST_SETTINGS_V5_REDDIT_ACCOUNT_ZERO_CANONICAL_CONTROLS_ISSUE121_V1" in account
assert "MORPHE_BOOST_SETTINGS_V5_HIDDEN_CANONICAL_OVERRIDE_ISSUE121_V1" in engine
assert "MORPHE_BOOST_SETTINGS_V5_SEARCH_NOTIFICATIONS_ACCOUNT_WAVE_ISSUE121_V1" in search

actual_pages: dict[str, dict[str, object]] = {}
for match in PAGE_PATTERN.finditer(registry):
    page_id = match.group("page_id")
    assert page_id not in actual_pages
    actual_pages[page_id] = {
        "renderer": match.group("renderer"),
        "keys": set(KEY_PATTERN.findall(match.group("keys"))),
    }

actual_wave_pages = {
    page_id: value
    for page_id, value in actual_pages.items()
    if page_id.startswith("v5/notifications_and_account")
}
assert set(actual_wave_pages) == set(expected_pages)
for page_id, page in actual_wave_pages.items():
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
assert "MorpheSettingsV5Search.handleMenuItem(this, item)" in hub

assert "MorpheSettingsV5Registry.requirePage(pageId)" in leaf
assert "page.containsKey(key)" in leaf
for resource in (
    "pref_privacy_v2",
    "pref_messages_v2",
    "morphe_boost_settings_skeleton",
):
    assert f'"{resource}"' in leaf

hidden_keys = set(wave["special_handlers"]["hidden_canonical_controls"])
assert hidden_keys == {"pref_search_history_save", "pref_searches_delete"}
assert "protected boolean includeHiddenControl(" in leaf
for key in hidden_keys:
    assert f'"{key}"' in leaf
assert '"pref_privacy_v2".equals(resourceName)' in leaf
assert "page.containsKey(key)" in leaf
assert "protected boolean includeHiddenControl(" in engine
assert "String rawKey = attributeText(resources, parser, \"key\")" in engine
assert "boolean forcedVisible = !visible" in engine
assert "&& includeHiddenControl(resourceName, rawKey)" in engine
assert "if (!visible && !forcedVisible)" in engine

for key in wave["special_handlers"]["history_delete_actions"]:
    assert f'"{key}"' in engine
assert "confirmDelete(control)" in engine
assert "deleteForKey(control.key)" in engine
assert 'factoryCall("he.z", "e", "c")' in engine
assert 'factoryCall("he.d0", "d", "c")' in engine
assert 'factoryCall("ld.c", "a", "clear")' in engine

assert '"pref_notifications_configure".equals(key)' in engine
assert "openNotificationSettings()" in engine
assert "Settings.ACTION_APP_NOTIFICATION_SETTINGS" in engine
assert "Settings.EXTRA_APP_PACKAGE" in engine

for key in wave["special_handlers"]["notification_side_effects"]:
    assert f'"{key}"' in engine
assert 'invokeStatic(\n                    "pe.b"' in engine
assert "reconcileNotificationAccess()" in engine
assert "Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS" in engine

assert "PreferenceManager.getDefaultSharedPreferences" in engine
assert "XmlResourceParser" in engine
assert "seenKeys.add(control.key)" in engine

assert "MorpheSettingsV5NotificationsAccountMetadata.titleFor(" in search
assert "MorpheSettingsV5NotificationsAccountMetadata.searchSummaryFor(" in search
assert "MorpheSettingsV5Navigation.openPage(host, entry.pageId)" in search
assert "activity.startActivity(intent)" in navigation

assert '"v5/notifications_and_account/reddit_account"' in account
assert "page.keys.length != 0" in account
assert "!page.isLeaf()" in account
assert "Boost has no app-local controls in this category" in account
assert "Reddit-hosted preferences" in account
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in account
assert "MorpheSettingsV5Search.handleMenuItem(this, item)" in account

for item in wave["items"]:
    key = item["key"]
    assert f'case "{key}":' in metadata, key
    assert f'"{key}"' in registry, key

for key, dependency in wave["dependencies"].items():
    assert f'"{key}"' in registry
    assert f'"{dependency}"' in registry

with tempfile.TemporaryDirectory(prefix="morphe-v5-notifications-account-audit-") as temp:
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
    assert report["current"]["v5_source_file_count"] >= 17
    assert report["current"]["implemented_target_page_count"] >= 74
    assert report["current"]["implemented_visible_item_count"] >= 178
    assert report["current"]["visible_state"] == "FALSE"

    violations = report["violations"]
    assert violations["placeholder_count"] == 0
    assert violations["classic_route_violations"] == []
    assert violations["duplicate_page_ids"] == []
    assert violations["duplicate_visible_keys"] == []
    assert violations["page_key_mismatches"] == []
    assert violations["missing_renderer_classes"] == []

    progress = next(
        row for row in report["root_progress"]
        if row["root"] == root_name
    )
    assert progress == {
        "root": root_name,
        "implemented_pages": 8,
        "expected_pages": 8,
        "implemented_visible_items": 16,
        "expected_visible_items": 16,
        "accounted_withheld_items": 0,
        "expected_withheld_items": 0,
    }

    assert "V5_SCREEN_NODE_COVERAGE=" in completed.stdout
    assert "V5_VISIBLE_ITEM_COVERAGE=" in completed.stdout
    assert "V5_CANONICAL_ACCOUNTING=" in completed.stdout
    assert "V5_VISIBLE_STATE=FALSE" in completed.stdout

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("V5_NOTIFICATIONS_ACCOUNT_SCREEN_COVERAGE=8/8")
print("V5_NOTIFICATIONS_ACCOUNT_VISIBLE_ITEM_COVERAGE=16/16")
print("V5_GLOBAL_SCREEN_COVERAGE=74/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=178/247")
print("V5_CANONICAL_ACCOUNTING=178/248")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("HIDDEN_CANONICAL_CONTROL_ACCOUNTING=PASS")
print("REDDIT_ACCOUNT_ZERO_CONTROL_ACCOUNTING=PASS")
print("SPECIAL_HANDLER_ACCOUNTING=PASS")
print("DEPENDENCY_ACCOUNTING=PASS")
print("GLOBAL_SEARCH_ROUTE=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_WAVE_V1_CONTRACT_PASS")
