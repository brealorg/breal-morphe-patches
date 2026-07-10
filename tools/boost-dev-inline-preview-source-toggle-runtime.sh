#!/usr/bin/env bash
set +e

FAIL=0

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_PKG="com.rubenmayayo.reddit.dev"
NORMAL_PKG="com.rubenmayayo.reddit"
LABEL="Boost Dev"
EXPECTED_TARGET_SDK="35"

MARKER="morphe_boost_inline_media_post_bind_source_policy_v16"
NAME="inline-preview-source-toggle-runtime"

MPP=""
SERIAL=""
ADB_ENDPOINT=""
OUT=""

OFF_XML_OK=0
ON_XML_OK=0

usage() {
  cat <<'EOF'
Usage:
  tools/boost-dev-inline-preview-source-toggle-runtime.sh [options]

Options:
  --mpp PATH                 MPP to test. Default: newest patches/build/libs/patches-*.mpp
  --serial SERIAL            adb serial to use
  --adb-endpoint HOST:PORT   run adb connect HOST:PORT, then use resulting device
  --name NAME                artifact name suffix
  --dev-package PKG          DEV package. Default: com.rubenmayayo.reddit.dev
  --marker MARKER            required post-bind runtime marker
  -h, --help                 show help

Behavior:
  - Builds and installs a Boost DEV clone from the selected MPP.
  - Launches the DEV clone.
  - Captures OFF and ON states with state-isolated logcat buffers.
  - Captures screenshots in both states.
  - Attempts fresh UIAutomator XML dumps without reusing stale remote files.
  - Treats UI XML as supplemental evidence. When Android returns
    "null root node", screenshots, state-isolated runtime markers, crash
    auditing, and explicit manual confirmation become authoritative.

Required runtime evidence:
  - Boost DEV remains alive.
  - No VerifyError, IllegalAccessError, fatal exception, or process crash.
  - OFF logs at least one source-removal marker and no keep markers.
  - ON logs at least one source-preservation marker and no removal markers.
  - No TableTextView/container/child/policy errors.
  - Preview bind activity is observed.
  - Screenshots exist for OFF and ON.
  - Manual confirmation accepts YES, yes, Y, or y.

When fresh XML exists for both states, it additionally requires:
  - OFF contains no preview.redd.it/external-preview.redd.it source URL.
  - ON contains at least one such source URL.
  - At least one URL transitions from absent in OFF to present in ON.
  - No tested source URL is shared by OFF and ON.
EOF
}

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

numeric_or_zero() {
  case "${1:-}" in
    ''|*[!0-9]*) printf '0\n' ;;
    *) printf '%s\n' "$1" ;;
  esac
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mpp)
      MPP="${2:-}"
      shift 2
      ;;
    --serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --adb-endpoint)
      ADB_ENDPOINT="${2:-}"
      shift 2
      ;;
    --name)
      NAME="${2:-}"
      shift 2
      ;;
    --dev-package)
      DEV_PKG="${2:-}"
      shift 2
      ;;
    --marker)
      MARKER="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      usage
      exit 2
      ;;
  esac
done

echo "===== Boost DEV inline preview source toggle runtime verifier ====="
date -Is
echo "ROOT=$ROOT"
echo "NAME=$NAME"
echo "DEV_PKG=$DEV_PKG"
echo "MARKER=$MARKER"

cd "$ROOT" || {
  echo "FAIL: cannot cd repo root: $ROOT"
  exit 1
}

if [ -z "$MPP" ]; then
  MPP="$(
    find patches/build/libs \
      -maxdepth 1 \
      -type f \
      -name 'patches-*.mpp' \
      -printf '%T@ %p\n' 2>/dev/null |
      sort -nr |
      awk 'NR == 1 {$1=""; sub(/^ /, ""); print}'
  )"
fi

echo
echo "===== repo/input sanity ====="
git --no-pager status -sb
echo "MPP=$MPP"

