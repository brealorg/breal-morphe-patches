#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/misc/settings/BoostMorpheSettingsSkeletonPatch.kt"
FRAGMENT="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsFragment.java"
HUB="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsHubFragment.java"
LAYOUT="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsLayout.java"
V4="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4.java"
V4_FRAGMENT="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4Fragment.java"
V4_APPEARANCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4AppearanceFragment.java"
V4_APP_ICON="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4AppIconFragment.java"
V4_POST_VIEWS="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4PostViewsFragment.java"
V4_SAVED_VIEWS="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4SavedViewsFragment.java"
V4_FONTS="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4FontsFragment.java"
V4_CATALOG="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4Catalog.java"
V4_THEME="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4Theme.java"
V4_NATIVE_PAGES="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV4NativePages.java"
V14_UI="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV14Ui.java"
SYSTEM_BARS="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSystemBarInsetsFix.java"

test -f "$SETTINGS"
test -f "$FRAGMENT"
test -f "$HUB"
test -f "$LAYOUT"
test -f "$V4"
test -f "$V4_FRAGMENT"
test -f "$V4_APPEARANCE"
test -f "$V4_APP_ICON"
test -f "$V4_POST_VIEWS"
test -f "$V4_SAVED_VIEWS"
test -f "$V4_FONTS"
test -f "$V4_CATALOG"
test -f "$V4_THEME"
test -f "$V4_NATIVE_PAGES"
test -f "$V14_UI"
test -f "$SYSTEM_BARS"

rg -q -F 'get("res/xml/pref_headers_v2.xml")' "$SETTINGS"
rg -q -F 'morphe_boost_settings_entry' "$SETTINGS"
rg -q -F 'app.morphe.extension.boostforreddit.settings.MorpheSettingsFragment' "$SETTINGS"
rg -q -F 'MORPHE_BOOST_TOP_LEVEL_SETTINGS_ISSUE106_V1' "$FRAGMENT"
rg -q -F 'MORPHE_BOOST_SETTINGS_LAYOUT_ISSUE106_V2_1' "$LAYOUT"
rg -q -F 'MORPHE_BOOST_SETTINGS_HUBS_ISSUE106_V2_1' "$HUB"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_ISSUE106_V1' "$V4"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_UI_ISSUE106_V1' "$V4_FRAGMENT"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_APPEARANCE_ISSUE106_V1' "$V4_APPEARANCE"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_APP_ICON_ISSUE106_V1' "$V4_APP_ICON"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_COMPACT_GROUPS_ISSUE106_V1' "$V4_APPEARANCE"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_POST_VIEWS_ISSUE106_V1' "$V4_POST_VIEWS"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_SAVED_VIEWS_ISSUE106_V1' "$V4_SAVED_VIEWS"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_FONTS_ISSUE106_V1' "$V4_FONTS"
rg -q -F 'MORPHE_BOOST_SETTINGS_V4_NATIVE_REST_ISSUE106_V1' "$V4_NATIVE_PAGES"
rg -q -F 'MORPHE_BOOST_SETTINGS_V14_COHERENT_UI_ISSUE106_V1' "$V14_UI"
rg -q -F 'setPreferencesFromResource(resourceId, rootKey);' "$FRAGMENT" "$HUB"

if rg -q -F 'get("res/xml/pref_advanced_v2.xml")' "$SETTINGS"; then
    echo 'FAIL: Morphe settings are still written into Advanced' >&2
    exit 1
fi

python3 - \
    "$SETTINGS" \
    "$FRAGMENT" \
    "$LAYOUT" \
    "$HUB" \
    "$V4" \
    "$V4_FRAGMENT" \
    "$V4_APPEARANCE" \
    "$V4_APP_ICON" \
    "$V4_POST_VIEWS" \
    "$V4_SAVED_VIEWS" \
    "$V4_FONTS" \
    "$V4_CATALOG" \
    "$V4_THEME" \
    "$V4_NATIVE_PAGES" \
    "$V14_UI" \
    "$SYSTEM_BARS" <<'PY_CHECK'
import re
import sys
from pathlib import Path

