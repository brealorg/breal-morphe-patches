#!/usr/bin/env python3
"""Authoritative Morphe release inspection and transaction controller.

Issue #43 provides read-only observation, planning, and verification. Issue #44
adds explicit idempotent finalize, publish, and resume transactions. Every
release mutation is preceded by a fresh authoritative observation and recorded
in a machine-readable transaction log.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, fields, is_dataclass, replace
from enum import Enum
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile
from typing import Any, Protocol, Sequence


_SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
_SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


class ReleaseState(str, Enum):
    NOT_FINALIZED = "NOT_FINALIZED"
    LOCAL_FINALIZED = "LOCAL_FINALIZED"
    READY_TO_PUBLISH = "READY_TO_PUBLISH"
    PARTIALLY_PUBLISHED = "PARTIALLY_PUBLISHED"
    PUBLISHED_NOT_VERIFIED = "PUBLISHED_NOT_VERIFIED"
    PUBLISHED_AND_VERIFIED = "PUBLISHED_AND_VERIFIED"
    INCONSISTENT_ABORT = "INCONSISTENT_ABORT"


class NextAction(str, Enum):
    FINALIZE_LOCAL = "FINALIZE_LOCAL"
    ALIGN_LOCAL_REFS = "ALIGN_LOCAL_REFS"
    PUBLISH = "PUBLISH"
    RESUME = "RESUME"
    VERIFY = "VERIFY"
    NONE = "NONE"
    MANUAL_DIAGNOSIS = "MANUAL_DIAGNOSIS"


class RefRelation(str, Enum):
    """Relationship between an observed ref and the target release commit."""

    ABSENT = "ABSENT"
    EQUAL = "EQUAL"
    ANCESTOR = "ANCESTOR"
    AHEAD = "AHEAD"
    DIVERGENT = "DIVERGENT"
    UNKNOWN = "UNKNOWN"


class VerifierStatus(str, Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    UNAVAILABLE = "UNAVAILABLE"
    NOT_RUN = "NOT_RUN"


@dataclass(frozen=True, slots=True)
class ReleaseIdentity:
    version: str
    tag: str
    release_commit: str
    mpp_asset_name: str
    signature_asset_name: str
    mpp_sha256: str
    signing_identity: str

    def __post_init__(self) -> None:
        if not self.version.strip():
            raise ValueError("version must not be empty")
        if not self.tag.strip():
            raise ValueError("tag must not be empty")
        if not _SHA1_RE.fullmatch(self.release_commit):
            raise ValueError("release_commit must be a full 40-character lowercase SHA-1")
        if not _SHA256_RE.fullmatch(self.mpp_sha256):
            raise ValueError("mpp_sha256 must be a 64-character lowercase SHA-256")
        if not self.mpp_asset_name.endswith(".mpp"):
            raise ValueError("mpp_asset_name must end in .mpp")
        if self.signature_asset_name != f"{self.mpp_asset_name}.asc":
            raise ValueError("signature_asset_name must equal mpp_asset_name + '.asc'")
        if not self.signing_identity.strip():
            raise ValueError("signing_identity must not be empty")


@dataclass(frozen=True, slots=True)
class SafetyObservations:
    current_branch_is_main: bool = True
    worktree_clean: bool = True
    index_clean: bool = True
    required_tools_available: bool = True
    observations_complete: bool = True


@dataclass(frozen=True, slots=True)
class LocalObservations:
    main_commit: str | None = None
    dev_commit: str | None = None
    dev_relation_to_target: RefRelation = RefRelation.ABSENT
    tag_commit: str | None = None
    tag_is_annotated: bool | None = None
    metadata_matches_identity: bool | None = None
    mpp_present: bool = False
    signature_present: bool = False
    mpp_sha256: str | None = None
    signature_valid: bool | None = None
    mpp_structure_valid: bool | None = None


@dataclass(frozen=True, slots=True)
class RemoteGitObservations:
    main_commit: str | None = None
    main_relation_to_target: RefRelation = RefRelation.ABSENT
    dev_commit: str | None = None
    dev_relation_to_target: RefRelation = RefRelation.ABSENT
    tag_commit: str | None = None


@dataclass(frozen=True, slots=True)
class GitHubReleaseObservations:
    present: bool = False
    tag: str | None = None
    is_draft: bool | None = None
    is_prerelease: bool | None = None
    mpp_asset_count: int = 0
    signature_asset_count: int = 0
    mpp_asset_digest: str | None = None
    remote_mpp_sha256: str | None = None
    remote_signature_valid: bool | None = None
    remote_mpp_structure_valid: bool | None = None


@dataclass(frozen=True, slots=True)
class VerificationObservations:
    full_verifier_status: VerifierStatus = VerifierStatus.NOT_RUN


@dataclass(frozen=True, slots=True)
class ReleaseObservations:
    safety: SafetyObservations = SafetyObservations()
    local: LocalObservations = LocalObservations()
    remote: RemoteGitObservations = RemoteGitObservations()
    github: GitHubReleaseObservations = GitHubReleaseObservations()
    verification: VerificationObservations = VerificationObservations()


@dataclass(frozen=True, slots=True)
class ReleaseStateResult:
    state: ReleaseState
    next_action: NextAction
    safe_to_mutate: bool
    observations_complete: bool
    reasons: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["state"] = self.state.value
        data["next_action"] = self.next_action.value
        return data


def _commit_matches(commit: str | None, target: str) -> bool:
    return commit == target


def _relation_contains_target(
    commit: str | None,
    relation: RefRelation,
    target: str,
) -> bool:
    """Return whether the observed ref points at or contains the release target.

    A branch that has advanced after a release still contains the immutable
    release commit. The annotated release tag remains the exact identity
    anchor, so an AHEAD branch is valid historical-release evidence rather
    than a conflict.
    """

    return commit == target or relation in {
        RefRelation.EQUAL,
        RefRelation.AHEAD,
    }


def _local_release_valid(identity: ReleaseIdentity, local: LocalObservations) -> bool:
    return all(
        (
            local.main_commit == identity.release_commit,
            local.tag_commit == identity.release_commit,
            local.tag_is_annotated is True,
            local.metadata_matches_identity is True,
            local.mpp_present,
            local.signature_present,
            local.mpp_sha256 == identity.mpp_sha256,
            local.signature_valid is True,
            local.mpp_structure_valid is True,
        )
    )


def _remote_surface_complete(
    identity: ReleaseIdentity,
    remote: RemoteGitObservations,
    github: GitHubReleaseObservations,
) -> bool:
    return all(
        (
            _relation_contains_target(
                remote.main_commit,
                remote.main_relation_to_target,
                identity.release_commit,
            ),
            _relation_contains_target(
                remote.dev_commit,
                remote.dev_relation_to_target,
                identity.release_commit,
            ),
            remote.tag_commit == identity.release_commit,
            github.present,
            github.tag == identity.tag,
            github.is_draft is False,
            github.is_prerelease is False,
            github.mpp_asset_count == 1,
            github.signature_asset_count == 1,
        )
    )


def _remote_component_matches_target(
    identity: ReleaseIdentity,
    remote: RemoteGitObservations,
    github: GitHubReleaseObservations,
) -> bool:
    return any(
        (
            _relation_contains_target(
                remote.main_commit,
                remote.main_relation_to_target,
                identity.release_commit,
            ),
            _relation_contains_target(
                remote.dev_commit,
                remote.dev_relation_to_target,
                identity.release_commit,
            ),
            remote.tag_commit == identity.release_commit,
            github.present,
        )
    )


def _collect_conflicts(
    identity: ReleaseIdentity,
    observations: ReleaseObservations,
) -> list[str]:
    local = observations.local
    remote = observations.remote
    github = observations.github
    verification = observations.verification

    conflicts: list[str] = []

    if local.tag_commit is not None and local.tag_commit != identity.release_commit:
        conflicts.append("local tag points to a different commit")

    metadata_conflict_evidence = any(
        (
            local.main_commit == identity.release_commit,
            local.tag_commit == identity.release_commit,
            _remote_component_matches_target(identity, remote, github),
        )
    )
    if local.metadata_matches_identity is False and metadata_conflict_evidence:
        conflicts.append("local release metadata conflicts with the requested identity")

    if local.mpp_sha256 is not None and local.mpp_sha256 != identity.mpp_sha256:
        conflicts.append("local MPP SHA256 conflicts with the release identity")

    if local.signature_present and local.signature_valid is False:
        conflicts.append("local detached signature is invalid")

    if local.mpp_present and local.mpp_structure_valid is False:
        conflicts.append("local MPP structure is invalid")

    local_finalized_evidence = any(
        (
            local.main_commit == identity.release_commit,
            local.tag_commit == identity.release_commit,
            local.mpp_present,
        )
    )
    if (
        local_finalized_evidence
        and local.dev_relation_to_target is RefRelation.DIVERGENT
    ):
        conflicts.append("local dev is divergent from the release target")

    if remote.tag_commit is not None and remote.tag_commit != identity.release_commit:
        conflicts.append("remote tag points to a different commit")

    if remote.main_relation_to_target is RefRelation.DIVERGENT:
        conflicts.append("remote main is divergent from the release target")

    if remote.dev_relation_to_target is RefRelation.DIVERGENT:
        conflicts.append("remote dev is divergent from the release target")

    if github.present:
        if github.tag is not None and github.tag != identity.tag:
            conflicts.append("GitHub release is attached to a different tag")

        if github.is_prerelease is True:
            conflicts.append("GitHub release is unexpectedly marked as a prerelease")

        if github.mpp_asset_count > 1:
            conflicts.append("GitHub release has duplicate MPP assets")

        if github.signature_asset_count > 1:
            conflicts.append("GitHub release has duplicate signature assets")

        if github.is_draft is False:
            if github.mpp_asset_count != 1:
                conflicts.append("published GitHub release is missing the expected MPP asset")
            if github.signature_asset_count != 1:
                conflicts.append("published GitHub release is missing the expected signature asset")

        if (
            github.mpp_asset_digest is not None
            and github.mpp_asset_digest != identity.mpp_sha256
        ):
            conflicts.append("GitHub asset digest conflicts with the release identity")

        if (
            github.remote_mpp_sha256 is not None
            and github.remote_mpp_sha256 != identity.mpp_sha256
        ):
            conflicts.append("downloaded remote MPP SHA256 conflicts with the release identity")

        if github.remote_signature_valid is False:
            conflicts.append("downloaded remote detached signature is invalid")

        if github.remote_mpp_structure_valid is False:
            conflicts.append("downloaded remote MPP structure is invalid")

    if verification.full_verifier_status is VerifierStatus.FAIL:
        conflicts.append("full remote verifier reported a concrete failure")

    return conflicts


def _next_action_for_state(state: ReleaseState) -> NextAction:
    return {
        ReleaseState.NOT_FINALIZED: NextAction.FINALIZE_LOCAL,
        ReleaseState.LOCAL_FINALIZED: NextAction.ALIGN_LOCAL_REFS,
        ReleaseState.READY_TO_PUBLISH: NextAction.PUBLISH,
        ReleaseState.PARTIALLY_PUBLISHED: NextAction.RESUME,
        ReleaseState.PUBLISHED_NOT_VERIFIED: NextAction.VERIFY,
        ReleaseState.PUBLISHED_AND_VERIFIED: NextAction.NONE,
        ReleaseState.INCONSISTENT_ABORT: NextAction.MANUAL_DIAGNOSIS,
    }[state]


def _safe_to_mutate(
    state: ReleaseState,
    safety: SafetyObservations,
) -> bool:
    mutation_states = {
        ReleaseState.NOT_FINALIZED,
        ReleaseState.LOCAL_FINALIZED,
        ReleaseState.READY_TO_PUBLISH,
        ReleaseState.PARTIALLY_PUBLISHED,
    }
    return all(
        (
            state in mutation_states,
            safety.current_branch_is_main,
            safety.worktree_clean,
            safety.index_clean,
            safety.required_tools_available,
            safety.observations_complete,
        )
    )


def classify_release_state(
    identity: ReleaseIdentity,
    observations: ReleaseObservations,
) -> ReleaseStateResult:
    """Classify release state from immutable identity and normalized observations.

    This function is pure. It performs no I/O and mutates no state.
    """

    conflicts = _collect_conflicts(identity, observations)
    warnings: list[str] = []

    if not observations.safety.observations_complete:
        warnings.append("one or more observations are incomplete")

    if (
        observations.local.metadata_matches_identity is False
        and not any(
            (
                observations.local.main_commit == identity.release_commit,
                observations.local.tag_commit == identity.release_commit,
                _remote_component_matches_target(
                    identity,
                    observations.remote,
                    observations.github,
                ),
            )
        )
    ):
        warnings.append(
            "local release metadata does not yet match the requested identity"
        )

    if conflicts:
        state = ReleaseState.INCONSISTENT_ABORT
        return ReleaseStateResult(
            state=state,
            next_action=_next_action_for_state(state),
            safe_to_mutate=False,
            observations_complete=observations.safety.observations_complete,
            reasons=tuple(conflicts),
            warnings=tuple(warnings),
        )

    remote_surface_complete = _remote_surface_complete(
        identity,
        observations.remote,
        observations.github,
    )

    if remote_surface_complete:
        content_verified = all(
            (
                observations.github.remote_mpp_sha256 == identity.mpp_sha256,
                observations.github.remote_signature_valid is True,
                observations.github.remote_mpp_structure_valid is True,
                observations.verification.full_verifier_status is VerifierStatus.PASS,
            )
        )
        state = (
            ReleaseState.PUBLISHED_AND_VERIFIED
            if content_verified
            else ReleaseState.PUBLISHED_NOT_VERIFIED
        )
    elif _remote_component_matches_target(
        identity,
        observations.remote,
        observations.github,
    ):
        state = ReleaseState.PARTIALLY_PUBLISHED
    else:
        local_valid = _local_release_valid(identity, observations.local)

        if local_valid and (
            observations.local.dev_commit == identity.release_commit
            or observations.local.dev_relation_to_target is RefRelation.EQUAL
        ):
            state = ReleaseState.READY_TO_PUBLISH
        elif local_valid:
            state = ReleaseState.LOCAL_FINALIZED
        else:
            state = ReleaseState.NOT_FINALIZED

    return ReleaseStateResult(
        state=state,
        next_action=_next_action_for_state(state),
        safe_to_mutate=_safe_to_mutate(state, observations.safety),
        observations_complete=observations.safety.observations_complete,
        reasons=(),
        warnings=tuple(warnings),
    )


class PlanDisposition(str, Enum):
    APPLY_REQUIRED = "APPLY_REQUIRED"
    NOOP_ALREADY_SATISFIED = "NOOP_ALREADY_SATISFIED"
    CONFLICT_ABORT = "CONFLICT_ABORT"
    OBSERVATION_FAILED = "OBSERVATION_FAILED"


class PlanOperationType(str, Enum):
    FINALIZE_LOCAL = "FINALIZE_LOCAL"
    ALIGN_LOCAL_DEV = "ALIGN_LOCAL_DEV"
    PUSH_RELEASE_REFS_ATOMIC = "PUSH_RELEASE_REFS_ATOMIC"
    CREATE_DRAFT_RELEASE = "CREATE_DRAFT_RELEASE"
    UPLOAD_MPP_ASSET = "UPLOAD_MPP_ASSET"
    UPLOAD_SIGNATURE_ASSET = "UPLOAD_SIGNATURE_ASSET"
    VERIFY_DRAFT_ASSETS = "VERIFY_DRAFT_ASSETS"
    PUBLISH_DRAFT_RELEASE = "PUBLISH_DRAFT_RELEASE"
    VERIFY_PUBLISHED_RELEASE = "VERIFY_PUBLISHED_RELEASE"


@dataclass(frozen=True, slots=True)
class PlanFact:
    path: str
    expected: Any


@dataclass(frozen=True, slots=True)
class PlanOperation:
    sequence: int
    operation_id: str
    operation_type: PlanOperationType
    mutates_state: bool
    preconditions: tuple[PlanFact, ...]
    desired_result: tuple[PlanFact, ...]


@dataclass(frozen=True, slots=True)
class ReleasePlan:
    schema_version: int
    release_identity: ReleaseIdentity
    disposition: PlanDisposition
    observed_state: ReleaseState
    next_action: NextAction
    executable: bool
    observation_fingerprint: str
    plan_hash: str
    operations: tuple[PlanOperation, ...]
    conflicts: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()
    blocking_reasons: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)

    def to_json(self, *, indent: int | None = 2) -> str:
        return json.dumps(
            self.as_dict(),
            indent=indent,
            sort_keys=True,
            separators=(",", ":") if indent is None else None,
        )


def _jsonable(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if is_dataclass(value):
        return {
            field.name: _jsonable(getattr(value, field.name))
            for field in fields(value)
        }
    if isinstance(value, dict):
        return {
            str(key): _jsonable(item)
            for key, item in sorted(value.items(), key=lambda pair: str(pair[0]))
        }
    if isinstance(value, (tuple, list)):
        return [_jsonable(item) for item in value]
    return value


def _canonical_sha256(value: Any) -> str:
    encoded = json.dumps(
        _jsonable(value),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
    ).encode("utf-8")
    return f"sha256:{hashlib.sha256(encoded).hexdigest()}"


def _fact(path: str, expected: Any) -> PlanFact:
    return PlanFact(path=path, expected=expected)


def _add_operation(
    operations: list[PlanOperation],
    operation_type: PlanOperationType,
    *,
    mutates_state: bool,
    preconditions: tuple[PlanFact, ...],
    desired_result: tuple[PlanFact, ...],
) -> None:
    operations.append(
        PlanOperation(
            sequence=len(operations) + 1,
            operation_id=operation_type.value.lower(),
            operation_type=operation_type,
            mutates_state=mutates_state,
            preconditions=preconditions,
            desired_result=desired_result,
        )
    )


def _remote_refs_complete(
    identity: ReleaseIdentity,
    remote: RemoteGitObservations,
) -> bool:
    return all(
        (
            _relation_contains_target(
                remote.main_commit,
                remote.main_relation_to_target,
                identity.release_commit,
            ),
            _relation_contains_target(
                remote.dev_commit,
                remote.dev_relation_to_target,
                identity.release_commit,
            ),
            remote.tag_commit == identity.release_commit,
        )
    )


def _draft_assets_verified(
    identity: ReleaseIdentity,
    github: GitHubReleaseObservations,
) -> bool:
    return all(
        (
            github.mpp_asset_count == 1,
            github.signature_asset_count == 1,
            github.remote_mpp_sha256 == identity.mpp_sha256,
            github.remote_signature_valid is True,
            github.remote_mpp_structure_valid is True,
        )
    )


def _build_plan_operations(
    identity: ReleaseIdentity,
    observations: ReleaseObservations,
    state_result: ReleaseStateResult,
) -> tuple[PlanOperation, ...]:
    operations: list[PlanOperation] = []

    if state_result.state is ReleaseState.PUBLISHED_AND_VERIFIED:
        return ()

    if state_result.state is ReleaseState.PUBLISHED_NOT_VERIFIED:
        _add_operation(
            operations,
            PlanOperationType.VERIFY_PUBLISHED_RELEASE,
            mutates_state=False,
            preconditions=(
                _fact("github.is_draft", False),
                _fact("github.tag", identity.tag),
            ),
            desired_result=(
                _fact("verification.full_verifier_status", VerifierStatus.PASS.value),
                _fact("github.remote_mpp_sha256", identity.mpp_sha256),
            ),
        )
        return tuple(operations)

    remote_complete = _remote_refs_complete(identity, observations.remote)
    local_valid = _local_release_valid(identity, observations.local)
    local_dev_equal = (
        observations.local.dev_commit == identity.release_commit
        or observations.local.dev_relation_to_target is RefRelation.EQUAL
    )

    if not remote_complete:
        if not local_valid:
            _add_operation(
                operations,
                PlanOperationType.FINALIZE_LOCAL,
                mutates_state=True,
                preconditions=(
                    _fact("release_identity.release_commit", identity.release_commit),
                    _fact("release_identity.mpp_sha256", identity.mpp_sha256),
                ),
                desired_result=(
                    _fact("local.release_valid", True),
                    _fact("local.main_commit", identity.release_commit),
                    _fact("local.tag_commit", identity.release_commit),
                ),
            )

        if not local_dev_equal:
            _add_operation(
                operations,
                PlanOperationType.ALIGN_LOCAL_DEV,
                mutates_state=True,
                preconditions=(
                    _fact("local.dev_commit", observations.local.dev_commit),
                    _fact(
                        "local.dev_relation_to_target",
                        observations.local.dev_relation_to_target.value,
                    ),
                ),
                desired_result=(
                    _fact("local.dev_commit", identity.release_commit),
                ),
            )

        _add_operation(
            operations,
            PlanOperationType.PUSH_RELEASE_REFS_ATOMIC,
            mutates_state=True,
            preconditions=(
                _fact("remote.main_commit", observations.remote.main_commit),
                _fact("remote.dev_commit", observations.remote.dev_commit),
                _fact("remote.tag_commit", observations.remote.tag_commit),
                _fact("local.main_commit", identity.release_commit),
                _fact("local.dev_commit", identity.release_commit),
                _fact("local.tag_commit", identity.release_commit),
            ),
            desired_result=(
                _fact("remote.main_commit", identity.release_commit),
                _fact("remote.dev_commit", identity.release_commit),
                _fact("remote.tag_commit", identity.release_commit),
            ),
        )

    github = observations.github

    if not github.present:
        _add_operation(
            operations,
            PlanOperationType.CREATE_DRAFT_RELEASE,
            mutates_state=True,
            preconditions=(
                _fact("remote.release_refs_complete", True),
                _fact("github.present", False),
            ),
            desired_result=(
                _fact("github.present", True),
                _fact("github.is_draft", True),
                _fact("github.tag", identity.tag),
            ),
        )
        _add_operation(
            operations,
            PlanOperationType.UPLOAD_MPP_ASSET,
            mutates_state=True,
            preconditions=(
                _fact("github.is_draft", True),
                _fact("local.mpp_sha256", identity.mpp_sha256),
            ),
            desired_result=(_fact("github.mpp_asset_count", 1),),
        )
        _add_operation(
            operations,
            PlanOperationType.UPLOAD_SIGNATURE_ASSET,
            mutates_state=True,
            preconditions=(
                _fact("github.is_draft", True),
                _fact("local.signature_valid", True),
            ),
            desired_result=(_fact("github.signature_asset_count", 1),),
        )
        _add_operation(
            operations,
            PlanOperationType.VERIFY_DRAFT_ASSETS,
            mutates_state=False,
            preconditions=(
                _fact("github.mpp_asset_count", 1),
                _fact("github.signature_asset_count", 1),
            ),
            desired_result=(
                _fact("github.remote_mpp_sha256", identity.mpp_sha256),
                _fact("github.remote_signature_valid", True),
                _fact("github.remote_mpp_structure_valid", True),
            ),
        )
        _add_operation(
            operations,
            PlanOperationType.PUBLISH_DRAFT_RELEASE,
            mutates_state=True,
            preconditions=(
                _fact("github.is_draft", True),
                _fact("github.draft_assets_verified", True),
            ),
            desired_result=(_fact("github.is_draft", False),),
        )
    elif github.is_draft is True:
        if github.mpp_asset_count == 0:
            _add_operation(
                operations,
                PlanOperationType.UPLOAD_MPP_ASSET,
                mutates_state=True,
                preconditions=(
                    _fact("github.is_draft", True),
                    _fact("local.mpp_sha256", identity.mpp_sha256),
                ),
                desired_result=(_fact("github.mpp_asset_count", 1),),
            )
        if github.signature_asset_count == 0:
            _add_operation(
                operations,
                PlanOperationType.UPLOAD_SIGNATURE_ASSET,
                mutates_state=True,
                preconditions=(
                    _fact("github.is_draft", True),
                    _fact("local.signature_valid", True),
                ),
                desired_result=(_fact("github.signature_asset_count", 1),),
            )
        if not _draft_assets_verified(identity, github):
            _add_operation(
                operations,
                PlanOperationType.VERIFY_DRAFT_ASSETS,
                mutates_state=False,
                preconditions=(
                    _fact("github.mpp_asset_count", 1),
                    _fact("github.signature_asset_count", 1),
                ),
                desired_result=(
                    _fact("github.remote_mpp_sha256", identity.mpp_sha256),
                    _fact("github.remote_signature_valid", True),
                    _fact("github.remote_mpp_structure_valid", True),
                ),
            )
        _add_operation(
            operations,
            PlanOperationType.PUBLISH_DRAFT_RELEASE,
            mutates_state=True,
            preconditions=(
                _fact("github.is_draft", True),
                _fact("github.draft_assets_verified", True),
            ),
            desired_result=(_fact("github.is_draft", False),),
        )

    _add_operation(
        operations,
        PlanOperationType.VERIFY_PUBLISHED_RELEASE,
        mutates_state=False,
        preconditions=(
            _fact("github.is_draft", False),
            _fact("github.tag", identity.tag),
        ),
        desired_result=(
            _fact("verification.full_verifier_status", VerifierStatus.PASS.value),
            _fact("github.remote_mpp_sha256", identity.mpp_sha256),
        ),
    )
    return tuple(operations)


def _plan_blocking_reasons(
    identity: ReleaseIdentity,
    observations: ReleaseObservations,
    operations: tuple[PlanOperation, ...],
) -> tuple[str, ...]:
    reasons: list[str] = []
    safety = observations.safety
    mutating = any(operation.mutates_state for operation in operations)

    if mutating:
        if not safety.current_branch_is_main:
            reasons.append("current branch is not main")
        if not safety.worktree_clean:
            reasons.append("worktree is not clean")
        if not safety.index_clean:
            reasons.append("index is not clean")
        if not safety.required_tools_available:
            reasons.append("one or more required tools are unavailable")

    operation_types = {operation.operation_type for operation in operations}
    finalization_planned = PlanOperationType.FINALIZE_LOCAL in operation_types

    if (
        PlanOperationType.UPLOAD_MPP_ASSET in operation_types
        and not finalization_planned
    ):
        if not observations.local.mpp_present:
            reasons.append("canonical local MPP is unavailable for upload")
        elif observations.local.mpp_sha256 != identity.mpp_sha256:
            reasons.append("canonical local MPP digest does not match the release identity")
        elif observations.local.mpp_structure_valid is not True:
            reasons.append("canonical local MPP structure is not verified")

    if (
        PlanOperationType.UPLOAD_SIGNATURE_ASSET in operation_types
        and not finalization_planned
    ):
        if not observations.local.signature_present:
            reasons.append("canonical detached signature is unavailable for upload")
        elif observations.local.signature_valid is not True:
            reasons.append("canonical detached signature is not verified")

    return tuple(dict.fromkeys(reasons))


def generate_release_plan(
    identity: ReleaseIdentity,
    observations: ReleaseObservations,
) -> ReleasePlan:
    """Generate a deterministic operation plan without performing I/O."""

    state_result = classify_release_state(identity, observations)
    observation_fingerprint = _canonical_sha256(
        {
            "release_identity": identity,
            "observations": observations,
        }
    )

    if state_result.state is ReleaseState.INCONSISTENT_ABORT:
        disposition = PlanDisposition.CONFLICT_ABORT
        operations: tuple[PlanOperation, ...] = ()
        blocking_reasons = state_result.reasons
    elif not observations.safety.observations_complete:
        disposition = PlanDisposition.OBSERVATION_FAILED
        operations = ()
        blocking_reasons = ("one or more required observations are incomplete",)
    elif state_result.state is ReleaseState.PUBLISHED_AND_VERIFIED:
        disposition = PlanDisposition.NOOP_ALREADY_SATISFIED
        operations = ()
        blocking_reasons = ()
    else:
        disposition = PlanDisposition.APPLY_REQUIRED
        operations = _build_plan_operations(identity, observations, state_result)
        blocking_reasons = _plan_blocking_reasons(
            identity,
            observations,
            operations,
        )

    executable = (
        disposition is PlanDisposition.APPLY_REQUIRED
        and not blocking_reasons
    )

    plan_payload = {
        "schema_version": 1,
        "release_identity": identity,
        "disposition": disposition,
        "observed_state": state_result.state,
        "next_action": state_result.next_action,
        "executable": executable,
        "observation_fingerprint": observation_fingerprint,
        "operations": operations,
        "conflicts": state_result.reasons,
        "warnings": state_result.warnings,
        "blocking_reasons": blocking_reasons,
    }
    plan_hash = _canonical_sha256(plan_payload)

    return ReleasePlan(
        schema_version=1,
        release_identity=identity,
        disposition=disposition,
        observed_state=state_result.state,
        next_action=state_result.next_action,
        executable=executable,
        observation_fingerprint=observation_fingerprint,
        plan_hash=plan_hash,
        operations=operations,
        conflicts=state_result.reasons,
        warnings=state_result.warnings,
        blocking_reasons=blocking_reasons,
    )


@dataclass(frozen=True, slots=True)
class GitCommandResult:
    args: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class GitRunner(Protocol):
    def run(
        self,
        repo_path: Path,
        args: Sequence[str],
    ) -> GitCommandResult:
        ...


@dataclass(frozen=True, slots=True)
class SubprocessGitRunner:
    """Execute read-only Git commands without a shell."""

    git_executable: str = "git"

    def run(
        self,
        repo_path: Path,
        args: Sequence[str],
    ) -> GitCommandResult:
        command = (
            self.git_executable,
            "-C",
            str(repo_path),
            *tuple(args),
        )
        env = os.environ.copy()
        env.update(
            {
                "GIT_OPTIONAL_LOCKS": "0",
                "GIT_TERMINAL_PROMPT": "0",
                "LC_ALL": "C",
            }
        )

        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                env=env,
            )
        except FileNotFoundError as exc:
            return GitCommandResult(
                args=tuple(command),
                returncode=127,
                stdout="",
                stderr=str(exc),
            )
        except OSError as exc:
            return GitCommandResult(
                args=tuple(command),
                returncode=126,
                stdout="",
                stderr=str(exc),
            )

        return GitCommandResult(
            args=tuple(command),
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )


@dataclass(frozen=True, slots=True)
class LocalGitObservationResult:
    repo_root: str | None
    current_branch: str | None
    safety: SafetyObservations
    local: LocalObservations
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def _git_error(
    label: str,
    result: GitCommandResult,
) -> str:
    detail = result.stderr.strip() or result.stdout.strip() or "no diagnostic output"
    return f"{label} failed with exit {result.returncode}: {detail}"


def _resolve_commit_ref(
    runner: GitRunner,
    repo_path: Path,
    ref: str,
    *,
    label: str,
    errors: list[str],
) -> str | None:
    result = runner.run(
        repo_path,
        ("rev-parse", "--verify", "--quiet", f"{ref}^{{commit}}"),
    )
    if result.returncode == 0:
        value = result.stdout.strip()
        if _SHA1_RE.fullmatch(value):
            return value
        errors.append(f"{label} returned an invalid commit object ID: {value!r}")
        return None
    if result.returncode == 1:
        return None

    errors.append(_git_error(label, result))
    return None


def _observe_cleanliness(
    runner: GitRunner,
    repo_path: Path,
    errors: list[str],
) -> tuple[bool, bool]:
    unstaged = runner.run(
        repo_path,
        ("diff", "--quiet", "--no-ext-diff", "--"),
    )
    if unstaged.returncode not in {0, 1}:
        errors.append(_git_error("unstaged worktree check", unstaged))

    staged = runner.run(
        repo_path,
        ("diff", "--cached", "--quiet", "--no-ext-diff", "--"),
    )
    if staged.returncode not in {0, 1}:
        errors.append(_git_error("staged index check", staged))

    untracked = runner.run(
        repo_path,
        ("ls-files", "--others", "--exclude-standard", "-z"),
    )
    if untracked.returncode != 0:
        errors.append(_git_error("untracked file check", untracked))

    worktree_clean = (
        unstaged.returncode == 0
        and untracked.returncode == 0
        and not untracked.stdout
    )
    index_clean = staged.returncode == 0
    return worktree_clean, index_clean


def _observe_ref_relation(
    runner: GitRunner,
    repo_path: Path,
    observed_commit: str | None,
    target_commit: str,
    *,
    label: str,
    errors: list[str],
    warnings: list[str],
) -> RefRelation:
    if observed_commit is None:
        return RefRelation.ABSENT
    if observed_commit == target_commit:
        return RefRelation.EQUAL

    target_exists = runner.run(
        repo_path,
        ("cat-file", "-e", f"{target_commit}^{{commit}}"),
    )
    if target_exists.returncode != 0:
        warnings.append(
            f"cannot classify {label}: release commit is unavailable in the local object database"
        )
        return RefRelation.UNKNOWN

    observed_is_ancestor = runner.run(
        repo_path,
        ("merge-base", "--is-ancestor", observed_commit, target_commit),
    )
    if observed_is_ancestor.returncode not in {0, 1}:
        errors.append(_git_error(f"{label} ancestor check", observed_is_ancestor))
        return RefRelation.UNKNOWN
    if observed_is_ancestor.returncode == 0:
        return RefRelation.ANCESTOR

    target_is_ancestor = runner.run(
        repo_path,
        ("merge-base", "--is-ancestor", target_commit, observed_commit),
    )
    if target_is_ancestor.returncode not in {0, 1}:
        errors.append(_git_error(f"{label} ahead check", target_is_ancestor))
        return RefRelation.UNKNOWN
    if target_is_ancestor.returncode == 0:
        return RefRelation.AHEAD

    return RefRelation.DIVERGENT


def observe_local_git(
    repo_path: str | Path,
    identity: ReleaseIdentity,
    *,
    runner: GitRunner | None = None,
) -> LocalGitObservationResult:
    """Observe local Git state without changing refs, index, worktree, or config."""

    requested_path = Path(repo_path)
    active_runner = runner or SubprocessGitRunner()
    errors: list[str] = []
    warnings: list[str] = []

    repo_root_result = active_runner.run(
        requested_path,
        ("rev-parse", "--show-toplevel"),
    )
    tools_available = repo_root_result.returncode not in {126, 127}

    if repo_root_result.returncode != 0:
        errors.append(_git_error("repository discovery", repo_root_result))
        safety = SafetyObservations(
            current_branch_is_main=False,
            worktree_clean=False,
            index_clean=False,
            required_tools_available=tools_available,
            observations_complete=False,
        )
        return LocalGitObservationResult(
            repo_root=None,
            current_branch=None,
            safety=safety,
            local=LocalObservations(),
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    repo_root = repo_root_result.stdout.strip()
    if not repo_root:
        errors.append("repository discovery returned an empty repository root")
        safety = SafetyObservations(
            current_branch_is_main=False,
            worktree_clean=False,
            index_clean=False,
            required_tools_available=tools_available,
            observations_complete=False,
        )
        return LocalGitObservationResult(
            repo_root=None,
            current_branch=None,
            safety=safety,
            local=LocalObservations(),
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    canonical_repo = Path(repo_root)

    branch_result = active_runner.run(
        canonical_repo,
        ("symbolic-ref", "--quiet", "--short", "HEAD"),
    )
    if branch_result.returncode == 0:
        current_branch = branch_result.stdout.strip() or None
    elif branch_result.returncode == 1:
        current_branch = None
        warnings.append("HEAD is detached")
    else:
        current_branch = None
        errors.append(_git_error("current branch observation", branch_result))

    worktree_clean, index_clean = _observe_cleanliness(
        active_runner,
        canonical_repo,
        errors,
    )

    main_commit = _resolve_commit_ref(
        active_runner,
        canonical_repo,
        "refs/heads/main",
        label="local main resolution",
        errors=errors,
    )
    dev_commit = _resolve_commit_ref(
        active_runner,
        canonical_repo,
        "refs/heads/dev",
        label="local dev resolution",
        errors=errors,
    )
    tag_commit = _resolve_commit_ref(
        active_runner,
        canonical_repo,
        f"refs/tags/{identity.tag}",
        label="local release tag resolution",
        errors=errors,
    )

    tag_type = active_runner.run(
        canonical_repo,
        ("cat-file", "-t", f"refs/tags/{identity.tag}"),
    )
    if tag_type.returncode == 0:
        object_type = tag_type.stdout.strip()
        if object_type == "tag":
            tag_is_annotated: bool | None = True
        elif object_type == "commit":
            tag_is_annotated = False
        else:
            tag_is_annotated = False
            warnings.append(
                f"release tag points to unexpected Git object type: {object_type!r}"
            )
    elif tag_type.returncode in {1, 128} and tag_commit is None:
        tag_is_annotated = None
    else:
        tag_is_annotated = None
        errors.append(_git_error("local release tag type observation", tag_type))

    dev_relation = _observe_ref_relation(
        active_runner,
        canonical_repo,
        dev_commit,
        identity.release_commit,
        label="local dev relation",
        errors=errors,
        warnings=warnings,
    )

    observations_complete = not errors and dev_relation is not RefRelation.UNKNOWN

    local = LocalObservations(
        main_commit=main_commit,
        dev_commit=dev_commit,
        dev_relation_to_target=dev_relation,
        tag_commit=tag_commit,
        tag_is_annotated=tag_is_annotated,
    )
    safety = SafetyObservations(
        current_branch_is_main=current_branch == "main",
        worktree_clean=worktree_clean,
        index_clean=index_clean,
        required_tools_available=tools_available,
        observations_complete=observations_complete,
    )

    return LocalGitObservationResult(
        repo_root=str(canonical_repo),
        current_branch=current_branch,
        safety=safety,
        local=local,
        errors=tuple(errors),
        warnings=tuple(warnings),
    )


@dataclass(frozen=True, slots=True)
class RemoteGitObservationResult:
    repo_root: str | None
    remote_name: str
    remote: RemoteGitObservations
    tag_is_annotated: bool | None
    observations_complete: bool
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def _parse_ls_remote_output(
    stdout: str,
    expected_refs: set[str],
    *,
    errors: list[str],
    warnings: list[str],
) -> dict[str, str]:
    refs: dict[str, str] = {}

    for line_number, raw_line in enumerate(stdout.splitlines(), start=1):
        line = raw_line.rstrip("\r")
        if not line:
            continue

        parts = line.split("\t")
        if len(parts) != 2:
            errors.append(
                f"remote ref listing line {line_number} is malformed: {raw_line!r}"
            )
            continue

        object_id, ref = parts
        if not _SHA1_RE.fullmatch(object_id):
            errors.append(
                f"remote ref listing line {line_number} has invalid object ID: "
                f"{object_id!r}"
            )
            continue

        if ref not in expected_refs:
            warnings.append(f"remote ref listing returned unexpected ref: {ref}")
            continue

        if ref in refs:
            errors.append(f"remote ref listing returned duplicate ref: {ref}")
            continue

        refs[ref] = object_id

    return refs


def _object_available_locally(
    runner: GitRunner,
    repo_path: Path,
    object_id: str,
) -> bool:
    result = runner.run(
        repo_path,
        ("cat-file", "-e", f"{object_id}^{{commit}}"),
    )
    return result.returncode == 0


def _observe_remote_ref_relation(
    runner: GitRunner,
    repo_path: Path,
    observed_commit: str | None,
    target_commit: str,
    *,
    label: str,
    errors: list[str],
    warnings: list[str],
) -> RefRelation:
    if observed_commit is None:
        return RefRelation.ABSENT
    if observed_commit == target_commit:
        return RefRelation.EQUAL

    if not _object_available_locally(runner, repo_path, target_commit):
        warnings.append(
            f"cannot classify {label}: release commit is unavailable in the "
            "local object database"
        )
        return RefRelation.UNKNOWN

    if not _object_available_locally(runner, repo_path, observed_commit):
        warnings.append(
            f"cannot classify {label}: remote commit {observed_commit} is "
            "unavailable in the local object database"
        )
        return RefRelation.UNKNOWN

    observed_is_ancestor = runner.run(
        repo_path,
        ("merge-base", "--is-ancestor", observed_commit, target_commit),
    )
    if observed_is_ancestor.returncode not in {0, 1}:
        errors.append(_git_error(f"{label} ancestor check", observed_is_ancestor))
        return RefRelation.UNKNOWN
    if observed_is_ancestor.returncode == 0:
        return RefRelation.ANCESTOR

    target_is_ancestor = runner.run(
        repo_path,
        ("merge-base", "--is-ancestor", target_commit, observed_commit),
    )
    if target_is_ancestor.returncode not in {0, 1}:
        errors.append(_git_error(f"{label} ahead check", target_is_ancestor))
        return RefRelation.UNKNOWN
    if target_is_ancestor.returncode == 0:
        return RefRelation.AHEAD

    return RefRelation.DIVERGENT


def observe_remote_git(
    repo_path: str | Path,
    identity: ReleaseIdentity,
    *,
    remote_name: str = "origin",
    runner: GitRunner | None = None,
) -> RemoteGitObservationResult:
    """Observe remote Git refs using ``git ls-remote`` without fetching.

    The observer never updates local refs or the object database. Ancestry can
    only be classified when both the target and observed remote commits already
    exist in the local object database; otherwise the relation is UNKNOWN and
    the observation is marked incomplete.
    """

    normalized_remote = remote_name.strip()
    if not normalized_remote or normalized_remote.startswith("-"):
        return RemoteGitObservationResult(
            repo_root=None,
            remote_name=remote_name,
            remote=RemoteGitObservations(
                main_relation_to_target=RefRelation.UNKNOWN,
                dev_relation_to_target=RefRelation.UNKNOWN,
            ),
            tag_is_annotated=None,
            observations_complete=False,
            errors=("remote name must be non-empty and must not start with '-'",),
        )

    requested_path = Path(repo_path)
    active_runner = runner or SubprocessGitRunner()
    errors: list[str] = []
    warnings: list[str] = []

    repo_root_result = active_runner.run(
        requested_path,
        ("rev-parse", "--show-toplevel"),
    )
    if repo_root_result.returncode != 0:
        errors.append(_git_error("repository discovery", repo_root_result))
        return RemoteGitObservationResult(
            repo_root=None,
            remote_name=normalized_remote,
            remote=RemoteGitObservations(
                main_relation_to_target=RefRelation.UNKNOWN,
                dev_relation_to_target=RefRelation.UNKNOWN,
            ),
            tag_is_annotated=None,
            observations_complete=False,
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    repo_root = repo_root_result.stdout.strip()
    if not repo_root:
        errors.append("repository discovery returned an empty repository root")
        return RemoteGitObservationResult(
            repo_root=None,
            remote_name=normalized_remote,
            remote=RemoteGitObservations(
                main_relation_to_target=RefRelation.UNKNOWN,
                dev_relation_to_target=RefRelation.UNKNOWN,
            ),
            tag_is_annotated=None,
            observations_complete=False,
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    canonical_repo = Path(repo_root)
    main_ref = "refs/heads/main"
    dev_ref = "refs/heads/dev"
    tag_ref = f"refs/tags/{identity.tag}"
    peeled_tag_ref = f"{tag_ref}^{{}}"
    expected_refs = {main_ref, dev_ref, tag_ref, peeled_tag_ref}

    listing = active_runner.run(
        canonical_repo,
        (
            "ls-remote",
            "--exit-code",
            normalized_remote,
            main_ref,
            dev_ref,
            tag_ref,
            peeled_tag_ref,
        ),
    )

    if listing.returncode not in {0, 2}:
        errors.append(_git_error("remote ref listing", listing))
        return RemoteGitObservationResult(
            repo_root=str(canonical_repo),
            remote_name=normalized_remote,
            remote=RemoteGitObservations(
                main_relation_to_target=RefRelation.UNKNOWN,
                dev_relation_to_target=RefRelation.UNKNOWN,
            ),
            tag_is_annotated=None,
            observations_complete=False,
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    refs = _parse_ls_remote_output(
        listing.stdout,
        expected_refs,
        errors=errors,
        warnings=warnings,
    )

    main_commit = refs.get(main_ref)
    dev_commit = refs.get(dev_ref)
    raw_tag_object = refs.get(tag_ref)
    peeled_tag_commit = refs.get(peeled_tag_ref)

    if peeled_tag_commit is not None and raw_tag_object is None:
        errors.append("remote tag peel was returned without the tag ref")

    if peeled_tag_commit is not None:
        tag_commit = peeled_tag_commit
        tag_is_annotated: bool | None = True
    elif raw_tag_object is not None:
        tag_commit = raw_tag_object
        tag_is_annotated = False
    else:
        tag_commit = None
        tag_is_annotated = None

    main_relation = _observe_remote_ref_relation(
        active_runner,
        canonical_repo,
        main_commit,
        identity.release_commit,
        label="remote main relation",
        errors=errors,
        warnings=warnings,
    )
    dev_relation = _observe_remote_ref_relation(
        active_runner,
        canonical_repo,
        dev_commit,
        identity.release_commit,
        label="remote dev relation",
        errors=errors,
        warnings=warnings,
    )

    observations_complete = (
        not errors
        and main_relation is not RefRelation.UNKNOWN
        and dev_relation is not RefRelation.UNKNOWN
    )

    remote = RemoteGitObservations(
        main_commit=main_commit,
        main_relation_to_target=main_relation,
        dev_commit=dev_commit,
        dev_relation_to_target=dev_relation,
        tag_commit=tag_commit,
    )

    return RemoteGitObservationResult(
        repo_root=str(canonical_repo),
        remote_name=normalized_remote,
        remote=remote,
        tag_is_annotated=tag_is_annotated,
        observations_complete=observations_complete,
        errors=tuple(errors),
        warnings=tuple(warnings),
    )


GITHUB_API_VERSION = "2026-03-10"
_REPOSITORY_SLUG_RE = re.compile(
    r"^[A-Za-z0-9](?:[A-Za-z0-9_.-]{0,99})/"
    r"[A-Za-z0-9](?:[A-Za-z0-9_.-]{0,99})$"
)


@dataclass(frozen=True, slots=True)
class GhCommandResult:
    args: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class GhRunner(Protocol):
    def run(self, args: Sequence[str]) -> GhCommandResult:
        ...


@dataclass(frozen=True, slots=True)
class SubprocessGhRunner:
    """Execute read-only GitHub CLI commands without a shell."""

    gh_executable: str = "gh"

    def run(self, args: Sequence[str]) -> GhCommandResult:
        command = (self.gh_executable, *tuple(args))
        env = os.environ.copy()
        env.update(
            {
                "GH_PROMPT_DISABLED": "1",
                "GH_PAGER": "cat",
                "NO_COLOR": "1",
                "LC_ALL": "C",
            }
        )

        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                env=env,
            )
        except FileNotFoundError as exc:
            return GhCommandResult(
                args=tuple(command),
                returncode=127,
                stdout="",
                stderr=str(exc),
            )
        except OSError as exc:
            return GhCommandResult(
                args=tuple(command),
                returncode=126,
                stdout="",
                stderr=str(exc),
            )

        return GhCommandResult(
            args=tuple(command),
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )


@dataclass(frozen=True, slots=True)
class GitHubReleaseObservationResult:
    repository: str
    viewer_can_push: bool | None
    github: GitHubReleaseObservations
    release_id: int | None
    is_immutable: bool | None
    target_commitish: str | None
    html_url: str | None
    mpp_asset_ids: tuple[int, ...]
    signature_asset_ids: tuple[int, ...]
    observations_complete: bool
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def _gh_error(label: str, result: GhCommandResult) -> str:
    detail = result.stderr.strip() or result.stdout.strip() or "no diagnostic output"
    return f"{label} failed with exit {result.returncode}: {detail}"


def _normalize_github_digest(
    digest: Any,
    *,
    label: str,
    errors: list[str],
    warnings: list[str],
) -> str | None:
    if digest is None:
        warnings.append(f"{label} does not expose an API digest")
        return None
    if not isinstance(digest, str):
        errors.append(f"{label} digest must be a string or null")
        return None

    prefix = "sha256:"
    if not digest.startswith(prefix):
        errors.append(f"{label} digest must use the sha256:<hex> format")
        return None

    value = digest[len(prefix):]
    if not _SHA256_RE.fullmatch(value):
        errors.append(f"{label} digest contains an invalid SHA256 value")
        return None
    return value


def _parse_release_pages(
    stdout: str,
    *,
    errors: list[str],
) -> list[dict[str, Any]]:
    try:
        payload = json.loads(stdout)
    except json.JSONDecodeError as exc:
        errors.append(f"GitHub release listing returned invalid JSON: {exc}")
        return []

    if not isinstance(payload, list):
        errors.append("GitHub release listing must be a JSON array of pages")
        return []

    releases: list[dict[str, Any]] = []
    for page_number, page in enumerate(payload, start=1):
        if not isinstance(page, list):
            errors.append(
                f"GitHub release listing page {page_number} must be a JSON array"
            )
            continue
        for item_number, item in enumerate(page, start=1):
            if not isinstance(item, dict):
                errors.append(
                    "GitHub release listing item "
                    f"{page_number}:{item_number} must be a JSON object"
                )
                continue
            releases.append(item)
    return releases


def _positive_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _validate_matching_release(
    release: dict[str, Any],
    identity: ReleaseIdentity,
    *,
    errors: list[str],
    warnings: list[str],
) -> GitHubReleaseObservationResult:
    release_id = release.get("id")
    draft = release.get("draft")
    prerelease = release.get("prerelease")
    immutable = release.get("immutable")
    target_commitish = release.get("target_commitish")
    html_url = release.get("html_url")
    assets = release.get("assets")

    if not _positive_int(release_id):
        errors.append("matching GitHub release has an invalid release id")
        release_id = None
    if not isinstance(draft, bool):
        errors.append("matching GitHub release has a non-boolean draft field")
        draft = None
    if not isinstance(prerelease, bool):
        errors.append("matching GitHub release has a non-boolean prerelease field")
        prerelease = None
    if not isinstance(immutable, bool):
        errors.append("matching GitHub release has a non-boolean immutable field")
        immutable = None
    if not isinstance(target_commitish, str) or not target_commitish:
        errors.append("matching GitHub release has an invalid target_commitish")
        target_commitish = None
    if not isinstance(html_url, str) or not html_url:
        errors.append("matching GitHub release has an invalid html_url")
        html_url = None
    if not isinstance(assets, list):
        errors.append("matching GitHub release assets must be a JSON array")
        assets = []

    mpp_asset_ids: list[int] = []
    signature_asset_ids: list[int] = []
    mpp_digest: str | None = None
    expected_asset_not_uploaded = False

    for index, asset in enumerate(assets, start=1):
        if not isinstance(asset, dict):
            errors.append(f"GitHub release asset {index} must be a JSON object")
            continue

        asset_id = asset.get("id")
        name = asset.get("name")
        state = asset.get("state")

        if not _positive_int(asset_id):
            errors.append(f"GitHub release asset {index} has an invalid asset id")
            continue
        if not isinstance(name, str) or not name:
            errors.append(f"GitHub release asset {index} has an invalid name")
            continue
        if not isinstance(state, str) or not state:
            errors.append(f"GitHub release asset {index} has an invalid state")
            continue

        is_mpp = name == identity.mpp_asset_name
        is_signature = name == identity.signature_asset_name
        if not is_mpp and not is_signature:
            continue

        if state != "uploaded":
            expected_asset_not_uploaded = True
            warnings.append(
                f"expected GitHub release asset {name!r} is in state {state!r}"
            )

        if is_mpp:
            mpp_asset_ids.append(asset_id)
            if len(mpp_asset_ids) == 1:
                mpp_digest = _normalize_github_digest(
                    asset.get("digest"),
                    label=f"GitHub release asset {name!r}",
                    errors=errors,
                    warnings=warnings,
                )
        else:
            signature_asset_ids.append(asset_id)

    if len(mpp_asset_ids) != 1:
        mpp_digest = None

    github = GitHubReleaseObservations(
        present=True,
        tag=identity.tag,
        is_draft=draft,
        is_prerelease=prerelease,
        mpp_asset_count=len(mpp_asset_ids),
        signature_asset_count=len(signature_asset_ids),
        mpp_asset_digest=mpp_digest,
        remote_mpp_sha256=None,
        remote_signature_valid=None,
        remote_mpp_structure_valid=None,
    )

    observations_complete = not errors and not expected_asset_not_uploaded
    return GitHubReleaseObservationResult(
        repository="",
        viewer_can_push=True,
        github=github,
        release_id=release_id,
        is_immutable=immutable,
        target_commitish=target_commitish,
        html_url=html_url,
        mpp_asset_ids=tuple(mpp_asset_ids),
        signature_asset_ids=tuple(signature_asset_ids),
        observations_complete=observations_complete,
        errors=tuple(errors),
        warnings=tuple(warnings),
    )


def _github_api_get_args(endpoint: str, *, paginate: bool = False) -> tuple[str, ...]:
    args: list[str] = [
        "api",
        "--method",
        "GET",
    ]
    if paginate:
        args.extend(("--paginate", "--slurp"))
    args.extend(
        (
            "--header",
            "Accept: application/vnd.github+json",
            "--header",
            f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
            endpoint,
        )
    )
    return tuple(args)


def _parse_repository_access(
    stdout: str,
    repository: str,
    *,
    errors: list[str],
) -> bool | None:
    try:
        payload = json.loads(stdout)
    except json.JSONDecodeError as exc:
        errors.append(f"GitHub repository metadata returned invalid JSON: {exc}")
        return None

    if not isinstance(payload, dict):
        errors.append("GitHub repository metadata must be a JSON object")
        return None

    full_name = payload.get("full_name")
    if not isinstance(full_name, str) or full_name.casefold() != repository.casefold():
        errors.append("GitHub repository metadata full_name does not match the requested repository")

    permissions = payload.get("permissions")
    if not isinstance(permissions, dict):
        errors.append("GitHub repository metadata is missing the authenticated permissions object")
        return None

    can_push = permissions.get("push")
    if not isinstance(can_push, bool):
        errors.append("GitHub repository metadata permissions.push must be boolean")
        return None

    if not can_push:
        errors.append(
            "authenticated GitHub viewer lacks push permission; draft release "
            "visibility cannot be guaranteed"
        )
    return can_push


def observe_github_release(
    repository: str,
    identity: ReleaseIdentity,
    *,
    runner: GhRunner | None = None,
) -> GitHubReleaseObservationResult:
    """Observe GitHub Release metadata using a read-only REST API listing.

    The list endpoint is used instead of the by-tag endpoint because authenticated
    callers with push access can also observe draft releases. The command uses
    explicit GET semantics, pagination, JSON slurping, and the current GitHub
    REST API version. It never downloads, uploads, edits, publishes, or deletes
    release assets.
    """

    normalized_repository = repository.strip()
    if not _REPOSITORY_SLUG_RE.fullmatch(normalized_repository):
        return GitHubReleaseObservationResult(
            repository=repository,
            viewer_can_push=None,
            github=GitHubReleaseObservations(),
            release_id=None,
            is_immutable=None,
            target_commitish=None,
            html_url=None,
            mpp_asset_ids=(),
            signature_asset_ids=(),
            observations_complete=False,
            errors=(
                "repository must use the OWNER/REPO form with safe path characters",
            ),
        )

    active_runner = runner or SubprocessGhRunner()
    errors: list[str] = []
    warnings: list[str] = []

    repository_result = active_runner.run(
        _github_api_get_args(f"repos/{normalized_repository}")
    )
    if repository_result.returncode != 0:
        return GitHubReleaseObservationResult(
            repository=normalized_repository,
            viewer_can_push=None,
            github=GitHubReleaseObservations(),
            release_id=None,
            is_immutable=None,
            target_commitish=None,
            html_url=None,
            mpp_asset_ids=(),
            signature_asset_ids=(),
            observations_complete=False,
            errors=(_gh_error("GitHub repository metadata", repository_result),),
        )

    viewer_can_push = _parse_repository_access(
        repository_result.stdout,
        normalized_repository,
        errors=errors,
    )
    if errors or viewer_can_push is not True:
        return GitHubReleaseObservationResult(
            repository=normalized_repository,
            viewer_can_push=viewer_can_push,
            github=GitHubReleaseObservations(),
            release_id=None,
            is_immutable=None,
            target_commitish=None,
            html_url=None,
            mpp_asset_ids=(),
            signature_asset_ids=(),
            observations_complete=False,
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    endpoint = f"repos/{normalized_repository}/releases?per_page=100"
    result = active_runner.run(_github_api_get_args(endpoint, paginate=True))

    if result.returncode != 0:
        return GitHubReleaseObservationResult(
            repository=normalized_repository,
            viewer_can_push=True,
            github=GitHubReleaseObservations(),
            release_id=None,
            is_immutable=None,
            target_commitish=None,
            html_url=None,
            mpp_asset_ids=(),
            signature_asset_ids=(),
            observations_complete=False,
            errors=(_gh_error("GitHub release listing", result),),
        )

    releases = _parse_release_pages(result.stdout, errors=errors)

    matches: list[dict[str, Any]] = []
    for index, release in enumerate(releases, start=1):
        tag_name = release.get("tag_name")
        if not isinstance(tag_name, str) or not tag_name:
            errors.append(f"GitHub release listing item {index} has an invalid tag_name")
            continue
        if tag_name == identity.tag:
            matches.append(release)

    if len(matches) > 1:
        errors.append(
            f"GitHub release listing returned multiple releases for tag {identity.tag!r}"
        )

    if errors or len(matches) > 1:
        return GitHubReleaseObservationResult(
            repository=normalized_repository,
            viewer_can_push=True,
            github=GitHubReleaseObservations(),
            release_id=None,
            is_immutable=None,
            target_commitish=None,
            html_url=None,
            mpp_asset_ids=(),
            signature_asset_ids=(),
            observations_complete=False,
            errors=tuple(errors),
            warnings=tuple(warnings),
        )

    if not matches:
        return GitHubReleaseObservationResult(
            repository=normalized_repository,
            viewer_can_push=True,
            github=GitHubReleaseObservations(),
            release_id=None,
            is_immutable=None,
            target_commitish=None,
            html_url=None,
            mpp_asset_ids=(),
            signature_asset_ids=(),
            observations_complete=True,
            errors=(),
            warnings=(),
        )

    parsed = _validate_matching_release(
        matches[0],
        identity,
        errors=errors,
        warnings=warnings,
    )
    return GitHubReleaseObservationResult(
        repository=normalized_repository,
        viewer_can_push=True,
        github=parsed.github,
        release_id=parsed.release_id,
        is_immutable=parsed.is_immutable,
        target_commitish=parsed.target_commitish,
        html_url=parsed.html_url,
        mpp_asset_ids=parsed.mpp_asset_ids,
        signature_asset_ids=parsed.signature_asset_ids,
        observations_complete=parsed.observations_complete,
        errors=parsed.errors,
        warnings=parsed.warnings,
    )


REQUIRED_MPP_ENTRIES: tuple[str, ...] = (
    "classes.dex",
    "extensions/boostforreddit.mpe",
)


@dataclass(frozen=True, slots=True)
class GpgCommandResult:
    args: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class GpgRunner(Protocol):
    def run(
        self,
        mpp_path: Path,
        signature_path: Path,
    ) -> GpgCommandResult:
        ...


@dataclass(frozen=True, slots=True)
class SubprocessGpgRunner:
    """Verify a detached signature without invoking a shell."""

    gpg_executable: str = "gpg"

    def run(
        self,
        mpp_path: Path,
        signature_path: Path,
    ) -> GpgCommandResult:
        command = (
            self.gpg_executable,
            "--batch",
            "--no-tty",
            "--no-auto-key-retrieve",
            "--no-auto-check-trustdb",
            "--status-fd",
            "1",
            "--verify",
            str(signature_path),
            str(mpp_path),
        )
        env = os.environ.copy()
        env.update(
            {
                "LC_ALL": "C",
                "GPG_TTY": "",
            }
        )

        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                env=env,
            )
        except FileNotFoundError as exc:
            return GpgCommandResult(
                args=tuple(command),
                returncode=127,
                stdout="",
                stderr=str(exc),
            )
        except OSError as exc:
            return GpgCommandResult(
                args=tuple(command),
                returncode=126,
                stdout="",
                stderr=str(exc),
            )

        return GpgCommandResult(
            args=tuple(command),
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )


@dataclass(frozen=True, slots=True)
class LocalArtifactObservationResult:
    repo_root: str
    mpp_path: str
    signature_path: str
    local: LocalObservations
    required_entries: tuple[str, ...]
    missing_entries: tuple[str, ...]
    duplicate_entries: tuple[str, ...]
    observations_complete: bool
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def _canonical_artifact_path(
    repo_root: Path,
    filename: str,
    *,
    label: str,
) -> Path:
    if not filename or Path(filename).name != filename:
        raise ValueError(f"{label} must be a plain filename")
    return repo_root / "patches" / "build" / "libs" / filename


def _regular_file_state(
    path: Path,
    *,
    label: str,
    errors: list[str],
) -> bool:
    try:
        if not path.exists():
            return False
        if path.is_symlink():
            errors.append(f"{label} must not be a symbolic link: {path}")
            return False
        if not path.is_file():
            errors.append(f"{label} is not a regular file: {path}")
            return False
        return True
    except OSError as exc:
        errors.append(f"{label} could not be inspected: {exc}")
        return False


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def _inspect_mpp_structure(
    path: Path,
    *,
    required_entries: tuple[str, ...],
    errors: list[str],
    warnings: list[str],
) -> tuple[bool | None, tuple[str, ...], tuple[str, ...]]:
    try:
        with zipfile.ZipFile(path, mode="r") as archive:
            infos = archive.infolist()
            names = [item.filename for item in infos]

            corrupt_member = archive.testzip()
            if corrupt_member is not None:
                warnings.append(f"MPP ZIP member failed CRC verification: {corrupt_member}")
                structure_valid = False
            else:
                structure_valid = True

            missing_entries = tuple(
                entry for entry in required_entries if names.count(entry) == 0
            )
            duplicate_entries = tuple(
                entry for entry in required_entries if names.count(entry) > 1
            )

            if missing_entries:
                structure_valid = False
                warnings.append(
                    "MPP is missing required entries: " + ", ".join(missing_entries)
                )
            if duplicate_entries:
                structure_valid = False
                warnings.append(
                    "MPP contains duplicate required entries: "
                    + ", ".join(duplicate_entries)
                )

            return structure_valid, missing_entries, duplicate_entries
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        warnings.append(f"MPP ZIP structure is invalid: {exc}")
        return False, required_entries, ()


def _parse_gpg_status(
    result: GpgCommandResult,
    *,
    expected_signing_identity: str,
    errors: list[str],
    warnings: list[str],
) -> tuple[bool | None, bool]:
    statuses: list[tuple[str, tuple[str, ...]]] = []
    prefix = "[GNUPG:] "

    for raw_line in result.stdout.splitlines():
        if not raw_line.startswith(prefix):
            continue
        fields = raw_line[len(prefix):].split()
        if fields:
            statuses.append((fields[0], tuple(fields[1:])))

    valid_fingerprints = tuple(
        fields[0].upper()
        for keyword, fields in statuses
        if keyword == "VALIDSIG" and fields
    )
    expected = expected_signing_identity.replace(" ", "").upper()

    if len(valid_fingerprints) == 1 and result.returncode == 0:
        actual = valid_fingerprints[0]
        if actual == expected:
            return True, True
        warnings.append(
            "detached signature was made by an unexpected signing identity: "
            f"{actual}"
        )
        return False, True

    if len(valid_fingerprints) > 1:
        warnings.append("detached signature produced multiple VALIDSIG identities")
        return False, True

    status_keywords = {keyword for keyword, _ in statuses}
    if status_keywords.intersection({"BADSIG", "ERRSIG"}):
        warnings.append("detached signature is invalid")
        return False, True

    if "NO_PUBKEY" in status_keywords:
        errors.append("detached signature could not be verified because the public key is unavailable")
        return None, False

    detail = result.stderr.strip() or result.stdout.strip() or "no diagnostic output"
    if result.returncode in {126, 127}:
        errors.append(f"GPG verification tool is unavailable: {detail}")
    else:
        errors.append(
            "GPG verification did not produce one valid signature "
            f"(exit {result.returncode}): {detail}"
        )
    return None, False


def observe_local_artifacts(
    repo_path: str | Path,
    identity: ReleaseIdentity,
    *,
    gpg_runner: GpgRunner | None = None,
    required_entries: Sequence[str] = REQUIRED_MPP_ENTRIES,
) -> LocalArtifactObservationResult:
    """Inspect canonical local MPP and signature files without modifying them."""

    errors: list[str] = []
    warnings: list[str] = []
    observations_complete = True

    root = Path(repo_path).resolve()
    normalized_required = tuple(required_entries)

    if not normalized_required:
        raise ValueError("required_entries must not be empty")
    if len(set(normalized_required)) != len(normalized_required):
        raise ValueError("required_entries must not contain duplicates")
    if any(not entry or entry.startswith("/") for entry in normalized_required):
        raise ValueError("required_entries must contain non-empty relative ZIP names")

    mpp_path = _canonical_artifact_path(
        root,
        identity.mpp_asset_name,
        label="MPP asset name",
    )
    signature_path = _canonical_artifact_path(
        root,
        identity.signature_asset_name,
        label="signature asset name",
    )

    mpp_present = _regular_file_state(
        mpp_path,
        label="canonical MPP",
        errors=errors,
    )
    signature_present = _regular_file_state(
        signature_path,
        label="canonical detached signature",
        errors=errors,
    )

    if errors:
        observations_complete = False

    mpp_sha256: str | None = None
    mpp_structure_valid: bool | None = None
    missing_entries: tuple[str, ...] = ()
    duplicate_entries: tuple[str, ...] = ()

    if mpp_present:
        try:
            mpp_sha256 = _sha256_file(mpp_path)
        except OSError as exc:
            errors.append(f"canonical MPP could not be hashed: {exc}")
            observations_complete = False

        (
            mpp_structure_valid,
            missing_entries,
            duplicate_entries,
        ) = _inspect_mpp_structure(
            mpp_path,
            required_entries=normalized_required,
            errors=errors,
            warnings=warnings,
        )
    elif signature_present:
        warnings.append("detached signature exists without the canonical MPP")

    signature_valid: bool | None = None
    if mpp_present and signature_present:
        active_runner = gpg_runner or SubprocessGpgRunner()
        gpg_result = active_runner.run(mpp_path, signature_path)
        signature_valid, gpg_complete = _parse_gpg_status(
            gpg_result,
            expected_signing_identity=identity.signing_identity,
            errors=errors,
            warnings=warnings,
        )
        observations_complete = observations_complete and gpg_complete

    local = LocalObservations(
        mpp_present=mpp_present,
        signature_present=signature_present,
        mpp_sha256=mpp_sha256,
        signature_valid=signature_valid,
        mpp_structure_valid=mpp_structure_valid,
    )

    return LocalArtifactObservationResult(
        repo_root=str(root),
        mpp_path=str(mpp_path),
        signature_path=str(signature_path),
        local=local,
        required_entries=normalized_required,
        missing_entries=missing_entries,
        duplicate_entries=duplicate_entries,
        observations_complete=observations_complete,
        errors=tuple(errors),
        warnings=tuple(warnings),
    )


MAX_MANAGER_DESCRIPTION_LENGTH = 700
_REQUIRED_README_RELEASE_FIELDS: tuple[str, ...] = (
    "Version",
    "Release tag",
    "Asset",
    "SHA256",
    "Manager JSON",
    "Download URL",
)


@dataclass(frozen=True, slots=True)
class LocalMetadataObservationResult:
    repo_root: str
    readme_path: str
    bundle_path: str
    repository: str
    local: LocalObservations
    expected_download_url: str
    expected_signature_download_url: str
    expected_manager_json_url: str
    bundle_version: str | None
    bundle_created_at: str | None
    bundle_description: str | None
    bundle_download_url: str | None
    bundle_signature_download_url: str | None
    readme_version: str | None
    readme_tag: str | None
    readme_asset: str | None
    readme_sha256: str | None
    readme_standalone_sha256: str | None
    readme_manager_json_url: str | None
    readme_download_url: str | None
    observations_complete: bool
    mismatches: tuple[str, ...] = ()
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


class _DuplicateJsonKeyError(ValueError):
    pass


def _read_canonical_utf8_file(
    path: Path,
    *,
    label: str,
    errors: list[str],
) -> str | None:
    if not _regular_file_state(path, label=label, errors=errors):
        if not errors or not errors[-1].startswith(label):
            errors.append(f"{label} is missing: {path}")
        return None

    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        errors.append(f"{label} is not valid UTF-8: {exc}")
    except OSError as exc:
        errors.append(f"{label} could not be read: {exc}")
    return None


def _json_object_without_duplicate_keys(
    text: str,
    *,
    label: str,
    errors: list[str],
) -> dict[str, Any] | None:
    def object_pairs_hook(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        output: dict[str, Any] = {}
        for key, value in pairs:
            if key in output:
                raise _DuplicateJsonKeyError(f"duplicate JSON key {key!r}")
            output[key] = value
        return output

    try:
        payload = json.loads(text, object_pairs_hook=object_pairs_hook)
    except (json.JSONDecodeError, _DuplicateJsonKeyError) as exc:
        errors.append(f"{label} is invalid JSON: {exc}")
        return None

    if not isinstance(payload, dict):
        errors.append(f"{label} root must be a JSON object")
        return None
    return payload


def _required_nonempty_string(
    payload: dict[str, Any],
    key: str,
    *,
    label: str,
    errors: list[str],
) -> str | None:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        errors.append(f"{label} field {key!r} must be a non-empty string")
        return None
    return value


def _strip_markdown_code(value: str) -> str:
    normalized = value.strip()
    if len(normalized) >= 2 and normalized.startswith("`") and normalized.endswith("`"):
        return normalized[1:-1]
    return normalized


def _parse_current_release_readme(
    text: str,
    *,
    errors: list[str],
) -> tuple[dict[str, str], str | None]:
    sections = list(
        re.finditer(
            r"(?ms)^## Current release\s*$\n(.*?)(?=^##\s|\Z)",
            text,
        )
    )
    if len(sections) != 1:
        errors.append(
            "README.md must contain exactly one '## Current release' section"
        )
        return {}, None

    section = sections[0].group(1)
    rows: dict[str, str] = {}
    for raw_line in section.splitlines():
        line = raw_line.strip()
        if not line.startswith("|") or not line.endswith("|"):
            continue
        cells = [cell.strip() for cell in line[1:-1].split("|")]
        if len(cells) != 2:
            continue
        key, value = cells
        if key in {"Field", "---"} or set(key) <= {"-", ":"}:
            continue
        if key in rows:
            errors.append(f"README.md Current release contains duplicate field {key!r}")
            continue
        rows[key] = _strip_markdown_code(value)

    for field in _REQUIRED_README_RELEASE_FIELDS:
        if field not in rows or not rows[field]:
            errors.append(
                f"README.md Current release is missing non-empty field {field!r}"
            )

    standalone_matches = re.findall(
        r"(?m)^SHA256:\s*`([0-9a-f]{64})`\s*$",
        section,
    )
    standalone_sha: str | None
    if len(standalone_matches) != 1:
        errors.append(
            "README.md Current release must contain exactly one standalone SHA256 line"
        )
        standalone_sha = None
    else:
        standalone_sha = standalone_matches[0]

    return rows, standalone_sha


def _release_metadata_urls(
    repository: str,
    identity: ReleaseIdentity,
) -> tuple[str, str, str]:
    download_url = (
        f"https://github.com/{repository}/releases/download/"
        f"{identity.tag}/{identity.mpp_asset_name}"
    )
    signature_url = (
        f"https://github.com/{repository}/releases/download/"
        f"{identity.tag}/{identity.signature_asset_name}"
    )
    manager_json_url = (
        f"https://raw.githubusercontent.com/{repository}/main/patches-bundle.json"
    )
    return download_url, signature_url, manager_json_url


def observe_local_metadata(
    repo_path: str | Path,
    repository: str,
    identity: ReleaseIdentity,
) -> LocalMetadataObservationResult:
    """Inspect canonical README and Manager bundle metadata without mutation."""

    normalized_repository = repository.strip()
    if not _REPOSITORY_SLUG_RE.fullmatch(normalized_repository):
        raise ValueError(
            "repository must use the OWNER/REPO form with safe path characters"
        )

    root = Path(repo_path).resolve()
    readme_path = root / "README.md"
    bundle_path = root / "patches-bundle.json"
    expected_download_url, expected_signature_url, expected_manager_url = (
        _release_metadata_urls(normalized_repository, identity)
    )

    errors: list[str] = []
    mismatches: list[str] = []
    warnings: list[str] = []

    readme_text = _read_canonical_utf8_file(
        readme_path,
        label="canonical README.md",
        errors=errors,
    )
    bundle_text = _read_canonical_utf8_file(
        bundle_path,
        label="canonical patches-bundle.json",
        errors=errors,
    )

    bundle_version: str | None = None
    bundle_created_at: str | None = None
    bundle_description: str | None = None
    bundle_download_url: str | None = None
    bundle_signature_download_url: str | None = None

    if bundle_text is not None:
        bundle = _json_object_without_duplicate_keys(
            bundle_text,
            label="canonical patches-bundle.json",
            errors=errors,
        )
        if bundle is not None:
            bundle_version = _required_nonempty_string(
                bundle,
                "version",
                label="canonical patches-bundle.json",
                errors=errors,
            )
            bundle_created_at = _required_nonempty_string(
                bundle,
                "created_at",
                label="canonical patches-bundle.json",
                errors=errors,
            )
            bundle_description = _required_nonempty_string(
                bundle,
                "description",
                label="canonical patches-bundle.json",
                errors=errors,
            )
            bundle_download_url = _required_nonempty_string(
                bundle,
                "download_url",
                label="canonical patches-bundle.json",
                errors=errors,
            )
            bundle_signature_download_url = _required_nonempty_string(
                bundle,
                "signature_download_url",
                label="canonical patches-bundle.json",
                errors=errors,
            )

            if (
                bundle_description is not None
                and len(bundle_description) > MAX_MANAGER_DESCRIPTION_LENGTH
            ):
                mismatches.append(
                    "patches-bundle.json description exceeds the Manager-facing length limit"
                )

            if bundle_version is not None:
                acceptable_versions = {
                    identity.version,
                    f"v{identity.version}",
                }
                if bundle_version not in acceptable_versions:
                    mismatches.append(
                        "patches-bundle.json version does not match the release identity"
                    )
            if (
                bundle_download_url is not None
                and bundle_download_url != expected_download_url
            ):
                mismatches.append(
                    "patches-bundle.json download_url does not match the release identity"
                )
            if (
                bundle_signature_download_url is not None
                and bundle_signature_download_url != expected_signature_url
            ):
                mismatches.append(
                    "patches-bundle.json signature_download_url does not match the release identity"
                )

    readme_rows: dict[str, str] = {}
    standalone_sha: str | None = None
    if readme_text is not None:
        readme_rows, standalone_sha = _parse_current_release_readme(
            readme_text,
            errors=errors,
        )

    readme_version = readme_rows.get("Version")
    readme_tag = readme_rows.get("Release tag")
    readme_asset = readme_rows.get("Asset")
    readme_sha256 = readme_rows.get("SHA256")
    readme_manager_json_url = readme_rows.get("Manager JSON")
    readme_download_url = readme_rows.get("Download URL")

    expected_readme = {
        "Version": identity.version,
        "Release tag": identity.tag,
        "Asset": identity.mpp_asset_name,
        "SHA256": identity.mpp_sha256,
        "Manager JSON": expected_manager_url,
        "Download URL": expected_download_url,
    }
    for field, expected in expected_readme.items():
        actual = readme_rows.get(field)
        if actual is not None and actual != expected:
            mismatches.append(
                f"README.md Current release field {field!r} does not match the release identity"
            )

    if standalone_sha is not None and standalone_sha != identity.mpp_sha256:
        mismatches.append(
            "README.md standalone SHA256 does not match the release identity"
        )
    if (
        standalone_sha is not None
        and readme_sha256 is not None
        and standalone_sha != readme_sha256
    ):
        mismatches.append(
            "README.md table and standalone SHA256 values disagree"
        )

    if readme_sha256 is not None and not _SHA256_RE.fullmatch(readme_sha256):
        mismatches.append("README.md Current release SHA256 is malformed")

    observations_complete = not errors
    metadata_matches = None if errors else not mismatches
    local = LocalObservations(metadata_matches_identity=metadata_matches)

    return LocalMetadataObservationResult(
        repo_root=str(root),
        readme_path=str(readme_path),
        bundle_path=str(bundle_path),
        repository=normalized_repository,
        local=local,
        expected_download_url=expected_download_url,
        expected_signature_download_url=expected_signature_url,
        expected_manager_json_url=expected_manager_url,
        bundle_version=bundle_version,
        bundle_created_at=bundle_created_at,
        bundle_description=bundle_description,
        bundle_download_url=bundle_download_url,
        bundle_signature_download_url=bundle_signature_download_url,
        readme_version=readme_version,
        readme_tag=readme_tag,
        readme_asset=readme_asset,
        readme_sha256=readme_sha256,
        readme_standalone_sha256=standalone_sha,
        readme_manager_json_url=readme_manager_json_url,
        readme_download_url=readme_download_url,
        observations_complete=observations_complete,
        mismatches=tuple(dict.fromkeys(mismatches)),
        errors=tuple(dict.fromkeys(errors)),
        warnings=tuple(warnings),
    )


@dataclass(frozen=True, slots=True)
class GhBinaryCommandResult:
    args: tuple[str, ...]
    returncode: int
    stdout: bytes
    stderr: str


class GhBinaryRunner(Protocol):
    def run(self, args: Sequence[str]) -> GhBinaryCommandResult:
        ...


@dataclass(frozen=True, slots=True)
class SubprocessGhBinaryRunner:
    """Execute a read-only GitHub API request and retain binary stdout."""

    gh_executable: str = "gh"

    def run(self, args: Sequence[str]) -> GhBinaryCommandResult:
        command = (self.gh_executable, *tuple(args))
        env = os.environ.copy()
        env.update(
            {
                "GH_PROMPT_DISABLED": "1",
                "GH_PAGER": "cat",
                "NO_COLOR": "1",
                "LC_ALL": "C",
            }
        )

        try:
            completed = subprocess.run(
                command,
                check=False,
                capture_output=True,
                env=env,
            )
        except FileNotFoundError as exc:
            return GhBinaryCommandResult(
                args=tuple(command),
                returncode=127,
                stdout=b"",
                stderr=str(exc),
            )
        except OSError as exc:
            return GhBinaryCommandResult(
                args=tuple(command),
                returncode=126,
                stdout=b"",
                stderr=str(exc),
            )

        return GhBinaryCommandResult(
            args=tuple(command),
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr.decode("utf-8", errors="replace"),
        )


@dataclass(frozen=True, slots=True)
class RemoteAssetObservationResult:
    repository: str
    attempted: bool
    mpp_asset_id: int | None
    signature_asset_id: int | None
    mpp_size: int | None
    signature_size: int | None
    github: GitHubReleaseObservations
    verification: VerificationObservations
    required_entries: tuple[str, ...]
    missing_entries: tuple[str, ...]
    duplicate_entries: tuple[str, ...]
    observations_complete: bool
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def _github_asset_download_args(
    repository: str,
    asset_id: int,
) -> tuple[str, ...]:
    return (
        "api",
        "--method",
        "GET",
        "--header",
        "Accept: application/octet-stream",
        "--header",
        f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
        f"repos/{repository}/releases/assets/{asset_id}",
    )


def _binary_gh_error(label: str, result: GhBinaryCommandResult) -> str:
    detail = result.stderr.strip() or "no diagnostic output"
    return f"{label} failed with exit {result.returncode}: {detail}"


def observe_remote_assets(
    repository: str,
    identity: ReleaseIdentity,
    release: GitHubReleaseObservationResult,
    *,
    binary_runner: GhBinaryRunner | None = None,
    gpg_runner: GpgRunner | None = None,
    required_entries: Sequence[str] = REQUIRED_MPP_ENTRIES,
) -> RemoteAssetObservationResult:
    """Download expected release assets to a temporary directory and verify them.

    The files are never written to the repository and are deleted when the
    observation returns. Known absence or a partial draft is a complete
    observation with verifier status NOT_RUN. Tool or transport failures are
    incomplete observations with verifier status UNAVAILABLE. Concrete digest,
    ZIP, or signature failures produce verifier status FAIL.
    """

    normalized_repository = repository.strip()
    normalized_required = tuple(required_entries)
    if not _REPOSITORY_SLUG_RE.fullmatch(normalized_repository):
        raise ValueError("repository must use the OWNER/REPO form")
    if not normalized_required:
        raise ValueError("required_entries must not be empty")
    if len(set(normalized_required)) != len(normalized_required):
        raise ValueError("required_entries must not contain duplicates")

    github = release.github
    if not github.present:
        return RemoteAssetObservationResult(
            repository=normalized_repository,
            attempted=False,
            mpp_asset_id=None,
            signature_asset_id=None,
            mpp_size=None,
            signature_size=None,
            github=GitHubReleaseObservations(),
            verification=VerificationObservations(VerifierStatus.NOT_RUN),
            required_entries=normalized_required,
            missing_entries=(),
            duplicate_entries=(),
            observations_complete=release.observations_complete,
            errors=release.errors,
            warnings=release.warnings,
        )

    if len(release.mpp_asset_ids) != 1 or len(release.signature_asset_ids) != 1:
        return RemoteAssetObservationResult(
            repository=normalized_repository,
            attempted=False,
            mpp_asset_id=(release.mpp_asset_ids[0] if len(release.mpp_asset_ids) == 1 else None),
            signature_asset_id=(
                release.signature_asset_ids[0]
                if len(release.signature_asset_ids) == 1
                else None
            ),
            mpp_size=None,
            signature_size=None,
            github=GitHubReleaseObservations(),
            verification=VerificationObservations(VerifierStatus.NOT_RUN),
            required_entries=normalized_required,
            missing_entries=(),
            duplicate_entries=(),
            observations_complete=release.observations_complete,
            errors=release.errors,
            warnings=release.warnings,
        )

    errors: list[str] = list(release.errors)
    warnings: list[str] = list(release.warnings)
    active_binary_runner = binary_runner or SubprocessGhBinaryRunner()
    mpp_asset_id = release.mpp_asset_ids[0]
    signature_asset_id = release.signature_asset_ids[0]

    mpp_download = active_binary_runner.run(
        _github_asset_download_args(normalized_repository, mpp_asset_id)
    )
    signature_download = active_binary_runner.run(
        _github_asset_download_args(normalized_repository, signature_asset_id)
    )

    if mpp_download.returncode != 0:
        errors.append(_binary_gh_error("remote MPP download", mpp_download))
    if signature_download.returncode != 0:
        errors.append(
            _binary_gh_error("remote detached signature download", signature_download)
        )
    if mpp_download.returncode == 0 and not mpp_download.stdout:
        errors.append("remote MPP download returned an empty body")
    if signature_download.returncode == 0 and not signature_download.stdout:
        errors.append("remote detached signature download returned an empty body")

    if errors:
        return RemoteAssetObservationResult(
            repository=normalized_repository,
            attempted=True,
            mpp_asset_id=mpp_asset_id,
            signature_asset_id=signature_asset_id,
            mpp_size=(len(mpp_download.stdout) if mpp_download.returncode == 0 else None),
            signature_size=(
                len(signature_download.stdout)
                if signature_download.returncode == 0
                else None
            ),
            github=GitHubReleaseObservations(),
            verification=VerificationObservations(VerifierStatus.UNAVAILABLE),
            required_entries=normalized_required,
            missing_entries=(),
            duplicate_entries=(),
            observations_complete=False,
            errors=tuple(dict.fromkeys(errors)),
            warnings=tuple(dict.fromkeys(warnings)),
        )

    remote_sha256: str | None = None
    structure_valid: bool | None = None
    signature_valid: bool | None = None
    missing_entries: tuple[str, ...] = ()
    duplicate_entries: tuple[str, ...] = ()
    gpg_complete = True

    with tempfile.TemporaryDirectory(prefix="morphe-releasectl-remote-") as temp_name:
        temp_root = Path(temp_name)
        mpp_path = temp_root / identity.mpp_asset_name
        signature_path = temp_root / identity.signature_asset_name
        mpp_path.write_bytes(mpp_download.stdout)
        signature_path.write_bytes(signature_download.stdout)

        try:
            remote_sha256 = _sha256_file(mpp_path)
        except OSError as exc:
            errors.append(f"downloaded remote MPP could not be hashed: {exc}")

        (
            structure_valid,
            missing_entries,
            duplicate_entries,
        ) = _inspect_mpp_structure(
            mpp_path,
            required_entries=normalized_required,
            errors=errors,
            warnings=warnings,
        )

        active_gpg_runner = gpg_runner or SubprocessGpgRunner()
        gpg_result = active_gpg_runner.run(mpp_path, signature_path)
        signature_valid, gpg_complete = _parse_gpg_status(
            gpg_result,
            expected_signing_identity=identity.signing_identity,
            errors=errors,
            warnings=warnings,
        )

    concrete_failure = any(
        (
            remote_sha256 is not None and remote_sha256 != identity.mpp_sha256,
            structure_valid is False,
            signature_valid is False,
        )
    )
    observations_complete = (
        release.observations_complete and not errors and gpg_complete
    )

    if concrete_failure:
        verifier_status = VerifierStatus.FAIL
    elif observations_complete and all(
        (
            remote_sha256 == identity.mpp_sha256,
            structure_valid is True,
            signature_valid is True,
        )
    ):
        verifier_status = VerifierStatus.PASS
    else:
        verifier_status = VerifierStatus.UNAVAILABLE

    remote_github = GitHubReleaseObservations(
        remote_mpp_sha256=remote_sha256,
        remote_signature_valid=signature_valid,
        remote_mpp_structure_valid=structure_valid,
    )
    return RemoteAssetObservationResult(
        repository=normalized_repository,
        attempted=True,
        mpp_asset_id=mpp_asset_id,
        signature_asset_id=signature_asset_id,
        mpp_size=len(mpp_download.stdout),
        signature_size=len(signature_download.stdout),
        github=remote_github,
        verification=VerificationObservations(verifier_status),
        required_entries=normalized_required,
        missing_entries=missing_entries,
        duplicate_entries=duplicate_entries,
        observations_complete=observations_complete,
        errors=tuple(dict.fromkeys(errors)),
        warnings=tuple(dict.fromkeys(warnings)),
    )


def _merge_local_observations(
    git: LocalObservations,
    artifacts: LocalObservations,
    metadata: LocalObservations,
) -> LocalObservations:
    return LocalObservations(
        main_commit=git.main_commit,
        dev_commit=git.dev_commit,
        dev_relation_to_target=git.dev_relation_to_target,
        tag_commit=git.tag_commit,
        tag_is_annotated=git.tag_is_annotated,
        metadata_matches_identity=metadata.metadata_matches_identity,
        mpp_present=artifacts.mpp_present,
        signature_present=artifacts.signature_present,
        mpp_sha256=artifacts.mpp_sha256,
        signature_valid=artifacts.signature_valid,
        mpp_structure_valid=artifacts.mpp_structure_valid,
    )


def _merge_github_observations(
    release: GitHubReleaseObservations,
    assets: GitHubReleaseObservations,
) -> GitHubReleaseObservations:
    return replace(
        release,
        remote_mpp_sha256=assets.remote_mpp_sha256,
        remote_signature_valid=assets.remote_signature_valid,
        remote_mpp_structure_valid=assets.remote_mpp_structure_valid,
    )


@dataclass(frozen=True, slots=True)
class ReleaseInspectionResult:
    schema_version: int
    repository: str
    remote_name: str
    identity: ReleaseIdentity
    observations: ReleaseObservations
    state_result: ReleaseStateResult
    plan: ReleasePlan
    local_git: LocalGitObservationResult
    remote_git: RemoteGitObservationResult
    github_release: GitHubReleaseObservationResult
    local_artifacts: LocalArtifactObservationResult
    local_metadata: LocalMetadataObservationResult
    remote_assets: RemoteAssetObservationResult
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def inspect_release(
    repo_path: str | Path,
    repository: str,
    identity: ReleaseIdentity,
    *,
    remote_name: str = "origin",
    git_runner: GitRunner | None = None,
    gh_runner: GhRunner | None = None,
    gh_binary_runner: GhBinaryRunner | None = None,
    gpg_runner: GpgRunner | None = None,
) -> ReleaseInspectionResult:
    """Run every Issue #43 observer and classify the normalized state."""

    local_git = observe_local_git(repo_path, identity, runner=git_runner)
    canonical_repo = Path(local_git.repo_root or Path(repo_path).resolve())
    remote_git = observe_remote_git(
        canonical_repo,
        identity,
        remote_name=remote_name,
        runner=git_runner,
    )
    github_release = observe_github_release(
        repository,
        identity,
        runner=gh_runner,
    )
    local_artifacts = observe_local_artifacts(
        canonical_repo,
        identity,
        gpg_runner=gpg_runner,
    )
    local_metadata = observe_local_metadata(
        canonical_repo,
        repository,
        identity,
    )
    remote_assets = observe_remote_assets(
        repository,
        identity,
        github_release,
        binary_runner=gh_binary_runner,
        gpg_runner=gpg_runner,
    )

    source_results = (
        local_git,
        remote_git,
        github_release,
        local_artifacts,
        local_metadata,
        remote_assets,
    )
    observations_complete = all(
        (
            result.safety.observations_complete
            if isinstance(result, LocalGitObservationResult)
            else getattr(result, "observations_complete", False)
        )
        for result in source_results
    )

    local = _merge_local_observations(
        local_git.local,
        local_artifacts.local,
        local_metadata.local,
    )
    github = _merge_github_observations(
        github_release.github,
        remote_assets.github,
    )
    safety = replace(
        local_git.safety,
        observations_complete=observations_complete,
    )
    observations = ReleaseObservations(
        safety=safety,
        local=local,
        remote=remote_git.remote,
        github=github,
        verification=remote_assets.verification,
    )
    state_result = classify_release_state(identity, observations)
    plan = generate_release_plan(identity, observations)

    errors: list[str] = []
    warnings: list[str] = []
    for result in source_results:
        errors.extend(getattr(result, "errors", ()))
        warnings.extend(getattr(result, "warnings", ()))
    warnings.extend(local_metadata.mismatches)

    return ReleaseInspectionResult(
        schema_version=1,
        repository=repository,
        remote_name=remote_name,
        identity=identity,
        observations=observations,
        state_result=state_result,
        plan=plan,
        local_git=local_git,
        remote_git=remote_git,
        github_release=github_release,
        local_artifacts=local_artifacts,
        local_metadata=local_metadata,
        remote_assets=remote_assets,
        errors=tuple(dict.fromkeys(errors)),
        warnings=tuple(dict.fromkeys(warnings)),
    )