if [ -z "$MPP" ] || [ ! -f "$MPP" ]; then
  mark_fail "MPP missing"
else
  sha256sum "$MPP"

  unzip -p "$MPP" extensions/boostforreddit.mpe 2>/dev/null |
    strings |
    grep -F "$MARKER" ||
    mark_fail "marker missing from MPP"
fi

echo
echo "===== adb selection ====="

env -u ANDROID_SERIAL adb start-server >/dev/null 2>&1 || true

if [ -n "$ADB_ENDPOINT" ]; then
  echo "ADB_ENDPOINT=$ADB_ENDPOINT"
  env -u ANDROID_SERIAL adb connect "$ADB_ENDPOINT" || true
  sleep 1
fi

env -u ANDROID_SERIAL adb devices -l

if [ -z "$SERIAL" ]; then
  SERIAL="$(
    env -u ANDROID_SERIAL adb devices -l |
      awk '
        NR > 1 &&
        $1 ~ /^adb-.*\._adb-tls-connect\._tcp$/ &&
        $2 == "device" {
          print $1
          exit
        }
      '
  )"
fi

if [ -z "$SERIAL" ]; then
  SERIAL="$(
    env -u ANDROID_SERIAL adb devices -l |
      awk 'NR > 1 && $2 == "device" {print $1; exit}'
  )"
fi

echo "SERIAL=$SERIAL"

if [ -z "$SERIAL" ]; then
  mark_fail "no adb device selected"
else
  env -u ANDROID_SERIAL adb -s "$SERIAL" get-state ||
    mark_fail "adb serial unusable"
fi

adbq() {
  env -u ANDROID_SERIAL adb -s "$SERIAL" "$@"
}

echo
echo "===== build/install DEV clone ====="

if [ "$FAIL" -eq 0 ]; then
  BUILD_LOG="/tmp/${NAME}.boost-dev-from-mpp.$(date +%Y%m%d-%H%M%S).log"

  tools/boost-dev-from-mpp.sh \
    --mpp "$MPP" \
    --name "$NAME" \
    --dev-package "$DEV_PKG" \
    --normal-package "$NORMAL_PKG" \
    --label "$LABEL" \
    --expected-target-sdk "$EXPECTED_TARGET_SDK" \
    --serial "$SERIAL" \
    --marker "$MARKER" \
    --install \
    --no-verify-with-sdk \
    2>&1 |
    tee "$BUILD_LOG"

  DEV_RC=${PIPESTATUS[0]}

  echo "DEV_BUILD_RC=$DEV_RC"
  echo "DEV_BUILD_LOG=$BUILD_LOG"

  [ "$DEV_RC" -eq 0 ] ||
    mark_fail "boost-dev-from-mpp failed rc=$DEV_RC"

  grep -Fq 'RESULT=MORPHE_BOOST_DEV_FROM_MPP_OK' "$BUILD_LOG" ||
    mark_fail "DEV build/install success result missing"

  grep -Fq 'INFO: Applied: Spoof client' "$BUILD_LOG" ||
    mark_fail "Spoof client was not applied"

  grep -Fq 'INFO: Applied: Modify login WebView' "$BUILD_LOG" ||
    mark_fail "Modify login WebView was not applied"

  grep -Fq 'INFO: Applied: Show inline Giphy previews in comments' "$BUILD_LOG" ||
    mark_fail "inline preview patch was not applied"

  grep -Fq "targetSdkVersion:'35'" "$BUILD_LOG" ||
    mark_fail "targetSdk 35 evidence missing"
fi

echo
echo "===== launch DEV ====="

if [ "$FAIL" -eq 0 ]; then
  adbq shell am force-stop "$DEV_PKG" >/dev/null 2>&1 || true
  adbq logcat -c || true
  adbq logcat -b crash -c || true

  adbq shell monkey \
    -p "$DEV_PKG" \
    -c android.intent.category.LAUNCHER \
    1 ||
    mark_fail "DEV launch failed"

  sleep 1
