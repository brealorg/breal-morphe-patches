#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 -m py_compile \
  tools/morphe-flow.py \
  tools/morphe_flow_operations.py
python3 -m unittest -q \
  tests/tools/test_morphe_flow.py \
  tests/tools/test_morphe_flow_main_sync.py \
  tests/tools/test_morphe_flow_operations_source_contract.py \
  tests/tools/test_morphe_flow_source_contract.py

echo 'MORPHE_FLOW_READ_ONLY_CONTRACT=PASS'
echo 'MORPHE_FLOW_INTEGRATION_TESTS=PASS'
echo 'MORPHE_FLOW_OPERATION_RECEIPTS=PASS'
echo 'RESULT=MORPHE_FLOW_CONTRACT_OK'