def _yes_no(value: bool) -> str:
    return "YES" if value else "NO"


def _result_token(
    command: str,
    inspection: ReleaseInspectionResult,
) -> str:
    prefix = f"MORPHE_RELEASE_STATE_{command.upper()}"
    if inspection.state_result.state is ReleaseState.INCONSISTENT_ABORT:
        return f"{prefix}_CONFLICT"
    if not inspection.state_result.observations_complete:
        return f"{prefix}_OBSERVATION_FAILED"
    if (
        command == "verify"
        and inspection.state_result.state is not ReleaseState.PUBLISHED_AND_VERIFIED
    ):
        return f"{prefix}_NOT_VERIFIED"
    return f"{prefix}_OK"


def inspection_payload(
    command: str,
    inspection: ReleaseInspectionResult,
) -> dict[str, Any]:
    payload = inspection.as_dict()
    payload["command"] = command
    payload["state"] = inspection.state_result.state.value
    payload["next_action"] = inspection.state_result.next_action.value
    payload["safe_to_mutate"] = inspection.state_result.safe_to_mutate
    payload["observations_complete"] = (
        inspection.state_result.observations_complete
    )
    payload["result"] = _result_token(command, inspection)
    return payload


def render_inspection_text(
    command: str,
    inspection: ReleaseInspectionResult,
) -> str:
    state = inspection.state_result
    observations = inspection.observations
    lines = [
        f"SCHEMA_VERSION={inspection.schema_version}",
        f"COMMAND={command}",
        f"VERSION={inspection.identity.version}",
        f"TAG={inspection.identity.tag}",
        f"RELEASE_COMMIT={inspection.identity.release_commit}",
        f"MPP_ASSET={inspection.identity.mpp_asset_name}",
        f"EXPECTED_MPP_SHA256={inspection.identity.mpp_sha256}",
        f"STATE={state.state.value}",
        f"NEXT_ACTION={state.next_action.value}",
        f"SAFE_TO_MUTATE={_yes_no(state.safe_to_mutate)}",
        f"OBSERVATIONS_COMPLETE={_yes_no(state.observations_complete)}",
        f"CURRENT_BRANCH={inspection.local_git.current_branch or 'DETACHED'}",
        f"LOCAL_MAIN={observations.local.main_commit or 'ABSENT'}",
        f"LOCAL_DEV={observations.local.dev_commit or 'ABSENT'}",
        f"LOCAL_TAG={observations.local.tag_commit or 'ABSENT'}",
        f"REMOTE_MAIN={observations.remote.main_commit or 'ABSENT'}",
        f"REMOTE_DEV={observations.remote.dev_commit or 'ABSENT'}",
        f"REMOTE_TAG={observations.remote.tag_commit or 'ABSENT'}",
        f"GITHUB_RELEASE_PRESENT={_yes_no(observations.github.present)}",
        f"GITHUB_RELEASE_DRAFT={observations.github.is_draft}",
        f"GITHUB_MPP_ASSET_COUNT={observations.github.mpp_asset_count}",
        f"GITHUB_SIGNATURE_ASSET_COUNT={observations.github.signature_asset_count}",
        f"REMOTE_MPP_SHA256={observations.github.remote_mpp_sha256 or 'NOT_VERIFIED'}",
        f"REMOTE_SIGNATURE_VALID={observations.github.remote_signature_valid}",
        f"REMOTE_MPP_STRUCTURE_VALID={observations.github.remote_mpp_structure_valid}",
        f"FULL_VERIFIER_STATUS={observations.verification.full_verifier_status.value}",
        f"PLAN_DISPOSITION={inspection.plan.disposition.value}",
        f"PLAN_EXECUTABLE={_yes_no(inspection.plan.executable)}",
        f"PLAN_HASH={inspection.plan.plan_hash}",
        f"ERROR_COUNT={len(inspection.errors)}",
        f"WARNING_COUNT={len(inspection.warnings)}",
    ]
    lines.extend(f"REASON={item}" for item in state.reasons)
    lines.extend(f"ERROR={item}" for item in inspection.errors)
    lines.extend(f"WARNING={item}" for item in inspection.warnings)
    if command == "plan":
        for operation in inspection.plan.operations:
            lines.append(
                "PLAN_OPERATION="
                f"{operation.sequence}:{operation.operation_type.value}:"
                f"MUTATES={_yes_no(operation.mutates_state)}"
            )
    lines.append(f"RESULT={_result_token(command, inspection)}")
    return "\n".join(lines) + "\n"




