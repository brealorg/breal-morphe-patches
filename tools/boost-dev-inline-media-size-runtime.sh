#!/usr/bin/env bash
set +e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MPP="${1:-patches/build/libs/patches-1.4.92.mpp}"

DEV_PKG="com.rubenmayayo.reddit.dev.issue70"
NORMAL_PKG="com.rubenmayayo.reddit"
REGULAR_DEV_PKG="com.rubenmayayo.reddit.dev"
LABEL="Boost Issue70"
MARKER="MORPHE_BOOST_INLINE_MEDIA_ADAPTIVE_SIZE_ISSUE70_V4"

FAIL=0
SERIAL=""
OUT="/tmp/boost-issue70-width-v4.$(date +%Y%m%d-%H%M%S)"
BUILD_LOG="$OUT/build.log"

mkdir -p "$OUT" logs

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

cd "$ROOT" || exit 1

echo "===== ISSUE #70 WIDTH PRESET RUNTIME V4 ====="
date -Is
echo "ROOT=$ROOT"
echo "MPP=$MPP"
echo "OUT=$OUT"

tools/check-boost-inline-media-size-contract.sh ||
  mark_fail "V4 contract failed"

test -s "$MPP" ||
  mark_fail "MPP missing"

if [ "$FAIL" -eq 0 ]; then
  unzip -p "$MPP" extensions/boostforreddit.mpe 2>/dev/null |
    strings |
    grep -F "$MARKER" ||
      mark_fail "V4 marker missing from MPP"
fi

echo
echo "===== ADB DEVICE ====="

SERIAL="$(
  tools/boost-adb-serial.sh \
    --hint "${MORPHE_ADB_HINT:-192.168.1.248}" \
    --expect-model "${MORPHE_ADB_EXPECT_MODEL:-Pixel_6}" \
    --expect-device "${MORPHE_ADB_EXPECT_DEVICE:-oriole}"
)" || mark_fail "Could not resolve expected Pixel ADB device"

echo "SERIAL=$SERIAL"

if [ -z "$SERIAL" ]; then
  mark_fail "No usable ADB device"
fi

adbq() {
  env -u ANDROID_SERIAL adb -s "$SERIAL" "$@"
}

package_update_time() {
  adbq shell dumpsys package "$1" 2>/dev/null |
    sed -n 's/^[[:space:]]*lastUpdateTime=//p' |
    head -1 |
    tr -d '\r'
}

if [ "$FAIL" -eq 0 ]; then
  NORMAL_BEFORE="$(package_update_time "$NORMAL_PKG")"
  REGULAR_DEV_BEFORE="$(package_update_time "$REGULAR_DEV_PKG")"

  echo "NORMAL_BEFORE=${NORMAL_BEFORE:-NOT_INSTALLED}"
  echo "REGULAR_DEV_BEFORE=${REGULAR_DEV_BEFORE:-NOT_INSTALLED}"
fi

echo
echo "===== BUILD / INSTALL ISSUE70 DEV ====="

if [ "$FAIL" -eq 0 ]; then
  tools/boost-dev-from-mpp.sh \
    --mpp "$MPP" \
    --name "issue70-width-presets-v4" \
    --dev-package "$DEV_PKG" \
    --normal-package "$NORMAL_PKG" \
    --label "$LABEL" \
    --expected-target-sdk 35 \
    --serial "$SERIAL" \
    --marker "$MARKER" \
    --install \
    --no-verify-with-sdk \
    > "$BUILD_LOG" 2>&1

  BUILD_RC=$?

  echo "BUILD_RC=$BUILD_RC"
  echo "BUILD_LOG=$BUILD_LOG"

  grep -E \
    'INFO: Applied: (Show inline Giphy previews in comments|Boost Morphe settings|Spoof client)|targetSdkVersion|OK: DEV marker present|RESULT=MORPHE_BOOST_DEV_FROM_MPP' \
    "$BUILD_LOG" ||
    true

  if [ "$BUILD_RC" -ne 0 ]; then
    tail -100 "$BUILD_LOG"
    mark_fail "DEV build/install failed"
  fi

  grep -Fq \
    'RESULT=MORPHE_BOOST_DEV_FROM_MPP_OK' \
    "$BUILD_LOG" ||
      mark_fail "DEV success result missing"
fi

echo
echo "===== LAUNCH ====="

if [ "$FAIL" -eq 0 ]; then
  adbq shell am force-stop "$DEV_PKG" >/dev/null 2>&1 || true
  adbq logcat -c || true
  adbq logcat -b crash -c || true

  adbq shell monkey \
    -p "$DEV_PKG" \
    -c android.intent.category.LAUNCHER \
    1 >/dev/null ||
      mark_fail "Launch failed"

  sleep 2
fi

