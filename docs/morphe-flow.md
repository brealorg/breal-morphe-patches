# Morphe flow lifecycle controller

`tools/morphe-flow.py` is the canonical lifecycle observer introduced by
Hardening v22.1.

## v22.1 scope

The first version is read-only. It does not run `fetch`, `checkout`, `stash`,
`commit`, `branch`, `worktree add/remove`, `push`, pull-request mutations,
workflow dispatches, tags, releases, or asset uploads.

It observes:

- every registered Git worktree;
- dirty tracked and untracked files;
- local branch, HEAD, upstream, ahead/behind, and merge-base state;
- patch-equivalent commits using `git cherry`;
- repository-wide stashes;
- remote branch heads using `git ls-remote`;
- GitHub pull requests using read-only `gh pr list`;
- remote-only work/release branches and open PRs without a local branch.

Merged pull-request cleanup is proven from GitHub identity rather than patch-id
heuristics: the local branch HEAD must equal the recorded PR head, and the PR
merge commit must be reachable from current `main`. This correctly handles
squash merges where `git cherry` reports the original branch commits as unique.

Every report declares `MUTATIONS=NONE`, includes a canonical
`REPORT_FINGERPRINT=sha256:...`, and provides normalized lifecycle states,
blockers, warnings, and exactly one next action. A stale local `origin/main`
tracking ref is a blocker, not a warning. `SAFE_TO_MUTATE=YES` means only
that the complete observed state is internally consistent enough for a future
operation-specific planner; it is not mutation authorization.

## Commands

Full audit with remote and PR observation:

```bash
python3 tools/morphe-flow.py \
  --repo ~/dev/breal-morphe-patches \
  --json-output /tmp/morphe-flow-audit.json \
  audit
```

Local-only audit without network or GitHub CLI. This mode is diagnostic and
never authorizes mutation because remote and PR observations are intentionally
skipped:

```bash
python3 tools/morphe-flow.py \
  --repo ~/dev/breal-morphe-patches \
  --local-only \
  audit
```

Issue-scoped status:

```bash
python3 tools/morphe-flow.py \
  --repo ~/dev/breal-morphe-patches \
  issue status 106
```

Use `--strict` when a caller should receive exit code `2` for incomplete
observations or lifecycle blockers. Normal audit mode prints the complete report
and exits successfully so inspection remains usable during diagnosis.

## v22.2 operation-specific push readiness

A repository-wide blocker no longer automatically blocks every unrelated
operation. The command below evaluates only the exact work branch, while still
requiring canonical `main`, current `origin/main`, complete remote/GitHub
observation, a clean target worktree, and a forward-only remote relation:

```bash
python3 tools/morphe-flow.py \
  --repo ~/dev/breal-morphe-patches \
  branch ready-push work/example
```

The command disables local Git hooks and executes an exact SHA-bound
`git push --dry-run --porcelain`. It snapshots local refs, the worktree
registry, target status, and target index bytes/metadata before and after, then
re-observes remote `main` and the target remote branch. A successful result
contains `OPERATION_READY=YES`, `MUTATIONS=NONE`, `REMOTE_WRITES=NONE`, an
`OPERATION_FINGERPRINT`, and `NEXT=REQUEST_EXPLICIT_PUSH_AUTHORIZATION`. It
never performs the actual push. Dirty unrelated worktrees are counted and
reported, but do not invalidate a correctly isolated target operation.

## v22.3 operation-scoped receipts

Repository-wide snapshots remain useful diagnostics, but they are not universal
postconditions. A transaction verifier now checks only the fields that the
specific operation is authorized to change or must preserve by contract.

The first v22.3 verifier covers a completed local `main` synchronization:

```bash
python3 tools/morphe-flow.py \
  --repo ~/dev/breal-morphe-patches \
  main verify-sync \
  --old-main <previous-main-sha> \
  --new-main <merged-main-sha> \
  --pr-head <authorized-pr-head-sha> \
  --work-branch work/example
```

`SYNC_LOCAL_MAIN` verifies local `main`, `origin/main`, remote `main`, the
preserved work branch, the clean main worktree, the squash parent, the squash
tree, and the optional symbolic `origin/HEAD` alias. Other worktrees are observed
only for diagnostic context. Dirty state, index metadata, or concurrent work in
an unrelated issue worktree is reported under
`UNRELATED_ACTIVITY_POLICY=REPORT_ONLY_NEVER_GATE_SYNC_LOCAL_MAIN` and cannot
make the main-sync receipt fail.

Every required mismatch is emitted as a field-level `CHECK=... STATUS=FAIL`
entry with expected and actual values. The verifier is read-only, requires no
historic whole-repository snapshot, and emits an operation fingerprint derived
only from the authorized operation and its relevant observations.

## Mutation boundary

Later v22 phases may consume the audit and readiness reports, but they must
not bypass them. A future mutation command must:

1. collect a fresh complete audit;
2. bind the intended operation to exact branch, worktree, local HEAD, remote HEAD,
   PR head, base SHA, and report fingerprint;
3. print a dry-run plan;
4. require explicit approval for the exact mutation;
5. re-observe all preconditions immediately before mutation;
6. verify postconditions and record one transaction result.
