#!/usr/bin/env python3
"""Release state, deterministic planning, and read-only Git observation.

This Issue #43 checkpoint adds local and remote Git inspection. It contains
no GitHub API, GPG, build, upload, publication, or mutation logic.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, fields, is_dataclass
from enum import Enum
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
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


def _relation_matches_target(
    commit: str | None,
    relation: RefRelation,
    target: str,
) -> bool:
    return commit == target or relation is RefRelation.EQUAL


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
            _relation_matches_target(
                remote.main_commit,
                remote.main_relation_to_target,
                identity.release_commit,
            ),
            _relation_matches_target(
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
            _relation_matches_target(
                remote.main_commit,
                remote.main_relation_to_target,
                identity.release_commit,
            ),
            _relation_matches_target(
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

    if local.metadata_matches_identity is False:
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
    if local_finalized_evidence and local.dev_relation_to_target in {
        RefRelation.AHEAD,
        RefRelation.DIVERGENT,
    }:
        conflicts.append("local dev is ahead of or divergent from the release target")

    if remote.tag_commit is not None and remote.tag_commit != identity.release_commit:
        conflicts.append("remote tag points to a different commit")

    if remote.main_relation_to_target in {RefRelation.AHEAD, RefRelation.DIVERGENT}:
        conflicts.append("remote main is ahead of or divergent from the release target")

    if remote.dev_relation_to_target in {RefRelation.AHEAD, RefRelation.DIVERGENT}:
        conflicts.append("remote dev is ahead of or divergent from the release target")

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
            _relation_matches_target(
                remote.main_commit,
                remote.main_relation_to_target,
                identity.release_commit,
            ),
            _relation_matches_target(
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


__all__ = [
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
