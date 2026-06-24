#!/usr/bin/env bash

FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

SRC_APK="${1:-}"
DEV_PKG="${2:-com.rubenmayayo.reddit.devsettings}"

if [ -z "$SRC_APK" ] || [ "$SRC_APK" = "--help" ] || [ "$SRC_APK" = "-h" ]; then
  cat <<'HELP'
Usage:
  tools/build-boost-devclone.sh <source-apk> [dev-package]

Purpose:
  Build an isolated devclone APK from an already patched Boost APK.

Rules:
  - Changes manifest package id.
  - Rewrites provider authorities to the dev package.
  - Rewrites package-scoped permission declarations/usages.
  - Does NOT rewrite app class/activity/service/receiver names.
  - Verifies activity-alias targetActivity references before build.
HELP
  exit 0
fi

OLD_PKG="com.rubenmayayo.reddit"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK="local-artifacts/boost-candidates/$STAMP-devclone-${DEV_PKG##*.}"
DECODE="$WORK/apktool"
UNSIGNED="$WORK/boost-devclone-unsigned.apk"
ALIGNED="$WORK/boost-devclone-aligned.apk"
SIGNED="$WORK/boost-devclone-signed.apk"
DEV_KS="$WORK/devclone-debug.jks"

mkdir -p "$WORK"