fi

if [ "$FAIL" -eq 0 ]; then
  OUT="/tmp/boost-dev-inline-preview-source-toggle-runtime.$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$OUT"
fi

capture_fresh_xml() {
  local state="$1"
  local remote="/sdcard/boost_inline_preview_source_${state}.xml"
  local local_xml="$OUT/${state}.xml"
  local dump_log="$OUT/${state}-uiautomator.txt"

  local attempt=1
  local dump_ok=0

  : > "$dump_log"

  while [ "$attempt" -le 4 ]; do
    echo "DUMP_ATTEMPT=$attempt" |
      tee -a "$dump_log"

    adbq shell rm -f "$remote" >/dev/null 2>&1 || true

    DUMP_OUTPUT="$(
      adbq shell uiautomator dump --compressed "$remote" 2>&1
    )"
    DUMP_RC=$?

    {
      echo "DUMP_RC=$DUMP_RC"
      printf '%s\n' "$DUMP_OUTPUT"
    } | tee -a "$dump_log"

    REMOTE_SIZE="$(
      adbq shell \
        "if [ -s '$remote' ]; then wc -c < '$remote'; else echo 0; fi" \
        2>/dev/null |
        tr -dc '0-9'
    )"

    REMOTE_SIZE="$(numeric_or_zero "$REMOTE_SIZE")"

    echo "REMOTE_SIZE=$REMOTE_SIZE" |
      tee -a "$dump_log"

    if [ "$REMOTE_SIZE" -gt 200 ]; then
      adbq pull "$remote" "$local_xml" \
        2>&1 |
        tee -a "$dump_log"

      PULL_RC=${PIPESTATUS[0]}

      if [ "$PULL_RC" -eq 0 ] && [ -s "$local_xml" ]; then
        if python3 - "$local_xml" <<'__VALIDATE_CAPTURED_XML__'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]

try:
    root = ET.parse(path).getroot()
except Exception as exc:
    print(f"XML_INVALID={exc}")
    raise SystemExit(1)

print(f"XML_ROOT_TAG={root.tag}")
print("XML_VALID=PASS")
__VALIDATE_CAPTURED_XML__
        then
          dump_ok=1
          break
        fi
      fi
    fi

    attempt=$((attempt + 1))
    sleep 1
  done

  if [ "$state" = "off" ]; then
    OFF_XML_OK="$dump_ok"
  else
    ON_XML_OK="$dump_ok"
  fi

  echo "${state^^}_FRESH_XML_DUMP_OK=$dump_ok"

  if [ "$dump_ok" -eq 0 ]; then
    rm -f "$local_xml"
    echo "WARN: fresh ${state^^} UI XML unavailable; screenshot/logcat/manual fallback will be used"
  fi
}

capture_state() {
  local state="$1"
  local label="$2"
  local screenshot="$OUT/${state}.png"
  local logcat_file="$OUT/${state}-logcat.txt"
  local pid_file="$OUT/${state}-pid.txt"

  echo
  echo "===== ${label} capture ====="

  adbq logcat -c || true
  adbq logcat -b crash -c || true

  if [ "$state" = "off" ]; then
    cat <<'EOF'
1. Set "Show source text with preview" OFF.
2. Reopen or refresh the same comment thread.
3. Scroll until the tested inline previews are newly bound.
4. Confirm the source URLs are hidden while previews remain visible.
5. Tap a preview and confirm Boost's viewer opens, then return.
6. Leave the tested comments visible.
EOF
  else
    cat <<'EOF'
1. Set "Show source text with preview" ON.
2. Reopen or refresh the same comment thread.
3. Scroll until the same inline previews are newly bound.
4. Confirm the source URLs are displayed while previews remain visible.
5. Tap a preview and confirm Boost's viewer opens, then return.
6. Leave the tested comments visible.
EOF
  fi

  echo
  printf 'Press ENTER when the %s state is visible and stable: ' "$label"
  IFS= read -r _

  sleep 1

  adbq shell pidof "$DEV_PKG" |
    tr -d '\r' |
    tee "$pid_file"

  STATE_PID="$(tr -d '\r\n ' < "$pid_file")"

  echo "${label}_PID=${STATE_PID:-NONE}"

  [ -n "$STATE_PID" ] ||
    mark_fail "$label: Boost DEV process is not alive"

  adbq exec-out screencap -p > "$screenshot"
  SCREENSHOT_RC=$?

  echo "${label}_SCREENSHOT_RC=$SCREENSHOT_RC"

  if [ "$SCREENSHOT_RC" -ne 0 ] || [ ! -s "$screenshot" ]; then
    mark_fail "$label: screenshot capture failed"
  else
    sha256sum "$screenshot"
  fi

  adbq logcat \
    -d \
    -v threadtime \
    -b main \
    -b system \
    -b crash \
    > "$logcat_file" 2>&1

  echo "${label}_LOGCAT_LINES=$(wc -l < "$logcat_file")"

  capture_fresh_xml "$state"
}

