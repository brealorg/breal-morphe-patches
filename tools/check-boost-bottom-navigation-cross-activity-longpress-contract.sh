#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java"

python3 - "$SOURCE" <<'PY_CONTRACT'
from pathlib import Path
import re
import sys

source = Path(sys.argv[1]).read_text(
    encoding="utf-8",
)

def method_body(name: str) -> str:
    match = re.search(
        rf"(?m)^[ \t]*(?:public|private|protected)[ \t]+static[ \t]+[^\s(]+[ \t]+{re.escape(name)}[ \t]*\(",
        source,
    )
    if match is None:
        raise SystemExit(
            f"FAIL=METHOD_NOT_FOUND method={name}"
        )
    opening = source.find(
        "{",
        match.end(),
    )
    depth = 0
    for index in range(
        opening,
        len(source),
    ):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[
                    match.start():index + 1
                ]
    raise SystemExit(
        f"FAIL=METHOD_NOT_CLOSED method={name}"
    )

profile_schedule = method_body(
    "scheduleNativeProfileLongPress"
)
subscriptions_schedule = method_body(
    "scheduleNativeSubscriptionsLongPress"
)
profile_fallback = method_body(
    "invokeProfileLongPressFallback"
)
subscriptions_fallback = method_body(
    "invokeSubscriptionsLongPressFallback"
)
add_account = method_body(
    "invokeProfileAddAccount"
)
queue_selection = method_body(
    "queueProfileSelection"
)
consume_selection = method_body(
    "schedulePendingProfileSelection"
)
standardize_home = method_body(
    "standardizeHome"
)

bridge_marker = (
    "MORPHE_BOOST_PROFILE_LONGPRESS_DIALOG_BRIDGE_V2"
)

if bridge_marker not in source:
    raise SystemExit(
        "FAIL=PROFILE_DIALOG_BRIDGE_MARKER_MISSING"
    )

if "Activity is not Boost base activity:" in profile_schedule:
    raise SystemExit(
        "FAIL=PROFILE_NON_BASE_THROW_RETAINED"
    )

if "Activity is not Boost base activity:" in subscriptions_schedule:
    raise SystemExit(
        "FAIL=SUBSCRIPTIONS_NON_BASE_THROW_RETAINED"
    )

if "invokeProfileLongPressFallback(" not in profile_schedule:
    raise SystemExit(
        "FAIL=PROFILE_FALLBACK_NOT_ATTACHED"
    )

if "invokeSubscriptionsLongPressFallback(" not in subscriptions_schedule:
    raise SystemExit(
        "FAIL=SUBSCRIPTIONS_FALLBACK_NOT_ATTACHED"
    )

required_profile_tokens = (
    '"com.rubenmayayo.reddit.ui.customviews.dialogs.UserSelectorView"',
    '"com.rubenmayayo.reddit.ui.customviews.dialogs.UserSelectorView$a"',
    "Proxy.newProxyInstance(",
    "new InvocationHandler()",
    "Context.class,",
    "boolean.class,",
    '"setCallback"',
    '"m1.f$e"',
    '"Z"',
    '"o"',
    '"W"',
    "queueProfileSelection(",
    "invokeProfileAddAccount(",
)

for token in required_profile_tokens:
    if token not in profile_fallback:
        raise SystemExit(
            "FAIL=PROFILE_DIALOG_BRIDGE_TOKEN_MISSING "
            f"token={token}"
        )

for forbidden in (
    '"com.rubenmayayo.reddit.ui.customviews.e"',
    '"getSupportFragmentManager"',
    '"userSelectorBottomSheetDialog"',
    "D1+show",
):
    if forbidden in profile_fallback:
        raise SystemExit(
            "FAIL=CRASHING_BOTTOM_SHEET_PATH_RETAINED "
            f"token={forbidden}"
        )

if '"l0"' not in add_account:
    raise SystemExit(
        "FAIL=ADD_ACCOUNT_NATIVE_I_L0_MISSING"
    )

if "PENDING_PROFILE_SELECTION_INDEX" not in queue_selection:
    raise SystemExit(
        "FAIL=PENDING_SELECTION_NOT_QUEUED"
    )

if "MAIN_ACTIVITY" not in queue_selection:
    raise SystemExit(
        "FAIL=PENDING_SELECTION_MAIN_ROUTE_MISSING"
    )

if '"L2"' not in consume_selection:
    raise SystemExit(
        "FAIL=NATIVE_MAIN_L2_CONSUMER_MISSING"
    )

if "schedulePendingProfileSelection(activity);" not in standardize_home:
    raise SystemExit(
        "FAIL=MAIN_ON_RESUME_PENDING_SELECTION_CONSUMER_MISSING"
    )

if "NAVIGATION_UTILITY" not in subscriptions_fallback:
    raise SystemExit(
        "FAIL=SUBSCRIPTIONS_NATIVE_UTILITY_MISSING"
    )

if '"t0"' not in subscriptions_fallback:
    raise SystemExit(
        "FAIL=SUBSCRIPTIONS_NATIVE_T0_ACTION_MISSING"
    )

if 'BOTTOM_NAV_BASE_ACTIVITY + "$y"' not in profile_schedule:
    raise SystemExit(
        "FAIL=PROFILE_BASE_NATIVE_LISTENER_REMOVED"
    )

if 'BOTTOM_NAV_BASE_ACTIVITY + "$z"' not in subscriptions_schedule:
    raise SystemExit(
        "FAIL=SUBSCRIPTIONS_BASE_NATIVE_LISTENER_REMOVED"
    )

print(
    "PASS=PROFILE_BASE_ACTIVITY_NATIVE_LISTENER_PRESERVED"
)
print(
    "PASS=SUBSCRIPTIONS_BASE_ACTIVITY_NATIVE_LISTENER_PRESERVED"
)
print(
    "PASS=PROFILE_NON_BASE_USES_NATIVE_USER_SELECTOR_VIEW_DIALOG"
)
print(
    "PASS=PROFILE_DIALOG_USES_EXPLICIT_USER_SELECTOR_CALLBACK_PROXY"
)
print(
    "PASS=PROFILE_ADD_ACCOUNT_USES_NATIVE_I_L0_ACTION"
)
print(
    "PASS=PROFILE_ACCOUNT_SELECTION_QUEUES_NATIVE_MAIN_L2_ACTION"
)
print(
    "PASS=CRASHING_BOTTOM_SHEET_HOST_PATH_REMOVED"
)
print(
    "PASS=SUBSCRIPTIONS_NON_BASE_USES_NATIVE_I_T0_ACTION"
)
print(
    "MARKER=MORPHE_BOOST_PROFILE_LONGPRESS_DIALOG_BRIDGE_V2"
)
PY_CONTRACT

echo "RESULT=MORPHE_BOTTOM_NAV_CROSS_ACTIVITY_LONGPRESS_CONTRACT_PASS"
