#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v rg >/dev/null 2>&1; then
  echo 'FAIL: project contracts require ripgrep (rg)' >&2
  echo 'RESULT=MORPHE_PROJECT_CONTRACTS_FAIL'
  exit 127
fi

mapfile -t SHELL_CONTRACTS < <(
  find tools \
    -maxdepth 1 \
    -type f \
    -name 'check-*-contract.sh' \
    -print |
    sort
)

mapfile -t PYTHON_SOURCE_CONTRACTS < <(
  find tests/tools \
    -maxdepth 1 \
    -type f \
    -name 'test_*_source_contract.py' \
    -print |
    sort
)

test "${#SHELL_CONTRACTS[@]}" -gt 0

for contract in "${SHELL_CONTRACTS[@]}"; do
  echo
  echo "===== $contract ====="
  bash "$contract"
done

for contract in "${PYTHON_SOURCE_CONTRACTS[@]}"; do
  echo
  echo "===== $contract ====="
  python3 -m unittest -q "$contract"
  echo "SOURCE_CONTRACT=PASS path=$contract"
done

echo
echo "SHELL_CONTRACT_COUNT=${#SHELL_CONTRACTS[@]}"
echo "PYTHON_SOURCE_CONTRACT_COUNT=${#PYTHON_SOURCE_CONTRACTS[@]}"
echo 'RESULT=MORPHE_PROJECT_CONTRACTS_OK'
