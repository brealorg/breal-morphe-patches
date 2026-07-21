#!/usr/bin/env python3
"""Source contract for the reversible DEV-only settings binding runtime audit."""

import json
import os
import re
import unittest
from pathlib import Path


TEST_PATH = Path(__file__).resolve()
REPO_ROOT = Path(os.environ.get("MORPHE_REPO_ROOT", TEST_PATH.parents[2]))
FIXTURE = TEST_PATH.parent / "fixtures" / "boost_morphe_settings_bindings.json"
JAVA = (
    REPO_ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings"
    / "MorpheSettingsV4BindingRuntimeAuditFragment.java"
)
TOOL = REPO_ROOT / "tools/boost-morphe-settings-binding-runtime.sh"
REPORT_WRITER = REPO_ROOT / "tools/write-boost-morphe-settings-audit-report.py"


def string_array(source, name):
    match = re.search(
        rf"{name}\s*=\s*new String\[\]\s*\{{(.*?)\}};", source, re.S
    )
    if match is None:
        raise AssertionError(f"missing String array {name}")
    return re.findall(r'"([^"]*)"', match.group(1))


def int_array(source, name):
    match = re.search(
        rf"{name}\s*=\s*new int\[\]\s*\{{(.*?)\}};", source, re.S
    )
    if match is None:
        raise AssertionError(f"missing int array {name}")
    return [int(value) for value in re.findall(r"-?\d+", match.group(1))]


def method_block(source, signature):
    start = source.index(signature)
    end = source.find("\n    private static ", start + len(signature))
    return source[start:] if end < 0 else source[start:end]


def switch_string_returns(source, signature):
    block = method_block(source, signature)
    result = {}
    pending = []
    for line in block.splitlines():
        case = re.search(r'case "([^"]+)":', line)
        if case:
            pending.append(case.group(1))
        returned = re.search(r'return "([^"]+)";', line)
        if returned:
            for key in pending:
                result[key] = returned.group(1)
            pending = []
    return result


class MorpheSettingsBindingRuntimeSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(FIXTURE.read_text(encoding="utf-8"))
        cls.java = JAVA.read_text(encoding="utf-8")
        cls.tool = TOOL.read_text(encoding="utf-8")
        cls.report_writer = REPORT_WRITER.read_text(encoding="utf-8")

    def test_runtime_key_registry_exactly_matches_static_binding_manifest(self):
        specs = (
            self.manifest["appearance"]
            + self.manifest["post_views"]
            + self.manifest["fonts"]
        )
        expected_boolean = {spec["key"] for spec in specs if spec["type"] == "boolean"}
        expected_string = {spec["key"] for spec in specs if spec["type"] == "string"}
        expected_integer = {spec["key"] for spec in specs if spec["type"] == "integer"}
        self.assertEqual(expected_boolean, set(string_array(self.java, "BOOLEAN_KEYS")))
        self.assertEqual(expected_string, set(string_array(self.java, "STRING_KEYS")))
        self.assertEqual(expected_integer, set(string_array(self.java, "INTEGER_KEYS")))
        self.assertEqual(
            {
                spec["key"]
                for spec in specs
                if spec["type"] == "boolean" and spec["default"] is True
            },
            set(string_array(self.java, "BOOLEAN_DEFAULT_TRUE_KEYS")),
        )
        self.assertEqual(
            {
                spec["key"]
                for spec in specs
                if spec["type"] == "boolean" and spec["default"] is True
            },
            set(string_array(self.java, "BOOLEAN_DEFAULT_TRUE_KEYS")),
        )

    def test_runtime_exercises_every_declared_domain(self):
        self.assertEqual(2, self.manifest["schema"])
        self.assertEqual("appearance-layout", self.manifest["scope"])
        self.assertEqual(56, self.manifest["harness_version"])
        self.assertEqual(
            "reversible_dev_only",
            self.manifest["safety"]["classification"],
        )
        self.assertFalse(self.manifest["safety"]["network"])
        self.assertFalse(self.manifest["safety"]["destructive"])
        self.assertFalse(self.manifest["safety"]["secrets"])
        view_spec = next(
            spec for spec in self.manifest["post_views"] if spec["key"] == "pref_view"
        )
        integer_spec = next(
            spec
            for spec in self.manifest["post_views"]
            if spec["key"] == "pref_cards_preview_self_lines"
        )
        self.assertEqual(view_spec["domain"], string_array(self.java, "VIEW_VALUES"))
        self.assertEqual(self.manifest["font_domain"], string_array(self.java, "FONT_VALUES"))
        self.assertEqual(
            self.manifest["font_size_domain"],
            string_array(self.java, "FONT_SIZE_VALUES"),
        )
        self.assertEqual(
            self.manifest["saved_views"]["domain"],
            int_array(self.java, "SAVED_VIEW_VALUES"),
        )
        self.assertIn(
            f'INTEGER_MINIMUM = {integer_spec["domain"]["minimum"]};', self.java
        )
        self.assertIn(
            f'INTEGER_MAXIMUM = {integer_spec["domain"]["maximum"]};', self.java
        )
        self.assertIn(
            f'"{self.manifest["saved_views"]["preferences"]}"', self.java
        )
        self.assertEqual(
            [""]
            + [option["alias"] for option in self.manifest["app_icons"] if option["alias"]],
            string_array(self.java, "ICON_AUDIT_VALUES"),
        )
        self.assertIn("DOMAIN_ACTION_COUNT = 196", self.java)
        for probe in self.manifest["render_probes"]:
            self.assertIn(f'"{probe["name"]}"', self.java)
        self.assertIn('if ("pref_view".equals(key)) {\n            return "0";', self.java)
        self.assertIn('return "Medium";', self.java)
        self.assertIn('return "";', self.java)
        self.assertIn('snapshot.getBoolean("present:" + identity, false)', self.java)

    def test_runtime_app_icon_inventory_matches_static_binding_manifest(self):
        self.assertEqual(
            [option["resource"] for option in self.manifest["app_icons"]],
            string_array(self.java, "ICON_RESOURCES"),
        )
        self.assertEqual(
            [option["alias"] for option in self.manifest["app_icons"] if option["alias"]],
            string_array(self.java, "ICON_ALIASES"),
        )
        self.assertIn("PackageManager.MATCH_DISABLED_COMPONENTS", self.java)
        self.assertIn("enabledAliases <= 1", self.java)

    def test_effect_audit_maps_every_binding_to_the_native_consumer_and_cache(self):
        specs = (
            self.manifest["appearance"]
            + self.manifest["post_views"]
            + self.manifest["fonts"]
        )
        expected_consumers = {spec["key"]: spec["consumer"] for spec in specs}
        actual_consumers = switch_string_returns(
            self.java,
            "private static String nativeConsumerForKey(String key)",
        )
        self.assertEqual(expected_consumers, actual_consumers)

        cache_fields = {"d", "g", "h", "i"}
        expected_effects = {
            spec["key"]: next(
                (effect for effect in reversed(spec["side_effects"]) if effect in cache_fields),
                None,
            )
            for spec in specs
        }
        expected_effects = {
            key: effect for key, effect in expected_effects.items() if effect is not None
        }
        actual_effects = switch_string_returns(
            self.java,
            "private static String effectFieldForKey(String key)",
        )
        self.assertEqual(expected_effects, actual_effects)
        self.assertIn("AUDIT_ITEM_COUNT = 26", self.java)
        self.assertIn("EFFECT_AUDIT_RELOAD_COUNT = 26", self.java)

    def test_app_transaction_is_dev_only_reload_verified_and_restored(self):
        required = (
            'DEV_PACKAGE = "com.rubenmayayo.reddit.dev"',
            "if (!DEV_PACKAGE.equals(context.getPackageName()))",
            "MORPHE_BINDING_AUDIT_REFUSED_NON_DEV_PACKAGE",
            'SCOPE_APPEARANCE_LAYOUT = "appearance-layout"',
            "SNAPSHOT_LOCK_TIMEOUT_MS",
            '"MORPHE_BINDING_AUDIT_FAIL phase=write active_lock"',
            'PHASE_RECOVER_FORCE = "recover_force"',
            "recoverIfNeeded(context, nonce, scope, true)",
            "MORPHE_BINDING_AUDIT_ORPHAN_RECOVERY_FORCED",
            "createSnapshot(",
            "exerciseFullDomainsViaUiActions(",
            "exerciseBooleanDomain(defaults, snapshot, key)",
            "exerciseStringDomain(defaults, snapshot, key)",
            "exerciseIntegerDomain(",
            "exerciseSavedViewDomain(savedViews, snapshot)",
            "exerciseAppIconDomain(context, snapshot)",
            "verifyPersistedAuditValues(context, snapshot)",
            "verifyNativeConsumersAfterReload(context, snapshot)",
            "MorpheSettingsV4AppearanceFragment.applyDynamicColors(",
            "MorpheSettingsV4AppearanceFragment.applySystemBarPreference(",
            "MorpheSettingsV4PostViewsFragment.applyBooleanPreference(",
            "MorpheSettingsV4PostViewsFragment.applySubredditPrefix(",
            "MorpheSettingsV4PostViewsFragment.applyDefaultViewPreference(",
            "MorpheSettingsV4PostViewsFragment.applyPreviewLinesPreference(",
            "MorpheSettingsV4FontsFragment.applyFontPreference(",
            "MorpheSettingsV4SavedViewsFragment.applySavedView(",
            "MorpheSettingsV4AppIconFragment.applyIconSelection(",
            "MorpheSettingsV4FontsFragment.applyPreviewTypography(",
            "MorpheSettingsV4PostViewsFragment.applyDependentRowState(",
            "BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(",
            "invokeNativeConsumer(nativeConsumerForKey(key))",
            "MORPHE_DOMAIN_AUDIT_OK items=",
            "MORPHE_RENDER_AUDIT_OK count=",
            "MORPHE_DOMAIN_AUDIT_RELOAD_OK count=",
            "snapshotIconComponents(context, editor)",
            '"icon_state:" + alias',
            "restoreIconComponents(context, snapshot)",
            "verifyExpectedAuditIcon(context, snapshot)",
            "restoreOriginalValues(context, snapshot)",
            "MORPHE_BINDING_AUDIT_RELOAD_OK",
            "MORPHE_BINDING_AUDIT_RESTORE_OK",
            "MORPHE_BOOST_SETTINGS_APPEARANCE_LAYOUT_AUDIT_V56_APP_PASS",
        )
        for snippet in required:
            self.assertIn(snippet, self.java)
        for forbidden in (
            "exerciseBooleanBindings(",
            "exerciseStringBindings(",
            "exerciseIntegerBindings(",
            "commitBoolean(",
            "commitString(",
            "commitInteger(",
        ):
            self.assertNotIn(forbidden, self.java)

    def test_render_probes_share_the_same_helpers_as_the_visible_ui(self):
        fonts = (
            JAVA.parent / "MorpheSettingsV4FontsFragment.java"
        ).read_text(encoding="utf-8")
        post_views = (
            JAVA.parent / "MorpheSettingsV4PostViewsFragment.java"
        ).read_text(encoding="utf-8")
        self.assertIn("applyPreviewTypography(", fonts)
        self.assertIn("preview.setTypeface(typefaceFor(fontValue))", fonts)
        self.assertIn("preview.setTextSize(previewSizeSp(sizeValue, title))", fonts)
        self.assertIn("applyDependentRowState(", post_views)
        self.assertIn("row.setAlpha(enabled ? 1.0f : 0.44f)", post_views)
        self.assertIn("RENDER_PROBE_COUNT = 6", self.java)
        self.assertIn(
            "MORPHE_BOOST_SETTINGS_AUDIT_SYSTEM_BARS_ANDROID15_V56_1",
            self.java,
        )
        self.assertIn("assertSettingsSystemBarRendering(", self.java)
        self.assertIn(
            "MORPHE_BOOST_COMMENTS_SYSTEM_BAR_SURFACE_V1",
            self.java,
        )
        self.assertIn(
            "MORPHE_BOOST_SETTINGS_V4_NAVIGATION_SURFACE_ISSUE106_V3",
            self.java,
        )
        self.assertIn("Build.VERSION.SDK_INT >= 35", self.java)
        self.assertNotIn(
            "window.getStatusBarColor() != tokens.background",
            self.java,
        )

    def test_runtime_entry_avoids_fragment_activity_api_missing_from_boost(self):
        self.assertNotIn("getActivity()", self.java)
        self.assertNotIn("requireActivity()", self.java)
        self.assertIn(
            "Activity activity = activityFromContext(inflater.getContext());",
            self.java,
        )
        self.assertIn("current instanceof ContextWrapper", self.java)

    def test_shell_orchestrator_fails_closed_and_never_mutates_normal_boost(self):
        required = (
            "MORPHE_RUNTIME_AUDIT_MUTATE_DEV",
            "--scope appearance-layout",
            "tools/boost-adb-serial.sh",
            "ADB=(env -u ANDROID_SERIAL adb -s \"$SERIAL\")",
            "AUDIT_ACTIVE=1",
            "another settings audit owns",
            "flock -n 9",
            "MORPHE_RUNTIME_AUDIT_RECOVER_ORPHANED_DEV",
            "--recover-orphaned",
            "start_phase recover_force",
            "MORPHE_BOOST_SETTINGS_AUDIT_V56_ORPHAN_RECOVERY_PASS",
            "DEV_AUDIT_HOST_LOCK=PASS",
            "trap recover_on_exit EXIT",
            "start_phase write",
            "MorpheSettingsV4BindingRuntimeAuditFragment",
            "userId\\|appId",
            'shell pm list packages -U "$DEV_PACKAGE"',
            "could not resolve Boost DEV uid from userId, appId, or pm -U",
            "pkgFlags=.*DEBUGGABLE",
            "--debuggable-dev",
            'shell am start -W',
            'rg -q "^uid=${DEV_UID}\\\\("',
            "DEV_DEBUGGABLE=PASS",
            "DEV_RUN_AS_UID=PASS",
            "DEV_AUDIT_GATEWAY_LAUNCH=ARMED",
            "shell am force-stop \"$DEV_PACKAGE\"",
            "start_phase verify_restore",
            'logcat --uid="$DEV_UID"',
            "MORPHE_BINDING_AUDIT_RELOAD_OK",
            "MORPHE_BINDING_AUDIT_RESTORE_OK",
            "MORPHE_DOMAIN_AUDIT_OK items=26 actions=196",
            "MORPHE_RENDER_AUDIT_OK count=6",
            "MORPHE_DOMAIN_AUDIT_RELOAD_OK count=26",
            "DEV_ALL_REGISTERED_DOMAINS_VIA_UI_ACTIONS=PASS",
            "DEV_APPEARANCE_LAYOUT_NATIVE_CONSUMERS=PASS",
            "DEV_RENDERED_EFFECT_PROBES=PASS",
            "DEV_APP_ICON_DURABLE_SNAPSHOT=PASS",
            "DEV_NATIVE_CONSUMERS_AND_ICON_AFTER_COLD_RELOAD=PASS",
            "tools/write-boost-morphe-settings-audit-report.py",
            "STRUCTURED_AUDIT_REPORT=PASS",
            "RESULT=MORPHE_BOOST_SETTINGS_APPEARANCE_LAYOUT_AUDIT_V56_RUNTIME_PASS",
            "NORMAL_BOOST_UNTOUCHED=PASS",
        )
        for snippet in required:
            self.assertIn(snippet, self.tool)
        forbidden = (
            'force-stop "$NORMAL_PACKAGE"',
            "pm clear",
            " uninstall ",
            " install ",
            'shell run-as "$DEV_PACKAGE" /system/bin/am start',
        )
        for snippet in forbidden:
            self.assertNotIn(snippet, self.tool)

        for snippet in (
            "manifest_sha256",
            '"items": items',
            '"render_probes": render_probes',
            '"apk_sha256": args.apk_sha256',
            '"status": status',
        ):
            self.assertIn(snippet, self.report_writer)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(
        MorpheSettingsBindingRuntimeSourceContract
    )
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if result.wasSuccessful():
        relative = TEST_PATH.relative_to(REPO_ROOT)
        print(f"SOURCE_CONTRACT=PASS path={relative}")
        print("RESULT=MORPHE_ISSUE106_SETTINGS_BINDING_RUNTIME_SOURCE_OK")
    raise SystemExit(0 if result.wasSuccessful() else 1)
