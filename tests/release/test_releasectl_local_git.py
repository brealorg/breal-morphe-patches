#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import replace
import importlib.util
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_local_git", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


RefRelation = MODULE.RefRelation
ReleaseIdentity = MODULE.ReleaseIdentity
SubprocessGitRunner = MODULE.SubprocessGitRunner
observe_local_git = MODULE.observe_local_git


SHA256 = "d" * 64


def run_git(repo: Path, *args: str) -> str:
    completed = subprocess.run(
        ("git", "-C", str(repo), *args),
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return completed.stdout.strip()


def write_and_commit(repo: Path, filename: str, content: str, message: str) -> str:
    path = repo / filename
    path.write_text(content, encoding="utf-8")
    run_git(repo, "add", "--", filename)
    run_git(repo, "commit", "-m", message)
    return run_git(repo, "rev-parse", "HEAD")


class RepoFixture:
    def __init__(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.path = Path(self.tempdir.name)
        run_git(self.path, "init", "-b", "main")
        run_git(self.path, "config", "user.name", "Morphe Test")
        run_git(self.path, "config", "user.email", "morphe@example.invalid")
        self.base = write_and_commit(
            self.path,
            "fixture.txt",
            "base\n",
            "base",
        )

    def close(self) -> None:
        self.tempdir.cleanup()


def identity(target: str, *, tag: str = "morphe-patches-67") -> ReleaseIdentity:
    return ReleaseIdentity(
        version="1.4.67",
        tag=tag,
        release_commit=target,
        mpp_asset_name="patches-1.4.67.mpp",
        signature_asset_name="patches-1.4.67.mpp.asc",
        mpp_sha256=SHA256,
        signing_identity="0123456789ABCDEF",
    )


class LocalGitObserverTests(unittest.TestCase):
    def setUp(self) -> None:
        self.repo = RepoFixture()

    def tearDown(self) -> None:
        self.repo.close()

    def observe(self, target: str, *, tag: str = "morphe-patches-67"):
        return observe_local_git(
            self.repo.path,
            identity(target, tag=tag),
        )

    def test_g01_annotated_tag_and_ancestor_dev(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        target = write_and_commit(
            self.repo.path,
            "fixture.txt",
            "main target\n",
            "target",
        )
        run_git(
            self.repo.path,
            "tag",
            "-a",
            "morphe-patches-67",
            "-m",
            "release",
            target,
        )

        result = self.observe(target)

        self.assertEqual(result.current_branch, "main")
        self.assertEqual(result.local.main_commit, target)
        self.assertEqual(result.local.dev_commit, self.repo.base)
        self.assertEqual(
            result.local.dev_relation_to_target,
            RefRelation.ANCESTOR,
        )
        self.assertEqual(result.local.tag_commit, target)
        self.assertTrue(result.local.tag_is_annotated)
        self.assertTrue(result.safety.current_branch_is_main)
        self.assertTrue(result.safety.worktree_clean)
        self.assertTrue(result.safety.index_clean)
        self.assertTrue(result.safety.observations_complete)
        self.assertEqual(result.errors, ())

    def test_g02_lightweight_tag_is_detected(self) -> None:
        target = self.repo.base
        run_git(self.repo.path, "branch", "dev", target)
        run_git(self.repo.path, "tag", "morphe-patches-67", target)

        result = self.observe(target)

        self.assertEqual(result.local.tag_commit, target)
        self.assertFalse(result.local.tag_is_annotated)

    def test_g03_equal_dev_relation(self) -> None:
        target = self.repo.base
        run_git(self.repo.path, "branch", "dev", target)

        result = self.observe(target)

        self.assertEqual(
            result.local.dev_relation_to_target,
            RefRelation.EQUAL,
        )

    def test_g04_ahead_dev_relation(self) -> None:
        target = self.repo.base
        run_git(self.repo.path, "switch", "-c", "dev")
        dev_head = write_and_commit(
            self.repo.path,
            "dev.txt",
            "ahead\n",
            "dev ahead",
        )
        run_git(self.repo.path, "switch", "main")

        result = self.observe(target)

        self.assertEqual(result.local.dev_commit, dev_head)
        self.assertEqual(
            result.local.dev_relation_to_target,
            RefRelation.AHEAD,
        )

    def test_g05_divergent_dev_relation(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        target = write_and_commit(
            self.repo.path,
            "main.txt",
            "main\n",
            "main change",
        )
        run_git(self.repo.path, "switch", "dev")
        write_and_commit(
            self.repo.path,
            "dev.txt",
            "dev\n",
            "dev change",
        )
        run_git(self.repo.path, "switch", "main")

        result = self.observe(target)

        self.assertEqual(
            result.local.dev_relation_to_target,
            RefRelation.DIVERGENT,
        )

    def test_g06_missing_dev_and_tag_are_absent(self) -> None:
        result = self.observe(self.repo.base)

        self.assertIsNone(result.local.dev_commit)
        self.assertEqual(
            result.local.dev_relation_to_target,
            RefRelation.ABSENT,
        )
        self.assertIsNone(result.local.tag_commit)
        self.assertIsNone(result.local.tag_is_annotated)

    def test_g07_unstaged_and_untracked_files_dirty_worktree_only(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        (self.repo.path / "fixture.txt").write_text("modified\n", encoding="utf-8")
        (self.repo.path / "untracked.txt").write_text("new\n", encoding="utf-8")

        result = self.observe(self.repo.base)

        self.assertFalse(result.safety.worktree_clean)
        self.assertTrue(result.safety.index_clean)

    def test_g08_staged_change_dirties_index_only(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        (self.repo.path / "fixture.txt").write_text("staged\n", encoding="utf-8")
        run_git(self.repo.path, "add", "--", "fixture.txt")

        result = self.observe(self.repo.base)

        self.assertTrue(result.safety.worktree_clean)
        self.assertFalse(result.safety.index_clean)

    def test_g09_detached_head_is_observed_without_failure(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        run_git(self.repo.path, "checkout", "--detach", self.repo.base)

        result = self.observe(self.repo.base)

        self.assertIsNone(result.current_branch)
        self.assertFalse(result.safety.current_branch_is_main)
        self.assertTrue(result.safety.observations_complete)
        self.assertIn("HEAD is detached", result.warnings)

    def test_g10_non_repository_returns_observation_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = observe_local_git(
                directory,
                identity(self.repo.base),
            )

        self.assertIsNone(result.repo_root)
        self.assertFalse(result.safety.observations_complete)
        self.assertTrue(result.safety.required_tools_available)
        self.assertTrue(result.errors)

    def test_g11_unknown_target_relation_is_incomplete(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        unknown_target = "c" * 40

        result = self.observe(unknown_target)

        self.assertEqual(
            result.local.dev_relation_to_target,
            RefRelation.UNKNOWN,
        )
        self.assertFalse(result.safety.observations_complete)
        self.assertTrue(result.warnings)

    def test_g12_observer_does_not_mutate_repository(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)
        run_git(
            self.repo.path,
            "tag",
            "-a",
            "morphe-patches-67",
            "-m",
            "release",
            self.repo.base,
        )
        (self.repo.path / "untracked.txt").write_text("preserve\n", encoding="utf-8")

        before = {
            "head": run_git(self.repo.path, "rev-parse", "HEAD"),
            "branch": run_git(self.repo.path, "branch", "--show-current"),
            "refs": run_git(self.repo.path, "show-ref"),
            "status": run_git(
                self.repo.path,
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ),
            "diff": run_git(self.repo.path, "diff", "--binary"),
            "cached": run_git(self.repo.path, "diff", "--cached", "--binary"),
        }

        self.observe(self.repo.base)

        after = {
            "head": run_git(self.repo.path, "rev-parse", "HEAD"),
            "branch": run_git(self.repo.path, "branch", "--show-current"),
            "refs": run_git(self.repo.path, "show-ref"),
            "status": run_git(
                self.repo.path,
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ),
            "diff": run_git(self.repo.path, "diff", "--binary"),
            "cached": run_git(self.repo.path, "diff", "--cached", "--binary"),
        }

        self.assertEqual(after, before)

    def test_g13_result_serializes_with_stable_values(self) -> None:
        run_git(self.repo.path, "branch", "dev", self.repo.base)

        result = self.observe(self.repo.base)
        payload = result.as_dict()

        self.assertEqual(
            payload["local"]["dev_relation_to_target"],
            "EQUAL",
        )
        self.assertEqual(payload["current_branch"], "main")

    def test_g14_missing_git_executable_is_reported(self) -> None:
        runner = SubprocessGitRunner(
            git_executable="/__morphe_missing__/git",
        )

        result = observe_local_git(
            self.repo.path,
            identity(self.repo.base),
            runner=runner,
        )

        self.assertFalse(result.safety.required_tools_available)
        self.assertFalse(result.safety.observations_complete)
        self.assertTrue(result.errors)

    def test_g15_non_main_branch_is_not_mutation_safe(self) -> None:
        run_git(self.repo.path, "switch", "-c", "dev")

        result = self.observe(self.repo.base)

        self.assertEqual(result.current_branch, "dev")
        self.assertFalse(result.safety.current_branch_is_main)

    @unittest.skipUnless(shutil.which("git"), "git is required")
    def test_g16_repo_root_is_canonical_from_subdirectory(self) -> None:
        nested = self.repo.path / "a" / "b"
        nested.mkdir(parents=True)

        result = observe_local_git(
            nested,
            identity(self.repo.base),
        )

        self.assertEqual(
            Path(result.repo_root or "").resolve(),
            self.repo.path.resolve(),
        )


if __name__ == "__main__":
    unittest.main()
