#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/misc/settings/BoostMorpheSettingsSkeletonPatch.kt"
FRAGMENT="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsFragment.java"

test -f "$SETTINGS"
test -f "$FRAGMENT"

rg -q -F 'get("res/xml/pref_headers_v2.xml")' "$SETTINGS"
rg -q -F 'morphe_boost_settings_entry' "$SETTINGS"
rg -q -F 'app.morphe.extension.boostforreddit.settings.MorpheSettingsFragment' "$SETTINGS"
rg -q -F 'MORPHE_BOOST_TOP_LEVEL_SETTINGS_ISSUE106_V1' "$FRAGMENT"
rg -q -F 'setPreferencesFromResource(resourceId, rootKey);' "$FRAGMENT"

if rg -q -F 'get("res/xml/pref_advanced_v2.xml")' "$SETTINGS"; then
    echo 'FAIL: Morphe settings are still written into Advanced' >&2
    exit 1
fi

python3 - "$SETTINGS" "$FRAGMENT" <<'PY_CHECK'
import re
import sys
from pathlib import Path

settings = Path(sys.argv[1]).read_text()
fragment = Path(sys.argv[2]).read_text()

preference_keys = [
    "morphe_boost_inline_media_previews_enabled",
    "morphe_boost_inline_media_preview_size",
    "morphe_boost_inline_media_preview_alignment",
    "morphe_boost_inline_media_preview_show_source_text",
    "morphe_boost_direct_reddit_gif_tap_action",
    "morphe_boost_giphy_preview_tap_action",
    "morphe_boost_static_preview_tap_action",
    "morphe_boost_search_open_keyboard_on_entry",
    "morphe_boost_prefer_high_refresh_rate",
    "morphe_boost_reddit_undelete_enabled",
    "morphe_boost_imgur_undelete_enabled",
]

for key in preference_keys:
    assert settings.count(f'android:key="{key}"') == 1, key

assert settings.count('android:key="morphe_boost_settings_entry"') == 1
assert settings.count('android:title="Morphe"') == 1
assert settings.count(
    'android:summary="Features added by Morphe patches"'
) == 1
assert settings.index(
    'android:key="morphe_boost_settings_entry"'
) < settings.index("PreferenceFragmentGeneralCompat")

morphe_entry = re.search(
    r'<Preference\s+[^>]*android:key="morphe_boost_settings_entry"[^>]*/>',
    settings,
    re.S,
)
assert morphe_entry is not None
assert 'app:allowDividerBelow="true"' in morphe_entry.group(0)

general_entry = re.search(
    r'<Preference\s+[^>]*PreferenceFragmentGeneralCompat[^>]*/>',
    settings,
    re.S,
)
assert general_entry is not None
assert 'app:allowDividerAbove="true"' in general_entry.group(0)

categories = re.findall(
    r'<PreferenceCategory android:title="([^"]+)">',
    settings,
)

assert categories == [
    "Media previews",
    "Open behavior",
    "Search",
    "Display &amp; performance",
    "Recovery &amp; archives",
]

root_fragments = [
    "PreferenceFragmentGeneralCompat",
    "PreferenceFragmentAppearanceCompat",
    "PreferenceFragmentMessagesCompat",
    "PreferenceFragmentFiltersCompat",
    "PreferenceFragmentDataSavingCompat",
    "PreferenceFragmentPrivacyCompat",
    "MorpheSettingsFragment",
    "PreferenceFragmentAdvancedCompat",
    "PreferenceFragmentAboutCompat",
]

for name in root_fragments:
    assert settings.count(name) == 1, name

assert 'RESOURCE_NAME = "morphe_boost_settings_skeleton"' in fragment
assert 'context.getPackageName()' in fragment
assert 'BOOST_PACKAGE = "com.rubenmayayo.reddit"' in fragment
assert fragment.count("resources.getIdentifier(") == 2
assert "if (resourceId == 0)" in fragment
assert "throw new IllegalStateException(" in fragment
PY_CHECK

echo 'TOP_LEVEL_MORPHE_ENTRY=PASS'
echo 'MORPHE_ENTRY_FIRST_WITH_DIVIDER=PASS'
echo 'CANONICAL_MORPHE_KEYS=PASS'
echo 'ADVANCED_DUPLICATE_REMOVED=PASS'
echo 'MORPHE_FRAGMENT_RESOURCE_FALLBACK=PASS'
echo 'RESULT=MORPHE_ISSUE106_SETTINGS_CONTRACT_OK'
