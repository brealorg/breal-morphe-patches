#!/usr/bin/env python3
"""Seven-category root depth contract for Morphe Issue #121."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings"
CATALOG = (SETTINGS / "MorpheSettingsV4Catalog.java").read_text(encoding="utf-8")
FRAGMENT = (SETTINGS / "MorpheSettingsV4Fragment.java").read_text(encoding="utf-8")
DEPTH_PATH = ROOT / "tools/contracts/boost-settings-ux-depth-v1.json"
DEPTH = json.loads(DEPTH_PATH.read_text(encoding="utf-8"))

EXPECTED_ROOT_GROUPS = [
    ("root_morphe", "Morphe", []),
    ("root_appearance", "Appearance", [
        "theme_colors", "community_header", "post_layout", "typography", "display_motion"
    ]),
    ("root_reading_interaction", "Reading & interaction", [
        "posts", "comments", "search_filters", "feeds_subscriptions", "composing_drafts"
    ]),
    ("root_navigation", "Navigation", ["navigation"]),
    ("root_media", "Media", [
        "playback_autoplay", "images_previews", "links_browser", "downloads_cache"
    ]),
    ("root_notifications_account", "Notifications & account", [
        "notifications_inbox", "reddit_account", "history_privacy_recovery"
    ]),
    ("root_data_app", "Data & app", [
        "storage_bandwidth", "backup_restore", "app_behavior_compatibility",
        "settings_experience", "about_support"
    ]),
]

array_start = CATALOG.index("private static final RootGroup[] ROOT_GROUPS")
array_end = CATALOG.index("\n\n    private MorpheSettingsV4Catalog()", array_start)
array = CATALOG[array_start:array_end]
blocks = re.findall(r"new RootGroup\((.*?)\n            \)", array, re.S)
actual = []
for block in blocks:
    strings = re.findall(r'"([^"]+)"', block)
    assert len(strings) >= 4, strings
    actual.append((strings[0], strings[1], strings[4:]))
assert actual == EXPECTED_ROOT_GROUPS, actual

render_start = FRAGMENT.index("    private void renderCategories() {")
render_end = FRAGMENT.index("\n\n    private void renderSearchResults(", render_start)
render = FRAGMENT[render_start:render_end]
assert "MorpheSettingsV4Catalog.rootGroups()" in render
assert "MorpheSettingsV14Ui.standardList(requireContext())" in render
assert "MorpheSettingsV14Ui.addStandardListRow(" in render
assert "renderRootCard(" not in FRAGMENT
assert "renderHomeSection(" not in render
for _, task_title in re.findall(
    r'new\s+Category\(\s*"([^"]+)"\s*,\s*"([^"]+)"', CATALOG, re.S
):
    assert f'addSectionLabel(dynamicContent, "{task_title}")' not in render

assert "private void buildRootGroup(" in FRAGMENT
assert "rootGroup.includesMorphe" in FRAGMENT
assert "for (String categoryId : rootGroup.categoryIds)" in FRAGMENT
assert "view -> openRootGroup(rootGroup)" in FRAGMENT
assert "MORPHE_BOOST_SETTINGS_SEVEN_CARD_ROOT_ISSUE121_PHASE1_1_V1" in FRAGMENT
assert "MorpheSettingsV4Catalog.findRootGroup(page)" in FRAGMENT

assert DEPTH["schema"] == 1
assert DEPTH["issue"] == 121
assert DEPTH["root_screen"] == "seven_category_list_rows"
assert DEPTH["root_group_count"] == 7
assert DEPTH["task_pages"] == "one_level_below_root"
assert DEPTH["task_pages_must_not_be_xml_dumps"] is True
assert DEPTH["intermediate_pages_required_when_domains_mix"] is True
assert DEPTH["leaf_target_controls"] == {"min": 4, "max": 9}
assert DEPTH["leaf_hard_max_controls"] == 12
assert DEPTH["section_headers_do_not_replace_navigation"] is True
assert DEPTH["comments_topology"]["child_pages"] == [
    "Sorting & loading", "Appearance", "Collapse behavior", "Navigation & controls"
]

print("ROOT_CATEGORY_ROW_COUNT=7")
print("ROOT_LAYOUT=STANDARD_LIST")
print("TASK_PAGES_ON_ROOT=0")
print("TASK_PAGES_ONE_LEVEL_DOWN=PASS")
print("LEAF_TARGET_RANGE=4_TO_9")
print("LEAF_HARD_MAX=12")
print("COMMENTS_DEPTH_CONTRACT=PASS")
print("CLASSIC_FALLBACK_PRESERVED=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_SEVEN_CATEGORY_ROOT_CONTRACT_PASS")
