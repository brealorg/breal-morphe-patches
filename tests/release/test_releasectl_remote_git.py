#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_remote_git", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GitCommandResult = MODULE.GitCommandResult
RefRelation = MODULE.RefRelation
ReleaseIdentity = MODULE.ReleaseIdentity
SubprocessGitRunner = MODULE.SubprocessGitRunner
observe_remote_git = MODULE.observe_remote_git


SHA256 = "d" * 64


def run_git(repo: Path, *args: str, check: bool = True) -> str:
    completed = subprocess.run(
        ("git", "-C", str(repo), *args),
        check=check,
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


class RemoteRepoFixture:
    def __init__(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tempdir.name)
        self.repo = self.root / "repo"
        self.remote = self.root / "remote.git"
        self.repo.mkdir()
        self.remote.mkdir()

        run_git(self.repo, "init", "-b", "main")
        run_git(self.repo, "config", "user.name", "Morphe Test")
        run_git(self.repo, "config", "user.email", "morphe@example.invalid")
        self.base = write_and_commit(
            self.repo,
            "fixture.txt",
            "base\n",
            "base",
        )
        run_git(self.remote, "init", "--bare")
        run_git(self.repo, "remote", "add", "origin", str(self.remote))

    def push(self, source: str, destination: str) -> None:
        run_git(self.repo, "push", "origin", f"{source}:{destination}")

    def close(self) -> None:
        self.tempdir.cleanup()


class StaticRunner:
    def __init__(self, repo_root: Path, listing_stdout: str) -> None:
        self.repo_root = repo_root
        self.listing_stdout = listing_stdout

    def run(self, repo_path: Path, args: tuple[str, ...]) -> GitCommandResult:
        command = ("git", "-C", str(repo_path), *args)
        if args == ("rev-parse", "--show-toplevel"):
            return GitCommandResult(
                args=command,
                returncode=0,
                stdout=f"{self.repo_root}\n",
                stderr="",
            )
        if args and args[0] == "ls-remote":
            return GitCommandResult(
                args=command,
                returncode=0,
                stdout=self.listing_stdout,
                stderr="",
            )
        raise AssertionError(f"unexpected command: {args!r}")


class RemoteGitObserverTests(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = RemoteRepoFixture()

    def tearDown(self) -> None:
        self.fixture.close()

    def observe(self, target: str, *, remote_name: str = "origin"):
        return observe_remote_git(
            self.fixture.repo,
            identity(target),
            remote_name=remote_name,
        )

    def test_r01_equal_branches_and_annotated_tag(self) -> None:
        run_git(self.fixture.repo, "branch", "dev", self.fixture.base)
        run_git(
            self.fixture.repo,
            "tag",
            "-a",
            "morphe-patches-67",
            "-m",
            "release",
            self.fixture.base,
        )
        self.fixture.push("main", "refs/heads/main")
        self.fixture.push("dev", "refs/heads/dev")
        self.fixture.push(
            "refs/tags/morphe-patches-67",
            "refs/tags/morphe-patches-67",
        )

        result = self.observe(self.fixture.base)

        self.assertEqual(result.remote.main_commit, self.fixture.base)
        self.assertEqual(result.remote.dev_commit, self.fixture.base)
        self.assertEqual(result.remote.tag_commit, self.fixture.base)
        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.EQUAL,
        )
        self.assertEqual(
            result.remote.dev_relation_to_target,
            RefRelation.EQUAL,
        )
        self.assertTrue(result.tag_is_annotated)
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.errors, ())

    def test_r02_lightweight_tag_is_detected(self) -> None:
        run_git(
            self.fixture.repo,
            "tag",
            "morphe-patches-67",
            self.fixture.base,
        )
        self.fixture.push(
            "refs/tags/morphe-patches-67",
            "refs/tags/morphe-patches-67",
        )

        result = self.observe(self.fixture.base)

        self.assertEqual(result.remote.tag_commit, self.fixture.base)
        self.assertFalse(result.tag_is_annotated)

    def test_r03_remote_ancestor_relations(self) -> None:
        run_git(self.fixture.repo, "branch", "dev", self.fixture.base)
        self.fixture.push("main", "refs/heads/main")
        self.fixture.push("dev", "refs/heads/dev")
        target = write_and_commit(
            self.fixture.repo,
            "fixture.txt",
            "target\n",
            "target",
        )

        result = self.observe(target)

        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.ANCESTOR,
        )
        self.assertEqual(
            result.remote.dev_relation_to_target,
            RefRelation.ANCESTOR,
        )
        self.assertTrue(result.observations_complete)

    def test_r04_remote_ahead_relations(self) -> None:
        target = self.fixture.base
        ahead = write_and_commit(
            self.fixture.repo,
            "ahead.txt",
            "ahead\n",
            "ahead",
        )
        run_git(self.fixture.repo, "branch", "dev", ahead)
        self.fixture.push("main", "refs/heads/main")
        self.fixture.push("dev", "refs/heads/dev")

        result = self.observe(target)

        self.assertEqual(result.remote.main_commit, ahead)
        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.AHEAD,
        )
        self.assertEqual(
            result.remote.dev_relation_to_target,
            RefRelation.AHEAD,
        )

    def test_r05_remote_divergent_relations(self) -> None:
        target = write_and_commit(
            self.fixture.repo,
            "target.txt",
            "target\n",
            "target",
        )
        run_git(self.fixture.repo, "switch", "-c", "remote-line", self.fixture.base)
        remote_head = write_and_commit(
            self.fixture.repo,
            "remote.txt",
            "remote\n",
            "remote",
        )
        self.fixture.push(remote_head, "refs/heads/main")
        self.fixture.push(remote_head, "refs/heads/dev")
        run_git(self.fixture.repo, "switch", "main")

        result = self.observe(target)

        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.DIVERGENT,
        )
        self.assertEqual(
            result.remote.dev_relation_to_target,
            RefRelation.DIVERGENT,
        )

    def test_r06_unavailable_remote_object_is_unknown(self) -> None:
        publisher = self.fixture.root / "publisher"
        publisher.mkdir()
        run_git(publisher, "init", "-b", "main")
        run_git(publisher, "config", "user.name", "Publisher")
        run_git(publisher, "config", "user.email", "publisher@example.invalid")
        unknown = write_and_commit(
            publisher,
            "unknown.txt",
            "unknown\n",
            "unknown",
        )
        run_git(publisher, "remote", "add", "origin", str(self.fixture.remote))
        run_git(publisher, "push", "origin", "main:refs/heads/main")

        self.assertNotEqual(unknown, self.fixture.base)
        result = self.observe(self.fixture.base)

        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.UNKNOWN,
        )
        self.assertFalse(result.observations_complete)
        self.assertTrue(result.warnings)
        self.assertEqual(result.errors, ())

    def test_r07_no_matching_refs_is_complete_absence(self) -> None:
        result = self.observe(self.fixture.base)

        self.assertIsNone(result.remote.main_commit)
        self.assertIsNone(result.remote.dev_commit)
        self.assertIsNone(result.remote.tag_commit)
        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.ABSENT,
        )
        self.assertEqual(
            result.remote.dev_relation_to_target,
            RefRelation.ABSENT,
        )
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.errors, ())

    def test_r08_tag_only_is_observed_without_branches(self) -> None:
        run_git(
            self.fixture.repo,
            "tag",
            "morphe-patches-67",
            self.fixture.base,
        )
        self.fixture.push(
            "refs/tags/morphe-patches-67",
            "refs/tags/morphe-patches-67",
        )

        result = self.observe(self.fixture.base)

        self.assertEqual(result.remote.tag_commit, self.fixture.base)
        self.assertEqual(
            result.remote.main_relation_to_target,
            RefRelation.ABSENT,
        )
        self.assertEqual(
            result.remote.dev_relation_to_target,
            RefRelation.ABSENT,
        )
        self.assertTrue(result.observations_complete)

    def test_r09_malformed_listing_is_rejected(self) -> None:
        runner = StaticRunner(
            self.fixture.repo,
            "not-a-valid-ls-remote-line\n",
        )

        result = observe_remote_git(
            self.fixture.repo,
            identity(self.fixture.base),
            runner=runner,
        )

        self.assertFalse(result.observations_complete)
        self.assertTrue(result.errors)

    def test_r10_duplicate_ref_is_rejected(self) -> None:
        line = f"{self.fixture.base}\trefs/heads/main\n"
        runner = StaticRunner(self.fixture.repo, line + line)

        result = observe_remote_git(
            self.fixture.repo,
            identity(self.fixture.base),
            runner=runner,
        )

        self.assertFalse(result.observations_complete)
        self.assertIn(
            "remote ref listing returned duplicate ref: refs/heads/main",
            result.errors,
        )

    def test_r11_missing_git_executable_is_reported(self) -> None:
        result = observe_remote_git(
            self.fixture.repo,
            identity(self.fixture.base),
            runner=SubprocessGitRunner(
                git_executable="definitely-missing-morphe-git"
            ),
        )

        self.assertIsNone(result.repo_root)
        self.assertFalse(result.observations_complete)
        self.assertTrue(result.errors)

    def test_r12_missing_remote_is_reported(self) -> None:
        result = self.observe(
            self.fixture.base,
            remote_name="does-not-exist",
        )

        self.assertFalse(result.observations_complete)
        self.assertTrue(result.errors)

    def test_r13_invalid_remote_name_is_rejected_without_git(self) -> None:
        result = observe_remote_git(
            self.fixture.repo,
            identity(self.fixture.base),
            remote_name=" --upload-pack=bad ",
        )

        self.assertIsNone(result.repo_root)
        self.assertFalse(result.observations_complete)
        self.assertEqual(
            result.errors,
            ("remote name must be non-empty and must not start with '-'",),
        )

    def test_r14_result_serializes_with_stable_values(self) -> None:
        result = self.observe(self.fixture.base)
        payload = result.as_dict()

        self.assertEqual(payload["remote_name"], "origin")
        self.assertEqual(
            payload["remote"]["main_relation_to_target"],
            "ABSENT",
        )
        self.assertEqual(
            payload["remote"]["dev_relation_to_target"],
            "ABSENT",
        )

    def test_r15_repo_root_is_canonical_from_subdirectory(self) -> None:
        nested = self.fixture.repo / "nested" / "directory"
        nested.mkdir(parents=True)

        result = observe_remote_git(
            nested,
            identity(self.fixture.base),
        )

        self.assertEqual(
            Path(result.repo_root or "").resolve(),
            self.fixture.repo.resolve(),
        )

    def test_r16_observer_does_not_mutate_repository(self) -> None:
        run_git(self.fixture.repo, "branch", "dev", self.fixture.base)
        self.fixture.push("main", "refs/heads/main")
        self.fixture.push("dev", "refs/heads/dev")

        before_refs = run_git(self.fixture.repo, "show-ref")
        before_status = run_git(
            self.fixture.repo,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
        )
        before_objects = run_git(self.fixture.repo, "count-objects", "-v")
        fetch_head = self.fixture.repo / ".git" / "FETCH_HEAD"
        before_fetch_head = fetch_head.read_bytes() if fetch_head.exists() else None

        result = self.observe(self.fixture.base)

        after_refs = run_git(self.fixture.repo, "show-ref")
        after_status = run_git(
            self.fixture.repo,
            "status",
            "--porcelain=v1",
            "--untracked-files=all",
        )
        after_objects = run_git(self.fixture.repo, "count-objects", "-v")
        after_fetch_head = fetch_head.read_bytes() if fetch_head.exists() else None

        self.assertTrue(result.observations_complete)
        self.assertEqual(after_refs, before_refs)
        self.assertEqual(after_status, before_status)
        self.assertEqual(after_objects, before_objects)
        self.assertEqual(after_fetch_head, before_fetch_head)


if __name__ == "__main__":
    unittest.main()