settings = Path(sys.argv[1]).read_text()
fragment = Path(sys.argv[2]).read_text()
layout = Path(sys.argv[3]).read_text()
hub = Path(sys.argv[4]).read_text()
v4 = Path(sys.argv[5]).read_text()
v4_fragment = Path(sys.argv[6]).read_text()
v4_appearance = Path(sys.argv[7]).read_text()
v4_app_icon = Path(sys.argv[8]).read_text()
v4_post_views = Path(sys.argv[9]).read_text()
v4_saved_views = Path(sys.argv[10]).read_text()
v4_fonts = Path(sys.argv[11]).read_text()
v4_catalog = Path(sys.argv[12]).read_text()
v4_theme = Path(sys.argv[13]).read_text()
v4_native_pages = Path(sys.argv[14]).read_text()
v14_ui = Path(sys.argv[15]).read_text()
system_bars = Path(sys.argv[16]).read_text()

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

toggle_key = "morphe_boost_settings_v4_enabled"
assert settings.count(f'android:key="{toggle_key}"') == 1
assert settings.count(
    'android:key="morphe_boost_settings_layout_v2_enabled"'
) == 0
toggle = re.search(
    rf'<CheckBoxPreference\s+[^>]*android:key="{toggle_key}"[^>]*/>',
    settings,
    re.S,
)
assert toggle is not None
assert 'android:title="Material settings"' in toggle.group(0)
assert 'android:defaultValue="true"' in toggle.group(0)
assert "Use Morphe's modern task-based settings. Reopen Settings to apply." in toggle.group(0)

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
    "Settings",
]

legacy_start = settings.index('get("res/xml/pref_headers_v2.xml")')
v2_start = settings.index('"morphe_boost_settings_layout_v2" to')
first_hub_start = settings.index(
    '"morphe_boost_settings_hub_appearance_layout" to'
)
legacy_root = settings[legacy_start:v2_start]
v2_root = settings[v2_start:first_hub_start]
v2_resources = settings[v2_start:]

for root in (legacy_root, v2_root):
    assert root.count('android:key="morphe_boost_settings_entry"') == 1
    assert root.count('android:title="Morphe"') == 1
    assert root.count(
        'android:summary="Features added by Morphe patches"'
    ) == 1

    morphe_entry = re.search(
        r'<Preference\s+[^>]*android:key="morphe_boost_settings_entry"[^>]*/>',
        root,
        re.S,
    )
    assert morphe_entry is not None
    assert 'app:allowDividerBelow="true"' in morphe_entry.group(0)

assert legacy_root.index(
    'android:key="morphe_boost_settings_entry"'
) < legacy_root.index("PreferenceFragmentGeneralCompat")
assert v2_root.index(
    'android:key="morphe_boost_settings_entry"'
) < v2_root.index("morphe_boost_settings_v2_appearance_layout")
assert 'app:allowDividerAbove="true"' in v2_root.split(
    'morphe_boost_settings_v2_appearance_layout', 1
)[1].split('</Preference>', 1)[0]

hub_names = [
    "appearance_layout",
    "posts_comments",
    "navigation",
    "media_links",
    "search_filters",
    "data_storage",
    "account_privacy",
    "app_legacy",
]

for name in hub_names:
    resource_name = f"morphe_boost_settings_hub_{name}"
    assert settings.count(f'"{resource_name}" to') == 1, resource_name
    assert v2_root.count(f'android:value="{resource_name}"') == 1, name

assert v2_root.count("MorpheSettingsHubFragment") == len(hub_names)
assert "PreferenceFragmentAdvancedCompat" not in v2_resources
assert 'android:key="remove_ads"' not in v2_resources
assert 'android:key="buy_pro"' not in v2_resources
assert 'android:key="support_launch"' not in v2_resources
assert 'android:key="privacy_policy"' not in v2_resources

v2_leaf_fragments = [
    "PreferenceFragmentAppearanceCompat",
    "PreferenceFragmentViewsCompat",
    "PreferenceFragmentFontsCompat",
    "PreferenceFragmentPostsCompat",
    "PreferenceFragmentCommentsCompat",
    "PreferenceFragmentToolbarCompat",
    "PreferenceFragmentBottomNavigationCompat",
    "PreferenceFragmentDrawerCompat",
    "PreferenceFragmentMediaCompat",
    "PreferenceFragmentLinksCompat",
    "PreferenceFragmentSearchCompat",
    "PreferenceFragmentFiltersCompat",
    "PreferenceFragmentMessagesCompat",
    "PreferenceFragmentDataSavingCompat",
    "PreferenceFragmentAccountCompat",
    "PreferenceFragmentPrivacyCompat",
    "PreferenceFragmentGeneralCompat",
    "PreferenceFragmentMiscCompat",
    "PreferenceFragmentAboutCompat",
]

for name in v2_leaf_fragments:
    assert v2_resources.count(name) == 1, name

