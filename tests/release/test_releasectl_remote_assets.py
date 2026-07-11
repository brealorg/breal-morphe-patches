#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import FrozenInstanceError
import hashlib
import importlib.util
from io import BytesIO
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch
import zipfile


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_remote_assets", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GITHUB_API_VERSION = MODULE.GITHUB_API_VERSION
GhBinaryCommandResult = MODULE.GhBinaryCommandResult
GitHubReleaseObservationResult = MODULE.GitHubReleaseObservationResult
GitHubReleaseObservations = MODULE.GitHubReleaseObservations
GpgCommandResult = MODULE.GpgCommandResult
ReleaseIdentity = MODULE.ReleaseIdentity
SubprocessGhBinaryRunner = MODULE.SubprocessGhBinaryRunner
VerifierStatus = MODULE.VerifierStatus
observe_remote_assets = MODULE.observe_remote_assets

REPOSITORY = "brealorg/breal-morphe-patches"
SIGNER = "475CF5EB633BF19031A40E64A56106614B7AD500"


def mpp_bytes(*, include_boost: bool = True) -> bytes:
    output = BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("classes.dex", b"dex")
        if include_boost:
            archive.writestr("extensions/boostforreddit.mpe", b"boost")
    return output.getvalue()


GOOD_MPP = mpp_bytes()
GOOD_SHA = hashlib.sha256(GOOD_MPP).hexdigest()
SIGNATURE = b"detached-signature"


def identity(*, sha256: str = GOOD_SHA) -> ReleaseIdentity:
    return ReleaseIdentity(
        version="1.4.67",
        tag="morphe-patches-67",
        release_commit="a" * 40,
        mpp_asset_name="patches-1.4.67.mpp",
        signature_asset_name="patches-1.4.67.mpp.asc",
        mpp_sha256=sha256,
        signing_identity=SIGNER,
    )


def release_result(
    *,
    present: bool = True,
    mpp_ids: tuple[int, ...] = (101,),
    signature_ids: tuple[int, ...] = (102,),
    complete: bool = True,
) -> GitHubReleaseObservationResult:
    return GitHubReleaseObservationResult(
        repository=REPOSITORY,
        viewer_can_push=True,
        github=GitHubReleaseObservations(
            present=present,
            tag="morphe-patches-67" if present else None,
            is_draft=False if present else None,
            is_prerelease=False if present else None,
            mpp_asset_count=len(mpp_ids),
            signature_asset_count=len(signature_ids),
            mpp_asset_digest=GOOD_SHA if len(mpp_ids) == 1 else None,
        ),
        release_id=67 if present else None,
        is_immutable=False if present else None,
        target_commitish="a" * 40 if present else None,
        html_url="https://example.invalid/release" if present else None,
        mpp_asset_ids=mpp_ids,
        signature_asset_ids=signature_ids,
        observations_complete=complete,
    )


class StaticBinaryRunner:
    def __init__(
        self,
        *,
        mpp: bytes = GOOD_MPP,
        signature: bytes = SIGNATURE,
        mpp_rc: int = 0,
        signature_rc: int = 0,
    ) -> None:
        self.mpp = mpp
        self.signature = signature
        self.mpp_rc = mpp_rc
        self.signature_rc = signature_rc
        self.calls: list[tuple[str, ...]] = []

    def run(self, args):
        call = tuple(args)
        self.calls.append(call)
        asset_id = call[-1].rsplit("/", 1)[-1]
        if asset_id == "101":
            return GhBinaryCommandResult(
                args=("fake-gh", *call),
                returncode=self.mpp_rc,
                stdout=self.mpp if self.mpp_rc == 0 else b"",
                stderr="mpp failed" if self.mpp_rc else "",
            )
        return GhBinaryCommandResult(
            args=("fake-gh", *call),
            returncode=self.signature_rc,
            stdout=self.signature if self.signature_rc == 0 else b"",
            stderr="signature failed" if self.signature_rc else "",
        )


class FakeGpgRunner:
    def __init__(self, *, mode: str = "valid") -> None:
        self.mode = mode
        self.calls: list[tuple[Path, Path]] = []

    def run(self, mpp_path: Path, signature_path: Path) -> GpgCommandResult:
        self.calls.append((mpp_path, signature_path))
        if self.mode == "valid":
            return GpgCommandResult(
                args=("fake-gpg",),
                returncode=0,
                stdout=f"[GNUPG:] VALIDSIG {SIGNER} 2026-07-10 0 4 0 1 10 00 {SIGNER}\n",
                stderr="",
            )
        if self.mode == "bad":
            return GpgCommandResult(
                args=("fake-gpg",),
                returncode=1,
                stdout="[GNUPG:] BADSIG DEADBEEF bad\n",
                stderr="bad signature",
            )
        if self.mode == "no-key":
            return GpgCommandResult(
                args=("fake-gpg",),
                returncode=2,
                stdout="[GNUPG:] NO_PUBKEY DEADBEEF\n",
                stderr="no public key",
            )
        raise AssertionError(self.mode)


