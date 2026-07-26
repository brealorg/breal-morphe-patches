#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "morphe_flow_operations.py"
SPEC = importlib.util.spec_from_file_location("morphe_flow_operations", SOURCE)
assert SPEC is not None
assert SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def run(*argv: str, cwd: Path, allow: tuple[int, ...] = (0,)) -> str:
    result = subprocess.run(
        list(argv),
        cwd=str(cwd),
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode not in allow:
        raise AssertionError(result.stderr or result.stdout)
    return result.stdout.strip()


def git(cwd: Path, *args: str) -> str:
    return run("git", *args, cwd=cwd)


class MainSyncVerificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.remote = self.base / "remote.git"
        self.repo = self.base / "repo"
        self.feature = self.base / "repo-feature"
        self.unrelated = self.base / "repo-issue121"

        git(self.base, "init", "--bare", str(self.remote))
        git(self.base, "clone", str(self.remote), str(self.repo))
        git(self.repo, "config", "user.name", "Test")
        git(self.repo, "config", "user.email", "test@example.invalid")
        git(self.repo, "checkout", "-b", "main")
        (self.repo / "base.txt").write_text("base\n", encoding="utf-8")
        git(self.repo, "add", "base.txt")
        git(self.repo, "commit", "-m", "base")
        self.old_main = git(self.repo, "rev-parse", "HEAD")
        git(self.repo, "push", "-u", "origin", "main")
        git(self.remote, "symbolic-ref", "HEAD", "refs/heads/main")
        git(self.repo, "remote", "set-head", "origin", "main")

        self.work_branch = "work/hardening-v22-test"
        git(self.repo, "branch", self.work_branch)
        git(self.repo, "worktree", "add", str(self.feature), self.work_branch)
        git(self.feature, "config", "user.name", "Test")
        git(self.feature, "config", "user.email", "test@example.invalid")
        (self.feature / "feature.txt").write_text("feature\n", encoding="utf-8")
        git(self.feature, "add", "feature.txt")
        git(self.feature, "commit", "-m", "feature")
        self.pr_head = git(self.feature, "rev-parse", "HEAD")
        git(self.feature, "push", "-u", "origin", self.work_branch)

        git(self.repo, "merge", "--squash", self.work_branch)
        git(self.repo, "commit", "-m", "squash feature")
        self.new_main = git(self.repo, "rev-parse", "HEAD")
        git(self.repo, "push", "origin", "main")

        git(self.repo, "branch", "work/issue121-dirty")
        git(self.repo, "worktree", "add", str(self.unrelated), "work/issue121-dirty")
        (self.unrelated / "dirty.txt").write_text("dirty\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _collect(self):
        return MODULE.collect_main_sync_verification(
            self.repo,
            old_main_sha=self.old_main,
            new_main_sha=self.new_main,
            pr_head_sha=self.pr_head,
            work_branch=self.work_branch,
        )

    def test_verification_ignores_dirty_unrelated_worktree(self) -> None:
        report = self._collect()
        self.assertTrue(report.verified, report.blockers)
        self.assertEqual(1, report.unrelated_dirty_worktree_count)
        self.assertEqual("REPORT_ONLY_NEVER_GATE_SYNC_LOCAL_MAIN", report.unrelated_activity_policy)
        self.assertEqual((), report.blockers)
        self.assertEqual("NONE", report.mutations)
        checks = {item.field: item for item in report.checks}
        self.assertEqual("PASS", checks["local_main"].status)
        self.assertEqual("PASS", checks["origin_main"].status)
        self.assertEqual("PASS", checks["remote_main"].status)
        self.assertEqual("PASS", checks["squash_parent_line"].status)
        self.assertEqual("PASS", checks["squash_tree"].status)

    def test_verifier_is_read_only(self) -> None:
        refs_before = git(self.repo, "show-ref")
        worktrees_before = git(self.repo, "worktree", "list", "--porcelain")
        main_status_before = git(self.repo, "status", "--porcelain=v1")
        unrelated_status_before = git(self.unrelated, "status", "--porcelain=v1")

        report = self._collect()

        self.assertTrue(report.verified)
        self.assertEqual(refs_before, git(self.repo, "show-ref"))
        self.assertEqual(worktrees_before, git(self.repo, "worktree", "list", "--porcelain"))
        self.assertEqual(main_status_before, git(self.repo, "status", "--porcelain=v1"))
        self.assertEqual(unrelated_status_before, git(self.unrelated, "status", "--porcelain=v1"))

    def test_remote_main_mismatch_reports_exact_field(self) -> None:
        other = self.base / "other"
        git(self.base, "clone", str(self.remote), str(other))
        git(other, "config", "user.name", "Other")
        git(other, "config", "user.email", "other@example.invalid")
        (other / "remote-only.txt").write_text("remote\n", encoding="utf-8")
        git(other, "add", "remote-only.txt")
        git(other, "commit", "-m", "advance remote")
        git(other, "push", "origin", "main")

        report = self._collect()

        self.assertFalse(report.verified)
        self.assertTrue(any(item.startswith("remote_main:") for item in report.blockers))
        checks = {item.field: item for item in report.checks}
        self.assertEqual("FAIL", checks["remote_main"].status)
        self.assertEqual(self.new_main, checks["remote_main"].expected)
        self.assertNotEqual(self.new_main, checks["remote_main"].actual)

    def test_validation_rejects_non_exact_identifiers(self) -> None:
        with self.assertRaises(ValueError):
            MODULE.collect_main_sync_verification(
                self.repo,
                old_main_sha="abc",
                new_main_sha=self.new_main,
                pr_head_sha=self.pr_head,
                work_branch=self.work_branch,
            )
        with self.assertRaises(ValueError):
            MODULE.collect_main_sync_verification(
                self.repo,
                old_main_sha=self.old_main,
                new_main_sha=self.new_main,
                pr_head_sha=self.pr_head,
                work_branch="main",
            )


if __name__ == "__main__":
    unittest.main()