assert 'get("res/xml/$resourceName.xml")' in settings
assert 'get("res/xml/\\$resourceName.xml")' not in settings
assert 'RESOURCE_NAME = "morphe_boost_settings_skeleton"' in fragment
assert 'context.getPackageName()' in fragment
assert 'BOOST_PACKAGE = "com.rubenmayayo.reddit"' in fragment
assert fragment.count("resources.getIdentifier(") == 2
assert "throw new IllegalStateException(" in fragment

assert 'ENABLED_KEY =\n            "morphe_boost_settings_layout_v2_enabled"' in layout
assert f'V4_ENABLED_KEY =\n            "{toggle_key}"' in layout
assert 'RESOURCE_NAME =\n            "morphe_boost_settings_layout_v2"' in layout
assert "import android.preference.PreferenceManager;" in layout
assert "import androidx.preference.PreferenceManager;" not in layout
assert 'preferences.contains(V4_ENABLED_KEY)' in layout
assert 'preferences.getBoolean(ENABLED_KEY, false)' in layout
assert 'return originalResourceId;' in layout
assert 'return resourceId == 0 ? originalResourceId : resourceId;' in layout
assert layout.count("resources.getIdentifier(") == 2

assert 'RESOURCE_ARGUMENT =\n            "morphe_boost_settings_hub_resource"' in hub
assert 'resourceName.startsWith(RESOURCE_PREFIX)' in hub
assert 'Preference backup = findPreference(BACKUP_KEY);' in hub
assert 'intent.setClassName(context.getPackageName(), BACKUP_ACTIVITY);' in hub
assert 'startActivity(intent);' in hub

assert f'ENABLED_KEY =\n            "{toggle_key}"' in v4
assert 'LEGACY_V2_ENABLED_KEY =\n            "morphe_boost_settings_layout_v2_enabled"' in v4
assert 'public static void prepareIntent(Activity activity)' in v4
assert 'preferences.contains(ENABLED_KEY)' in v4
assert 'boolean legacyEnabled = preferences.getBoolean(' in v4
assert 'LEGACY_V2_ENABLED_KEY,' in v4
assert '.putBoolean(ENABLED_KEY, true)' in v4
assert 'intent.getStringExtra(EXTRA_SHOW_FRAGMENT)' in v4
assert 'intent.putExtra(EXTRA_SHOW_FRAGMENT, FRAGMENT_NAME);' in v4
assert 'MorpheSettingsV4Fragment' in v4
assert 'THEME_MODE_KEY = "pref_theme_mode_type"' in v4
assert 'SYSTEM_THEME_MODE = "system"' in v4
assert 'forceSystemThemeMode(preferences);' in v4
assert '.putString(THEME_MODE_KEY, SYSTEM_THEME_MODE)' in v4

assert 'extends Fragment' in v4_fragment
assert 'new ScrollView(context)' in v4_fragment
assert 'new EditText(context)' in v4_fragment
assert 'renderSearchResults' in v4_fragment
assert 'hideMenuItem(menu, "action_generic_search")' in v4_fragment
assert 'openClassicSettings' in v4_fragment
assert 'view -> openLeaf(leaf, null)' in v4_fragment
assert 'MorpheSettingsV14Ui.addSegmentedRow(' in v4_fragment
assert 'intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);' in v4_fragment
assert 'activity.startActivity(intent);' in v4_fragment
assert 'private Activity hostActivity()' in v4_fragment
for forbidden_call in [
    'getActivity()',
    'getFragmentManager()',
    'fragment.setTargetFragment',
    'fragmentManager.beginTransaction()',
    'navigateFragment(',
    'fragment_container',
]:
    assert forbidden_call not in v4_fragment, forbidden_call
assert 'androidx.compose' not in v4_fragment
assert 'ComposeView' not in v4_fragment

