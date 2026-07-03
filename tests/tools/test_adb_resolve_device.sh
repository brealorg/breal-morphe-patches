#!/usr/bin/env bash
FAIL=0
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SCRIPT="$ROOT/tools/adb-resolve-device.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

run_ok() {
  local name="$1"
  shift
  echo
  echo "--- OK: $name ---"
  env -u ANDROID_SERIAL "$@" >"$TMP_DIR/$name.out" 2>"$TMP_DIR/$name.err"
  local rc=$?
  cat "$TMP_DIR/$name.out"
  if [ -s "$TMP_DIR/$name.err" ]; then
    echo "stderr:"
    cat "$TMP_DIR/$name.err"
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
  env -u ANDROID_SERIAL "$@" >"$TMP_DIR/$name.out" 2>"$TMP_DIR/$name.err"
  local rc=$?
  cat "$TMP_DIR/$name.out"
  if [ -s "$TMP_DIR/$name.err" ]; then
    echo "stderr:"
    cat "$TMP_DIR/$name.err"
  fi
  if [ "$rc" -eq 0 ]; then
    mark_fail "$name returned 0, expected failure"
  fi
}

assert_grep() {
  local pattern="$1"
  local file="$2"
  grep -E "$pattern" "$file" >/dev/null 2>&1 || mark_fail "missing pattern $pattern in $file"
}

cat >"$TMP_DIR/one_tcp.txt" <<'EOF'
List of devices attached
adb-19021FDF600GBN-4kva4S._adb-tls-connect._tcp device product:oriole_beta model:Pixel_6 device:oriole transport_id:8
EOF

cat >"$TMP_DIR/one_usb.txt" <<'EOF'
List of devices attached
19021FDF600GBN device product:oriole_beta model:Pixel_6 device:oriole transport_id:9
EOF

cat >"$TMP_DIR/two_devices.txt" <<'EOF'
List of devices attached
19021FDF600GBN device product:oriole_beta model:Pixel_6 device:oriole transport_id:9
192.168.1.248:35169 device product:oriole_beta model:Pixel_6 device:oriole transport_id:10
EOF

cat >"$TMP_DIR/offline_only.txt" <<'EOF'
List of devices attached
19021FDF600GBN offline product:oriole_beta model:Pixel_6 device:oriole transport_id:9
EOF

cat >"$TMP_DIR/none.txt" <<'EOF'
List of devices attached

EOF

run_ok one_tcp_shell "$SCRIPT" --devices-file "$TMP_DIR/one_tcp.txt" --format shell
assert_grep "^ADB_SERIAL='adb-19021FDF600GBN-4kva4S._adb-tls-connect._tcp'$" "$TMP_DIR/one_tcp_shell.out"
assert_grep "^ADB_TRANSPORT='tcp'$" "$TMP_DIR/one_tcp_shell.out"
assert_grep "^ADB_MODEL='Pixel_6'$" "$TMP_DIR/one_tcp_shell.out"

run_ok one_usb_json "$SCRIPT" --devices-file "$TMP_DIR/one_usb.txt" --format json
python3 - "$TMP_DIR/one_usb_json.out" <<'PY' || mark_fail "json parse failed"
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
assert data["serial"] == "19021FDF600GBN"
assert data["transport"] == "usb"
assert data["model"] == "Pixel_6"
PY

run_fail two_devices_ambiguous "$SCRIPT" --devices-file "$TMP_DIR/two_devices.txt"
assert_grep "more than one adb device matched" "$TMP_DIR/two_devices_ambiguous.err"

run_ok two_devices_prefer_tcp "$SCRIPT" --devices-file "$TMP_DIR/two_devices.txt" --prefer-tcp --format plain
assert_grep "^ADB_SERIAL=192.168.1.248:35169$" "$TMP_DIR/two_devices_prefer_tcp.out"
assert_grep "^ADB_TRANSPORT=tcp$" "$TMP_DIR/two_devices_prefer_tcp.out"

run_ok two_devices_serial "$SCRIPT" --devices-file "$TMP_DIR/two_devices.txt" --serial 19021FDF600GBN --format plain
assert_grep "^ADB_SERIAL=19021FDF600GBN$" "$TMP_DIR/two_devices_serial.out"
assert_grep "^ADB_TRANSPORT=usb$" "$TMP_DIR/two_devices_serial.out"

run_fail offline_rejected "$SCRIPT" --devices-file "$TMP_DIR/offline_only.txt"
assert_grep "no usable adb devices" "$TMP_DIR/offline_rejected.err"

run_ok offline_allowed "$SCRIPT" --devices-file "$TMP_DIR/offline_only.txt" --allow-offline --format plain
assert_grep "^ADB_STATE=offline$" "$TMP_DIR/offline_allowed.out"

run_fail none "$SCRIPT" --devices-file "$TMP_DIR/none.txt"
assert_grep "no adb devices listed" "$TMP_DIR/none.err"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_PL08A_ADB_RESOLVE_DEVICE_TEST_OK"
else
  echo "RESULT=MORPHE_PL08A_ADB_RESOLVE_DEVICE_TEST_FAIL"
fi
exit "$FAIL"
