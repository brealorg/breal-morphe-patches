#!/usr/bin/env python3
"""Operation-scoped, read-only verification for Morphe lifecycle transactions."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from typing import Any, Sequence


OPERATION_SCHEMA_VERSION = 1
_FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
_WORK_BRANCH = re.compile(r"^work/[A-Za-z0-9][A-Za-z0-9._/-]*$")


class OperationObservationError(RuntimeError):
    """Raised when an operation-scoped observation cannot be completed."""


@dataclass(frozen=True, slots=True)
class CommandResult:
    argv: tuple[str, ...]
    returncode: int
    stdout: str
    stderr: str


class CommandRunner:
    """Execute one command without a shell."""

    def run(
        self,
        argv: Sequence[str],
        *,
        cwd: Path,
        timeout: int = 45,
        allow_failure: bool = False,
    ) -> CommandResult:
        completed = subprocess.run(
            list(argv),
            cwd=str(cwd),
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
            env={
                **os.environ,
                "GIT_OPTIONAL_LOCKS": "0",
                "GIT_TERMINAL_PROMPT": "0",
                "LC_ALL": "C",
            },
        )
        result = CommandResult(
            argv=tuple(argv),
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )
        if result.returncode != 0 and not allow_failure:
            detail = result.stderr.strip() or result.stdout.strip() or "unknown command error"
            raise OperationObservationError(f"{' '.join(argv)} failed in {cwd}: {detail}")
        return result


class GitOperationReader:
    """Fail-closed read-only Git observer for operation receipts."""

    def __init__(self, runner: CommandRunner) -> None:
        self._runner = runner

    @staticmethod
    def _validate(args: Sequence[str]) -> None:
        if not args:
            raise ValueError("missing Git subcommand")
        command = args[0]
        if command == "rev-parse":
            return
        if command == "rev-list" and tuple(args[1:4]) == ("--parents", "-n", "1"):
            return
        if command == "status" and tuple(args[1:]) == (
            "--porcelain=v1",
            "-z",
            "--untracked-files=all",
        ):
            return
        if command == "worktree" and tuple(args[1:]) == ("list", "--porcelain"):
            return
        if command == "ls-remote" and len(args) >= 4 and tuple(args[1:3]) == (
            "--heads",
            "origin",
        ):
            return
        if command == "symbolic-ref" and tuple(args[1:]) == (
            "-q",
            "refs/remotes/origin/HEAD",
        ):
            return
        raise ValueError(f"refusing non-read-only operation Git command: {' '.join(args)}")

    def run(
        self,
        cwd: Path,
        *args: str,
        allow_failure: bool = False,
    ) -> CommandResult:
        self._validate(args)
        return self._runner.run(
            ("git", *args),
            cwd=cwd,
            allow_failure=allow_failure,
        )


@dataclass(frozen=True, slots=True)
class FieldCheck:
    field: str
    expected: str
    actual: str
    status: str
    required: bool
    detail: str


@dataclass(frozen=True, slots=True)
class UnrelatedWorktreeActivity:
    path: str
    branch: str
    head_sha: str
    dirty: bool | None
    status: str


@dataclass(frozen=True, slots=True)
class MainSyncVerificationReport:
    schema_version: int
    generated_at: str
    operation: str
    phase: str
    repository_root: str
    expected_old_main_sha: str
    expected_new_main_sha: str
    expected_pr_head_sha: str
    work_branch: str
    main_worktree_path: str | None
    local_main_sha: str | None
    origin_main_sha: str | None
    remote_main_sha: str | None
    remote_work_branch_sha: str | None
    main_worktree_head_sha: str | None
    main_worktree_dirty: bool | None
    origin_head_symbolic_target: str | None
    origin_head_resolved_sha: str | None
    merge_parent_line: str | None
    merge_tree_sha: str | None
    pr_head_tree_sha: str | None
    checks: tuple[FieldCheck, ...]
    unrelated_worktrees: tuple[UnrelatedWorktreeActivity, ...]
    unrelated_dirty_worktree_count: int
    unrelated_activity_policy: str
    blockers: tuple[str, ...]
    warnings: tuple[str, ...]
    operation_fingerprint: str
    verified: bool
    next_action: str
    mutations: str = "NONE"

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def _require_sha(name: str, value: str) -> None:
    if not _FULL_SHA.fullmatch(value):
        raise ValueError(f"{name} requires an exact lowercase 40-character commit SHA")


def _require_work_branch(branch: str) -> None:
    if not _WORK_BRANCH.fullmatch(branch):
        raise ValueError("work branch must start with work/ and contain only safe ref characters")
    if ".." in branch or "//" in branch or branch.endswith(("/", ".")):
        raise ValueError(f"malformed work branch: {branch}")


def _parse_worktrees(text: str) -> list[dict[str, str | bool]]:
    records: list[dict[str, str | bool]] = []
    current: dict[str, str | bool] = {}
    for raw_line in text.splitlines() + [""]:
        if not raw_line:
            if current:
                records.append(current)
                current = {}
            continue
        key, separator, value = raw_line.partition(" ")
        current[key] = value if separator else True
    return records


def _remote_heads(git: GitOperationReader, root: Path, work_branch: str) -> dict[str, str]:
    result = git.run(
        root,
        "ls-remote",
        "--heads",
        "origin",
        "refs/heads/main",
        f"refs/heads/{work_branch}",
    )
    heads: dict[str, str] = {}
    for line in result.stdout.splitlines():
        sha, separator, ref = line.partition("\t")
        if separator and ref.startswith("refs/heads/"):
            heads[ref.removeprefix("refs/heads/")] = sha
    return heads


def _rev_parse(
    git: GitOperationReader,
    root: Path,
    expression: str,
    *,
    allow_missing: bool = False,
) -> str | None:
    result = git.run(root, "rev-parse", expression, allow_failure=allow_missing)
    if result.returncode != 0:
        return None
    return result.stdout.strip()


def _status_dirty(git: GitOperationReader, path: Path) -> bool:
    result = git.run(
        path,
        "status",
        "--porcelain=v1",
        "-z",
        "--untracked-files=all",
    )
    return bool(result.stdout)


def _check(
    field: str,
    expected: str | None,
    actual: str | None,
    *,
    detail: str,
    required: bool = True,
) -> FieldCheck:
    expected_text = expected if expected is not None else "ABSENT"
    actual_text = actual if actual is not None else "ABSENT"
    status = "PASS" if expected_text == actual_text else ("FAIL" if required else "WARN")
    return FieldCheck(
        field=field,
        expected=expected_text,
        actual=actual_text,
        status=status,
        required=required,
        detail=detail,
    )


def _fingerprint(payload: dict[str, Any]) -> str:
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return "sha256:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def collect_main_sync_verification(
    candidate: Path,
    *,
    old_main_sha: str,
    new_main_sha: str,
    pr_head_sha: str,
    work_branch: str,
    runner: CommandRunner | None = None,
) -> MainSyncVerificationReport:
    """Verify only the postconditions causally relevant to SYNC_LOCAL_MAIN."""

    _require_sha("old main", old_main_sha)
    _require_sha("new main", new_main_sha)
    _require_sha("PR head", pr_head_sha)
    _require_work_branch(work_branch)
    if old_main_sha == new_main_sha:
        raise ValueError("old and new main SHAs must differ")

    command_runner = runner or CommandRunner()
    git = GitOperationReader(command_runner)
    root_result = git.run(candidate.resolve(), "rev-parse", "--show-toplevel")
    root = Path(root_result.stdout.strip()).resolve()

    local_main = _rev_parse(git, root, "refs/heads/main", allow_missing=True)
    origin_main = _rev_parse(git, root, "refs/remotes/origin/main", allow_missing=True)
    heads = _remote_heads(git, root, work_branch)
    remote_main = heads.get("main")
    remote_work_branch = heads.get(work_branch)

    worktree_result = git.run(root, "worktree", "list", "--porcelain")
    worktree_records = _parse_worktrees(worktree_result.stdout)
    main_records = [
        item
        for item in worktree_records
        if item.get("branch") == "refs/heads/main"
    ]
    main_record = main_records[0] if len(main_records) == 1 else None
    main_path = Path(str(main_record["worktree"])) if main_record else None
    main_head = str(main_record.get("HEAD")) if main_record and main_record.get("HEAD") else None
    main_dirty = _status_dirty(git, main_path) if main_path and main_path.exists() else None

    origin_head_result = git.run(
        root,
        "symbolic-ref",
        "-q",
        "refs/remotes/origin/HEAD",
        allow_failure=True,
    )
    origin_head_target = (
        origin_head_result.stdout.strip()
        if origin_head_result.returncode == 0
        else None
    )
    origin_head_sha = (
        _rev_parse(git, root, "refs/remotes/origin/HEAD", allow_missing=True)
        if origin_head_target
        else None
    )

    parent_result = git.run(root, "rev-list", "--parents", "-n", "1", new_main_sha)
    parent_line = parent_result.stdout.strip()
    merge_tree = _rev_parse(git, root, f"{new_main_sha}^{{tree}}", allow_missing=True)
    pr_tree = _rev_parse(git, root, f"{pr_head_sha}^{{tree}}", allow_missing=True)

    checks: list[FieldCheck] = [
        _check(
            "local_main",
            new_main_sha,
            local_main,
            detail="local refs/heads/main must equal the authorized squash commit",
        ),
        _check(
            "origin_main",
            new_main_sha,
            origin_main,
            detail="local refs/remotes/origin/main must equal the authorized squash commit",
        ),
        _check(
            "remote_main",
            new_main_sha,
            remote_main,
            detail="remote refs/heads/main must equal the authorized squash commit",
        ),
        _check(
            "remote_work_branch",
            pr_head_sha,
            remote_work_branch,
            detail="the merged work branch must remain at the authorized PR head until cleanup",
        ),
        _check(
            "main_worktree_count",
            "1",
            str(len(main_records)),
            detail="exactly one registered worktree must own refs/heads/main",
        ),
        _check(
            "main_worktree_head",
            new_main_sha,
            main_head,
            detail="the main worktree HEAD must equal local main",
        ),
        _check(
            "main_worktree_clean",
            "FALSE",
            None if main_dirty is None else str(main_dirty).upper(),
            detail="the main worktree must be clean after fast-forward",
        ),
        _check(
            "squash_parent_line",
            f"{new_main_sha} {old_main_sha}",
            parent_line,
            detail="the squash commit must have exactly the authorized old main as parent",
        ),
        _check(
            "squash_tree",
            pr_tree,
            merge_tree,
            detail="the squash commit tree must equal the authorized PR-head tree",
        ),
    ]

    warnings: list[str] = []
    if origin_head_target is None:
        warnings.append("refs/remotes/origin/HEAD is absent; this optional alias is not required")
    else:
        checks.extend(
            (
                _check(
                    "origin_head_symbolic_target",
                    "refs/remotes/origin/main",
                    origin_head_target,
                    detail="origin/HEAD must remain a symbolic alias of origin/main",
                ),
                _check(
                    "origin_head_resolved_sha",
                    new_main_sha,
                    origin_head_sha,
                    detail="origin/HEAD must resolve to the synchronized origin/main SHA",
                ),
            )
        )

    unrelated: list[UnrelatedWorktreeActivity] = []
    for item in worktree_records:
        path_text = str(item.get("worktree") or "")
        if not path_text or (main_path and Path(path_text) == main_path):
            continue
        branch_ref = item.get("branch")
        branch = (
            str(branch_ref).removeprefix("refs/heads/")
            if branch_ref
            else "DETACHED"
        )
        head = str(item.get("HEAD") or "UNKNOWN")
        path = Path(path_text)
        dirty: bool | None
        status: str
        if not path.exists():
            dirty = None
            status = "MISSING_PATH_REPORTED_ONLY"
        else:
            try:
                dirty = _status_dirty(git, path)
                status = "DIRTY_REPORTED_ONLY" if dirty else "CLEAN_REPORTED_ONLY"
            except (OSError, OperationObservationError):
                dirty = None
                status = "UNOBSERVED_REPORTED_ONLY"
        unrelated.append(
            UnrelatedWorktreeActivity(
                path=path_text,
                branch=branch,
                head_sha=head,
                dirty=dirty,
                status=status,
            )
        )

    blockers = tuple(
        f"{item.field}: expected {item.expected}, observed {item.actual}"
        for item in checks
        if item.required and item.status == "FAIL"
    )
    unrelated_dirty_count = sum(item.dirty is True for item in unrelated)
    if unrelated_dirty_count:
        warnings.append(
            f"observed {unrelated_dirty_count} dirty unrelated worktree(s); "
            "operation-scoped policy reports but does not gate on them"
        )

    payload = {
        "schema_version": OPERATION_SCHEMA_VERSION,
        "operation": "SYNC_LOCAL_MAIN",
        "phase": "POSTCONDITION",
        "repository_root": str(root),
        "expected_old_main_sha": old_main_sha,
        "expected_new_main_sha": new_main_sha,
        "expected_pr_head_sha": pr_head_sha,
        "work_branch": work_branch,
        "actual": {
            "local_main_sha": local_main,
            "origin_main_sha": origin_main,
            "remote_main_sha": remote_main,
            "remote_work_branch_sha": remote_work_branch,
            "main_worktree_path": str(main_path) if main_path else None,
            "main_worktree_head_sha": main_head,
            "main_worktree_dirty": main_dirty,
            "origin_head_symbolic_target": origin_head_target,
            "origin_head_resolved_sha": origin_head_sha,
            "merge_parent_line": parent_line,
            "merge_tree_sha": merge_tree,
            "pr_head_tree_sha": pr_tree,
        },
        "checks": [asdict(item) for item in checks],
        "unrelated_activity_policy": "REPORT_ONLY_NEVER_GATE_SYNC_LOCAL_MAIN",
    }
    operation_fingerprint = _fingerprint(payload)
    verified = not blockers

    return MainSyncVerificationReport(
        schema_version=OPERATION_SCHEMA_VERSION,
        generated_at=datetime.now(timezone.utc).isoformat(),
        operation="SYNC_LOCAL_MAIN",
        phase="POSTCONDITION",
        repository_root=str(root),
        expected_old_main_sha=old_main_sha,
        expected_new_main_sha=new_main_sha,
        expected_pr_head_sha=pr_head_sha,
        work_branch=work_branch,
        main_worktree_path=str(main_path) if main_path else None,
        local_main_sha=local_main,
        origin_main_sha=origin_main,
        remote_main_sha=remote_main,
        remote_work_branch_sha=remote_work_branch,
        main_worktree_head_sha=main_head,
        main_worktree_dirty=main_dirty,
        origin_head_symbolic_target=origin_head_target,
        origin_head_resolved_sha=origin_head_sha,
        merge_parent_line=parent_line,
        merge_tree_sha=merge_tree,
        pr_head_tree_sha=pr_tree,
        checks=tuple(checks),
        unrelated_worktrees=tuple(unrelated),
        unrelated_dirty_worktree_count=unrelated_dirty_count,
        unrelated_activity_policy="REPORT_ONLY_NEVER_GATE_SYNC_LOCAL_MAIN",
        blockers=blockers,
        warnings=tuple(warnings),
        operation_fingerprint=operation_fingerprint,
        verified=verified,
        next_action="START_NEXT_OPERATION" if verified else "RESOLVE_MAIN_SYNC_POSTCONDITION_FAILURES",
    )


def print_main_sync_verification(report: MainSyncVerificationReport) -> None:
    print("===== MORPHE FLOW MAIN SYNC VERIFICATION =====")
    print(f"SCHEMA_VERSION={report.schema_version}")
    print(f"OPERATION={report.operation}")
    print(f"PHASE={report.phase}")
    print(f"REPOSITORY_ROOT={report.repository_root}")
    print(f"EXPECTED_OLD_MAIN={report.expected_old_main_sha}")
    print(f"EXPECTED_NEW_MAIN={report.expected_new_main_sha}")
    print(f"EXPECTED_PR_HEAD={report.expected_pr_head_sha}")
    print(f"WORK_BRANCH={report.work_branch}")
    print(f"MAIN_WORKTREE={report.main_worktree_path or 'ABSENT'}")
    print(f"LOCAL_MAIN={report.local_main_sha or 'ABSENT'}")
    print(f"ORIGIN_MAIN={report.origin_main_sha or 'ABSENT'}")
    print(f"REMOTE_MAIN={report.remote_main_sha or 'ABSENT'}")
    print(f"REMOTE_WORK_BRANCH={report.remote_work_branch_sha or 'ABSENT'}")
    print(f"MAIN_WORKTREE_HEAD={report.main_worktree_head_sha or 'ABSENT'}")
    print(
        "MAIN_WORKTREE_DIRTY="
        + ("UNKNOWN" if report.main_worktree_dirty is None else str(report.main_worktree_dirty).upper())
    )
    print(f"ORIGIN_HEAD_TARGET={report.origin_head_symbolic_target or 'ABSENT'}")
    print(f"ORIGIN_HEAD_SHA={report.origin_head_resolved_sha or 'ABSENT'}")
    for check in report.checks:
        print(
            f"CHECK={check.field} STATUS={check.status} REQUIRED={str(check.required).upper()} "
            f"EXPECTED={check.expected} ACTUAL={check.actual} DETAIL={check.detail}"
        )
    print(f"UNRELATED_ACTIVITY_POLICY={report.unrelated_activity_policy}")
    print(f"UNRELATED_WORKTREE_COUNT={len(report.unrelated_worktrees)}")
    print(f"UNRELATED_DIRTY_WORKTREE_COUNT={report.unrelated_dirty_worktree_count}")
    for item in report.unrelated_worktrees:
        print(
            f"UNRELATED_ACTIVITY={item.status} BRANCH={item.branch} "
            f"HEAD={item.head_sha} PATH={item.path}"
        )
    for blocker in report.blockers:
        print(f"BLOCKER={blocker}")
    for warning in report.warnings:
        print(f"WARNING={warning}")
    print(f"OPERATION_FINGERPRINT={report.operation_fingerprint}")
    print(f"VERIFIED={'YES' if report.verified else 'NO'}")
    print(f"NEXT={report.next_action}")
    print(f"MUTATIONS={report.mutations}")
    print("RESULT=MORPHE_FLOW_MAIN_SYNC_VERIFICATION_COMPLETE")