assert 'extends Fragment' in v4_appearance
assert 'MORPHE_BOOST_SETTINGS_V4_APPEARANCE_ISSUE106_V1' in v4_appearance
assert 'MORPHE_BOOST_SETTINGS_V4_COMPACT_GROUPS_ISSUE106_V1' in v4_appearance
assert 'MORPHE_BOOST_SETTINGS_V4_SYSTEM_THEME_ISSUE106_V1' in v4_appearance
assert 'PreferenceManager.getDefaultSharedPreferences(context)' in v4_appearance
assert 'KEY_DYNAMIC_COLORS = "pref_dynamic_colors"' in v4_appearance
assert 'KEY_COLORED_STATUS_BAR' in v4_appearance
assert 'KEY_COLORED_NAV_BAR' in v4_appearance
assert 'Class.forName("id.b")' in v4_appearance
assert 'applyDynamicColors(preferences, enabled);' in v4_appearance
assert 'applySystemBarPreference(preferences, key, enabled);' in v4_appearance
assert 'BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(' in v4_appearance
assert 'BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);' in v4_appearance
assert 'private LinearLayout addGroup(LinearLayout parent)' in v4_appearance
assert 'private void addGroupedRow(LinearLayout group, LinearLayout row)' in v4_appearance
assert 'private void addGroupDivider(LinearLayout group)' in v4_appearance
assert 'return MorpheSettingsV14Ui.baseRow(context, tokens);' in v4_appearance
assert 'MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);' in v4_appearance
assert 'ROW_SINGLE_DP = 56' in v14_ui
assert 'ROW_TWO_LINE_DP = 72' in v14_ui
assert 'row.setMinimumHeight(dp(context, ROW_SINGLE_DP));' in v14_ui
assert 'addHeader(content);' not in v4_appearance
assert 'createIconContainer(' not in v4_appearance
assert 'addSectionLabel(content, "Appearance");' in v4_appearance
assert 'addSectionLabel(content, "Personalization");' in v4_appearance
assert 'addSectionLabel(content, "System bars");' in v4_appearance
for removed_theme_ui in [
    '"Theme mode"',
    '"Customize colors"',
    '"Dark starts"',
    '"Light starts"',
    'new AlertDialog.Builder',
    'new TimePickerDialog',
    'openClassicAppearance()',
    'Open classic appearance settings',
]:
    assert removed_theme_ui not in v4_appearance, removed_theme_ui
for forbidden_call in [
    'getActivity()',
    'getFragmentManager()',
    'getParentFragmentManager()',
    'setTargetFragment(',
]:
    assert forbidden_call not in v4_appearance, forbidden_call
assert 'androidx.compose' not in v4_appearance
assert 'ComposeView' not in v4_appearance
assert 'openBoostHelper("w")' not in v4_appearance
assert 'V4_APP_ICON_FRAGMENT' in v4_appearance

assert 'extends Fragment' in v4_app_icon
assert 'MORPHE_BOOST_SETTINGS_V4_APP_ICON_ISSUE106_V1' in v4_app_icon
assert 'PackageManager.COMPONENT_ENABLED_STATE_ENABLED' in v4_app_icon
assert 'PackageManager.COMPONENT_ENABLED_STATE_DISABLED' in v4_app_icon
assert 'PackageManager.DONT_KILL_APP' in v4_app_icon
assert 'packageManager.setComponentEnabledSetting(' in v4_app_icon
assert 'applyIconSelection(context, option.alias);' in v4_app_icon
for alias in ['"grey"', '"vivid"', '"metal"', '"yellow"']:
    assert alias in v4_app_icon, alias
for resource_name in [
    '"ic_launcher"',
    '"ic_launcher_grey"',
    '"ic_launcher_vivid"',
    '"ic_launcher_metal"',
    '"ic_launcher_yellow"',
]:
    assert resource_name in v4_app_icon, resource_name
for forbidden_call in [
    'getActivity()',
    'getFragmentManager()',
    'getParentFragmentManager()',
    'setTargetFragment(',
]:
    assert forbidden_call not in v4_app_icon, forbidden_call
assert 'androidx.compose' not in v4_app_icon
assert 'ComposeView' not in v4_app_icon

assert 'extends Fragment' in v4_post_views
assert 'MORPHE_BOOST_SETTINGS_V4_POST_VIEWS_ISSUE106_V1' in v4_post_views
assert 'PreferenceManager.getDefaultSharedPreferences(context)' in v4_post_views
for key in [
    'pref_view',
    'pref_view_per_subscription',
    'pref_left_handed',
    'pref_show_subreddit_prefix',
    'pref_cards_rounded_corners',
    'pref_cards_full_preview',
    'pref_cards_subreddit_icon',
    'pref_cards_gallery_carousel',
    'pref_cards_links_as_thumbnails',
    'pref_cards_preview_self',
    'pref_cards_preview_self_lines',
    'pref_mini_cards_rounded_corners',
    'pref_mini_cards_truncate_title',
    'pref_mini_cards_buttons_visible',
    'pref_dense_buttons_visible',
    'pref_load_readability',
    'pref_lock_sidebar',
]:
    assert key in v4_post_views, key
