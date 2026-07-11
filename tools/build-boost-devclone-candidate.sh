#!/usr/bin/env bash
set -u

usage() {
  cat <<'USAGE'
Usage:
  tools/build-boost-devclone-candidate.sh --source-apk PATH [options]

Options:
  --source-apk PATH       Source patched normal Boost APK. Required.
  --base-apk PATH         Clean Boost APK used for the source candidate.
                          Defaults to the known Boost 1.12.12 APKMirror APK.
  --patch-result PATH     Morphe patch-result.json for the source candidate.
                          Defaults to patch-result.json beside --source-apk.
  --name NAME             Candidate directory suffix. Default: boost-devclone.
  --dev-package PACKAGE   Dev clone package. Default: com.rubenmayayo.reddit.dev.
  --normal-package PKG    Original package. Default: com.rubenmayayo.reddit.
  --label LABEL           App label. Default: Boost Dev.
  --out-root DIR          Output root. Default: local-artifacts/boost-dev-overwrite-candidates.
  --help                  Show this help.

Purpose:
  Build a Boost dev-clone APK from a patched normal Boost APK, preserving runtime markers
  while retargeting manifest package, authorities, and resource XML self-target intents.
USAGE
}

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

pass() {
  echo "[PASS] $*"
}

DEFAULT_BASE_APK="/home/b-real/com.rubenmayayo.reddit_1.12.12-210011212_minAPI21(arm64-v8a,armeabi,armeabi-v7a,mips,mips64,x86,x86_64)(nodpi)_apkmirror.com.apk"
SOURCE_APK=""
BASE_APK="$DEFAULT_BASE_APK"
PATCH_RESULT=""
NAME="boost-devclone"
DEV_PACKAGE="com.rubenmayayo.reddit.dev"
NORMAL_PACKAGE="com.rubenmayayo.reddit"
LABEL="Boost Dev"
OUT_ROOT="local-artifacts/boost-dev-overwrite-candidates"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --source-apk)
      SOURCE_APK="${2:-}"
      shift 2
      ;;
    --base-apk|--base)
      BASE_APK="${2:-}"
      shift 2
      ;;
    --patch-result)
      PATCH_RESULT="${2:-}"
      shift 2
      ;;
    --name)
      NAME="${2:-}"
      shift 2
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
    --out-root)
      OUT_ROOT="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[ -n "$SOURCE_APK" ] || { usage; fail "--source-apk is required"; }
[ -f "$SOURCE_APK" ] || fail "Source APK missing: $SOURCE_APK"
[ -f "$BASE_APK" ] || fail "Base APK missing: $BASE_APK"
if [ -z "$PATCH_RESULT" ]; then
  PATCH_RESULT="$(dirname "$SOURCE_APK")/patch-result.json"
fi
[ -f "$PATCH_RESULT" ] || fail "Patch result missing: $PATCH_RESULT"
[ -x tools/boost-bytecode-safety-gate.sh ] || fail "bytecode safety gate missing or not executable"

AAPT="$(command -v aapt || true)"
[ -n "$AAPT" ] || fail "aapt missing"

if command -v apktool >/dev/null 2>&1; then
  APKTOOL_MODE="bin"
  APKTOOL_BIN="$(command -v apktool)"
  APKTOOL_JAR=""
else
  APKTOOL_JAR="$(find "$HOME" /tmp -maxdepth 6 -type f -iname '*apktool*.jar' 2>/dev/null | sort | head -n1)"
  [ -n "$APKTOOL_JAR" ] || fail "apktool missing"
  APKTOOL_MODE="jar"
  APKTOOL_BIN=""
fi

ZIPALIGN=""
APKSIGNER=""
for f in "$ANDROID_HOME"/build-tools/*/zipalign "$ANDROID_SDK_ROOT"/build-tools/*/zipalign "$HOME"/Android/Sdk/build-tools/*/zipalign; do
  [ -x "$f" ] && ZIPALIGN="$f"
done
for f in "$ANDROID_HOME"/build-tools/*/apksigner "$ANDROID_SDK_ROOT"/build-tools/*/apksigner "$HOME"/Android/Sdk/build-tools/*/apksigner; do
  [ -x "$f" ] && APKSIGNER="$f"
