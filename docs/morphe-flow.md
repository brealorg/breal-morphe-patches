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

## Mutation boundary

Later v22 phases may consume this report, but they must not bypass it. A future
mutation command must:

1. collect a fresh complete audit;
2. bind the intended operation to exact branch, worktree, local HEAD, remote HEAD,
   PR head, base SHA, and report fingerprint;
3. print a dry-run plan;
4. require explicit approval for the exact mutation;
5. re-observe all preconditions immediately before mutation;
6. verify postconditions and record one transaction result.
