#!/usr/bin/env bash
set -u

FAIL=0

DEFAULT_BOOST_APK="/home/b-real/com.rubenmayayo.reddit_1.12.12-210011212_minAPI21(arm64-v8a,armeabi,armeabi-v7a,mips,mips64,x86,x86_64)(nodpi)_apkmirror.com.apk"
NORMAL_PACKAGE="com.rubenmayayo.reddit"
DEV_PACKAGE="com.rubenmayayo.reddit.dev"
LABEL="Boost Dev"
EXPECTED_TARGET_SDK="35"
BASE_APK="$DEFAULT_BOOST_APK"
MPP=""
NAME="boost-dev-from-mpp"
SERIAL=""
MARKERS=()
INSTALL=0
NO_VERIFY_WITH_SDK=1

usage() {
  cat <<'EOF'
Usage:
  tools/boost-dev-from-mpp.sh --mpp PATH --name NAME [options]

Build a Boost DEV clone from a clean original Boost APK and a Morphe MPP.

Options:
  --mpp PATH                 MPP file to apply. Required.
  --name NAME                Candidate name suffix. Required.
  --base PATH                Clean original Boost APK. Defaults to known APKMirror Boost 1.12.12.
  --serial SERIAL            adb serial. Required when --install is used.
  --marker TEXT              Marker string required in normal and DEV APK. Repeatable.
  --install                  Install DEV APK to the selected device.
  --verify-with-sdk          Pass SDK verifier to Morphe CLI. Default is --no-verify-with-sdk.
  --no-verify-with-sdk       Do not pass SDK verifier to Morphe CLI. Default.
  --dev-package PACKAGE      DEV package. Default: com.rubenmayayo.reddit.dev.
  --normal-package PACKAGE   Normal package. Default: com.rubenmayayo.reddit.
  --label LABEL              DEV app label. Default: Boost Dev.
  --expected-target-sdk SDK  Expected patched targetSdk. Default: 35.
  -h, --help                 Show this help.

Output:
  local-artifacts/boost-dev-from-mpp/<timestamp>-<name>/
  Prints RESULT=MORPHE_BOOST_DEV_FROM_MPP_OK on success.

Rules:
  - Base APK must be clean Boost package com.rubenmayayo.reddit.
  - Base APK must not already contain Morphe markers.
  - Normal candidate is built with tools/build-boost-candidate.sh.
  - DEV clone is built with tools/build-boost-devclone-candidate.sh.
EOF
}

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mpp)
      MPP="${2:-}"
      shift 2
      ;;
    --name)
      NAME="${2:-}"
      shift 2
      ;;
    --base)
      BASE_APK="${2:-}"
      shift 2
      ;;
    --serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --marker)
      MARKERS+=("${2:-}")
      shift 2
      ;;
    --install)
      INSTALL=1
      shift
      ;;
    --verify-with-sdk)
      NO_VERIFY_WITH_SDK=0
      shift
      ;;
    --no-verify-with-sdk)
      NO_VERIFY_WITH_SDK=1
      shift
      ;;
    --dev-package)
      DEV_PACKAGE="${2:-}"
      shift 2
      ;;
    --normal-package)
      NORMAL_PACKAGE="${2:-}"
      shift 2
      ;;
    --label)
      LABEL="${2:-}"
      shift 2
      ;;
    --expected-target-sdk)
      EXPECTED_TARGET_SDK="${2:-}"
      shift 2
      ;;
    -h|--help)
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

if [ -z "$MPP" ]; then
  echo "Missing required --mpp" >&2
  usage >&2
  exit 2
fi

if [ -z "$NAME" ]; then
  echo "Missing required --name" >&2
  usage >&2
  exit 2
fi

if [ "$INSTALL" -eq 1 ] && [ -z "$SERIAL" ]; then
  echo "Missing --serial when --install is used" >&2
  usage >&2
  exit 2
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
OUT_ROOT="local-artifacts/boost-dev-from-mpp"
OUT_DIR="$OUT_ROOT/${timestamp}-${NAME}"
mkdir -p "$OUT_DIR"

exec > >(tee "$OUT_DIR/boost-dev-from-mpp.log") 2>&1

echo "BOOST DEV FROM MPP"
echo "Started: $(date --iso-8601=seconds)"
echo "BASE_APK=$BASE_APK"
echo "MPP=$MPP"
echo "NAME=$NAME"
echo "NORMAL_PACKAGE=$NORMAL_PACKAGE"
echo "DEV_PACKAGE=$DEV_PACKAGE"
echo "LABEL=$LABEL"
echo "EXPECTED_TARGET_SDK=$EXPECTED_TARGET_SDK"
echo "INSTALL=$INSTALL"
echo "SERIAL=$SERIAL"
echo "NO_VERIFY_WITH_SDK=$NO_VERIFY_WITH_SDK"
printf 'MARKERS=%s\n' "${MARKERS[*]:-}"

