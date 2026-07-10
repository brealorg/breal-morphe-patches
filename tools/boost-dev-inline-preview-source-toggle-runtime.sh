#!/usr/bin/env bash
set +e

FAIL=0
RC_FINAL=0

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_PKG="com.rubenmayayo.reddit.dev"
NORMAL_PKG="com.rubenmayayo.reddit"
LABEL="Boost Dev"
EXPECTED_TARGET_SDK="35"
MARKER="morphe_boost_inline_media_prerender_source_policy_v10"
NAME="inline-preview-source-toggle-runtime"
MPP=""
SERIAL=""
ADB_ENDPOINT=""
OUT=""

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
  --marker MARKER            required runtime marker
  -h, --help                 show help

Behavior:
  - Builds and installs Boost DEV clone from MPP.
  - Launches DEV clone.
  - Prompts for OFF capture and ON capture.
  - Dumps UI XML, screenshots, and logcat.
  - Requires:
      BOOST_FATAL_COUNT=0
      PREVIEW_BIND_COUNT>0
      V10_RENDER_COUNT>0
      V10_SET_COUNT>0
      OFF_MEDIA_URL_COUNT=0
      ON_MEDIA_URL_COUNT>0
EOF
}

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mpp) MPP="${2:-}"; shift 2 ;;
    --serial) SERIAL="${2:-}"; shift 2 ;;
    --adb-endpoint) ADB_ENDPOINT="${2:-}"; shift 2 ;;
    --name) NAME="${2:-}"; shift 2 ;;
    --dev-package) DEV_PKG="${2:-}"; shift 2 ;;
    --marker) MARKER="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1"; usage; exit 2 ;;
  esac
done

echo "===== Boost DEV inline preview source toggle runtime verifier ====="
date -Is
echo "ROOT=$ROOT"
echo "NAME=$NAME"
echo "DEV_PKG=$DEV_PKG"
echo "MARKER=$MARKER"

cd "$ROOT" || mark_fail "cannot cd repo root: $ROOT"

if [ -z "$MPP" ]; then
  MPP="$(find patches/build/libs -maxdepth 1 -type f -name 'patches-*.mpp' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
fi

echo
echo "===== repo/input sanity ====="
git --no-pager status -sb
echo "MPP=$MPP"

if [ -z "$MPP" ] || [ ! -f "$MPP" ]; then
  mark_fail "MPP missing"
else
  sha256sum "$MPP"
  unzip -p "$MPP" extensions/boostforreddit.mpe 2>/dev/null \
    | strings \
    | grep -F "$MARKER" \
    || mark_fail "marker missing from MPP"
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
    env -u ANDROID_SERIAL adb devices -l \
      | awk 'NR > 1 && $2 == "device" { print $1; exit }'
  )"
fi

echo "SERIAL=$SERIAL"

if [ -z "$SERIAL" ]; then
  mark_fail "no adb device selected"
else
  env -u ANDROID_SERIAL adb -s "$SERIAL" get-state || mark_fail "adb serial unusable"
fi

echo
echo "===== build/install DEV clone ====="
if [ "$FAIL" -eq 0 ]; then
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
    2>&1 | tee "/tmp/${NAME}.boost-dev-from-mpp.txt"

  DEV_RC=${PIPESTATUS[0]}
  [ "$DEV_RC" -eq 0 ] || mark_fail "boost-dev-from-mpp failed rc=$DEV_RC"
fi

echo
echo "===== launch DEV ====="
if [ "$FAIL" -eq 0 ]; then
  env -u ANDROID_SERIAL adb -s "$SERIAL" logcat -c || mark_fail "logcat clear failed"
  env -u ANDROID_SERIAL adb -s "$SERIAL" shell monkey -p "$DEV_PKG" -c android.intent.category.LAUNCHER 1 \
    || mark_fail "DEV launch failed"
fi

if [ "$FAIL" -eq 0 ]; then
  OUT="/tmp/boost-dev-inline-preview-source-toggle-runtime.$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$OUT"

  echo
  cat <<'EOF'
===== OFF capture =====
1. In Boost Dev, set "Show source text with preview" OFF.
2. Open comments with several inline GIF/media previews.
3. Scroll until mixed text + source URL comments are visible/bound.
4. Leave the tested area visible.
5. Press ENTER here.
EOF
  read -r _

  env -u ANDROID_SERIAL adb -s "$SERIAL" shell uiautomator dump /sdcard/boost_inline_preview_source_off.xml
  env -u ANDROID_SERIAL adb -s "$SERIAL" pull /sdcard/boost_inline_preview_source_off.xml "$OUT/off.xml" >/dev/null
  env -u ANDROID_SERIAL adb -s "$SERIAL" exec-out screencap -p > "$OUT/off.png"

  echo
  cat <<'EOF'
===== ON capture =====
1. Set "Show source text with preview" ON.
2. Reopen/refresh/scroll the same area.
3. Leave source URLs visible.
4. Press ENTER here.
EOF
  read -r _

  env -u ANDROID_SERIAL adb -s "$SERIAL" shell uiautomator dump /sdcard/boost_inline_preview_source_on.xml
  env -u ANDROID_SERIAL adb -s "$SERIAL" pull /sdcard/boost_inline_preview_source_on.xml "$OUT/on.xml" >/dev/null
  env -u ANDROID_SERIAL adb -s "$SERIAL" exec-out screencap -p > "$OUT/on.png"

  echo
  echo "===== logcat capture ====="
  env -u ANDROID_SERIAL adb -s "$SERIAL" logcat -d -v time > "$OUT/logcat.txt"

  grep -Ei "InlineGiphy|$MARKER|morphe_boost_preserve_links_inline_preview|morphe_boost_skip_profile_avatar_preview|FATAL EXCEPTION|Process: ${DEV_PKG//./\\.}|AndroidRuntime.*${DEV_PKG//./\\.}" \
    "$OUT/logcat.txt" \
    | tee "$OUT/logcat-filtered.txt" || true

  echo
  echo "===== UI XML media URL audit ====="
  OUT_DIR="$OUT" python3 <<'PY'
