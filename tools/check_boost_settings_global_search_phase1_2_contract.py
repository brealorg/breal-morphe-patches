#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings"
DEPTH = ROOT / "tools/contracts/boost-settings-ux-depth-v1.json"

helper = (SETTINGS / "MorpheSettingsV4Search.java").read_text(encoding="utf-8")
root = (SETTINGS / "MorpheSettingsV4Fragment.java").read_text(encoding="utf-8")
native = (SETTINGS / "MorpheSettingsV4NativePages.java").read_text(encoding="utf-8")

search = json.loads(DEPTH.read_text(encoding="utf-8"))["global_search"]
assert search == {
    "scope": "all_settings",
    "root_surface": "inline_search_field",
    "deeper_morphe_pages": "top_app_bar_action",
    "navigation_mode": "platform_dialog_overlay",
    "androidx_navigation_calls": "forbidden",
    "back_callback_priority": "platform_overlay",
    "single_back_behavior": "dismiss_overlay_and_ime",
    "opens_with_keyboard": True,
    "result_back_stack": "returns_to_originating_page",
}

assert "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_ISSUE121_PHASE1_2_V1" in helper
assert (
    "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_FRAGMENT_ABI_COMPAT_"
    in helper
)
assert (
    "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_PLATFORM_DIALOG_"
    in helper
)
assert (
    "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_SINGLE_BACK_DISMISS_"
    in helper
)
assert "fragment.getActivity()" not in helper
assert "fragment.getFragmentManager()" not in helper
assert "beginTransaction()" not in helper
assert "FragmentManager" not in helper
assert "FragmentTransaction" not in helper
assert "new Dialog(activity)" in helper
assert "dialog.setContentView(shell)" in helper
assert "dialog.dismiss()" in helper
assert "SOFT_INPUT_STATE_ALWAYS_VISIBLE" in helper
assert "Build.VERSION.SDK_INT >= 33" in helper
assert "OnBackInvokedDispatcher.PRIORITY_OVERLAY" in helper
assert "registerOnBackInvokedCallback" in helper
assert "unregisterOnBackInvokedCallback" in helper
assert "KeyEvent.KEYCODE_BACK" in helper
assert "hideSoftInputFromWindow" in helper
assert "dismissSearch(dialog, searchField)" in helper
assert "view -> dismissSearch(dialog, null)" in helper
assert "MorpheSettingsV4Catalog.buildSearchIndex(context)" in helper
assert "Search all settings" in helper
assert "SHOW_AS_ACTION_ALWAYS" in helper
assert "EXTRA_OPEN_SEARCH" in helper
assert "focusSearchField();" in root
assert "InputMethodManager.SHOW_IMPLICIT" in root
assert "!PAGE_ROOT.equals(page)" in root
assert "arguments.containsKey(ARGUMENT_PAGE)" in root
assert "navigationFromArguments = true;" in root
assert "!navigationFromArguments && activity != null" in root
assert "MorpheSettingsV4Search.prepareMenu(this, menu, true);" in native
assert "MorpheSettingsV4Search.handleMenuItem(this, item)" in native

PLAIN = [
    "MorpheSettingsV4AppIconFragment.java",
    "MorpheSettingsV4AppearanceFragment.java",
    "MorpheSettingsV4DataStorageFragment.java",
    "MorpheSettingsV4DownloadsFragment.java",
    "MorpheSettingsV4FontsFragment.java",
    "MorpheSettingsV4PostViewsFragment.java",
    "MorpheSettingsV4SavedViewsFragment.java",
    "MorpheSettingsV4ToolbarFragment.java",
    "MorpheSettingsFragment.java",
    "MorpheSettingsHubFragment.java",
]
for filename in PLAIN:
    text = (SETTINGS / filename).read_text(encoding="utf-8")
    assert "MorpheSettingsV4Search.prepareMenu(this, menu, true);" in text, filename
    assert "MorpheSettingsV4Search.handleMenuItem(this, item)" in text, filename

assert root.count("addSearchField(content);") == 1
assert "MorpheSettingsV4Search.prepareMenu" in root

print("GLOBAL_SEARCH_SCOPE=ALL_SETTINGS")
print("ROOT_SEARCH_SURFACE=INLINE_FIELD")
print("DEEP_PAGE_SEARCH_SURFACE=TOP_APP_BAR_ACTION")
print("SEARCH_OPENS_WITH_KEYBOARD=PASS")
print("SEARCH_BACK_STACK_RETURNS_TO_ORIGIN=PASS")
print("FRAGMENT_ACTIVITY_ABI_COMPAT=PASS")
print("SEARCH_NAVIGATION_MODE=PLATFORM_DIALOG_OVERLAY")
print("ANDROIDX_NAVIGATION_CALLS=ABSENT")
print("SEARCH_SINGLE_BACK_DISMISS=PASS")
print("BACK_CALLBACK_PRIORITY=PLATFORM_OVERLAY")
print("MORPHE_OWNED_PAGE_COVERAGE_COUNT=12")
print("RESULT=MORPHE_ISSUE121_SETTINGS_GLOBAL_SEARCH_PHASE1_2_CONTRACT_PASS")