done

[ -n "$ZIPALIGN" ] || fail "zipalign missing"
[ -n "$APKSIGNER" ] || fail "apksigner missing"

DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
[ -f "$DEBUG_KEYSTORE" ] || fail "debug keystore missing: $DEBUG_KEYSTORE"

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$OUT_ROOT/${STAMP}-${NAME}"
DEV_DIR="$OUT_DIR/dev"
DECODED="$DEV_DIR/decoded"

UNSIGNED_APK="$DEV_DIR/boost-devclone-unsigned.apk"
ALIGNED_APK="$DEV_DIR/boost-devclone-aligned.apk"
SIGNED_APK="$DEV_DIR/boost-devclone.apk"
REPORT="$OUT_DIR/devclone-report.txt"

mkdir -p "$DEV_DIR"

{
  echo "BOOST DEVCLONE BUILDER"
  echo "Started: $(date -Is)"
  echo "SOURCE_APK=$SOURCE_APK"
  echo "SOURCE_SHA256=$(sha256sum "$SOURCE_APK" | awk '{print $1}')"
  echo "BASE_APK=$BASE_APK"
  echo "PATCH_RESULT=$PATCH_RESULT"
  echo "DEV_PACKAGE=$DEV_PACKAGE"
  echo "NORMAL_PACKAGE=$NORMAL_PACKAGE"
  echo "LABEL=$LABEL"
  echo "OUT_DIR=$OUT_DIR"
  echo "AAPT=$AAPT"
  echo "APKTOOL_MODE=$APKTOOL_MODE"
  echo "APKTOOL_BIN=${APKTOOL_BIN:-}"
  echo "APKTOOL_JAR=${APKTOOL_JAR:-}"
  echo "ZIPALIGN=$ZIPALIGN"
  echo "APKSIGNER=$APKSIGNER"
  echo

  echo "===== source bytecode safety gate ====="
  BYTECODE_REPORT="$OUT_DIR/source-bytecode-safety-report.json"
  BYTECODE_LOG="$OUT_DIR/source-bytecode-safety.log"
  tools/boost-bytecode-safety-gate.sh \
    --base-apk "$BASE_APK" \
    --candidate-apk "$SOURCE_APK" \
    --patch-result "$PATCH_RESULT" \
    --report "$BYTECODE_REPORT" \
    2>&1 | tee "$BYTECODE_LOG"
  BYTECODE_RC=${PIPESTATUS[0]}
  [ "$BYTECODE_RC" -eq 0 ] \
    || fail "source candidate failed bytecode safety gate rc=$BYTECODE_RC"
  grep -qx 'BYTECODE_GATE=PASS' "$BYTECODE_LOG" \
    || fail "source bytecode gate did not emit PASS"
  pass "source candidate bytecode safety gate passed"

  echo
  echo "===== decode ====="
  rm -rf "$DECODED"
  if [ "$APKTOOL_MODE" = "bin" ]; then
    apktool d -f "$SOURCE_APK" -o "$DECODED"
  else
    java -jar "$APKTOOL_JAR" d -f "$SOURCE_APK" -o "$DECODED"
  fi

  echo
  echo "===== rewrite manifest/resources ====="
  DECODED="$DECODED" NORMAL_PACKAGE="$NORMAL_PACKAGE" DEV_PACKAGE="$DEV_PACKAGE" LABEL="$LABEL" python3 <<'PY'
from pathlib import Path
import os
import xml.etree.ElementTree as ET

decoded = Path(os.environ["DECODED"])
normal_pkg = os.environ["NORMAL_PACKAGE"]
dev_pkg = os.environ["DEV_PACKAGE"]
label = os.environ["LABEL"]

android_ns = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", android_ns)

manifest_path = decoded / "AndroidManifest.xml"
tree = ET.parse(manifest_path)
root = tree.getroot()
root.set("package", dev_pkg)

android_label = f"{{{android_ns}}}label"
android_authorities = f"{{{android_ns}}}authorities"
android_target_package = f"{{{android_ns}}}targetPackage"

manifest_attr_rewrites = 0
for node in root.iter():
    if node.tag == "application":
        node.set(android_label, label)

    for attr in (android_authorities, android_target_package):
        if attr in node.attrib and normal_pkg in node.attrib[attr]:
            node.set(attr, node.attrib[attr].replace(normal_pkg, dev_pkg))
            manifest_attr_rewrites += 1

tree.write(manifest_path, encoding="utf-8", xml_declaration=True)

resource_xml_rewrites = 0
for xml_path in sorted((decoded / "res").rglob("*.xml")):
    text = xml_path.read_text(errors="ignore")
    new = text

    # Critical for Boost preferences and launcher shortcuts:
    # Retarget self-package values while leaving class names unchanged.
    new = new.replace(f'android:targetPackage="{normal_pkg}"', f'android:targetPackage="{dev_pkg}"')
    new = new.replace(f'targetPackage="{normal_pkg}"', f'targetPackage="{dev_pkg}"')
    new = new.replace(f'android:authorities="{normal_pkg}', f'android:authorities="{dev_pkg}')
    new = new.replace(f'authorities="{normal_pkg}', f'authorities="{dev_pkg}')

    if new != text:
        xml_path.write_text(new)
        resource_xml_rewrites += 1

label_rewrites = 0
for strings in (decoded / "res").glob("values*/strings.xml"):
    text = strings.read_text(errors="ignore")
    new = text.replace(">Boost<", f">{label}<")
    if new != text:
        strings.write_text(new)
        label_rewrites += 1

print(f"MANIFEST_ATTR_REWRITES={manifest_attr_rewrites}")
print(f"RESOURCE_XML_REWRITE_FILES={resource_xml_rewrites}")
print(f"LABEL_REWRITE_FILES={label_rewrites}")
PY

  echo
  echo "===== decoded leak check ====="
  if grep -RInF "targetPackage=\"$NORMAL_PACKAGE\"" "$DECODED/res" "$DECODED/AndroidManifest.xml" 2>/dev/null; then
    fail "Normal targetPackage leak remains after rewrite"
  fi
  pass "no normal targetPackage leaks remain"

  if grep -RInF "targetPackage=\"$DEV_PACKAGE\"" "$DECODED/res" "$DECODED/AndroidManifest.xml" 2>/dev/null; then
    pass "dev targetPackage values present"
  else
    echo "[WARN] no dev targetPackage values found"
  fi

  echo
  echo "===== build ====="
  if [ "$APKTOOL_MODE" = "bin" ]; then
    apktool b "$DECODED" -o "$UNSIGNED_APK"
  else
    java -jar "$APKTOOL_JAR" b "$DECODED" -o "$UNSIGNED_APK"
  fi

  "$ZIPALIGN" -f -p 4 "$UNSIGNED_APK" "$ALIGNED_APK"

  "$APKSIGNER" sign \
    --ks "$DEBUG_KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$SIGNED_APK" \
    "$ALIGNED_APK"

  echo
  echo "===== output checks ====="
  echo "DEV_APK=$SIGNED_APK"
  echo "DEV_APK_SHA256=$(sha256sum "$SIGNED_APK" | awk '{print $1}')"

  "$AAPT" dump badging "$SIGNED_APK" | grep -E "package:|targetSdkVersion|application-label|launchable-activity" | sed -n '1,160p'

  "$APKSIGNER" verify --print-certs "$SIGNED_APK" | grep -E 'Signer #1 certificate DN|Signer #1 certificate SHA-256 digest' | sed -n '1,40p'

  echo
  echo "RESULT=PASS"
  echo "DIR=$OUT_DIR"
  echo "APK=$SIGNED_APK"
} 2>&1 | tee "$REPORT"

RC=${PIPESTATUS[0]}
exit "$RC"
