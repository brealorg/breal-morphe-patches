#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "morphe-flow.py"
SPEC = importlib.util.spec_from_file_location("morphe_flow", SOURCE)
assert SPEC is not None
assert SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def run(*argv: str, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        list(argv),
        cwd=str(cwd) if cwd else None,
        text=True,
        capture_output=True,
        check=False,
    )


def git(cwd: Path, *args: str) -> str:
    result = run("git", *args, cwd=cwd)
    if result.returncode != 0:
        raise AssertionError(result.stderr or result.stdout)
    return result.stdout.strip()


def git_ro(cwd: Path, *args: str) -> str:
    environment = os.environ.copy()
    environment["GIT_OPTIONAL_LOCKS"] = "0"
    environment["GIT_TERMINAL_PROMPT"] = "0"
    result = subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        text=True,
        capture_output=True,
        check=False,
        env=environment,
    )
    if result.returncode != 0:
        raise AssertionError(result.stderr or result.stdout)
    return result.stdout.strip()


class ReadOnlyAllowlistTest(unittest.TestCase):
    def test_rejects_mutating_git_operations(self) -> None:
        for args in (
            ("push", "origin", "main"),
            ("fetch", "origin"),
            ("commit", "-m", "x"),
            ("checkout", "main"),
            ("branch", "-D", "work/x"),
            ("worktree", "remove", "/tmp/x"),
            ("stash", "push"),
            ("config", "user.name", "Mutating"),
            ("symbolic-ref", "HEAD", "refs/heads/main"),
        ):
            with self.subTest(args=args):
                with self.assertRaises(ValueError):
                    MODULE.GitReader._validate(args)

    def test_accepts_required_read_only_git_operations(self) -> None:
        for args in (
            ("status", "--porcelain=v1"),
            ("rev-parse", "HEAD"),
            ("worktree", "list", "--porcelain"),
            ("stash", "list"),
            ("remote", "get-url", "origin"),
            ("config", "--get", "remote.origin.url"),
            ("ls-remote", "--heads", "origin"),
        ):
            with self.subTest(args=args):
                MODULE.GitReader._validate(args)


class ParsingTest(unittest.TestCase):
    def test_origin_slug_variants(self) -> None:
        self.assertEqual(
            "brealorg/breal-morphe-patches",
            MODULE._parse_origin_slug("git@github.com:brealorg/breal-morphe-patches.git"),
        )
        self.assertEqual(
            "brealorg/breal-morphe-patches",
            MODULE._parse_origin_slug("https://github.com/brealorg/breal-morphe-patches.git"),
        )

    def test_issue_number_variants(self) -> None:
        self.assertEqual(106, MODULE._issue_number("work/issue106-settings-v2"))
        self.assertEqual(117, MODULE._issue_number("work/issue-117-back-selection"))
        self.assertIsNone(MODULE._issue_number("work/release-1.4.95"))

    def test_worktree_porcelain(self) -> None:
        parsed = MODULE._parse_worktree_porcelain(
            "worktree /repo\nHEAD abc\nbranch refs/heads/main\n\n"
            "worktree /repo-2\nHEAD def\ndetached\nprunable gitdir file points to non-existent location\n\n"
        )
        self.assertEqual(2, len(parsed))
        self.assertEqual("refs/heads/main", parsed[0]["branch"])
        self.assertTrue(parsed[1]["detached"])




class MergedPullRequestClassificationTest(unittest.TestCase):
    def _classify(
        self,
        *,
        head_matches: bool | None,
        merge_in_main: bool | None,
    ) -> tuple[str, bool, tuple[str, ...], tuple[str, ...], str]:
        pull_request = MODULE.PullRequestObservation(
            number=118,
            state="MERGED",
            draft=False,
            head_branch="work/issue106-test",
            head_sha="branch-head",
            base_branch="main",
            url="https://example.invalid/pr/118",
            merge_commit_sha="merge-commit",
        )
        return MODULE._classify_worktree(
            branch="work/issue106-test",
            exists=True,
            detached=False,
            prunable=None,
            dirty=False,
            head_sha="branch-head",
            upstream="origin/work/issue106-test",
            upstream_ahead=0,
            upstream_behind=0,
            main_ahead=7,
            main_behind=5,
            cherry_unique=7,
            cherry_equivalent=0,
            remote_relation="EQUAL",
            pull_request=pull_request,
            pull_request_head_matches=head_matches,
            pull_request_merge_in_main=merge_in_main,
        )

    def test_merged_squash_branch_is_cleanup_ready_despite_unique_patch_ids(self) -> None:
        state, safe, blockers, _, next_action = self._classify(
            head_matches=True,
            merge_in_main=True,
        )
        self.assertEqual("MERGED_CLEANUP_READY", state)
        self.assertTrue(safe)
        self.assertEqual((), blockers)
        self.assertEqual("REMOVE_STALE_BRANCH_AND_WORKTREE", next_action)

    def test_merged_branch_moved_after_pr_is_blocked(self) -> None:
        state, safe, blockers, _, next_action = self._classify(
            head_matches=False,
            merge_in_main=True,
        )
        self.assertEqual("MERGED_PR_BRANCH_MOVED", state)
        self.assertFalse(safe)
        self.assertIn("post-merge work", blockers[0])
        self.assertEqual("MANUAL_DIAGNOSIS", next_action)


class IntegrationAuditTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.remote = self.base / "remote.git"
        self.repo = self.base / "repo"
        self.issue_worktree = self.base / "repo-issue106"

        git(self.base, "init", "--bare", str(self.remote))
        git(self.base, "clone", str(self.remote), str(self.repo))
        git(self.repo, "config", "user.name", "Test")
        git(self.repo, "config", "user.email", "test@example.invalid")
        git(self.repo, "checkout", "-b", "main")
        (self.repo / "README.md").write_text("base\n", encoding="utf-8")
        git(self.repo, "add", "README.md")
        git(self.repo, "commit", "-m", "base")
        git(self.repo, "push", "-u", "origin", "main")
        git(self.remote, "symbolic-ref", "HEAD", "refs/heads/main")

        git(self.repo, "branch", "work/issue106-test")
        git(self.repo, "worktree", "add", str(self.issue_worktree), "work/issue106-test")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def _index_state(self, worktree: Path) -> str:
        index = Path(git_ro(worktree, "rev-parse", "--git-path", "index"))
        if not index.is_absolute():
            index = worktree / index
        payload = index.read_bytes()
        stat = index.stat()
        return f"{hashlib.sha256(payload).hexdigest()}:{stat.st_mtime_ns}:{stat.st_size}"

    def _snapshot(self) -> dict[str, str]:
        return {
            "refs": git_ro(self.repo, "show-ref"),
            "status_main": git_ro(self.repo, "status", "--porcelain=v1"),
            "status_issue": git_ro(self.issue_worktree, "status", "--porcelain=v1"),
            "worktrees": git_ro(self.repo, "worktree", "list", "--porcelain"),
            "index_main": self._index_state(self.repo),
            "index_issue": self._index_state(self.issue_worktree),
        }

    def test_audit_detects_dirty_issue_worktree_without_mutation(self) -> None:
        (self.issue_worktree / "dirty.txt").write_text("dirty\n", encoding="utf-8")
        before = self._snapshot()

        report = MODULE.collect_audit(self.repo, local_only=True)

        after = self._snapshot()
        self.assertEqual(before, after)
        issue = next(item for item in report.worktrees if item.branch == "work/issue106-test")
        self.assertEqual(106, issue.issue_number)
        self.assertTrue(issue.dirty)
        self.assertEqual("WORKTREE_DIRTY", issue.lifecycle_state)
        self.assertEqual("NONE", report.mutations)
        self.assertRegex(report.report_fingerprint, r"^sha256:[0-9a-f]{64}$")
        self.assertFalse(report.safe_to_mutate)


    def test_clean_local_only_audit_never_authorizes_mutation(self) -> None:
        report = MODULE.collect_audit(self.repo, local_only=True)
        self.assertFalse(report.safe_to_mutate)
        main = next(item for item in report.worktrees if item.branch == "main")
        self.assertFalse(main.safe_to_mutate)
        self.assertIn("remote Git and GitHub observations were skipped", main.warnings)
        issue = next(item for item in report.worktrees if item.branch == "work/issue106-test")
        self.assertEqual("LOCAL_OBSERVATION_REDUNDANT_BRANCH", issue.lifecycle_state)
        self.assertFalse(issue.safe_to_mutate)

    def test_cli_json_is_machine_readable(self) -> None:
        result = run(
            sys.executable,
            str(SOURCE),
            "--repo",
            str(self.repo),
            "--local-only",
            "--format",
            "json",
            "audit",
            cwd=ROOT,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        payload = json.loads(result.stdout)
        self.assertEqual(1, payload["schema_version"])
        self.assertEqual("NONE", payload["mutations"])
        self.assertEqual(2, len(payload["worktrees"]))

    def test_full_audit_observes_matching_remote_and_open_pr(self) -> None:
        (self.issue_worktree / "feature.txt").write_text("feature\n", encoding="utf-8")
        git(self.issue_worktree, "add", "feature.txt")
        git(self.issue_worktree, "commit", "-m", "feature")
        git(self.issue_worktree, "push", "-u", "origin", "work/issue106-test")
        head = git(self.issue_worktree, "rev-parse", "HEAD")
        github_url = "https://github.com/brealorg/breal-morphe-patches.git"
        git(self.repo, "config", "remote.origin.url", github_url)
        git(
            self.repo,
            "config",
            f"url.file://{self.remote}.insteadOf",
            github_url,
        )

        fake_bin = self.base / "bin"
        fake_bin.mkdir()
        fake_gh = fake_bin / "gh"
        fake_gh.write_text(
            "#!/usr/bin/env python3\n"
            "import json\n"
            f"print(json.dumps([{{'number': 500, 'state': 'OPEN', 'isDraft': False, "
            f"'headRefName': 'work/issue106-test', 'headRefOid': '{head}', "
            "'baseRefName': 'main', 'url': 'https://example.invalid/pr/500', "
            "'mergeCommit': None}]))\n",
            encoding="utf-8",
        )
        fake_gh.chmod(0o755)

        old_path = MODULE.os.environ.get("PATH", "")
        MODULE.os.environ["PATH"] = f"{fake_bin}:{old_path}"
        try:
            report = MODULE.collect_audit(self.repo, local_only=False)
        finally:
            MODULE.os.environ["PATH"] = old_path

        issue = next(item for item in report.worktrees if item.branch == "work/issue106-test")
        self.assertEqual("PR_OPEN", issue.lifecycle_state)
        self.assertEqual(500, issue.pull_request.number if issue.pull_request else None)
        self.assertEqual("EQUAL", issue.remote_relation)
        self.assertTrue(report.origin_main_tracking_current)
        self.assertTrue(report.safe_to_mutate)

    def test_full_audit_proves_squash_merged_pr_cleanup(self) -> None:
        (self.issue_worktree / "part-one.txt").write_text("one\n", encoding="utf-8")
        git(self.issue_worktree, "add", "part-one.txt")
        git(self.issue_worktree, "commit", "-m", "part one")
        (self.issue_worktree / "part-two.txt").write_text("two\n", encoding="utf-8")
        git(self.issue_worktree, "add", "part-two.txt")
        git(self.issue_worktree, "commit", "-m", "part two")
        git(self.issue_worktree, "push", "-u", "origin", "work/issue106-test")
        branch_head = git(self.issue_worktree, "rev-parse", "HEAD")

        git(self.repo, "merge", "--squash", "work/issue106-test")
        git(self.repo, "commit", "-m", "squash merged feature")
        merge_commit = git(self.repo, "rev-parse", "HEAD")
        git(self.repo, "push", "origin", "main")

        github_url = "https://github.com/brealorg/breal-morphe-patches.git"
        git(self.repo, "config", "remote.origin.url", github_url)
        git(
            self.repo,
            "config",
            f"url.file://{self.remote}.insteadOf",
            github_url,
        )

        fake_bin = self.base / "bin-merged"
        fake_bin.mkdir()
        fake_gh = fake_bin / "gh"
        fake_gh.write_text(
            "#!/usr/bin/env python3\n"
            "import json\n"
            f"print(json.dumps([{{'number': 118, 'state': 'MERGED', 'isDraft': False, "
            f"'headRefName': 'work/issue106-test', 'headRefOid': '{branch_head}', "
            "'baseRefName': 'main', 'url': 'https://example.invalid/pr/118', "
            f"'mergeCommit': {{'oid': '{merge_commit}'}}}}]))\n",
            encoding="utf-8",
        )
        fake_gh.chmod(0o755)

        old_path = MODULE.os.environ.get("PATH", "")
        MODULE.os.environ["PATH"] = f"{fake_bin}:{old_path}"
        try:
            report = MODULE.collect_audit(self.repo, local_only=False)
        finally:
            MODULE.os.environ["PATH"] = old_path

        issue = next(item for item in report.worktrees if item.branch == "work/issue106-test")
        self.assertGreater(issue.cherry_unique or 0, 0)
        self.assertTrue(issue.pull_request_head_matches)
        self.assertTrue(issue.pull_request_merge_in_main)
        self.assertEqual("MERGED_CLEANUP_READY", issue.lifecycle_state)
        self.assertTrue(issue.safe_to_mutate)
        self.assertTrue(report.safe_to_mutate)

    def test_strict_mode_returns_two_for_blockers(self) -> None:
        (self.issue_worktree / "dirty.txt").write_text("dirty\n", encoding="utf-8")
        result = run(
            sys.executable,
            str(SOURCE),
            "--repo",
            str(self.repo),
            "--local-only",
            "--strict",
            "audit",
            cwd=ROOT,
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("SAFE_TO_MUTATE=NO", result.stdout)
        self.assertIn("MUTATIONS=NONE", result.stdout)


if __name__ == "__main__":
    unittest.main()
