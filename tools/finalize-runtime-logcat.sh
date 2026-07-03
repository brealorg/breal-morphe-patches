#!/usr/bin/env bash

# Canonical runtime logcat finalizer.
# Read-only with respect to adb/device/app state: it does not call adb, install,
# uninstall, clear data, force-stop, or launch anything. It only consumes existing
# logcat files and writes finalizer artifacts into the runtime output directory.

set -u

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WRAPPER="$ROOT/tools/classify-runtime-logcat.sh"

OUT_DIR=""
LOGCATS=()
PACKAGE_ARGS=()
EXPECT_ACTIVITY_ARGS=()
HARD_PATTERN_ARGS=()
REQUIRE_PATTERN_ARGS=()
FAIL_ON_WARN=0
ALLOW_WARN=0

usage() {
  cat <<'USAGE'
Usage:
  tools/finalize-runtime-logcat.sh --out-dir DIR [options]

Options:
  --out-dir DIR              Runtime artifact directory to finalize.
  --logcat PATH              Existing logcat file to classify. Repeatable.
                             If omitted, immediate files in --out-dir matching
                             *logcat*.txt or logcat* are auto-discovered.
  --package PACKAGE          Target package/process context. Repeatable.
  --expect-activity NAME     Expected activity marker. Repeatable.
  --activity NAME            Alias for --expect-activity.
  --hard-pattern TEXT        Hard-blocker substring. Repeatable.
  --pattern TEXT             Alias for --hard-pattern.
  --require-pattern TEXT     Warn if substring is missing. Repeatable.
  --allow-warn               Explicitly document that WARN exits zero.
  --fail-on-warn             Return nonzero for WARN as well as FAIL.
  -h, --help                 Show this help.

Output files in --out-dir:
  runtime-finalizer-summary.txt
  runtime-finalizer.env
  runtime-classifications/<logcat-file>/runtime-classification.txt
  runtime-classifications/<logcat-file>/runtime-classification.json
  runtime-classifications/<logcat-file>/runtime-hard-blockers.txt
  runtime-classifications/<logcat-file>/runtime-warning-summary.txt

Exit:
  0 when aggregate classification is PASS/WARN/UNKNOWN, unless --fail-on-warn.
  1 when aggregate classification is FAIL, or WARN with --fail-on-warn.
  2 for usage/tool errors.

Intended use:
  Run this at the end of runtime scripts/finalizers after logcat capture.
USAGE
}

die_usage() {
  echo "FAIL: $*" >&2
  exit 2
}

need_value() {
  [ "${2:-}" ] || die_usage "$1 requires a value"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out-dir)
      need_value "$1" "${2:-}"
      OUT_DIR="$2"
      shift 2
      ;;
    --logcat)
      need_value "$1" "${2:-}"
      LOGCATS+=("$2")
      shift 2
      ;;
    --package)
      need_value "$1" "${2:-}"
      PACKAGE_ARGS+=("--package" "$2")
      shift 2
      ;;
    --expect-activity|--activity)
      need_value "$1" "${2:-}"
      EXPECT_ACTIVITY_ARGS+=("--expect-activity" "$2")
      shift 2
      ;;
    --hard-pattern|--pattern)
      need_value "$1" "${2:-}"
      HARD_PATTERN_ARGS+=("--hard-pattern" "$2")
      shift 2
      ;;
    --require-pattern)
      need_value "$1" "${2:-}"
      REQUIRE_PATTERN_ARGS+=("--require-pattern" "$2")
      shift 2
      ;;
    --allow-warn)
      ALLOW_WARN=1
      shift
      ;;
    --fail-on-warn)
      FAIL_ON_WARN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die_usage "unknown argument: $1"
      ;;
  esac
done

[ -n "$OUT_DIR" ] || die_usage "--out-dir is required"
[ -x "$WRAPPER" ] || die_usage "runtime logcat wrapper missing or not executable: $WRAPPER"

mkdir -p "$OUT_DIR" || die_usage "could not create out dir: $OUT_DIR"

if [ "${#LOGCATS[@]}" -eq 0 ]; then
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    LOGCATS+=("$path")
  done < <(
    find "$OUT_DIR" -maxdepth 1 -type f \( -iname '*logcat*.txt' -o -iname 'logcat*' \) \
      ! -iname 'runtime-classification*.txt' \
      ! -iname 'runtime-finalizer*.txt' \
      | sort
  )
fi

SUMMARY="$OUT_DIR/runtime-finalizer-summary.txt"
ENV_OUT="$OUT_DIR/runtime-finalizer.env"
CLASS_ROOT="$OUT_DIR/runtime-classifications"
mkdir -p "$CLASS_ROOT" || die_usage "could not create classification dir: $CLASS_ROOT"

