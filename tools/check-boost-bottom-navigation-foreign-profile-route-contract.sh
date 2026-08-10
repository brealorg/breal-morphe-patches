#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java"

python3 - "$SOURCE" <<'PY_CONTRACT'
from pathlib import Path
import re
import sys

source_path = Path(sys.argv[1])
if not source_path.is_file():
    raise SystemExit(f"FAIL=SOURCE_MISSING path={source_path}")

source = source_path.read_text(encoding="utf-8")

def method(name: str) -> str:
    signature = re.search(
        rf"(?m)^[ \t]*private[ \t]+static[ \t]+[^\s(]+[ \t]+{re.escape(name)}[ \t]*\(",
        source,
    )
    if signature is None:
        raise SystemExit(f"FAIL=METHOD_NOT_FOUND_{name}")
    opening = source.find("{", signature.end())
    if opening < 0:
        raise SystemExit(f"FAIL=METHOD_BODY_NOT_FOUND_{name}")
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[signature.start():index + 1]
    raise SystemExit(f"FAIL=METHOD_NOT_CLOSED_{name}")

resolver = method("selectedItemIdForActivity")
helper = method("isForeignUserProfileActivity")
selected = method("handleItem")
reselected = method("handleReselectedItem")

if "MORPHE_BOOST_FOREIGN_PROFILE_TO_OWN_PROFILE_ROUTE_V1" not in source:
    raise SystemExit("FAIL=FOREIGN_PROFILE_ROUTE_MARKER_MISSING")

if "USER_ACTIVITY.equals(activityName)" not in resolver or '"item_profile"' not in resolver:
    raise SystemExit("FAIL=USERACTIVITY_VISUAL_PROFILE_MAPPING_CHANGED")

for token in (
    "currentUsername(activity)",
    '"x"',
    'getStringExtra("username")',
    "equalsIgnoreCase(",
    "return false;",
):
    if token not in helper:
        raise SystemExit("FAIL=FOREIGN_PROFILE_HELPER_MISSING_" + re.sub(r"[^A-Za-z0-9]+", "_", token).strip("_"))

selected_route = selected.find("Foreign Profile selection routes to own Profile marker=")
selected_current = selected.find("int currentItemId")
if selected_route < 0 or selected_current < 0 or selected_route > selected_current:
    raise SystemExit("FAIL=FOREIGN_PROFILE_SELECTED_ROUTE_NOT_BEFORE_SAME_ROOT")

if "isForeignUserProfileActivity(activity)" not in selected:
    raise SystemExit("FAIL=SELECTED_PATH_FOREIGN_PROFILE_DETECTION_MISSING")

if "openProfile(activity)" not in selected:
    raise SystemExit("FAIL=SELECTED_PATH_OWN_PROFILE_ROUTE_MISSING")

reselected_route = reselected.find("Foreign Profile reselect routes to own Profile marker=")
generic_noop = reselected.find("selectedId == inboxId")
if reselected_route < 0 or generic_noop < 0 or reselected_route > generic_noop:
    raise SystemExit("FAIL=FOREIGN_PROFILE_RESELECT_ROUTE_NOT_BEFORE_GENERIC_NOOP")

if "isForeignUserProfileActivity(activity)" not in reselected:
    raise SystemExit("FAIL=RESELECT_PATH_FOREIGN_PROFILE_DETECTION_MISSING")

if "openProfile(activity)" not in reselected:
    raise SystemExit("FAIL=RESELECT_PATH_OWN_PROFILE_ROUTE_MISSING")

if not re.search(
    r"selectedId\s*==\s*inboxId.*?selectedId\s*==\s*profileId.*?return\s+true;",
    reselected,
    re.S,
):
    raise SystemExit("FAIL=OWN_PROFILE_RESELECT_NOOP_POLICY_NOT_PRESERVED")

print("PASS=USERACTIVITY_VISUAL_PROFILE_SELECTION_PRESERVED")
print("PASS=FOREIGN_PROFILE_DETECTION_USES_SHOWN_AND_CURRENT_USERNAME")
print("PASS=FOREIGN_PROFILE_DETECTION_FAILS_CLOSED_TO_EXISTING_BEHAVIOR")
print("PASS=SELECTED_PROFILE_ROUTE_PRECEDES_SAME_ROOT_SHORT_CIRCUIT")
print("PASS=RESELECTED_PROFILE_ROUTE_PRECEDES_OWN_PROFILE_NOOP")
print("PASS=OWN_PROFILE_RESELECT_NOOP_PRESERVED")
print("MARKER=MORPHE_BOOST_FOREIGN_PROFILE_TO_OWN_PROFILE_ROUTE_V1")
PY_CONTRACT

echo 'RESULT=MORPHE_BOOST_FOREIGN_PROFILE_ROUTE_CONTRACT_PASS'
