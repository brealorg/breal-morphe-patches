#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java"

python3 - "$SOURCE" <<'PY'
from pathlib import Path
import re
import sys

source_path = Path(sys.argv[1])
if not source_path.is_file():
    raise SystemExit(f"FAIL=SOURCE_MISSING path={source_path}")

source = source_path.read_text(encoding="utf-8")


def method_body(name: str) -> str:
    signature = re.search(
        rf"(?m)^[ \t]*private[ \t]+static[ \t]+"
        rf"[^\s(]+[ \t]+{re.escape(name)}[ \t]*\(",
        source,
    )
    if signature is None:
        raise SystemExit(f"FAIL=METHOD_NOT_FOUND method={name}")

    opening = source.find("{", signature.end())
    if opening < 0 or ";" in source[signature.end() : opening]:
        raise SystemExit(f"FAIL=METHOD_BODY_NOT_FOUND method={name}")

    depth = 0
    for offset in range(opening, len(source)):
        character = source[offset]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return source[signature.start() : offset + 1]

    raise SystemExit(f"FAIL=METHOD_NOT_CLOSED method={name}")


selected_listener = method_body("attachSelectedListener")
reselected_listener = method_body("attachReselectedListener")
selected_handler = method_body("handleItem")

if "handleItem(" not in selected_listener:
    raise SystemExit("FAIL=SELECT_LISTENER_LOST_SELECTION_HANDLER")

if (
    "dispatchHomeGoToTop(" not in reselected_listener
    and "handleReselectedItem(" not in reselected_listener
):
    raise SystemExit("FAIL=HOME_RESELECT_ROUTE_MISSING")

current_guard = "selectedId == currentItemId"
subscriptions_route = "if (selectedId == subscriptionsId)"

guard_offset = selected_handler.find(current_guard)
subscriptions_offset = selected_handler.find(subscriptions_route)

broken_issue163_path = (
    "handleItem(" in reselected_listener
    and guard_offset >= 0
    and subscriptions_offset >= 0
    and guard_offset < subscriptions_offset
)

if broken_issue163_path:
    print("BROKEN_RESELECT_CALLS_SELECTION_HANDLER=YES")
    print("BROKEN_CURRENT_ITEM_GUARD_PRECEDES_SUBSCRIPTIONS_ROUTE=YES")
    raise SystemExit(
        "FAIL=ISSUE163_RESELECT_SWALLOWED_BY_CURRENT_ITEM_GUARD"
    )

if "handleItem(" in reselected_listener:
    raise SystemExit(
        "FAIL=RESELECT_DISPATCH_NOT_SEPARATED shared_handler=handleItem"
    )

if "dispatchHomeGoToTop(" in reselected_listener:
    raise SystemExit(
        "FAIL=RESELECT_POLICY_SPLIT home_route=listener"
    )

dedicated_call = "handleReselectedItem("
if dedicated_call not in reselected_listener:
    raise SystemExit(
        "FAIL=DEDICATED_RESELECT_HANDLER_NOT_WIRED "
        "expected=handleReselectedItem"
    )

reselected_handler = method_body("handleReselectedItem")

required_policy_tokens = (
    "MORPHE_BOOST_BOTTOM_NAV_STATE_MACHINE_ISSUE163_V1",
    "homeId",
    "searchId",
    "subscriptionsId",
    "inboxId",
    "profileId",
    "dispatchHomeGoToTop(",
    "focusSearchInput(",
    "openSubscriptions(",
)

for token in required_policy_tokens:
    if token not in reselected_handler:
        raise SystemExit(
            f"FAIL=RESELECT_POLICY_TOKEN_MISSING token={token}"
        )

reselect_guard_offset = reselected_handler.find(current_guard)
reselect_subscriptions_offset = reselected_handler.find(subscriptions_route)

if reselect_subscriptions_offset < 0:
    raise SystemExit("FAIL=RESELECT_SUBSCRIPTIONS_ROUTE_MISSING")

if (
    reselect_guard_offset >= 0
    and reselect_guard_offset < reselect_subscriptions_offset
):
    raise SystemExit(
        "FAIL=RESELECT_CURRENT_ITEM_GUARD_SWALLOWS_SUBSCRIPTIONS"
    )

print("PASS=SELECT_AND_RESELECT_DISPATCH_SEPARATED")
print("PASS=RESELECT_POLICY_COVERS_FIVE_DESTINATIONS")
print("PASS=SUBSCRIPTIONS_RESELECT_ROUTE_REACHABLE")
print("MARKER=MORPHE_BOOST_BOTTOM_NAV_STATE_MACHINE_ISSUE163_V1")
PY

echo "RESULT=MORPHE_ISSUE163_STATE_MACHINE_CONTRACT_PASS"
