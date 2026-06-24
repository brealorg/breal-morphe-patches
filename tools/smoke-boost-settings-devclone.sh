#!/usr/bin/env bash

FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

SIGNED_APK="${1:-}"
DEV_PKG="${2:-com.rubenmayayo.reddit.devsettings}"

if [ -z "$SIGNED_APK" ] || [ "$SIGNED_APK" = "--help" ] || [ "$SIGNED_APK" = "-h" ]; then
  cat <<'HELP'
Usage:
  tools/smoke-boost-settings-devclone.sh <signed-devclone-apk> [dev-package]

Purpose:
  Runtime smoke test for Boost Morphe settings UI.

Notes:
  - Only touches the dev package, default com.rubenmayayo.reddit.devsettings.
  - Normal Boost com.rubenmayayo.reddit is not modified.
  - The logcat watch runs up to 90 seconds and may look idle. Do not press Ctrl+C unless intentionally aborting.
HELP
  exit 0
fi

echo "===== input ====="
echo "SIGNED_APK=$SIGNED_APK"
echo "DEV_PKG=$DEV_PKG"
test -f "$SIGNED_APK" || mark_fail "signed APK missing: $SIGNED_APK"

if [ -f "$SIGNED_APK" ]; then
  ls -lh "$SIGNED_APK"
  sha256sum "$SIGNED_APK"
fi

echo
echo "===== package summary ====="
if command -v aapt >/dev/null 2>&1 && [ -f "$SIGNED_APK" ]; then
  aapt dump badging "$SIGNED_APK" | sed -n '1,24p'
fi

echo
echo "===== remove old dev package only ====="
if adb shell pm path "$DEV_PKG" 2>/dev/null | grep -q '^package:'; then
  adb uninstall "$DEV_PKG" || mark_fail "failed to uninstall old $DEV_PKG"
else
  echo "OK: $DEV_PKG not installed"
fi

echo
echo "===== install devclone ====="
if [ "$FAIL" -eq 0 ]; then
  adb install -r "$SIGNED_APK" || mark_fail "adb install failed"
fi

echo
echo "===== launch and watch settings runtime ====="
if [ "$FAIL" -eq 0 ]; then
  adb logcat -c

  echo "Launching Boost Settings Test..."
  adb shell am start -n "$DEV_PKG/com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity" \
    || mark_fail "main launch failed"

  echo
  echo "ACTION on phone:"
  echo "  1. Open Boost Settings Test."
  echo "  2. Open Settings."
  echo "  3. Tap Morphe."
  echo "  4. Toggle the three switches once."
  echo
  echo "Do not press Ctrl+C. This may look idle until the 90s timeout finishes."

  WORK="$(dirname "$SIGNED_APK")"
  LOGCAT="$WORK/settings-runtime-smoke-logcat.txt"

  timeout 90s adb logcat \
    | grep -Ei 'BoostMorphePreferenceFragment|BoostMediaPreferences|morphe_boost_|Morphe|PreferenceFragment|Fragment\$InstantiationException|ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|InflateException|AndroidRuntime|FATAL EXCEPTION|com\.rubenmayayo\.reddit\.devsettings' \
    | tee "$LOGCAT"

  echo
  echo "===== runtime log classification ====="
  if grep -Ei 'FATAL EXCEPTION|Fragment\$InstantiationException|ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|InflateException' "$LOGCAT" >/dev/null 2>&1; then
    mark_fail "runtime crash/error found in settings smoke log"
  else
    echo "OK: no settings/fragment crash markers captured"
  fi

  echo "LOGCAT=$LOGCAT"
fi

echo
echo "===== installed dev package recap ====="
adb shell pm path "$DEV_PKG" || true
adb shell dumpsys package "$DEV_PKG" \
  | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=|targetSdk=|signatures=' \
  | head -80 || true

echo
echo "===== result ====="
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=BOOST_SETTINGS_DEVCLONE_RUNTIME_SMOKE_GREEN"
else
  echo "RESULT=BOOST_SETTINGS_DEVCLONE_RUNTIME_SMOKE_FAIL"
fi
echo "Terminal still alive."
exit "$FAIL"