DEFAULT_SIGNING_IDENTITY = "475CF5EB633BF19031A40E64A56106614B7AD500"
TRANSACTION_SCHEMA_VERSION = 1


@dataclass(frozen=True, slots=True)
class MutationCommandResult:
    args: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class MutationRunner(Protocol):
    def run(
        self,
        repo_path: Path,
        args: Sequence[str],
    ) -> MutationCommandResult:
        ...


@dataclass(frozen=True, slots=True)
class SubprocessMutationRunner:
    """Execute an explicit mutating command without a shell."""

    def run(
        self,
        repo_path: Path,
        args: Sequence[str],
    ) -> MutationCommandResult:
        command = tuple(args)
        env = os.environ.copy()
        env.update(
            {
                "GIT_TERMINAL_PROMPT": "0",
                "GH_PROMPT_DISABLED": "1",
                "GH_PAGER": "cat",
                "NO_COLOR": "1",
                "LC_ALL": "C",
            }
        )
        try:
            completed = subprocess.run(
                command,
                cwd=repo_path,
                check=False,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                env=env,
            )
        except FileNotFoundError as exc:
            return MutationCommandResult(command, 127, "", str(exc))
        except OSError as exc:
            return MutationCommandResult(command, 126, "", str(exc))
        return MutationCommandResult(
            command,
            completed.returncode,
            completed.stdout,
            completed.stderr,
        )


