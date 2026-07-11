#!/usr/bin/env python3
from __future__ import annotations

from io import BytesIO
import hashlib
import importlib.util
from pathlib import Path
import subprocess
from types import SimpleNamespace
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_workflows", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


VERSION = "1.4.67"
TAG = "morphe-patches-67"
SIGNER = MODULE.DEFAULT_SIGNING_IDENTITY


def run(*args: str, cwd: Path) -> str:
    completed = subprocess.run(args, cwd=cwd, check=True, text=True, capture_output=True)
    return completed.stdout.strip()


def build_mpp() -> bytes:
    output = BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("classes.dex", b"dex")
        archive.writestr("extensions/boostforreddit.mpe", b"boost")
    return output.getvalue()


class WorkflowFixture:
    def __init__(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.remote = self.root / "remote.git"
        self.repo.mkdir()
        run("git", "init", "-b", "main", cwd=self.repo)
        run("git", "config", "user.name", "Morphe Test", cwd=self.repo)
        run("git", "config", "user.email", "test@example.invalid", cwd=self.repo)
        (self.repo / "scripts").mkdir()
        validator = self.repo / "scripts" / "validate-release-notes.py"
        validator.write_text("#!/usr/bin/env python3\nraise SystemExit(0)\n", encoding="utf-8")
        validator.chmod(0o755)
        mpp = build_mpp()
        self.sha = hashlib.sha256(mpp).hexdigest()
        libs = self.repo / "patches" / "build" / "libs"
        libs.mkdir(parents=True)
        self.mpp = libs / f"patches-{VERSION}.mpp"
        self.sig = Path(str(self.mpp) + ".asc")
        self.mpp.write_bytes(mpp)
        self.sig.write_text("signature", encoding="utf-8")
        self.notes = self.root / "notes.md"
        self.notes.write_text(
            "## Boost for Reddit\n\n### Changes\n\n- Test release.\n\n### User impact\n\n- Test.\n",
            encoding="utf-8",
        )
        (self.repo / ".gitignore").write_text(
            "patches/build/\nlocal-artifacts/\n",
            encoding="utf-8",
        )
        (self.repo / "README.md").write_text(
            f"## Current release\nVersion {VERSION}\nTag {TAG}\nSHA256 `{self.sha}`\n",
            encoding="utf-8",
        )
        run("git", "add", ".gitignore", "README.md", "scripts/validate-release-notes.py", cwd=self.repo)
        run("git", "commit", "-m", "Release fixture", cwd=self.repo)
        self.commit = run("git", "rev-parse", "HEAD", cwd=self.repo)
        run("git", "branch", "dev", cwd=self.repo)
        run("git", "tag", "-a", TAG, "-m", TAG, cwd=self.repo)
        run("git", "init", "--bare", str(self.remote), cwd=self.root)
        run("git", "remote", "add", "origin", str(self.remote), cwd=self.repo)

    def close(self) -> None:
        self.temp.cleanup()


class HybridRunner:
    def __init__(self, signer: str = SIGNER) -> None:
        self.calls: list[tuple[str, ...]] = []
        self.signer = signer

    def run(self, repo_path: Path, args):
        call = tuple(args)
        self.calls.append(call)
        if call[0] == "gh":
            return MODULE.MutationCommandResult(call, 0, "{}\n", "")
        if call[0] == "gpg":
            return MODULE.MutationCommandResult(
                call,
                0,
                f"[GNUPG:] VALIDSIG {self.signer} 2026-07-11 0 4 0 1 10 00 {self.signer}\n",
                "",
            )
        completed = subprocess.run(
            call,
            cwd=repo_path,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        return MODULE.MutationCommandResult(
            call, completed.returncode, completed.stdout, completed.stderr
        )


def one_operation(operation_type):
    operation = MODULE.PlanOperation(
        sequence=1,
        operation_id=operation_type.value.lower(),
        operation_type=operation_type,
        mutates_state=operation_type not in {
            MODULE.PlanOperationType.VERIFY_DRAFT_ASSETS,
            MODULE.PlanOperationType.VERIFY_PUBLISHED_RELEASE,
        },
        preconditions=(),
        desired_result=(),
    )
    return SimpleNamespace(
        disposition=MODULE.PlanDisposition.APPLY_REQUIRED,
        operations=(operation,),
        plan_hash=f"sha256:{operation_type.value.lower()}",
    )


def inspection(
    state,
    *,
    operation_type=None,
    complete=True,
    reasons=(),
    verifier=MODULE.VerifierStatus.NOT_RUN,
    release_id=67,
):
    plan = (
        one_operation(operation_type)
        if operation_type is not None
        else SimpleNamespace(
            disposition=(
                MODULE.PlanDisposition.NOOP_ALREADY_SATISFIED
                if state is MODULE.ReleaseState.PUBLISHED_AND_VERIFIED
                else MODULE.PlanDisposition.CONFLICT_ABORT
            ),
            operations=(),
            plan_hash="sha256:none",
        )
    )
    return SimpleNamespace(
        state_result=SimpleNamespace(
            state=state,
            observations_complete=complete,
            reasons=tuple(reasons),
        ),
        plan=plan,
        observations=SimpleNamespace(
            verification=MODULE.VerificationObservations(verifier)
        ),
        github_release=SimpleNamespace(release_id=release_id),
    )


class SequenceInspector:
    def __init__(self, values) -> None:
        self.values = list(values)
        self.calls = 0

    def __call__(self, *args, **kwargs):
        if self.calls >= len(self.values):
            raise AssertionError("inspection sequence exhausted")
        value = self.values[self.calls]
        self.calls += 1
        return value


class ReleaseWorkflowTests(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = WorkflowFixture()
        self.runner = HybridRunner()

    def tearDown(self) -> None:
        self.fixture.close()

    def test_w01_completed_publish_retry_is_successful_noop(self) -> None:
        inspector = SequenceInspector(
            [inspection(MODULE.ReleaseState.PUBLISHED_AND_VERIFIED)]
        )
        result = MODULE.publish_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            release_notes_file=self.fixture.notes,
            runner=self.runner,
            inspect_fn=inspector,
        )
        self.assertEqual(result.result, "MORPHE_RELEASE_ALREADY_PUBLISHED_VERIFIED_OK")
        self.assertTrue(result.already_satisfied)
        self.assertEqual(result.postcheck_result, "OK")
        self.assertFalse(any(call[0] == "gh" for call in self.runner.calls))

    def test_w02_publish_resumes_all_missing_remote_phases(self) -> None:
        inspector = SequenceInspector(
            [
                inspection(MODULE.ReleaseState.READY_TO_PUBLISH),
                inspection(
                    MODULE.ReleaseState.READY_TO_PUBLISH,
                    operation_type=MODULE.PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
                ),
                inspection(
                    MODULE.ReleaseState.PARTIALLY_PUBLISHED,
                    operation_type=MODULE.PlanOperationType.CREATE_DRAFT_RELEASE,
                ),
                inspection(
                    MODULE.ReleaseState.PARTIALLY_PUBLISHED,
                    operation_type=MODULE.PlanOperationType.UPLOAD_MPP_ASSET,
                ),
                inspection(
                    MODULE.ReleaseState.PARTIALLY_PUBLISHED,
                    operation_type=MODULE.PlanOperationType.UPLOAD_SIGNATURE_ASSET,
                ),
                inspection(
                    MODULE.ReleaseState.PARTIALLY_PUBLISHED,
                    operation_type=MODULE.PlanOperationType.PUBLISH_DRAFT_RELEASE,
                    verifier=MODULE.VerifierStatus.PASS,
                ),
                inspection(MODULE.ReleaseState.PUBLISHED_AND_VERIFIED),
            ]
        )
        result = MODULE.publish_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            release_notes_file=self.fixture.notes,
            runner=self.runner,
            inspect_fn=inspector,
        )
        self.assertEqual(result.result, "MORPHE_RELEASE_PUBLISH_EXISTING_OK")
        self.assertEqual(result.state, MODULE.ReleaseState.PUBLISHED_AND_VERIFIED)
        calls = self.runner.calls
        self.assertTrue(any(call[:3] == ("git", "push", "--atomic") for call in calls))
        self.assertTrue(any(call[:4] == ("gh", "api", "--method", "POST") for call in calls))
        uploads = [call for call in calls if call[:3] == ("gh", "release", "upload")]
        self.assertEqual(len(uploads), 2)
        self.assertTrue(any(call[:4] == ("gh", "api", "--method", "PATCH") for call in calls))
        entries = MODULE.read_transaction_log(Path(result.transaction_log))
        phases = [entry.phase for entry in entries if entry.status == "COMPLETED"]
        for required in (
            "VALIDATE_RELEASE_NOTES",
            "PUSH_RELEASE_REFS_ATOMIC",
            "CREATE_DRAFT_RELEASE",
            "UPLOAD_MPP_ASSET",
            "UPLOAD_SIGNATURE_ASSET",
            "PUBLISH_DRAFT_RELEASE",
            "COMPLETE",
        ):
            self.assertIn(required, phases)

    def test_w03_conflicting_state_aborts_without_github_mutation(self) -> None:
        inspector = SequenceInspector(
            [
                inspection(MODULE.ReleaseState.READY_TO_PUBLISH),
                inspection(
                    MODULE.ReleaseState.INCONSISTENT_ABORT,
                    reasons=("remote tag points to a different commit",),
                ),
            ]
        )
        result = MODULE.publish_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            release_notes_file=self.fixture.notes,
            runner=self.runner,
            inspect_fn=inspector,
        )
        self.assertEqual(result.postcheck_result, "FAIL")
        self.assertIn("remote tag points to a different commit", result.error or "")
        self.assertFalse(any(call[0] == "gh" for call in self.runner.calls))
        self.assertFalse(any(call[:3] == ("git", "push", "--atomic") for call in self.runner.calls))

    def test_w04_release_notes_are_validated_before_atomic_push(self) -> None:
        inspector = SequenceInspector(
            [
                inspection(MODULE.ReleaseState.READY_TO_PUBLISH),
                inspection(
                    MODULE.ReleaseState.READY_TO_PUBLISH,
                    operation_type=MODULE.PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
                ),
                inspection(MODULE.ReleaseState.PUBLISHED_AND_VERIFIED),
            ]
        )
        result = MODULE.publish_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            release_notes_file=self.fixture.notes,
            runner=self.runner,
            inspect_fn=inspector,
        )
        self.assertEqual(result.postcheck_result, "OK")
        validate_index = next(
            index
            for index, call in enumerate(self.runner.calls)
            if call[:2] == ("python3", "scripts/validate-release-notes.py")
        )
        push_index = next(
            index
            for index, call in enumerate(self.runner.calls)
            if call[:3] == ("git", "push", "--atomic")
        )
        self.assertLess(validate_index, push_index)

    def test_w05_resume_uses_same_engine_and_result_contract(self) -> None:
        inspector = SequenceInspector(
            [inspection(MODULE.ReleaseState.PUBLISHED_AND_VERIFIED)]
        )
        result = MODULE.publish_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            release_notes_file=self.fixture.notes,
            command_name="resume",
            runner=self.runner,
            inspect_fn=inspector,
        )
        text = MODULE._render_workflow_text(result)
        self.assertIn("COMMAND=resume", text)
        self.assertIn("POSTCHECK_RESULT=OK", text)
        self.assertIn("FINAL_STATE=PUBLISHED_AND_VERIFIED", text)


if __name__ == "__main__":
    unittest.main()
