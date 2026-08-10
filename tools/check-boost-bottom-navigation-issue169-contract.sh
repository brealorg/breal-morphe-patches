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

def method_body(name: str) -> str:
    signature = re.search(
        rf"(?m)^[ \t]*private[ \t]+static[ \t]+[^\s(]+[ \t]+{re.escape(name)}[ \t]*\(",
        source,
    )
    if signature is None:
        raise SystemExit(f"FAIL=METHOD_NOT_FOUND method={name}")
    opening = source.find("{", signature.end())
    if opening < 0 or ";" in source[signature.end():opening]:
        raise SystemExit(f"FAIL=METHOD_BODY_NOT_FOUND method={name}")
    depth = 0
    for offset in range(opening, len(source)):
        character = source[offset]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return source[signature.start():offset + 1]
    raise SystemExit(f"FAIL=METHOD_NOT_CLOSED method={name}")

resolver = method_body("selectedItemIdForActivity")
handler = method_body("handleReselectedItem")
listener = method_body("attachReselectedListener")

if "com.rubenmayayo.reddit.ui.submissions.search.SearchSubmissionsActivity" not in source:
    raise SystemExit("FAIL=ISSUE169_SEARCH_RESULTS_ACTIVITY_CONSTANT_MISSING")
if "SEARCH_SUBMISSIONS_ACTIVITY.equals" not in resolver:
    raise SystemExit("FAIL=ISSUE169_SEARCH_RESULTS_DESTINATION_MAPPING_MISSING")
if '"item_search"' not in resolver:
    raise SystemExit("FAIL=ISSUE169_SEARCH_RESULTS_ITEM_MAPPING_MISSING")
if "MORPHE_BOOST_BOTTOM_NAV_RESELECT_RECOVERY_ISSUE169_V2" not in source:
    raise SystemExit("FAIL=ISSUE169_RESELECT_RECOVERY_MARKER_MISSING")
if "RESELECT_RECOVERY_MARKER" not in handler:
    raise SystemExit("FAIL=ISSUE169_RESELECT_RECOVERY_MARKER_NOT_USED")
if "Bottom navigation invalid reselect ignored marker=" in handler:
    raise SystemExit("FAIL=ISSUE169_INVALID_RESELECT_DROP_PATH_RETAINED")
if "boolean handled = handleItem(activity, item);" not in handler:
    raise SystemExit("FAIL=ISSUE169_RESELECT_MISMATCH_NOT_ROUTED_AS_SELECTION")
if "preserveSourceSelectionAfterRoute(" not in handler:
    raise SystemExit("FAIL=ISSUE169_SOURCE_SELECTION_NOT_PRESERVED")
if "SEARCH_SUBMISSIONS_ACTIVITY.equals(activityName)" not in handler:
    raise SystemExit("FAIL=ISSUE169_SEARCH_RESULTS_RESELECT_POLICY_MISSING")
if "openSearch(activity)" not in handler:
    raise SystemExit("FAIL=ISSUE169_SEARCH_RESULTS_RESELECT_ROUTE_MISSING")
if "MorpheBottomNavigationReselectedListener" not in listener:
    raise SystemExit("FAIL=ISSUE169_GENERAL_RESELECT_LISTENER_IDENTITY_MISSING")
if "Bottom navigation reselect routed marker=" not in listener:
    raise SystemExit("FAIL=ISSUE169_GENERAL_RESELECT_ROUTE_LOG_MISSING")
if "MorpheSubredditReselectedListener" in listener:
    raise SystemExit("FAIL=ISSUE169_OLD_RESELECT_LISTENER_IDENTITY_RETAINED")
if "Subreddit reselect routed marker=" in listener:
    raise SystemExit("FAIL=ISSUE169_OLD_RESELECT_ROUTE_LOG_RETAINED")

print("PASS=ISSUE169_SEARCH_RESULTS_MAP_TO_SEARCH")
print("PASS=ISSUE169_RESELECT_MISMATCH_RECLASSIFIED_AS_SELECTION")
print("PASS=ISSUE169_SOURCE_SELECTION_PRESERVED_AFTER_RECOVERY_ROUTE")
print("PASS=ISSUE169_SEARCH_RESULTS_RESELECT_OPENS_SEARCH_ENTRY")
print("PASS=ISSUE169_RESELECT_LISTENER_DIAGNOSTICS_GENERALIZED")
print("MARKER=MORPHE_BOOST_BOTTOM_NAV_RESELECT_RECOVERY_ISSUE169_V2")
PY_CONTRACT

echo "RESULT=MORPHE_ISSUE169_BOTTOM_NAV_STATE_RECOVERY_CONTRACT_PASS"
