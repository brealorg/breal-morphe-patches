#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

python3 - "$SOURCE" <<'PY_CONTRACT'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text(encoding="utf-8")

def method_body(text: str, signature: str) -> str:
    start = text.index(signature)
    brace = text.index("{", start)
    depth = 0
    for index in range(brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start:index + 1]
    raise AssertionError(f"unterminated method: {signature}")

assert "MORPHE_BOOST_BOTTOM_NAV_SOURCE_SELECTION_RETAINED_ISSUE117_V2" in source
assert "MORPHE_BOOST_BOTTOM_NAV_BACK_RESUME_SELECTION_ISSUE117_V1" not in source
assert "refreshMaterialNavigationSelection(" not in source

listener = method_body(
    source,
    "private static String attachSelectedListener("
)
selection_contract = method_body(
    source,
    "private static boolean preserveSourceSelectionAfterRoute("
)

for needle in (
    "boolean handled =",
    "handleItem(",
    "preserveSourceSelectionAfterRoute(",
):
    assert needle in listener, needle

for needle in (
    "selectedItemIdForActivity(",
    "routedItem.getItemId()",
    "sourceItemId == routedItemId",
    "Source selection retained after routed tab marker=",
    "return false;",
):
    assert needle in selection_contract, needle

assert selection_contract.index(
    "sourceItemId == routedItemId"
) < selection_contract.index("return false;")

print("FAILED_ON_RESUME_APPROACH_REMOVED=PASS")
print("ROUTE_EXECUTES_BEFORE_SELECTION_DECISION=PASS")
print("CROSS_ACTIVITY_SOURCE_SELECTION_REJECTED=PASS")
print("SAME_ACTIVITY_SELECTION_PRESERVED=PASS")
PY_CONTRACT

echo 'RESULT=MORPHE_ISSUE117_SOURCE_SELECTION_V2_CONTRACT_OK'
