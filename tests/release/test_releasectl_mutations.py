#!/usr/bin/env python3
from __future__ import annotations

from io import BytesIO
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_mutations", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


SIGNER = MODULE.DEFAULT_SIGNING_IDENTITY
VERSION = "1.4.67"
TAG = "morphe-patches-67"


def run(*args: str, cwd: Path) -> str:
    completed = subprocess.run(args, cwd=cwd, check=True, text=True, capture_output=True)
    return completed.stdout.strip()


def build_mpp() -> bytes:
    output = BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("classes.dex", b"dex")
        archive.writestr("extensions/boostforreddit.mpe", b"boost")
    return output.getvalue()


class GitFixture:
    def __init__(self, *, remote_has_refs: bool = False) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.remote = self.root / "remote.git"
        self.repo.mkdir()
        run("git", "init", "-b", "main", cwd=self.repo)
        run("git", "config", "user.name", "Morphe Test", cwd=self.repo)
        run("git", "config", "user.email", "test@example.invalid", cwd=self.repo)
        (self.repo / "README.md").write_text("initial\n", encoding="utf-8")
        run("git", "add", "README.md", cwd=self.repo)
        run("git", "commit", "-m", "initial", cwd=self.repo)
        self.base = run("git", "rev-parse", "HEAD", cwd=self.repo)
        run("git", "branch", "dev", cwd=self.repo)
        (self.repo / "README.md").write_text("release\n", encoding="utf-8")
        run("git", "add", "README.md", cwd=self.repo)
        run("git", "commit", "-m", "release", cwd=self.repo)
        self.release = run("git", "rev-parse", "HEAD", cwd=self.repo)
        run("git", "tag", "-a", TAG, "-m", TAG, cwd=self.repo)
        run("git", "init", "--bare", str(self.remote), cwd=self.root)
        run("git", "remote", "add", "origin", str(self.remote), cwd=self.repo)
        if remote_has_refs:
            run("git", "push", "origin", "main", "dev", TAG, cwd=self.repo)

        mpp = build_mpp()
        libs = self.repo / "patches" / "build" / "libs"
        libs.mkdir(parents=True)
        self.mpp_path = libs / f"patches-{VERSION}.mpp"
        self.mpp_path.write_bytes(mpp)
        self.sig_path = Path(str(self.mpp_path) + ".asc")
        self.sig_path.write_text("signature", encoding="utf-8")
        self.sha = hashlib.sha256(mpp).hexdigest()
        (self.repo / "README.md").write_text(
            f"## Current release\nVersion {VERSION}\nTag {TAG}\nSHA256 `{self.sha}`\n",
            encoding="utf-8",
        )
        run("git", "add", "README.md", cwd=self.repo)
        run("git", "commit", "-m", "metadata", cwd=self.repo)
        self.release = run("git", "rev-parse", "HEAD", cwd=self.repo)
        run("git", "tag", "-d", TAG, cwd=self.repo)
        run("git", "tag", "-a", TAG, "-m", TAG, cwd=self.repo)

    def close(self) -> None:
        self.temp.cleanup()