if [ "$FAIL" -eq 0 ]; then
  capture_state off OFF
  capture_state on ON
fi

if [ "$FAIL" -eq 0 ]; then
  cat "$OUT/off-logcat.txt" "$OUT/on-logcat.txt" \
    > "$OUT/logcat.txt"

  grep -Ei \
    "InlineGiphy|$MARKER|morphe_boost_preserve_links_inline_preview|morphe_boost_skip_profile_avatar_preview|VerifyError|IllegalAccessError|FATAL EXCEPTION|Process: ${DEV_PKG//./\\.}|AndroidRuntime.*${DEV_PKG//./\\.}" \
    "$OUT/logcat.txt" |
    tee "$OUT/logcat-filtered.txt" ||
    true
fi

XML_AUDIT_AVAILABLE=0
OFF_TEXT_PREVIEW_URL_COUNT=0
ON_TEXT_PREVIEW_URL_COUNT=0
ON_ONLY_TEXT_PREVIEW_URL_COUNT=0
SHARED_TEXT_PREVIEW_URL_COUNT=0

echo
echo "===== optional fresh UI XML audit ====="

if [ "$OFF_XML_OK" -eq 1 ] && [ "$ON_XML_OK" -eq 1 ]; then
  XML_AUDIT_AVAILABLE=1

  OUT_DIR="$OUT" python3 <<'__V16_XML_AUDIT__'
from pathlib import Path
import html
import os
import re
import sys
import xml.etree.ElementTree as ET

root = Path(os.environ["OUT_DIR"])

pattern = re.compile(
    r"https?://(?:external-preview|preview)\.redd\.it/"
    r"[^\s\"'<>]+",
    re.IGNORECASE,
)

def extract(state: str) -> list[str]:
    path = root / f"{state}.xml"
    document = ET.parse(path)
    values: dict[str, str] = {}

    for node in document.iter():
        for attribute in ("text", "content-desc"):
            text = html.unescape(node.attrib.get(attribute, "") or "")

            for match in pattern.findall(text):
                value = (
                    html.unescape(match)
                    .replace("\\/", "/")
                    .strip()
                    .rstrip(".,);]")
                )

                if value:
                    values.setdefault(value.lower(), value)

    return sorted(values.values(), key=str.lower)

try:
    off = extract("off")
    on = extract("on")
except Exception as exc:
    print(f"XML_AUDIT_ERROR={exc}")
    sys.exit(1)

off_keys = {value.lower() for value in off}
on_keys = {value.lower() for value in on}

on_only = [
    value
    for value in on
    if value.lower() not in off_keys
]

shared = [
    value
    for value in on
    if value.lower() in off_keys
]

print("OFF_TEXT_PREVIEW_URLS:")
print("\n".join(off) if off else "NONE")

print("ON_TEXT_PREVIEW_URLS:")
print("\n".join(on) if on else "NONE")

