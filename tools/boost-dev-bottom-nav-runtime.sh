#!/usr/bin/env bash
# Device check for Boost's bottom navigation geometry.
#
# Builds a debuggable DEV clone in its own package (the regular .dev install is
# left alone), enables the bottom navigation through the app's preferences, and
# for "Hide on scroll" on and off:
#   - cold-starts Home, scrolls, opens r/popular and r/all, returns with Back;
#   - asserts that every list's bottom padding equals the visible bar's measured
#     height (MORPHE_BOOST_NAV_CONTENT_CLEARANCE_V1), so content is never under
#     the bar;
#   - asserts the hide-on-scroll behavior matches the preference;
#   - fails on crashes or on nav/clearance errors from the Boost process.
# Screenshots land in the output directory for a visual check.
#
# Settings changed mid-session and the drawer's All -> Popular switch need a
# person; the script prints that checklist at the end.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

MPP=""
SERIAL=""
SKIP_INSTALL=0
DEV_PKG="com.rubenmayayo.reddit.dev.nav"
CLEARANCE_MARKER="MORPHE_BOOST_NAV_CONTENT_CLEARANCE_V1"
HIDE_MARKER="native bottom-navigation hide preference applied"

usage() {
  cat <<'EOF'
Usage: tools/boost-dev-bottom-nav-runtime.sh [--mpp PATH] [--serial SERIAL] [--skip-install]

  --mpp PATH       MPP to test. Default: the newest patches/build/libs/patches-*.mpp.
  --serial SERIAL  adb serial. Default: resolved with tools/boost-adb-serial.sh.
  --skip-install   Reuse the installed com.rubenmayayo.reddit.dev.nav build.

Prints RESULT=MORPHE_BOOST_BOTTOM_NAV_RUNTIME_OK or _FAIL.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mpp) MPP="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --skip-install) SKIP_INSTALL=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

if [ -z "$MPP" ]; then
  MPP="$(ls -t patches/build/libs/patches-*.mpp 2>/dev/null | head -1)"
fi

OUT="local-artifacts/boost-bottom-nav-runtime/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

FAIL=0
mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

finish() {
  echo
  echo "OUT=$OUT"
  if [ "$FAIL" -eq 0 ]; then
    echo "RESULT=MORPHE_BOOST_BOTTOM_NAV_RUNTIME_OK"
    exit 0
  fi
  echo "RESULT=MORPHE_BOOST_BOTTOM_NAV_RUNTIME_FAIL"
  exit 1
}

if [ -z "$SERIAL" ]; then
  SERIAL="$(
    env -u ANDROID_SERIAL tools/boost-adb-serial.sh \
      --hint "${MORPHE_ADB_HINT:-192.168.1.248}" \
      --expect-model "${MORPHE_ADB_EXPECT_MODEL:-Pixel_6}" \
      --expect-device "${MORPHE_ADB_EXPECT_DEVICE:-oriole}"
  )" || { mark_fail "no usable adb device"; finish; }
fi

echo "MPP=$MPP"
echo "SERIAL=$SERIAL"
echo "DEV_PKG=$DEV_PKG"

adbq() {
  env -u ANDROID_SERIAL adb -s "$SERIAL" "$@"
}

if [ "$SKIP_INSTALL" -eq 0 ]; then
  test -s "$MPP" || { mark_fail "MPP missing: $MPP"; finish; }

  unzip -p "$MPP" extensions/boostforreddit.mpe 2>/dev/null |
    strings |
    grep -F "$CLEARANCE_MARKER" >/dev/null ||
      { mark_fail "clearance marker missing from MPP"; finish; }

  echo
  echo "===== BUILD / INSTALL DEV ====="
  tools/boost-dev-from-mpp.sh \
    --mpp "$MPP" \
    --name "bottom-nav-runtime" \
    --dev-package "$DEV_PKG" \
    --label "Boost Nav" \
    --serial "$SERIAL" \
    --marker "$CLEARANCE_MARKER" \
    --debuggable-dev \
    --install \
    > "$OUT/build.log" 2>&1 ||
      { tail -40 "$OUT/build.log"; mark_fail "DEV build/install failed"; finish; }
  grep -F 'RESULT=MORPHE_BOOST_DEV_FROM_MPP_OK' "$OUT/build.log"
fi

PREFS="shared_prefs/${DEV_PKG}_preferences.xml"

launch_home() {
  adbq shell monkey -p "$DEV_PKG" -c android.intent.category.LAUNCHER 1 \
    >/dev/null 2>&1
}

# Writes Boost's preferences while the app is stopped. Only the two
# bottom-navigation keys change; everything else Boost wrote is kept.
write_nav_prefs() {
  local hide="$1"
  local local_xml="$OUT/prefs-$hide.xml"

  adbq shell am force-stop "$DEV_PKG"
  adbq shell run-as "$DEV_PKG" cat "$PREFS" > "$local_xml" 2>/dev/null

  python3 - "$local_xml" "$hide" <<'PY'
import sys
import xml.etree.ElementTree as ET

path, hide = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
    if root.tag != "map":
        raise ValueError(root.tag)
except Exception:
    root = ET.Element("map")

values = {
    "pref_bottom_navigation": "true",
    "pref_bottom_navigation_hide_on_scroll": hide,
}
for node in list(root):
    if node.get("name") in values:
        root.remove(node)
for name, value in values.items():
    ET.SubElement(root, "boolean", name=name, value=value)

ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)
PY

  adbq shell "run-as $DEV_PKG sh -c 'mkdir -p shared_prefs && cat > $PREFS'" \
    < "$local_xml"
  adbq shell run-as "$DEV_PKG" cat "$PREFS" |
    grep -F "pref_bottom_navigation_hide_on_scroll\" value=\"$hide\"" >/dev/null ||
      mark_fail "could not write preferences (hide=$hide)"
}

