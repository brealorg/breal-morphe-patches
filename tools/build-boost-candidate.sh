#!/usr/bin/env bash

# Build a patched Boost APK candidate from the local MPP.
# Does not install anything. Does not touch adb/device state.

usage() {
  cat <<'USAGE'
Usage:
  tools/build-boost-candidate.sh [options] [-- extra morphe-cli patch args]

Options:
  --base PATH                 Base/original Boost APK.
  --mpp PATH                  MPP file. Defaults to patches/build/libs/patches-<version>.mpp.
  --jar PATH                  Morphe CLI jar.
  --name NAME                 Candidate name. Default: boost-candidate.
  --expected-target-sdk SDK   Expected targetSdk for static gate. Default: 35.
  --no-verify-with-sdk        Do not pass --verify-with-sdk to Morphe CLI.
  --help                      Show this help.

Output:
  local-artifacts/boost-candidates/<timestamp>-<name>/
USAGE
}

DEFAULT_BASE='/home/b-real/com.rubenmayayo.reddit_1.12.12-210011212_minAPI21(arm64-v8a,armeabi,armeabi-v7a,mips,mips64,x86,x86_64)(nodpi)_apkmirror.com.apk'
DEFAULT_JAR='/home/b-real/.local/share/morphe/tools/morphe-cli-1.10.0-dev.1-all.jar'

VERSION="$(grep -E '^version\s*=' gradle.properties 2>/dev/null | sed -E 's/^version\s*=\s*//')"
DEFAULT_MPP="patches/build/libs/patches-${VERSION}.mpp"

BASE_APK="$DEFAULT_BASE"
MPP="$DEFAULT_MPP"
JAR="$DEFAULT_JAR"
NAME="boost-candidate"
EXPECTED_TARGET_SDK="35"
VERIFY_WITH_SDK=1
EXTRA_ARGS=()

need_value() {
  if [ -z "${2:-}" ]; then
    echo "Missing value for $1"
    usage
    exit 2
  fi
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
    --name)
      need_value "$1" "${2:-}"
      NAME="$2"
      shift 2
      ;;
    --expected-target-sdk)
      need_value "$1" "${2:-}"
      EXPECTED_TARGET_SDK="$2"
      shift 2
      ;;
    --no-verify-with-sdk)
      VERIFY_WITH_SDK=0
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --)
      shift
      EXTRA_ARGS+=("$@")
      break
      ;;
    *)
      echo "Unknown argument: $1"
      usage
      exit 2
      ;;
  esac
done

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

STAMP="$(date +%Y%m%d-%H%M%S)"
SAFE_NAME="$(safe_name "$NAME")"
ROOT="local-artifacts/boost-candidates/${STAMP}-${SAFE_NAME}"
mkdir -p "$ROOT"

OUT_APK="$ROOT/boost-candidate.apk"
RESULT_JSON="$ROOT/patch-result.json"
PATCH_LOG="$ROOT/morphe-patch.log"
OPTIONS_FILE="$ROOT/options.json"
OPTIONS_CREATE_LOG="$ROOT/options-create.log"

BASE_APK="$(abs_path "$BASE_APK")"
MPP="$(abs_path "$MPP")"
JAR="$(abs_path "$JAR")"

FAIL=0

pass() {
  printf '[PASS] %s\n' "$*"
}

warn() {
  printf '[WARN] %s\n' "$*"
}

fail() {
  FAIL=1
  printf '[FAIL] %s\n' "$*"
}

echo "BOOST CANDIDATE BUILDER"
echo "Started: $(date -Is)"
echo "Repo: $PWD"
echo

echo "Inputs:"
echo "  BASE_APK: $BASE_APK"
echo "  MPP: $MPP"
echo "  JAR: $JAR"
echo "  OUT_APK: $OUT_APK"
echo "  expected targetSdk: $EXPECTED_TARGET_SDK"
echo

[ -f "$BASE_APK" ] || fail "base APK missing: $BASE_APK"
[ -f "$MPP" ] || fail "MPP missing: $MPP"
[ -f "$JAR" ] || fail "Morphe CLI jar missing: $JAR"
[ -x tools/boost-check-candidate.sh ] || fail "tools/boost-check-candidate.sh missing or not executable"

ENV_FILE="$HOME/.config/morphe/reddit.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  . "$ENV_FILE"
  set +a
  pass "sourced reddit env file without printing values"
else
  warn "reddit env file not found: $ENV_FILE"
fi

[ -n "${REDDIT_CLIENT_ID:-}" ] || fail "REDDIT_CLIENT_ID is missing; Spoof client cannot run"
[ -n "${REDDIT_REDIRECT_URI:-}" ] || fail "REDDIT_REDIRECT_URI is missing; Spoof client cannot run"

if [ -n "${REDDIT_CLIENT_ID:-}" ]; then
  pass "reddit client-id option is available"
fi

if [ -n "${REDDIT_REDIRECT_URI:-}" ]; then
  pass "reddit redirect-uri option is available"
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
VERIFY_ARG=()
if [ "$VERIFY_WITH_SDK" -eq 1 ]; then
  if [ -d "$SDK" ]; then
    VERIFY_ARG=("--verify-with-sdk=$SDK")
    pass "will verify with Android SDK: $SDK"
  else
    warn "Android SDK dir not found; Morphe --verify-with-sdk disabled: $SDK"
  fi
else
  warn "Morphe --verify-with-sdk disabled by flag"
fi

if [ "$FAIL" -ne 0 ]; then
  echo
  echo "RESULT: FAIL before patching"
  echo "DIR: $ROOT"
  exit 1
fi

