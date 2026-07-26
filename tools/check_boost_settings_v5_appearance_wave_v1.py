#!/usr/bin/env python3
"""Static contract for the hidden, complete Settings V5 Appearance wave."""

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
WAVE_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-appearance-wave-v1.json"
V5_CONTRACT = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"

EXPECTED_WAVE_SHA = "987fec53ecef1bd96dd22839a0fd67a6c25722d1504cc77e472ca5aa1b20fe10"
EXPECTED_CAPTURE_V5_SHA = "4c8e081069d7444938c3ff5ad0e451bb3b16183592114a6b8adf77cd1208d3f7"
EXPECTED_V5_SHA = "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
EXPECTED_ARCHIVE_SHA = "1953af26eb18a1f00a9adeb02252696551d394867892afa0b62ac65ad0b53ae3"
EXPECTED_BASE_APK_SHA = "a68c22d632a5dd1f446c3759a171c7dbb9edab2afc3c2f87e39323f198606742"
EXPECTED_SOURCE_MPP_SHA = "c238c79d16f17877a46483e6f467f36a0086f50fe8c4172b64663e4ab30016f7"

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
assert wave["wave"] == "Appearance"
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
    "screen_nodes": 22,
    "visible_by_default": False,
    "visible_items": 39,
    "withheld_items": 0,
}

appearance_screens = [screen for screen in v5["screens"] if screen["root"] == "Appearance"]
appearance_items = [item for item in v5["items"] if item["root"] == "Appearance"]
assert len(appearance_screens) == 22
assert len(appearance_items) == 39
assert all(item["v5_visibility"] == "VISIBLE" for item in appearance_items)

expected_pages = {entry["page_id"]: entry for entry in wave["screens"]}
expected_keys_by_page = {page_id: set() for page_id in expected_pages}
for item in wave["items"]:
    expected_keys_by_page[item["page_id"]].add(item["key"])
assert len(expected_pages) == 22
assert sum(len(keys) for keys in expected_keys_by_page.values()) == 39
assert max(len(keys) for keys in expected_keys_by_page.values()) == 8

source_paths = [SETTINGS / name for name in wave["source_files"]]
assert all(path.is_file() for path in source_paths)
source = {path.name: path.read_text(encoding="utf-8") for path in source_paths}
all_text = "\n".join(source.values())
registry = source["MorpheSettingsV5Registry.java"]
fragment = source["MorpheSettingsV5AppearanceFragment.java"]
bindings = source["MorpheSettingsV5AppearanceBindings.java"]
search = source["MorpheSettingsV5Search.java"]

for token in FORBIDDEN:
    assert token not in all_text, token

assert "V5_VISIBLE_BY_DEFAULT = false" in registry
assert "V5_VISIBLE_BY_DEFAULT = true" not in registry
assert "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_WAVE_ISSUE121_V1" in registry
assert "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_FRAGMENT_ISSUE121_V1" in fragment
assert "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_BINDINGS_ISSUE121_V1" in bindings
assert "MORPHE_BOOST_SETTINGS_V5_SEARCH_ISSUE121_V1" in search

actual_pages: dict[str, dict] = {}
for match in PAGE_PATTERN.finditer(registry):
    page_id = match.group("page_id")
    assert page_id not in actual_pages
    actual_pages[page_id] = {
        "renderer": match.group("renderer"),
        "keys": set(KEY_PATTERN.findall(match.group("keys"))),
    }
appearance_actual_pages = {
    page_id: value
    for page_id, value in actual_pages.items()
    if page_id.startswith("v5/appearance")
}
assert set(appearance_actual_pages) == set(expected_pages)
for page_id, page in appearance_actual_pages.items():
    assert page["renderer"] == "MorpheSettingsV5AppearanceFragment"
    assert page["keys"] == expected_keys_by_page[page_id], (
        page_id,
        sorted(page["keys"]),
        sorted(expected_keys_by_page[page_id]),
    )

