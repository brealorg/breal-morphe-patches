#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import FrozenInstanceError
import hashlib
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
import warnings
import zipfile


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GpgCommandResult = MODULE.GpgCommandResult
LocalArtifactObservationResult = MODULE.LocalArtifactObservationResult
ReleaseIdentity = MODULE.ReleaseIdentity
REQUIRED_MPP_ENTRIES = MODULE.REQUIRED_MPP_ENTRIES
observe_local_artifacts = MODULE.observe_local_artifacts


TARGET = "a" * 40
EXPECTED_SHA = "b" * 64
SIGNER = "0123456789ABCDEF0123456789ABCDEF01234567"
OTHER_SIGNER = "89ABCDEF0123456789ABCDEF0123456789ABCDEF"


def identity(*, sha256: str = EXPECTED_SHA, mpp_name: str = "patches-1.4.67.mpp") -> ReleaseIdentity:
    return ReleaseIdentity(
        version="1.4.67",
        tag="morphe-patches-67",
        release_commit=TARGET,
        mpp_asset_name=mpp_name,
        signature_asset_name=f"{mpp_name}.asc",
        mpp_sha256=sha256,
        signing_identity=SIGNER,
    )


class FakeGpgRunner:
    def __init__(
        self,
        *,
        returncode: int = 0,
        stdout: str | None = None,
        stderr: str = "",
    ) -> None:
        self.returncode = returncode
        self.stdout = (
            stdout
            if stdout is not None
            else f"[GNUPG:] VALIDSIG {SIGNER} 2026-07-10 0 4 0 1 10 00 {SIGNER}\n"
        )
        self.stderr = stderr
        self.calls: list[tuple[Path, Path]] = []

    def run(self, mpp_path: Path, signature_path: Path) -> GpgCommandResult:
        self.calls.append((mpp_path, signature_path))
        return GpgCommandResult(
            args=("fake-gpg", "--verify", str(signature_path), str(mpp_path)),
            returncode=self.returncode,
            stdout=self.stdout,
            stderr=self.stderr,
        )


class LocalArtifactObserverTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tempdir = tempfile.TemporaryDirectory()
        self.repo = Path(self.tempdir.name)
        self.libs = self.repo / "patches" / "build" / "libs"
        self.libs.mkdir(parents=True)
        self.mpp = self.libs / "patches-1.4.67.mpp"
        self.sig = self.libs / "patches-1.4.67.mpp.asc"

    def tearDown(self) -> None:
        self.tempdir.cleanup()

    def write_mpp(
        self,
        *,
        entries: tuple[str, ...] = REQUIRED_MPP_ENTRIES,
        duplicate: str | None = None,
    ) -> str:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.mpp, "w", compression=zipfile.ZIP_DEFLATED) as archive:
                for index, name in enumerate(entries):
                    archive.writestr(name, f"payload-{index}".encode("utf-8"))
                if duplicate is not None:
                    archive.writestr(duplicate, b"duplicate")
        return hashlib.sha256(self.mpp.read_bytes()).hexdigest()

    def write_signature(self) -> None:
        self.sig.write_text("detached signature fixture\n", encoding="utf-8")

    def test_a01_absent_artifacts_are_complete_absence(self) -> None:
        result = observe_local_artifacts(self.repo, identity())
        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.mpp_present)
        self.assertFalse(result.local.signature_present)
        self.assertIsNone(result.local.mpp_sha256)
        self.assertEqual(result.errors, ())

    def test_a02_valid_artifacts_and_matching_signature(self) -> None:
        actual_sha = self.write_mpp()
        self.write_signature()
        runner = FakeGpgRunner()

        result = observe_local_artifacts(
            self.repo,
            identity(sha256=actual_sha),
            gpg_runner=runner,
        )

        self.assertTrue(result.observations_complete)
        self.assertTrue(result.local.mpp_present)
        self.assertTrue(result.local.signature_present)
        self.assertEqual(result.local.mpp_sha256, actual_sha)
        self.assertTrue(result.local.mpp_structure_valid)
        self.assertTrue(result.local.signature_valid)
        self.assertEqual(result.missing_entries, ())
        self.assertEqual(result.duplicate_entries, ())
        self.assertEqual(runner.calls, [(self.mpp, self.sig)])

    def test_a03_sha_mismatch_is_observed_without_hiding_actual_digest(self) -> None:
        actual_sha = self.write_mpp()
        self.write_signature()

        result = observe_local_artifacts(
            self.repo,
            identity(sha256="f" * 64),
            gpg_runner=FakeGpgRunner(),
        )

        self.assertEqual(result.local.mpp_sha256, actual_sha)
        self.assertNotEqual(result.local.mpp_sha256, "f" * 64)
        self.assertTrue(result.observations_complete)

    def test_a04_missing_signature_is_known_absence(self) -> None:
        self.write_mpp()
        runner = FakeGpgRunner()

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=runner,
        )

        self.assertTrue(result.observations_complete)
        self.assertTrue(result.local.mpp_present)
        self.assertFalse(result.local.signature_present)
        self.assertIsNone(result.local.signature_valid)
        self.assertEqual(runner.calls, [])

    def test_a05_orphan_signature_is_reported(self) -> None:
        self.write_signature()

        result = observe_local_artifacts(self.repo, identity())

        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.mpp_present)
        self.assertTrue(result.local.signature_present)
        self.assertIn(
            "detached signature exists without the canonical MPP",
            result.warnings,
        )

    def test_a06_bad_zip_is_concrete_invalid_structure(self) -> None:
        self.mpp.write_bytes(b"not a ZIP file")
        self.write_signature()

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=FakeGpgRunner(),
        )

        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.mpp_structure_valid)
        self.assertEqual(result.missing_entries, REQUIRED_MPP_ENTRIES)

    def test_a07_missing_required_entry_is_invalid(self) -> None:
        self.write_mpp(entries=("classes.dex",))
        self.write_signature()

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=FakeGpgRunner(),
        )

        self.assertFalse(result.local.mpp_structure_valid)
        self.assertEqual(result.missing_entries, ("extensions/boostforreddit.mpe",))

    def test_a08_duplicate_required_entry_is_invalid(self) -> None:
        self.write_mpp(duplicate="classes.dex")
        self.write_signature()

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=FakeGpgRunner(),
        )

        self.assertFalse(result.local.mpp_structure_valid)
        self.assertEqual(result.duplicate_entries, ("classes.dex",))

    def test_a09_symlinked_mpp_is_rejected(self) -> None:
        outside = self.repo / "outside.mpp"
        outside.write_bytes(b"payload")
        self.mpp.symlink_to(outside)

        result = observe_local_artifacts(self.repo, identity())

        self.assertFalse(result.observations_complete)
        self.assertFalse(result.local.mpp_present)
        self.assertTrue(any("symbolic link" in error for error in result.errors))

    def test_a10_signature_directory_is_rejected(self) -> None:
        self.write_mpp()
        self.sig.mkdir()

        result = observe_local_artifacts(self.repo, identity())

        self.assertFalse(result.observations_complete)
        self.assertFalse(result.local.signature_present)
        self.assertTrue(any("not a regular file" in error for error in result.errors))

    def test_a11_bad_signature_is_concrete_invalid(self) -> None:
        self.write_mpp()
        self.write_signature()
        runner = FakeGpgRunner(
            returncode=1,
            stdout="[GNUPG:] BADSIG 01234567 Test User\n",
            stderr="BAD signature",
        )

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=runner,
        )

        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.signature_valid)

    def test_a12_unexpected_signing_identity_is_invalid(self) -> None:
        self.write_mpp()
        self.write_signature()
        runner = FakeGpgRunner(
            stdout=f"[GNUPG:] VALIDSIG {OTHER_SIGNER} 2026-07-10 0 4 0 1 10 00 {OTHER_SIGNER}\n"
        )

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=runner,
        )

        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.signature_valid)
        self.assertTrue(any("unexpected signing identity" in item for item in result.warnings))

    def test_a13_missing_public_key_is_incomplete(self) -> None:
        self.write_mpp()
        self.write_signature()
        runner = FakeGpgRunner(
            returncode=2,
            stdout="[GNUPG:] NO_PUBKEY DEADBEEFDEADBEEF\n",
        )

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=runner,
        )

        self.assertFalse(result.observations_complete)
        self.assertIsNone(result.local.signature_valid)
        self.assertTrue(any("public key is unavailable" in item for item in result.errors))

    def test_a14_missing_gpg_executable_is_incomplete(self) -> None:
        self.write_mpp()
        self.write_signature()
        runner = FakeGpgRunner(returncode=127, stdout="", stderr="gpg not found")

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=runner,
        )

        self.assertFalse(result.observations_complete)
        self.assertIsNone(result.local.signature_valid)
        self.assertTrue(any("tool is unavailable" in item for item in result.errors))

    def test_a15_multiple_valid_signatures_are_invalid(self) -> None:
        self.write_mpp()
        self.write_signature()
        runner = FakeGpgRunner(
            stdout=(
                f"[GNUPG:] VALIDSIG {SIGNER} 2026-07-10 0 4 0 1 10 00 {SIGNER}\n"
                f"[GNUPG:] VALIDSIG {OTHER_SIGNER} 2026-07-10 0 4 0 1 10 00 {OTHER_SIGNER}\n"
            )
        )

        result = observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=runner,
        )

        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.signature_valid)

    def test_a16_result_serialization_uses_stable_values(self) -> None:
        result = observe_local_artifacts(self.repo, identity())
        payload = result.as_dict()

        self.assertEqual(payload["repo_root"], str(self.repo.resolve()))
        self.assertEqual(payload["required_entries"], list(REQUIRED_MPP_ENTRIES))
        self.assertFalse(payload["local"]["mpp_present"])

    def test_a17_only_canonical_artifact_path_is_used(self) -> None:
        alternate = self.repo / "candidate" / self.mpp.name
        alternate.parent.mkdir()
        alternate.write_bytes(b"candidate data")

        result = observe_local_artifacts(self.repo, identity())

        self.assertFalse(result.local.mpp_present)
        self.assertEqual(result.mpp_path, str(self.mpp))

    def test_a18_observer_does_not_mutate_artifacts(self) -> None:
        self.write_mpp()
        self.write_signature()
        before = {
            path: (path.stat().st_mtime_ns, hashlib.sha256(path.read_bytes()).hexdigest())
            for path in (self.mpp, self.sig)
        }

        observe_local_artifacts(
            self.repo,
            identity(),
            gpg_runner=FakeGpgRunner(),
        )

        after = {
            path: (path.stat().st_mtime_ns, hashlib.sha256(path.read_bytes()).hexdigest())
            for path in (self.mpp, self.sig)
        }
        self.assertEqual(after, before)

    def test_a19_path_traversal_asset_name_is_rejected(self) -> None:
        unsafe = identity(mpp_name="../patches-1.4.67.mpp")
        with self.assertRaises(ValueError):
            observe_local_artifacts(self.repo, unsafe)

    def test_a20_duplicate_required_entries_contract_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            observe_local_artifacts(
                self.repo,
                identity(),
                required_entries=("classes.dex", "classes.dex"),
            )

    def test_result_model_is_immutable(self) -> None:
        result = observe_local_artifacts(self.repo, identity())
        with self.assertRaises(FrozenInstanceError):
            result.repo_root = "other"  # type: ignore[misc]


if __name__ == "__main__":
    unittest.main()
