#!/usr/bin/env python3
from __future__ import annotations

from contextlib import redirect_stdout
from dataclasses import replace
import hashlib
import importlib.util
from io import BytesIO, StringIO
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_integration", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GhBinaryCommandResult = MODULE.GhBinaryCommandResult
GhCommandResult = MODULE.GhCommandResult
GpgCommandResult = MODULE.GpgCommandResult
PlanDisposition = MODULE.PlanDisposition
ReleaseIdentity = MODULE.ReleaseIdentity
ReleaseState = MODULE.ReleaseState
VerifierStatus = MODULE.VerifierStatus
inspect_release = MODULE.inspect_release
inspection_payload = MODULE.inspection_payload
render_inspection_text = MODULE.render_inspection_text

REPOSITORY = "brealorg/breal-morphe-patches"
SIGNER = "475CF5EB633BF19031A40E64A56106614B7AD500"
VERSION = "1.4.67"
TAG = "morphe-patches-67"
MPP_NAME = f"patches-{VERSION}.mpp"
SIG_NAME = f"{MPP_NAME}.asc"
SIGNATURE = b"detached-signature"


def run(*args: str, cwd: Path) -> str:
    completed = subprocess.run(
        args,
        cwd=cwd,
        check=True,
        text=True,
        capture_output=True,
    )
    return completed.stdout.strip()


def build_mpp() -> bytes:
    output = BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("classes.dex", b"dex")
        archive.writestr("extensions/boostforreddit.mpe", b"boost")
    return output.getvalue()


class FakeGpgRunner:
    def run(self, mpp_path: Path, signature_path: Path) -> GpgCommandResult:
        return GpgCommandResult(
            args=("fake-gpg",),
            returncode=0,
            stdout=f"[GNUPG:] VALIDSIG {SIGNER} 2026-07-10 0 4 0 1 10 00 {SIGNER}\n",
            stderr="",
        )


class FakeGhRunner:
    def __init__(self, listing: list[list[dict[str, object]]]) -> None:
        self.listing = listing
        self.calls: list[tuple[str, ...]] = []

    def run(self, args) -> GhCommandResult:
        call = tuple(args)
        self.calls.append(call)
        if call[-1] == f"repos/{REPOSITORY}":
            payload = {
                "full_name": REPOSITORY,
                "permissions": {"pull": True, "push": True, "admin": True},
            }
        else:
            payload = self.listing
        return GhCommandResult(
            args=("fake-gh", *call),
            returncode=0,
            stdout=json.dumps(payload),
            stderr="",
        )


class FakeBinaryRunner:
    def __init__(self, mpp: bytes) -> None:
        self.mpp = mpp
        self.calls: list[tuple[str, ...]] = []

    def run(self, args) -> GhBinaryCommandResult:
        call = tuple(args)
        self.calls.append(call)
        data = self.mpp if call[-1].endswith("/101") else SIGNATURE
        return GhBinaryCommandResult(
            args=("fake-gh", *call),
            returncode=0,
            stdout=data,
            stderr="",
        )


