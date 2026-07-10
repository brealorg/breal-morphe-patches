#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import replace
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
import sys
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


LocalObservations = MODULE.LocalObservations
ReleaseIdentity = MODULE.ReleaseIdentity
ReleaseObservations = MODULE.ReleaseObservations
ReleaseState = MODULE.ReleaseState
classify_release_state = MODULE.classify_release_state
observe_local_metadata = MODULE.observe_local_metadata


TARGET = "a" * 40
SHA = "d" * 64
REPOSITORY = "brealorg/breal-morphe-patches"


def identity() -> ReleaseIdentity:
    return ReleaseIdentity(
        version="1.4.67",
        tag="morphe-patches-67",
        release_commit=TARGET,
        mpp_asset_name="patches-1.4.67.mpp",
        signature_asset_name="patches-1.4.67.mpp.asc",
        mpp_sha256=SHA,
        signing_identity="0123456789ABCDEF",
    )


def expected_download_url() -> str:
    return (
        "https://github.com/brealorg/breal-morphe-patches/releases/"
        "download/morphe-patches-67/patches-1.4.67.mpp"
    )


def expected_signature_url() -> str:
    return expected_download_url() + ".asc"


def expected_manager_url() -> str:
    return (
        "https://raw.githubusercontent.com/brealorg/"
        "breal-morphe-patches/main/patches-bundle.json"
    )


def valid_bundle(*, version: str = "1.4.67") -> dict[str, object]:
    return {
        "created_at": "2026-07-10T20:00:00",
        "description": "A precise Manager-facing release description.\n",
        "download_url": expected_download_url(),
        "signature_download_url": expected_signature_url(),
        "version": version,
    }


def valid_readme() -> str:
    return f"""# Morphe patches

## Current release

| Field | Value |
|---|---|
| Version | `1.4.67` |
| Release tag | `morphe-patches-67` |
| Asset | `patches-1.4.67.mpp` |
| SHA256 | `{SHA}` |

SHA256: `{SHA}`
| Manager JSON | `{expected_manager_url()}` |
| Download URL | `{expected_download_url()}` |

## Development notes

Unrelated content.
"""


class LocalMetadataObserverTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.write_valid()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_valid(self) -> None:
        (self.root / "README.md").write_text(valid_readme(), encoding="utf-8")
        (self.root / "patches-bundle.json").write_text(
            json.dumps(valid_bundle(), indent=2) + "\n",
            encoding="utf-8",
        )

    def observe(self):
        return observe_local_metadata(self.root, REPOSITORY, identity())

    def test_m01_exact_metadata_matches(self) -> None:
        result = self.observe()
        self.assertTrue(result.observations_complete)
        self.assertTrue(result.local.metadata_matches_identity)
        self.assertEqual(result.errors, ())
        self.assertEqual(result.mismatches, ())
        self.assertEqual(result.bundle_version, "1.4.67")
        self.assertEqual(result.readme_sha256, SHA)

    def test_m02_v_prefixed_bundle_version_is_accepted(self) -> None:
        payload = valid_bundle(version="v1.4.67")
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        result = self.observe()
        self.assertTrue(result.observations_complete)
        self.assertTrue(result.local.metadata_matches_identity)

    def test_m03_stale_metadata_is_complete_not_finalized_state(self) -> None:
        payload = valid_bundle(version="1.4.66")
        payload["download_url"] = expected_download_url().replace("67", "66")
        payload["signature_download_url"] = expected_signature_url().replace("67", "66")
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        readme = valid_readme().replace("1.4.67", "1.4.66").replace(
            "morphe-patches-67", "morphe-patches-66"
        )
        (self.root / "README.md").write_text(readme, encoding="utf-8")

        result = self.observe()
        state = classify_release_state(
            identity(),
            ReleaseObservations(local=result.local),
        )
        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.metadata_matches_identity)
        self.assertEqual(state.state, ReleaseState.NOT_FINALIZED)
        self.assertIn(
            "local release metadata does not yet match the requested identity",
            state.warnings,
        )

    def test_m04_stale_metadata_conflicts_after_target_commit_exists(self) -> None:
        payload = valid_bundle(version="1.4.66")
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        result = self.observe()
        local = replace(result.local, main_commit=TARGET)
        state = classify_release_state(
            identity(),
            ReleaseObservations(local=local),
        )
        self.assertEqual(state.state, ReleaseState.INCONSISTENT_ABORT)
        self.assertIn(
            "local release metadata conflicts with the requested identity",
            state.reasons,
        )

    def test_m05_bundle_download_url_mismatch(self) -> None:
        payload = valid_bundle()
        payload["download_url"] = "https://example.invalid/wrong.mpp"
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        result = self.observe()
        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.metadata_matches_identity)
        self.assertIn("download_url", " ".join(result.mismatches))

    def test_m06_bundle_signature_url_mismatch(self) -> None:
        payload = valid_bundle()
        payload["signature_download_url"] = "https://example.invalid/wrong.asc"
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        result = self.observe()
        self.assertFalse(result.local.metadata_matches_identity)
        self.assertIn("signature_download_url", " ".join(result.mismatches))

    def test_m07_readme_version_mismatch(self) -> None:
        text = valid_readme().replace("| Version | `1.4.67` |", "| Version | `1.4.66` |")
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertFalse(result.local.metadata_matches_identity)
        self.assertIn("'Version'", " ".join(result.mismatches))

    def test_m08_readme_tag_mismatch(self) -> None:
        text = valid_readme().replace("morphe-patches-67", "morphe-patches-66", 1)
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("'Release tag'", " ".join(result.mismatches))

    def test_m09_readme_asset_mismatch(self) -> None:
        text = valid_readme().replace(
            "| Asset | `patches-1.4.67.mpp` |",
            "| Asset | `patches-1.4.66.mpp` |",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("'Asset'", " ".join(result.mismatches))

    def test_m10_readme_table_sha_mismatch(self) -> None:
        wrong = "e" * 64
        text = valid_readme().replace(
            f"| SHA256 | `{SHA}` |",
            f"| SHA256 | `{wrong}` |",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("'SHA256'", " ".join(result.mismatches))
        self.assertIn("disagree", " ".join(result.mismatches))

    def test_m11_readme_download_url_mismatch(self) -> None:
        text = valid_readme().replace(
            f"| Download URL | `{expected_download_url()}` |",
            "| Download URL | `https://example.invalid/wrong.mpp` |",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("'Download URL'", " ".join(result.mismatches))

    def test_m12_readme_manager_url_mismatch(self) -> None:
        text = valid_readme().replace(
            expected_manager_url(),
            "https://example.invalid/patches-bundle.json",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("'Manager JSON'", " ".join(result.mismatches))

    def test_m13_readme_standalone_sha_mismatch(self) -> None:
        wrong = "e" * 64
        text = valid_readme().replace(f"SHA256: `{SHA}`", f"SHA256: `{wrong}`")
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("standalone SHA256", " ".join(result.mismatches))
        self.assertIn("disagree", " ".join(result.mismatches))

    def test_m14_missing_bundle_is_incomplete(self) -> None:
        (self.root / "patches-bundle.json").unlink()
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIsNone(result.local.metadata_matches_identity)
        self.assertIn("is missing", " ".join(result.errors))

    def test_m15_missing_readme_is_incomplete(self) -> None:
        (self.root / "README.md").unlink()
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIsNone(result.local.metadata_matches_identity)

    def test_m16_invalid_bundle_json_is_incomplete(self) -> None:
        (self.root / "patches-bundle.json").write_text("{", encoding="utf-8")
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIn("invalid JSON", " ".join(result.errors))

    def test_m17_duplicate_bundle_key_is_rejected(self) -> None:
        text = (
            '{"version":"1.4.67","version":"1.4.66",'
            '"created_at":"x","description":"x",'
            f'"download_url":"{expected_download_url()}",'
            f'"signature_download_url":"{expected_signature_url()}"}}'
        )
        (self.root / "patches-bundle.json").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIn("duplicate JSON key", " ".join(result.errors))

    def test_m18_bundle_root_must_be_object(self) -> None:
        (self.root / "patches-bundle.json").write_text("[]", encoding="utf-8")
        result = self.observe()
        self.assertIn("root must be a JSON object", " ".join(result.errors))

    def test_m19_duplicate_current_release_section_is_rejected(self) -> None:
        text = valid_readme() + "\n" + valid_readme().split("# Morphe patches\n", 1)[1]
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIn("exactly one", " ".join(result.errors))

    def test_m20_missing_required_readme_row_is_rejected(self) -> None:
        text = valid_readme().replace(
            f"| Download URL | `{expected_download_url()}` |\n",
            "",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("missing non-empty field 'Download URL'", " ".join(result.errors))

    def test_m21_duplicate_readme_field_is_rejected(self) -> None:
        text = valid_readme().replace(
            "| Release tag | `morphe-patches-67` |",
            "| Release tag | `morphe-patches-67` |\n| Release tag | `morphe-patches-67` |",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertIn("duplicate field 'Release tag'", " ".join(result.errors))

    def test_m22_symlink_bundle_is_rejected(self) -> None:
        target = self.root / "bundle-target.json"
        target.write_text(json.dumps(valid_bundle()), encoding="utf-8")
        (self.root / "patches-bundle.json").unlink()
        (self.root / "patches-bundle.json").symlink_to(target)
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIn("symbolic link", " ".join(result.errors))

    def test_m23_observer_does_not_mutate_files(self) -> None:
        before = {
            path.name: (path.read_bytes(), path.stat().st_mtime_ns)
            for path in (self.root / "README.md", self.root / "patches-bundle.json")
        }
        self.observe()
        after = {
            path.name: (path.read_bytes(), path.stat().st_mtime_ns)
            for path in (self.root / "README.md", self.root / "patches-bundle.json")
        }
        self.assertEqual(before, after)

    def test_m24_result_serializes_stably(self) -> None:
        payload = self.observe().as_dict()
        self.assertEqual(payload["repository"], REPOSITORY)
        self.assertTrue(payload["local"]["metadata_matches_identity"])
        self.assertEqual(payload["mismatches"], [])

    def test_m25_invalid_repository_slug_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            observe_local_metadata(self.root, "owner/../repo", identity())

    def test_m26_non_string_description_is_incomplete(self) -> None:
        payload = valid_bundle()
        payload["description"] = ["not", "a", "string"]
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        result = self.observe()
        self.assertFalse(result.observations_complete)
        self.assertIn("'description'", " ".join(result.errors))

    def test_m27_overlong_description_is_metadata_mismatch(self) -> None:
        payload = valid_bundle()
        payload["description"] = "x" * 701
        (self.root / "patches-bundle.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )
        result = self.observe()
        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.metadata_matches_identity)
        self.assertIn("length limit", " ".join(result.mismatches))

    def test_m28_malformed_readme_sha_is_metadata_mismatch(self) -> None:
        text = valid_readme().replace(
            f"| SHA256 | `{SHA}` |",
            "| SHA256 | `not-a-sha` |",
        )
        (self.root / "README.md").write_text(text, encoding="utf-8")
        result = self.observe()
        self.assertTrue(result.observations_complete)
        self.assertFalse(result.local.metadata_matches_identity)
        self.assertIn("malformed", " ".join(result.mismatches))


if __name__ == "__main__":
    unittest.main()
