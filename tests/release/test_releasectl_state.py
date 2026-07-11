#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import FrozenInstanceError, replace
import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
import sys
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GitHubReleaseObservations = MODULE.GitHubReleaseObservations
LocalObservations = MODULE.LocalObservations
NextAction = MODULE.NextAction
RefRelation = MODULE.RefRelation
ReleaseIdentity = MODULE.ReleaseIdentity
ReleaseObservations = MODULE.ReleaseObservations
ReleaseState = MODULE.ReleaseState
RemoteGitObservations = MODULE.RemoteGitObservations
SafetyObservations = MODULE.SafetyObservations
VerificationObservations = MODULE.VerificationObservations
VerifierStatus = MODULE.VerifierStatus
classify_release_state = MODULE.classify_release_state


TARGET = "a" * 40
OLD = "b" * 40
WRONG = "c" * 40
SHA = "d" * 64
WRONG_SHA = "e" * 64


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


def valid_local(*, dev_equal: bool) -> LocalObservations:
    return LocalObservations(
        main_commit=TARGET,
        dev_commit=TARGET if dev_equal else OLD,
        dev_relation_to_target=(
            RefRelation.EQUAL if dev_equal else RefRelation.ANCESTOR
        ),
        tag_commit=TARGET,
        tag_is_annotated=True,
        metadata_matches_identity=True,
        mpp_present=True,
        signature_present=True,
        mpp_sha256=SHA,
        signature_valid=True,
        mpp_structure_valid=True,
    )


def complete_remote() -> RemoteGitObservations:
    return RemoteGitObservations(
        main_commit=TARGET,
        main_relation_to_target=RefRelation.EQUAL,
        dev_commit=TARGET,
        dev_relation_to_target=RefRelation.EQUAL,
        tag_commit=TARGET,
    )


def complete_release(
    *,
    draft: bool = False,
    prerelease: bool = False,
    mpp_count: int = 1,
    sig_count: int = 1,
    digest: str | None = SHA,
    remote_sha: str | None = SHA,
    signature_valid: bool | None = True,
    structure_valid: bool | None = True,
) -> GitHubReleaseObservations:
    return GitHubReleaseObservations(
        present=True,
        tag="morphe-patches-67",
        is_draft=draft,
        is_prerelease=prerelease,
        mpp_asset_count=mpp_count,
        signature_asset_count=sig_count,
        mpp_asset_digest=digest,
        remote_mpp_sha256=remote_sha,
        remote_signature_valid=signature_valid,
        remote_mpp_structure_valid=structure_valid,
    )