class RemoteAssetObserverTests(unittest.TestCase):
    def test_v01_valid_remote_assets_pass(self) -> None:
        binary = StaticBinaryRunner()
        gpg = FakeGpgRunner()
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=binary,
            gpg_runner=gpg,
        )

        self.assertTrue(result.attempted)
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.github.remote_mpp_sha256, GOOD_SHA)
        self.assertTrue(result.github.remote_signature_valid)
        self.assertTrue(result.github.remote_mpp_structure_valid)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.PASS)
        self.assertEqual(result.mpp_size, len(GOOD_MPP))
        self.assertEqual(result.signature_size, len(SIGNATURE))
        self.assertEqual(len(binary.calls), 2)
        self.assertEqual(len(gpg.calls), 1)

    def test_v02_absent_release_is_known_noop(self) -> None:
        binary = StaticBinaryRunner()
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(present=False, mpp_ids=(), signature_ids=()),
            binary_runner=binary,
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.attempted)
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.NOT_RUN)
        self.assertEqual(binary.calls, [])

    def test_v03_partial_asset_set_is_not_downloaded(self) -> None:
        binary = StaticBinaryRunner()
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(signature_ids=()),
            binary_runner=binary,
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.attempted)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.NOT_RUN)
        self.assertEqual(binary.calls, [])

    def test_v04_duplicate_asset_set_is_not_downloaded(self) -> None:
        binary = StaticBinaryRunner()
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(mpp_ids=(101, 103)),
            binary_runner=binary,
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.attempted)
        self.assertEqual(binary.calls, [])

    def test_v05_mpp_download_failure_is_unavailable(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=StaticBinaryRunner(mpp_rc=1),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.observations_complete)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.UNAVAILABLE)
        self.assertTrue(any("remote MPP download" in item for item in result.errors))

    def test_v06_signature_download_failure_is_unavailable(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=StaticBinaryRunner(signature_rc=1),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.observations_complete)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.UNAVAILABLE)

    def test_v07_empty_download_is_unavailable(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=StaticBinaryRunner(mpp=b""),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.observations_complete)
        self.assertTrue(any("empty body" in item for item in result.errors))

    def test_v08_sha_mismatch_is_concrete_failure(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(sha256="f" * 64),
            release_result(),
            binary_runner=StaticBinaryRunner(),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertTrue(result.observations_complete)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.FAIL)
        self.assertEqual(result.github.remote_mpp_sha256, GOOD_SHA)

    def test_v09_invalid_zip_is_concrete_failure(self) -> None:
        bad = b"not-a-zip"
        result = observe_remote_assets(
            REPOSITORY,
            identity(sha256=hashlib.sha256(bad).hexdigest()),
            release_result(),
            binary_runner=StaticBinaryRunner(mpp=bad),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.FAIL)
        self.assertFalse(result.github.remote_mpp_structure_valid)

    def test_v10_missing_required_entry_is_concrete_failure(self) -> None:
        incomplete = mpp_bytes(include_boost=False)
        result = observe_remote_assets(
            REPOSITORY,
            identity(sha256=hashlib.sha256(incomplete).hexdigest()),
            release_result(),
            binary_runner=StaticBinaryRunner(mpp=incomplete),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.FAIL)
        self.assertEqual(result.missing_entries, ("extensions/boostforreddit.mpe",))

    def test_v11_bad_signature_is_concrete_failure(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=StaticBinaryRunner(),
            gpg_runner=FakeGpgRunner(mode="bad"),
        )
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.FAIL)
        self.assertFalse(result.github.remote_signature_valid)

    def test_v12_missing_public_key_is_unavailable(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=StaticBinaryRunner(),
            gpg_runner=FakeGpgRunner(mode="no-key"),
        )
        self.assertFalse(result.observations_complete)
        self.assertEqual(result.verification.full_verifier_status, VerifierStatus.UNAVAILABLE)
        self.assertIsNone(result.github.remote_signature_valid)

    def test_v13_download_command_is_explicit_read_only_and_versioned(self) -> None:
        binary = StaticBinaryRunner()
        observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=binary,
            gpg_runner=FakeGpgRunner(),
        )
        expected_prefix = (
            "api",
            "--method",
            "GET",
            "--header",
            "Accept: application/octet-stream",
            "--header",
            f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
        )
        self.assertEqual(binary.calls[0][:-1], expected_prefix)
        self.assertTrue(binary.calls[0][-1].endswith("/101"))
        self.assertTrue(binary.calls[1][-1].endswith("/102"))

    def test_v14_result_is_immutable_and_serializes(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(),
            binary_runner=StaticBinaryRunner(),
            gpg_runner=FakeGpgRunner(),
        )
        with self.assertRaises(FrozenInstanceError):
            result.attempted = False  # type: ignore[misc]
        payload = result.as_dict()
        self.assertEqual(payload["verification"]["full_verifier_status"], "PASS")
        self.assertEqual(payload["github"]["remote_mpp_sha256"], GOOD_SHA)

    def test_v15_incomplete_release_metadata_stays_incomplete(self) -> None:
        result = observe_remote_assets(
            REPOSITORY,
            identity(),
            release_result(complete=False),
            binary_runner=StaticBinaryRunner(),
            gpg_runner=FakeGpgRunner(),
        )
        self.assertFalse(result.observations_complete)
        self.assertEqual(
            result.verification.full_verifier_status,
            VerifierStatus.UNAVAILABLE,
        )

    def test_v16_subprocess_binary_runner_disables_prompts(self) -> None:
        completed = subprocess.CompletedProcess(
            args=("gh", "api"),
            returncode=0,
            stdout=b"data",
            stderr=b"",
        )
        with patch.object(MODULE.subprocess, "run", return_value=completed) as mocked:
            result = SubprocessGhBinaryRunner().run(("api", "endpoint"))
        self.assertEqual(result.stdout, b"data")
        env = mocked.call_args.kwargs["env"]
        self.assertEqual(env["GH_PROMPT_DISABLED"], "1")
        self.assertEqual(env["GH_PAGER"], "cat")
        self.assertFalse(mocked.call_args.kwargs.get("text", False))


if __name__ == "__main__":
    unittest.main()