@dataclass(frozen=True, slots=True)
class TransactionEntry:
    schema_version: int
    timestamp: str
    command: str
    phase: str
    status: str
    version: str
    tag: str
    release_commit: str | None
    mpp_sha256: str | None
    details: dict[str, Any]


@dataclass(frozen=True, slots=True)
class WorkflowExecutionResult:
    command: str
    result: str
    state: ReleaseState
    next_action: NextAction
    release_commit: str | None
    mpp_sha256: str | None
    transaction_log: str
    postcheck_result: str
    already_satisfied: bool = False
    error: str | None = None
    next_command: str | None = None

    def as_dict(self) -> dict[str, Any]:
        return _jsonable(self)


def _utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _safe_tag_component(tag: str) -> str:
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,199}", tag):
        raise ValueError("tag contains unsupported characters")
    return tag


def transaction_log_path(repo_root: Path, tag: str) -> Path:
    safe_tag = _safe_tag_component(tag)
    return repo_root / "local-artifacts" / "release-transactions" / f"{safe_tag}.jsonl"


def append_transaction_entry(
    path: Path,
    *,
    command: str,
    phase: str,
    status: str,
    version: str,
    tag: str,
    release_commit: str | None = None,
    mpp_sha256: str | None = None,
    details: dict[str, Any] | None = None,
) -> TransactionEntry:
    entry = TransactionEntry(
        schema_version=TRANSACTION_SCHEMA_VERSION,
        timestamp=_utc_timestamp(),
        command=command,
        phase=phase,
        status=status,
        version=version,
        tag=tag,
        release_commit=release_commit,
        mpp_sha256=mpp_sha256,
        details=details or {},
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(_jsonable(entry), sort_keys=True, separators=(",", ":"))
    with path.open("a", encoding="utf-8") as handle:
        handle.write(encoded + "\n")
        handle.flush()
        os.fsync(handle.fileno())
    return entry


def read_transaction_log(path: Path) -> tuple[TransactionEntry, ...]:
    if not path.exists():
        return ()
    entries: list[TransactionEntry] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        try:
            payload = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"transaction log line {line_number} is invalid JSON: {exc}") from exc
        if not isinstance(payload, dict):
            raise ValueError(f"transaction log line {line_number} must be a JSON object")
        entries.append(
            TransactionEntry(
                schema_version=int(payload["schema_version"]),
                timestamp=str(payload["timestamp"]),
                command=str(payload["command"]),
                phase=str(payload["phase"]),
                status=str(payload["status"]),
                version=str(payload["version"]),
                tag=str(payload["tag"]),
                release_commit=payload.get("release_commit"),
                mpp_sha256=payload.get("mpp_sha256"),
                details=dict(payload.get("details") or {}),
            )
        )
    return tuple(entries)


