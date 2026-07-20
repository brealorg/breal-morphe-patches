#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUG_FORM="$ROOT/.github/ISSUE_TEMPLATE/bug_report.yml"
FEATURE_FORM="$ROOT/.github/ISSUE_TEMPLATE/feature_request.yml"

check_exact_line() {
  local file="$1"
  local expected="$2"
  local count

  count="$(rg -Fxc -- "$expected" "$file" || true)"
  if [[ "$count" != 1 ]]; then
    echo "FAIL: expected exactly one '$expected' line in $file" >&2
    exit 1
  fi
}

test -f "$BUG_FORM"
test -f "$FEATURE_FORM"

check_exact_line "$BUG_FORM" 'labels: ["bug"]'
check_exact_line "$FEATURE_FORM" 'labels: ["enhancement"]'

rg -q -F 'label%3A%22bug%22' "$BUG_FORM"
rg -q -F 'label%3A%22enhancement%22' "$FEATURE_FORM"

if rg -n -F \
  -e 'labels: ["Bug report"]' \
  -e 'labels: ["Feature request"]' \
  -e 'label%3A%22Bug+report%22' \
  -e 'label%3A%22Feature+request%22' \
  "$BUG_FORM" "$FEATURE_FORM"
then
  echo 'FAIL: issue forms reference retired label names' >&2
  exit 1
fi

echo 'BUG_FORM_LABEL=bug'
echo 'FEATURE_FORM_LABEL=enhancement'
echo 'RESULT=MORPHE_ISSUE_FORM_LABEL_CONTRACT_OK'