for view_value in ['"0"', '"7"', '"1"', '"4"', '"5"', '"2"', '"6"', '"3"']:
    assert view_value in v4_post_views, view_value
assert 'MorpheSettingsV4Catalog.V4_SAVED_VIEWS_FRAGMENT' in v4_post_views
assert 'openMorpheFragment(' in v4_post_views
assert 'openBoostHelper("h1")' not in v4_post_views
assert 'setBoostStaticBoolean("g")' in v4_post_views
assert 'setBoostStaticBoolean("i")' in v4_post_views
assert 'setBoostStaticString("c", enabled ? "r/" : "")' in v4_post_views
assert 'applyBooleanPreference(preferences, key, value, refreshFlag);' in v4_post_views
assert 'applyDefaultViewPreference(' in v4_post_views
assert 'applyPreviewLinesPreference(preferences, current);' in v4_post_views
assert 'private void showDefaultViewDialog()' in v4_post_views
assert 'previewLinesSeekBar.setMax(100);' in v4_post_views
assert 'BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(' in v4_post_views
assert 'BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);' in v4_post_views
for forbidden_call in [
    'getActivity()',
    'getFragmentManager()',
    'getParentFragmentManager()',
    'setTargetFragment(',
]:
    assert forbidden_call not in v4_post_views, forbidden_call
assert 'androidx.compose' not in v4_post_views
assert 'ComposeView' not in v4_post_views

assert 'MORPHE_BOOST_SETTINGS_V4_SAVED_VIEWS_ISSUE106_V1' in v4_saved_views
assert 'com.rubenmayayo.reddit.VIEW_PER_SUBSCRIPTION' in v4_saved_views
assert 'savedViews.getAll()' in v4_saved_views
assert 'applySavedView(' in v4_saved_views
assert 'removeSavedView(savedViews, entry.key);' in v4_saved_views
assert 'clearSavedViews(savedViews);' in v4_saved_views
assert 'savedViews.edit().putInt(key, viewType).apply();' in v4_saved_views
assert 'savedViews.edit().remove(key).apply();' in v4_saved_views
assert 'savedViews.edit().clear().apply();' in v4_saved_views
assert 'No saved views yet' in v4_saved_views
assert 'showViewTypeDialog(entry)' in v4_saved_views
assert 'addAddSavedViewCard(entriesHost);' in v4_saved_views
assert 'private void showAddSavedViewDialog()' in v4_saved_views
assert 'private String normalizeSavedViewKey(' in v4_saved_views
assert '"Add saved view"' in v4_saved_views
assert '"Custom feed"' in v4_saved_views
assert 'SavedViewsActivity' not in v4_saved_views
for forbidden_call in [
    'getActivity()',
    'getFragmentManager()',
    'getParentFragmentManager()',
    'setTargetFragment(',
]:
    assert forbidden_call not in v4_saved_views, forbidden_call
assert 'androidx.compose' not in v4_saved_views
assert 'ComposeView' not in v4_saved_views

assert 'extends Fragment' in v4_fonts
assert 'MORPHE_BOOST_SETTINGS_V4_FONTS_ISSUE106_V1' in v4_fonts
assert 'MORPHE_BOOST_SETTINGS_V4_FONT_PREVIEWS_ISSUE106_V2' in v4_fonts
assert 'MORPHE_BOOST_SETTINGS_V4_FONT_SPECIMEN_ISSUE106_V3' in v4_fonts
assert 'PreferenceManager.getDefaultSharedPreferences(context)' in v4_fonts
for key in [
    'pref_title_font',
    'pref_font_size_title',
    'pref_comments_font',
    'pref_font_size',
]:
    assert key in v4_fonts, key
for font_value in [
    'sans-serif-thin',
    'sans-serif-condensed-light',
    'sans-serif-condensed',
    'serif-monospace',
    'sans-serif-smallcaps',
    'RobotoSlab-Regular.ttf',
]:
    assert font_value in v4_fonts, font_value
for size_value in ['XSmall', 'Small', 'Medium', 'Large', 'XLarge', 'XXLarge']:
    assert f'"{size_value}"' in v4_fonts, size_value