def _mutation_error(label: str, result: MutationCommandResult) -> RuntimeError:
    detail = result.stderr.strip() or result.stdout.strip() or "no diagnostic output"
    return RuntimeError(f"{label} failed with exit {result.returncode}: {detail}")


def _run_mutation(
    runner: MutationRunner,
    repo_root: Path,
    args: Sequence[str],
    *,
    label: str,
) -> MutationCommandResult:
    result = runner.run(repo_root, args)
    if result.returncode != 0:
        raise _mutation_error(label, result)
    return result


def _resolve_mutation_repo_root(
    repo_path: str | Path,
    runner: MutationRunner,
) -> Path:
    requested = Path(repo_path).expanduser().resolve()
    result = _run_mutation(
        runner,
        requested,
        ("git", "rev-parse", "--show-toplevel"),
        label="repository root lookup",
    )
    root = Path(result.stdout.strip()).resolve()
    if not root.is_dir():
        raise RuntimeError("resolved repository root is not a directory")
    return root


def _read_exact_ref(
    repo_root: Path,
    ref: str,
    runner: MutationRunner,
) -> str | None:
    result = runner.run(repo_root, ("git", "rev-parse", "--verify", "--quiet", ref))
    if result.returncode == 1:
        return None
    if result.returncode != 0:
        raise _mutation_error(f"read ref {ref}", result)
    value = result.stdout.strip()
    if not _SHA1_RE.fullmatch(value):
        raise RuntimeError(f"read ref {ref} returned an invalid commit id")
    return value


