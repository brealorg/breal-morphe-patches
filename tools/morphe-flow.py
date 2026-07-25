#!/usr/bin/env python3
"""Read-only lifecycle audit for Morphe repository worktrees and branches.

Hardening v22.1 deliberately performs no Git or GitHub mutation. It observes
local worktrees, branches, stashes, remote heads, and pull requests, then emits
one normalized lifecycle report that later mutation commands can consume.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Any, Iterable, Sequence


SCHEMA_VERSION = 1
ISSUE_BRANCH_RE = re.compile(r"(?:^|[\/_-])issue[-_]?([0-9]+)(?:$|[\/_-])", re.IGNORECASE)


class ObservationError(RuntimeError):
    """Raised when a required local observation cannot be completed."""


@dataclass(frozen=True, slots=True)
class CommandResult:
    argv: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class CommandRunner:
    """Execute commands without invoking a shell."""

    def run(
        self,
        argv: Sequence[str],
        *,
        cwd: Path | None = None,
        timeout: int = 30,
        env_overrides: dict[str, str] | None = None,
    ) -> CommandResult:
        environment = os.environ.copy()
        if env_overrides:
            environment.update(env_overrides)
        completed = subprocess.run(
            list(argv),
            cwd=str(cwd) if cwd is not None else None,
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
            env=environment,
        )
        return CommandResult(
            argv=tuple(argv),
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )


class GitReader:
    """Fail-closed allowlist for read-only Git operations."""

    _SIMPLE_READ_ONLY = {
        "cat-file",
        "cherry",
        "for-each-ref",
        "ls-remote",
        "merge-base",
        "rev-list",
        "rev-parse",
        "status",
    }

    def __init__(self, runner: CommandRunner) -> None:
        self._runner = runner

    @staticmethod
    def _validate(args: Sequence[str]) -> None:
        if not args:
            raise ValueError("missing Git subcommand")
        command = args[0]
        if command in GitReader._SIMPLE_READ_ONLY:
            return
        if command == "config" and tuple(args[1:]) == (
            "--get",
            "remote.origin.url",
        ):
            return
        if command == "worktree" and len(args) >= 2 and args[1] == "list":
            return
        if command == "stash" and len(args) >= 2 and args[1] == "list":
            return
        if command == "remote" and len(args) >= 2 and args[1] == "get-url":
            return
        raise ValueError(f"refusing non-read-only Git operation: {' '.join(args)}")

    def run(
        self,
        cwd: Path,
        *args: str,
        timeout: int = 30,
        allow_failure: bool = False,
    ) -> CommandResult:
        self._validate(args)
        result = self._runner.run(
            ("git", *args),
            cwd=cwd,
            timeout=timeout,
            env_overrides={
                "GIT_OPTIONAL_LOCKS": "0",
                "GIT_TERMINAL_PROMPT": "0",
                "LC_ALL": "C",
            },
        )
        if result.returncode != 0 and not allow_failure:
            detail = result.stderr.strip() or result.stdout.strip() or "unknown Git error"
            raise ObservationError(f"git {' '.join(args)} failed in {cwd}: {detail}")
        return result


@dataclass(frozen=True, slots=True)
class PullRequestObservation:
    number: int
    state: str
    draft: bool
    head_branch: str
    head_sha: str | None
    base_branch: str
    url: str
    merge_commit_sha: str | None = None


@dataclass(frozen=True, slots=True)
class WorktreeObservation:
    path: str
    exists: bool
    head_sha: str | None
    branch: str | None
    detached: bool
    locked: str | None
    prunable: str | None
    issue_number: int | None
    dirty: bool | None
    dirty_entries: tuple[str, ...]
    upstream: str | None
    upstream_ahead: int | None
    upstream_behind: int | None
    main_ahead: int | None
    main_behind: int | None
    merge_base_with_main: str | None
    cherry_unique: int | None
    cherry_equivalent: int | None
    remote_head_sha: str | None
    remote_relation: str
    pull_request: PullRequestObservation | None
    pull_request_head_matches: bool | None
    pull_request_merge_in_main: bool | None
    lifecycle_state: str
    safe_to_mutate: bool
    blockers: tuple[str, ...]
    warnings: tuple[str, ...]
    next_action: str


@dataclass(frozen=True, slots=True)
class BranchObservation:
    branch: str
    head_sha: str
    worktree_path: str | None
    issue_number: int | None
    upstream: str | None
    upstream_track: str | None
    main_ahead: int | None
    main_behind: int | None
    cherry_unique: int | None
    cherry_equivalent: int | None
    remote_head_sha: str | None
    pull_request: PullRequestObservation | None
    pull_request_head_matches: bool | None
    pull_request_merge_in_main: bool | None
    lifecycle_state: str
    next_action: str


@dataclass(frozen=True, slots=True)
class StashObservation:
    ref: str
    commit_sha: str
    message: str


@dataclass(frozen=True, slots=True)
class ObserverStatus:
    status: str
    errors: tuple[str, ...] = ()
    warnings: tuple[str, ...] = ()


@dataclass(frozen=True, slots=True)
class AuditReport:
    schema_version: int
    generated_at: str
    repository_root: str
    repository_slug: str | None
    origin_url: str | None
    local_main_sha: str | None
    origin_main_tracking_sha: str | None
    remote_main_sha: str | None
    origin_main_tracking_current: bool | None
    worktrees: tuple[WorktreeObservation, ...]
    branches: tuple[BranchObservation, ...]
    stashes: tuple[StashObservation, ...]
    remote_only_branches: tuple[str, ...]
    open_pull_requests_without_local_branch: tuple[PullRequestObservation, ...]
    local_observer: ObserverStatus
    remote_git_observer: ObserverStatus
    github_observer: ObserverStatus
    global_blockers: tuple[str, ...]
    global_warnings: tuple[str, ...]
    blocker_count: int
    warning_count: int
    safe_to_mutate: bool
    report_fingerprint: str
    mutations: str = "NONE"

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def _git_stdout(result: CommandResult) -> str:
    return result.stdout.strip()


def _resolve_repository_root(git: GitReader, candidate: Path) -> Path:
    result = git.run(candidate, "rev-parse", "--show-toplevel")
    return Path(_git_stdout(result)).resolve()


def _parse_origin_slug(url: str | None) -> str | None:
    if not url:
        return None
    value = url.strip()
    patterns = (
        r"^https?://github\.com/([^/]+/[^/]+?)(?:\.git)?$",
        r"^git@github\.com:([^/]+/[^/]+?)(?:\.git)?$",
        r"^ssh://git@github\.com/([^/]+/[^/]+?)(?:\.git)?$",
    )
    for pattern in patterns:
        match = re.match(pattern, value)
        if match:
            return match.group(1)
    return None


def _issue_number(branch: str | None) -> int | None:
    if not branch:
        return None
    match = ISSUE_BRANCH_RE.search(branch)
    return int(match.group(1)) if match else None


def _parse_worktree_porcelain(text: str) -> list[dict[str, str | bool]]:
    records: list[dict[str, str | bool]] = []
    current: dict[str, str | bool] = {}
    for raw_line in text.splitlines() + [""]:
        line = raw_line.rstrip("\n")
        if not line:
            if current:
                records.append(current)
                current = {}
            continue
        key, separator, value = line.partition(" ")
        current[key] = value if separator else True
    return records


def _parse_count_pair(text: str) -> tuple[int, int]:
    fields = text.strip().split()
    if len(fields) != 2:
        raise ObservationError(f"expected two revision counts, got: {text!r}")
    return int(fields[0]), int(fields[1])


def _rev_counts(
    git: GitReader,
    cwd: Path,
    left: str,
    right: str,
) -> tuple[int | None, int | None]:
    result = git.run(
        cwd,
        "rev-list",
        "--left-right",
        "--count",
        f"{left}...{right}",
        allow_failure=True,
    )
    if result.returncode != 0:
        return None, None
    left_only, right_only = _parse_count_pair(result.stdout)
    return right_only, left_only


def _merge_base(git: GitReader, cwd: Path, left: str, right: str) -> str | None:
    result = git.run(cwd, "merge-base", left, right, allow_failure=True)
    return _git_stdout(result) if result.returncode == 0 else None


def _cherry_counts(git: GitReader, cwd: Path, upstream: str, head: str) -> tuple[int | None, int | None]:
    result = git.run(cwd, "cherry", upstream, head, allow_failure=True)
    if result.returncode != 0:
        return None, None
    unique = 0
    equivalent = 0
    for line in result.stdout.splitlines():
        if line.startswith("+"):
            unique += 1
        elif line.startswith("-"):
            equivalent += 1
    return unique, equivalent


def _object_exists(git: GitReader, cwd: Path, sha: str) -> bool:
    result = git.run(cwd, "cat-file", "-e", f"{sha}^{{commit}}", allow_failure=True)
    return result.returncode == 0


def _is_ancestor(git: GitReader, cwd: Path, ancestor: str, descendant: str) -> bool | None:
    result = git.run(
        cwd,
        "merge-base",
        "--is-ancestor",
        ancestor,
        descendant,
        allow_failure=True,
    )
    if result.returncode == 0:
        return True
    if result.returncode == 1:
        return False
    return None


def _remote_relation(git: GitReader, cwd: Path, local_sha: str | None, remote_sha: str | None) -> str:
    if not remote_sha:
        return "ABSENT"
    if not local_sha:
        return "UNKNOWN"
    if local_sha == remote_sha:
        return "EQUAL"
    if not _object_exists(git, cwd, remote_sha):
        return "UNKNOWN_REMOTE_OBJECT"
    local_contains_remote = _is_ancestor(git, cwd, remote_sha, local_sha)
    remote_contains_local = _is_ancestor(git, cwd, local_sha, remote_sha)
    if local_contains_remote is True:
        return "LOCAL_AHEAD"
    if remote_contains_local is True:
        return "REMOTE_AHEAD"
    if local_contains_remote is False and remote_contains_local is False:
        return "DIVERGENT"
    return "UNKNOWN"


def _status_entries(git: GitReader, cwd: Path) -> tuple[str, ...]:
    result = git.run(
        cwd,
        "status",
        "--porcelain=v1",
        "-z",
        "--untracked-files=all",
    )
    return tuple(entry for entry in result.stdout.split("\x00") if entry)


def _upstream(git: GitReader, cwd: Path) -> str | None:
    result = git.run(
        cwd,
        "rev-parse",
        "--abbrev-ref",
        "--symbolic-full-name",
        "@{upstream}",
        allow_failure=True,
    )
    return _git_stdout(result) if result.returncode == 0 else None


def _read_worktrees(git: GitReader, root: Path) -> list[dict[str, str | bool]]:
    result = git.run(root, "worktree", "list", "--porcelain")
    return _parse_worktree_porcelain(result.stdout)


def _read_local_branches(git: GitReader, root: Path) -> list[dict[str, str | None]]:
    fmt = "%00".join(
        (
            "%(refname:short)",
            "%(objectname)",
            "%(upstream:short)",
            "%(upstream:track)",
            "%(worktreepath)",
        )
    )
    result = git.run(root, "for-each-ref", f"--format={fmt}", "refs/heads")
    branches: list[dict[str, str | None]] = []
    for raw_line in result.stdout.splitlines():
        fields = raw_line.split("\x00")
        if len(fields) != 5:
            raise ObservationError(f"unexpected for-each-ref output: {raw_line!r}")
        branches.append(
            {
                "branch": fields[0],
                "sha": fields[1],
                "upstream": fields[2] or None,
                "upstream_track": fields[3] or None,
                "worktree_path": fields[4] or None,
            }
        )
    return branches


def _read_stashes(git: GitReader, root: Path) -> tuple[StashObservation, ...]:
    fmt = "%gd%x00%H%x00%gs"
    result = git.run(root, "stash", "list", f"--format={fmt}")
    observations: list[StashObservation] = []
    for raw_line in result.stdout.splitlines():
        fields = raw_line.split("\x00", 2)
        if len(fields) == 3:
            observations.append(StashObservation(fields[0], fields[1], fields[2]))
    return tuple(observations)


def _read_remote_heads(git: GitReader, root: Path) -> tuple[dict[str, str], tuple[str, ...]]:
    result = git.run(root, "ls-remote", "--heads", "origin", allow_failure=True, timeout=45)
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or "git ls-remote failed"
        return {}, (detail,)
    heads: dict[str, str] = {}
    for line in result.stdout.splitlines():
        sha, separator, ref = line.partition("\t")
        if separator and ref.startswith("refs/heads/"):
            heads[ref.removeprefix("refs/heads/")] = sha
    return heads, ()


def _read_pull_requests(
    runner: CommandRunner,
    root: Path,
    repository_slug: str | None,
) -> tuple[tuple[PullRequestObservation, ...], tuple[str, ...]]:
    if not repository_slug:
        return (), ("could not derive GitHub owner/repository from origin URL",)
    if shutil.which("gh") is None:
        return (), ("GitHub CLI (gh) is unavailable",)
    fields = "number,state,isDraft,headRefName,headRefOid,baseRefName,url,mergeCommit"
    result = runner.run(
        (
            "gh",
            "pr",
            "list",
            "--repo",
            repository_slug,
            "--state",
            "all",
            "--limit",
            "200",
            "--json",
            fields,
        ),
        cwd=root,
        timeout=45,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or "gh pr list failed"
        return (), (detail,)
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        return (), (f"gh pr list returned invalid JSON: {exc}",)
    observations: list[PullRequestObservation] = []
    for item in payload:
        merge_commit = item.get("mergeCommit") or {}
        observations.append(
            PullRequestObservation(
                number=int(item["number"]),
                state=str(item.get("state") or "UNKNOWN"),
                draft=bool(item.get("isDraft")),
                head_branch=str(item.get("headRefName") or ""),
                head_sha=item.get("headRefOid"),
                base_branch=str(item.get("baseRefName") or ""),
                url=str(item.get("url") or ""),
                merge_commit_sha=merge_commit.get("oid") if isinstance(merge_commit, dict) else None,
            )
        )
    return tuple(observations), ()


def _pr_by_branch(pull_requests: Iterable[PullRequestObservation]) -> dict[str, PullRequestObservation]:
    grouped: dict[str, list[PullRequestObservation]] = {}
    for pull_request in pull_requests:
        grouped.setdefault(pull_request.head_branch, []).append(pull_request)
    selected: dict[str, PullRequestObservation] = {}
    for branch, items in grouped.items():
        items.sort(
            key=lambda item: (
                item.state.upper() == "OPEN",
                item.number,
            ),
            reverse=True,
        )
        selected[branch] = items[0]
    return selected


def _classify_worktree(
    *,
    branch: str | None,
    exists: bool,
    detached: bool,
    prunable: str | None,
    dirty: bool | None,
    head_sha: str | None,
    upstream: str | None,
    upstream_ahead: int | None,
    upstream_behind: int | None,
    main_ahead: int | None,
    main_behind: int | None,
    cherry_unique: int | None,
    cherry_equivalent: int | None,
    remote_relation: str,
    pull_request: PullRequestObservation | None,
    pull_request_head_matches: bool | None,
    pull_request_merge_in_main: bool | None,
) -> tuple[str, bool, tuple[str, ...], tuple[str, ...], str]:
    blockers: list[str] = []
    warnings: list[str] = []

    if not exists or prunable:
        blockers.append("worktree path is missing or marked prunable")
        return "PRUNABLE_WORKTREE", False, tuple(blockers), tuple(warnings), "PRUNE_WORKTREE_METADATA"
    if detached or not branch:
        blockers.append("worktree is detached from a local branch")
        return "DETACHED_WORKTREE", False, tuple(blockers), tuple(warnings), "ATTACH_OR_ARCHIVE_WORKTREE"
    if dirty:
        blockers.append("worktree contains tracked or untracked changes")
        state = "MAIN_DIRTY" if branch == "main" else "WORKTREE_DIRTY"
        return state, False, tuple(blockers), tuple(warnings), "CREATE_VERIFIED_CHECKPOINT"

    if branch == "main":
        if upstream_behind not in (None, 0) or upstream_ahead not in (None, 0):
            blockers.append("local main is not equal to its upstream")
            return "MAIN_NOT_ALIGNED", False, tuple(blockers), tuple(warnings), "ALIGN_MAIN_WITH_ORIGIN"
        return "MAIN_CLEAN", True, (), (), "NONE"

    if remote_relation in {"DIVERGENT", "REMOTE_AHEAD", "UNKNOWN_REMOTE_OBJECT", "UNKNOWN"}:
        blockers.append(f"local branch and remote branch relation is {remote_relation}")

    if pull_request and pull_request.state.upper() == "OPEN":
        if pull_request.base_branch != "main":
            blockers.append(f"pull request targets {pull_request.base_branch}, not main")
        if pull_request.head_sha and head_sha and pull_request.head_sha != head_sha:
            blockers.append("local HEAD does not equal the pull request head SHA")
        if main_behind not in (None, 0):
            warnings.append(f"branch is behind origin/main by {main_behind} commits")
        if blockers:
            return "PR_OPEN_BLOCKED", False, tuple(blockers), tuple(warnings), "RECONCILE_BRANCH_PR_STATE"
        return "PR_OPEN", True, (), tuple(warnings), "WAIT_FOR_OR_MERGE_PR"

    if pull_request and pull_request.state.upper() == "MERGED":
        if pull_request_head_matches is False:
            blockers.append(
                "local HEAD does not equal the merged pull request head SHA; "
                "the branch may contain post-merge work"
            )
            return "MERGED_PR_BRANCH_MOVED", False, tuple(blockers), tuple(warnings), "MANUAL_DIAGNOSIS"
        if pull_request_merge_in_main is False:
            blockers.append("merged pull request commit is not reachable from current main")
            return "MERGED_PR_NOT_IN_MAIN", False, tuple(blockers), tuple(warnings), "MANUAL_DIAGNOSIS"
        if pull_request_head_matches is True and pull_request_merge_in_main is True:
            return "MERGED_CLEANUP_READY", True, (), (), "REMOVE_STALE_BRANCH_AND_WORKTREE"
        blockers.append("merged pull request integration could not be proven from local main")
        return "MERGED_PR_INTEGRATION_UNKNOWN", False, tuple(blockers), tuple(warnings), "REFRESH_AND_RERUN_AUDIT"

    if pull_request and pull_request.state.upper() == "CLOSED":
        if cherry_unique == 0:
            return "CLOSED_PR_CLEANUP_READY", True, (), (), "REMOVE_STALE_BRANCH_AND_WORKTREE"
        blockers.append("closed unmerged pull request branch still has unique patches not present on main")
        return "CLOSED_PR_WITH_UNIQUE_WORK", False, tuple(blockers), tuple(warnings), "MANUAL_DIAGNOSIS"

    if cherry_unique == 0 and (cherry_equivalent or 0) > 0:
        return "PATCH_EQUIVALENT_TO_MAIN", True, (), (), "REMOVE_REDUNDANT_BRANCH_AND_WORKTREE"
    if main_ahead == 0 and main_behind not in (None, 0):
        return "BRANCH_ANCESTOR_OF_MAIN", True, (), (), "REMOVE_REDUNDANT_BRANCH_AND_WORKTREE"

    if remote_relation == "SKIPPED":
        if main_behind not in (None, 0):
            warnings.append(f"branch is behind origin/main by {main_behind} commits")
            return "LOCAL_OBSERVATION_NEEDS_REBASE", False, (), tuple(warnings), "RUN_FULL_AUDIT"
        if (cherry_unique or 0) > 0:
            return "LOCAL_OBSERVATION_UNPUBLISHED_WORK", False, (), tuple(warnings), "RUN_FULL_AUDIT"
        return "LOCAL_OBSERVATION_REDUNDANT_BRANCH", False, (), tuple(warnings), "RUN_FULL_AUDIT"

    if remote_relation == "ABSENT":
        if main_behind not in (None, 0):
            warnings.append(f"branch is behind origin/main by {main_behind} commits")
            return "LOCAL_ONLY_NEEDS_REBASE", False, ("branch must be rebased before publication",), tuple(warnings), "REBASE_ON_MAIN"
        return "LOCAL_ONLY_READY", True, (), (), "PREFLIGHT_PUSH"

    if remote_relation == "EQUAL":
        if main_behind not in (None, 0):
            warnings.append(f"branch is behind origin/main by {main_behind} commits")
            return "REMOTE_BRANCH_NEEDS_REBASE", False, ("published branch is behind main",), tuple(warnings), "REBASE_WITH_FORCE_WITH_LEASE_PREFLIGHT"
        return "REMOTE_BRANCH_NO_PR", True, (), (), "CREATE_OR_RECOVER_PR"

    if remote_relation == "LOCAL_AHEAD":
        if main_behind not in (None, 0):
            warnings.append(f"branch is behind origin/main by {main_behind} commits")
            return "LOCAL_AHEAD_REMOTE_NEEDS_REBASE", False, ("branch must be rebased before publication",), tuple(warnings), "REBASE_ON_MAIN"
        return "LOCAL_AHEAD_OF_REMOTE", True, (), tuple(warnings), "PREFLIGHT_PUSH"

    if remote_relation == "REMOTE_AHEAD":
        return "REMOTE_AHEAD_OF_LOCAL", False, tuple(blockers), tuple(warnings), "FETCH_AND_RECONCILE"
    if remote_relation == "UNKNOWN_REMOTE_OBJECT":
        return "REMOTE_OBJECT_NOT_FETCHED", False, tuple(blockers), tuple(warnings), "FETCH_ORIGIN_AND_RERUN_AUDIT"
    if remote_relation == "DIVERGENT":
        return "LOCAL_REMOTE_DIVERGED", False, tuple(blockers), tuple(warnings), "MANUAL_DIAGNOSIS"

    if blockers:
        return "BRANCH_REMOTE_MISMATCH", False, tuple(blockers), tuple(warnings), "MANUAL_DIAGNOSIS"
    return "UNCLASSIFIED", False, ("lifecycle state could not be classified",), tuple(warnings), "MANUAL_DIAGNOSIS"


def _classify_branch(
    *,
    branch: str,
    main_ahead: int | None,
    main_behind: int | None,
    cherry_unique: int | None,
    cherry_equivalent: int | None,
    remote_head_sha: str | None,
    pull_request: PullRequestObservation | None,
    pull_request_head_matches: bool | None,
    pull_request_merge_in_main: bool | None,
    worktree_path: str | None,
) -> tuple[str, str]:
    if branch == "main":
        return "CANONICAL_MAIN", "NONE"
    if branch == "dev":
        return "RELEASE_MIRROR", "NONE"
    if worktree_path:
        return "ATTACHED_TO_WORKTREE", "SEE_WORKTREE_STATE"
    if pull_request and pull_request.state.upper() == "OPEN":
        return "OPEN_PR_WITHOUT_WORKTREE", "REVIEW_OR_REATTACH_WORKTREE"
    if pull_request and pull_request.state.upper() == "MERGED":
        if pull_request_head_matches is True and pull_request_merge_in_main is True:
            return "STALE_MERGED_PR_BRANCH", "DELETE_LOCAL_BRANCH"
        return "MERGED_PR_BRANCH_REQUIRES_DIAGNOSIS", "MANUAL_DIAGNOSIS"
    if pull_request and pull_request.state.upper() == "CLOSED" and cherry_unique == 0:
        return "STALE_CLOSED_PR_BRANCH", "DELETE_LOCAL_BRANCH"
    if cherry_unique == 0 and (cherry_equivalent or 0) > 0:
        return "PATCH_EQUIVALENT_LOCAL_BRANCH", "DELETE_LOCAL_BRANCH"
    if main_ahead == 0 and main_behind not in (None, 0):
        return "MERGED_LOCAL_BRANCH", "DELETE_LOCAL_BRANCH"
    if remote_head_sha is None and (cherry_unique or 0) > 0:
        return "UNPUBLISHED_LOCAL_BRANCH", "REATTACH_OR_ARCHIVE"
    if remote_head_sha is None and main_ahead == 0 and main_behind == 0:
        return "REDUNDANT_LOCAL_BRANCH", "DELETE_LOCAL_BRANCH"
    if remote_head_sha is not None and pull_request is None:
        return "REMOTE_BRANCH_WITHOUT_PR", "CREATE_OR_CLOSE_REMOTE_BRANCH"
    return "UNCLASSIFIED_LOCAL_BRANCH", "MANUAL_DIAGNOSIS"


def _canonical_fingerprint(value: Any) -> str:
    encoded = json.dumps(
        value,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=True,
    ).encode("utf-8")
    return f"sha256:{hashlib.sha256(encoded).hexdigest()}"


def collect_audit(
    candidate: Path,
    *,
    local_only: bool,
    runner: CommandRunner | None = None,
) -> AuditReport:
    command_runner = runner or CommandRunner()
    git = GitReader(command_runner)
    local_errors: list[str] = []
    local_warnings: list[str] = []
    remote_errors: list[str] = []
    github_errors: list[str] = []
    global_blockers: list[str] = []
    global_warnings: list[str] = []

    root = _resolve_repository_root(git, candidate.resolve())

    origin_result = git.run(
        root,
        "config",
        "--get",
        "remote.origin.url",
        allow_failure=True,
    )
    if origin_result.returncode != 0:
        origin_result = git.run(root, "remote", "get-url", "origin", allow_failure=True)
    origin_url = _git_stdout(origin_result) if origin_result.returncode == 0 else None
    repository_slug = _parse_origin_slug(origin_url)

    worktree_records = _read_worktrees(git, root)
    branch_records = _read_local_branches(git, root)
    stashes = _read_stashes(git, root)

    local_main_result = git.run(root, "rev-parse", "refs/heads/main", allow_failure=True)
    local_main_sha = _git_stdout(local_main_result) if local_main_result.returncode == 0 else None
    tracking_result = git.run(root, "rev-parse", "refs/remotes/origin/main", allow_failure=True)
    origin_main_tracking_sha = _git_stdout(tracking_result) if tracking_result.returncode == 0 else None
    if local_main_sha is None:
        global_blockers.append("local main branch is unavailable")
    if origin_main_tracking_sha is None:
        global_blockers.append("refs/remotes/origin/main is unavailable")

    remote_heads: dict[str, str] = {}
    pull_requests: tuple[PullRequestObservation, ...] = ()
    if local_only:
        remote_status = ObserverStatus("SKIPPED", warnings=("local-only audit requested",))
        github_status = ObserverStatus("SKIPPED", warnings=("local-only audit requested",))
    else:
        remote_heads, remote_failures = _read_remote_heads(git, root)
        remote_errors.extend(remote_failures)
        pull_requests, pr_failures = _read_pull_requests(command_runner, root, repository_slug)
        github_errors.extend(pr_failures)
        remote_status = ObserverStatus("PASS" if not remote_errors else "INCOMPLETE", tuple(remote_errors))
        github_status = ObserverStatus("PASS" if not github_errors else "INCOMPLETE", tuple(github_errors))

    remote_main_sha = remote_heads.get("main") if remote_heads else None
    origin_main_tracking_current = (
        origin_main_tracking_sha == remote_main_sha
        if origin_main_tracking_sha and remote_main_sha
        else None
    )
    if not local_only and remote_main_sha is None:
        global_blockers.append("remote main ref is unavailable")
    if not local_only and remote_main_sha and local_main_sha != remote_main_sha:
        global_blockers.append("local main branch does not equal remote main")
    if origin_main_tracking_current is False:
        global_blockers.append(
            "local origin/main tracking ref is stale relative to remote main"
        )

    pr_map = _pr_by_branch(pull_requests)
    worktrees: list[WorktreeObservation] = []

    for record in worktree_records:
        path_value = str(record.get("worktree") or "")
        path = Path(path_value)
        exists = path.exists()
        branch_ref = record.get("branch")
        branch = (
            str(branch_ref).removeprefix("refs/heads/")
            if isinstance(branch_ref, str)
            else None
        )
        detached = bool(record.get("detached")) or branch is None
        head_sha = str(record.get("HEAD")) if record.get("HEAD") else None
        prunable = str(record.get("prunable")) if record.get("prunable") else None
        locked = str(record.get("locked")) if record.get("locked") else None

        dirty_entries: tuple[str, ...] = ()
        dirty: bool | None = None
        upstream = None
        upstream_ahead = None
        upstream_behind = None
        main_ahead = None
        main_behind = None
        merge_base = None
        cherry_unique = None
        cherry_equivalent = None

        if exists:
            try:
                dirty_entries = _status_entries(git, path)
                dirty = bool(dirty_entries)
                upstream = _upstream(git, path)
                if upstream:
                    upstream_ahead, upstream_behind = _rev_counts(git, path, upstream, "HEAD")
                if origin_main_tracking_sha:
                    main_ahead, main_behind = _rev_counts(git, path, "origin/main", "HEAD")
                    merge_base = _merge_base(git, path, "origin/main", "HEAD")
                    cherry_unique, cherry_equivalent = _cherry_counts(git, path, "origin/main", "HEAD")
            except ObservationError as exc:
                local_errors.append(str(exc))

        remote_head_sha = remote_heads.get(branch) if branch else None
        relation = _remote_relation(git, root, head_sha, remote_head_sha) if not local_only else "SKIPPED"
        pull_request = pr_map.get(branch or "")
        pull_request_head_matches = None
        pull_request_merge_in_main = None
        if pull_request and pull_request.head_sha and head_sha:
            pull_request_head_matches = pull_request.head_sha == head_sha
        if (
            pull_request
            and pull_request.state.upper() == "MERGED"
            and pull_request.merge_commit_sha
            and local_main_sha
            and _object_exists(git, root, pull_request.merge_commit_sha)
        ):
            pull_request_merge_in_main = _is_ancestor(
                git,
                root,
                pull_request.merge_commit_sha,
                local_main_sha,
            )
        state, safe, blockers, warnings, next_action = _classify_worktree(
            branch=branch,
            exists=exists,
            detached=detached,
            prunable=prunable,
            dirty=dirty,
            head_sha=head_sha,
            upstream=upstream,
            upstream_ahead=upstream_ahead,
            upstream_behind=upstream_behind,
            main_ahead=main_ahead,
            main_behind=main_behind,
            cherry_unique=cherry_unique,
            cherry_equivalent=cherry_equivalent,
            remote_relation=relation,
            pull_request=pull_request,
            pull_request_head_matches=pull_request_head_matches,
            pull_request_merge_in_main=pull_request_merge_in_main,
        )
        if local_only:
            safe = False
            warnings = (*warnings, "remote Git and GitHub observations were skipped")
            if next_action == "NONE":
                next_action = "RUN_FULL_AUDIT"
        worktrees.append(
            WorktreeObservation(
                path=str(path),
                exists=exists,
                head_sha=head_sha,
                branch=branch,
                detached=detached,
                locked=locked,
                prunable=prunable,
                issue_number=_issue_number(branch),
                dirty=dirty,
                dirty_entries=dirty_entries,
                upstream=upstream,
                upstream_ahead=upstream_ahead,
                upstream_behind=upstream_behind,
                main_ahead=main_ahead,
                main_behind=main_behind,
                merge_base_with_main=merge_base,
                cherry_unique=cherry_unique,
                cherry_equivalent=cherry_equivalent,
                remote_head_sha=remote_head_sha,
                remote_relation=relation,
                pull_request=pull_request,
                pull_request_head_matches=pull_request_head_matches,
                pull_request_merge_in_main=pull_request_merge_in_main,
                lifecycle_state=state,
                safe_to_mutate=safe,
                blockers=blockers,
                warnings=warnings,
                next_action=next_action,
            )
        )

    branches: list[BranchObservation] = []
    local_branch_names = {str(item["branch"]) for item in branch_records}
    for record in branch_records:
        branch = str(record["branch"])
        head_sha = str(record["sha"])
        main_ahead = None
        main_behind = None
        cherry_unique = None
        cherry_equivalent = None
        if origin_main_tracking_sha:
            main_ahead, main_behind = _rev_counts(git, root, "origin/main", branch)
            cherry_unique, cherry_equivalent = _cherry_counts(git, root, "origin/main", branch)
        remote_head_sha = remote_heads.get(branch)
        pull_request = pr_map.get(branch)
        pull_request_head_matches = None
        pull_request_merge_in_main = None
        if pull_request and pull_request.head_sha:
            pull_request_head_matches = pull_request.head_sha == head_sha
        if (
            pull_request
            and pull_request.state.upper() == "MERGED"
            and pull_request.merge_commit_sha
            and local_main_sha
            and _object_exists(git, root, pull_request.merge_commit_sha)
        ):
            pull_request_merge_in_main = _is_ancestor(
                git,
                root,
                pull_request.merge_commit_sha,
                local_main_sha,
            )
        lifecycle_state, next_action = _classify_branch(
            branch=branch,
            main_ahead=main_ahead,
            main_behind=main_behind,
            cherry_unique=cherry_unique,
            cherry_equivalent=cherry_equivalent,
            remote_head_sha=remote_head_sha,
            pull_request=pull_request,
            pull_request_head_matches=pull_request_head_matches,
            pull_request_merge_in_main=pull_request_merge_in_main,
            worktree_path=record["worktree_path"],
        )
        branches.append(
            BranchObservation(
                branch=branch,
                head_sha=head_sha,
                worktree_path=record["worktree_path"],
                issue_number=_issue_number(branch),
                upstream=record["upstream"],
                upstream_track=record["upstream_track"],
                main_ahead=main_ahead,
                main_behind=main_behind,
                cherry_unique=cherry_unique,
                cherry_equivalent=cherry_equivalent,
                remote_head_sha=remote_head_sha,
                pull_request=pull_request,
                pull_request_head_matches=pull_request_head_matches,
                pull_request_merge_in_main=pull_request_merge_in_main,
                lifecycle_state=lifecycle_state,
                next_action=next_action,
            )
        )

    remote_only_branches = tuple(
        sorted(
            branch
            for branch in remote_heads
            if branch not in local_branch_names
            and (branch.startswith("work/") or branch.startswith("release/") or branch.startswith("work-") or branch.startswith("release-"))
        )
    )
    open_prs_without_local = tuple(
        sorted(
            (
                pull_request
                for pull_request in pull_requests
                if pull_request.state.upper() == "OPEN"
                and pull_request.head_branch not in local_branch_names
            ),
            key=lambda item: item.number,
        )
    )

    if local_errors:
        global_blockers.append("local Git observations are incomplete")
    if remote_errors:
        global_blockers.append("remote Git observations are incomplete")
    if github_errors:
        global_blockers.append("GitHub pull-request observations are incomplete")
    if stashes:
        global_warnings.append(f"repository contains {len(stashes)} stash entries")
    if remote_only_branches:
        global_warnings.append(f"repository contains {len(remote_only_branches)} remote-only work/release branches")
    if open_prs_without_local:
        global_warnings.append(f"repository contains {len(open_prs_without_local)} open pull requests without local branches")

    blocker_count = len(global_blockers) + sum(len(item.blockers) for item in worktrees)
    warning_count = (
        len(global_warnings)
        + sum(len(item.warnings) for item in worktrees)
        + len(local_warnings)
    )
    observers_complete = not local_errors and not local_only and not remote_errors and not github_errors
    safe_to_mutate = observers_complete and blocker_count == 0

    local_status = ObserverStatus(
        "PASS" if not local_errors else "INCOMPLETE",
        tuple(local_errors),
        tuple(local_warnings),
    )

    sorted_worktrees = tuple(sorted(worktrees, key=lambda item: item.path))
    sorted_branches = tuple(sorted(branches, key=lambda item: item.branch))
    fingerprint_payload = {
        "schema_version": SCHEMA_VERSION,
        "repository_root": str(root),
        "repository_slug": repository_slug,
        "origin_url": origin_url,
        "local_main_sha": local_main_sha,
        "origin_main_tracking_sha": origin_main_tracking_sha,
        "remote_main_sha": remote_main_sha,
        "origin_main_tracking_current": origin_main_tracking_current,
        "worktrees": [asdict(item) for item in sorted_worktrees],
        "branches": [asdict(item) for item in sorted_branches],
        "stashes": [asdict(item) for item in stashes],
        "remote_only_branches": list(remote_only_branches),
        "open_pull_requests_without_local_branch": [asdict(item) for item in open_prs_without_local],
        "local_observer": asdict(local_status),
        "remote_git_observer": asdict(remote_status),
        "github_observer": asdict(github_status),
        "global_blockers": global_blockers,
        "global_warnings": global_warnings,
    }
    report_fingerprint = _canonical_fingerprint(fingerprint_payload)

    return AuditReport(
        schema_version=SCHEMA_VERSION,
        generated_at=datetime.now(timezone.utc).isoformat(),
        repository_root=str(root),
        repository_slug=repository_slug,
        origin_url=origin_url,
        local_main_sha=local_main_sha,
        origin_main_tracking_sha=origin_main_tracking_sha,
        remote_main_sha=remote_main_sha,
        origin_main_tracking_current=origin_main_tracking_current,
        worktrees=sorted_worktrees,
        branches=sorted_branches,
        stashes=stashes,
        remote_only_branches=remote_only_branches,
        open_pull_requests_without_local_branch=open_prs_without_local,
        local_observer=local_status,
        remote_git_observer=remote_status,
        github_observer=github_status,
        global_blockers=tuple(global_blockers),
        global_warnings=tuple(global_warnings),
        blocker_count=blocker_count,
        warning_count=warning_count,
        safe_to_mutate=safe_to_mutate,
        report_fingerprint=report_fingerprint,
    )


def _short_sha(value: str | None) -> str:
    return value[:12] if value else "-"


def _tri_state(value: bool | None) -> str:
    return "UNKNOWN" if value is None else str(value).upper()


def _print_human(report: AuditReport, *, issue: int | None = None) -> None:
    print("===== MORPHE FLOW AUDIT =====")
    print(f"SCHEMA_VERSION={report.schema_version}")
    print(f"REPOSITORY_ROOT={report.repository_root}")
    print(f"REPOSITORY_SLUG={report.repository_slug or '-'}")
    print(f"LOCAL_MAIN={_short_sha(report.local_main_sha)}")
    print(f"ORIGIN_MAIN_TRACKING={_short_sha(report.origin_main_tracking_sha)}")
    print(f"REMOTE_MAIN={_short_sha(report.remote_main_sha)}")
    tracking = report.origin_main_tracking_current
    print(f"ORIGIN_MAIN_TRACKING_CURRENT={'UNKNOWN' if tracking is None else str(tracking).upper()}")
    print(f"LOCAL_OBSERVER={report.local_observer.status}")
    print(f"REMOTE_GIT_OBSERVER={report.remote_git_observer.status}")
    print(f"GITHUB_OBSERVER={report.github_observer.status}")
    print(f"REPORT_FINGERPRINT={report.report_fingerprint}")
    print(f"MUTATIONS={report.mutations}")
    for blocker in report.global_blockers:
        print(f"GLOBAL_BLOCKER={blocker}")
    for warning in report.global_warnings:
        print(f"GLOBAL_WARNING={warning}")

    worktrees = [item for item in report.worktrees if issue is None or item.issue_number == issue]
    branches = [item for item in report.branches if issue is None or item.issue_number == issue]

    print()
    print(f"===== WORKTREES ({len(worktrees)}) =====")
    for item in worktrees:
        print(
            " ".join(
                (
                    f"STATE={item.lifecycle_state}",
                    f"SAFE={'YES' if item.safe_to_mutate else 'NO'}",
                    f"BRANCH={item.branch or 'DETACHED'}",
                    f"HEAD={_short_sha(item.head_sha)}",
                    f"DIRTY={'UNKNOWN' if item.dirty is None else str(item.dirty).upper()}",
                    f"MAIN_AHEAD={item.main_ahead if item.main_ahead is not None else '-'}",
                    f"MAIN_BEHIND={item.main_behind if item.main_behind is not None else '-'}",
                    f"REMOTE={item.remote_relation}",
                    f"PR={item.pull_request.number if item.pull_request else '-'}",
                    f"PR_HEAD_MATCH={_tri_state(item.pull_request_head_matches)}",
                    f"PR_MERGE_MAIN={_tri_state(item.pull_request_merge_in_main)}",
                    f"NEXT={item.next_action}",
                    f"PATH={item.path}",
                )
            )
        )
        for blocker in item.blockers:
            print(f"  BLOCKER={blocker}")
        for warning in item.warnings:
            print(f"  WARNING={warning}")
        for dirty_entry in item.dirty_entries[:20]:
            print(f"  DIRTY_ENTRY={dirty_entry}")
        if len(item.dirty_entries) > 20:
            print(f"  DIRTY_ENTRY_TRUNCATED={len(item.dirty_entries) - 20}")

    print()
    print(f"===== LOCAL BRANCHES ({len(branches)}) =====")
    for item in branches:
        print(
            " ".join(
                (
                    f"STATE={item.lifecycle_state}",
                    f"BRANCH={item.branch}",
                    f"HEAD={_short_sha(item.head_sha)}",
                    f"MAIN_AHEAD={item.main_ahead if item.main_ahead is not None else '-'}",
                    f"MAIN_BEHIND={item.main_behind if item.main_behind is not None else '-'}",
                    f"REMOTE={'YES' if item.remote_head_sha else 'NO'}",
                    f"PR={item.pull_request.number if item.pull_request else '-'}",
                    f"PR_HEAD_MATCH={_tri_state(item.pull_request_head_matches)}",
                    f"PR_MERGE_MAIN={_tri_state(item.pull_request_merge_in_main)}",
                    f"WORKTREE={item.worktree_path or '-'}",
                    f"NEXT={item.next_action}",
                )
            )
        )

    print()
    print(f"STASH_COUNT={len(report.stashes)}")
    print(f"REMOTE_ONLY_BRANCH_COUNT={len(report.remote_only_branches)}")
    print(f"OPEN_PR_WITHOUT_LOCAL_BRANCH_COUNT={len(report.open_pull_requests_without_local_branch)}")
    print(f"BLOCKER_COUNT={report.blocker_count}")
    print(f"WARNING_COUNT={report.warning_count}")
    print(f"SAFE_TO_MUTATE={'YES' if report.safe_to_mutate else 'NO'}")
    print("RESULT=MORPHE_FLOW_AUDIT_COMPLETE")


def _write_json(report: AuditReport, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report.as_dict(), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="morphe-flow", description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd(), help="path inside the target Git repository")
    parser.add_argument("--local-only", action="store_true", help="skip git ls-remote and GitHub pull-request observation")
    parser.add_argument("--json-output", type=Path, help="write the complete report as JSON")
    parser.add_argument("--format", choices=("human", "json"), default="human")
    parser.add_argument("--strict", action="store_true", help="return non-zero when observations are incomplete or blockers exist")

    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("audit", help="audit every local worktree and branch")
    issue_parser = subparsers.add_parser("issue", help="issue-scoped lifecycle commands")
    issue_subparsers = issue_parser.add_subparsers(dest="issue_command", required=True)
    issue_status = issue_subparsers.add_parser("status", help="show worktrees and branches associated with one issue")
    issue_status.add_argument("issue_number", type=int)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    try:
        report = collect_audit(args.repo, local_only=args.local_only)
    except (ObservationError, OSError, subprocess.SubprocessError, ValueError) as exc:
        print("===== MORPHE FLOW AUDIT =====")
        print(f"ERROR={exc}")
        print("MUTATIONS=NONE")
        print("RESULT=MORPHE_FLOW_AUDIT_FAILED")
        return 1

    issue = args.issue_number if args.command == "issue" else None
    if args.json_output:
        _write_json(report, args.json_output)
        print(f"JSON_OUTPUT={args.json_output}", file=sys.stderr if args.format == "json" else sys.stdout)

    if args.format == "json":
        if issue is None:
            payload = report.as_dict()
        else:
            payload = {
                "schema_version": report.schema_version,
                "generated_at": report.generated_at,
                "issue_number": issue,
                "worktrees": [asdict(item) for item in report.worktrees if item.issue_number == issue],
                "branches": [asdict(item) for item in report.branches if item.issue_number == issue],
                "mutations": "NONE",
            }
        print(json.dumps(payload, indent=2, sort_keys=True))
    else:
        _print_human(report, issue=issue)

    if args.strict and (not report.safe_to_mutate):
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
