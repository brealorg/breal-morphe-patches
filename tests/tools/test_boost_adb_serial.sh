#!/usr/bin/env bash

set -u

FAIL=0
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SCRIPT="$ROOT/tools/boost-adb-serial.sh"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"

cleanup() {
  rm -rf -- "$TMP_DIR"
}
trap cleanup EXIT

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

assert_grep() {
  local pattern="$1"
  local file="$2"

  grep -E "$pattern" "$file" >/dev/null 2>&1 ||
    mark_fail "missing pattern $pattern in $file"
}

assert_absent() {
  local path="$1"

  if [ -e "$path" ]; then
    mark_fail "unexpected path exists: $path"
  fi
}

new_case() {
  CASE_DIR="$TMP_DIR/$1"
  mkdir -p "$CASE_DIR"
  : >"$CASE_DIR/mdns.txt"
}

run_ok() {
  local name="$1"
  shift

  echo
  echo "--- OK: $name ---"
  env \
    -u ANDROID_SERIAL \
    -u MORPHE_ADB_SERIAL \
    -u MORPHE_ADB_HINT \
    -u MORPHE_ADB_EXPECT_MODEL \
    -u MORPHE_ADB_EXPECT_DEVICE \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_ADB_STATE_DIR="$CASE_DIR" \
    "$@" \
    "$SCRIPT" >"$CASE_DIR/out" 2>"$CASE_DIR/err"
  local rc=$?

  cat "$CASE_DIR/out"
  if [ -s "$CASE_DIR/err" ]; then
    echo "stderr:"
    cat "$CASE_DIR/err"
  fi
  if [ "$rc" -ne 0 ]; then
    mark_fail "$name returned $rc, expected 0"
  fi
}

run_fail() {
  local name="$1"
  shift

  echo
  echo "--- FAIL expected: $name ---"
  env \
    -u ANDROID_SERIAL \
    -u MORPHE_ADB_SERIAL \
    -u MORPHE_ADB_HINT \
    -u MORPHE_ADB_EXPECT_MODEL \
    -u MORPHE_ADB_EXPECT_DEVICE \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_ADB_STATE_DIR="$CASE_DIR" \
    "$@" \
    "$SCRIPT" >"$CASE_DIR/out" 2>"$CASE_DIR/err"
  local rc=$?

  cat "$CASE_DIR/out"
  if [ -s "$CASE_DIR/err" ]; then
    echo "stderr:"
    cat "$CASE_DIR/err"
  fi
  if [ "$rc" -eq 0 ]; then
    mark_fail "$name returned 0, expected failure"
  fi
}

mkdir -p "$FAKE_BIN"

cat >"$FAKE_BIN/adb" <<'FAKE_ADB'
#!/usr/bin/env bash
set -u

STATE_DIR="${FAKE_ADB_STATE_DIR:?}"

case "${1:-} ${2:-}" in
  "devices -l")
    if [ -f "$STATE_DIR/connected" ] &&
       [ -f "$STATE_DIR/devices-after.txt" ]; then
      cat "$STATE_DIR/devices-after.txt"
    else
      cat "$STATE_DIR/devices-before.txt"
    fi
    ;;
  "mdns services")
    cat "$STATE_DIR/mdns.txt"
    ;;
  "connect "*)
    printf '%s\n' "${2:-}" >>"$STATE_DIR/connect.log"
    : >"$STATE_DIR/connected"
    printf 'connected to %s\n' "${2:-}"
    ;;
  *)
    echo "unexpected fake adb invocation: $*" >&2
    exit 90
    ;;
esac
FAKE_ADB

cat >"$FAKE_BIN/sleep" <<'FAKE_SLEEP'
#!/usr/bin/env bash
exit 0
FAKE_SLEEP

chmod +x "$FAKE_BIN/adb" "$FAKE_BIN/sleep"

