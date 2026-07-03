#!/usr/bin/env bash
FAIL=0
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
WRAPPER="$ROOT/tools/classify-runtime-logcat.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

assert_file() {
  local path="$1"
  [ -f "$path" ] || mark_fail "missing file: $path"
}

assert_grep() {
  local pattern="$1"
  local path="$2"
  grep -E "$pattern" "$path" >/dev/null 2>&1 || mark_fail "missing pattern $pattern in $path"
}

run_expect_rc() {
  local name="$1"
  local expected_rc="$2"
  shift 2

  echo
  echo "--- $name ---"
  set +e
  "$@" >"$TMP_DIR/$name.stdout" 2>"$TMP_DIR/$name.stderr"
  local rc=$?
  set -e

  cat "$TMP_DIR/$name.stdout"
  if [ -s "$TMP_DIR/$name.stderr" ]; then
    echo "stderr:"
    cat "$TMP_DIR/$name.stderr"
  fi

  if [ "$rc" -ne "$expected_rc" ]; then
    mark_fail "$name rc=$rc expected=$expected_rc"
  fi
}

cat > "$TMP_DIR/monkey.log" <<'LOG'
07-03 15:23:01.547 D/AndroidRuntime(10647): >>>>>> START com.android.internal.os.RuntimeInit uid 2000 <<<<<<
07-03 15:23:02.426 D/AndroidRuntime(10647): Calling main entry com.android.commands.monkey.Monkey
07-03 15:23:02.659 I/AndroidRuntime(10647): VM exiting with result code 0.
LOG

cat > "$TMP_DIR/warn.log" <<'LOG'
W/System.err: net.dean.jraw.http.NetworkException: Request returned non-successful status code: 403 Blocked
LOG

cat > "$TMP_DIR/fatal.log" <<'LOG'
E/AndroidRuntime: FATAL EXCEPTION: main
E/AndroidRuntime: Process: com.rubenmayayo.reddit, PID: 123
LOG

run_expect_rc pass_case 0 "$WRAPPER" \
  --logcat "$TMP_DIR/monkey.log" \
  --out-dir "$TMP_DIR/pass-out" \
  --package com.rubenmayayo.reddit

assert_file "$TMP_DIR/pass-out/runtime-classification.txt"
assert_file "$TMP_DIR/pass-out/runtime-classification.json"
assert_file "$TMP_DIR/pass-out/runtime-classification.env"
assert_file "$TMP_DIR/pass-out/runtime-hard-blockers.txt"
assert_file "$TMP_DIR/pass-out/runtime-warning-summary.txt"
assert_grep '^CLASSIFICATION=PASS$' "$TMP_DIR/pass-out/runtime-classification.txt"
assert_grep '^RUNTIME_CLASSIFICATION="PASS"$' "$TMP_DIR/pass-out/runtime-classification.env"
assert_grep 'RESULT=RUNTIME_LOGCAT_CLASSIFICATION_OK' "$TMP_DIR/pass_case.stdout"

run_expect_rc warn_case 0 "$WRAPPER" \
  --logcat "$TMP_DIR/warn.log" \
  --out-dir "$TMP_DIR/warn-out" \
  --package com.rubenmayayo.reddit \
  --allow-warn

assert_file "$TMP_DIR/warn-out/runtime-warning-summary.txt"
assert_grep '^CLASSIFICATION=WARN$' "$TMP_DIR/warn-out/runtime-classification.txt"
assert_grep 'http_or_media_error' "$TMP_DIR/warn-out/runtime-warning-summary.txt"
assert_grep 'RESULT=RUNTIME_LOGCAT_CLASSIFICATION_WARN_ALLOWED' "$TMP_DIR/warn_case.stdout"

run_expect_rc warn_fail_on_warn_case 1 "$WRAPPER" \
  --logcat "$TMP_DIR/warn.log" \
  --out-dir "$TMP_DIR/warn-fail-out" \
  --package com.rubenmayayo.reddit \
  --fail-on-warn

assert_grep 'RESULT=RUNTIME_LOGCAT_CLASSIFICATION_WARN_FAIL_ON_WARN' "$TMP_DIR/warn_fail_on_warn_case.stdout"

run_expect_rc fail_case 1 "$WRAPPER" \
  --logcat "$TMP_DIR/fatal.log" \
  --out-dir "$TMP_DIR/fail-out" \
  --package com.rubenmayayo.reddit

assert_file "$TMP_DIR/fail-out/runtime-hard-blockers.txt"
assert_grep '^CLASSIFICATION=FAIL$' "$TMP_DIR/fail-out/runtime-classification.txt"
assert_grep 'target_fatal_exception' "$TMP_DIR/fail-out/runtime-hard-blockers.txt"
assert_grep 'RESULT=RUNTIME_LOGCAT_CLASSIFICATION_FAIL' "$TMP_DIR/fail_case.stdout"

run_expect_rc missing_args 2 "$WRAPPER" --logcat "$TMP_DIR/monkey.log"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_PL08C_RUNTIME_CLASSIFIER_WRAPPER_TEST_OK"
else
  echo "RESULT=MORPHE_PL08C_RUNTIME_CLASSIFIER_WRAPPER_TEST_FAIL"
fi
exit "$FAIL"
