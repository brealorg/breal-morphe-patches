#!/usr/bin/env bash
set -u

FAIL=0
SERIAL=""
PACKAGE="com.rubenmayayo.reddit.dev"
OUT_ROOT="/tmp"
NAME="boost-dev-runtime"
CLASSIFIER=0
ALLOW_WARN=1
FAIL_ON_WARN=0
CLEAR_LOGCAT=1
PULL_APK=1
PROMPT=1
EXPECT_ACTIVITIES=()
EXPECT_LOGS=()
REQUIRE_PATTERNS=()
HARD_PATTERNS=()
MARKERS=()

usage() {
  cat <<'EOF'
Usage:
  tools/boost-dev-runtime-capture.sh --serial SERIAL [options]

Capture Boost DEV runtime evidence after a manual interaction.

Options:
  --serial SERIAL             adb serial. Required.
  --package PACKAGE           Target package. Default: com.rubenmayayo.reddit.dev.
  --name NAME                 Capture name suffix. Default: boost-dev-runtime.
  --out-root DIR              Output root. Default: /tmp.
  --expect-activity TEXT      Expected activity marker in dumpsys/logcat. Repeatable.
  --expect-log TEXT           Required substring in logcat/activity proof. Repeatable.
  --require-pattern TEXT      Pass-through required classifier pattern. Repeatable.
  --hard-pattern TEXT         Pass-through hard blocker classifier pattern. Repeatable.
  --marker TEXT               Marker required inside installed APK dex. Repeatable.
  --classifier                Run tools/classify-runtime-logcat.sh.
  --fail-on-warn              Classifier WARN exits nonzero.
  --allow-warn                Classifier WARN exits zero. Default.
  --no-clear-logcat           Do not clear logcat before interaction.
  --no-pull-apk               Do not pull installed APK / marker scan.
  --no-prompt                 Do not wait for ENTER before capture.
  -h, --help                  Show this help.

Workflow:
  1. Verify adb/package.
  2. Optionally pull installed APK and marker-scan.
  3. Clear logcat unless disabled.
  4. Prompt user to perform interaction.
  5. Capture logcat, dumpsys activity, recents, appwidget, package.
  6. Check expected activity/log markers.
  7. Optionally run runtime classifier.

Output:
  <out-root>/<name>.<timestamp>/
  <out-root>/<name>.<timestamp>.tar.gz
  Prints RESULT=MORPHE_BOOST_DEV_RUNTIME_CAPTURE_OK on success.
EOF
}

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --package)
      PACKAGE="${2:-}"
      shift 2
      ;;
    --name)
      NAME="${2:-}"
      shift 2
      ;;
    --out-root)
      OUT_ROOT="${2:-}"
      shift 2
      ;;
    --expect-activity)
      EXPECT_ACTIVITIES+=("${2:-}")
      shift 2
      ;;
    --expect-log)
      EXPECT_LOGS+=("${2:-}")
      shift 2
      ;;
    --require-pattern)
      REQUIRE_PATTERNS+=("${2:-}")
      shift 2
      ;;
    --hard-pattern)
      HARD_PATTERNS+=("${2:-}")
      shift 2
      ;;
    --marker)
      MARKERS+=("${2:-}")
      shift 2
      ;;
    --classifier)
      CLASSIFIER=1
      shift
      ;;
    --fail-on-warn)
      FAIL_ON_WARN=1
      ALLOW_WARN=0
      shift
      ;;
    --allow-warn)
      ALLOW_WARN=1
      FAIL_ON_WARN=0
      shift
      ;;
    --no-clear-logcat)
      CLEAR_LOGCAT=0
      shift
      ;;
    --no-pull-apk)
      PULL_APK=0
      shift
      ;;
    --no-prompt)
      PROMPT=0
      shift
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

if [ -z "$SERIAL" ]; then
  echo "Missing required --serial" >&2
  usage >&2
  exit 2
fi

# PL-10: runtime scripts must use the explicit serial passed by the caller.
# ANDROID_SERIAL is intentionally ignored by all adb calls in this script via
# `env -u ANDROID_SERIAL adb -s "$SERIAL" ...`; make that visible in logs.
if [ -n "${ANDROID_SERIAL:-}" ] && [ "${ANDROID_SERIAL}" != "$SERIAL" ]; then
  echo "INFO: ignoring ANDROID_SERIAL=${ANDROID_SERIAL}; using explicit --serial=${SERIAL}" >&2
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${OUT_ROOT%/}/${NAME}.${timestamp}"
mkdir -p "$OUT_DIR"

