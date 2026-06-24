#!/usr/bin/env bash

FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

PROFILE="${1:-}"
if [ -z "$PROFILE" ] || [ "$PROFILE" = "--help" ] || [ "$PROFILE" = "-h" ]; then
  cat <<'HELP'
Usage:
  tools/apply-boost-candidate.sh settings-only
  tools/apply-boost-candidate.sh full-combo
  tools/apply-boost-candidate.sh release-sanity

Profiles:
  settings-only   Minimal settings UI proof-of-life. No Spoof client, no login.
  full-combo      Functional Boost combo with Spoof client and media/login patches.
  release-sanity  Same as full-combo, intended for release-candidate sanity APK.

Environment:
  BOOST_BASE_APK      Optional override for base Boost APK.
  MORPHE_CLI          Optional override for Morphe CLI jar.
  MORPHE_OUTPUT_ROOT  Optional output root.
  MORPHE_SKIP_BUILD=1 Optional: use existing latest patches/build/libs/*.mpp.

For full-combo/release-sanity:
  ~/.config/morphe/reddit.env must export REDDIT_CLIENT_ID and REDDIT_REDIRECT_URI.
HELP
  exit 0
fi

case "$PROFILE" in
  settings-only|full-combo|release-sanity) ;;
  *)
    echo "FAIL: unknown profile: $PROFILE"
    echo "Run: tools/apply-boost-candidate.sh --help"
    exit 1
    ;;
esac

BASE_APK="${BOOST_BASE_APK:-/home/b-real/com.rubenmayayo.reddit_1.12.12-210011212_minAPI21(arm64-v8a,armeabi,armeabi-v7a,mips,mips64,x86,x86_64)(nodpi)_apkmirror.com.apk}"
CLI="${MORPHE_CLI:-/home/b-real/.local/share/morphe/tools/morphe-cli-1.10.0-dev.1-all.jar}"
OUT_ROOT="${MORPHE_OUTPUT_ROOT:-local-artifacts/boost-candidates}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$OUT_ROOT/$STAMP-$PROFILE"
APK_OUT="$OUT/boost-$PROFILE.apk"
APPLY_LOG="$OUT/morphe-apply.log"
PATCH_HELP="$OUT/morphe-cli-patch-help.txt"

mkdir -p "$OUT"

echo "===== profile ====="
echo "PROFILE=$PROFILE"
echo "OUT=$OUT"
echo "APK_OUT=$APK_OUT"

echo
echo "===== repo status ====="
git --no-pager status -sb || mark_fail "git status failed"

echo
echo "===== tool/base checks ====="
test -f "$BASE_APK" || mark_fail "missing base APK: $BASE_APK"
test -f "$CLI" || mark_fail "missing Morphe CLI: $CLI"

if [ -f "$BASE_APK" ] && [ -f "$CLI" ]; then
  ls -lh "$BASE_APK" "$CLI"
  sha256sum "$BASE_APK" "$CLI"
fi

echo
echo "===== verify Morphe CLI patch syntax ====="
if [ -f "$CLI" ]; then
  java -jar "$CLI" patch --help > "$PATCH_HELP" 2>&1
  grep -q -- '-p=<patchesFile>' "$PATCH_HELP" \
    || mark_fail "CLI help does not show expected -p=<patchesFile>"
  grep -q -- '--exclusive' "$PATCH_HELP" \
    || mark_fail "CLI help does not show --exclusive; refusing non-isolated apply"
  echo "OK: CLI supports -p and --exclusive"
fi

echo
echo "===== build/select MPP ====="
if [ "${MORPHE_SKIP_BUILD:-0}" != "1" ]; then
  ./gradlew :patches:buildAndroid
  GRADLE_RC=$?
  if [ "$GRADLE_RC" -ne 0 ]; then
    mark_fail "Gradle :patches:buildAndroid failed"
  fi
else
  echo "MORPHE_SKIP_BUILD=1, using latest existing MPP"
fi

MPP="$(ls -1t patches/build/libs/*.mpp 2>/dev/null | head -1 || true)"
test -n "$MPP" && test -f "$MPP" || mark_fail "missing MPP after build"

if [ -n "$MPP" ] && [ -f "$MPP" ]; then
  ls -lh "$MPP"
  sha256sum "$MPP"

  echo
  echo "===== MPP content gate ====="
  unzip -l "$MPP" | grep -E 'classes.dex|extensions/boostforreddit.mpe' \
    || mark_fail "MPP missing classes.dex or extensions/boostforreddit.mpe"
fi

requires_spoof=0
expects_target35=0

PATCH_ARGS=(patch --exclusive -p="$MPP" -o="$APK_OUT")

case "$PROFILE" in
  settings-only)
    REQUIRED_PATCHES=(
      "Show inline Giphy previews in comments"
    )
    ;;

  full-combo|release-sanity)
    requires_spoof=1
    expects_target35=1

    ENV_FILE="$HOME/.config/morphe/reddit.env"
    echo
    echo "===== reddit spoof env gate ====="
    test -f "$ENV_FILE" || mark_fail "missing reddit env: $ENV_FILE"
    if [ -f "$ENV_FILE" ]; then
      set -a
      . "$ENV_FILE"
      set +a
    fi

    test -n "${REDDIT_CLIENT_ID:-}" || mark_fail "REDDIT_CLIENT_ID missing"
    test -n "${REDDIT_REDIRECT_URI:-}" || mark_fail "REDDIT_REDIRECT_URI missing"

    echo "REDDIT_CLIENT_ID length: ${#REDDIT_CLIENT_ID}"
    echo "REDDIT_REDIRECT_URI: ${REDDIT_REDIRECT_URI:-missing}"

    # Known-good binding position: options before first enabled patch, directly before Spoof client.
    PATCH_ARGS+=(
      -O=client-id="$REDDIT_CLIENT_ID"
      -O=redirect-uri="$REDDIT_REDIRECT_URI"
      -O=user-agent="android:com.rubenmayayo.reddit:v1.12.12 (by /u/breal_morphe)"
      -e="Spoof client"
    )

    REQUIRED_PATCHES=(
      "Spoof client"
      "Modify login WebView"
      "Disable ads"
      "Fix missing audio in video downloads"
      "Fix download completed notification visibility"
      "Fix /s/ links"
      "Fix slow Giphy loading"
      "Show inline Giphy previews in comments"
      "Fix Boost target SDK 35 compatibility"
    )
    ;;
esac

for patch_name in "${REQUIRED_PATCHES[@]}"; do
  if [ "$patch_name" != "Spoof client" ]; then
    PATCH_ARGS+=(-e="$patch_name")
  fi
done

PATCH_ARGS+=("$BASE_APK")

echo
echo "===== canonical patch args, redacted ====="
for arg in "${PATCH_ARGS[@]}"; do
  case "$arg" in
    -O=client-id=*)
      echo "-O=client-id=<redacted length ${#REDDIT_CLIENT_ID}>"
      ;;
    *)
      echo "$arg"
      ;;
  esac
done

echo
echo "===== apply candidate ====="
if [ "$FAIL" -eq 0 ]; then
  java -jar "$CLI" "${PATCH_ARGS[@]}" 2>&1 | tee "$APPLY_LOG"
  APPLY_RC=${PIPESTATUS[0]}
  if [ "$APPLY_RC" -ne 0 ]; then
    mark_fail "Morphe patch apply failed"
  fi
else
  echo "SKIP: apply because preflight failed"
fi

echo
echo "===== apply log gate ====="
if [ -f "$APPLY_LOG" ]; then
  grep -Ei 'Applied:|FAILED:|SEVERE:|error|client-id requires|option .* requires' "$APPLY_LOG" || true

  if grep -Ei 'FAILED:|SEVERE:|client-id requires|option .* requires' "$APPLY_LOG" >/dev/null 2>&1; then
    mark_fail "apply log contains hard failure"
  fi

  for patch_name in "${REQUIRED_PATCHES[@]}"; do
    grep -F "Applied: $patch_name" "$APPLY_LOG" >/dev/null 2>&1 \
      || mark_fail "required patch not applied: $patch_name"
  done
else
  mark_fail "missing apply log"
fi

echo
echo "===== APK artifact gate ====="
if [ -f "$APK_OUT" ]; then
  ls -lh "$APK_OUT"
  sha256sum "$APK_OUT"
else
  mark_fail "candidate APK not created"
fi

echo
echo "===== static marker gate ====="
if [ -f "$APK_OUT" ]; then
  DEX_LIST="$(zipinfo -1 "$APK_OUT" 'classes*.dex' 2>/dev/null | sort)"
  echo "$DEX_LIST"

  check_marker() {
    marker="$1"
    found=0
    while IFS= read -r dex; do
      [ -n "$dex" ] || continue
      if unzip -p "$APK_OUT" "$dex" | strings | grep -F "$marker" >/dev/null 2>&1; then
        echo "OK: $marker in $dex"
        found=1
      fi
    done <<EOF
$DEX_LIST
EOF
    if [ "$found" -eq 0 ]; then
      mark_fail "missing DEX marker: $marker"
    fi
  }

  check_marker "BoostMorphePreferenceFragment"
  check_marker "BoostMediaPreferences"
  check_marker "morphe_boost_settings"
fi

echo
echo "===== resource decode gate ====="
if command -v apktool >/dev/null 2>&1 && [ -f "$APK_OUT" ]; then
  DECODE="$OUT/apktool-candidate"
  apktool d -f -s "$APK_OUT" -o "$DECODE" >/dev/null \
    || mark_fail "apktool decode failed"

  XML="$DECODE/res/xml/morphe_boost_settings.xml"
  if [ -f "$XML" ]; then
    echo "OK: $XML exists"
    grep -nF 'morphe_boost_inline_previews' "$XML" || mark_fail "missing inline key in settings XML"
    grep -nF 'morphe_boost_left_align_previews' "$XML" || mark_fail "missing left-align key in settings XML"
    grep -nF 'morphe_boost_hide_source_after_preview' "$XML" || mark_fail "missing hide-source key in settings XML"
  else
    mark_fail "missing morphe_boost_settings.xml"
  fi

  for f in \
    "$DECODE/res/xml/pref_headers_v2.xml" \
    "$DECODE/res/xml/pref_headers.xml" \
    "$DECODE/res/xml/pref_headers_simple.xml"
  do
    if [ -f "$f" ]; then
      grep -nF 'app.morphe.extension.boostforreddit.settings.BoostMorphePreferenceFragment' "$f" \
        || mark_fail "missing Morphe fragment in $f"
    else
      mark_fail "missing decoded settings header: $f"
    fi
  done
else
  echo "WARN: apktool unavailable or APK missing; resource decode skipped"
fi

echo
echo "===== package summary ====="
if command -v aapt >/dev/null 2>&1 && [ -f "$APK_OUT" ]; then
  aapt dump badging "$APK_OUT" | sed -n '1,24p'
  if [ "$expects_target35" -eq 1 ]; then
    aapt dump badging "$APK_OUT" | grep -F "targetSdkVersion:'35'" \
      || mark_fail "targetSdkVersion 35 expected but not found"
  fi
fi

echo
echo "===== result ====="
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=BOOST_APPLY_${PROFILE}_GREEN"
else
  echo "RESULT=BOOST_APPLY_${PROFILE}_FAIL"
fi
echo "PROFILE=$PROFILE"
echo "OUT=$OUT"
echo "APK_OUT=$APK_OUT"
echo "APPLY_LOG=$APPLY_LOG"
echo "Terminal still alive."
exit "$FAIL"
