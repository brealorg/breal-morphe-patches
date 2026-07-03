#!/usr/bin/env bash
FAIL=0
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
FINALIZER="$ROOT/tools/finalize-runtime-logcat.sh"
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

cat > "$TMP_DIR/pass.logcat.txt" <<'LOG'
07-03 15:23:01.547 D/AndroidRuntime(10647): >>>>>> START com.android.internal.os.RuntimeInit uid 2000 <<<<<<
07-03 15:23:02.426 D/AndroidRuntime(10647): Calling main entry com.android.commands.monkey.Monkey
07-03 15:23:02.659 I/AndroidRuntime(10647): VM exiting with result code 0.
LOG

cat > "$TMP_DIR/warn.logcat.txt" <<'LOG'
W/System.err: net.dean.jraw.http.NetworkException: Request returned non-successful status code: 403 Blocked
LOG

cat > "$TMP_DIR/fail.logcat.txt" <<'LOG'
E/AndroidRuntime: FATAL EXCEPTION: main
E/AndroidRuntime: Process: com.rubenmayayo.reddit, PID: 123
LOG

PASS_OUT="$TMP_DIR/pass-runtime"
mkdir -p "$PASS_OUT"

run_expect_rc pass_explicit 0 "$FINALIZER" \
  --out-dir "$PASS_OUT" \
  --logcat "$TMP_DIR/pass.logcat.txt" \
  --package com.rubenmayayo.reddit

assert_file "$PASS_OUT/runtime-finalizer-summary.txt"
assert_file "$PASS_OUT/runtime-finalizer.env"
assert_file "$PASS_OUT/runtime-classifications/pass.logcat.txt/runtime-classification.txt"
assert_grep '^RUNTIME_FINALIZER_CLASSIFICATION=PASS$' "$PASS_OUT/runtime-finalizer-summary.txt"
assert_grep '^RUNTIME_FINALIZER_CLASSIFICATION="PASS"$' "$PASS_OUT/runtime-finalizer.env"
assert_grep 'RESULT=RUNTIME_FINALIZER_OK' "$TMP_DIR/pass_explicit.stdout"

WARN_OUT="$TMP_DIR/warn-runtime"
mkdir -p "$WARN_OUT"
cp "$TMP_DIR/warn.logcat.txt" "$WARN_OUT/logcat-focused.txt"

run_expect_rc warn_autodiscover_allowed 0 "$FINALIZER" \
  --out-dir "$WARN_OUT" \
  --package com.rubenmayayo.reddit \
  --allow-warn

assert_file "$WARN_OUT/runtime-classifications/logcat-focused.txt/runtime-warning-summary.txt"
assert_grep '^RUNTIME_FINALIZER_CLASSIFICATION=WARN$' "$WARN_OUT/runtime-finalizer-summary.txt"
assert_grep 'http_or_media_error' "$WARN_OUT/runtime-classifications/logcat-focused.txt/runtime-warning-summary.txt"
assert_grep 'RESULT=RUNTIME_FINALIZER_WARN_ALLOWED' "$TMP_DIR/warn_autodiscover_allowed.stdout"

WARN_FAIL_OUT="$TMP_DIR/warn-fail-runtime"
mkdir -p "$WARN_FAIL_OUT"
cp "$TMP_DIR/warn.logcat.txt" "$WARN_FAIL_OUT/logcat.txt"

run_expect_rc warn_fail_on_warn 1 "$FINALIZER" \
  --out-dir "$WARN_FAIL_OUT" \
  --package com.rubenmayayo.reddit \
  --fail-on-warn

assert_grep '^RUNTIME_FINALIZER_CLASSIFICATION=WARN$' "$WARN_FAIL_OUT/runtime-finalizer-summary.txt"
assert_grep 'RESULT=RUNTIME_FINALIZER_WARN_FAIL_ON_WARN' "$TMP_DIR/warn_fail_on_warn.stdout"

FAIL_OUT="$TMP_DIR/fail-runtime"
mkdir -p "$FAIL_OUT"

run_expect_rc fail_explicit 1 "$FINALIZER" \
  --out-dir "$FAIL_OUT" \
  --logcat "$TMP_DIR/fail.logcat.txt" \
  --package com.rubenmayayo.reddit

assert_file "$FAIL_OUT/runtime-classifications/fail.logcat.txt/runtime-hard-blockers.txt"
assert_grep '^RUNTIME_FINALIZER_CLASSIFICATION=FAIL$' "$FAIL_OUT/runtime-finalizer-summary.txt"
assert_grep 'target_fatal_exception' "$FAIL_OUT/runtime-classifications/fail.logcat.txt/runtime-hard-blockers.txt"
assert_grep 'RESULT=RUNTIME_FINALIZER_FAIL' "$TMP_DIR/fail_explicit.stdout"

MIXED_OUT="$TMP_DIR/mixed-runtime"
mkdir -p "$MIXED_OUT"
cp "$TMP_DIR/pass.logcat.txt" "$MIXED_OUT/logcat-pass.txt"
cp "$TMP_DIR/warn.logcat.txt" "$MIXED_OUT/logcat-warn.txt"

run_expect_rc mixed_pass_warn 0 "$FINALIZER" \
  --out-dir "$MIXED_OUT" \
  --package com.rubenmayayo.reddit

assert_grep '^RUNTIME_FINALIZER_CLASSIFICATION=WARN$' "$MIXED_OUT/runtime-finalizer-summary.txt"
assert_grep '^RUNTIME_FINALIZER_LOGCAT_COUNT=2$' "$MIXED_OUT/runtime-finalizer-summary.txt"
assert_grep '^RUNTIME_FINALIZER_PASS_COUNT=1$' "$MIXED_OUT/runtime-finalizer-summary.txt"
assert_grep '^RUNTIME_FINALIZER_WARN_COUNT=1$' "$MIXED_OUT/runtime-finalizer-summary.txt"

EMPTY_OUT="$TMP_DIR/empty-runtime"
mkdir -p "$EMPTY_OUT"

run_expect_rc no_logcat 2 "$FINALIZER" --out-dir "$EMPTY_OUT"
assert_grep 'RESULT=RUNTIME_FINALIZER_NO_LOGCAT' "$TMP_DIR/no_logcat.stdout"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_PL08D_RUNTIME_FINALIZER_TEST_OK"
else
  echo "RESULT=MORPHE_PL08D_RUNTIME_FINALIZER_TEST_FAIL"
fi
exit "$FAIL"