capture_mode() {
  local mode="$1"
  local log="$OUT/${mode}-logcat.txt"
  local screenshot="$OUT/${mode}.png"

  echo
  echo "============================================================"
  echo "TEST: ${mode^^}"
  echo "============================================================"
  echo
  echo "1. Velg Preview size → ${mode^}."
  echo "2. Gå tilbake til samme kommentartråd."
  echo "3. Bruk nøyaktig samme inline GIF/bilde som i de andre testene."
  echo "4. Scroll mediet ut og inn igjen slik at det bindes på nytt."
  echo "5. La mediet være synlig."
  echo

  adbq logcat -c || true
  adbq logcat -b crash -c || true

  printf 'Trykk ENTER når %s er synlig og stabil: ' "$mode"
  IFS= read -r _

  sleep 2

  PID="$(adbq shell pidof "$DEV_PKG" | tr -d '\r\n ')"
  echo "${mode^^}_PID=${PID:-NONE}"

  [ -n "$PID" ] ||
    mark_fail "$mode: process not alive"

  adbq exec-out screencap -p > "$screenshot"

  if [ ! -s "$screenshot" ]; then
    mark_fail "$mode: screenshot missing"
  else
    sha256sum "$screenshot"
  fi

  adbq logcat \
    -d \
    -v threadtime \
    -b main \
    -b system \
    -b crash \
    > "$log" 2>&1

  grep -F "$MARKER: mode=$mode" "$log" |
    tail -3 ||
      mark_fail "$mode: mode marker missing"

  grep -F "$MARKER: measured mode=$mode" "$log" |
    tail -10 ||
      mark_fail "$mode: measurement marker missing"

  if grep -E \
    "$MARKER: (bind failed|measurement failed|Glide load failed)|VerifyError|IllegalAccessError|FATAL EXCEPTION|Process: ${DEV_PKG//./\\.}" \
    "$log"
  then
    mark_fail "$mode: runtime blocker"
  fi
}

if [ "$FAIL" -eq 0 ]; then
  capture_mode compact
  capture_mode balanced
  capture_mode large
fi

extract_max_width() {
  local mode="$1"

  sed -n \
    "s/.*${MARKER}: measured mode=${mode} widthPx=\([0-9][0-9]*\).*/\1/p" \
    "$OUT/${mode}-logcat.txt" |
    awk '$1 > 0' |
    sort -n |
    tail -1
}

echo
echo "===== WIDTH ORDER AUDIT ====="

if [ "$FAIL" -eq 0 ]; then
  COMPACT_WIDTH="$(extract_max_width compact)"
  BALANCED_WIDTH="$(extract_max_width balanced)"
  LARGE_WIDTH="$(extract_max_width large)"

  echo "COMPACT_MAX_MEASURED_WIDTH=${COMPACT_WIDTH:-NONE}"
  echo "BALANCED_MAX_MEASURED_WIDTH=${BALANCED_WIDTH:-NONE}"
  echo "LARGE_MAX_MEASURED_WIDTH=${LARGE_WIDTH:-NONE}"

  if [ -z "$COMPACT_WIDTH" ] ||
     [ -z "$BALANCED_WIDTH" ] ||
     [ -z "$LARGE_WIDTH" ]; then
    mark_fail "Could not resolve all measured widths"
  elif [ "$COMPACT_WIDTH" -ge "$BALANCED_WIDTH" ]; then
    mark_fail "Compact width is not smaller than Balanced"
  elif [ "$BALANCED_WIDTH" -ge "$LARGE_WIDTH" ]; then
    mark_fail "Balanced width is not smaller than Large"
  else
    echo "WIDTH_ORDER_COMPACT_LT_BALANCED_LT_LARGE=PASS"
  fi
fi

echo
echo "===== PACKAGE ISOLATION ====="

if [ "$FAIL" -eq 0 ]; then
  NORMAL_AFTER="$(package_update_time "$NORMAL_PKG")"
  REGULAR_DEV_AFTER="$(package_update_time "$REGULAR_DEV_PKG")"

  echo "NORMAL_AFTER=${NORMAL_AFTER:-NOT_INSTALLED}"
  echo "REGULAR_DEV_AFTER=${REGULAR_DEV_AFTER:-NOT_INSTALLED}"

  if [ "$NORMAL_BEFORE" != "$NORMAL_AFTER" ]; then
    mark_fail "Normal Boost update time changed"
  fi

  if [ "$REGULAR_DEV_BEFORE" != "$REGULAR_DEV_AFTER" ]; then
    mark_fail "Regular Boost DEV update time changed"
  fi
fi

echo
echo "===== MANUAL ACCEPTANCE ====="

if [ "$FAIL" -eq 0 ]; then
  cat <<'EOF'
Bekreft:

- samme medium var tydelig minst i Compact
- samme medium var større i Balanced
- samme medium var størst i Large
- aspektforholdet var korrekt
- ingen cropping eller strekking
- tapping åpnet forventet Boost-viewer
EOF

  echo
  printf 'Godkjent? [y/N]: '
  IFS= read -r answer

  case "$answer" in
    y|Y|yes|YES|Yes)
      echo "MANUAL_ACCEPTANCE=PASS"
      ;;
    *)
      mark_fail "Manual acceptance not granted"
      ;;
  esac
fi

echo
echo "===== FINAL ====="
echo "OUT=$OUT"
echo "SERIAL=$SERIAL"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_ISSUE70_WIDTH_PRESETS_V4_PASS"
else
  echo "RESULT=MORPHE_ISSUE70_WIDTH_PRESETS_V4_FAIL"
fi

exit "$FAIL"
