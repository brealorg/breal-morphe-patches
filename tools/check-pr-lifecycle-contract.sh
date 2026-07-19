#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="$ROOT/.github/pull_request_template.md"
POLICY="$ROOT/docs/release-validation-policy.md"

test -f "$TEMPLATE"
test -f "$POLICY"

rg -q -F 'Addresses #' "$TEMPLATE"
rg -q -F 'do not use GitHub auto-close keywords' "$TEMPLATE"
rg -q -F 'Pull requests for user-visible work must reference the issue' "$POLICY"
rg -q -F 'Issue closure is a separate, manual closeout operation' "$POLICY"

if rg -n -i \
  '^[[:space:]]*(closes|closed|fixes|fixed|resolves|resolved)[[:space:]]+#[0-9]*' \
  "$TEMPLATE"
then
  echo 'FAIL: PR template contains an issue auto-close directive' >&2
  exit 1
fi

echo 'PR_TEMPLATE_NON_CLOSING_REFERENCE=PASS'
echo 'USER_VISIBLE_ISSUE_MANUAL_CLOSEOUT=PASS'
echo 'RESULT=MORPHE_PR_LIFECYCLE_CONTRACT_OK'