class ReleaseFixture:
    def __init__(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.remote = self.root / "remote.git"
        self.repo.mkdir()
        run("git", "init", "-b", "main", cwd=self.repo)
        run("git", "config", "user.name", "Morphe Test", cwd=self.repo)
        run("git", "config", "user.email", "test@example.invalid", cwd=self.repo)

        self.mpp = build_mpp()
        self.sha = hashlib.sha256(self.mpp).hexdigest()
        libs = self.repo / "patches" / "build" / "libs"
        libs.mkdir(parents=True)
        (libs / MPP_NAME).write_bytes(self.mpp)
        (libs / SIG_NAME).write_bytes(SIGNATURE)

        (self.repo / ".gitignore").write_text("patches/build/\n", encoding="utf-8")
        download = (
            f"https://github.com/{REPOSITORY}/releases/download/{TAG}/{MPP_NAME}"
        )
        manager = (
            f"https://raw.githubusercontent.com/{REPOSITORY}/main/patches-bundle.json"
        )
        (self.repo / "README.md").write_text(
            f"""# Morphe patches

## Current release

| Field | Value |
|---|---|
| Version | `{VERSION}` |
| Release tag | `{TAG}` |
| Asset | `{MPP_NAME}` |
| SHA256 | `{self.sha}` |

SHA256: `{self.sha}`
| Manager JSON | `{manager}` |
| Download URL | `{download}` |

## Development notes
""",
            encoding="utf-8",
        )
        (self.repo / "patches-bundle.json").write_text(
            json.dumps(
                {
                    "created_at": "2026-07-10T20:00:00",
                    "description": "Read-only release state integration fixture.\n",
                    "download_url": download,
                    "signature_download_url": f"{download}.asc",
                    "version": VERSION,
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        run("git", "add", ".gitignore", "README.md", "patches-bundle.json", cwd=self.repo)
        run("git", "commit", "-m", "Release fixture", cwd=self.repo)
        self.commit = run("git", "rev-parse", "HEAD", cwd=self.repo)
        run("git", "branch", "dev", cwd=self.repo)
        run("git", "tag", "-a", TAG, "-m", TAG, cwd=self.repo)
        run("git", "init", "--bare", str(self.remote), cwd=self.root)
        run("git", "remote", "add", "origin", str(self.remote), cwd=self.repo)
        run("git", "push", "origin", "main", "dev", TAG, cwd=self.repo)

    def close(self) -> None:
        self.temp.cleanup()

    def identity(self) -> ReleaseIdentity:
        return ReleaseIdentity(
            version=VERSION,
            tag=TAG,
            release_commit=self.commit,
            mpp_asset_name=MPP_NAME,
            signature_asset_name=SIG_NAME,
            mpp_sha256=self.sha,
            signing_identity=SIGNER,
        )

    def release(self, *, assets: list[dict[str, object]] | None = None) -> dict[str, object]:
        if assets is None:
            assets = [
                {
                    "id": 101,
                    "name": MPP_NAME,
                    "state": "uploaded",
                    "digest": f"sha256:{self.sha}",
                },
                {
                    "id": 102,
                    "name": SIG_NAME,
                    "state": "uploaded",
                    "digest": None,
                },
            ]
        return {
            "id": 67,
            "tag_name": TAG,
            "draft": False,
            "prerelease": False,
            "immutable": False,
            "target_commitish": self.commit,
            "html_url": f"https://github.com/{REPOSITORY}/releases/tag/{TAG}",
            "assets": assets,
        }

    def inspect(self, *, releases: list[dict[str, object]] | None = None):
        if releases is None:
            releases = [self.release()]
        return inspect_release(
            self.repo,
            REPOSITORY,
            self.identity(),
            gh_runner=FakeGhRunner([releases]),
            gh_binary_runner=FakeBinaryRunner(self.mpp),
            gpg_runner=FakeGpgRunner(),
        )


class ReleaseInspectionIntegrationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.fixture = ReleaseFixture()

    def tearDown(self) -> None:
        self.fixture.close()

    def test_i01_completed_release_is_published_and_verified(self) -> None:
        result = self.fixture.inspect()
        self.assertEqual(result.state_result.state, ReleaseState.PUBLISHED_AND_VERIFIED)
        self.assertEqual(
            result.observations.verification.full_verifier_status,
            VerifierStatus.PASS,
        )
        self.assertTrue(result.state_result.observations_complete)
        self.assertEqual(result.plan.disposition, PlanDisposition.NOOP_ALREADY_SATISFIED)
        self.assertEqual(result.plan.operations, ())

    def test_i02_remote_tag_without_release_is_partial(self) -> None:
        result = self.fixture.inspect(releases=[])
        self.assertEqual(result.state_result.state, ReleaseState.PARTIALLY_PUBLISHED)
        self.assertFalse(result.remote_assets.attempted)
        self.assertEqual(
            result.observations.verification.full_verifier_status,
            VerifierStatus.NOT_RUN,
        )

    def test_i03_published_release_missing_signature_is_inconsistent(self) -> None:
        assets = [
            {
                "id": 101,
                "name": MPP_NAME,
                "state": "uploaded",
                "digest": f"sha256:{self.fixture.sha}",
            }
        ]
        result = self.fixture.inspect(releases=[self.fixture.release(assets=assets)])
        self.assertEqual(result.state_result.state, ReleaseState.INCONSISTENT_ABORT)
        self.assertTrue(
            any("missing the expected signature asset" in item for item in result.state_result.reasons)
        )

    def test_i04_repeated_inspection_is_deterministic_and_read_only(self) -> None:
        head_before = run("git", "rev-parse", "HEAD", cwd=self.fixture.repo)
        status_before = run("git", "status", "--porcelain", cwd=self.fixture.repo)
        refs_before = run("git", "show-ref", cwd=self.fixture.remote)

        first = self.fixture.inspect()
        second = self.fixture.inspect()

        self.assertEqual(first.plan.plan_hash, second.plan.plan_hash)
        self.assertEqual(
            first.plan.observation_fingerprint,
            second.plan.observation_fingerprint,
        )
        self.assertEqual(run("git", "rev-parse", "HEAD", cwd=self.fixture.repo), head_before)
        self.assertEqual(run("git", "status", "--porcelain", cwd=self.fixture.repo), status_before)
        self.assertEqual(run("git", "show-ref", cwd=self.fixture.remote), refs_before)

    def test_i05_text_and_json_represent_same_state(self) -> None:
        result = self.fixture.inspect()
        text = render_inspection_text("inspect", result)
        payload = inspection_payload("inspect", result)
        state_line = next(line for line in text.splitlines() if line.startswith("STATE="))
        next_line = next(
            line for line in text.splitlines() if line.startswith("NEXT_ACTION=")
        )
        self.assertEqual(state_line.split("=", 1)[1], payload["state"])
        self.assertEqual(next_line.split("=", 1)[1], payload["next_action"])

    def test_i06_text_contains_machine_readable_remote_evidence(self) -> None:
        text = render_inspection_text("inspect", self.fixture.inspect())
        self.assertIn(f"REMOTE_MPP_SHA256={self.fixture.sha}", text)
        self.assertIn("REMOTE_SIGNATURE_VALID=True", text)
        self.assertIn("FULL_VERIFIER_STATUS=PASS", text)
        self.assertIn("RESULT=MORPHE_RELEASE_STATE_INSPECT_OK", text)

    def test_i07_json_contains_all_observer_sources(self) -> None:
        payload = inspection_payload("inspect", self.fixture.inspect())
        for key in (
            "local_git",
            "remote_git",
            "github_release",
            "local_artifacts",
            "local_metadata",
            "remote_assets",
        ):
            self.assertIn(key, payload)
        self.assertEqual(payload["schema_version"], 1)

    def test_i08_plan_text_lists_no_operations_for_completed_release(self) -> None:
        text = render_inspection_text("plan", self.fixture.inspect())
        self.assertIn("PLAN_DISPOSITION=NOOP_ALREADY_SATISFIED", text)
        self.assertNotIn("PLAN_OPERATION=", text)

    def test_i09_cli_inspect_json_uses_same_normalized_result(self) -> None:
        inspection = self.fixture.inspect()
        argv = [
            "inspect",
            "--version",
            VERSION,
            "--tag",
            TAG,
            "--release-commit",
            self.fixture.commit,
            "--mpp-sha256",
            self.fixture.sha,
            "--signing-identity",
            SIGNER,
            "--json",
        ]
        output = StringIO()
        with patch.object(MODULE, "inspect_release", return_value=inspection):
            with redirect_stdout(output):
                rc = MODULE.main(argv)
        self.assertEqual(rc, 0)
        payload = json.loads(output.getvalue())
        self.assertEqual(payload["state"], "PUBLISHED_AND_VERIFIED")
        self.assertEqual(payload["next_action"], "NONE")

    def test_i10_cli_verify_requires_verified_state(self) -> None:
        completed = self.fixture.inspect()
        partial = self.fixture.inspect(releases=[])
        base = [
            "verify",
            "--version",
            VERSION,
            "--tag",
            TAG,
            "--release-commit",
            self.fixture.commit,
            "--mpp-sha256",
            self.fixture.sha,
            "--signing-identity",
            SIGNER,
        ]
        with patch.object(MODULE, "inspect_release", return_value=completed):
            with redirect_stdout(StringIO()):
                self.assertEqual(MODULE.main(base), 0)
        with patch.object(MODULE, "inspect_release", return_value=partial):
            with redirect_stdout(StringIO()):
                self.assertEqual(MODULE.main(base), 5)

    def test_i11_cli_conflict_uses_dedicated_exit_code(self) -> None:
        assets = [
            {
                "id": 101,
                "name": MPP_NAME,
                "state": "uploaded",
                "digest": f"sha256:{self.fixture.sha}",
            }
        ]
        conflict = self.fixture.inspect(releases=[self.fixture.release(assets=assets)])
        argv = [
            "inspect",
            "--version",
            VERSION,
            "--tag",
            TAG,
            "--release-commit",
            self.fixture.commit,
            "--mpp-sha256",
            self.fixture.sha,
            "--signing-identity",
            SIGNER,
        ]
        with patch.object(MODULE, "inspect_release", return_value=conflict):
            with redirect_stdout(StringIO()):
                self.assertEqual(MODULE.main(argv), 3)

    def test_i12_work_branch_does_not_change_completed_state(self) -> None:
        run("git", "switch", "-c", "work/test-inspect", cwd=self.fixture.repo)
        result = self.fixture.inspect()
        self.assertEqual(result.state_result.state, ReleaseState.PUBLISHED_AND_VERIFIED)
        self.assertFalse(result.state_result.safe_to_mutate)
        self.assertTrue(result.state_result.observations_complete)


if __name__ == "__main__":
    unittest.main()
