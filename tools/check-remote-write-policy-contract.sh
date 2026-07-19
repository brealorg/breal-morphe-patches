#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POLICY="$ROOT/docs/branch-policy.md"

test -f "$POLICY"

rg -q -F 'Authorization and transport capability are separate gates.' "$POLICY"
rg -q -F 'git push --dry-run' "$POLICY"
rg -q -F 'git ls-remote' "$POLICY"
rg -q -F 'not request push approval and do not attempt the real push' "$POLICY"
rg -q -F 'requires its own explicit approval' "$POLICY"
rg -q -F 'refs/heads/${BRANCH}:refs/heads/${BRANCH}' "$POLICY"
rg -q -F 'verify the imported ref against the expected commit SHA' "$POLICY"
rg -q -F 'only after that postcondition passes.' "$POLICY"
rg -q -F 'marker and return a non-zero status.' "$POLICY"

echo 'REMOTE_WRITE_CAPABILITY_PREFLIGHT=PASS'
echo 'READ_ACCESS_NOT_WRITE_PROOF=PASS'
echo 'HANDOFF_FAILS_CLOSED=PASS'
echo 'RESULT=MORPHE_REMOTE_WRITE_POLICY_CONTRACT_OK'