echo
echo "===== create Morphe options file ====="
if java -jar "$JAR" options-create \
  --patches "$MPP" \
  --filter-package-name com.rubenmayayo.reddit \
  --out "$OPTIONS_FILE" > "$OPTIONS_CREATE_LOG" 2>&1; then
  pass "created Morphe options template"
else
  fail "options-create failed"
  cat "$OPTIONS_CREATE_LOG"
  echo "RESULT: FAIL before patching"
  echo "DIR: $ROOT"
  exit 1
fi

OPTIONS_FILE="$OPTIONS_FILE" python3 <<'OPTIONS_JSON_PATCH'
import json
import os
from pathlib import Path

path = Path(os.environ["OPTIONS_FILE"])
data = json.loads(path.read_text())

client_id = os.environ["REDDIT_CLIENT_ID"]
redirect_uri = os.environ["REDDIT_REDIRECT_URI"]

seen_spoof = False
seen_target = False
seen_native_upload = False

def walk(obj):
    global seen_spoof, seen_target, seen_native_upload

    if isinstance(obj, dict):
        for key, value in obj.items():
            if key == "Boost Morphe settings" and isinstance(value, dict):
                value["enabled"] = True

            if key == "Spoof client" and isinstance(value, dict):
                value["enabled"] = True
                options = value.setdefault("options", {})
                options["client-id"] = client_id
                options["redirect-uri"] = redirect_uri
                options.setdefault("user-agent", "org.quantumbadger.redreader/1.25.1")
                seen_spoof = True

            if key == "Fix Boost target SDK 35 compatibility" and isinstance(value, dict):
                value["enabled"] = True
                seen_target = True

            if key == "Fix Boost native image upload" and isinstance(value, dict):
                value["enabled"] = True
                seen_native_upload = True

            walk(value)

    elif isinstance(obj, list):
        for item in obj:
            walk(item)

walk(data)

missing = []
if not seen_spoof:
    missing.append("Spoof client")
if not seen_target:
    missing.append("Fix Boost target SDK 35 compatibility")
if not seen_native_upload:
    missing.append("Fix Boost native image upload")

if missing:
    raise SystemExit("Missing expected options entries: " + ", ".join(missing))

path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
OPTIONS_JSON_PATCH

OPTIONS_RC=$?
if [ "$OPTIONS_RC" -ne 0 ]; then
  fail "could not configure options file"
  echo "RESULT: FAIL before patching"
  echo "DIR: $ROOT"
  exit "$OPTIONS_RC"
fi

chmod 600 "$OPTIONS_FILE"
pass "configured options file: $OPTIONS_FILE"
echo "      Spoof client client-id=<redacted>"
echo "      Spoof client redirect-uri=<redacted>"
echo "      Fix Boost target SDK 35 compatibility enabled"
echo "      Fix Boost native image upload enabled"

echo
echo "Base APK sha256:"
sha256sum "$BASE_APK" | tee "$ROOT/base-apk.sha256"

echo
echo "MPP sha256:"
sha256sum "$MPP" | tee "$ROOT/mpp.sha256"

CMD=(
  java -jar "$JAR"
  patch
  --purge
  "${VERIFY_ARG[@]}"
  -p "$MPP"
  --options-file "$OPTIONS_FILE"
  -o "$OUT_APK"
  -r "$RESULT_JSON"
  "${EXTRA_ARGS[@]}"
  "$BASE_APK"
)

{
  printf '%q ' "${CMD[@]}"
  printf '\n'
} > "$ROOT/morphe-command.redacted.sh"

echo
echo "===== morphe patch command ====="
cat "$ROOT/morphe-command.redacted.sh"

echo
echo "===== run morphe patch ====="
"${CMD[@]}" 2>&1 | tee "$PATCH_LOG"
RC=${PIPESTATUS[0]}

echo
echo "MORPHE_EXIT_CODE=$RC"

if [ "$RC" -ne 0 ]; then
  echo "RESULT: FAIL during Morphe patch"
  echo "DIR: $ROOT"
  echo "LOG: $PATCH_LOG"
  exit "$RC"
fi

if [ ! -f "$OUT_APK" ]; then
  echo "RESULT: FAIL; Morphe exited 0 but output APK is missing"
  echo "DIR: $ROOT"
  echo "LOG: $PATCH_LOG"
  exit 1
fi

echo
echo "Output APK sha256:"
sha256sum "$OUT_APK" | tee "$ROOT/output-apk.sha256"

echo
echo "===== static gate ====="
CHECK_CMD=(
  tools/boost-check-candidate.sh
  --apk "$OUT_APK"
  --base "$BASE_APK"
  --mpp "$MPP"
  --package com.rubenmayayo.reddit
  --expected-target-sdk "$EXPECTED_TARGET_SDK"
)

{
  printf '%q ' "${CHECK_CMD[@]}"
  printf '\n'
} > "$ROOT/check-command.txt"

"${CHECK_CMD[@]}" 2>&1 | tee "$ROOT/static-gate.log"
CHECK_RC=${PIPESTATUS[0]}

echo
echo "STATIC_GATE_EXIT_CODE=$CHECK_RC"

if [ "$CHECK_RC" -ne 0 ]; then
  echo "RESULT: FAIL static gate"
  echo "DIR: $ROOT"
  echo "APK: $OUT_APK"
  echo "PATCH_LOG: $PATCH_LOG"
  echo "STATIC_GATE_LOG: $ROOT/static-gate.log"
  exit "$CHECK_RC"
fi

echo
echo "RESULT: PASS"
echo "DIR: $ROOT"
echo "APK: $OUT_APK"
echo "PATCH_LOG: $PATCH_LOG"
echo "STATIC_GATE_LOG: $ROOT/static-gate.log"
echo "Terminal is still alive."
