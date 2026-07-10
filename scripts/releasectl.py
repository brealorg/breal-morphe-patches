#!/usr/bin/env python3
"""Pure release-state model and classifier for Morphe Issue #43.

This checkpoint intentionally contains no Git, GitHub, filesystem, network,
GPG, build, upload, or mutation logic. Observation collection and CLI wiring
will be added separately after the pure state model is accepted.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import Enum
import re
from typing import Any


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


__all__ = [
    "GitHubReleaseObservations",
    "LocalObservations",
    "NextAction",
    "RefRelation",
    "ReleaseIdentity",
    "ReleaseObservations",
    "ReleaseState",
    "ReleaseStateResult",
    "RemoteGitObservations",
    "SafetyObservations",
    "VerificationObservations",
    "VerifierStatus",
    "classify_release_state",
]