class ReleaseStateClassifierTests(unittest.TestCase):
    def classify(self, observations: ReleaseObservations):
        return classify_release_state(identity(), observations)

    def test_s01_not_finalized(self) -> None:
        result = self.classify(ReleaseObservations())
        self.assertEqual(result.state, ReleaseState.NOT_FINALIZED)
        self.assertEqual(result.next_action, NextAction.FINALIZE_LOCAL)
        self.assertTrue(result.safe_to_mutate)

    def test_s02_local_finalized(self) -> None:
        result = self.classify(
            ReleaseObservations(local=valid_local(dev_equal=False))
        )
        self.assertEqual(result.state, ReleaseState.LOCAL_FINALIZED)
        self.assertEqual(result.next_action, NextAction.ALIGN_LOCAL_REFS)

    def test_s03_ready_to_publish(self) -> None:
        result = self.classify(
            ReleaseObservations(local=valid_local(dev_equal=True))
        )
        self.assertEqual(result.state, ReleaseState.READY_TO_PUBLISH)
        self.assertEqual(result.next_action, NextAction.PUBLISH)

    def test_s04_remote_main_only_is_partial(self) -> None:
        result = self.classify(
            ReleaseObservations(
                local=valid_local(dev_equal=True),
                remote=RemoteGitObservations(
                    main_commit=TARGET,
                    main_relation_to_target=RefRelation.EQUAL,
                    dev_commit=OLD,
                    dev_relation_to_target=RefRelation.ANCESTOR,
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PARTIALLY_PUBLISHED)
        self.assertEqual(result.next_action, NextAction.RESUME)

    def test_s05_remote_branches_without_tag_are_partial(self) -> None:
        remote = replace(complete_remote(), tag_commit=None)
        result = self.classify(
            ReleaseObservations(
                local=valid_local(dev_equal=True),
                remote=remote,
            )
        )
        self.assertEqual(result.state, ReleaseState.PARTIALLY_PUBLISHED)

    def test_s06_remote_tag_without_release_is_partial(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=RemoteGitObservations(tag_commit=TARGET)
            )
        )
        self.assertEqual(result.state, ReleaseState.PARTIALLY_PUBLISHED)

    def test_s07_draft_missing_asset_is_partial(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(
                    draft=True,
                    mpp_count=1,
                    sig_count=0,
                    remote_sha=None,
                    signature_valid=None,
                    structure_valid=None,
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PARTIALLY_PUBLISHED)

    def test_s08_published_release_missing_asset_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(mpp_count=0),
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)
        self.assertIn(
            "published GitHub release is missing the expected MPP asset",
            result.reasons,
        )

    def test_s09_remote_sha_mismatch_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(remote_sha=WRONG_SHA),
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)

    def test_s10_remote_tag_wrong_commit_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=replace(complete_remote(), tag_commit=WRONG)
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)

    def test_s11_complete_surface_without_verifier_is_not_verified(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.UNAVAILABLE
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PUBLISHED_NOT_VERIFIED)
        self.assertEqual(result.next_action, NextAction.VERIFY)
        self.assertFalse(result.safe_to_mutate)

    def test_s12_complete_release_is_verified(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.PASS
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PUBLISHED_AND_VERIFIED)
        self.assertEqual(result.next_action, NextAction.NONE)

    def test_s13_dirty_worktree_does_not_change_verified_state(self) -> None:
        result = self.classify(
            ReleaseObservations(
                safety=SafetyObservations(worktree_clean=False),
                remote=complete_remote(),
                github=complete_release(),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.PASS
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PUBLISHED_AND_VERIFIED)
        self.assertFalse(result.safe_to_mutate)

    def test_s14_verified_remote_does_not_require_local_artifact(self) -> None:
        result = self.classify(
            ReleaseObservations(
                local=LocalObservations(),
                remote=complete_remote(),
                github=complete_release(),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.PASS
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PUBLISHED_AND_VERIFIED)

    def test_s15_local_sha_mismatch_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                local=replace(
                    valid_local(dev_equal=True),
                    mpp_sha256=WRONG_SHA,
                ),
                remote=complete_remote(),
                github=complete_release(),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.PASS
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)

    def test_s16_local_dev_divergent_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                local=replace(
                    valid_local(dev_equal=False),
                    dev_relation_to_target=RefRelation.DIVERGENT,
                )
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)

    def test_s17_completed_release_allows_advanced_remote_branches(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=replace(
                    complete_remote(),
                    main_commit=WRONG,
                    main_relation_to_target=RefRelation.AHEAD,
                    dev_commit=WRONG,
                    dev_relation_to_target=RefRelation.AHEAD,
                ),
                github=complete_release(),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.PASS
                ),
            )
        )
        self.assertEqual(result.state, ReleaseState.PUBLISHED_AND_VERIFIED)
        self.assertEqual(result.next_action, NextAction.NONE)

    def test_s18_prerelease_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(prerelease=True),
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)

    def test_s19_duplicate_assets_are_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=complete_remote(),
                github=complete_release(mpp_count=2),
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)

    def test_s20_incomplete_observations_preserve_best_state(self) -> None:
        result = self.classify(
            ReleaseObservations(
                safety=SafetyObservations(observations_complete=False),
                local=valid_local(dev_equal=True),
            )
        )
        self.assertEqual(result.state, ReleaseState.READY_TO_PUBLISH)
        self.assertFalse(result.safe_to_mutate)
        self.assertIn("one or more observations are incomplete", result.warnings)


    def test_s21_remote_branch_divergence_is_conflict(self) -> None:
        result = self.classify(
            ReleaseObservations(
                remote=replace(
                    complete_remote(),
                    main_commit=WRONG,
                    main_relation_to_target=RefRelation.DIVERGENT,
                )
            )
        )
        self.assertEqual(result.state, ReleaseState.INCONSISTENT_ABORT)
        self.assertIn(
            "remote main is divergent from the release target",
            result.reasons,
        )

    def test_result_serialization_uses_stable_enum_values(self) -> None:
        result = self.classify(ReleaseObservations())
        payload = result.as_dict()
        self.assertEqual(payload["state"], "NOT_FINALIZED")
        self.assertEqual(payload["next_action"], "FINALIZE_LOCAL")

    def test_models_are_immutable(self) -> None:
        release = identity()
        with self.assertRaises(FrozenInstanceError):
            release.version = "9.9.9"  # type: ignore[misc]

    def test_invalid_identity_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            ReleaseIdentity(
                version="1.4.67",
                tag="morphe-patches-67",
                release_commit="short",
                mpp_asset_name="patches-1.4.67.mpp",
                signature_asset_name="patches-1.4.67.mpp.asc",
                mpp_sha256=SHA,
                signing_identity="key",
            )


if __name__ == "__main__":
    unittest.main()