screenshot() {
  adbq exec-out screencap -p > "$OUT/$1.png" 2>/dev/null
}

open_url() {
  adbq shell am start -a android.intent.action.VIEW -d "$1" -p "$DEV_PKG" \
    >/dev/null 2>&1
}

swipe_up() {
  adbq shell input swipe 540 1700 540 700 300
}

run_scenario() {
  local hide="$1"
  local log="$OUT/logcat-hide-$hide.txt"

  echo
  echo "===== SCENARIO hide_on_scroll=$hide ====="

  write_nav_prefs "$hide"
  adbq logcat -c

  launch_home
  sleep 8
  screenshot "hide-$hide-1-home"

  swipe_up
  sleep 2
  screenshot "hide-$hide-2-home-scrolled"

  open_url "https://www.reddit.com/r/popular"
  sleep 6
  screenshot "hide-$hide-3-popular"

  open_url "https://www.reddit.com/r/all"
  sleep 6
  swipe_up
  sleep 2
  screenshot "hide-$hide-4-all-scrolled"

  adbq shell input keyevent KEYCODE_BACK
  sleep 2
  adbq shell input keyevent KEYCODE_BACK
  sleep 3
  screenshot "hide-$hide-5-back-home"

  local pid
  pid="$(adbq shell pidof "$DEV_PKG" | tr -d '\r')"
  adbq logcat -d -v threadtime > "$log"

  if [ -z "$pid" ]; then
    mark_fail "hide=$hide: Boost process not running (crash?)"
  fi

  python3 - "$log" "$hide" "$pid" "$CLEARANCE_MARKER" "$HIDE_MARKER" <<'PY' ||
import re
import sys

log, hide, pid, clearance_marker, hide_marker = sys.argv[1:6]
lines = open(log, encoding="utf-8", errors="replace").read().splitlines()
fails = []

def from_boost(line):
    parts = line.split()
    return len(parts) > 2 and parts[2] == pid

boost = [line for line in lines if not pid or from_boost(line)]

for line in lines:
    if "FATAL EXCEPTION" in line and (not pid or from_boost(line)):
        fails.append("crash: " + line)

for needle in (
    "clearance install failed",
    "clearance update failed",
    "behavior preservation failed",
    "native visibility failed",
):
    for line in boost:
        if needle in line:
            fails.append(needle + ": " + line)

latest = {}
pattern = re.compile(
    r"paddingBottom=(-?\d+) navHeight=(-?\d+) navVisibility=(-?\d+)"
    r" fallback=(\d+) activity=(\S+)"
)
for line in boost:
    if clearance_marker not in line:
        continue
    match = pattern.search(line)
    if match:
        padding, height, visibility, _fallback, activity = match.groups()
        latest[activity] = (int(padding), int(height), int(visibility))

if not latest:
    fails.append("no content clearance logged")

for activity, (padding, height, visibility) in sorted(latest.items()):
    print(
        f"CLEARANCE activity={activity} paddingBottom={padding}"
        f" navHeight={height} navVisibility={visibility}"
    )
    if visibility == 0 and height > 0 and padding != height:
        fails.append(
            f"{activity}: paddingBottom {padding} != navHeight {height}"
        )

expected = "true" if hide == "true" else "false"
applied = [
    line for line in boost
    if hide_marker in line and f"enabled={expected}" in line
]
if not applied:
    fails.append(f"hide preference never applied with enabled={expected}")
for line in applied:
    has_behavior = "HideBottomViewOnScrollBehavior" in line.split(
        "behaviorAfter=", 1
    )[-1]
    if has_behavior != (hide == "true"):
        fails.append("behavior does not match preference: " + line)
        break

for fail in fails:
    print("FAIL: " + fail)
print("CHECKS=" + ("PASS" if not fails else "FAIL"))
sys.exit(1 if fails else 0)
PY
    mark_fail "hide=$hide: geometry/behavior checks failed"
}

# Boost's first launch resets several preferences, hide-on-scroll included
# (MainActivity first-run defaults). Let it run once before writing ours.
echo
echo "===== FIRST-RUN WARM-UP ====="
launch_home
sleep 10
adbq shell am force-stop "$DEV_PKG"

run_scenario true
run_scenario false

cat <<'EOF'

===== MANUAL CHECKLIST (not automatable without a UI driver) =====
On the "Boost Nav" app, compare each step with the bar docked (last post
ends above the bar):
  1. Hide on scroll OFF: open Settings from Profile, turn it ON and OFF again,
     go back Home. The last post must stay above the bar.
  2. Drawer: All -> Popular -> Home. The bar stays visible and docked.
  3. Open a post in the swipe viewer, leave the app and return. The bar must
     not appear over the viewer.
  4. With Hide on scroll ON: scroll the bar away, switch feed in the drawer.
     The bar comes back.
EOF

finish