print("ON_ONLY_TEXT_PREVIEW_URLS:")
print("\n".join(on_only) if on_only else "NONE")

print("SHARED_TEXT_PREVIEW_URLS:")
print("\n".join(shared) if shared else "NONE")

counts = {
    "OFF_TEXT_PREVIEW_URL_COUNT": len(off),
    "ON_TEXT_PREVIEW_URL_COUNT": len(on),
    "ON_ONLY_TEXT_PREVIEW_URL_COUNT": len(on_only),
    "SHARED_TEXT_PREVIEW_URL_COUNT": len(shared),
}

for key, value in counts.items():
    print(f"{key}={value}")

(root / "counts.env").write_text(
    "".join(f"{key}={value}\n" for key, value in counts.items())
)
__V16_XML_AUDIT__

  XML_AUDIT_RC=$?

  echo "XML_AUDIT_RC=$XML_AUDIT_RC"

  if [ "$XML_AUDIT_RC" -ne 0 ]; then
    mark_fail "fresh XML audit failed"
  elif [ -f "$OUT/counts.env" ]; then
    . "$OUT/counts.env"
  else
    mark_fail "fresh XML counts.env missing"
  fi
else
  echo "XML_AUDIT_AVAILABLE=0"
  echo "XML_AUDIT_MODE=SCREENSHOT_LOGCAT_MANUAL_FALLBACK"
  echo "WARN: Android did not provide fresh XML for both states"
fi

echo
echo "===== state-isolated v16 runtime audit ====="

OFF_LOG="$OUT/off-logcat.txt"
ON_LOG="$OUT/on-logcat.txt"

OFF_REMOVE_COUNT="$(
  grep -cF \
    "$MARKER: removed source from bound TextView source=" \
    "$OFF_LOG" 2>/dev/null ||
  true
)"

OFF_KEEP_COUNT="$(
  grep -cF \
    "$MARKER: keep bound source text source=" \
    "$OFF_LOG" 2>/dev/null ||
  true
)"

ON_REMOVE_COUNT="$(
  grep -cF \
    "$MARKER: removed source from bound TextView source=" \
    "$ON_LOG" 2>/dev/null ||
  true
)"

ON_KEEP_COUNT="$(
  grep -cF \
    "$MARKER: keep bound source text source=" \
    "$ON_LOG" 2>/dev/null ||
  true
)"

PREVIEW_BIND_COUNT="$(
  grep -hF \
    'morphe_boost_preserve_links_inline_preview_v4 bind' \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

TABLE_MISSING_COUNT="$(
  grep -hF \
    "$MARKER: bound TableTextView not available" \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

NO_CHILDREN_COUNT="$(
  grep -hF \
    "$MARKER: bound TableTextView has no children" \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

CHILD_TYPE_COUNT="$(
  grep -hF \
    "$MARKER: first TableTextView child is not TextView" \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

POLICY_FAIL_COUNT="$(
  grep -hF \
    "$MARKER: post-bind policy failed" \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

VERIFY_ERROR_COUNT="$(
  grep -hF \
    'java.lang.VerifyError' \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

ILLEGAL_ACCESS_COUNT="$(
  grep -hF \
    'java.lang.IllegalAccessError' \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

FATAL_EXCEPTION_COUNT="$(
  grep -hF \
    'FATAL EXCEPTION' \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

DEV_PROCESS_CRASH_COUNT="$(
  grep -hE \
    "Process: ${DEV_PKG//./\\.}|Force finishing activity ${DEV_PKG//./\\.}" \
    "$OFF_LOG" "$ON_LOG" 2>/dev/null |
    wc -l
)"

