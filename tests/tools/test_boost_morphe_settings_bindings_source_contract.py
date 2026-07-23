#!/usr/bin/env python3
"""Exhaustive source contract for Morphe Settings v4 preference bindings."""

import json
import os
import re
import unittest
from collections import Counter
from pathlib import Path


TEST_PATH = Path(__file__).resolve()
REPO_ROOT = Path(os.environ.get("MORPHE_REPO_ROOT", TEST_PATH.parents[2]))
FIXTURE = TEST_PATH.parent / "fixtures" / "boost_morphe_settings_bindings.json"
JAVA_ROOT = Path(
    os.environ.get(
        "MORPHE_JAVA_ROOT",
        REPO_ROOT
        / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings",
    )
)


def compact(text):
    return re.sub(r"\s+", " ", text).strip()


def string_constants(text):
    return dict(
        re.findall(
            r"private static final String (KEY_[A-Z0-9_]+)\s*=\s*\"([^\"]*)\";",
            text,
            re.S,
        )
    )


def string_array(text, name):
    match = re.search(
        rf"{name}\s*=\s*new String\[\]\s*\{{(.*?)\}};", text, re.S
    )
    if match is None:
        raise AssertionError(f"missing String array {name}")
    return re.findall(r'\"([^\"]*)\"', match.group(1))


def int_array(text, name):
    match = re.search(
        rf"{name}\s*=\s*new int\[\]\s*\{{(.*?)\}};", text, re.S
    )
    if match is None:
        raise AssertionError(f"missing int array {name}")
    return [int(value) for value in re.findall(r"-?\d+", match.group(1))]


class MorpheSettingsBindingsSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(FIXTURE.read_text(encoding="utf-8"))
        cls.sources = {}
        for short_name in (
            "Appearance",
            "PostViews",
            "Fonts",
            "AppIcon",
            "SavedViews",
        ):
            path = JAVA_ROOT / f"MorpheSettingsV4{short_name}Fragment.java"
            cls.sources[short_name] = path.read_text(encoding="utf-8")
        cls.compact_sources = {
            name: compact(source) for name, source in cls.sources.items()
        }

    def assert_contains(self, source_name, snippet):
        self.assertIn(compact(snippet), self.compact_sources[source_name])

    def assert_occurs_once(self, source_name, snippet):
        self.assertEqual(
            1,
            self.compact_sources[source_name].count(compact(snippet)),
            snippet,
        )

    def assert_exact_key_set(self, source_name, specs):
        expected = {spec["constant"]: spec["key"] for spec in specs}
        actual = string_constants(self.sources[source_name])
        self.assertEqual(expected, actual)
        literal_keys = set(re.findall(r'\"(pref_[^\"]+)\"', self.sources[source_name]))
        self.assertEqual(set(expected.values()), literal_keys)

    def assert_read(self, source_name, spec):
        constant = spec["constant"]
        default = spec["default"]
        if spec["type"] == "boolean":
            literal = "true" if default else "false"
            self.assert_contains(
                source_name, f"preferences.getBoolean({constant}, {literal})"
            )
        elif spec["type"] == "integer":
            self.assert_contains(
                source_name, f"preferences.getInt({constant}, {default})"
            )
        else:
            self.assert_contains(
                source_name,
                f'preferences.getString({constant}, "{default}")',
            )

    def test_appearance_rows_have_exact_read_write_and_effect_bindings(self):
        specs = self.manifest["appearance"]
        self.assert_exact_key_set("Appearance", specs)
        for spec in specs:
            self.assert_read("Appearance", spec)

        self.assert_occurs_once("Appearance", "checked -> updateDynamicColors(checked)")
        self.assert_contains(
            "Appearance", "applyDynamicColors(preferences, enabled)"
        )
        self.assert_contains(
            "Appearance",
            "preferences.edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply()",
        )
        self.assert_contains("Appearance", 'setBoostStaticBoolean("i")')
        self.assert_occurs_once(
            "Appearance",
            "checked -> updateSystemBarPreference( KEY_COLORED_STATUS_BAR, checked )",
        )
        self.assert_occurs_once(
            "Appearance",
            "checked -> updateSystemBarPreference( KEY_COLORED_NAV_BAR, checked )",
        )
        self.assert_contains(
            "Appearance", "applySystemBarPreference(preferences, key, enabled)"
        )
        self.assert_contains(
            "Appearance", "preferences.edit().putBoolean(key, enabled).apply()"
        )
        self.assert_contains("Appearance", 'setBoostStaticBoolean("h")')
        self.assert_contains("Appearance", "activity.recreate()")
        self.assert_contains(
            "Appearance", "BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars("
        )

    def test_post_view_rows_have_exact_read_write_and_effect_bindings(self):
        specs = self.manifest["post_views"]
        self.assert_exact_key_set("PostViews", specs)
        self.assertEqual(
            self.manifest["post_views"][0]["domain"],
            string_array(self.sources["PostViews"], "VIEW_VALUES"),
        )
        actual_reads = Counter(
            re.findall(
                r"preferences\.get(?:Boolean|String|Int)\(\s*(KEY_[A-Z0-9_]+)\s*,",
                self.compact_sources["PostViews"],
            )
        )
        expected_reads = Counter(spec["constant"] for spec in specs)
        expected_reads["KEY_DEFAULT_VIEW"] += 1
        expected_reads["KEY_REMEMBER_PER_COMMUNITY"] += 1
        expected_reads["KEY_CARDS_PREVIEW_TEXT"] += 1
        self.assertEqual(expected_reads, actual_reads)

        for spec in specs:
            self.assert_read("PostViews", spec)
            constant = spec["constant"]
            writer = spec["writer"]
            effects = spec["side_effects"]
            if writer == "boolean":
                flag = next((value for value in effects if value in {"g", "h", "i"}), None)
                flag_literal = f'"{flag}"' if flag else "null"
                self.assert_occurs_once(
                    "PostViews",
                    f"putBoolean({constant}, checked, {flag_literal})",
                )
            elif writer == "subreddit_prefix":
                self.assert_contains("PostViews", "this::updateSubredditPrefix")
            elif writer == "view":
                self.assert_contains(
                    "PostViews",
                    "applyDefaultViewPreference( preferences, VIEW_VALUES[choice] )",
                )
            elif writer == "preview_lines":
                self.assert_contains(
                    "PostViews",
                    "applyPreviewLinesPreference(preferences, current)",
                )

        self.assert_contains(
            "PostViews", "preferences.edit().putBoolean(key, value).apply()"
        )
        self.assert_contains(
            "PostViews",
            "applyBooleanPreference(preferences, key, value, refreshFlag)",
        )
        self.assert_contains(
            "PostViews",
            "preferences.edit().putBoolean(KEY_SUBREDDIT_PREFIX, enabled).apply()",
        )
        self.assert_contains(
            "PostViews", 'setBoostStaticString("c", enabled ? "r/" : "")'
        )
        self.assert_contains("PostViews", 'setBoostStaticBoolean("i")')
        self.assert_contains("PostViews", "previewLinesSeekBar.setMax(100)")
        self.assert_contains(
            "PostViews", "setRowEnabled(manageSavedViewsRow, checked)"
        )
        self.assert_contains(
            "PostViews", "setPreviewLinesEnabled(checked)"
        )

    def test_font_rows_have_exact_read_write_domain_and_cache_bindings(self):
        specs = self.manifest["fonts"]
        self.assert_exact_key_set("Fonts", specs)
        self.assertEqual(
            self.manifest["font_domain"],
            string_array(self.sources["Fonts"], "FONT_VALUES"),
        )
        self.assertEqual(
            self.manifest["font_size_domain"],
            string_array(self.sources["Fonts"], "SIZE_VALUES"),
        )
        actual_constant_reads = Counter(
            re.findall(
                r"preferences\.getString\(\s*(KEY_[A-Z0-9_]+)\s*,",
                self.compact_sources["Fonts"],
            )
        )
        self.assertEqual(
            Counter({spec["constant"]: 2 for spec in specs}),
            actual_constant_reads,
        )

        for spec in specs:
            self.assert_read("Fonts", spec)
            dialog = "showFontDialog" if spec["writer"] == "font" else "showSizeDialog"
            self.assert_occurs_once(
                "Fonts", f"view -> {dialog}({spec['constant']},"
            )

        self.assert_contains(
            "Fonts", "preferences.edit().putString(key, value).apply()"
        )
        self.assert_contains(
            "Fonts", "applyFontPreference(preferences, key, value)"
        )
        self.assert_contains("Fonts", "markNativeFontCacheDirty(key)")
        self.assert_contains(
            "Fonts",
            "if (KEY_TITLE_SIZE.equals(key) || KEY_COMMENTS_SIZE.equals(key))",
        )
        self.assert_contains("Fonts", 'fieldName = "d"')
        self.assert_contains(
            "Fonts", "else if (KEY_TITLE_FONT.equals(key))"
        )
        self.assert_contains("Fonts", 'fieldName = "h"')
        self.assert_contains(
            "Fonts", 'Class.forName("id.b").getDeclaredField(fieldName)'
        )
        for spec in specs:
            self.assert_contains("Fonts", f".remove({spec['constant']})")

    def test_app_icon_options_are_complete_and_component_states_are_exclusive(self):
        actual = []
        for title, resource, alias in re.findall(
            r'new IconOption\("([^\"]+)", "([^\"]+)", (null|"[^\"]+")\)',
            self.sources["AppIcon"],
        ):
            actual.append(
                {
                    "title": title,
                    "resource": resource,
                    "alias": None if alias == "null" else alias.strip('"'),
                }
            )
        self.assertEqual(self.manifest["app_icons"], actual)
        self.assert_contains(
            "AppIcon",
            "candidate.alias.equals(selectedAlias) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED",
        )
        self.assert_contains(
            "AppIcon", "applyIconSelection(context, option.alias)"
        )
        self.assert_contains(
            "AppIcon", "packageManager.setComponentEnabledSetting( component, state, PackageManager.DONT_KILL_APP )"
        )
        self.assert_contains(
            "AppIcon", "packageManager.getComponentEnabledSetting( aliasComponent(context, option.alias) )"
        )

    def test_saved_views_use_the_native_store_domain_and_crud_operations(self):
        spec = self.manifest["saved_views"]
        source = self.sources["SavedViews"]
        self.assertIn(f'"{spec["preferences"]}"', source)
        self.assertEqual(spec["domain"], int_array(source, "VIEW_VALUES"))
        for key in spec["special_keys"]:
            self.assertIn(f'"{key}"', source)
        self.assertIn(f'"{spec["custom_feed_suffix"]}"', source)
        for operation in (
            "savedViews.getAll()",
            "applySavedView( savedViews, entry.key, VIEW_VALUES[choice] )",
            "removeSavedView(savedViews, entry.key)",
            "clearSavedViews(savedViews)",
            "savedViews.edit().putInt(key, viewType).apply()",
            "savedViews.edit().remove(key).apply()",
            "savedViews.edit().clear().apply()",
            "normalizeSavedViewKey(",
        ):
            self.assertIn(compact(operation), compact(source))


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(
        MorpheSettingsBindingsSourceContract
    )
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if result.wasSuccessful():
        print(f"SOURCE_CONTRACT=PASS path={TEST_PATH.relative_to(REPO_ROOT) if TEST_PATH.is_relative_to(REPO_ROOT) else TEST_PATH}")
        print("RESULT=MORPHE_ISSUE106_SETTINGS_BINDINGS_SOURCE_OK")
    raise SystemExit(0 if result.wasSuccessful() else 1)
