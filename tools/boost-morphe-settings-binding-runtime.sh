#!/usr/bin/env bash
set -u

DEV_PACKAGE='com.rubenmayayo.reddit.dev'
NORMAL_PACKAGE='com.rubenmayayo.reddit'
SETTINGS_ACTIVITY='com.rubenmayayo.reddit.ui.preferences.v2.SettingsActivityCompat'
AUDIT_FRAGMENT='app.morphe.extension.boostforreddit.settings.MorpheSettingsV4BindingRuntimeAuditFragment'
EXTRA_FRAGMENT='extra_show_fragment'
EXTRA_PHASE='morphe_settings_binding_audit_phase'
EXTRA_NONCE='morphe_settings_binding_audit_nonce'
EXTRA_SCOPE='morphe_settings_binding_audit_scope'
SCOPE='appearance-layout'
SERIAL=''
AUDIT_ACTIVE=0
HOST_LOCK=''
LOCK_HELD=0
RECOVER_ORPHANED=0
REPORT_READY=0
REPORT_WRITTEN=0
FAIL_REASON=''
NORMAL_UPDATE_BEFORE=''
NORMAL_UNTOUCHED='unknown'
AUDIT_LOG_FILE=''
APK_COPY=''
REPORT_PATH=''
DEV_UID=''
APK_SHA256=''
VERSION_NAME='unknown'
VERSION_CODE='unknown'
ANDROID_RELEASE='unknown'
ANDROID_SDK='unknown'
DEVICE='unknown'
MODEL='unknown'
ADB=()

usage() {
  printf '%s\n' \
    'Usage: MORPHE_RUNTIME_AUDIT_MUTATE_DEV=1 tools/boost-morphe-settings-binding-runtime.sh [--scope appearance-layout] [--serial SERIAL]' \
    'Recovery: MORPHE_RUNTIME_AUDIT_RECOVER_ORPHANED_DEV=1 tools/boost-morphe-settings-binding-runtime.sh --recover-orphaned [--serial SERIAL]' \
    '' \
    'Runs the reversible Morphe settings audit against Boost DEV only.' \
    'Every registered domain is exercised through the real UI action method.' \
    'Native consumers, stable rendered properties, cold reload, and recovery are verified.' \
    'Boost DEV must be built with --debuggable-dev so the audit gateway is present.' \
    'A machine-readable JSON report is written under local-artifacts/boost-settings-audits.' \
    'Normal Boost is read-only and is never stopped, launched, or modified.'
}

fail() {
  FAIL_REASON="$*"
  echo "FAIL: $*" >&2
  exit 1
}

start_phase() {
  local phase="$1"
  "${ADB[@]}" shell am start -W \
    -n "${DEV_PACKAGE}/${SETTINGS_ACTIVITY}" \
    --es "$EXTRA_FRAGMENT" "$AUDIT_FRAGMENT" \
    --es "$EXTRA_PHASE" "$phase" \
    --es "$EXTRA_NONCE" "$NONCE" \
    --es "$EXTRA_SCOPE" "$SCOPE"
}

audit_log() {
  "${ADB[@]}" logcat -d -v brief -s 'MorpheSettingsAudit:I' '*:S'
}

capture_audit_log() {
  [ -n "$AUDIT_LOG_FILE" ] || return 0
  audit_log >"$AUDIT_LOG_FILE" 2>/dev/null || true
}

normal_last_update() {
  "${ADB[@]}" shell dumpsys package "$NORMAL_PACKAGE" \
    | sed -n 's/^[[:space:]]*lastUpdateTime=//p' \
    | head -1 \
    | tr -d '\r'
}

write_report() {
  local status="$1"
  local reason="$2"
  [ "$REPORT_READY" -eq 1 ] || return 0
  python3 tools/write-boost-morphe-settings-audit-report.py \
    --log "$AUDIT_LOG_FILE" \
    --manifest tests/tools/fixtures/boost_morphe_settings_bindings.json \
    --output "$REPORT_PATH" \
    --status "$status" \
    --failure-reason "$reason" \
    --package "$DEV_PACKAGE" \
    --version-name "$VERSION_NAME" \
    --version-code "$VERSION_CODE" \
    --apk-sha256 "$APK_SHA256" \
    --android-release "$ANDROID_RELEASE" \
    --android-sdk "$ANDROID_SDK" \
    --device "$DEVICE" \
    --model "$MODEL" \
    --normal-boost-untouched "$NORMAL_UNTOUCHED"
}

