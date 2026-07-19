#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

test -f "$SOURCE"

rg -q -F 'MORPHE_BOOST_SEARCH_CONTEXT_ISSUE94_SUBREDDIT_PARCELABLE_V3' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SEARCH_SCOPE_HINT_ISSUE94_POST_ONCREATE_V2' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SEARCH_INITIAL_FOCUS_V1' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SEARCH_FRESH_ACTIVITY_ISSUE86_V2' "$SOURCE"
rg -q -F 'MORPHE_BOOST_SEARCH_RESELECT_ISSUE86_V1' "$SOURCE"
rg -q -F 'scheduleInitialSearchInputFocus(activity);' "$SOURCE"
rg -q -F 'attachSubredditSearchScope(' "$SOURCE"
rg -q -F '"subscription",' "$SOURCE"
rg -q -F '|| !SUBREDDIT_ACTIVITY.equals(' "$SOURCE"
rg -q -F 'return focusSearchInput(activity);' "$SOURCE"
rg -q -F 'scheduleSearchScopeHint(activity);' "$SOURCE"
rg -q -F 'decor.post(new Runnable() {' "$SOURCE"
rg -q -F 'intent.getParcelableExtra("subscription");' "$SOURCE"
rg -q -U 'findField\(\s*subscription\.getClass\(\),\s*"b"\s*\)' "$SOURCE"
rg -q -U 'setHint\(\s*"Search in r/" \+ subreddit\s*\)' "$SOURCE"
rg -q -F 'activity.hasWindowFocus()' "$SOURCE"
rg -q -F 'SEARCH_INITIAL_FOCUS_RETRY_DELAY_MS' "$SOURCE"

SEARCH_ROUTE_BLOCK="$(
    sed -n \
        '/private static boolean openSearch(/,/private static boolean focusSearchInput(/p' \
        "$SOURCE"
)"

rg -q -F 'SEARCH_FRESH_ENTRY_MARKER' <<< "$SEARCH_ROUTE_BLOCK"
if rg -q 'FLAG_ACTIVITY_CLEAR_TOP|FLAG_ACTIVITY_SINGLE_TOP' <<< "$SEARCH_ROUTE_BLOCK"; then
    echo 'FAIL: global Search still reuses an existing activity' >&2
    exit 1
fi

if rg -q 'nativeSearch|"T1"' <<< "$SEARCH_ROUTE_BLOCK"; then
    echo 'FAIL: subreddit Search still invokes unrelated obfuscated T1' >&2
    exit 1
fi

SCOPE_HINT_LINE="$(rg -n -m1 -F 'scheduleSearchScopeHint(activity);' "$SOURCE" | cut -d: -f1)"
INITIAL_FOCUS_LINE="$(rg -n -m1 -F 'scheduleInitialSearchInputFocus(activity);' "$SOURCE" | cut -d: -f1)"
NAV_INSTALL_LINE="$(rg -n -m1 -F 'standardizeMaterialNavigation(' "$SOURCE" | cut -d: -f1)"
RESELECT_LINE="$(rg -n -m1 -F 'return focusSearchInput(activity);' "$SOURCE" | cut -d: -f1)"
SELECTION_GUARD_LINE="$(rg -n -m1 -F 'currentItemId != 0' "$SOURCE" | cut -d: -f1)"
SUBREDDIT_SCOPE_LINE="$(rg -n -F 'attachSubredditSearchScope(' "$SOURCE" | tail -1 | cut -d: -f1)"
START_ACTIVITY_LINE="$(rg -n -m1 -F 'activity.startActivity(intent);' "$SOURCE" | cut -d: -f1)"

test "$SCOPE_HINT_LINE" -lt "$NAV_INSTALL_LINE"
test "$INITIAL_FOCUS_LINE" -lt "$NAV_INSTALL_LINE"
test "$RESELECT_LINE" -lt "$SELECTION_GUARD_LINE"
test "$SUBREDDIT_SCOPE_LINE" -lt "$START_ACTIVITY_LINE"

echo 'SCOPE_HINT_SCHEDULED_BEFORE_NAV_INSTALL=PASS'
echo 'INITIAL_FOCUS_SCHEDULED_BEFORE_NAV_INSTALL=PASS'
echo 'GLOBAL_SEARCH_FRESH_ACTIVITY=PASS'
echo 'RESELECT_BEFORE_SELECTION_GUARD=PASS'
echo 'SUBREDDIT_SCOPE_ATTACHED_TO_FRESH_SEARCH=PASS'
echo 'OBFUSCATED_T1_ROUTE_REMOVED=PASS'
echo 'RESULT=MORPHE_SEARCH_ROUTE_CONTRACT_OK'