def _read_current_branch(repo_root: Path, runner: MutationRunner) -> str | None:
    result = _run_mutation(
        runner,
        repo_root,
        ("git", "branch", "--show-current"),
        label="current branch lookup",
    )
    return result.stdout.strip() or None


def _require_clean_main(repo_root: Path, runner: MutationRunner) -> None:
    branch = _read_current_branch(repo_root, runner)
    if branch != "main":
        raise RuntimeError(f"expected current branch main, got {branch or 'DETACHED'}")
    status = _run_mutation(
        runner,
        repo_root,
        ("git", "status", "--porcelain=v1", "--untracked-files=all"),
        label="worktree status",
    )
    if status.stdout.strip():
        raise RuntimeError("worktree or index is not clean")


def _canonical_mpp_path(repo_root: Path, version: str) -> Path:
    if not version or "/" in version or "\\" in version or version in {".", ".."}:
        raise ValueError("version contains unsupported path characters")
    return repo_root / "patches" / "build" / "libs" / f"patches-{version}.mpp"


def _read_sha_from_current_readme(repo_root: Path, version: str, tag: str) -> str | None:
    path = repo_root / "README.md"
    if not path.is_file() or path.is_symlink():
        return None
    text = path.read_text(encoding="utf-8")
    start = text.find("## Current release")
    if start < 0:
        return None
    section = text[start:]
    next_heading = section.find("\n## ", 3)
    if next_heading >= 0:
        section = section[:next_heading]
    if version not in section or tag not in section:
        return None
    matches = re.findall(r"(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])", section)
    unique = tuple(dict.fromkeys(matches))
    return unique[0] if len(unique) == 1 else None


def derive_existing_release_identity(
    repo_path: str | Path,
    *,
    version: str,
    tag: str,
    signing_identity: str,
    mpp_sha256: str | None = None,
    runner: MutationRunner | None = None,
) -> ReleaseIdentity:
    active_runner = runner or SubprocessMutationRunner()
    repo_root = _resolve_mutation_repo_root(repo_path, active_runner)
    release_commit = _read_exact_ref(repo_root, f"refs/tags/{tag}^{{commit}}", active_runner)
    if release_commit is None:
        raise RuntimeError(f"local release tag is missing: {tag}")
    mpp_path = _canonical_mpp_path(repo_root, version)
    digest = mpp_sha256.strip().lower() if mpp_sha256 else None
    if digest is None and mpp_path.is_file() and not mpp_path.is_symlink():
        digest = _sha256_file(mpp_path)
    if digest is None:
        digest = _read_sha_from_current_readme(repo_root, version, tag)
    if digest is None or not _SHA256_RE.fullmatch(digest):
        raise RuntimeError(
            "could not derive the release MPP SHA256 from the canonical artifact or current README"
        )
    return ReleaseIdentity(
        version=version,
        tag=tag,
        release_commit=release_commit,
        mpp_asset_name=f"patches-{version}.mpp",
        signature_asset_name=f"patches-{version}.mpp.asc",
        mpp_sha256=digest,
        signing_identity=signing_identity.replace(" ", "").upper(),
    )


