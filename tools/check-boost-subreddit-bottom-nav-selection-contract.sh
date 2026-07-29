#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java"

python3 - "$SOURCE" <<'PY'
from pathlib import Path
import re
import sys

source_path = Path(sys.argv[1])
source = source_path.read_text(encoding="utf-8")

marker = "MORPHE_BOOST_SUBREDDIT_BOTTOM_NAV_SELECTION_ISSUE134_V1"
if marker not in source:
    raise SystemExit(f"FAIL=ISSUE134_MARKER_MISSING marker={marker}")

match = re.search(
    r"private static int selectedItemIdForActivity\(.*?\n    }\n\n    private static void setCheckedItem",
    source,
    flags=re.S,
)
if match is None:
    raise SystemExit("FAIL=SELECTED_ITEM_RESOLVER_NOT_FOUND")

resolver = match.group(0)
required = (
    "SUBREDDIT_ACTIVITY.equals(activityName)",
    '"item_subs"',
    "SUBREDDIT_SELECTION_MARKER",
)
for token in required:
    if token not in resolver:
        raise SystemExit(f"FAIL=ISSUE134_RESOLVER_TOKEN_MISSING token={token}")

subreddit_block = re.search(
    r"if \(SUBREDDIT_ACTIVITY\.equals\(activityName\)\) \{.*?return resourceId\(.*?\"item_subs\".*?\);.*?\n        }",
    resolver,
    flags=re.S,
)
if subreddit_block is None:
    raise SystemExit("FAIL=SUBREDDIT_NOT_MAPPED_DIRECTLY_TO_SUBSCRIPTIONS")

issue117 = re.search(
    r"private static boolean preserveSourceSelectionAfterRoute\(.*?\n    }\n\n    private static String attachSubredditNavigationListeners",
    source,
    flags=re.S,
)
if issue117 is None:
    raise SystemExit("FAIL=ISSUE117_SOURCE_SELECTION_GUARD_NOT_FOUND")

issue117_body = issue117.group(0)
for token in (
    "SOURCE_SELECTION_RETAINED_MARKER",
    "selectedItemIdForActivity",
    "return false;",
):
    if token not in issue117_body:
        raise SystemExit(f"FAIL=ISSUE117_GUARD_REGRESSED token={token}")

print("PASS=ISSUE134_SUBREDDIT_ACTIVITY_MAPS_TO_SUBSCRIPTIONS")
print("PASS=ISSUE117_SOURCE_SELECTION_GUARD_RETAINED")
print(f"MARKER={marker}")
PY

echo "RESULT=MORPHE_ISSUE134_SOURCE_CONTRACT_PASS"
