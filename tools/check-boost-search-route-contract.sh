#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

test -f "$SOURCE"

rg -q -F 'MORPHE_BOOST_SEARCH_CONTEXT_ISSUE94_SUBREDDIT_T1_V2' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SEARCH_SCOPE_HINT_ISSUE94_POST_ONCREATE_V2' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SEARCH_RESELECT_ISSUE86_V1' "$SOURCE"
rg -q -F 'nativeSearch.invoke(activity);' "$SOURCE"
rg -q -F 'if (SUBREDDIT_ACTIVITY.equals(activity.getClass().getName())) {' "$SOURCE"
rg -q -F 'return focusSearchInput(activity);' "$SOURCE"
rg -q -F 'scheduleSearchScopeHint(activity);' "$SOURCE"
rg -q -F 'decor.post(new Runnable() {' "$SOURCE"
rg -q -F 'intent.getParcelableExtra("subscription");' "$SOURCE"
rg -q -U 'findField\(\s*subscription\.getClass\(\),\s*"b"\s*\)' "$SOURCE"
rg -q -U 'setHint\(\s*"Search in r/" \+ subreddit\s*\)' "$SOURCE"

SCOPE_HINT_LINE="$(rg -n -m1 -F 'scheduleSearchScopeHint(activity);' "$SOURCE" | cut -d: -f1)"
NAV_INSTALL_LINE="$(rg -n -m1 -F 'standardizeMaterialNavigation(' "$SOURCE" | cut -d: -f1)"
RESELECT_LINE="$(rg -n -m1 -F 'return focusSearchInput(activity);' "$SOURCE" | cut -d: -f1)"
SELECTION_GUARD_LINE="$(rg -n -m1 -F 'currentItemId != 0' "$SOURCE" | cut -d: -f1)"
NATIVE_LINE="$(rg -n -m1 -F 'nativeSearch.invoke(activity);' "$SOURCE" | cut -d: -f1)"
NATIVE_SCOPE_LINE="$(rg -n -m1 -F 'if (SUBREDDIT_ACTIVITY.equals(activity.getClass().getName())) {' "$SOURCE" | cut -d: -f1)"
FALLBACK_LINE="$(rg -n -m1 -F 'Class<?> destination = Class.forName(' "$SOURCE" | cut -d: -f1)"

test "$SCOPE_HINT_LINE" -lt "$NAV_INSTALL_LINE"
test "$RESELECT_LINE" -lt "$SELECTION_GUARD_LINE"
test "$NATIVE_SCOPE_LINE" -lt "$NATIVE_LINE"
test "$NATIVE_LINE" -lt "$FALLBACK_LINE"

echo 'SCOPE_HINT_SCHEDULED_BEFORE_NAV_INSTALL=PASS'
echo 'RESELECT_BEFORE_SELECTION_GUARD=PASS'
echo 'NATIVE_T1_SCOPED_TO_SUBREDDIT=PASS'
echo 'NATIVE_T1_BEFORE_GLOBAL_FALLBACK=PASS'
echo 'RESULT=MORPHE_SEARCH_ROUTE_CONTRACT_OK'