OFF_REMOVE_COUNT="$(numeric_or_zero "$OFF_REMOVE_COUNT")"
OFF_KEEP_COUNT="$(numeric_or_zero "$OFF_KEEP_COUNT")"
ON_REMOVE_COUNT="$(numeric_or_zero "$ON_REMOVE_COUNT")"
ON_KEEP_COUNT="$(numeric_or_zero "$ON_KEEP_COUNT")"
PREVIEW_BIND_COUNT="$(numeric_or_zero "$PREVIEW_BIND_COUNT")"
TABLE_MISSING_COUNT="$(numeric_or_zero "$TABLE_MISSING_COUNT")"
NO_CHILDREN_COUNT="$(numeric_or_zero "$NO_CHILDREN_COUNT")"
CHILD_TYPE_COUNT="$(numeric_or_zero "$CHILD_TYPE_COUNT")"
POLICY_FAIL_COUNT="$(numeric_or_zero "$POLICY_FAIL_COUNT")"
VERIFY_ERROR_COUNT="$(numeric_or_zero "$VERIFY_ERROR_COUNT")"
ILLEGAL_ACCESS_COUNT="$(numeric_or_zero "$ILLEGAL_ACCESS_COUNT")"
FATAL_EXCEPTION_COUNT="$(numeric_or_zero "$FATAL_EXCEPTION_COUNT")"
DEV_PROCESS_CRASH_COUNT="$(numeric_or_zero "$DEV_PROCESS_CRASH_COUNT")"

FINAL_PID="$(
  adbq shell pidof "$DEV_PKG" 2>/dev/null |
    tr -d '\r\n '
)"

echo "FINAL_PID=${FINAL_PID:-NONE}"
echo "OFF_REMOVE_COUNT=$OFF_REMOVE_COUNT"
echo "OFF_KEEP_COUNT=$OFF_KEEP_COUNT"
echo "ON_REMOVE_COUNT=$ON_REMOVE_COUNT"
echo "ON_KEEP_COUNT=$ON_KEEP_COUNT"
echo "PREVIEW_BIND_COUNT=$PREVIEW_BIND_COUNT"
echo "TABLE_MISSING_COUNT=$TABLE_MISSING_COUNT"
echo "NO_CHILDREN_COUNT=$NO_CHILDREN_COUNT"
echo "CHILD_TYPE_COUNT=$CHILD_TYPE_COUNT"
echo "POLICY_FAIL_COUNT=$POLICY_FAIL_COUNT"
echo "VERIFY_ERROR_COUNT=$VERIFY_ERROR_COUNT"
echo "ILLEGAL_ACCESS_COUNT=$ILLEGAL_ACCESS_COUNT"
echo "FATAL_EXCEPTION_COUNT=$FATAL_EXCEPTION_COUNT"
echo "DEV_PROCESS_CRASH_COUNT=$DEV_PROCESS_CRASH_COUNT"
echo "XML_AUDIT_AVAILABLE=$XML_AUDIT_AVAILABLE"
echo "OFF_TEXT_PREVIEW_URL_COUNT=$OFF_TEXT_PREVIEW_URL_COUNT"
echo "ON_TEXT_PREVIEW_URL_COUNT=$ON_TEXT_PREVIEW_URL_COUNT"
echo "ON_ONLY_TEXT_PREVIEW_URL_COUNT=$ON_ONLY_TEXT_PREVIEW_URL_COUNT"
echo "SHARED_TEXT_PREVIEW_URL_COUNT=$SHARED_TEXT_PREVIEW_URL_COUNT"

echo
echo "===== automated assertions ====="

[ -n "$FINAL_PID" ] ||
  mark_fail "Boost DEV process is not alive after capture"

[ "$PREVIEW_BIND_COUNT" -gt 0 ] ||
  mark_fail "inline preview bind activity was not observed"

[ "$OFF_REMOVE_COUNT" -gt 0 ] ||
  mark_fail "OFF source removal was not observed"

[ "$OFF_KEEP_COUNT" -eq 0 ] ||
  mark_fail "OFF unexpectedly contains keep markers"

[ "$ON_KEEP_COUNT" -gt 0 ] ||
  mark_fail "ON source preservation was not observed"

