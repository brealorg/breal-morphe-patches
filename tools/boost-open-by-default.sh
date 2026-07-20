#!/usr/bin/env bash

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PKG=""
USER_ID="${MORPHE_ANDROID_USER:-0}"
DO_LAUNCH=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --dev)
      PKG="com.rubenmayayo.reddit.dev"
      ;;
    --normal)
      PKG="com.rubenmayayo.reddit"
      ;;
    --launch)
      DO_LAUNCH=1
      ;;
    *)
      PKG="$1"
      ;;
  esac
  shift
done

DOMAINS=(
  reddit.com
  www.reddit.com
  m.reddit.com
  old.reddit.com
  new.reddit.com
  np.reddit.com
  amp.reddit.com
  redd.it
  v.redd.it
  reddit.app.link
  click.redditmail.com
  boostforreddit.com
)

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

SERIAL="$(
  "$ROOT/tools/boost-adb-serial.sh" \
    --hint "${MORPHE_ADB_HINT:-192.168.1.248}" \
    --expect-model "${MORPHE_ADB_EXPECT_MODEL:-Pixel_6}" \
    --expect-device "${MORPHE_ADB_EXPECT_DEVICE:-oriole}"
)" || fail "could not resolve expected Pixel adb target"
[ -n "$SERIAL" ] || fail "no adb device found"

if [ -z "$PKG" ]; then
  if adb -s "$SERIAL" shell pm path com.rubenmayayo.reddit.dev >/dev/null 2>&1; then
    PKG="com.rubenmayayo.reddit.dev"
  elif adb -s "$SERIAL" shell pm path com.rubenmayayo.reddit >/dev/null 2>&1; then
    PKG="com.rubenmayayo.reddit"
  else
    fail "neither dev nor normal Boost package is installed"
  fi
fi

echo "===== target ====="
echo "SERIAL=$SERIAL"
echo "USER_ID=$USER_ID"
echo "PKG=$PKG"
echo "DO_LAUNCH=$DO_LAUNCH"

adb -s "$SERIAL" shell pm path "$PKG" >/dev/null 2>&1 || fail "package not installed: $PKG"

echo
echo "===== before: pm get-app-links ====="
adb -s "$SERIAL" shell pm get-app-links --user "$USER_ID" "$PKG" || true

echo
echo "===== set app-link state ====="
adb -s "$SERIAL" shell pm set-app-links-allowed --user "$USER_ID" --package "$PKG" true || true

if adb -s "$SERIAL" shell pm set-app-links-user-selection --user "$USER_ID" --package "$PKG" true "${DOMAINS[@]}"; then
  echo "DOMAIN_SELECTION=OK"
else
  echo "DOMAIN_SELECTION=BULK_FAILED"
  for domain in "${DOMAINS[@]}"; do
    printf 'DOMAIN=%s ' "$domain"
    adb -s "$SERIAL" shell pm set-app-links-user-selection --user "$USER_ID" --package "$PKG" true "$domain" >/dev/null 2>&1 \
      && echo "OK" \
      || echo "FAILED"
  done
fi

echo
echo "===== after: pm get-app-links ====="
adb -s "$SERIAL" shell pm get-app-links --user "$USER_ID" "$PKG" || true

echo
echo "===== resolver probes ====="
for url in \
  "https://reddit.com/r/BoostForReddit" \
  "https://www.reddit.com/r/BoostForReddit" \
  "https://m.reddit.com/r/BoostForReddit" \
  "https://redd.it/1" \
  "https://old.reddit.com/r/BoostForReddit" \
  "https://new.reddit.com/r/BoostForReddit" \
  "https://np.reddit.com/r/BoostForReddit"
do
  echo "--- $url"
  adb -s "$SERIAL" shell cmd package resolve-activity --brief --user "$USER_ID" \
    -a android.intent.action.VIEW \
    -c android.intent.category.BROWSABLE \
    -d "$url" 2>/dev/null || true
done

if [ "$DO_LAUNCH" -eq 1 ]; then
  echo
  echo "===== launch probes ====="
  for url in \
    "https://reddit.com/r/BoostForReddit" \
    "https://redd.it/1"
  do
    echo "--- launch $url"
    adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
    adb -s "$SERIAL" shell am start -W --user "$USER_ID" \
      -a android.intent.action.VIEW \
      -c android.intent.category.BROWSABLE \
      -d "$url" || true
    sleep 2

    TOP="$(
      adb -s "$SERIAL" shell dumpsys activity activities 2>/dev/null \
        | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity' \
        | head -1
    )"
    echo "TOP=$TOP"

    if printf '%s\n' "$TOP" | grep -q "$PKG"; then
      echo "OPENED_TARGET=OK"
    else
      echo "OPENED_TARGET=NO"
    fi

    adb -s "$SERIAL" shell input keyevent HOME >/dev/null 2>&1 || true
  done
fi

echo
echo "RESULT=MORPHE_BOOST_OPEN_BY_DEFAULT_HELPER_RUN_OK"
