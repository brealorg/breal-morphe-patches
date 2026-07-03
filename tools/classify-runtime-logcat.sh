#!/usr/bin/env bash

# Write standard runtime logcat classification artifacts.
# Read-only wrapper: does not call adb, install APKs, mutate app data, or touch
# device state. It only reads an existing logcat file and writes classification
# summaries into an output directory.

set -u

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
CLASSIFIER="$ROOT/tools/classify-logcat-runtime.py"

LOGCAT=""
OUT_DIR=""
PACKAGE_ARGS=()
EXPECT_ACTIVITY_ARGS=()
HARD_PATTERN_ARGS=()
REQUIRE_PATTERN_ARGS=()
FAIL_ON_WARN=0
ALLOW_WARN=0

usage() {
  cat <<'USAGE'
Usage:
  tools/classify-runtime-logcat.sh --logcat PATH --out-dir DIR [options]

Options:
  --logcat PATH              Existing logcat file to classify.
  --out-dir DIR              Directory for runtime classification outputs.
  --package PACKAGE          Target package/process context. Repeatable.
  --expect-activity NAME     Expected activity marker. Repeatable.
  --activity NAME            Alias for --expect-activity.
  --hard-pattern TEXT        Hard-blocker substring. Repeatable.
  --pattern TEXT             Alias for --hard-pattern.
  --require-pattern TEXT     Warn if substring is missing. Repeatable.
  --fail-on-warn             Return nonzero for WARN as well as FAIL.
  --allow-warn               Explicitly document that WARN exits zero.
  -h, --help                 Show this help.

Output files:
  runtime-classification.txt
  runtime-classification.json
  runtime-hard-blockers.txt
  runtime-warning-summary.txt
  runtime-classification.env

Exit:
  0 when classifier returns PASS/WARN/UNKNOWN, unless --fail-on-warn is used.
  1 when classifier returns FAIL, or WARN with --fail-on-warn.
  2 for usage/tool errors.

This wrapper is intended for runtime scripts/finalizers after logcat capture.
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
    --logcat)
      need_value "$1" "${2:-}"
      LOGCAT="$2"
      shift 2
      ;;
    --out-dir)
      need_value "$1" "${2:-}"
      OUT_DIR="$2"
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
    --fail-on-warn)
      FAIL_ON_WARN=1
      shift
      ;;
    --allow-warn)
      ALLOW_WARN=1
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

[ -n "$LOGCAT" ] || die_usage "--logcat is required"
[ -n "$OUT_DIR" ] || die_usage "--out-dir is required"
[ -r "$LOGCAT" ] || die_usage "logcat file missing or unreadable: $LOGCAT"
[ -x "$CLASSIFIER" ] || die_usage "classifier missing or not executable: $CLASSIFIER"

mkdir -p "$OUT_DIR" || die_usage "could not create out dir: $OUT_DIR"

TEXT_OUT="$OUT_DIR/runtime-classification.txt"
JSON_OUT="$OUT_DIR/runtime-classification.json"
ENV_OUT="$OUT_DIR/runtime-classification.env"
HARD_OUT="$OUT_DIR/runtime-hard-blockers.txt"
WARN_OUT="$OUT_DIR/runtime-warning-summary.txt"

CLASSIFIER_ARGS=(
  "--logcat" "$LOGCAT"
  "${PACKAGE_ARGS[@]}"
  "${EXPECT_ACTIVITY_ARGS[@]}"
  "${HARD_PATTERN_ARGS[@]}"
  "${REQUIRE_PATTERN_ARGS[@]}"
)

if [ "$FAIL_ON_WARN" -eq 1 ]; then
  CLASSIFIER_ARGS+=("--fail-on-warn")
fi

set +e
"$CLASSIFIER" "${CLASSIFIER_ARGS[@]}" --format text > "$TEXT_OUT" 2>"$OUT_DIR/runtime-classification.stderr.txt"
TEXT_RC=$?
"$CLASSIFIER" "${CLASSIFIER_ARGS[@]}" --format json > "$JSON_OUT" 2>"$OUT_DIR/runtime-classification-json.stderr.txt"
JSON_RC=$?
set -e

# The classifier intentionally returns 1 for runtime FAIL, and also for WARN
# when --fail-on-warn is used. That is classification data, not a wrapper/tool
# failure. Only exit codes above 1 mean usage/input/tool failure.
if [ "$TEXT_RC" -gt 1 ] || [ "$JSON_RC" -gt 1 ]; then
  echo "FAIL: classifier tool/usage failure" >&2
  echo "TEXT_RC=$TEXT_RC JSON_RC=$JSON_RC" >&2
  cat "$OUT_DIR/runtime-classification.stderr.txt" >&2 || true
  cat "$OUT_DIR/runtime-classification-json.stderr.txt" >&2 || true
  exit 2