def _render_workflow_text(result: WorkflowExecutionResult) -> str:
    lines = [
        f"COMMAND={result.command}",
        f"STATE={result.state.value}",
        f"NEXT_ACTION={result.next_action.value}",
        f"RELEASE_COMMIT={result.release_commit or 'UNKNOWN'}",
        f"MPP_SHA256={result.mpp_sha256 or 'UNKNOWN'}",
        f"TRANSACTION_LOG={result.transaction_log}",
        f"INNER_RESULT={result.result}",
        f"POSTCHECK_RESULT={result.postcheck_result}",
        f"FINAL_STATE={result.state.value}",
        f"ALREADY_SATISFIED={_yes_no(result.already_satisfied)}",
    ]
    if result.next_command:
        lines.append(f"NEXT_COMMAND={result.next_command}")
    if result.error:
        lines.append(f"ERROR={result.error}")
    lines.append(f"RESULT={result.result}")
    return "\n".join(lines) + "\n"


def _workflow_failure(
    *,
    command: str,
    result_token: str,
    transaction_log: Path,
    error: str,
    state: ReleaseState = ReleaseState.INCONSISTENT_ABORT,
    release_commit: str | None = None,
    mpp_sha256: str | None = None,
) -> WorkflowExecutionResult:
    return WorkflowExecutionResult(
        command=command,
        result=result_token,
        state=state,
        next_action=NextAction.MANUAL_DIAGNOSIS,
        release_commit=release_commit,
        mpp_sha256=mpp_sha256,
        transaction_log=str(transaction_log),
        postcheck_result="FAIL",
        error=error,
    )


def _probe_remote_tag(
    repo_root: Path,
    remote_name: str,
    tag: str,
    runner: MutationRunner,
) -> bool:
    result = runner.run(
        repo_root,
        ("git", "ls-remote", "--exit-code", "--tags", remote_name, f"refs/tags/{tag}"),
    )
    if result.returncode == 0:
        return True
    if result.returncode == 2:
        return False
    raise _mutation_error("remote tag probe", result)


def _probe_github_release(
    repo_root: Path,
    repository: str,
    tag: str,
    runner: MutationRunner,
) -> bool:
    result = runner.run(
        repo_root,
        (
            "gh", "api", "--method", "GET",
            "--header", "Accept: application/vnd.github+json",
            "--header", f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
            f"repos/{repository}/releases/tags/{tag}",
        ),
    )
    if result.returncode == 0:
        return True
    diagnostic = (result.stderr + "\n" + result.stdout).casefold()
    if result.returncode == 1 and ("404" in diagnostic or "not found" in diagnostic):
        return False
    raise _mutation_error("GitHub release probe", result)


def _verify_signing_identity(
    repo_root: Path,
    mpp_path: Path,
    signature_path: Path,
    signing_identity: str,
    runner: MutationRunner,
) -> None:
    result = _run_mutation(
        runner,
        repo_root,
        (
            "gpg", "--batch", "--status-fd", "1", "--verify",
            str(signature_path), str(mpp_path),
        ),
        label="detached signature verification",
    )
    fingerprints = []
    for line in result.stdout.splitlines():
        if line.startswith("[GNUPG:] VALIDSIG "):
            parts = line.split()
            if len(parts) >= 3:
                fingerprints.append(parts[2].upper())
    expected = signing_identity.replace(" ", "").upper()
    if fingerprints != [expected]:
        raise RuntimeError(
            "detached signature does not have exactly one expected signing fingerprint"
        )


def _align_local_dev(
    repo_root: Path,
    identity: ReleaseIdentity,
    runner: MutationRunner,
) -> None:
    main_commit = _read_exact_ref(repo_root, "refs/heads/main", runner)
    dev_commit = _read_exact_ref(repo_root, "refs/heads/dev", runner)
    if main_commit != identity.release_commit:
        raise RuntimeError("local main does not point exactly at the release commit")
    if dev_commit == identity.release_commit:
        return
    zero = "0" * 40
    old = dev_commit or zero
    payload = (
        "start\n"
        f"update refs/heads/dev {identity.release_commit} {old}\n"
        "prepare\ncommit\n"
    )
    command = ("git", "update-ref", "--stdin")
    env = os.environ.copy()
    env.update({"GIT_TERMINAL_PROMPT": "0", "LC_ALL": "C"})
    try:
        completed = subprocess.run(
            command,
            cwd=repo_root,
            input=payload,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            env=env,
        )
    except OSError as exc:
        raise RuntimeError(f"local dev ref transaction failed: {exc}") from exc
    if completed.returncode != 0:
        raise RuntimeError(
            "local dev ref transaction failed: "
            + (completed.stderr.strip() or completed.stdout.strip())
        )


def _atomic_push_release_refs(
    repo_root: Path,
    identity: ReleaseIdentity,
    remote_name: str,
    runner: MutationRunner,
) -> None:
    main_commit = _read_exact_ref(repo_root, "refs/heads/main", runner)
    dev_commit = _read_exact_ref(repo_root, "refs/heads/dev", runner)
    tag_commit = _read_exact_ref(repo_root, f"refs/tags/{identity.tag}^{{commit}}", runner)
    if (main_commit, dev_commit, tag_commit) != (
        identity.release_commit,
        identity.release_commit,
        identity.release_commit,
    ):
        raise RuntimeError("local main, dev and annotated tag must all identify the release commit")
    _run_mutation(
        runner,
        repo_root,
        (
            "git", "push", "--atomic", remote_name,
            "refs/heads/main:refs/heads/main",
            "refs/heads/dev:refs/heads/dev",
            f"refs/tags/{identity.tag}:refs/tags/{identity.tag}",
        ),
        label="atomic release ref push",
    )


def _create_draft_release(
    repo_root: Path,
    repository: str,
    identity: ReleaseIdentity,
    notes_file: Path,
    runner: MutationRunner,
) -> None:
    _run_mutation(
        runner,
        repo_root,
        (
            "gh", "api", "--method", "POST",
            "--header", "Accept: application/vnd.github+json",
            "--header", f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
            f"repos/{repository}/releases",
            "-f", f"tag_name={identity.tag}",
            "-f", f"target_commitish={identity.release_commit}",
            "-f", f"name=Morphe patch bundle {identity.version}",
            "-F", "draft=true",
            "-F", "prerelease=false",
            "-F", f"body=@{notes_file}",
        ),
        label="draft GitHub release creation",
    )


def _upload_release_asset(
    repo_root: Path,
    identity: ReleaseIdentity,
    asset_path: Path,
    runner: MutationRunner,
) -> None:
    _run_mutation(
        runner,
        repo_root,
        ("gh", "release", "upload", identity.tag, str(asset_path)),
        label=f"upload release asset {asset_path.name}",
    )


def _publish_draft_release(
    repo_root: Path,
    repository: str,
    release_id: int,
    runner: MutationRunner,
) -> None:
    _run_mutation(
        runner,
        repo_root,
        (
            "gh", "api", "--method", "PATCH",
            "--header", "Accept: application/vnd.github+json",
            "--header", f"X-GitHub-Api-Version: {GITHUB_API_VERSION}",
            f"repos/{repository}/releases/{release_id}",
            "-F", "draft=false",
        ),
        label="draft GitHub release publication",
    )


def _compose_and_validate_release_notes(
    repo_root: Path,
    identity: ReleaseIdentity,
    release_notes_file: Path,
    transaction_root: Path,
    runner: MutationRunner,
) -> Path:
    if not release_notes_file.is_file() or release_notes_file.is_symlink():
        raise RuntimeError(f"release notes file is unavailable: {release_notes_file}")
    transaction_root.parent.mkdir(parents=True, exist_ok=True)
    notes_path = transaction_root.with_suffix(".release-notes.md")
    source = release_notes_file.read_text(encoding="utf-8")
    notes_path.write_text(
        source.rstrip()
        + "\n\n### Validation\n\n"
        + "- Final local release gate passed before publish.\n"
        + "- README SHA is aligned to the published MPP.\n"
        + f"- Release tag: `{identity.tag}`.\n"
        + f"- Release asset: `{identity.mpp_asset_name}`.\n"
        + f"- Signature asset: `{identity.signature_asset_name}`.\n"
        + f"- MPP SHA256: `{identity.mpp_sha256}`.\n",
        encoding="utf-8",
    )
    _run_mutation(
        runner,
        repo_root,
        (
            "python3", "scripts/validate-release-notes.py",
            "--notes-file", str(notes_path),
            "--version", identity.version,
            "--tag", identity.tag,
            "--asset", identity.mpp_asset_name,
            "--sha256", identity.mpp_sha256,
            "--require-sha",
        ),
        label="release notes validation",
    )
    return notes_path


def finalize_release_workflow(
    repo_path: str | Path,
    repository: str,
    *,
    version: str,
    tag: str,
    changelog: Sequence[str],
    name: str | None = None,
    remote_name: str = "origin",
    signing_identity: str = DEFAULT_SIGNING_IDENTITY,
    runner: MutationRunner | None = None,
    inspect_fn=inspect_release,
) -> WorkflowExecutionResult:
    active_runner = runner or SubprocessMutationRunner()
    repo_root = _resolve_mutation_repo_root(repo_path, active_runner)
    log_path = transaction_log_path(repo_root, tag)
    try:
        if not changelog:
            raise RuntimeError("at least one --changelog value is required")
        _canonical_mpp_path(repo_root, version)
        existing_tag = _read_exact_ref(
            repo_root, f"refs/tags/{tag}^{{commit}}", active_runner
        )
        if existing_tag is None:
            _require_clean_main(repo_root, active_runner)
    except (OSError, RuntimeError, ValueError) as exc:
        return _workflow_failure(
            command="finalize",
            result_token="MORPHE_RELEASE_FINALIZE_LOCAL_FAIL",
            transaction_log=log_path,
            error=str(exc),
        )

    append_transaction_entry(
        log_path, command="finalize", phase="START", status="OBSERVED",
        version=version, tag=tag,
    )
    try:
        existing_tag = _read_exact_ref(repo_root, f"refs/tags/{tag}^{{commit}}", active_runner)
        if existing_tag is not None:
            identity = derive_existing_release_identity(
                repo_root,
                version=version,
                tag=tag,
                signing_identity=signing_identity,
                runner=active_runner,
            )
            inspection = inspect_fn(repo_root, repository, identity, remote_name=remote_name)
            if not inspection.state_result.observations_complete:
                raise RuntimeError("existing finalized release could not be observed completely")
            if inspection.state_result.state is ReleaseState.INCONSISTENT_ABORT:
                raise RuntimeError("existing finalized release conflicts with the requested identity")
            if inspection.observations.local.main_commit == identity.release_commit:
                relation = inspection.observations.local.dev_relation_to_target
                if relation in {RefRelation.ABSENT, RefRelation.ANCESTOR}:
                    append_transaction_entry(
                        log_path, command="finalize", phase="ALIGN_LOCAL_DEV",
                        status="STARTED", version=version, tag=tag,
                        release_commit=identity.release_commit,
                        mpp_sha256=identity.mpp_sha256,
                    )
                    _align_local_dev(repo_root, identity, active_runner)
                    append_transaction_entry(
                        log_path, command="finalize", phase="ALIGN_LOCAL_DEV",
                        status="COMPLETED", version=version, tag=tag,
                        release_commit=identity.release_commit,
                        mpp_sha256=identity.mpp_sha256,
                    )
            post = inspect_fn(repo_root, repository, identity, remote_name=remote_name)
            if not post.state_result.observations_complete:
                raise RuntimeError("finalize retry postcheck is incomplete")
            append_transaction_entry(
                log_path, command="finalize", phase="COMPLETE", status="ALREADY_SATISFIED",
                version=version, tag=tag, release_commit=identity.release_commit,
                mpp_sha256=identity.mpp_sha256,
                details={"state": post.state_result.state.value},
            )
            return WorkflowExecutionResult(
                command="finalize",
                result="MORPHE_RELEASE_FINALIZE_LOCAL_ALREADY_FINALIZED_OK",
                state=post.state_result.state,
                next_action=post.state_result.next_action,
                release_commit=identity.release_commit,
                mpp_sha256=identity.mpp_sha256,
                transaction_log=str(log_path),
                postcheck_result="OK",
                already_satisfied=True,
                next_command=(
                    f"scripts/release-publish-existing.sh --version {version} "
                    f"--tag {tag} --release-notes-file PATH"
                ),
            )

        _require_clean_main(repo_root, active_runner)
        if _probe_remote_tag(repo_root, remote_name, tag, active_runner):
            raise RuntimeError("remote tag already exists while the local tag is absent")
        if _probe_github_release(repo_root, repository, tag, active_runner):
            raise RuntimeError("GitHub release already exists while the local tag is absent")

        phase_commands = [
            (
                "PREPARE_METADATA",
                (
                    "python3", "scripts/prepare-release.py", "--version", version,
                    "--tag", tag,
                    *sum((("--changelog", item) for item in changelog), ()),
                ),
            ),
            ("BUILD_FINAL_MPP", ("./gradlew", ":patches:buildAndroid", "--no-daemon")),
        ]
        for phase, command_args in phase_commands:
            append_transaction_entry(
                log_path, command="finalize", phase=phase, status="STARTED",
                version=version, tag=tag,
            )
            _run_mutation(active_runner, repo_root, command_args, label=phase.lower())
            append_transaction_entry(
                log_path, command="finalize", phase=phase, status="COMPLETED",
                version=version, tag=tag,
            )

        mpp_path = _canonical_mpp_path(repo_root, version)
        signature_path = Path(str(mpp_path) + ".asc")
        if not mpp_path.is_file() or mpp_path.is_symlink():
            raise RuntimeError(f"canonical MPP is unavailable: {mpp_path}")
        mpp_sha = _sha256_file(mpp_path)
        if signature_path.exists():
            if signature_path.is_symlink() or not signature_path.is_file():
                raise RuntimeError("canonical detached signature path is not a regular file")
            signature_path.unlink()
        append_transaction_entry(
            log_path, command="finalize", phase="SIGN_ARTIFACT", status="STARTED",
            version=version, tag=tag, mpp_sha256=mpp_sha,
        )
        _run_mutation(
            active_runner,
            repo_root,
            (
                "gpg", "--batch", "--yes", "--armor", "--detach-sign",
                "--output", str(signature_path), str(mpp_path),
            ),
            label="MPP signing",
        )
        _verify_signing_identity(
            repo_root, mpp_path, signature_path, signing_identity, active_runner
        )
        append_transaction_entry(
            log_path, command="finalize", phase="SIGN_ARTIFACT", status="COMPLETED",
            version=version, tag=tag, mpp_sha256=mpp_sha,
        )

        _run_mutation(
            active_runner,
            repo_root,
            (
                "python3", "scripts/update-readme-current-release-sha.py",
                "--version", version, "--tag", tag,
                "--asset", f"patches-{version}.mpp", "--sha256", mpp_sha,
            ),
            label="README SHA update",
        )
        candidate_name = name or f"release-{version}-local-finalize"
        candidate = _run_mutation(
            active_runner,
            repo_root,
            (
                "tools/build-boost-candidate.sh", "--mpp", str(mpp_path),
                "--name", candidate_name, "--no-verify-with-sdk",
            ),
            label="Boost candidate build",
        )
        directory_match = re.findall(r"^DIR:[ \t]*(.+)$", candidate.stdout, re.MULTILINE)
        apk_match = re.findall(r"^APK:[ \t]*(.+)$", candidate.stdout, re.MULTILINE)
        if not directory_match or not apk_match:
            raise RuntimeError("Boost candidate output did not expose DIR and APK")
        candidate_dir = Path(directory_match[-1].strip())
        candidate_apk = Path(apk_match[-1].strip())
        if not candidate_dir.is_absolute():
            candidate_dir = repo_root / candidate_dir
        if not candidate_apk.is_absolute():
            candidate_apk = repo_root / candidate_apk
        static_log = candidate_dir / "static-gate.log"
        patch_log = candidate_dir / "morphe-patch.log"
        if not candidate_apk.is_file():
            raise RuntimeError("Boost candidate APK is missing")
        if "RESULT: PASS" not in static_log.read_text(encoding="utf-8"):
            raise RuntimeError("Boost candidate static gate did not pass")
        patch_text = patch_log.read_text(encoding="utf-8")
        for required in ("Applied: Modify login WebView", "Applied: Spoof client"):
            if required not in patch_text:
                raise RuntimeError(f"Boost candidate baseline patch missing: {required}")

        if _probe_remote_tag(repo_root, remote_name, tag, active_runner):
            raise RuntimeError("remote tag appeared before local release commit")
        if _probe_github_release(repo_root, repository, tag, active_runner):
            raise RuntimeError("GitHub release appeared before local release commit")

        status = _run_mutation(
            active_runner, repo_root,
            ("git", "status", "--porcelain=v1", "--untracked-files=all"),
            label="release metadata status",
        )
        if not status.stdout.strip():
            raise RuntimeError("release metadata preparation produced no tracked changes")
        _run_mutation(active_runner, repo_root, ("git", "add", "-A"), label="release metadata staging")
        _run_mutation(
            active_runner, repo_root,
            ("git", "commit", "-m", f"Release Morphe patch bundle {version} [skip ci]"),
            label="release metadata commit",
        )
        release_commit = _read_exact_ref(repo_root, "HEAD", active_runner)
        assert release_commit is not None
        _run_mutation(
            active_runner, repo_root,
            ("git", "tag", "-a", tag, "-m", f"Morphe patch bundle {version}", release_commit),
            label="annotated release tag creation",
        )
        identity = ReleaseIdentity(
            version=version,
            tag=tag,
            release_commit=release_commit,
            mpp_asset_name=f"patches-{version}.mpp",
            signature_asset_name=f"patches-{version}.mpp.asc",
            mpp_sha256=mpp_sha,
            signing_identity=signing_identity.replace(" ", "").upper(),
        )
        append_transaction_entry(
            log_path, command="finalize", phase="ALIGN_LOCAL_DEV", status="STARTED",
            version=version, tag=tag, release_commit=release_commit, mpp_sha256=mpp_sha,
        )
        _align_local_dev(repo_root, identity, active_runner)
        append_transaction_entry(
            log_path, command="finalize", phase="ALIGN_LOCAL_DEV", status="COMPLETED",
            version=version, tag=tag, release_commit=release_commit, mpp_sha256=mpp_sha,
        )
        post = inspect_fn(repo_root, repository, identity, remote_name=remote_name)
        if not post.state_result.observations_complete:
            raise RuntimeError("finalize postcheck is incomplete")
        if post.state_result.state not in {
            ReleaseState.READY_TO_PUBLISH,
            ReleaseState.PARTIALLY_PUBLISHED,
            ReleaseState.PUBLISHED_NOT_VERIFIED,
            ReleaseState.PUBLISHED_AND_VERIFIED,
        }:
            raise RuntimeError(
                f"finalize postcheck produced unexpected state {post.state_result.state.value}"
            )
        append_transaction_entry(
            log_path, command="finalize", phase="COMPLETE", status="COMPLETED",
            version=version, tag=tag, release_commit=release_commit,
            mpp_sha256=mpp_sha, details={"state": post.state_result.state.value},
        )
        return WorkflowExecutionResult(
            command="finalize",
            result="MORPHE_RELEASE_FINALIZE_LOCAL_OK",
            state=post.state_result.state,
            next_action=post.state_result.next_action,
            release_commit=release_commit,
            mpp_sha256=mpp_sha,
            transaction_log=str(log_path),
            postcheck_result="OK",
            next_command=(
                f"scripts/release-publish-existing.sh --version {version} "
                f"--tag {tag} --release-notes-file PATH"
            ),
        )
    except (OSError, RuntimeError, ValueError) as exc:
        append_transaction_entry(
            log_path, command="finalize", phase="ABORT", status="FAILED",
            version=version, tag=tag, details={"error": str(exc)},
        )
        return _workflow_failure(
            command="finalize",
            result_token="MORPHE_RELEASE_FINALIZE_LOCAL_FAIL",
            transaction_log=log_path,
            error=str(exc),
        )