assert 'private void showFontDialog(' in v4_fonts
assert 'private void showSizeDialog(' in v4_fonts
assert 'private void updatePreviewAndSummaries()' in v4_fonts
assert 'addSectionLabel(content, "Live preview")' in v4_fonts
assert 'private void addPreviewDivider(LinearLayout parent)' in v4_fonts
assert 'previewCanvas.setBackground(MorpheSettingsV4Theme.rounded(' in v4_fonts
assert '"Updates as you choose"' not in v4_fonts
assert '"LIVE PREVIEW"' not in v4_fonts
assert '"POST PREVIEW"' not in v4_fonts
assert '"COMMENT PREVIEW"' not in v4_fonts
assert 'previewExampleCard' not in v4_fonts
assert 'previewDrawable' not in v4_fonts
assert 'applyPreviewTypography(' in v4_fonts
assert 'preview.setTypeface(typefaceFor(fontValue));' in v4_fonts
assert 'preview.setTextSize(previewSizeSp(sizeValue, title));' in v4_fonts
assert 'applyFontPreference(preferences, key, value);' in v4_fonts
assert 'private static void markNativeFontCacheDirty(String key)' in v4_fonts
assert 'Class.forName("id.b")' in v4_fonts
assert '.remove(KEY_TITLE_FONT)' in v4_fonts
assert 'BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(' in v4_fonts
assert 'BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);' in v4_fonts
assert 'applyDependentRowState(' in v4_post_views
assert 'row.setAlpha(enabled ? 1.0f : 0.44f);' in v4_post_views
for forbidden_call in [
    'getActivity()',
    'getFragmentManager()',
    'getParentFragmentManager()',
    'setTargetFragment(',
]:
    assert forbidden_call not in v4_fonts, forbidden_call
assert 'androidx.compose' not in v4_fonts
assert 'ComposeView' not in v4_fonts

category_ids = [
    "appearance_layout",
    "posts_comments",
    "navigation",
    "media_links",
    "search_filters",
    "notifications",
    "data_storage",
    "account_privacy",
    "app_legacy",
    "about",
]
for category_id in category_ids:
    assert f'"{category_id}"' in v4_catalog, category_id

for name in [
    "PreferenceFragmentAppearanceCompat",
    "PreferenceFragmentAccountCompat",
]:
    assert name in v4_catalog, name

native_leaf_mappings = {
    "PreferenceFragmentPostsCompat": "Posts",
    "PreferenceFragmentCommentsCompat": "Comments",
    "PreferenceFragmentBottomNavigationCompat": "BottomNavigation",
    "PreferenceFragmentDrawerCompat": "Drawer",
    "PreferenceFragmentMediaCompat": "Media",
    "PreferenceFragmentLinksCompat": "Links",
    "PreferenceFragmentSearchCompat": "Search",
    "PreferenceFragmentFiltersCompat": "Filters",
    "PreferenceFragmentMessagesCompat": "Messages",
    "PreferenceFragmentPrivacyCompat": "Privacy",
    "PreferenceFragmentGeneralCompat": "General",
    "PreferenceFragmentMiscCompat": "Misc",
    "PreferenceFragmentAboutCompat": "About",
}
for legacy_fragment, native_page in native_leaf_mappings.items():
    assert (
        f'legacyDestination.endsWith("{legacy_fragment}")'
        in v4_native_pages
    ), legacy_fragment
    assert f'return PREFIX + "{native_page}";' in v4_native_pages, native_page

native_redirects = {
    "PreferenceFragmentViewsCompat": "V4_POST_VIEWS_FRAGMENT",
    "PreferenceFragmentToolbarCompat": "V4_TOOLBAR_FRAGMENT",
}
for legacy_fragment, destination in native_redirects.items():
    assert (
        f'legacyDestination.endsWith("{legacy_fragment}")'
        in v4_native_pages
    ), legacy_fragment
    assert (
        f'return MorpheSettingsV4Catalog.{destination};'
        in v4_native_pages
    ), destination

for retired_catalog_fragment in [
    "PreferenceFragmentFontsCompat",
    "PreferenceFragmentDataSavingCompat",
]:
    assert retired_catalog_fragment not in v4_catalog, retired_catalog_fragment

for native_destination in [
    "V4_FONTS_FRAGMENT",
    "V4_DATA_STORAGE_FRAGMENT",
]:
    assert native_destination in v4_catalog, native_destination

