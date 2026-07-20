#!/usr/bin/env bash

set -u

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
FAIL=0

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

require_text() {
  local file="$1"
  local text="$2"

  grep -F -- "$text" "$file" >/dev/null 2>&1 ||
    mark_fail "$file missing: $text"
}

forbid_pattern() {
  local file="$1"
  local pattern="$2"

  if grep -E -- "$pattern" "$file" >/dev/null 2>&1; then
    mark_fail "$file retains direct adb resolver pattern: $pattern"
  fi
}

CONSUMERS=(
  tools/boost-open-by-default.sh
  tools/boost-dev-inline-media-size-runtime.sh
  tools/boost-dev-inline-preview-source-toggle-runtime.sh
  tools/boost-dev-issue-runtime.sh
)

for relative in "${CONSUMERS[@]}"; do
  file="$ROOT/$relative"
  echo "CHECK=$relative"

  bash -n "$file" || mark_fail "$relative syntax"
  require_text "$file" 'boost-adb-serial.sh'
  require_text "$file" '--hint "${MORPHE_ADB_HINT:-192.168.1.248}"'
  require_text "$file" '--expect-model "${MORPHE_ADB_EXPECT_MODEL:-Pixel_6}"'
  require_text "$file" '--expect-device "${MORPHE_ADB_EXPECT_DEVICE:-oriole}"'

  forbid_pattern "$file" 'choose_serial[[:space:]]*\('
  forbid_pattern "$file" 'adb[[:space:]]+devices([[:space:]]|$)'
  forbid_pattern "$file" 'adb[[:space:]]+mdns[[:space:]]+services'
  forbid_pattern "$file" 'adb[[:space:]]+connect([[:space:]]|$)'
done

require_text "$ROOT/tools/boost-dev-from-mpp.sh" '--serial SERIAL'
require_text "$ROOT/tools/boost-dev-runtime-capture.sh" '--serial SERIAL'

echo
if [ "$FAIL" -eq 0 ]; then
  echo 'RESULT=MORPHE_HARDENING_V21_ADB_RUNTIME_CONSUMERS_OK'
else
  echo 'RESULT=MORPHE_HARDENING_V21_ADB_RUNTIME_CONSUMERS_FAIL'
fi
exit "$FAIL"