new_case connected_concrete
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
run_ok connected_concrete \
  ANDROID_SERIAL=192.168.1.248:5555 \
  MORPHE_ADB_EXPECT_MODEL=Pixel_6 \
  MORPHE_ADB_EXPECT_DEVICE=oriole
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/out"
assert_grep 'ignoring stale-prone ANDROID_SERIAL=192\.168\.1\.248:5555' "$CASE_DIR/err"
assert_absent "$CASE_DIR/connect.log"

new_case mdns_reconnect
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached

EOF
cat >"$CASE_DIR/devices-after.txt" <<'EOF'
List of devices attached
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
cat >"$CASE_DIR/mdns.txt" <<'EOF'
adb-19021FDF600GBN-4kva4S _adb-tls-connect._tcp 192.168.1.248:35999
EOF
run_ok mdns_reconnect \
  MORPHE_ADB_HINT=192.168.1.248 \
  MORPHE_ADB_EXPECT_MODEL=Pixel_6 \
  MORPHE_ADB_EXPECT_DEVICE=oriole
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/out"
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/connect.log"

new_case alias_and_concrete
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
adb-19021FDF600GBN-4kva4S._adb-tls-connect._tcp device product:oriole_beta model:Pixel_6 device:oriole transport_id:77
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
run_ok alias_and_concrete
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/out"
assert_absent "$CASE_DIR/connect.log"

new_case hinted_multiple
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
192.168.1.128:5555 device product:rk3588 model:Rock_5A device:rockchip transport_id:70
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
run_ok hinted_multiple MORPHE_ADB_HINT=192.168.1.248
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/out"

new_case shield_connected_pixel_mdns
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
192.168.1.128:5555 device product:mdarcy model:SHIELD_Android_TV device:mdarcy transport_id:70
EOF
cat >"$CASE_DIR/devices-after.txt" <<'EOF'
List of devices attached
192.168.1.128:5555 device product:mdarcy model:SHIELD_Android_TV device:mdarcy transport_id:70
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
cat >"$CASE_DIR/mdns.txt" <<'EOF'
adb-19021FDF600GBN-4kva4S _adb-tls-connect._tcp 192.168.1.248:35999
EOF
run_ok shield_connected_pixel_mdns \
  MORPHE_ADB_HINT=192.168.1.248 \
  MORPHE_ADB_EXPECT_MODEL=Pixel_6 \
  MORPHE_ADB_EXPECT_DEVICE=oriole
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/out"
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/connect.log"

new_case wrong_identity_at_hinted_endpoint
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
192.168.1.248:35999 device product:mdarcy model:SHIELD_Android_TV device:mdarcy transport_id:70
EOF
run_fail wrong_identity_at_hinted_endpoint \
  MORPHE_ADB_HINT=192.168.1.248 \
  MORPHE_ADB_EXPECT_MODEL=Pixel_6 \
  MORPHE_ADB_EXPECT_DEVICE=oriole
assert_grep 'could not resolve a unique current adb target' "$CASE_DIR/err"

new_case ambiguous_multiple
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
192.168.1.128:5555 device product:rk3588 model:Rock_5A device:rockchip transport_id:70
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
run_fail ambiguous_multiple
assert_grep 'could not resolve a unique current adb target' "$CASE_DIR/err"

new_case stale_override
cat >"$CASE_DIR/devices-before.txt" <<'EOF'
List of devices attached
192.168.1.248:35999 device product:oriole_beta model:Pixel_6 device:oriole transport_id:78
EOF
run_ok stale_override MORPHE_ADB_SERIAL=192.168.1.248:5555
assert_grep '^192\.168\.1\.248:35999$' "$CASE_DIR/out"
assert_grep 'MORPHE_ADB_SERIAL not connected, ignoring' "$CASE_DIR/err"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_HARDENING_V21_BOOST_ADB_SERIAL_CONTRACT_OK"
else
  echo "RESULT=MORPHE_HARDENING_V21_BOOST_ADB_SERIAL_CONTRACT_FAIL"
fi
exit "$FAIL"