def publish_release_workflow(
    repo_path: str | Path,
    repository: str,
    *,
    version: str,
    tag: str,
    release_notes_file: str | Path,
    remote_name: str = "origin",
    signing_identity: str = DEFAULT_SIGNING_IDENTITY,
    mpp_sha256: str | None = None,
    command_name: str = "publish",
    runner: MutationRunner | None = None,
    inspect_fn=inspect_release,
) -> WorkflowExecutionResult:
    active_runner = runner or SubprocessMutationRunner()
    repo_root = _resolve_mutation_repo_root(repo_path, active_runner)
    log_path = transaction_log_path(repo_root, tag)
    try:
        notes_source = Path(release_notes_file).expanduser().resolve()
        if not notes_source.is_file() or notes_source.is_symlink():
            raise RuntimeError(f"release notes file is unavailable: {notes_source}")
        identity = derive_existing_release_identity(
            repo_root,
            version=version,
            tag=tag,
            signing_identity=signing_identity,
            mpp_sha256=mpp_sha256,
            runner=active_runner,
        )
        _require_clean_main(repo_root, active_runner)
        append_transaction_entry(
            log_path, command=command_name, phase="START", status="OBSERVED",
            version=version, tag=tag, release_commit=identity.release_commit,
            mpp_sha256=identity.mpp_sha256,
        )
        notes_path = _compose_and_validate_release_notes(
            repo_root,
            identity,
            notes_source,
            log_path,
            active_runner,
        )
        append_transaction_entry(
            log_path, command=command_name, phase="VALIDATE_RELEASE_NOTES",
            status="COMPLETED", version=version, tag=tag,
            release_commit=identity.release_commit, mpp_sha256=identity.mpp_sha256,
        )
        initial = inspect_fn(repo_root, repository, identity, remote_name=remote_name)
        if initial.state_result.state is ReleaseState.PUBLISHED_AND_VERIFIED:
            token = "MORPHE_RELEASE_ALREADY_PUBLISHED_VERIFIED_OK"
            append_transaction_entry(
                log_path, command=command_name, phase="COMPLETE",
                status="ALREADY_SATISFIED", version=version, tag=tag,
                release_commit=identity.release_commit, mpp_sha256=identity.mpp_sha256,
            )
            return WorkflowExecutionResult(
                command=command_name,
                result=token,
                state=ReleaseState.PUBLISHED_AND_VERIFIED,
                next_action=NextAction.NONE,
                release_commit=identity.release_commit,
                mpp_sha256=identity.mpp_sha256,
                transaction_log=str(log_path),
                postcheck_result="OK",
                already_satisfied=True,
            )

        mpp_path = _canonical_mpp_path(repo_root, version)
        signature_path = Path(str(mpp_path) + ".asc")
        for _iteration in range(16):
            inspection = inspect_fn(repo_root, repository, identity, remote_name=remote_name)
            state = inspection.state_result.state
            if state is ReleaseState.INCONSISTENT_ABORT:
                raise RuntimeError("release state is inconsistent: " + "; ".join(inspection.state_result.reasons))
            if not inspection.state_result.observations_complete:
                raise RuntimeError("one or more required release observations are incomplete")
            if state is ReleaseState.PUBLISHED_AND_VERIFIED:
                append_transaction_entry(
                    log_path, command=command_name, phase="COMPLETE", status="COMPLETED",
                    version=version, tag=tag, release_commit=identity.release_commit,
                    mpp_sha256=identity.mpp_sha256,
                )
                return WorkflowExecutionResult(
                    command=command_name,
                    result="MORPHE_RELEASE_PUBLISH_EXISTING_OK",
                    state=state,
                    next_action=NextAction.NONE,
                    release_commit=identity.release_commit,
                    mpp_sha256=identity.mpp_sha256,
                    transaction_log=str(log_path),
                    postcheck_result="OK",
                )
            if state is ReleaseState.NOT_FINALIZED:
                raise RuntimeError("release is not locally finalized; run finalize first")
            plan = inspection.plan
            if plan.disposition in {PlanDisposition.CONFLICT_ABORT, PlanDisposition.OBSERVATION_FAILED}:
                raise RuntimeError("release plan is not executable")
            if not plan.operations:
                raise RuntimeError("release plan has no operation but the release is not verified")
            operation = plan.operations[0]
            phase = operation.operation_type.value
            append_transaction_entry(
                log_path, command=command_name, phase=phase, status="STARTED",
                version=version, tag=tag, release_commit=identity.release_commit,
                mpp_sha256=identity.mpp_sha256,
                details={"plan_hash": plan.plan_hash},
            )
            if operation.operation_type is PlanOperationType.FINALIZE_LOCAL:
                raise RuntimeError("publish cannot perform local finalization")
            if operation.operation_type is PlanOperationType.ALIGN_LOCAL_DEV:
                _align_local_dev(repo_root, identity, active_runner)
            elif operation.operation_type is PlanOperationType.PUSH_RELEASE_REFS_ATOMIC:
                _atomic_push_release_refs(repo_root, identity, remote_name, active_runner)
            elif operation.operation_type is PlanOperationType.CREATE_DRAFT_RELEASE:
                _create_draft_release(
                    repo_root, repository, identity, notes_path, active_runner
                )
            elif operation.operation_type is PlanOperationType.UPLOAD_MPP_ASSET:
                if not mpp_path.is_file() or mpp_path.is_symlink():
                    raise RuntimeError("canonical MPP is unavailable for upload")
                if _sha256_file(mpp_path) != identity.mpp_sha256:
                    raise RuntimeError("canonical MPP digest changed before upload")
                _upload_release_asset(repo_root, identity, mpp_path, active_runner)
            elif operation.operation_type is PlanOperationType.UPLOAD_SIGNATURE_ASSET:
                if not signature_path.is_file() or signature_path.is_symlink():
                    raise RuntimeError("canonical detached signature is unavailable for upload")
                _verify_signing_identity(
                    repo_root, mpp_path, signature_path, signing_identity, active_runner
                )
                _upload_release_asset(repo_root, identity, signature_path, active_runner)
            elif operation.operation_type is PlanOperationType.VERIFY_DRAFT_ASSETS:
                if inspection.observations.verification.full_verifier_status is not VerifierStatus.PASS:
                    raise RuntimeError("draft release assets are not fully verified")
            elif operation.operation_type is PlanOperationType.PUBLISH_DRAFT_RELEASE:
                release_id = inspection.github_release.release_id
                if release_id is None:
                    raise RuntimeError("draft release id is unavailable")
                if inspection.observations.verification.full_verifier_status is not VerifierStatus.PASS:
                    raise RuntimeError("draft assets must be verified before publication")
                _publish_draft_release(
                    repo_root, repository, release_id, active_runner
                )
            elif operation.operation_type is PlanOperationType.VERIFY_PUBLISHED_RELEASE:
                if state is not ReleaseState.PUBLISHED_AND_VERIFIED:
                    raise RuntimeError("published release did not pass authoritative verification")
            else:
                raise RuntimeError(f"unsupported release operation: {operation.operation_type.value}")
            append_transaction_entry(
                log_path, command=command_name, phase=phase, status="COMPLETED",
                version=version, tag=tag, release_commit=identity.release_commit,
                mpp_sha256=identity.mpp_sha256,
            )
        raise RuntimeError("release workflow exceeded the maximum resume iteration count")
    except (OSError, RuntimeError, ValueError) as exc:
        append_transaction_entry(
            log_path, command=command_name, phase="ABORT", status="FAILED",
            version=version, tag=tag, details={"error": str(exc)},
        )
        return _workflow_failure(
            command=command_name,
            result_token="MORPHE_RELEASE_PUBLISH_EXISTING_FAIL",
            transaction_log=log_path,
            error=str(exc),
        )

def _identity_from_args(args: argparse.Namespace) -> ReleaseIdentity:
    version = args.version.strip()
    return ReleaseIdentity(
        version=version,
        tag=args.tag.strip(),
        release_commit=args.release_commit.strip().lower(),
        mpp_asset_name=f"patches-{version}.mpp",
        signature_asset_name=f"patches-{version}.mpp.asc",
        mpp_sha256=args.mpp_sha256.strip().lower(),
        signing_identity=args.signing_identity.strip().replace(" ", "").upper(),
    )


def _build_cli_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="releasectl.py",
        description="Authoritative Morphe release state and transaction controller",
    )
    inspection_common = argparse.ArgumentParser(add_help=False)
    inspection_common.add_argument("--repo", default=".", help="repository path")
    inspection_common.add_argument(
        "--repository",
        default="brealorg/breal-morphe-patches",
        help="GitHub OWNER/REPO",
    )
    inspection_common.add_argument("--remote", default="origin", help="Git remote name")
    inspection_common.add_argument("--version", required=True)
    inspection_common.add_argument("--tag", required=True)
    inspection_common.add_argument("--release-commit", required=True)
    inspection_common.add_argument("--mpp-sha256", required=True)
    inspection_common.add_argument("--signing-identity", required=True)
    inspection_common.add_argument(
        "--json",
        action="store_true",
        help="emit normalized JSON instead of text",
    )

    mutation_common = argparse.ArgumentParser(add_help=False)
    mutation_common.add_argument("--repo", default=".", help="repository path")
    mutation_common.add_argument(
        "--repository",
        default="brealorg/breal-morphe-patches",
        help="GitHub OWNER/REPO",
    )
    mutation_common.add_argument("--remote", default="origin", help="Git remote name")
    mutation_common.add_argument("--version", required=True)
    mutation_common.add_argument("--tag", required=True)
    mutation_common.add_argument(
        "--signing-identity",
        default=DEFAULT_SIGNING_IDENTITY,
        help="expected detached-signature fingerprint",
    )
    mutation_common.add_argument(
        "--json",
        action="store_true",
        help="emit normalized JSON instead of text",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser(
        "inspect", parents=[inspection_common], help="inspect and classify"
    )
    subparsers.add_parser(
        "plan", parents=[inspection_common], help="inspect and print plan"
    )
    subparsers.add_parser(
        "verify",
        parents=[inspection_common],
        help="require PUBLISHED_AND_VERIFIED",
    )
    finalize = subparsers.add_parser(
        "finalize",
        parents=[mutation_common],
        help="idempotently prepare, build, sign, commit, tag, and align locally",
    )
    finalize.add_argument(
        "--changelog",
        action="append",
        required=True,
        help="Manager-facing current-release description; repeatable",
    )
    finalize.add_argument("--name", help="Boost candidate/runtime suffix")

    for command in ("publish", "resume"):
        publication = subparsers.add_parser(
            command,
            parents=[mutation_common],
            help=(
                "idempotently publish or resume missing release phases"
                if command == "publish"
                else "resume only the release phases still missing"
            ),
        )
        publication.add_argument("--release-notes-file", required=True)
        publication.add_argument(
            "--mpp-sha256",
            help="explicit immutable MPP SHA256 when no canonical local artifact exists",
        )
    return parser


def _exit_code(command: str, inspection: ReleaseInspectionResult) -> int:
    state = inspection.state_result.state
    if state is ReleaseState.INCONSISTENT_ABORT:
        return 3
    if not inspection.state_result.observations_complete:
        return 4
    if command == "verify" and state is not ReleaseState.PUBLISHED_AND_VERIFIED:
        return 5
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = _build_cli_parser()
    args = parser.parse_args(argv)

    if args.command in {"finalize", "publish", "resume"}:
        try:
            if args.command == "finalize":
                workflow = finalize_release_workflow(
                    args.repo,
                    args.repository,
                    version=args.version.strip(),
                    tag=args.tag.strip(),
                    changelog=tuple(args.changelog),
                    name=args.name,
                    remote_name=args.remote,
                    signing_identity=args.signing_identity,
                )
            else:
                workflow = publish_release_workflow(
                    args.repo,
                    args.repository,
                    version=args.version.strip(),
                    tag=args.tag.strip(),
                    release_notes_file=args.release_notes_file,
                    remote_name=args.remote,
                    signing_identity=args.signing_identity,
                    mpp_sha256=args.mpp_sha256,
                    command_name=args.command,
                )
        except ValueError as exc:
            parser.error(str(exc))
            return 2
        if args.json:
            print(json.dumps(workflow.as_dict(), indent=2, sort_keys=True))
        else:
            sys.stdout.write(_render_workflow_text(workflow))
        return 0 if workflow.postcheck_result == "OK" else 3

    try:
        identity = _identity_from_args(args)
        inspection = inspect_release(
            args.repo,
            args.repository,
            identity,
            remote_name=args.remote,
        )
    except ValueError as exc:
        parser.error(str(exc))
        return 2

    if args.json:
        print(
            json.dumps(
                inspection_payload(args.command, inspection),
                indent=2,
                sort_keys=True,
            )
        )
    else:
        sys.stdout.write(render_inspection_text(args.command, inspection))

    return _exit_code(args.command, inspection)


__all__ = [
    "DEFAULT_SIGNING_IDENTITY",
    "TRANSACTION_SCHEMA_VERSION",
    "MutationCommandResult",
    "MutationRunner",
    "SubprocessMutationRunner",
    "TransactionEntry",
    "WorkflowExecutionResult",
    "transaction_log_path",
    "append_transaction_entry",
    "read_transaction_log",
    "derive_existing_release_identity",
    "finalize_release_workflow",
    "publish_release_workflow",
    "GhBinaryCommandResult",
    "GhBinaryRunner",
    "SubprocessGhBinaryRunner",
    "RemoteAssetObservationResult",
    "ReleaseInspectionResult",
    "observe_remote_assets",
    "inspect_release",
    "inspection_payload",
    "render_inspection_text",
    "main",
    "MAX_MANAGER_DESCRIPTION_LENGTH",
    "LocalMetadataObservationResult",
    "observe_local_metadata",
    "REQUIRED_MPP_ENTRIES",
    "GpgCommandResult",
    "GpgRunner",
    "LocalArtifactObservationResult",
    "SubprocessGpgRunner",
    "observe_local_artifacts",
    "GITHUB_API_VERSION",
    "GhCommandResult",
    "GhRunner",
    "GitHubReleaseObservationResult",
    "SubprocessGhRunner",
    "observe_github_release",
    "GitCommandResult",
    "GitRunner",
    "LocalGitObservationResult",
    "RemoteGitObservationResult",
    "SubprocessGitRunner",
    "observe_local_git",
    "observe_remote_git",
    "GitHubReleaseObservations",
    "PlanDisposition",
    "PlanFact",
    "PlanOperation",
    "PlanOperationType",
    "LocalObservations",
    "NextAction",
    "RefRelation",
    "ReleaseIdentity",
    "ReleaseObservations",
    "ReleasePlan",
    "ReleaseState",
    "ReleaseStateResult",
    "RemoteGitObservations",
    "SafetyObservations",
    "VerificationObservations",
    "VerifierStatus",
    "classify_release_state",
    "generate_release_plan",
]


if __name__ == "__main__":
    raise SystemExit(main())
