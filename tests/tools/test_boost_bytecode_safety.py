#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
GATE = ROOT / "tools" / "boost-bytecode-safety-gate.sh"
FIXTURES = ROOT / "tests" / "fixtures" / "bytecode-safety"


def run_fixture(name: str, *, report: Path | None = None) -> subprocess.CompletedProcess[str]:
    fixture = FIXTURES / name
    command = [
        str(GATE),
        "--base-smali",
        str(fixture / "base"),
        "--candidate-smali",
        str(fixture / "candidate"),
        "--patch-result",
        str(fixture / "patch-result.json"),
    ]
    if report is not None:
        command.extend(("--report", str(report)))
    return subprocess.run(
        command,
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


class BytecodeSafetyFixtureTests(unittest.TestCase):
    def test_b01_reused_p1_is_rejected_with_exact_reason(self) -> None:
        completed = run_fixture("verifyerror_reused_p1")
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("BYTECODE_GATE=FAIL", completed.stdout)
        self.assertIn("CLASS=com.rubenmayayo.reddit.ui.adapters.CommentViewHolder", completed.stdout)
        self.assertIn("INSTRUCTION=invoke-static {p1}", completed.stdout)
        self.assertIn("REASON=OBJECT_USE_FROM_PRIMITIVE_REGISTER", completed.stdout)

    def test_b02_package_private_table_text_field_is_rejected(self) -> None:
        completed = run_fixture("illegalaccess_tabletextview_b")
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("BYTECODE_GATE=FAIL", completed.stdout)
        self.assertIn("REASON=PACKAGE_PRIVATE_FIELD_INACCESSIBLE", completed.stdout)
        self.assertIn("TableTextView;->b:Landroid/widget/TextView;", completed.stdout)

    def test_b03_public_get_child_at_contract_passes(self) -> None:
        completed = run_fixture("valid_public_getchildat")
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("BYTECODE_GATE=PASS", completed.stdout)
        self.assertIn("MODIFIED_METHODS=1", completed.stdout)

    def test_b04_stable_holder_field_reload_passes(self) -> None:
        completed = run_fixture("valid_stable_object_reload")
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("BYTECODE_GATE=PASS", completed.stdout)
        self.assertIn("REGISTER_FLOW_CHECKS=2", completed.stdout)

    def test_b05_incompatible_control_flow_join_is_rejected(self) -> None:
        completed = run_fixture("incompatible_join")
        self.assertEqual(completed.returncode, 1, completed.stdout + completed.stderr)
        self.assertIn("REASON=CONTROL_FLOW_JOIN_TYPE_CONFLICT", completed.stdout)
        self.assertIn("register=v0", completed.stdout)

    def test_b06_output_is_deterministic(self) -> None:
        first = run_fixture("illegalaccess_tabletextview_b")
        second = run_fixture("illegalaccess_tabletextview_b")
        self.assertEqual(first.returncode, second.returncode)
        self.assertEqual(first.stdout, second.stdout)
        self.assertEqual(first.stderr, second.stderr)

    def test_b07_json_report_matches_machine_result(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            completed = run_fixture("valid_public_getchildat", report=report)
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(payload["schema_version"], 1)
            self.assertEqual(payload["bytecode_gate"], "PASS")
            self.assertEqual(payload["modified_methods"], 1)
            self.assertEqual(payload["findings"], [])

    def test_b08_invalid_patch_result_fails_closed(self) -> None:
        fixture = FIXTURES / "valid_public_getchildat"
        with tempfile.TemporaryDirectory() as directory:
            invalid = Path(directory) / "patch-result.json"
            invalid.write_text("not-json\n", encoding="utf-8")
            completed = subprocess.run(
                [
                    str(GATE),
                    "--base-smali",
                    str(fixture / "base"),
                    "--candidate-smali",
                    str(fixture / "candidate"),
                    "--patch-result",
                    str(invalid),
                ],
                cwd=ROOT,
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(completed.returncode, 2)
        self.assertIn("BYTECODE_GATE=FAIL", completed.stdout)
        self.assertIn("REASON=ANALYSIS_UNAVAILABLE", completed.stdout)


class BytecodeSafetyIntegrationContractTests(unittest.TestCase):
    def test_i01_normal_candidate_builder_runs_gate_after_static_gate(self) -> None:
        text = (ROOT / "tools" / "build-boost-candidate.sh").read_text(encoding="utf-8")
        self.assertIn("===== bytecode safety gate =====", text)
        self.assertIn("--base-apk \"$BASE_APK\"", text)
        self.assertIn("--candidate-apk \"$OUT_APK\"", text)
        self.assertIn("--patch-result \"$RESULT_JSON\"", text)
        self.assertIn("BYTECODE_GATE=PASS", text)

    def test_i02_devclone_rechecks_source_before_decode(self) -> None:
        text = (ROOT / "tools" / "build-boost-devclone-candidate.sh").read_text(
            encoding="utf-8"
        )
        gate = text.index("===== source bytecode safety gate =====")
        decode = text.index("===== decode =====")
        self.assertLess(gate, decode)
        self.assertIn("--patch-result \"$PATCH_RESULT\"", text)

    def test_i03_dev_from_mpp_passes_canonical_base_and_patch_result(self) -> None:
        text = (ROOT / "tools" / "boost-dev-from-mpp.sh").read_text(encoding="utf-8")
        self.assertIn("--base-apk \"$BASE_APK\"", text)
        self.assertIn("--patch-result \"$NORMAL_DIR/patch-result.json\"", text)

    def test_i04_release_finalize_requires_log_and_json_pass(self) -> None:
        text = (ROOT / "scripts" / "releasectl.py").read_text(encoding="utf-8")
        self.assertIn('bytecode_log = candidate_dir / "bytecode-safety.log"', text)
        self.assertIn('bytecode_report = candidate_dir / "bytecode-safety-report.json"', text)
        self.assertIn('bytecode_payload.get("bytecode_gate") != "PASS"', text)

    def test_i05_ci_workflow_runs_fixture_suite_for_boost_patch_changes(self) -> None:
        text = (ROOT / ".github" / "workflows" / "boost-bytecode-safety.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/**", text)
        self.assertIn("test_boost_bytecode_safety.py", text)


if __name__ == "__main__":
    unittest.main()