assert "MorpheSettingsV5Registry.requirePage(" in fragment
assert "MorpheSettingsV5Registry.childrenFor(" in fragment
assert "MorpheSettingsV5AppearanceBindings.renderPage(" in fragment
assert "MorpheSettingsV5Search.prepareMenu(this, menu)" in fragment
assert "MorpheSettingsV5Search.handleMenuItem(this, item)" in fragment
navigation = (SETTINGS / "MorpheSettingsV5Navigation.java").read_text(
    encoding="utf-8"
)
assert "MorpheSettingsV5Navigation.openPage(this, targetPageId)" in fragment
assert "activity.startActivity(intent)" in navigation

assert "PreferenceManager.getDefaultSharedPreferences" in bindings
assert '"com.rubenmayayo.reddit.VIEW_PER_SUBSCRIPTION"' in bindings
assert "pref_theme_values_night" in bindings
assert "pref_theme_values" in bindings
assert 'Class.forName("he.f0")' in bindings
assert 'invokeStaticNoArgs("kb.a", "d")' in bindings
assert 'Class.forName("id.b")' in bindings
assert "TimePickerDialog" in bindings
assert "PackageManager.COMPONENT_ENABLED_STATE_ENABLED" in bindings
assert "pref_cards_preview_self_lines" in bindings
assert "setBoostStaticString(" in bindings
assert "showAddSavedViewDialog" in bindings
assert "renderSavedEntries" in bindings
assert "confirmResetTypography" in bindings
assert "hideSoftInputFromWindow" in search
assert "OnBackInvokedDispatcher.PRIORITY_OVERLAY" in search
assert "new Dialog(activity)" in search
assert "MorpheSettingsV5Navigation.openPage(host, entry.pageId)" in search

for key, dependency in wave["dependencies"].items():
    assert f'"{key}"' in bindings
    if dependency.startswith("not:"):
        assert f'"{dependency[4:]}"' in bindings
        assert "inverseDependency" in bindings
    else:
        assert f'"{dependency}"' in bindings

for category, keys in wave["special_handlers"].items():
    assert keys, category
    for key in keys:
        assert f'"{key}"' in bindings, (category, key)

with tempfile.TemporaryDirectory(prefix="morphe-v5-appearance-audit-") as temp:
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
    assert report["current"]["v5_source_file_count"] >= 4
    assert report["current"]["implemented_target_page_count"] >= 22
    assert report["current"]["implemented_visible_item_count"] >= 39
    assert report["current"]["visible_state"] == "FALSE"
    violations = report["violations"]
    assert violations["placeholder_count"] == 0
    assert violations["classic_route_violations"] == []
    assert violations["duplicate_page_ids"] == []
    assert violations["duplicate_visible_keys"] == []
    assert violations["page_key_mismatches"] == []
    assert violations["missing_renderer_classes"] == []
    appearance = next(
        row for row in report["root_progress"] if row["root"] == "Appearance"
    )
    assert appearance == {
        "root": "Appearance",
        "implemented_pages": 22,
        "expected_pages": 22,
        "implemented_visible_items": 39,
        "expected_visible_items": 39,
        "accounted_withheld_items": 0,
        "expected_withheld_items": 0,
    }
    assert "V5_SCREEN_NODE_COVERAGE=" in completed.stdout
    assert "V5_VISIBLE_ITEM_COVERAGE=" in completed.stdout
    assert "V5_VISIBLE_STATE=FALSE" in completed.stdout

print(f"CONTRACT={WAVE_CONTRACT}")
print(f"CONTRACT_SHA256={EXPECTED_WAVE_SHA}")
print("V5_APPEARANCE_SCREEN_COVERAGE=22/22")
print("V5_APPEARANCE_VISIBLE_ITEM_COVERAGE=39/39")
print("V5_GLOBAL_SCREEN_COVERAGE=22/105")
print("V5_GLOBAL_VISIBLE_ITEM_COVERAGE=39/247")
print("V5_VISIBLE_STATE=FALSE")
print("PLACEHOLDER_PAGE_COUNT=0")
print("LEGACY_ROUTE_COUNT=0")
print("SPECIAL_HANDLER_ACCOUNTING=PASS")
print("DEPENDENCY_ACCOUNTING=PASS")
print("GLOBAL_SEARCH_ROUTE=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_APPEARANCE_WAVE_V1_CONTRACT_PASS")