recover_on_exit() {
  local rc=$?
  local normal_after=''
  trap - EXIT INT TERM
  if [ "$AUDIT_ACTIVE" -eq 1 ]; then
    echo 'RECOVERY=ATTEMPTING_DEV_SETTINGS_AND_ICON_RESTORE' >&2
    "${ADB[@]}" shell am force-stop "$DEV_PACKAGE" >/dev/null 2>&1 || true
    start_phase recover >/dev/null 2>&1 || true
    capture_audit_log
    audit_log | tail -40 >&2 || true
    "${ADB[@]}" shell am force-stop "$DEV_PACKAGE" >/dev/null 2>&1 || true
  fi
  if [ -n "$NORMAL_UPDATE_BEFORE" ]; then
    normal_after="$(normal_last_update 2>/dev/null || true)"
    if [ -n "$normal_after" ] && [ "$normal_after" = "$NORMAL_UPDATE_BEFORE" ]; then
      NORMAL_UNTOUCHED='true'
    else
      NORMAL_UNTOUCHED='false'
    fi
  fi
  if [ "$REPORT_READY" -eq 1 ] && [ "$REPORT_WRITTEN" -eq 0 ]; then
    capture_audit_log
    write_report FAIL "${FAIL_REASON:-audit interrupted}" >&2 || true
  fi
  if [ "$LOCK_HELD" -eq 1 ]; then
    flock -u 9 2>/dev/null || true
    exec 9>&-
  fi
  if [ -n "$APK_COPY" ]; then
    rm -f -- "$APK_COPY"
  fi
  exit "$rc"
}

trap recover_on_exit EXIT
trap 'FAIL_REASON=interrupted; exit 130' INT
trap 'FAIL_REASON=terminated; exit 143' TERM

while [ "$#" -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --scope) SCOPE="${2:-}"; shift 2 ;;
    --recover-orphaned) RECOVER_ORPHANED=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[ "$SCOPE" = 'appearance-layout' ] \
  || fail "unsupported audit scope: $SCOPE"
if [ "$RECOVER_ORPHANED" -eq 1 ]; then
  [ "${MORPHE_RUNTIME_AUDIT_RECOVER_ORPHANED_DEV:-}" = '1' ] || {
    usage
    fail 'set MORPHE_RUNTIME_AUDIT_RECOVER_ORPHANED_DEV=1 to authorize forced DEV recovery'
  }
else
  [ "${MORPHE_RUNTIME_AUDIT_MUTATE_DEV:-}" = '1' ] || {
    usage
    fail 'set MORPHE_RUNTIME_AUDIT_MUTATE_DEV=1 to authorize temporary DEV-only settings writes'
  }
fi

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" \
  || fail 'not inside repository'
cd "$ROOT" || fail 'cannot enter repository root'

if [ -n "$SERIAL" ]; then
  export MORPHE_ADB_SERIAL="$SERIAL"
fi
SERIAL="$(env -u ANDROID_SERIAL tools/boost-adb-serial.sh)" \
  || fail 'could not resolve adb target'
ADB=(env -u ANDROID_SERIAL adb -s "$SERIAL")
NONCE="settings-audit-v56-$(date +%s)-$$"

LOCK_KEY="$(printf '%s' "$SERIAL" | tr -c 'A-Za-z0-9._-' '_')"
HOST_LOCK="/tmp/morphe-boost-settings-audit-${LOCK_KEY}.lock"
command -v flock >/dev/null 2>&1 \
  || fail 'flock is required for crash-safe host locking'
exec 9>"$HOST_LOCK" || fail "could not open ${HOST_LOCK}"
flock -n 9 \
  || fail "another settings audit owns ${HOST_LOCK}"
LOCK_HELD=1

"${ADB[@]}" get-state >/dev/null \
  || fail "adb target unavailable: $SERIAL"
"${ADB[@]}" shell pm path "$DEV_PACKAGE" | rg -q '^package:' \
  || fail "Boost DEV is not installed: $DEV_PACKAGE"
DEV_UID="$("${ADB[@]}" shell dumpsys package "$DEV_PACKAGE" \
  | sed -n 's/^[[:space:]]*\(userId\|appId\)=\([0-9][0-9]*\).*$/\2/p' \
  | head -1 \
  | tr -d '\r')"
if [ -z "$DEV_UID" ]; then
  DEV_UID="$("${ADB[@]}" shell pm list packages -U "$DEV_PACKAGE" \
    | tr -d '\r' \
    | sed -n "s/^package:${DEV_PACKAGE}[[:space:]]uid:\([0-9][0-9]*\)$/\1/p" \
    | head -1)"
fi
case "$DEV_UID" in
  ''|*[!0-9]*) fail 'could not resolve Boost DEV uid from userId, appId, or pm -U' ;;
esac
"${ADB[@]}" shell dumpsys package "$DEV_PACKAGE" \
  | rg -q 'pkgFlags=.*DEBUGGABLE' \
  || fail 'Boost DEV is not debuggable; rebuild it with the Morphe "Enable Android debugging" patch'
