#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import FrozenInstanceError, replace
import importlib.util
from pathlib import Path
import sys
import unittest


SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "releasectl.py"
SPEC = importlib.util.spec_from_file_location("morphe_releasectl_plan", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


GitHubReleaseObservations = MODULE.GitHubReleaseObservations
LocalObservations = MODULE.LocalObservations
PlanDisposition = MODULE.PlanDisposition
PlanOperationType = MODULE.PlanOperationType
RefRelation = MODULE.RefRelation
ReleaseIdentity = MODULE.ReleaseIdentity
ReleaseObservations = MODULE.ReleaseObservations
ReleaseState = MODULE.ReleaseState
RemoteGitObservations = MODULE.RemoteGitObservations
SafetyObservations = MODULE.SafetyObservations
VerificationObservations = MODULE.VerificationObservations
VerifierStatus = MODULE.VerifierStatus
generate_release_plan = MODULE.generate_release_plan


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


def valid_local(*, dev_equal: bool = True) -> LocalObservations:
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


def github_release(
    *,
    draft: bool,
    mpp_count: int = 1,
    signature_count: int = 1,
    verified_assets: bool = True,
) -> GitHubReleaseObservations:
    return GitHubReleaseObservations(
        present=True,
        tag="morphe-patches-67",
        is_draft=draft,
        is_prerelease=False,
        mpp_asset_count=mpp_count,
        signature_asset_count=signature_count,
        mpp_asset_digest=SHA if mpp_count else None,
        remote_mpp_sha256=SHA if verified_assets and mpp_count else None,
        remote_signature_valid=(
            True if verified_assets and signature_count else None
        ),
        remote_mpp_structure_valid=(
            True if verified_assets and mpp_count else None
        ),
    )


def operation_types(plan) -> list[PlanOperationType]:
    return [operation.operation_type for operation in plan.operations]


class ReleasePlanTests(unittest.TestCase):
    def plan(self, observations: ReleaseObservations):
        return generate_release_plan(identity(), observations)

    def test_p01_not_finalized_plans_full_sequence(self) -> None:
        plan = self.plan(ReleaseObservations())
        self.assertEqual(plan.disposition, PlanDisposition.APPLY_REQUIRED)
        self.assertEqual(plan.observed_state, ReleaseState.NOT_FINALIZED)
        self.assertEqual(
            operation_types(plan),
            [
                PlanOperationType.FINALIZE_LOCAL,
                PlanOperationType.ALIGN_LOCAL_DEV,
                PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
                PlanOperationType.CREATE_DRAFT_RELEASE,
                PlanOperationType.UPLOAD_MPP_ASSET,
                PlanOperationType.UPLOAD_SIGNATURE_ASSET,
                PlanOperationType.VERIFY_DRAFT_ASSETS,
                PlanOperationType.PUBLISH_DRAFT_RELEASE,
                PlanOperationType.VERIFY_PUBLISHED_RELEASE,
            ],
        )
        self.assertTrue(plan.executable)

    def test_p02_local_finalized_skips_finalize(self) -> None:
        plan = self.plan(
            ReleaseObservations(local=valid_local(dev_equal=False))
        )
        self.assertEqual(
            operation_types(plan)[:2],
            [
                PlanOperationType.ALIGN_LOCAL_DEV,
                PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
            ],
        )
        self.assertNotIn(
            PlanOperationType.FINALIZE_LOCAL,
            operation_types(plan),
        )

    def test_p03_ready_to_publish_starts_with_atomic_push(self) -> None:
        plan = self.plan(
            ReleaseObservations(local=valid_local(dev_equal=True))
        )
        self.assertEqual(
            operation_types(plan)[0],
            PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
        )

    def test_p04_complete_remote_refs_skip_push(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                local=valid_local(),
                remote=complete_remote(),
            )
        )
        self.assertNotIn(
            PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
            operation_types(plan),
        )
        self.assertEqual(
            operation_types(plan)[0],
            PlanOperationType.CREATE_DRAFT_RELEASE,
        )

    def test_p05_partial_remote_refs_include_atomic_push(self) -> None:
        remote = replace(
            complete_remote(),
            dev_commit=OLD,
            dev_relation_to_target=RefRelation.ANCESTOR,
            tag_commit=None,
        )
        plan = self.plan(
            ReleaseObservations(
                local=valid_local(),
                remote=remote,
            )
        )
        self.assertIn(
            PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
            operation_types(plan),
        )

    def test_p06_draft_missing_signature_uploads_only_signature(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                local=valid_local(),
                remote=complete_remote(),
                github=github_release(
                    draft=True,
                    signature_count=0,
                    verified_assets=False,
                ),
            )
        )
        self.assertEqual(
            operation_types(plan),
            [
                PlanOperationType.UPLOAD_SIGNATURE_ASSET,
                PlanOperationType.VERIFY_DRAFT_ASSETS,
                PlanOperationType.PUBLISH_DRAFT_RELEASE,
                PlanOperationType.VERIFY_PUBLISHED_RELEASE,
            ],
        )

    def test_p07_verified_draft_only_publishes_and_verifies(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                local=valid_local(),
                remote=complete_remote(),
                github=github_release(draft=True),
            )
        )
        self.assertEqual(
            operation_types(plan),
            [
                PlanOperationType.PUBLISH_DRAFT_RELEASE,
                PlanOperationType.VERIFY_PUBLISHED_RELEASE,
            ],
        )

    def test_p08_published_not_verified_plans_read_only_verify(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                remote=complete_remote(),
                github=github_release(draft=False),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.UNAVAILABLE
                ),
            )
        )
        self.assertEqual(
            operation_types(plan),
            [PlanOperationType.VERIFY_PUBLISHED_RELEASE],
        )
        self.assertTrue(plan.executable)
        self.assertFalse(plan.operations[0].mutates_state)

    def test_p09_verified_release_is_noop(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                remote=complete_remote(),
                github=github_release(draft=False),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.PASS
                ),
            )
        )
        self.assertEqual(
            plan.disposition,
            PlanDisposition.NOOP_ALREADY_SATISFIED,
        )
        self.assertEqual(plan.operations, ())
        self.assertFalse(plan.executable)

    def test_p10_conflict_produces_no_operations(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                remote=replace(complete_remote(), tag_commit=WRONG)
            )
        )
        self.assertEqual(plan.disposition, PlanDisposition.CONFLICT_ABORT)
        self.assertEqual(plan.operations, ())
        self.assertTrue(plan.conflicts)

    def test_p11_incomplete_observation_produces_no_operations(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                safety=SafetyObservations(observations_complete=False),
                local=valid_local(),
            )
        )
        self.assertEqual(
            plan.disposition,
            PlanDisposition.OBSERVATION_FAILED,
        )
        self.assertEqual(plan.operations, ())
        self.assertFalse(plan.executable)

    def test_p12_dirty_worktree_blocks_mutating_plan(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                safety=SafetyObservations(worktree_clean=False),
                local=valid_local(),
            )
        )
        self.assertEqual(plan.disposition, PlanDisposition.APPLY_REQUIRED)
        self.assertFalse(plan.executable)
        self.assertIn("worktree is not clean", plan.blocking_reasons)

    def test_p13_dirty_worktree_does_not_block_verify_only_plan(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                safety=SafetyObservations(worktree_clean=False),
                remote=complete_remote(),
                github=github_release(draft=False),
                verification=VerificationObservations(
                    full_verifier_status=VerifierStatus.UNAVAILABLE
                ),
            )
        )
        self.assertTrue(plan.executable)
        self.assertEqual(plan.blocking_reasons, ())

    def test_p14_missing_local_asset_blocks_partial_resume(self) -> None:
        plan = self.plan(
            ReleaseObservations(
                remote=complete_remote(),
                github=github_release(
                    draft=True,
                    mpp_count=0,
                    signature_count=1,
                    verified_assets=False,
                ),
            )
        )
        self.assertIn(
            PlanOperationType.UPLOAD_MPP_ASSET,
            operation_types(plan),
        )
        self.assertFalse(plan.executable)
        self.assertIn(
            "canonical local MPP is unavailable for upload",
            plan.blocking_reasons,
        )

    def test_p15_atomic_push_captures_expected_old_refs(self) -> None:
        remote = RemoteGitObservations(
            main_commit=OLD,
            main_relation_to_target=RefRelation.ANCESTOR,
            dev_commit=None,
            dev_relation_to_target=RefRelation.ABSENT,
            tag_commit=None,
        )
        plan = self.plan(
            ReleaseObservations(
                local=valid_local(),
                remote=remote,
            )
        )
        push = next(
            operation
            for operation in plan.operations
            if operation.operation_type
            is PlanOperationType.PUSH_RELEASE_REFS_ATOMIC
        )
        observed = {
            fact.path: fact.expected
            for fact in push.preconditions[:3]
        }
        self.assertEqual(
            observed,
            {
                "remote.main_commit": OLD,
                "remote.dev_commit": None,
                "remote.tag_commit": None,
            },
        )

    def test_p16_sequences_are_contiguous(self) -> None:
        plan = self.plan(ReleaseObservations())
        self.assertEqual(
            [operation.sequence for operation in plan.operations],
            list(range(1, len(plan.operations) + 1)),
        )

    def test_p17_plan_is_deterministic(self) -> None:
        observations = ReleaseObservations(local=valid_local())
        first = self.plan(observations)
        second = self.plan(observations)
        self.assertEqual(
            first.observation_fingerprint,
            second.observation_fingerprint,
        )
        self.assertEqual(first.plan_hash, second.plan_hash)
        self.assertEqual(first.to_json(), second.to_json())

    def test_p18_changed_observation_changes_hashes(self) -> None:
        first = self.plan(ReleaseObservations(local=valid_local()))
        second = self.plan(
            ReleaseObservations(
                local=valid_local(),
                safety=SafetyObservations(worktree_clean=False),
            )
        )
        self.assertNotEqual(
            first.observation_fingerprint,
            second.observation_fingerprint,
        )
        self.assertNotEqual(first.plan_hash, second.plan_hash)

    def test_p19_json_contains_stable_schema_and_operation_ids(self) -> None:
        payload = self.plan(ReleaseObservations()).as_dict()
        self.assertEqual(payload["schema_version"], 1)
        self.assertEqual(payload["disposition"], "APPLY_REQUIRED")
        self.assertEqual(
            payload["operations"][0]["operation_type"],
            "FINALIZE_LOCAL",
        )
        self.assertEqual(
            payload["operations"][0]["operation_id"],
            "finalize_local",
        )

    def test_p20_plan_models_are_immutable(self) -> None:
        plan = self.plan(ReleaseObservations())
        with self.assertRaises(FrozenInstanceError):
            plan.executable = False  # type: ignore[misc]


if __name__ == "__main__":
    unittest.main()