find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi

  for p in \
    "$ANDROID_HOME"/build-tools/*/apksigner \
    "$ANDROID_SDK_ROOT"/build-tools/*/apksigner \
    "$HOME"/Android/Sdk/build-tools/*/apksigner
  do
    [ -x "$p" ] && echo "$p" && return 0
  done

  return 1
}

echo "===== devclone input ====="
echo "SRC_APK=$SRC_APK"
echo "DEV_PKG=$DEV_PKG"
echo "WORK=$WORK"

test -f "$SRC_APK" || mark_fail "source APK missing: $SRC_APK"

if [ -f "$SRC_APK" ]; then
  ls -lh "$SRC_APK"
  sha256sum "$SRC_APK"
fi

echo
echo "===== tool checks ====="
command -v apktool >/dev/null 2>&1 || mark_fail "apktool missing"
command -v keytool >/dev/null 2>&1 || mark_fail "keytool missing"
APKSIGNER="$(find_apksigner || true)"
echo "APKSIGNER=${APKSIGNER:-missing}"
test -n "$APKSIGNER" || mark_fail "apksigner missing"

echo
echo "===== decode source APK ====="
if [ "$FAIL" -eq 0 ]; then
  apktool d -f "$SRC_APK" -o "$DECODE" || mark_fail "apktool decode failed"
fi

echo
echo "===== manifest/package rewrite ====="
if [ "$FAIL" -eq 0 ]; then
  DEV_PKG="$DEV_PKG" OLD_PKG="$OLD_PKG" DECODE="$DECODE" python3 <<'PY'
from pathlib import Path
import os
import re
import sys

dev_pkg = os.environ["DEV_PKG"]
old_pkg = os.environ["OLD_PKG"]
decode = Path(os.environ["DECODE"])
manifest = decode / "AndroidManifest.xml"

s = manifest.read_text()

# Only the manifest package id changes.
s = s.replace(f'package="{old_pkg}"', f'package="{dev_pkg}"', 1)

# Provider authorities must be unique for side-by-side install.
def rewrite_authority(match):
    prefix, value, suffix = match.groups()
    if value == old_pkg or value.startswith(old_pkg + "."):
        value = dev_pkg + value[len(old_pkg):]
    return prefix + value + suffix

s = re.sub(r'(android:authorities=")([^"]+)(")', rewrite_authority, s)

# Rewrite package-scoped permission references only. Do not rewrite android:name globally.
s = re.sub(
    r'(<permission\b[^>]*android:name=")' + re.escape(old_pkg) + r'([^"]*")',
    lambda m: m.group(1) + dev_pkg + m.group(2),
    s,
)
s = re.sub(
    r'(<uses-permission\b[^>]*android:name=")' + re.escape(old_pkg) + r'([^"]*")',
    lambda m: m.group(1) + dev_pkg + m.group(2),
    s,
)
s = re.sub(
    r'(android:permission=")' + re.escape(old_pkg) + r'([^"]*")',
    lambda m: m.group(1) + dev_pkg + m.group(2),
    s,
)

# Visible launcher label for the throwaway app.
s = s.replace('android:label="@string/app_name"', 'android:label="Boost Settings Test"', 1)

manifest.write_text(s)

# Resource XML explicit targetPackage, for old backup/intents.
for xml in decode.rglob("*.xml"):
    try:
        text = xml.read_text()
    except UnicodeDecodeError:
        continue

    new = text.replace(
        f'android:targetPackage="{old_pkg}"',
        f'android:targetPackage="{dev_pkg}"',
    )
    if new != text:
        xml.write_text(new)

print("UPDATED:", manifest)
PY
  [ "$?" -eq 0 ] || mark_fail "manifest/resource rewrite failed"
fi

echo
echo "===== manifest safety gates ====="
if [ -f "$DECODE/AndroidManifest.xml" ]; then
  MANIFEST="$DECODE/AndroidManifest.xml"

  echo
  echo "----- package/authority lines -----"
  grep -nE 'package=|android:authorities=' "$MANIFEST" || true

  DEV_PKG="$DEV_PKG" OLD_PKG="$OLD_PKG" MANIFEST="$MANIFEST" python3 <<'PY'
from pathlib import Path
import os
import re
import sys

dev_pkg = os.environ["DEV_PKG"]
old_pkg = os.environ["OLD_PKG"]
manifest = Path(os.environ["MANIFEST"])
s = manifest.read_text()

fail = 0

authorities = re.findall(r'android:authorities="([^"]+)"', s)
dev_leaf = dev_pkg.rsplit(".", 1)[-1]

bad_authorities = []
for a in authorities:
    is_old_normal_authority = (
        a == old_pkg
        or (
            a.startswith(old_pkg + ".")
            and not (a == dev_pkg or a.startswith(dev_pkg + "."))
        )
    )
    is_double_dev_leaf = f"{dev_leaf}.{dev_leaf}" in a

    if is_old_normal_authority or is_double_dev_leaf:
        bad_authorities.append(a)

if bad_authorities:
    print("FAIL: unsafe provider authorities:")
    for a in bad_authorities:
        print("  " + a)
    fail = 1
else:
    print("OK: provider authorities are isolated")

# Activity class names must remain original app classes, not dev package classes.
bad_class_names = []
for tag in re.findall(r'<(activity|activity-alias|service|receiver|provider)\b[^>]*>', s):
    pass

for m in re.finditer(r'<(activity|activity-alias|service|receiver)\b[^>]*android:name="([^"]+)"[^>]*>', s):
    tag, name = m.groups()
    if name == dev_pkg or name.startswith(dev_pkg + "."):
        bad_class_names.append((tag, name))

if bad_class_names:
    print("FAIL: app component class names were rewritten to dev package:")
    for tag, name in bad_class_names:
        print(f"  <{tag}> {name}")
    fail = 1
else:
    print("OK: app component class names were not rewritten to dev package")

activities = set(re.findall(r'<activity\b[^>]*android:name="([^"]+)"', s))
aliases = re.findall(r'<activity-alias\b[^>]*android:name="([^"]+)"[^>]*android:targetActivity="([^"]+)"', s)

bad_aliases = [(name, target) for name, target in aliases if target not in activities]

if bad_aliases:
    print("FAIL: activity-alias targetActivity does not match an existing activity:")
    for name, target in bad_aliases:
        print(f"  alias={name} target={target}")
    print("Activities:")
    for a in sorted(activities):
        print("  " + a)
    fail = 1
else:
    print("OK: activity-alias targets match existing activities")

if fail:
    sys.exit(1)
PY
  [ "$?" -eq 0 ] || mark_fail "manifest safety gate failed"
fi

echo
echo "===== optional settings XML gate ====="
if [ -f "$DECODE/res/xml/morphe_boost_settings.xml" ]; then
  echo "OK: morphe_boost_settings.xml exists"
  grep -nF 'morphe_boost_inline_previews' "$DECODE/res/xml/morphe_boost_settings.xml" || mark_fail "missing inline key"
  grep -nF 'morphe_boost_left_align_previews' "$DECODE/res/xml/morphe_boost_settings.xml" || mark_fail "missing left-align key"
  grep -nF 'morphe_boost_hide_source_after_preview' "$DECODE/res/xml/morphe_boost_settings.xml" || mark_fail "missing hide-source key"
else
  echo "WARN: morphe_boost_settings.xml not found; source APK may not be settings candidate"
fi

echo
echo "===== build/sign devclone ====="
if [ "$FAIL" -eq 0 ]; then
  apktool b "$DECODE" -o "$UNSIGNED" || mark_fail "apktool build failed"

  if command -v zipalign >/dev/null 2>&1; then
    zipalign -f 4 "$UNSIGNED" "$ALIGNED" || mark_fail "zipalign failed"
  else
    cp "$UNSIGNED" "$ALIGNED" || mark_fail "copy unsigned APK failed"
  fi

  keytool -genkeypair \
    -keystore "$DEV_KS" \
    -storetype JKS \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Boost Devclone,O=Morphe,C=NO" \
    >/dev/null \
    || mark_fail "keytool failed"

  "$APKSIGNER" sign \
    --ks "$DEV_KS" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$SIGNED" \
    "$ALIGNED" \
    || mark_fail "apksigner sign failed"

  "$APKSIGNER" verify --print-certs "$SIGNED" | sed -n '1,80p' || mark_fail "apksigner verify failed"
fi

echo
echo "===== signed artifact ====="
if [ -f "$SIGNED" ]; then
  ls -lh "$SIGNED"
  sha256sum "$SIGNED"
  aapt dump badging "$SIGNED" | sed -n '1,24p' || true
else
  mark_fail "signed APK missing"
fi

echo
echo "===== result ====="
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=BOOST_DEVCLONE_BUILD_GREEN"
else
  echo "RESULT=BOOST_DEVCLONE_BUILD_FAIL"
fi
echo "WORK=$WORK"
echo "DEV_PKG=$DEV_PKG"
echo "SIGNED=$SIGNED"
echo "Terminal still alive."
exit "$FAIL"