if [ "${#LOGCATS[@]}" -eq 0 ]; then
  {
    echo "RUNTIME_FINALIZER_CLASSIFICATION=UNKNOWN"
    echo "RUNTIME_FINALIZER_LOGCAT_COUNT=0"
    echo "RUNTIME_FINALIZER_FAIL_COUNT=0"
    echo "RUNTIME_FINALIZER_WARN_COUNT=0"
    echo "RUNTIME_FINALIZER_UNKNOWN_COUNT=1"
    echo
    echo "No logcat files supplied or auto-discovered."
  } > "$SUMMARY"
  {
    echo 'RUNTIME_FINALIZER_CLASSIFICATION="UNKNOWN"'
    echo 'RUNTIME_FINALIZER_LOGCAT_COUNT="0"'
    echo 'RUNTIME_FINALIZER_FAIL_COUNT="0"'
    echo 'RUNTIME_FINALIZER_WARN_COUNT="0"'
    echo 'RUNTIME_FINALIZER_UNKNOWN_COUNT="1"'
  } > "$ENV_OUT"
  cat "$SUMMARY"
  echo "RESULT=RUNTIME_FINALIZER_NO_LOGCAT"
  exit 2
fi

sanitize_name() {
  basename -- "$1" | sed -E 's/[^A-Za-z0-9._-]+/_/g'
}

FAIL_COUNT=0
WARN_COUNT=0
PASS_COUNT=0
UNKNOWN_COUNT=0
TOOL_FAIL=0
TOTAL=0

TMP_SUMMARY="$(mktemp)"
trap 'rm -f "$TMP_SUMMARY"' EXIT

for logcat in "${LOGCATS[@]}"; do
  TOTAL=$((TOTAL + 1))

  if [ ! -r "$logcat" ]; then
    echo "===== $logcat =====" >> "$TMP_SUMMARY"
    echo "WRAPPER_RC=2" >> "$TMP_SUMMARY"
    echo "CLASSIFICATION=TOOL_FAIL" >> "$TMP_SUMMARY"
    echo "FAIL: logcat file missing or unreadable" >> "$TMP_SUMMARY"
    echo >> "$TMP_SUMMARY"
    TOOL_FAIL=1
    continue
  fi

  SAFE="$(sanitize_name "$logcat")"
  CLASS_OUT="$CLASS_ROOT/$SAFE"
  mkdir -p "$CLASS_OUT" || {
    echo "===== $logcat =====" >> "$TMP_SUMMARY"
    echo "WRAPPER_RC=2" >> "$TMP_SUMMARY"
    echo "CLASSIFICATION=TOOL_FAIL" >> "$TMP_SUMMARY"
    echo "FAIL: could not create classification out dir: $CLASS_OUT" >> "$TMP_SUMMARY"
    echo >> "$TMP_SUMMARY"
    TOOL_FAIL=1
    continue
  }

  WRAPPER_ARGS=(
    "--logcat" "$logcat"
    "--out-dir" "$CLASS_OUT"
    "${PACKAGE_ARGS[@]}"
    "${EXPECT_ACTIVITY_ARGS[@]}"
    "${HARD_PATTERN_ARGS[@]}"
    "${REQUIRE_PATTERN_ARGS[@]}"
  )

  if [ "$ALLOW_WARN" -eq 1 ]; then
    WRAPPER_ARGS+=("--allow-warn")
  fi
  if [ "$FAIL_ON_WARN" -eq 1 ]; then
    WRAPPER_ARGS+=("--fail-on-warn")
  fi

  set +e
  "$WRAPPER" "${WRAPPER_ARGS[@]}" > "$CLASS_OUT/wrapper.stdout.txt" 2> "$CLASS_OUT/wrapper.stderr.txt"
  WRAPPER_RC=$?
  set -e

  TEXT_OUT="$CLASS_OUT/runtime-classification.txt"
  HARD_OUT="$CLASS_OUT/runtime-hard-blockers.txt"
  WARN_OUT="$CLASS_OUT/runtime-warning-summary.txt"

  CLASSIFICATION=""
  if [ -s "$TEXT_OUT" ]; then
    CLASSIFICATION="$(grep -E '^CLASSIFICATION=' "$TEXT_OUT" | head -n 1 | cut -d= -f2-)"
  fi

  if [ -z "$CLASSIFICATION" ]; then
    CLASSIFICATION="TOOL_FAIL"
    TOOL_FAIL=1
  fi

  case "$CLASSIFICATION" in
    PASS)
      PASS_COUNT=$((PASS_COUNT + 1))
      ;;
    WARN)
      WARN_COUNT=$((WARN_COUNT + 1))
      ;;
    FAIL)
      FAIL_COUNT=$((FAIL_COUNT + 1))
      ;;
    UNKNOWN)
      UNKNOWN_COUNT=$((UNKNOWN_COUNT + 1))
      ;;
    *)
      TOOL_FAIL=1
      ;;
  esac

  {
    echo "===== $logcat ====="
    echo "CLASSIFICATION=$CLASSIFICATION"
    echo "WRAPPER_RC=$WRAPPER_RC"
    echo "CLASSIFICATION_DIR=$CLASS_OUT"
    if [ -s "$TEXT_OUT" ]; then
      grep -E '^(CLASSIFICATION|HARD_BLOCKERS|WARNINGS|APP_CRASH|ANDROID_RUNTIME|FATAL_EXCEPTION|RELEVANT_LINES|EXPECTED_ACTIVITY_SEEN)=' "$TEXT_OUT" || true
    fi
    if [ -s "$HARD_OUT" ]; then
      echo
      echo "--- hard blockers ---"
      sed -n '1,80p' "$HARD_OUT"
    fi
    if [ -s "$WARN_OUT" ]; then
      echo
      echo "--- warnings ---"
      sed -n '1,80p' "$WARN_OUT"
    fi
    if [ -s "$CLASS_OUT/wrapper.stderr.txt" ]; then
      echo
      echo "--- wrapper stderr ---"
      sed -n '1,80p' "$CLASS_OUT/wrapper.stderr.txt"
    fi
    echo
  } >> "$TMP_SUMMARY"