assert 'morphe_boost_settings_skeleton' in v4_catalog
assert 'buildSearchIndex(Context context)' in v4_catalog
assert 'resources.getXml(resourceId)' in v4_catalog
assert 'attributeText(resources, parser, "title")' in v4_catalog
assert 'attributeText(resources, parser, "summary")' in v4_catalog
assert 'attributeText(resources, parser, "key")' in v4_catalog
assert 'BACKUP_ACTIVITY' in v4_catalog
assert 'V4_APPEARANCE_FRAGMENT' in v4_catalog
assert 'V4_APP_ICON_FRAGMENT' in v4_catalog
assert 'V4_POST_VIEWS_FRAGMENT' in v4_catalog
assert 'V4_SAVED_VIEWS_FRAGMENT' in v4_catalog
assert 'V4_FONTS_FRAGMENT' in v4_catalog
assert 'CLASSIC_APPEARANCE_FRAGMENT' in v4_catalog
assert 'addV4AppearanceSearchItems(result, seen);' in v4_catalog
assert 'addV4PostViewsSearchItems(result, seen);' in v4_catalog
assert 'addV4FontsSearchItems(result, seen);' in v4_catalog
assert 'Leaf.fragment("Appearance", "Dynamic color, app icon, and system bars"' in v4_catalog
assert 'Leaf.fragment("Post views", "Cards, lists, thumbnails, and density"' in v4_catalog
assert 'Leaf.fragment("Fonts", "Font family, size, and style"' in v4_catalog
assert '"Appearance & layout · Fonts"' in v4_catalog
assert 'PreferenceFragmentFontsCompat' not in v4_catalog
assert 'PreferenceFragmentViewsCompat' not in v4_catalog
assert 'Leaf.fragment("Classic theme editor", "Legacy Boost palettes and per-color customization"' not in v4_catalog
assert 'MORPHE_BOOST_SETTINGS_V14_EDITOR_SYSTEM_ISSUE106_V1' in v4_native_pages
assert 'MORPHE_BOOST_SETTINGS_V14_NO_COMPILE_ONLY_UI_ABI_ISSUE106_V1' in v4_native_pages
assert 'MORPHE_BOOST_SETTINGS_V14_3_MATERIAL_TOGGLE_LAST_ISSUE106_V1' in v4_native_pages
assert 'preview toggle here is self-referential clutter' not in v4_native_pages
assert 'showColorPatternEditor();' in v4_native_pages
assert '{"Theme mode",' not in v4_catalog
assert '{"Customize colors",' not in v4_catalog
assert 'pref_colored_status_bar' in v4_catalog
assert 'pref_colored_nav_bar' in v4_catalog

assert 'system_accent1_200' in v4_theme
assert 'system_accent1_600' in v4_theme
assert 'system_accent2_200' in v4_theme
assert 'system_accent2_600' in v4_theme
assert 'system_accent3_200' in v4_theme
assert 'system_accent3_600' in v4_theme
assert 'system_neutral1_900' in v4_theme
assert 'system_neutral2_700' in v4_theme
assert 'KEY_DYNAMIC_COLORS = "pref_dynamic_colors"' in v4_theme
assert '.getBoolean(KEY_DYNAMIC_COLORS, false)' in v4_theme
assert 'TypedArray' not in v4_theme
assert 'Accent navigationAccent()' in v4_theme
assert 'hashCode()' not in v4_theme
assert 'tokens.navigationAccent()' in v4_fragment
assert 'tokens.accentFor(' not in v4_fragment
assert 'MORPHE_BOOST_SETTINGS_V4_SUBTLE_COLOR_ISSUE106_V1' in v4_fragment
assert 'MORPHE_BOOST_SETTINGS_V4_SYSTEM_BARS_ISSUE106_V1' in v4_fragment
assert 'MORPHE_BOOST_SETTINGS_V4_SYSTEM_BAR_OWNER_ISSUE106_V2' in v4_fragment
assert 'MORPHE_BOOST_SETTINGS_V14_COHERENT_UI_ISSUE106_V1' in v14_ui
assert 'tokens.dark ? 0.06f : 0.08f' in v4_fragment
assert 'styleSystemBars(activity);' in v4_fragment
assert 'BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(' in v4_fragment
assert 'BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);' in v4_fragment
assert 'window.setStatusBarColor(tokens.background);' not in v4_fragment
assert 'MORPHE_BOOST_SETTINGS_V4_SYSTEM_BAR_OWNER_ISSUE106_V2' in system_bars
assert 'MORPHE_BOOST_SETTINGS_V4_NAVIGATION_SURFACE_ISSUE106_V3' in system_bars
assert 'MORPHE_SETTINGS_V4_SYSTEM_BARS.get(activity)' in system_bars
assert 'applyMorpheSettingsV4SystemBarsNow(activity, v4State);' in system_bars
assert 'installStatusBarSurface(decor, statusHeight, state.color);' in system_bars
assert 'installMorpheSettingsV4NavigationBarSurface(' in system_bars
assert 'surface.setTag(MORPHE_SETTINGS_V4_NAVIGATION_SURFACE_MARKER);' in system_bars
assert 'window.setNavigationBarColor(state.color);' in system_bars
assert 'removeMorpheSettingsV4NavigationBarSurface(decor);' in system_bars
assert 'window.setNavigationBarColor(Color.TRANSPARENT);' in system_bars
assert 'window.setNavigationBarContrastEnforced(false);' in system_bars
assert 'RippleDrawable' in v4_theme
assert 'primaryContainer' in v4_theme

