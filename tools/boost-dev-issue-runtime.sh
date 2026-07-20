#!/usr/bin/env bash
FAIL=0
NAME=""
URL=""
VERSION=""
MPP=""
SERIAL=""
WATCH_SECONDS="75"
DEV_PACKAGE="com.rubenmayayo.reddit.dev"
NORMAL_PACKAGE="com.rubenmayayo.reddit"
MARKERS=()

mark_fail() { echo "FAIL: $*"; FAIL=1; }

usage() {
  cat <<'USAGE'
Usage:
  tools/boost-dev-issue-runtime.sh --name NAME [options]

Options:
  --name NAME              Runtime/artifact name suffix. Required.
  --url URL                Open direct repro URL in Boost Dev.
  --version VERSION        Use patches-<VERSION>.mpp after buildAndroid.
  --mpp PATH               Use explicit MPP path.
  --serial SERIAL          One-run adb serial override.
  --watch-seconds N        Logcat capture seconds. Default: 75.
  --marker TEXT            Marker required in DEV APK. Repeatable.
  -h, --help               Show this help.

Policy:
  - Uses DEV-clone package, not normal Boost.
  - Resolves adb through tools/boost-adb-serial.sh.
  - Does not trust stale ANDROID_SERIAL.
  - Does not select old APKs by global latest timestamp.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --name) NAME="${2:-}"; shift 2 ;;
    --url) URL="${2:-}"; shift 2 ;;
    --version) VERSION="${2:-}"; shift 2 ;;
    --mpp) MPP="${2:-}"; shift 2 ;;
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --watch-seconds) WATCH_SECONDS="${2:-}"; shift 2 ;;
    --marker) MARKERS+=("${2:-}"); shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "FAIL: unknown arg: $1"; exit 2 ;;
  esac
done

[ -n "$NAME" ] || { echo "FAIL: --name required"; exit 2; }

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 2
cd "$ROOT" || exit 2

OUT="local-artifacts/boost-dev-issue-runtime/$(date +%Y%m%d-%H%M%S)-$NAME"
mkdir -p "$OUT"
exec > >(tee "$OUT/runtime.log") 2>&1

echo "BOOST DEV ISSUE RUNTIME"
echo "OUT=$OUT"
echo "NAME=$NAME"
echo "URL=${URL:-<none>}"
echo "ANDROID_SERIAL_IGNORED=${ANDROID_SERIAL:-<unset>}"

echo
echo "===== repo ====="
git --no-pager status -sb || mark_fail "git status failed"
git --no-pager log --oneline --decorate -5 || true

echo
echo "===== resolve adb ====="
if [ -n "$SERIAL" ]; then
  export MORPHE_ADB_SERIAL="$SERIAL"
fi
SERIAL="$(
  tools/boost-adb-serial.sh \
    --hint "${MORPHE_ADB_HINT:-192.168.1.248}" \
    --expect-model "${MORPHE_ADB_EXPECT_MODEL:-Pixel_6}" \
    --expect-device "${MORPHE_ADB_EXPECT_DEVICE:-oriole}"
)" || mark_fail "adb serial resolve failed"
echo "SERIAL=$SERIAL"
adb -s "$SERIAL" get-state || mark_fail "adb target unavailable"

echo
echo "===== build/select MPP ====="
if [ -z "$MPP" ]; then
  ./gradlew :patches:buildAndroid --no-daemon || mark_fail "buildAndroid failed"
  if [ -n "$VERSION" ]; then
    MPP="$(tools/boost-resolve-mpp.sh --version "$VERSION")" \
      || mark_fail "canonical Android MPP resolve failed"
  else
    MPP="$(tools/boost-resolve-mpp.sh)" \
      || mark_fail "canonical Android MPP resolve failed"
  fi
fi

[ -f "$MPP" ] || mark_fail "MPP not found: $MPP"
echo "MPP=$MPP"
sha256sum "$MPP" || true
tools/check-mpp-release-asset.sh "$MPP" || mark_fail "MPP missing required Android entries"

echo
echo "===== build/install DEV clone ====="
CMD=(tools/boost-dev-from-mpp.sh
  --mpp "$MPP"
  --name "$NAME"
  --dev-package "$DEV_PACKAGE"
  --normal-package "$NORMAL_PACKAGE"
  --label "Boost Dev"
  --expected-target-sdk 35
  --no-verify-with-sdk
  --install
  --serial "$SERIAL"
)
for marker in "${MARKERS[@]}"; do
  CMD+=(--marker "$marker")
done
printf '%q ' "${CMD[@]}"
echo
"${CMD[@]}" || mark_fail "DEV build/install failed"

echo
echo "===== package after install ====="
adb -s "$SERIAL" shell pm path "$DEV_PACKAGE" || mark_fail "DEV package not installed"

echo
echo "===== runtime ====="
adb -s "$SERIAL" logcat -c || mark_fail "logcat clear failed"
adb -s "$SERIAL" shell am force-stop "$DEV_PACKAGE" || true

if [ -n "$URL" ]; then
  adb -s "$SERIAL" shell am start -W \
    -a android.intent.action.VIEW \
    -d "$URL" \
    -p "$DEV_PACKAGE" | tee "$OUT/am-start.txt"
else
  adb -s "$SERIAL" shell monkey -p "$DEV_PACKAGE" -c android.intent.category.LAUNCHER 1 | tee "$OUT/monkey-start.txt"
fi

timeout "${WATCH_SECONDS}s" adb -s "$SERIAL" logcat -v time > "$OUT/logcat.txt" || true

grep -Ei 'morphe|LoggingUtils|ClassCastException|FATAL EXCEPTION|NullNode|ArrayNode|LoadCommentsAsync|AndroidRuntime|com\.android\.commands\.monkey' \
  "$OUT/logcat.txt" > "$OUT/logcat-filtered.txt" || true

adb -s "$SERIAL" shell dumpsys activity activities > "$OUT/activity.txt" || true

echo
echo "===== classify ====="
grep -E 'com\.android\.commands\.monkey|VM exiting with result code 0' "$OUT/logcat-filtered.txt" > "$OUT/monkey-noise.txt" || true
grep -Ei 'LoggingUtils: Exception|E/morphe:.*Exception|ClassCastException|NullNode cannot be cast|FATAL EXCEPTION' \
  "$OUT/logcat-filtered.txt" > "$OUT/blockers.txt" || true

echo "FILTERED_LINES=$(wc -l < "$OUT/logcat-filtered.txt" | tr -d ' ')"
echo "MONKEY_NOISE_LINES=$(wc -l < "$OUT/monkey-noise.txt" | tr -d ' ')"
echo "BLOCKER_LINES=$(wc -l < "$OUT/blockers.txt" | tr -d ' ')"

if [ -s "$OUT/blockers.txt" ]; then
  echo "===== blockers ====="
  sed -n '1,120p' "$OUT/blockers.txt"
  mark_fail "runtime blocker observed"
fi

echo
echo "===== activity summary ====="
grep -E 'mResumedActivity|topResumedActivity|ResumedActivity|CommentsActivity|CommentsIntentActivity|MainActivity|com\.rubenmayayo\.reddit\.dev' \
  "$OUT/activity.txt" | tail -30 || true

echo
echo "OUT=$OUT"
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_BOOST_DEV_ISSUE_RUNTIME_OK"
else
  echo "RESULT=MORPHE_BOOST_DEV_ISSUE_RUNTIME_FAIL"
fi
exit "$FAIL"
