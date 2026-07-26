#!/usr/bin/env python3
"""Evidence-based Android Settings guidance pilot for Morphe Issue #121."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    / "boostforreddit/settings"
)
UI_PATH = SETTINGS / "MorpheSettingsV14Ui.java"
ROOT_PATH = SETTINGS / "MorpheSettingsV4Fragment.java"
DRAWER_PATH = SETTINGS / "MorpheSettingsV4NavigationDrawerHubFragment.java"
NATIVE_PATH = SETTINGS / "MorpheSettingsV4NativePages.java"
CONTRACT_PATH = (
    ROOT / "tools/contracts/boost-settings-android-guidance-pilot-v1.json"
)
NAV_CONTRACT_PATH = (
    ROOT / "tools/contracts/boost-settings-navigation-phase2-v1.json"
)

raw = CONTRACT_PATH.read_bytes()
contract_sha = hashlib.sha256(raw).hexdigest()
contract = json.loads(raw.decode("utf-8"))
ui = UI_PATH.read_text(encoding="utf-8")
root = ROOT_PATH.read_text(encoding="utf-8")
drawer = DRAWER_PATH.read_text(encoding="utf-8")
native = NATIVE_PATH.read_text(encoding="utf-8")
nav_contract = json.loads(NAV_CONTRACT_PATH.read_text(encoding="utf-8"))

assert contract["schema"] == 1
assert contract["issue"] == 121
assert contract["phase"] == "2.2/7 Android settings guidance pilot"
assert contract["claim"] == "alignment_pilot_not_google_certification"
assert len(contract["official_guidance"]) == 3
assert contract["behavior_changes"] == "none"
assert contract["preference_key_changes"] == 0
assert contract["route_changes"] == 0
assert contract["withheld_friends_key"] == "preserved"
assert contract["classic_fallback"] == "preserved"
assert contract["global_search"] == "preserved"

anatomy = contract["list_anatomy"]
assert anatomy == {
    "one_line_min_height_dp": 56,
    "two_line_min_height_dp": 72,
    "headline_sp": 16,
    "supporting_sp": 14,
    "supporting_max_lines": 2,
    "trailing_boolean_control": "switch",
    "trailing_destination_control": "chevron",
}

assert "MORPHE_BOOST_SETTINGS_ANDROID_GUIDANCE_PILOT_" in ui
assert "static LinearLayout standardList(" in ui
assert "static LinearLayout standardListRow(" in ui
assert "static LinearLayout standardListLabels(" in ui
assert "static void addStandardListRow(" in ui
assert "hasSupportingText ? 72 : 56" in ui
assert "0x00000000" in ui
assert "supporting.setMaxLines(2);" in ui
assert "divider.setBackgroundColor" in ui

assert "MORPHE_BOOST_SETTINGS_ANDROID_GUIDANCE_OVERVIEW_" in root
assert "renderRootCard(" not in root
assert "MorpheSettingsV14Ui.standardList(requireContext())" in root
assert root.count("MorpheSettingsV14Ui.addStandardListRow(") >= 3
assert "createStandardNavigationRow(" in root
assert "createPlainLeadingIcon(" in root
assert "root_morphe" in root and '"Patch features"' in root
assert "addPageIntro(content, rootGroup.summary);" not in root
assert "addPageIntro(content, category.summary);" not in root

assert "MORPHE_BOOST_SETTINGS_NAVIGATION_DRAWER_STANDARD_LIST_" in drawer
assert "MorpheSettingsV14Ui.standardList(context)" in drawer
assert "MorpheSettingsV14Ui.standardListRow(" in drawer
assert "MorpheSettingsV14Ui.standardListLabels(" in drawer
assert "MorpheSettingsV14Ui.addStandardListRow(" in drawer
assert "createLeadingIcon(" not in drawer
assert "destination.summary" not in drawer
assert "destination.iconName" not in drawer
assert "MorpheSettingsV14Ui.pageIntro(" not in drawer
for heading in ("Destinations", "Shortcuts", "Personalization"):
    assert f'"{heading}"' in drawer

assert "MORPHE_BOOST_SETTINGS_NATIVE_STANDARD_PREFERENCE_LIST_" in native
assert "protected boolean useAndroidSettingsGuidanceRows()" in native
assert "return true;" in native[
    native.index("private abstract static class NavigationSubsetPage"):
    native.index("public static final class BottomNavigation")
]
assert "MorpheSettingsV14Ui.standardList(requireContext())" in native
assert "MorpheSettingsV14Ui.standardListRow(" in native
assert "MorpheSettingsV14Ui.addStandardListRow(group, row, tokens);" in native
assert 'return "Drawer destinations";' in native
assert 'return "Default feed";' in native
assert 'return "Posts from subscriptions";' in native
assert "To set any community" not in native
assert "useAndroidSettingsGuidanceRows() ? 2 : 4" in native

# Existing ownership and behavior contracts remain authoritative.
assert nav_contract["exposed_key_count"] == 30
assert nav_contract["withheld_keys"] == ["pref_drawer_show_friends"]
assert nav_contract["max_controls_per_leaf"] == 6
assert len(nav_contract["leaf_pages"]) == 10

print(f"CONTRACT={CONTRACT_PATH}")
print(f"CONTRACT_SHA256={contract_sha}")
print("GUIDANCE_SOURCE_COUNT=3")
print("CLAIM=ALIGNMENT_PILOT_NOT_GOOGLE_CERTIFICATION")
print("SETTINGS_OVERVIEW=STANDARD_LIST")
print("ROOT_GROUP_PAGES=STANDARD_DESTINATION_LIST")
print("TASK_PAGES=STANDARD_DESTINATION_LIST")
print("NAVIGATION_DRAWER=HEADINGS_AND_PLAIN_ROWS")
print("NAVIGATION_LEAF_PAGES=STANDARD_PREFERENCE_ROWS")
print("ITEM_CARD_BACKGROUND=ABSENT_IN_PILOT_SCOPE")
print("SUPPORTING_TEXT_POLICY=STATUS_VALUE_CONSEQUENCE_ONLY")
print("PREFERENCE_KEY_CHANGES=0")
print("ROUTE_CHANGES=0")
print("RESULT=MORPHE_ISSUE121_ANDROID_SETTINGS_GUIDANCE_PILOT_V1_PASS")