assert "settingsHeaderOnCreatePreferencesFingerprint" in settings
assert "settingsActivityOnCreateFingerprint" in settings
assert "SettingsActivityCompat\\$HeaderFragment" in settings
assert 'SettingsActivityCompat;"' in settings
assert "SET_PREFERENCES_FROM_RESOURCE_REFERENCE" in settings
assert "GET_INTENT_REFERENCE" in settings
assert "val loadPreferencesIndex = indexOfFirstInstructionOrThrow" in settings
assert "val getIntentIndex = indexOfFirstInstructionOrThrow" in settings
assert "addInstructions(\n                loadPreferencesIndex," in settings
assert "addInstructions(\n                getIntentIndex," in settings
assert "$SETTINGS_LAYOUT_EXTENSION_DESCRIPTOR->resolveRootResource" in settings
assert "$SETTINGS_V4_EXTENSION_DESCRIPTOR->prepareIntent" in settings
assert "dependsOn(sharedExtensionPatch, boostMorpheSettingsResourcesPatch)" in settings
PY_CHECK

echo 'TOP_LEVEL_MORPHE_ENTRY=PASS'
echo 'LEGACY_V2_LAYOUT_DEFAULT_OFF=PASS'
echo 'SETTINGS_V4_TOGGLE_DEFAULT_ON=PASS'
echo 'SETTINGS_V4_V2_MIGRATION=PASS'
echo 'SETTINGS_V4_ACTIVITY_ROUTE=PASS'
echo 'SETTINGS_V4_MATERIAL3_VIEW_SHELL=PASS'
echo 'SETTINGS_V4_FULL_DYNAMIC_COLOR=PASS'
echo 'SETTINGS_V4_SUBTLE_DYNAMIC_COLOR=PASS'
echo 'SETTINGS_V4_SYSTEM_BARS_MATCH=PASS'
echo 'SETTINGS_V4_SYSTEM_BAR_OWNER=PASS'
echo 'SETTINGS_V4_NAVIGATION_SURFACE=PASS'
echo 'SETTINGS_V4_APPEARANCE_CONTROLS=PASS'
echo 'SETTINGS_V4_COMPACT_GROUPS=PASS'
echo 'SETTINGS_V4_SYSTEM_THEME=PASS'
echo 'SETTINGS_V4_APP_ICON_M3=PASS'
echo 'SETTINGS_V4_POST_VIEWS_CONTROLS=PASS'
echo 'SETTINGS_V4_SAVED_VIEWS_M3=PASS'
echo 'SETTINGS_V4_SAVED_VIEWS_ADD=PASS'
echo 'SETTINGS_V4_FONTS_M3=PASS'
echo 'SETTINGS_V4_FONT_PREVIEWS=PASS'
echo 'SETTINGS_V4_FONT_SPECIMEN=PASS'
echo 'SETTINGS_V4_TASK_HUBS=PASS'
echo 'SETTINGS_V4_SEARCH_INDEX=PASS'
echo 'SETTINGS_V4_CLASSIC_FALLBACK=PASS'
echo 'SETTINGS_V14_NATIVE_PAGES=PASS'
echo 'SETTINGS_V14_COHERENT_UI=PASS'
echo 'SETTINGS_V14_3_MATERIAL_TOGGLE_LAST=PASS'
echo 'SETTINGS_V4_MINIFIED_ANDROIDX_COMPAT=PASS'
echo 'SETTINGS_V4_NO_COMPOSE=PASS'
echo 'SETTINGS_V2_RESOURCE_FALLBACK=PASS'
echo 'CANONICAL_MORPHE_KEYS=PASS'
echo 'ADVANCED_DUPLICATE_REMOVED=PASS'
echo 'RESULT=MORPHE_ISSUE106_SETTINGS_CONTRACT_OK'
