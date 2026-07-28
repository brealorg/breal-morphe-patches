#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

test -f "$SOURCE"

rg -q -F 'MORPHE_BOOST_NATIVE_COORDINATOR_BOTTOM_NAV_ISSUE97_V1' "$SOURCE"
rg -q -F 'MORPHE_BOOST_NATIVE_PROFILE_LONGPRESS_ISSUE97_V2' "$SOURCE"
rg -q -F 'MORPHE_BOOST_NATIVE_SUBSCRIPTIONS_LONGPRESS_ISSUE135_V1' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SUBSCRIPTIONS_CURRENT_SUBREDDIT_ISSUE141_V2_NATIVE_J6' "$SOURCE"
rg -q -F 'private static boolean openSubscriptionsFromCurrentSubreddit(' "$SOURCE"
rg -q -F 'SUBREDDIT_ACTIVITY.equals(' "$SOURCE"
rg -q -F '"j6"' "$SOURCE"
rg -q -F '"SubredditActivity.j6"' "$SOURCE"
rg -q -F 'route.invoke(activity);' "$SOURCE"
rg -q -F '" handled=true"' "$SOURCE"
! rg -q -F '"L1"' "$SOURCE"
! rg -q -F 'Boolean.TRUE.equals(rawHandled)' "$SOURCE"
rg -q -F 'configureNativeCanonicalNavigation(' "$SOURCE"
rg -q -F 'scheduleNativeProfileLongPress(' "$SOURCE"
rg -q -F 'BOTTOM_NAV_BASE_ACTIVITY + "$y"' "$SOURCE"
rg -q -F 'getDeclaredConstructor(' "$SOURCE"
rg -q -F 'profileItem.setOnLongClickListener(' "$SOURCE"
rg -q -F 'profileItem.setLongClickable(true);' "$SOURCE"
rg -q -F 'nativeListener.onLongClick(' "$SOURCE"
rg -q -F 'scheduleNativeSubscriptionsLongPress(' "$SOURCE"
rg -q -F 'BOTTOM_NAV_BASE_ACTIVITY + "$z"' "$SOURCE"
rg -q -F 'subscriptionsItem.setOnLongClickListener(' "$SOURCE"
rg -q -F 'subscriptionsItem.setLongClickable(true);' "$SOURCE"

MENU_LINE="$(rg -n -m1 -F 'menu.clear();' "$SOURCE" | cut -d: -f1)"
PROFILE_SCHEDULE_LINE="$(
    rg -n -F 'scheduleNativeProfileLongPress(' "$SOURCE" |
    tail -1 |
    cut -d: -f1
)"
SUBSCRIPTIONS_SCHEDULE_LINE="$(
    rg -n -F 'scheduleNativeSubscriptionsLongPress(' "$SOURCE" |
    tail -1 |
    cut -d: -f1
)"

test "$MENU_LINE" -lt "$PROFILE_SCHEDULE_LINE"
test "$MENU_LINE" -lt "$SUBSCRIPTIONS_SCHEDULE_LINE"

CURRENT_SUBREDDIT_ROUTE_LINE="$(
    rg -n -m1 -F 'if (openSubscriptionsFromCurrentSubreddit(activity)) {' "$SOURCE" |
    cut -d: -f1
)"
GENERIC_SUBSCRIPTIONS_ROUTE_LINE="$(
    rg -n -F 'NAVIGATION_UTILITY,' "$SOURCE" |
    tail -1 |
    cut -d: -f1
)"

test -n "$CURRENT_SUBREDDIT_ROUTE_LINE"
test -n "$GENERIC_SUBSCRIPTIONS_ROUTE_LINE"
test "$CURRENT_SUBREDDIT_ROUTE_LINE" -lt "$GENERIC_SUBSCRIPTIONS_ROUTE_LINE"

echo 'NATIVE_COORDINATOR_NAVIGATION=PASS'
echo 'PROFILE_LONGPRESS_REATTACHED_AFTER_MENU_REBUILD=PASS'
echo 'SUBSCRIPTIONS_LONGPRESS_REATTACHED_AFTER_MENU_REBUILD=PASS'
echo 'CURRENT_SUBREDDIT_ROUTE_PRECEDES_GENERIC_SUBSCRIPTIONS_FALLBACK=PASS'
echo 'RESULT=MORPHE_BOTTOM_NAVIGATION_LONGPRESS_CONTRACT_OK'