class ReleaseMutationHelperTests(unittest.TestCase):
    def test_t01_transaction_log_round_trip(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "release.jsonl"
            MODULE.append_transaction_entry(
                path,
                command="publish",
                phase="PUSH_RELEASE_REFS_ATOMIC",
                status="COMPLETED",
                version=VERSION,
                tag=TAG,
                release_commit="a" * 40,
                mpp_sha256="b" * 64,
                details={"plan_hash": "sha256:test"},
            )
            entries = MODULE.read_transaction_log(path)
            self.assertEqual(len(entries), 1)
            self.assertEqual(entries[0].phase, "PUSH_RELEASE_REFS_ATOMIC")
            self.assertEqual(entries[0].details["plan_hash"], "sha256:test")

    def test_t02_transaction_log_is_json_lines(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "release.jsonl"
            for phase in ("START", "COMPLETE"):
                MODULE.append_transaction_entry(
                    path,
                    command="finalize",
                    phase=phase,
                    status="COMPLETED",
                    version=VERSION,
                    tag=TAG,
                )
            lines = path.read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 2)
            self.assertTrue(all(isinstance(json.loads(line), dict) for line in lines))

    def test_t03_invalid_transaction_log_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "release.jsonl"
            path.write_text("not-json\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                MODULE.read_transaction_log(path)

    def test_t04_transaction_path_rejects_traversal(self) -> None:
        with self.assertRaises(ValueError):
            MODULE.transaction_log_path(Path("/tmp/repo"), "../bad")

    def test_t05_derive_existing_identity_uses_canonical_artifact(self) -> None:
        fixture = GitFixture()
        self.addCleanup(fixture.close)
        identity = MODULE.derive_existing_release_identity(
            fixture.repo,
            version=VERSION,
            tag=TAG,
            signing_identity=SIGNER,
        )
        self.assertEqual(identity.release_commit, fixture.release)
        self.assertEqual(identity.mpp_sha256, fixture.sha)

    def test_t06_align_local_dev_is_transactional(self) -> None:
        fixture = GitFixture()
        self.addCleanup(fixture.close)
        identity = MODULE.derive_existing_release_identity(
            fixture.repo,
            version=VERSION,
            tag=TAG,
            signing_identity=SIGNER,
        )
        self.assertNotEqual(run("git", "rev-parse", "dev", cwd=fixture.repo), identity.release_commit)
        MODULE._align_local_dev(fixture.repo, identity, MODULE.SubprocessMutationRunner())
        self.assertEqual(run("git", "rev-parse", "dev", cwd=fixture.repo), identity.release_commit)
        self.assertEqual(run("git", "rev-parse", "main", cwd=fixture.repo), identity.release_commit)

    def test_t07_atomic_push_requires_main_and_publishes_dev_and_tag(self) -> None:
        fixture = GitFixture()
        self.addCleanup(fixture.close)
        identity = MODULE.derive_existing_release_identity(
            fixture.repo,
            version=VERSION,
            tag=TAG,
            signing_identity=SIGNER,
        )
        MODULE._align_local_dev(fixture.repo, identity, MODULE.SubprocessMutationRunner())
        run("git", "push", "origin", "main:refs/heads/main", cwd=fixture.repo)
        MODULE._atomic_push_release_refs(
            fixture.repo,
            identity,
            "origin",
            MODULE.SubprocessMutationRunner(),
        )
        listing = run(
            "git", "ls-remote", "origin", "refs/heads/main", "refs/heads/dev", f"refs/tags/{TAG}^{{}}", cwd=fixture.repo
        )
        self.assertEqual(listing.count(identity.release_commit), 3)

    def test_t07b_atomic_push_aborts_when_remote_main_is_not_release_commit(self) -> None:
        fixture = GitFixture()
        self.addCleanup(fixture.close)
        identity = MODULE.derive_existing_release_identity(
            fixture.repo,
            version=VERSION,
            tag=TAG,
            signing_identity=SIGNER,
        )
        MODULE._align_local_dev(
            fixture.repo,
            identity,
            MODULE.SubprocessMutationRunner(),
        )
        with self.assertRaisesRegex(
            RuntimeError,
            "remote main must already equal the release commit",
        ):
            MODULE._atomic_push_release_refs(
                fixture.repo,
                identity,
                "origin",
                MODULE.SubprocessMutationRunner(),
            )
        self.assertEqual(
            run("git", "ls-remote", "origin", cwd=fixture.repo),
            "",
        )

    def test_t08_wrapper_scripts_are_thin(self) -> None:
        finalize = (ROOT / "scripts" / "release-finalize-local.sh").read_text(encoding="utf-8")
        publish = (ROOT / "scripts" / "release-publish-existing.sh").read_text(encoding="utf-8")
        self.assertIn('releasectl.py" finalize "$@"', finalize)
        self.assertIn('releasectl.py" publish "$@"', publish)
        for text in (finalize, publish):
            self.assertNotIn("gh release", text)
            self.assertNotIn("git push", text)
            self.assertNotIn("gradlew", text)

    def test_t09_metadata_repair_never_force_moves_tag(self) -> None:
        text = (ROOT / "scripts" / "release-repair-metadata-only.sh").read_text(encoding="utf-8")
        self.assertNotIn("git tag -f", text)
        self.assertNotIn("push --force", text)
        self.assertNotIn("git push", text)
        self.assertIn("direct metadata repair publication is retired", text)
        self.assertIn("immutable release tag and published assets must remain unchanged", text)

    def test_t10_cli_exposes_mutating_commands(self) -> None:
        completed = subprocess.run(
            [sys.executable, str(SCRIPT), "--help"],
            cwd=ROOT,
            check=True,
            text=True,
            capture_output=True,
        )
        for command in ("finalize", "publish", "resume"):
            self.assertIn(command, completed.stdout)

    def test_t11_upload_helper_never_clobbers(self) -> None:
        class Runner:
            def __init__(self) -> None:
                self.calls = []
            def run(self, repo_path, args):
                self.calls.append(tuple(args))
                return MODULE.MutationCommandResult(tuple(args), 0, "", "")
        runner = Runner()
        identity = MODULE.ReleaseIdentity(
            VERSION, TAG, "a" * 40, f"patches-{VERSION}.mpp",
            f"patches-{VERSION}.mpp.asc", "b" * 64, SIGNER,
        )
        MODULE._upload_release_asset(Path("/tmp"), identity, Path("/tmp/asset.mpp"), runner)
        call = runner.calls[0]
        self.assertNotIn("--clobber", call)
        self.assertEqual(call[:3], ("gh", "release", "upload"))

    def test_t12_draft_creation_is_explicit(self) -> None:
        class Runner:
            def __init__(self) -> None:
                self.calls = []
            def run(self, repo_path, args):
                self.calls.append(tuple(args))
                return MODULE.MutationCommandResult(tuple(args), 0, "{}", "")
        runner = Runner()
        identity = MODULE.ReleaseIdentity(
            VERSION, TAG, "a" * 40, f"patches-{VERSION}.mpp",
            f"patches-{VERSION}.mpp.asc", "b" * 64, SIGNER,
        )
        MODULE._create_draft_release(Path("/tmp"), "owner/repo", identity, Path("/tmp/notes"), runner)
        call = runner.calls[0]
        self.assertIn("draft=true", call)
        self.assertIn("prerelease=false", call)
        self.assertNotIn("gh release create", " ".join(call))


if __name__ == "__main__":
    unittest.main()
