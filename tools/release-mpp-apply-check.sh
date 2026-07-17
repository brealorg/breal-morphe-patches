#!/usr/bin/env bash

FAIL=0
BASE_APK=""
MPP=""
JAR="${HOME}/.local/share/morphe/tools/morphe-desktop-1.11.0-all.jar"
OPTIONS_FILE=""
PACKAGE="com.rubenmayayo.reddit"
EXPECTED_TARGET_SDK="35"
NAME="release-mpp-apply-check"
OUT_ROOT="/tmp"
ENABLE_PATCHES=()
REQUIRE_APPLIED=()
REQUIRE_MARKERS=()
EXTRA_MORPHE_ARGS=()
SKIP_STATIC_GATE=0

usage() {
  cat <<'USAGE'
Usage:
  tools/release-mpp-apply-check.sh --base APK --mpp MPP --options-file JSON [options] [-- extra morphe-cli patch args]

Required:
  --base PATH                 Base/original APK.
  --mpp PATH                  Release MPP to apply.
  --options-file PATH         Known-good Morphe options JSON.

Options:
  --jar PATH                  Morphe CLI jar.
  --package NAME              Expected package. Default: com.rubenmayayo.reddit.
  --expected-target-sdk SDK   Expected targetSdk. Default: 35.
  --name NAME                 Output suffix. Default: release-mpp-apply-check.
  --out-root DIR              Output root. Default: /tmp.
  --enable PATCH              Patch to enable with -e. Repeatable.
  --require-applied PATCH     Required "INFO: Applied: PATCH" line. Repeatable.
  --require-marker TEXT       Required APK marker for static gate. Repeatable.
  --skip-static-gate          Skip tools/boost-check-candidate.sh.
  -h, --help                  Show this help.
USAGE
}

need_value() {
  if [ -z "${2:-}" ]; then
    echo "Missing value for $1" >&2
    usage >&2
    exit 2
  fi
}

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

