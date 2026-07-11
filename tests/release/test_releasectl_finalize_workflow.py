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
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_finalize", SCRIPT)
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


class FinalizeFixture:
    def __init__(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.remote = self.root / "remote.git"
        self.repo.mkdir()
        run("git", "init", "-b", "main", cwd=self.repo)
        run("git", "config", "user.name", "Morphe Test", cwd=self.repo)
        run("git", "config", "user.email", "test@example.invalid", cwd=self.repo)
        (self.repo / ".gitignore").write_text(
            "patches/build/\nlocal-artifacts/\ncandidates/\n",
            encoding="utf-8",
        )
        (self.repo / "README.md").write_text("# Releases\n", encoding="utf-8")
        (self.repo / "patches-bundle.json").write_text("{}\n", encoding="utf-8")
        run("git", "add", ".gitignore", "README.md", "patches-bundle.json", cwd=self.repo)
        run("git", "commit", "-m", "baseline", cwd=self.repo)
        self.base = run("git", "rev-parse", "HEAD", cwd=self.repo)
        run("git", "branch", "dev", cwd=self.repo)
        run("git", "init", "--bare", str(self.remote), cwd=self.root)
        run("git", "remote", "add", "origin", str(self.remote), cwd=self.repo)

    def close(self) -> None:
        self.temp.cleanup()


class FinalizeRunner:
    def __init__(self, fixture: FinalizeFixture) -> None:
        self.fixture = fixture
        self.calls: list[tuple[str, ...]] = []
        self.mpp = build_mpp()
        self.sha = hashlib.sha256(self.mpp).hexdigest()
        self.bytecode_pass = True

    def run(self, repo_path: Path, args):
        call = tuple(args)
        self.calls.append(call)
        if call[:4] == ("gh", "api", "--method", "GET"):
            return MODULE.MutationCommandResult(call, 1, "", "HTTP 404: Not Found")
        if call[:2] == ("python3", "scripts/prepare-release.py"):
            (repo_path / "README.md").write_text(
                f"# Releases\n\n## Current release\nVersion {VERSION}\nTag {TAG}\n",
                encoding="utf-8",
            )
            (repo_path / "patches-bundle.json").write_text(
                f'{{"version":"{VERSION}"}}\n', encoding="utf-8"
            )
            return MODULE.MutationCommandResult(call, 0, "prepared\n", "")
        if call[:2] == ("./gradlew", ":patches:buildAndroid"):
            libs = repo_path / "patches" / "build" / "libs"
            libs.mkdir(parents=True, exist_ok=True)
            (libs / f"patches-{VERSION}.mpp").write_bytes(self.mpp)
            return MODULE.MutationCommandResult(call, 0, "built\n", "")
        if call and call[0] == "gpg" and "--detach-sign" in call:
            output_index = call.index("--output") + 1
            Path(call[output_index]).write_text("signature", encoding="utf-8")
            return MODULE.MutationCommandResult(call, 0, "", "")
        if call and call[0] == "gpg" and "--verify" in call:
            return MODULE.MutationCommandResult(
                call,
                0,
                f"[GNUPG:] VALIDSIG {SIGNER} 2026-07-11 0 4 0 1 10 00 {SIGNER}\n",
                "",
            )
        if call[:2] == ("python3", "scripts/update-readme-current-release-sha.py"):
            with (repo_path / "README.md").open("a", encoding="utf-8") as handle:
                handle.write(f"SHA256 `{self.sha}`\n")
            return MODULE.MutationCommandResult(call, 0, "updated\n", "")
        if call and call[0] == "tools/build-boost-candidate.sh":
            candidate = repo_path / "candidates" / "release"
            candidate.mkdir(parents=True, exist_ok=True)
            apk = candidate / "boost.apk"
            apk.write_bytes(b"apk")
            (candidate / "static-gate.log").write_text("RESULT: PASS\n", encoding="utf-8")
            bytecode_state = "PASS" if self.bytecode_pass else "FAIL"
            (candidate / "bytecode-safety.log").write_text(
                f"BYTECODE_GATE={bytecode_state}\n", encoding="utf-8"
            )
            (candidate / "bytecode-safety-report.json").write_text(
                f'{{"bytecode_gate":"{bytecode_state}"}}\n', encoding="utf-8"
            )
            (candidate / "morphe-patch.log").write_text(
                "Applied: Modify login WebView\nApplied: Spoof client\n",
                encoding="utf-8",
            )
            return MODULE.MutationCommandResult(
                call, 0, f"DIR: {candidate}\nAPK: {apk}\n", ""
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


def final_inspection(identity):
    return SimpleNamespace(
        state_result=SimpleNamespace(
            state=MODULE.ReleaseState.READY_TO_PUBLISH,
            next_action=MODULE.NextAction.PUBLISH,
            observations_complete=True,
        ),
        observations=SimpleNamespace(
            local=SimpleNamespace(
                main_commit=identity.release_commit,
                dev_relation_to_target=MODULE.RefRelation.EQUAL,
            )
        ),
    )


class IdentityInspector:
    def __init__(self) -> None:
        self.calls = 0

    def __call__(self, repo_root, repository, identity, remote_name="origin"):
        self.calls += 1
        return final_inspection(identity)


class FinalizeWorkflowTests(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = FinalizeFixture()
        self.runner = FinalizeRunner(self.fixture)

    def tearDown(self) -> None:
        self.fixture.close()

    def test_f01_fresh_finalize_builds_commits_tags_and_aligns(self) -> None:
        inspector = IdentityInspector()
        result = MODULE.finalize_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            changelog=("Current release only.",),
            runner=self.runner,
            inspect_fn=inspector,
        )
        self.assertEqual(result.result, "MORPHE_RELEASE_FINALIZE_LOCAL_OK")
        self.assertEqual(result.state, MODULE.ReleaseState.READY_TO_PUBLISH)
        self.assertEqual(run("git", "rev-parse", "main", cwd=self.fixture.repo), result.release_commit)
        self.assertEqual(run("git", "rev-parse", "dev", cwd=self.fixture.repo), result.release_commit)
        self.assertEqual(run("git", "rev-list", "-n", "1", TAG, cwd=self.fixture.repo), result.release_commit)
        self.assertEqual(result.mpp_sha256, self.runner.sha)
        self.assertIn("--release-notes-file PATH", result.next_command or "")
        phases = [entry.phase for entry in MODULE.read_transaction_log(Path(result.transaction_log))]
        for phase in (
            "PREPARE_METADATA",
            "BUILD_FINAL_MPP",
            "SIGN_ARTIFACT",
            "BYTECODE_SAFETY_GATE",
            "ALIGN_LOCAL_DEV",
            "COMPLETE",
        ):
            self.assertIn(phase, phases)

    def test_f02_finalize_retry_is_non_destructive_success(self) -> None:
        inspector = IdentityInspector()
        first = MODULE.finalize_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            changelog=("Current release only.",),
            runner=self.runner,
            inspect_fn=inspector,
        )
        self.assertEqual(first.postcheck_result, "OK")
        head_before = run("git", "rev-parse", "HEAD", cwd=self.fixture.repo)
        self.runner.calls.clear()
        retry_inspector = IdentityInspector()
        second = MODULE.finalize_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            changelog=("Current release only.",),
            runner=self.runner,
            inspect_fn=retry_inspector,
        )
        self.assertEqual(second.result, "MORPHE_RELEASE_FINALIZE_LOCAL_ALREADY_FINALIZED_OK")
        self.assertTrue(second.already_satisfied)
        self.assertEqual(run("git", "rev-parse", "HEAD", cwd=self.fixture.repo), head_before)
        prohibited = {"./gradlew", "gpg", "tools/build-boost-candidate.sh"}
        self.assertFalse(any(call and call[0] in prohibited for call in self.runner.calls))

    def test_f03_bytecode_gate_failure_blocks_release_commit_and_tag(self) -> None:
        self.runner.bytecode_pass = False
        result = MODULE.finalize_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            changelog=("Current release only.",),
            runner=self.runner,
            inspect_fn=IdentityInspector(),
        )
        self.assertEqual(result.postcheck_result, "FAIL")
        self.assertIn("bytecode safety gate did not pass", result.error or "")
        self.assertEqual(run("git", "rev-parse", "HEAD", cwd=self.fixture.repo), self.fixture.base)
        self.assertIsNone(MODULE._read_exact_ref(self.fixture.repo, f"refs/tags/{TAG}", self.runner))

    def test_f04_invalid_changelog_aborts_before_release_mutations(self) -> None:
        result = MODULE.finalize_release_workflow(
            self.fixture.repo,
            "owner/repo",
            version=VERSION,
            tag=TAG,
            changelog=(),
            runner=self.runner,
            inspect_fn=IdentityInspector(),
        )
        self.assertEqual(result.postcheck_result, "FAIL")
        self.assertEqual(run("git", "rev-parse", "HEAD", cwd=self.fixture.repo), self.fixture.base)
        self.assertIsNone(MODULE._read_exact_ref(self.fixture.repo, f"refs/tags/{TAG}", self.runner))


if __name__ == "__main__":
    unittest.main()
