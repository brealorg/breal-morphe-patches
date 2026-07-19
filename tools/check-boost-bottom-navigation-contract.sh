#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

test -f "$SOURCE"

rg -q -F 'MORPHE_BOOST_NATIVE_COORDINATOR_BOTTOM_NAV_ISSUE97_V1' "$SOURCE"
rg -q -F 'MORPHE_BOOST_NATIVE_PROFILE_LONGPRESS_ISSUE97_V2' "$SOURCE"
rg -q -F 'configureNativeCanonicalNavigation(' "$SOURCE"
rg -q -F 'scheduleNativeProfileLongPress(' "$SOURCE"
rg -q -F 'BOTTOM_NAV_BASE_ACTIVITY + "$y"' "$SOURCE"
rg -q -F 'getDeclaredConstructor(' "$SOURCE"
rg -q -F 'profileItem.setOnLongClickListener(' "$SOURCE"
rg -q -F 'profileItem.setLongClickable(true);' "$SOURCE"
rg -q -F 'nativeListener.onLongClick(' "$SOURCE"

MENU_LINE="$(rg -n -m1 -F 'menu.clear();' "$SOURCE" | cut -d: -f1)"
SCHEDULE_LINE="$(
    rg -n -F 'scheduleNativeProfileLongPress(' "$SOURCE" |
    tail -1 |
    cut -d: -f1
)"

test "$MENU_LINE" -lt "$SCHEDULE_LINE"

echo 'NATIVE_COORDINATOR_NAVIGATION=PASS'
echo 'PROFILE_LONGPRESS_REATTACHED_AFTER_MENU_REBUILD=PASS'
echo 'RESULT=MORPHE_ISSUE97_BOTTOM_NAVIGATION_CONTRACT_OK'
