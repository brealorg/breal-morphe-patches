#!/usr/bin/env bash
set -u

usage() {
  cat <<'USAGE'
Usage:
  tools/boost-bytecode-safety-gate.sh \
    --base-apk BASE.apk \
    --candidate-apk CANDIDATE.apk \
    --patch-result patch-result.json \
    [--report report.json]

Test/debug mode:
  tools/boost-bytecode-safety-gate.sh \
    --base-smali BASE_DIR \
    --candidate-smali CANDIDATE_DIR \
    --patch-result patch-result.json \
    [--report report.json]

Options:
  --critical-prefix DESC  Modified class descriptor prefix to analyze. Repeatable.
                          Default: Lcom/rubenmayayo/reddit/
  --keep-decoded          Keep apktool decode directory and print its path.
  --help                  Show this help.

The gate is read-only with respect to the APKs. APK mode requires apktool or an
apktool jar discoverable below $HOME or /tmp. Analysis fails closed if decoding,
patch-result parsing, class/member resolution, or register flow cannot establish
safety for a newly introduced instruction in a modified Boost method.
USAGE
}

BASE_APK=""
CANDIDATE_APK=""
BASE_SMALI=""
CANDIDATE_SMALI=""
PATCH_RESULT=""
REPORT=""
KEEP_DECODED=0
CRITICAL_PREFIXES=()

need_value() {
  if [ -z "${2:-}" ]; then
    echo "Missing value for $1" >&2
    usage >&2
    exit 2
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --base-apk)
      need_value "$1" "${2:-}"
      BASE_APK="$2"
      shift 2
      ;;
    --candidate-apk|--apk)
      need_value "$1" "${2:-}"
      CANDIDATE_APK="$2"
      shift 2
      ;;
    --base-smali)
      need_value "$1" "${2:-}"
      BASE_SMALI="$2"
      shift 2
      ;;
    --candidate-smali)
      need_value "$1" "${2:-}"
      CANDIDATE_SMALI="$2"
      shift 2
      ;;
    --patch-result)
      need_value "$1" "${2:-}"
      PATCH_RESULT="$2"
      shift 2
      ;;
    --report)
      need_value "$1" "${2:-}"
      REPORT="$2"
      shift 2
      ;;
    --critical-prefix)
      need_value "$1" "${2:-}"
      CRITICAL_PREFIXES+=("$2")
      shift 2
      ;;
    --keep-decoded)
      KEEP_DECODED=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
ANALYZER="$ROOT/tools/boost_bytecode_safety.py"

fail_environment() {
  echo "BYTECODE_GATE=FAIL"
  echo "REASON=ANALYSIS_UNAVAILABLE"
  echo "DETAIL=$*"
  exit 2
}

[ -f "$ANALYZER" ] || fail_environment "missing analyzer: $ANALYZER"
[ -n "$PATCH_RESULT" ] || fail_environment "--patch-result is required"
[ -f "$PATCH_RESULT" ] || fail_environment "patch result missing: $PATCH_RESULT"

APK_MODE=0
SMALI_MODE=0
if [ -n "$BASE_APK" ] || [ -n "$CANDIDATE_APK" ]; then
  APK_MODE=1
fi
if [ -n "$BASE_SMALI" ] || [ -n "$CANDIDATE_SMALI" ]; then
  SMALI_MODE=1
fi

if [ "$APK_MODE" -eq 1 ] && [ "$SMALI_MODE" -eq 1 ]; then
  fail_environment "APK mode and decoded-Smali mode are mutually exclusive"
fi
if [ "$APK_MODE" -eq 0 ] && [ "$SMALI_MODE" -eq 0 ]; then
  fail_environment "provide base/candidate APKs or decoded Smali directories"
fi

TMP=""
cleanup() {
  if [ -n "$TMP" ] && [ "$KEEP_DECODED" -eq 0 ]; then
    rm -rf "$TMP"
  fi
}
trap cleanup EXIT

run_apktool() {
  if command -v apktool >/dev/null 2>&1; then
    apktool "$@"
    return
  fi
  local jar
  jar="$(find "$HOME" /tmp -maxdepth 7 -type f -iname '*apktool*.jar' 2>/dev/null | sort | head -n1)"
  [ -n "$jar" ] || return 127
  java -jar "$jar" "$@"
}

if [ "$APK_MODE" -eq 1 ]; then
  [ -f "$BASE_APK" ] || fail_environment "base APK missing: $BASE_APK"
  [ -f "$CANDIDATE_APK" ] || fail_environment "candidate APK missing: $CANDIDATE_APK"
  TMP="$(mktemp -d /tmp/morphe-bytecode-gate.XXXXXX 2>/dev/null || mktemp -d)"
  BASE_SMALI="$TMP/base"
  CANDIDATE_SMALI="$TMP/candidate"

  echo "BYTECODE_GATE_PHASE=DECODE_BASE"
  if ! run_apktool d -f -r "$BASE_APK" -o "$BASE_SMALI" >/dev/null 2>"$TMP/base-apktool.err"; then
    cat "$TMP/base-apktool.err" >&2
    fail_environment "apktool failed to decode base APK"
  fi

  echo "BYTECODE_GATE_PHASE=DECODE_CANDIDATE"
  if ! run_apktool d -f -r "$CANDIDATE_APK" -o "$CANDIDATE_SMALI" >/dev/null 2>"$TMP/candidate-apktool.err"; then
    cat "$TMP/candidate-apktool.err" >&2
    fail_environment "apktool failed to decode candidate APK"
  fi
fi

[ -d "$BASE_SMALI" ] || fail_environment "base Smali directory missing: $BASE_SMALI"
[ -d "$CANDIDATE_SMALI" ] || fail_environment "candidate Smali directory missing: $CANDIDATE_SMALI"

CMD=(
  python3 "$ANALYZER"
  --base-smali "$BASE_SMALI"
  --candidate-smali "$CANDIDATE_SMALI"
  --patch-result "$PATCH_RESULT"
)
if [ -n "$REPORT" ]; then
  CMD+=(--report "$REPORT")
fi
for prefix in "${CRITICAL_PREFIXES[@]}"; do
  CMD+=(--critical-prefix "$prefix")
done

"${CMD[@]}"
RC=$?
if [ "$KEEP_DECODED" -eq 1 ] && [ -n "$TMP" ]; then
  echo "DECODED_DIR=$TMP"
fi
exit "$RC"