exec > >(tee "$OUT_DIR/boost-dev-runtime-capture.log") 2>&1

echo "BOOST DEV RUNTIME CAPTURE"
echo "Started: $(date --iso-8601=seconds)"
echo "SERIAL=$SERIAL"
echo "PACKAGE=$PACKAGE"
echo "NAME=$NAME"
echo "OUT_DIR=$OUT_DIR"
echo "CLASSIFIER=$CLASSIFIER"
echo "CLEAR_LOGCAT=$CLEAR_LOGCAT"
echo "PULL_APK=$PULL_APK"
echo "PROMPT=$PROMPT"
printf 'EXPECT_ACTIVITIES=%s\n' "${EXPECT_ACTIVITIES[*]:-}"
printf 'EXPECT_LOGS=%s\n' "${EXPECT_LOGS[*]:-}"
printf 'REQUIRE_PATTERNS=%s\n' "${REQUIRE_PATTERNS[*]:-}"
printf 'HARD_PATTERNS=%s\n' "${HARD_PATTERNS[*]:-}"
printf 'MARKERS=%s\n' "${MARKERS[*]:-}"

echo
echo "===== adb/package sanity ====="
env -u ANDROID_SERIAL adb -s "$SERIAL" get-state | tee "$OUT_DIR/adb-state.txt" || mark_fail "adb unavailable"

env -u ANDROID_SERIAL adb -s "$SERIAL" shell dumpsys package "$PACKAGE" > "$OUT_DIR/package.txt" \
  || mark_fail "package dumpsys failed"

grep -E 'versionName|versionCode|targetSdk|firstInstallTime|lastUpdateTime|installerPackageName' "$OUT_DIR/package.txt" \
  | tee "$OUT_DIR/package-summary.txt" || mark_fail "package summary missing"

echo
echo "===== installed APK marker check ====="
if [ "$FAIL" -eq 0 ] && [ "$PULL_APK" -eq 1 ]; then
  APK_PATH="$(env -u ANDROID_SERIAL adb -s "$SERIAL" shell pm path "$PACKAGE" | sed 's/package://' | tr -d '\r' | head -1)"
  echo "APK_PATH=$APK_PATH" | tee "$OUT_DIR/installed-apk-path.txt"

  if [ -n "$APK_PATH" ]; then
    env -u ANDROID_SERIAL adb -s "$SERIAL" pull "$APK_PATH" "$OUT_DIR/installed.apk" \
      2>&1 | tee "$OUT_DIR/pull-apk.txt" || mark_fail "pull installed APK failed"
  else
    mark_fail "installed APK path missing"
  fi

  if [ -s "$OUT_DIR/installed.apk" ]; then
    aapt dump badging "$OUT_DIR/installed.apk" > "$OUT_DIR/installed.badging" \
      || mark_fail "installed APK badging failed"
    grep -E "package:|sdkVersion:|targetSdkVersion:" "$OUT_DIR/installed.badging" || true

    for marker in "${MARKERS[@]}"; do
      if unzip -p "$OUT_DIR/installed.apk" '*.dex' 2>/dev/null | strings | grep -F "$marker" >/dev/null; then
        echo "OK: installed APK marker present: $marker"
      else
        mark_fail "installed APK marker missing: $marker"
      fi
    done
  else
    mark_fail "installed APK pull missing"
  fi
fi

echo
echo "===== clear logcat ====="
if [ "$CLEAR_LOGCAT" -eq 1 ]; then
  env -u ANDROID_SERIAL adb -s "$SERIAL" logcat -c || true
else
  echo "SKIP: clear logcat disabled"
fi

echo
echo "===== interaction point ====="
if [ "$PROMPT" -eq 1 ]; then
  echo "Utfør handlingen på telefonen nå. Vent til relevant skjerm er åpnet, trykk deretter ENTER her."
  read -r _
else
  echo "SKIP: prompt disabled"
fi

echo
echo "===== capture ====="
env -u ANDROID_SERIAL adb -s "$SERIAL" logcat -d -v time > "$OUT_DIR/logcat.txt" || mark_fail "logcat capture failed"
env -u ANDROID_SERIAL adb -s "$SERIAL" shell dumpsys activity activities > "$OUT_DIR/activity.txt" || true
env -u ANDROID_SERIAL adb -s "$SERIAL" shell dumpsys activity recents > "$OUT_DIR/recents.txt" || true
env -u ANDROID_SERIAL adb -s "$SERIAL" shell dumpsys appwidget > "$OUT_DIR/appwidget.txt" || true
env -u ANDROID_SERIAL adb -s "$SERIAL" shell dumpsys package "$PACKAGE" > "$OUT_DIR/package-after.txt" || true