abs_path() {
  if command -v realpath >/dev/null 2>&1; then
    realpath "$1" 2>/dev/null || printf '%s\n' "$1"
  else
    case "$1" in
      /*) printf '%s\n' "$1" ;;
      *) printf '%s/%s\n' "$PWD" "$1" ;;
    esac
  fi
}

safe_name() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9._-]+/-/g; s/^-+|-+$//g'
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --base)
      need_value "$1" "${2:-}"
      BASE_APK="$2"
      shift 2
      ;;
    --mpp)
      need_value "$1" "${2:-}"
      MPP="$2"
      shift 2
      ;;
    --jar)
      need_value "$1" "${2:-}"
      JAR="$2"
      shift 2
      ;;
    --options-file)
      need_value "$1" "${2:-}"
      OPTIONS_FILE="$2"
      shift 2
      ;;
    --package)
      need_value "$1" "${2:-}"
      PACKAGE="$2"
      shift 2
      ;;
    --expected-target-sdk)
      need_value "$1" "${2:-}"
      EXPECTED_TARGET_SDK="$2"
      shift 2
      ;;
    --name)
      need_value "$1" "${2:-}"
      NAME="$2"
      shift 2
      ;;
    --out-root)
      need_value "$1" "${2:-}"
      OUT_ROOT="$2"
      shift 2
      ;;
    --enable|-e)
      need_value "$1" "${2:-}"
      ENABLE_PATCHES+=("$2")
      shift 2
      ;;
    --require-applied)
      need_value "$1" "${2:-}"
      REQUIRE_APPLIED+=("$2")
      shift 2
      ;;
    --require-marker)
      need_value "$1" "${2:-}"
      REQUIRE_MARKERS+=("$2")
      shift 2
      ;;
    --skip-static-gate)
      SKIP_STATIC_GATE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA_MORPHE_ARGS+=("$@")
      break
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${OUT_ROOT%/}/${STAMP}-$(safe_name "$NAME")"
mkdir -p "$OUT_DIR"

exec > >(tee "$OUT_DIR/release-mpp-apply-check.log") 2>&1

echo "RELEASE MPP APPLY CHECK"
echo "Started: $(date -Is)"
echo "OUT_DIR=$OUT_DIR"

[ -n "$BASE_APK" ] || mark_fail "--base is required"
[ -n "$MPP" ] || mark_fail "--mpp is required"
[ -n "$OPTIONS_FILE" ] || mark_fail "--options-file is required"
[ -f "$BASE_APK" ] || mark_fail "base APK missing: $BASE_APK"
[ -f "$MPP" ] || mark_fail "MPP missing: $MPP"
[ -f "$JAR" ] || mark_fail "Morphe CLI jar missing: $JAR"
[ -f "$OPTIONS_FILE" ] || mark_fail "options file missing: $OPTIONS_FILE"
[ -x tools/boost-check-candidate.sh ] || mark_fail "tools/boost-check-candidate.sh missing or not executable"

BASE_APK="$(abs_path "$BASE_APK")"
MPP="$(abs_path "$MPP")"
JAR="$(abs_path "$JAR")"
OPTIONS_FILE="$(abs_path "$OPTIONS_FILE")"

OUT_APK="$OUT_DIR/apply-check.apk"
RESULT_JSON="$OUT_DIR/patch-result.json"
PATCH_LOG="$OUT_DIR/morphe-patch.log"

echo "BASE_APK=$BASE_APK"
echo "MPP=$MPP"
echo "JAR=$JAR"
echo "OPTIONS_FILE=$OPTIONS_FILE"
echo "PACKAGE=$PACKAGE"
echo "EXPECTED_TARGET_SDK=$EXPECTED_TARGET_SDK"

if [ "$FAIL" -ne 0 ]; then
  echo "RESULT=MORPHE_RELEASE_MPP_APPLY_CHECK_FAIL"
  exit 1
fi

CMD=(java -jar "$JAR" patch --purge -p "$MPP" --options-file "$OPTIONS_FILE" -o "$OUT_APK" -r "$RESULT_JSON")
for patch in "${ENABLE_PATCHES[@]}"; do
  CMD+=(-e "$patch")
done
CMD+=("${EXTRA_MORPHE_ARGS[@]}")
CMD+=("$BASE_APK")

echo
echo "===== morphe apply ====="
printf '+ '
printf '%q ' "${CMD[@]}"
echo

"${CMD[@]}" 2>&1 | tee "$PATCH_LOG"
RC=${PIPESTATUS[0]}
[ "$RC" -eq 0 ] || mark_fail "morphe patch failed rc=$RC"

echo
echo "===== required applied patches ====="
for patch in "${REQUIRE_APPLIED[@]}"; do
  if grep -Fq "INFO: Applied: $patch" "$PATCH_LOG"; then
    echo "[PASS] applied: $patch"
  else
    mark_fail "missing applied patch: $patch"
  fi
done

echo
echo "===== static gate ====="
if [ "$FAIL" -eq 0 ] && [ "$SKIP_STATIC_GATE" -eq 0 ]; then
  GATE_CMD=(tools/boost-check-candidate.sh --apk "$OUT_APK" --base "$BASE_APK" --mpp "$MPP" --package "$PACKAGE" --expected-target-sdk "$EXPECTED_TARGET_SDK")
  for marker in "${REQUIRE_MARKERS[@]}"; do
    GATE_CMD+=(--require-marker "$marker")
  done

  printf '+ '
  printf '%q ' "${GATE_CMD[@]}"
  echo

  "${GATE_CMD[@]}" 2>&1 | tee "$OUT_DIR/static-gate.log"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "static gate failed rc=$RC"
fi

echo
echo "OUT_DIR=$OUT_DIR"
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_RELEASE_MPP_APPLY_CHECK_OK"
else
  echo "RESULT=MORPHE_RELEASE_MPP_APPLY_CHECK_FAIL"
fi
exit "$FAIL"