echo
echo "===== repo / input sanity ====="
git rev-parse --show-toplevel >/dev/null 2>&1 || mark_fail "not inside git repo"

[ -s "$BASE_APK" ] || mark_fail "base APK missing: $BASE_APK"
[ -s "$MPP" ] || mark_fail "MPP missing: $MPP"

if [ "$FAIL" -eq 0 ]; then
  sha256sum "$BASE_APK" | tee "$OUT_DIR/base-apk.sha256"
  sha256sum "$MPP" | tee "$OUT_DIR/mpp.sha256"

  aapt dump badging "$BASE_APK" > "$OUT_DIR/base.badging" || mark_fail "aapt badging failed for base APK"
  grep -E "package:|sdkVersion:|targetSdkVersion:" "$OUT_DIR/base.badging" || true

  BASE_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$OUT_DIR/base.badging" | head -1)"
  BASE_VERSION_NAME="$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" "$OUT_DIR/base.badging" | head -1)"

  echo "BASE_PACKAGE=$BASE_PACKAGE"
  echo "BASE_VERSION_NAME=$BASE_VERSION_NAME"

  [ "$BASE_PACKAGE" = "$NORMAL_PACKAGE" ] || mark_fail "base APK package mismatch: expected $NORMAL_PACKAGE got $BASE_PACKAGE"
  [ "$BASE_VERSION_NAME" = "1.12.12" ] || mark_fail "base APK version mismatch: expected 1.12.12 got $BASE_VERSION_NAME"

  if unzip -p "$BASE_APK" '*.dex' 2>/dev/null | strings | grep -E 'morphe-|app\.morphe|Breal Morphe' > "$OUT_DIR/base-forbidden-morphe-markers.txt"; then
    mark_fail "base APK appears already patched / contains Morphe markers"
  else
    echo "OK: base APK has no Morphe markers"
  fi

  unzip -t "$MPP" >/dev/null || mark_fail "MPP zip test failed"
  unzip -l "$MPP" | tee "$OUT_DIR/mpp-list.txt" | grep -E 'classes.dex|extensions/boostforreddit.mpe' \
    || mark_fail "MPP missing classes.dex or boost extension"
fi

echo
echo "===== build patched normal candidate ====="
NORMAL_APK=""
NORMAL_DIR=""
if [ "$FAIL" -eq 0 ]; then
  NORMAL_NAME="${NAME}-normal"
  if [ "$NO_VERIFY_WITH_SDK" -eq 1 ]; then
    tools/build-boost-candidate.sh \
      --base "$BASE_APK" \
      --mpp "$MPP" \
      --name "$NORMAL_NAME" \
      --expected-target-sdk "$EXPECTED_TARGET_SDK" \
      --no-verify-with-sdk \
      2>&1 | tee "$OUT_DIR/build-normal.log"
  else
    tools/build-boost-candidate.sh \
      --base "$BASE_APK" \
      --mpp "$MPP" \
      --name "$NORMAL_NAME" \
      --expected-target-sdk "$EXPECTED_TARGET_SDK" \
      2>&1 | tee "$OUT_DIR/build-normal.log"
  fi

  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "normal candidate build failed rc=$RC"

  NORMAL_APK="$(grep -E '^APK:' "$OUT_DIR/build-normal.log" | tail -1 | sed 's/^APK: //')"
  NORMAL_DIR="$(grep -E '^DIR:' "$OUT_DIR/build-normal.log" | tail -1 | sed 's/^DIR: //')"

  [ -s "$NORMAL_APK" ] || mark_fail "normal candidate APK missing: $NORMAL_APK"
  echo "NORMAL_DIR=$NORMAL_DIR" | tee "$OUT_DIR/normal-dir.txt"
  echo "NORMAL_APK=$NORMAL_APK" | tee "$OUT_DIR/normal-apk.txt"
fi

echo
echo "===== validate normal candidate ====="
if [ "$FAIL" -eq 0 ]; then
  aapt dump badging "$NORMAL_APK" > "$OUT_DIR/normal.badging" || mark_fail "normal badging failed"
  grep -E "package:|sdkVersion:|targetSdkVersion:" "$OUT_DIR/normal.badging" || true

  NORMAL_CANDIDATE_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$OUT_DIR/normal.badging" | head -1)"
  NORMAL_TARGET_SDK="$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" "$OUT_DIR/normal.badging" | head -1)"

  [ "$NORMAL_CANDIDATE_PACKAGE" = "$NORMAL_PACKAGE" ] || mark_fail "normal candidate package mismatch"
  [ "$NORMAL_TARGET_SDK" = "$EXPECTED_TARGET_SDK" ] || mark_fail "normal candidate targetSdk mismatch"

  for marker in "${MARKERS[@]}"; do
    if unzip -p "$NORMAL_APK" '*.dex' 2>/dev/null | strings | grep -F "$marker" >/dev/null; then
      echo "OK: normal marker present: $marker"
    else
      mark_fail "normal marker missing: $marker"
    fi
  done