RUN_AS_ID="$("${ADB[@]}" shell run-as "$DEV_PACKAGE" id 2>/dev/null | tr -d '\r')"
printf '%s\n' "$RUN_AS_ID" | rg -q "^uid=${DEV_UID}\\(" \
  || fail "run-as did not resolve to Boost DEV uid ${DEV_UID}"

if [ "$RECOVER_ORPHANED" -eq 1 ]; then
  NORMAL_UPDATE_BEFORE="$(normal_last_update)"
  [ -n "$NORMAL_UPDATE_BEFORE" ] \
    || fail 'could not capture Normal Boost package timestamp'
  "${ADB[@]}" logcat -c
  "${ADB[@]}" shell am force-stop "$DEV_PACKAGE"
  start_phase recover_force >/dev/null
  RECOVERY_LOG="$(audit_log)"
  printf '%s\n' "$RECOVERY_LOG" \
    | rg -q 'MORPHE_BINDING_AUDIT_RECOVERY_(OK|NOT_NEEDED)' \
    || fail 'forced DEV recovery did not complete'
  "${ADB[@]}" shell am force-stop "$DEV_PACKAGE"
  NORMAL_UPDATE_AFTER="$(normal_last_update)"
  [ "$NORMAL_UPDATE_BEFORE" = "$NORMAL_UPDATE_AFTER" ] \
    || fail 'Normal Boost package changed during DEV recovery'
  NORMAL_UNTOUCHED='true'
  echo 'DEV_ORPHANED_SETTINGS_AND_ICON_RECOVERY=PASS'
  echo 'NORMAL_BOOST_UNTOUCHED=PASS'
  echo 'RESULT=MORPHE_BOOST_SETTINGS_AUDIT_V56_ORPHAN_RECOVERY_PASS'
  exit 0
fi

PACKAGE_DUMP="$("${ADB[@]}" shell dumpsys package "$DEV_PACKAGE")"
VERSION_NAME="$(printf '%s\n' "$PACKAGE_DUMP" \
  | sed -n 's/^[[:space:]]*versionName=//p' | head -1 | tr -d '\r')"
VERSION_CODE="$(printf '%s\n' "$PACKAGE_DUMP" \
  | sed -n 's/^[[:space:]]*versionCode=\([0-9][0-9]*\).*$/\1/p' \
  | head -1 | tr -d '\r')"
INSTALLED_APK_PATH="$("${ADB[@]}" shell pm path "$DEV_PACKAGE" \
  | sed -n 's/^package://p' | head -1 | tr -d '\r')"
[ -n "$INSTALLED_APK_PATH" ] || fail 'could not resolve installed DEV APK'
APK_COPY="$(mktemp /tmp/morphe-settings-audit-v56-apk-XXXXXX.apk)" \
  || fail 'could not allocate APK verification file'
"${ADB[@]}" pull "$INSTALLED_APK_PATH" "$APK_COPY" >/dev/null \
  || fail 'could not pull installed DEV APK for hashing'