echo
echo "===== proof filters ====="
grep -E 'topResumedActivity|mResumedActivity|ResumedActivity|Hist #' "$OUT_DIR/activity.txt" \
  | grep -E "$PACKAGE|reddit|ActivityRecord|Hist #" \
  | head -120 \
  | tee "$OUT_DIR/top-activity.txt" || true

grep -Ei 'START u0|Transition requested|baseIntent|topActivity|dat=|has extras|PendingIntent|RemoteViews|AppWidget|CommentsActivity|MainActivity|ImageWidgetProvider|WidgetProvider' "$OUT_DIR/logcat.txt" \
  | tail -260 \
  | tee "$OUT_DIR/logcat-proof.txt" || true

grep -E "$PACKAGE|ImageWidgetProvider|WidgetProvider|RemoteViews|provider|views=" "$OUT_DIR/appwidget.txt" \
  | head -260 \
  | tee "$OUT_DIR/appwidget-proof.txt" || true

echo
echo "===== expectations ====="
for activity in "${EXPECT_ACTIVITIES[@]}"; do
  if grep -F "$activity" "$OUT_DIR/top-activity.txt" "$OUT_DIR/activity.txt" "$OUT_DIR/logcat-proof.txt" >/dev/null 2>&1; then
    echo "OK: expected activity marker present: $activity"
  else
    mark_fail "expected activity marker missing: $activity"
  fi
done

for expected in "${EXPECT_LOGS[@]}"; do
  if grep -F "$expected" "$OUT_DIR/logcat-proof.txt" "$OUT_DIR/logcat.txt" "$OUT_DIR/activity.txt" >/dev/null 2>&1; then
    echo "OK: expected log/activity marker present: $expected"
  else
    mark_fail "expected log/activity marker missing: $expected"
  fi
done

echo
echo "===== runtime classifier ====="
if [ "$CLASSIFIER" -eq 1 ]; then
  CLASSIFIER_ARGS=(
    --logcat "$OUT_DIR/logcat.txt"
    --out-dir "$OUT_DIR/runtime-classification"
    --package "$PACKAGE"
  )

  for activity in "${EXPECT_ACTIVITIES[@]}"; do
    CLASSIFIER_ARGS+=(--expect-activity "$activity")
  done

  for pattern in "${REQUIRE_PATTERNS[@]}"; do
    CLASSIFIER_ARGS+=(--require-pattern "$pattern")
  done

  for pattern in "${HARD_PATTERNS[@]}"; do
    CLASSIFIER_ARGS+=(--hard-pattern "$pattern")
  done

  if [ "$FAIL_ON_WARN" -eq 1 ]; then
    CLASSIFIER_ARGS+=(--fail-on-warn)
  elif [ "$ALLOW_WARN" -eq 1 ]; then
    CLASSIFIER_ARGS+=(--allow-warn)
  fi

  tools/classify-runtime-logcat.sh "${CLASSIFIER_ARGS[@]}" \
    2>&1 | tee "$OUT_DIR/runtime-classifier.txt" || mark_fail "runtime classifier failed"

  if grep -F "RUNTIME_CLASSIFIER_WRAPPER_CLASSIFICATION=PASS" "$OUT_DIR/runtime-classifier.txt" >/dev/null; then
    echo "OK: runtime classifier PASS"
  elif grep -F "RUNTIME_CLASSIFIER_WRAPPER_CLASSIFICATION=WARN" "$OUT_DIR/runtime-classifier.txt" >/dev/null && [ "$ALLOW_WARN" -eq 1 ]; then
    echo "WARN: runtime classifier WARN allowed"
  else
    mark_fail "runtime classifier did not PASS"
  fi
else
  echo "SKIP: classifier disabled"
fi

echo
echo "===== archive ====="
ARCHIVE="$OUT_DIR.tar.gz"
tar -czf "$ARCHIVE" -C "$(dirname "$OUT_DIR")" "$(basename "$OUT_DIR")" \
  || mark_fail "archive failed"
ls -lh "$ARCHIVE" | tee "$OUT_DIR/archive.txt" || true

echo
echo "===== summary ====="
echo "OUT_DIR=$OUT_DIR"
echo "ARCHIVE=$ARCHIVE"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_BOOST_DEV_RUNTIME_CAPTURE_OK"
else
  echo "RESULT=MORPHE_BOOST_DEV_RUNTIME_CAPTURE_FAIL"
fi

exit "$FAIL"
