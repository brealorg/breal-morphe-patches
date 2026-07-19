#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPENDABOT="$ROOT/.github/dependabot.yml"
PR_BUILD="$ROOT/.github/workflows/build_pull_request.yml"
FEED_SMOKE="$ROOT/.github/workflows/release-feed-smoke.yml"
BYTECODE_SAFETY="$ROOT/.github/workflows/boost-bytecode-safety.yml"
WRAPPER_UPDATE="$ROOT/.github/workflows/update-gradle-wrapper.yml"
LEGACY_DEV_PR="$ROOT/.github/workflows/open_pull_request.yml"
RELEASE="$ROOT/.github/workflows/release.yml"
POLICY="$ROOT/docs/branch-policy.md"

test -f "$DEPENDABOT"
test -f "$PR_BUILD"
test -f "$FEED_SMOKE"
test -f "$BYTECODE_SAFETY"
test -f "$WRAPPER_UPDATE"
test -f "$RELEASE"
test -f "$POLICY"
test ! -e "$LEGACY_DEV_PR"

if rg -n -F 'target-branch: dev' "$DEPENDABOT" "$WRAPPER_UPDATE"; then
  echo 'FAIL: maintenance automation still targets dev' >&2
  exit 1
fi

test "$(rg -c -F 'target-branch: main' "$DEPENDABOT")" -eq 3
rg -q -F 'target-branch: main' "$WRAPPER_UPDATE"

if rg -n -F -- '- dev' "$PR_BUILD"; then
  echo 'FAIL: pull-request build still targets dev' >&2
  exit 1
fi

rg -q -F -- '- main' "$PR_BUILD"
rg -q -F 'HEAD:refs/heads/dev' "$RELEASE"
rg -q -F '`main` is the canonical development and pull-request target.' "$POLICY"
rg -q -F '`dev` is retained only as a release mirror' "$POLICY"

python3 - "$PR_BUILD" "$FEED_SMOKE" "$BYTECODE_SAFETY" <<'PY_CHECK'
import re
import sys
from pathlib import Path

for raw_path in sys.argv[1:]:
    path = Path(raw_path)
    text = path.read_text(encoding="utf-8")
    match = re.search(
        r"(?ms)^  pull_request:\s*\n(.*?)(?=^  [A-Za-z_][A-Za-z0-9_-]*:|^[A-Za-z_][A-Za-z0-9_-]*:|\Z)",
        text,
    )
    assert match is not None, f"missing pull_request block: {path}"
    block = match.group(1)
    assert re.search(
        r"(?m)^\s*(?:-\s+main|branches:\s*\[main\])\s*$",
        block,
    ), f"pull_request does not target main: {path}"
    assert not re.search(
        r"(?m)^\s*(?:-\s+dev|branches:.*\bdev\b)",
        block,
    ), f"pull_request still targets dev: {path}"
PY_CHECK

echo 'MAIN_CANONICAL_PR_TARGET=PASS'
echo 'MAINTENANCE_AUTOMATION_TARGETS_MAIN=PASS'
echo 'DEV_RELEASE_MIRROR_DOCUMENTED=PASS'
echo 'RESULT=MORPHE_BRANCH_POLICY_CONTRACT_OK'