APK_SHA256="$(sha256sum "$APK_COPY" | cut -d' ' -f1)"
ANDROID_RELEASE="$("${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r')"
ANDROID_SDK="$("${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
DEVICE="$("${ADB[@]}" shell getprop ro.product.device | tr -d '\r')"
MODEL="$("${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"

REPORT_DIR='local-artifacts/boost-settings-audits'
mkdir -p "$REPORT_DIR" || fail 'could not create audit report directory'
REPORT_PATH="${REPORT_DIR}/morphe-boost-settings-${SCOPE}-v56-$(date -u +%Y%m%dT%H%M%SZ).json"
AUDIT_LOG_FILE="$(mktemp /tmp/morphe-settings-audit-v56-log-XXXXXX.txt)" \
  || fail 'could not allocate audit log file'
REPORT_READY=1

echo 'DEV_DEBUGGABLE=PASS'
echo 'DEV_RUN_AS_UID=PASS'
echo 'DEV_AUDIT_GATEWAY_LAUNCH=ARMED'
echo 'DEV_AUDIT_HOST_LOCK=PASS'
echo "AUDIT_SCOPE=$SCOPE"

NORMAL_UPDATE_BEFORE="$(normal_last_update)"
[ -n "$NORMAL_UPDATE_BEFORE" ] \
  || fail 'could not capture Normal Boost package timestamp'
"${ADB[@]}" logcat -c
"${ADB[@]}" shell am force-stop "$DEV_PACKAGE"

AUDIT_ACTIVE=1
start_phase write >/dev/null
WRITE_LOG="$(audit_log)"
printf '%s\n' "$WRITE_LOG" | rg -F 'MORPHE_BINDING_AUDIT_WRITE_OK' >/dev/null \
  || fail 'DEV write phase did not complete'
printf '%s\n' "$WRITE_LOG" \
  | rg -F 'MORPHE_DOMAIN_AUDIT_OK items=26 actions=196' >/dev/null \
  || fail 'DEV full-domain UI-action audit did not complete'
ITEM_COUNT="$(printf '%s\n' "$WRITE_LOG" \
  | rg -c -F 'MORPHE_AUDIT_ITEM_OK')"
[ "$ITEM_COUNT" = '26' ] \
  || fail "expected 26 audit items, got ${ITEM_COUNT}"
printf '%s\n' "$WRITE_LOG" \
  | rg -F 'MORPHE_RENDER_AUDIT_OK count=6' >/dev/null \
  || fail 'DEV rendered-effect probes did not complete'
RENDER_ITEM_COUNT="$(printf '%s\n' "$WRITE_LOG" \
  | rg -c -F 'MORPHE_RENDER_AUDIT_ITEM_OK')"
[ "$RENDER_ITEM_COUNT" = '6' ] \
  || fail "expected 6 render probes, got ${RENDER_ITEM_COUNT}"
printf '%s\n' "$WRITE_LOG" \
  | rg -F 'MORPHE_BINDING_AUDIT_APP_ICONS_OK' >/dev/null \
  || fail 'DEV app-icon component audit did not complete'
echo 'DEV_ALL_REGISTERED_DOMAINS_VIA_UI_ACTIONS=PASS'
echo 'DEV_APPEARANCE_LAYOUT_NATIVE_CONSUMERS=PASS'
echo 'DEV_RENDERED_EFFECT_PROBES=PASS'
echo 'DEV_APP_ICON_FULL_DOMAIN=PASS'
echo 'DEV_APP_ICON_DURABLE_SNAPSHOT=PASS'

"${ADB[@]}" shell am force-stop "$DEV_PACKAGE"
start_phase verify_restore >/dev/null
VERIFY_LOG="$(audit_log)"
printf '%s\n' "$VERIFY_LOG" \
  | rg -F 'MORPHE_BINDING_AUDIT_RELOAD_OK' >/dev/null \
  || fail 'DEV cold-reload verification did not complete'
printf '%s\n' "$VERIFY_LOG" \
  | rg -F 'MORPHE_DOMAIN_AUDIT_RELOAD_OK count=26' >/dev/null \
  || fail 'DEV consumers and app icon did not reload expected values'
printf '%s\n' "$VERIFY_LOG" \
  | rg -F 'MORPHE_BINDING_AUDIT_RESTORE_OK' >/dev/null \
  || fail 'DEV settings and app-icon restoration did not complete'
printf '%s\n' "$VERIFY_LOG" \
  | rg -F 'RESULT=MORPHE_BOOST_SETTINGS_APPEARANCE_LAYOUT_AUDIT_V56_APP_PASS' >/dev/null \
  || fail 'DEV in-app audit did not emit V56 PASS'
AUDIT_ACTIVE=0

echo 'DEV_COLD_RELOAD=PASS'
echo 'DEV_NATIVE_CONSUMERS_AND_ICON_AFTER_COLD_RELOAD=PASS'
echo 'DEV_ORIGINAL_SETTINGS_AND_ICON_RESTORED=PASS'

ERROR_LOG="$("${ADB[@]}" logcat --uid="$DEV_UID" -d -v threadtime \
  | rg 'FATAL EXCEPTION|AndroidRuntime|NoSuchMethodError|NoClassDefFoundError|Resources\$NotFoundException|InflateException' \
  || true)"
[ -z "$ERROR_LOG" ] || {
  printf '%s\n' "$ERROR_LOG" >&2
  fail 'DEV runtime errors observed'
}
echo 'DEV_RUNTIME_ERRORS=ABSENT'

NORMAL_UPDATE_AFTER="$(normal_last_update)"
[ "$NORMAL_UPDATE_BEFORE" = "$NORMAL_UPDATE_AFTER" ] \
  || fail 'Normal Boost package changed during DEV audit'
NORMAL_UNTOUCHED='true'
echo 'NORMAL_BOOST_UNTOUCHED=PASS'

capture_audit_log
write_report PASS '' || fail 'structured audit report failed closed'
REPORT_WRITTEN=1
echo 'STRUCTURED_AUDIT_REPORT=PASS'

"${ADB[@]}" shell am force-stop "$DEV_PACKAGE"
"${ADB[@]}" shell monkey \
  -p "$DEV_PACKAGE" \
  -c android.intent.category.LAUNCHER \
  1 >/dev/null

echo "SERIAL=$SERIAL"
echo "DEV_UID=$DEV_UID"
echo "DEV_APK_SHA256=$APK_SHA256"
echo "AUDIT_REPORT_JSON=$REPORT_PATH"
echo 'RESULT=MORPHE_BOOST_SETTINGS_APPEARANCE_LAYOUT_AUDIT_V56_RUNTIME_PASS'
