from __future__ import annotations

import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]


def load_script(name: str, relative_path: str):
    path = ROOT / relative_path
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class ReleasePipelineHardeningSourceContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.compose = load_script(
            "morphe_compose_release_notes",
            "scripts/compose-release-notes.py",
        )
        cls.validator = load_script(
            "morphe_validate_release_notes",
            "scripts/validate-release-notes.py",
        )
        cls.prepare = load_script(
            "morphe_prepare_release",
            "scripts/prepare-release.py",
        )
        cls.releasectl = load_script(
            "morphe_releasectl",
            "scripts/releasectl.py",
        )

    def test_structured_composer_generates_exact_headings(self) -> None:
        body = self.compose.compose_release_notes(
            version="1.4.99",
            app_area="Boost for Reddit",
            changes="Restore native action\nAdd regression coverage",
            user_impact="Long-press works again.",
            issue_number="135",
        )
        self.assertIn("\n### Boost for Reddit\n", body)
        self.assertIn("\n### Changes\n", body)
        self.assertIn("\n### User impact\n", body)
        self.assertNotIn("\n## Changes\n", body)
        self.assertEqual(
            self.validator.validate(
                body,
                version="1.4.99",
                tag="",
                asset="",
                sha256="",
                require_sha=False,
                human_input_only=True,
            ),
            [],
        )

    def test_level_two_changes_heading_is_rejected(self) -> None:
        body = self.compose.compose_release_notes(
            version="1.4.99",
            app_area="Boost for Reddit",
            changes="Restore native action",
            user_impact="Long-press works again.",
        ).replace("### Changes", "## Changes")
        errors = self.validator.validate(
            body,
            version="1.4.99",
            tag="",
            asset="",
            sha256="",
            require_sha=False,
            human_input_only=True,
        )
        self.assertTrue(
            any("level-three heading" in error for error in errors),
            errors,
        )

    def test_java_21_is_rejected_before_release_preparation(self) -> None:
        output = "    java.specification.version = 21\n"
        completed = subprocess.CompletedProcess(
            args=["java"],
            returncode=0,
            stdout="",
            stderr=output,
        )
        with mock.patch.object(
            self.prepare.subprocess,
            "run",
            return_value=completed,
        ):
            with self.assertRaises(SystemExit) as raised:
                self.prepare.require_release_java_17()
        self.assertIn("requires Java 17", str(raised.exception))
        self.assertIn("No files changed", str(raised.exception))

    def test_java_17_is_accepted(self) -> None:
        output = "    java.specification.version = 17\n"
        completed = subprocess.CompletedProcess(
            args=["java"],
            returncode=0,
            stdout="",
            stderr=output,
        )
        with mock.patch.object(
            self.prepare.subprocess,
            "run",
            return_value=completed,
        ):
            self.prepare.require_release_java_17()

    def test_release_failure_preserves_known_identity_and_digest(self) -> None:
        mismatch = self.releasectl.ArtifactDigestMismatch(
            "a" * 64,
            "b" * 64,
        )
        result = self.releasectl._workflow_failure(
            command="publish",
            result_token="FAIL",
            transaction_log=Path("/tmp/release.jsonl"),
            error=str(mismatch),
            state=self.releasectl.ReleaseState.NOT_FINALIZED,
            next_action=self.releasectl.NextAction.MANUAL_DIAGNOSIS,
            release_commit="c" * 40,
            mpp_sha256=mismatch.expected,
            actual_mpp_sha256=mismatch.actual,
            failure_category="ARTIFACT_MISMATCH",
        )
        rendered = self.releasectl._render_workflow_text(result)
        self.assertIn(f"RELEASE_COMMIT={'c' * 40}", rendered)
        self.assertIn(f"EXPECTED_MPP_SHA256={'a' * 64}", rendered)
        self.assertIn(f"ACTUAL_MPP_SHA256={'b' * 64}", rendered)
        self.assertIn("FAILURE_CATEGORY=ARTIFACT_MISMATCH", rendered)
        self.assertNotIn("STATE=INCONSISTENT_ABORT", rendered)

    def test_source_ordering_and_exact_runtime_contract(self) -> None:
        workflow = (ROOT / ".github/workflows/release.yml").read_text(
            encoding="utf-8"
        )
        smoke = (
            ROOT / ".github/workflows/release-feed-smoke.yml"
        ).read_text(encoding="utf-8")
        prepare = (ROOT / "scripts/prepare-release.py").read_text(
            encoding="utf-8"
        )
        ctl = (ROOT / "scripts/releasectl.py").read_text(encoding="utf-8")

        self.assertNotIn("      release_notes:", workflow)
        self.assertIn("java-version: '17.0.19+10'", workflow)
        self.assertIn("java-version: '17.0.19+10'", smoke)

        checkout = workflow.index("- name: Checkout exact main")
        notes = workflow.index("- name: Compose and validate release notes")
        java = workflow.index("- name: Setup Java")
        gpg = workflow.index("- name: Import GPG key")
        self.assertLess(checkout, notes)
        self.assertLess(notes, java)
        self.assertLess(java, gpg)

        preflight = prepare.index("require_release_java_17()")
        first_mutation = prepare.index("write(gradle_path, gradle_text)")
        self.assertLess(preflight, first_mutation)

        for marker in (
            "INPUT_VALIDATION_FAILED",
            "ARTIFACT_MISMATCH",
            "EXPECTED_MPP_SHA256",
            "ACTUAL_MPP_SHA256",
            "ReleaseInputValidationError",
            "ArtifactDigestMismatch",
        ):
            self.assertIn(marker, ctl)


if __name__ == "__main__":
    unittest.main()