fi

echo
echo "===== build DEV clone ====="
DEV_APK=""
DEV_DIR=""
if [ "$FAIL" -eq 0 ]; then
  DEV_NAME="${NAME}-devclone"
  tools/build-boost-devclone-candidate.sh \
    --source-apk "$NORMAL_APK" \
    --name "$DEV_NAME" \
    --dev-package "$DEV_PACKAGE" \
    --normal-package "$NORMAL_PACKAGE" \
    --label "$LABEL" \
    2>&1 | tee "$OUT_DIR/build-devclone.log"

  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "DEV clone build failed rc=$RC"

  DEV_APK="$(grep -E '^APK:|^SIGNED_APK=' "$OUT_DIR/build-devclone.log" | tail -1 | sed -E 's/^(APK: |SIGNED_APK=)//')"
  if [ -z "$DEV_APK" ] || [ ! -s "$DEV_APK" ]; then
    DEV_APK="$(find local-artifacts/boost-dev-overwrite-candidates -type f \( -name '*dev*.apk' -o -name '*.apk' \) -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -1 | cut -d' ' -f2-)"
  fi

  DEV_DIR="$(dirname "$DEV_APK")"
  [ -s "$DEV_APK" ] || mark_fail "DEV APK missing"
  echo "DEV_DIR=$DEV_DIR" | tee "$OUT_DIR/dev-dir.txt"
  echo "DEV_APK=$DEV_APK" | tee "$OUT_DIR/dev-apk.txt"
fi

echo
echo "===== validate DEV APK ====="
if [ "$FAIL" -eq 0 ]; then
  aapt dump badging "$DEV_APK" > "$OUT_DIR/dev.badging" || mark_fail "DEV badging failed"
  grep -E "package:|sdkVersion:|targetSdkVersion:" "$OUT_DIR/dev.badging" || true

  DEV_CANDIDATE_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" "$OUT_DIR/dev.badging" | head -1)"
  DEV_TARGET_SDK="$(sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" "$OUT_DIR/dev.badging" | head -1)"

  [ "$DEV_CANDIDATE_PACKAGE" = "$DEV_PACKAGE" ] || mark_fail "DEV package mismatch: expected $DEV_PACKAGE got $DEV_CANDIDATE_PACKAGE"
  [ "$DEV_TARGET_SDK" = "$EXPECTED_TARGET_SDK" ] || mark_fail "DEV targetSdk mismatch"

  apksigner verify --print-certs "$DEV_APK" > "$OUT_DIR/dev-apksigner.txt" 2>&1 \
    || mark_fail "DEV APK signature verification failed"
  grep -E 'Signer #1 certificate DN|Signer #1 certificate SHA-256' "$OUT_DIR/dev-apksigner.txt" || true

  for marker in "${MARKERS[@]}"; do
    if unzip -p "$DEV_APK" '*.dex' 2>/dev/null | strings | grep -F "$marker" >/dev/null; then
      echo "OK: DEV marker present: $marker"
    else
      mark_fail "DEV marker missing: $marker"
    fi
  done
fi

echo
echo "===== install DEV ====="
if [ "$FAIL" -eq 0 ] && [ "$INSTALL" -eq 1 ]; then
  env -u ANDROID_SERIAL adb -s "$SERIAL" get-state || mark_fail "adb unavailable: $SERIAL"
  env -u ANDROID_SERIAL adb -s "$SERIAL" install -r "$DEV_APK" || mark_fail "DEV install failed"

  env -u ANDROID_SERIAL adb -s "$SERIAL" shell dumpsys package "$DEV_PACKAGE" \
    | grep -E 'versionName|versionCode|targetSdk|firstInstallTime|lastUpdateTime|installerPackageName' \
    | tee "$OUT_DIR/installed-dev-package.txt" || mark_fail "installed DEV package check failed"
fi

echo
echo "===== summary ====="
echo "OUT_DIR=$OUT_DIR"
echo "BASE_APK=$BASE_APK"
echo "MPP=$MPP"
echo "NORMAL_APK=$NORMAL_APK"
echo "DEV_APK=$DEV_APK"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_BOOST_DEV_FROM_MPP_OK"
else
  echo "RESULT=MORPHE_BOOST_DEV_FROM_MPP_FAIL"
fi

exit "$FAIL"