from pathlib import Path
import html
import os
import re
import xml.etree.ElementTree as ET

root = Path(os.environ["OUT_DIR"])
url_re = re.compile(r"""https?://[^\s"'<>]+""", re.I)
media_terms = [
    ".gif", "giphy.com", "redgifs", "imgur", "i.redd.it",
    "preview.redd.it", "v.redd.it", "reddit.com/gallery"
]

def clean(url):
    return html.unescape(url).rstrip(".,);]")

def urls(name):
    text = (root / f"{name}.xml").read_text(errors="replace")
    found = []
    tree = ET.fromstring(text)
    for node in tree.iter("node"):
        for attr in ("text", "content-desc"):
            value = html.unescape(node.attrib.get(attr, "") or "")
            for m in url_re.finditer(value):
                found.append(clean(m.group(0)))

    seen = set()
    out = []
    for u in found:
        k = u.lower()
        if k not in seen:
            seen.add(k)
            out.append(u)
    return out

off = urls("off")
on = urls("on")

off_media = [u for u in off if any(t in u.lower() for t in media_terms)]
on_media = [u for u in on if any(t in u.lower() for t in media_terms)]

print("OFF_MEDIA_URLS:")
print("\n".join(off_media) if off_media else "NONE")
print("ON_MEDIA_URLS:")
print("\n".join(on_media) if on_media else "NONE")
print(f"OFF_MEDIA_URL_COUNT={len(off_media)}")
print(f"ON_MEDIA_URL_COUNT={len(on_media)}")

(root / "counts.env").write_text(
    f"OFF_MEDIA_URL_COUNT={len(off_media)}\nON_MEDIA_URL_COUNT={len(on_media)}\n"
)
PY

  PY_RC=$?
  [ "$PY_RC" -eq 0 ] || mark_fail "UI XML audit failed rc=$PY_RC"

  if [ -f "$OUT/counts.env" ]; then
    . "$OUT/counts.env"
  else
    OFF_MEDIA_URL_COUNT=999
    ON_MEDIA_URL_COUNT=0
    mark_fail "missing counts.env"
  fi

  BOOST_FATAL_COUNT="$(grep -Eic "FATAL EXCEPTION|Process: ${DEV_PKG//./\\.}|AndroidRuntime.*${DEV_PKG//./\\.}" "$OUT/logcat-filtered.txt" || true)"
  PREVIEW_BIND_COUNT="$(grep -Fic 'morphe_boost_preserve_links_inline_preview_v4 bind' "$OUT/logcat-filtered.txt" || true)"
  V10_RENDER_COUNT="$(grep -Fic "$MARKER: removed preview source before render" "$OUT/logcat-filtered.txt" || true)"
  V10_SET_COUNT="$(grep -Fic "$MARKER: set rendered comment html updated=true" "$OUT/logcat-filtered.txt" || true)"
  V10_KEEP_COUNT="$(grep -Fic "$MARKER: keep original comment html" "$OUT/logcat-filtered.txt" || true)"
  AVATAR_SKIP_COUNT="$(grep -Fic 'morphe_boost_skip_profile_avatar_preview_v1' "$OUT/logcat-filtered.txt" || true)"

  echo
  echo "BOOST_FATAL_COUNT=$BOOST_FATAL_COUNT"
  echo "PREVIEW_BIND_COUNT=$PREVIEW_BIND_COUNT"
  echo "V10_RENDER_COUNT=$V10_RENDER_COUNT"
  echo "V10_SET_COUNT=$V10_SET_COUNT"
  echo "V10_KEEP_COUNT=$V10_KEEP_COUNT"
  echo "AVATAR_SKIP_COUNT=$AVATAR_SKIP_COUNT"
  echo "OFF_MEDIA_URL_COUNT=$OFF_MEDIA_URL_COUNT"
  echo "ON_MEDIA_URL_COUNT=$ON_MEDIA_URL_COUNT"
  echo "OUT=$OUT"

  [ "$BOOST_FATAL_COUNT" -eq 0 ] || mark_fail "Boost DEV fatal observed"
  [ "$PREVIEW_BIND_COUNT" -gt 0 ] || mark_fail "preview bind not observed"
  [ "$V10_RENDER_COUNT" -gt 0 ] || mark_fail "v10 render removal not observed"
  [ "$V10_SET_COUNT" -gt 0 ] || mark_fail "v10 rendered html set not observed"
  [ "$OFF_MEDIA_URL_COUNT" -eq 0 ] || mark_fail "OFF still exposes media/GIF source URLs"
  [ "$ON_MEDIA_URL_COUNT" -gt 0 ] || mark_fail "ON did not expose source URLs"
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_BOOST_INLINE_PREVIEW_SOURCE_TOGGLE_RUNTIME_OK"
  RC_FINAL=0
else
  echo "RESULT=MORPHE_BOOST_INLINE_PREVIEW_SOURCE_TOGGLE_RUNTIME_FAIL"
  RC_FINAL=1
fi

echo "OUT=$OUT"
echo "Terminal still alive."

echo
echo "SCRIPT EXIT CODE=$RC_FINAL"
echo "Terminal still alive."
exit "$RC_FINAL"
