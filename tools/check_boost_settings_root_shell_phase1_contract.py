#!/usr/bin/env python3
"""Static task-page contract for Morphe Issue #121 Settings Phase 1."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings"
CATALOG_PATH = SETTINGS / "MorpheSettingsV4Catalog.java"
FRAGMENT_PATH = SETTINGS / "MorpheSettingsV4Fragment.java"
NATIVE_PATH = SETTINGS / "MorpheSettingsV4NativePages.java"

catalog = CATALOG_PATH.read_text(encoding="utf-8")
fragment = FRAGMENT_PATH.read_text(encoding="utf-8")
native = NATIVE_PATH.read_text(encoding="utf-8")

EXPECTED_CATEGORIES = [
    ("theme_colors", "Theme & colors"),
    ("community_header", "Community header"),
    ("post_layout", "Post layout"),
    ("typography", "Typography"),
    ("display_motion", "Display & motion"),
    ("posts", "Posts"),
    ("comments", "Comments"),
    ("search_filters", "Search & filters"),
    ("feeds_subscriptions", "Feeds & subscriptions"),
    ("composing_drafts", "Composing & drafts"),
    ("navigation", "Navigation & gestures"),
    ("playback_autoplay", "Playback & autoplay"),
    ("images_previews", "Images, GIFs & previews"),
    ("links_browser", "Links & browser"),
    ("downloads_cache", "Downloads & cache"),
    ("notifications_inbox", "Notifications & inbox"),
    ("reddit_account", "Reddit account"),
    ("history_privacy_recovery", "History, privacy & recovery"),
    ("storage_bandwidth", "Storage & bandwidth"),
    ("backup_restore", "Backup & restore"),
    ("app_behavior_compatibility", "App behavior & compatibility"),
    ("settings_experience", "Settings experience"),
    ("about_support", "About & support"),
]

category_pattern = re.compile(
    r'new\s+Category\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,',
    re.S,
)
actual_categories = category_pattern.findall(catalog)
assert actual_categories == EXPECTED_CATEGORIES, actual_categories
assert len(EXPECTED_CATEGORIES) + 1 == 24

assert "MORPHE_BOOST_SETTINGS_ROOT_SHELL_ISSUE121_PHASE1_V1" in fragment
assert "if (!opensDirectly(category))" in catalog
assert "addTaskPageSearchItem(result, seen, category);" in catalog
assert "static boolean opensDirectly(Category category)" in catalog
assert "final String pageId;" in catalog
assert "openCategory(category);" in fragment
assert "intent.putExtra(ARGUMENT_PAGE, categoryId);" in fragment
assert "MorpheSettingsV4Catalog.opensDirectly(category)" in fragment
assert "category.leaves.length == 1" in catalog
assert '"navigation".equals(category.id)' not in catalog
navigation_start = catalog.index(
    'new Category(\n                    "navigation"'
)
navigation_end = catalog.index("new Category(", navigation_start + 1)
navigation_category = catalog[navigation_start:navigation_end]
assert navigation_category.count("Leaf.fragment(") == 4
for title in (
    "Toolbar",
    "Bottom navigation",
    "Navigation drawer",
    "Back & exit",
):
    assert f'"{title}"' in navigation_category
assert "category.leaves.length == 0" in fragment
assert "classic Boost settings while this task page is organized" in fragment
assert "public static final class Headers extends NativePage" in native
assert 'super("Community header", "pref_headers_v2");' in native

print("TASK_PAGE_COUNT=24")
print("TASK_PAGE_SEARCH_ROUTING=PASS")
print("DIRECT_EXISTING_ROUTE_POLICY=PASS")
print("PLACEHOLDER_TASK_PAGE_POLICY=PASS")
print("CLASSIC_FALLBACK_PRESERVED=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_ROOT_SHELL_PHASE1_CONTRACT_PASS")