fi

if [ ! -s "$TEXT_OUT" ] || [ ! -s "$JSON_OUT" ]; then
  echo "FAIL: classifier did not produce expected output files" >&2
  echo "TEXT_RC=$TEXT_RC JSON_RC=$JSON_RC" >&2
  cat "$OUT_DIR/runtime-classification.stderr.txt" >&2 || true
  cat "$OUT_DIR/runtime-classification-json.stderr.txt" >&2 || true
  exit 2
fi

python3 - "$JSON_OUT" "$ENV_OUT" "$HARD_OUT" "$WARN_OUT" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
env_path = Path(sys.argv[2])
hard_path = Path(sys.argv[3])
warn_path = Path(sys.argv[4])

data = json.loads(json_path.read_text(encoding="utf-8"))

def env_value(value):
    if value is None:
        return ""
    return str(value).replace("\n", " ")

env_lines = [
    f'RUNTIME_CLASSIFICATION="{env_value(data.get("classification"))}"',
    f'RUNTIME_HARD_BLOCKERS="{env_value(data.get("hard_blockers"))}"',
    f'RUNTIME_WARNINGS="{env_value(data.get("warnings"))}"',
    f'RUNTIME_NOISE="{env_value(data.get("noise"))}"',
    f'RUNTIME_APP_CRASH="{env_value(data.get("app_crash"))}"',
    f'RUNTIME_ANDROID_RUNTIME="{env_value(data.get("android_runtime"))}"',
    f'RUNTIME_FATAL_EXCEPTION="{env_value(data.get("fatal_exception"))}"',
    f'RUNTIME_RELEVANT_LINES="{env_value(data.get("relevant_lines"))}"',
]
env_path.write_text("\n".join(env_lines) + "\n", encoding="utf-8")

def evidence_lines(items):
    out = []
    for item in items:
        out.append(f'{item.get("level")} {item.get("category")} line={item.get("line_no")}: {item.get("message")}')
        line = item.get("line") or ""
        if line:
            out.append("  " + line[:300])
    return "\n".join(out) + ("\n" if out else "")

hard_path.write_text(evidence_lines(data.get("hard_blocker_evidence", [])), encoding="utf-8")
warn_path.write_text(evidence_lines(data.get("warning_evidence", [])), encoding="utf-8")
PY

cat "$TEXT_OUT"
echo
echo "===== runtime classification files ====="
printf 'TEXT=%s\n' "$TEXT_OUT"
printf 'JSON=%s\n' "$JSON_OUT"
printf 'ENV=%s\n' "$ENV_OUT"
printf 'HARD_BLOCKERS=%s\n' "$HARD_OUT"
printf 'WARNINGS=%s\n' "$WARN_OUT"

CLASSIFICATION="$(grep -E '^CLASSIFICATION=' "$TEXT_OUT" | head -n 1 | cut -d= -f2-)"
WARNINGS="$(grep -E '^WARNINGS=' "$TEXT_OUT" | head -n 1 | cut -d= -f2-)"
HARD_BLOCKERS="$(grep -E '^HARD_BLOCKERS=' "$TEXT_OUT" | head -n 1 | cut -d= -f2-)"

echo
echo "RUNTIME_CLASSIFIER_WRAPPER_CLASSIFICATION=$CLASSIFICATION"
echo "RUNTIME_CLASSIFIER_WRAPPER_HARD_BLOCKERS=$HARD_BLOCKERS"
echo "RUNTIME_CLASSIFIER_WRAPPER_WARNINGS=$WARNINGS"

if [ "$CLASSIFICATION" = "FAIL" ]; then
  echo "RESULT=RUNTIME_LOGCAT_CLASSIFICATION_FAIL"
  exit 1
fi

if [ "$CLASSIFICATION" = "WARN" ] && [ "$FAIL_ON_WARN" -eq 1 ]; then
  echo "RESULT=RUNTIME_LOGCAT_CLASSIFICATION_WARN_FAIL_ON_WARN"
  exit 1
fi

if [ "$CLASSIFICATION" = "WARN" ] && [ "$ALLOW_WARN" -eq 1 ]; then
  echo "RESULT=RUNTIME_LOGCAT_CLASSIFICATION_WARN_ALLOWED"
  exit 0
fi

if [ "$CLASSIFICATION" = "WARN" ]; then
  echo "RESULT=RUNTIME_LOGCAT_CLASSIFICATION_WARN"
  exit 0
fi

echo "RESULT=RUNTIME_LOGCAT_CLASSIFICATION_OK"
exit 0
