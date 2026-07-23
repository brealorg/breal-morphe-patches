#!/usr/bin/env python3
"""Contract tests for the structured Boost settings audit report."""

import json
import subprocess
import tempfile
import unittest
from pathlib import Path


TEST_PATH = Path(__file__).resolve()
REPO_ROOT = TEST_PATH.parents[2]
MANIFEST = TEST_PATH.parent / "fixtures" / "boost_morphe_settings_bindings.json"
WRITER = REPO_ROOT / "tools/write-boost-morphe-settings-audit-report.py"


def domain_count(spec, manifest):
    if spec["type"] == "boolean":
        return 2
    if spec["type"] == "integer":
        domain = spec["domain"]
        return domain["maximum"] - domain["minimum"] + 1
    if spec["key"] == "pref_view":
        return len(spec["domain"])
    if spec["writer"] == "font":
        return len(manifest["font_domain"])
    return len(manifest["font_size_domain"])


def complete_log():
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    lines = []
    for spec in (
        manifest["appearance"]
        + manifest["post_views"]
        + manifest["fonts"]
    ):
        lines.append(
            "MORPHE_AUDIT_ITEM_OK "
            f"key={spec['key']} "
            f"domain_count={domain_count(spec, manifest)} "
            f"consumer=id.b.{spec['consumer']} "
            "effect=direct_consumer render=not_rendered"
        )
    lines.append(
        "MORPHE_AUDIT_ITEM_OK key=saved_views domain_count=8 "
        "consumer=VIEW_PER_SUBSCRIPTION "
        "effect=canonical_named_preferences render=native_consumer"
    )
    lines.append(
        "MORPHE_AUDIT_ITEM_OK key=app_icon domain_count=5 "
        "consumer=PackageManager effect=launcher_alias "
        "render=launcher_components"
    )
    for probe, keys in (
        ("title_typography", "pref_title_font,pref_font_size_title"),
        ("comment_typography", "pref_comments_font,pref_font_size"),
        ("saved_views_enabled", "pref_view_per_subscription"),
        ("preview_lines_enabled", "pref_cards_preview_self"),
        ("default_view_summary", "pref_view"),
        ("settings_shell_system_bars", "-"),
    ):
        lines.append(
            "MORPHE_RENDER_AUDIT_ITEM_OK "
            f"probe={probe} keys={keys}"
        )
    lines.extend(
        (
            "MORPHE_BINDING_AUDIT_WRITE_OK",
            "MORPHE_DOMAIN_AUDIT_OK items=26 actions=196",
            "MORPHE_RENDER_AUDIT_OK count=6",
            "MORPHE_BINDING_AUDIT_RELOAD_OK",
            "MORPHE_DOMAIN_AUDIT_RELOAD_OK count=26",
            "MORPHE_BINDING_AUDIT_RESTORE_OK",
            "RESULT=MORPHE_BOOST_SETTINGS_"
            "APPEARANCE_LAYOUT_AUDIT_V56_APP_PASS",
        )
    )
    return "\n".join(lines) + "\n"


class AuditReportWriterContract(unittest.TestCase):
    def run_writer(self, log_text, requested_status="PASS"):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        log = root / "audit.log"
        output = root / "audit.json"
        log.write_text(log_text, encoding="utf-8")
        command = [
            "python3",
            str(WRITER),
            "--log",
            str(log),
            "--manifest",
            str(MANIFEST),
            "--output",
            str(output),
            "--status",
            requested_status,
            "--package",
            "com.rubenmayayo.reddit.dev",
            "--version-name",
            "1.12.12",
            "--version-code",
            "1234",
            "--apk-sha256",
            "a" * 64,
            "--android-release",
            "17",
            "--android-sdk",
            "37",
            "--device",
            "oriole",
            "--model",
            "Pixel_6",
            "--normal-boost-untouched",
            "true",
        ]
        result = subprocess.run(command, capture_output=True, text=True)
        report = json.loads(output.read_text(encoding="utf-8"))
        return temporary, result, report

    def test_complete_pass_report_is_machine_readable_and_exact(self):
        temporary, result, report = self.run_writer(complete_log())
        self.addCleanup(temporary.cleanup)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("PASS", report["result"]["status"])
        self.assertEqual(26, report["summary"]["audit_items"])
        self.assertEqual(196, report["summary"]["domain_actions"])
        self.assertEqual(6, report["summary"]["render_probes"])
        self.assertEqual(56, report["harness"]["version"])
        self.assertEqual(64, len(report["harness"]["manifest_sha256"]))
        self.assertEqual(
            "reversible_dev_only",
            report["safety"]["classification"],
        )

    def test_requested_pass_fails_closed_when_a_phase_is_missing(self):
        log = complete_log().replace("MORPHE_BINDING_AUDIT_RESTORE_OK\n", "")
        temporary, result, report = self.run_writer(log)
        self.addCleanup(temporary.cleanup)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("FAIL", report["result"]["status"])
        self.assertFalse(report["phases"]["restore"])
        self.assertIn("missing phases", report["result"]["failure_reason"])

    def test_explicit_failure_report_can_be_written_from_partial_log(self):
        temporary, result, report = self.run_writer(
            "MORPHE_BINDING_AUDIT_FAIL phase=write\n",
            requested_status="FAIL",
        )
        self.addCleanup(temporary.cleanup)
        self.assertEqual(0, result.returncode)
        self.assertEqual("FAIL", report["result"]["status"])
        self.assertEqual([], report["items"])


if __name__ == "__main__":
    unittest.main()