[ "$ON_REMOVE_COUNT" -eq 0 ] ||
  mark_fail "ON unexpectedly contains removal markers"

[ "$TABLE_MISSING_COUNT" -eq 0 ] ||
  mark_fail "bound TableTextView was unavailable"

[ "$NO_CHILDREN_COUNT" -eq 0 ] ||
  mark_fail "bound TableTextView had no children"

[ "$CHILD_TYPE_COUNT" -eq 0 ] ||
  mark_fail "TableTextView child 0 was not TextView"

[ "$POLICY_FAIL_COUNT" -eq 0 ] ||
  mark_fail "post-bind source policy failed"

[ "$VERIFY_ERROR_COUNT" -eq 0 ] ||
  mark_fail "VerifyError detected"

[ "$ILLEGAL_ACCESS_COUNT" -eq 0 ] ||
  mark_fail "IllegalAccessError detected"

[ "$FATAL_EXCEPTION_COUNT" -eq 0 ] ||
  mark_fail "fatal exception detected"

[ "$DEV_PROCESS_CRASH_COUNT" -eq 0 ] ||
  mark_fail "Boost DEV process crash detected"

[ -s "$OUT/off.png" ] ||
  mark_fail "OFF screenshot missing"

[ -s "$OUT/on.png" ] ||
  mark_fail "ON screenshot missing"

if [ "$XML_AUDIT_AVAILABLE" -eq 1 ]; then
  [ "$OFF_TEXT_PREVIEW_URL_COUNT" -eq 0 ] ||
    mark_fail "fresh OFF XML still exposes preview URLs"

  [ "$ON_TEXT_PREVIEW_URL_COUNT" -gt 0 ] ||
    mark_fail "fresh ON XML contains no preview URLs"

  [ "$ON_ONLY_TEXT_PREVIEW_URL_COUNT" -gt 0 ] ||
    mark_fail "fresh XML proves no OFF-to-ON URL transition"

  [ "$SHARED_TEXT_PREVIEW_URL_COUNT" -eq 0 ] ||
    mark_fail "one or more preview URLs are shared by OFF and ON"
fi

echo
echo "===== manual visual confirmation ====="
echo "Confirm all conditions:"
echo "  - Boost remained open throughout."
echo "  - Preview images remained visible in OFF and ON."
echo "  - Tapping a preview opened Boost's viewer in both states."
echo "  - Unrelated comment text and links remained intact."
echo "  - OFF hid only the source URL."
echo "  - ON displayed the source URL."

printf 'MANUAL_PREVIEW_CONFIRMATION [YES/NO]= '
IFS= read -r MANUAL_CONFIRMATION

MANUAL_NORMALIZED="$(
  printf '%s' "$MANUAL_CONFIRMATION" |
    tr '[:lower:]' '[:upper:]' |
    sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
)"

echo "MANUAL_CONFIRMATION=$MANUAL_CONFIRMATION"
echo "MANUAL_CONFIRMATION_NORMALIZED=$MANUAL_NORMALIZED"

case "$MANUAL_NORMALIZED" in
  YES|Y)
    echo "MANUAL_VISUAL_CONFIRMATION=PASS"
    ;;
  *)
    echo "MANUAL_VISUAL_CONFIRMATION=FAIL"
    mark_fail "manual visual confirmation was not affirmative"
    ;;
esac

echo
echo "===== evidence artifacts ====="
echo "OUT=$OUT"

find "$OUT" \
  -maxdepth 1 \
  -type f \
  -printf '%f\n' |
  sort

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_BOOST_INLINE_PREVIEW_SOURCE_TOGGLE_RUNTIME_OK"
  RC=0
else
  echo "RESULT=MORPHE_BOOST_INLINE_PREVIEW_SOURCE_TOGGLE_RUNTIME_FAIL"
  RC=1
fi

echo "XML_AUDIT_AVAILABLE=$XML_AUDIT_AVAILABLE"
echo "OUT=$OUT"
echo "Terminal still alive."
exit "$RC"