done

AGGREGATE="PASS"
if [ "$TOOL_FAIL" -ne 0 ]; then
  AGGREGATE="TOOL_FAIL"
elif [ "$FAIL_COUNT" -gt 0 ]; then
  AGGREGATE="FAIL"
elif [ "$WARN_COUNT" -gt 0 ]; then
  AGGREGATE="WARN"
elif [ "$UNKNOWN_COUNT" -gt 0 ] && [ "$PASS_COUNT" -eq 0 ]; then
  AGGREGATE="UNKNOWN"
elif [ "$UNKNOWN_COUNT" -gt 0 ]; then
  AGGREGATE="WARN"
fi

{
  echo "RUNTIME_FINALIZER_CLASSIFICATION=$AGGREGATE"
  echo "RUNTIME_FINALIZER_LOGCAT_COUNT=$TOTAL"
  echo "RUNTIME_FINALIZER_PASS_COUNT=$PASS_COUNT"
  echo "RUNTIME_FINALIZER_WARN_COUNT=$WARN_COUNT"
  echo "RUNTIME_FINALIZER_FAIL_COUNT=$FAIL_COUNT"
  echo "RUNTIME_FINALIZER_UNKNOWN_COUNT=$UNKNOWN_COUNT"
  echo
  cat "$TMP_SUMMARY"
} > "$SUMMARY"

{
  echo "RUNTIME_FINALIZER_CLASSIFICATION=\"$AGGREGATE\""
  echo "RUNTIME_FINALIZER_LOGCAT_COUNT=\"$TOTAL\""
  echo "RUNTIME_FINALIZER_PASS_COUNT=\"$PASS_COUNT\""
  echo "RUNTIME_FINALIZER_WARN_COUNT=\"$WARN_COUNT\""
  echo "RUNTIME_FINALIZER_FAIL_COUNT=\"$FAIL_COUNT\""
  echo "RUNTIME_FINALIZER_UNKNOWN_COUNT=\"$UNKNOWN_COUNT\""
} > "$ENV_OUT"

cat "$SUMMARY"
echo
echo "===== runtime finalizer files ====="
printf 'SUMMARY=%s\n' "$SUMMARY"
printf 'ENV=%s\n' "$ENV_OUT"
printf 'CLASSIFICATIONS=%s\n' "$CLASS_ROOT"

echo
echo "RUNTIME_FINALIZER_WRAPPER_CLASSIFICATION=$AGGREGATE"
echo "RUNTIME_FINALIZER_WRAPPER_LOGCAT_COUNT=$TOTAL"
echo "RUNTIME_FINALIZER_WRAPPER_FAIL_COUNT=$FAIL_COUNT"
echo "RUNTIME_FINALIZER_WRAPPER_WARN_COUNT=$WARN_COUNT"

if [ "$AGGREGATE" = "TOOL_FAIL" ]; then
  echo "RESULT=RUNTIME_FINALIZER_TOOL_FAIL"
  exit 2
fi

if [ "$AGGREGATE" = "FAIL" ]; then
  echo "RESULT=RUNTIME_FINALIZER_FAIL"
  exit 1
fi

if [ "$AGGREGATE" = "WARN" ] && [ "$FAIL_ON_WARN" -eq 1 ]; then
  echo "RESULT=RUNTIME_FINALIZER_WARN_FAIL_ON_WARN"
  exit 1
fi

if [ "$AGGREGATE" = "WARN" ] && [ "$ALLOW_WARN" -eq 1 ]; then
  echo "RESULT=RUNTIME_FINALIZER_WARN_ALLOWED"
  exit 0
fi

if [ "$AGGREGATE" = "WARN" ]; then
  echo "RESULT=RUNTIME_FINALIZER_WARN"
  exit 0
fi

if [ "$AGGREGATE" = "UNKNOWN" ]; then
  echo "RESULT=RUNTIME_FINALIZER_UNKNOWN"
  exit 0
fi

echo "RESULT=RUNTIME_FINALIZER_OK"
exit 0
