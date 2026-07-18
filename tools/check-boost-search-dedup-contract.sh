#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRODUCTION="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/search/SearchInsertedRowsTracker.java"
SEARCH_ROWS="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/search/SearchExploreRows.java"
TEST="$ROOT/tools/tests/boost-search/SearchInsertedRowsTrackerContractTest.java"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/morphe-issue61-contract.XXXXXX")"
trap 'rm -rf -- "$TMP"' EXIT

test -f "$PRODUCTION"
test -f "$SEARCH_ROWS"
test -f "$TEST"

grep -F 'SearchInsertedRowsTracker<Activity>' "$SEARCH_ROWS" >/dev/null
grep -F 'INSERTED_ROWS.record(activity, inserted)' "$SEARCH_ROWS" >/dev/null
grep -F 'INSERTED_ROWS.remove(activity, rows)' "$SEARCH_ROWS" >/dev/null
grep -F 'MORPHE_SEARCH_EXPLORE_ISSUE95_STABLE_ANCHOR_V1' \
    "$SEARCH_ROWS" >/dev/null
grep -F 'INSERTED_ROWS.insertionIndex(activity, rows)' \
    "$SEARCH_ROWS" >/dev/null
grep -F 'rows.addAll(insertionIndex, inserted)' \
    "$SEARCH_ROWS" >/dev/null
if grep -F 'rows.addAll(inserted)' "$SEARCH_ROWS" >/dev/null; then
    echo 'FAIL: Search refresh still appends owned rows without anchor' >&2
    exit 1
fi

if grep -F 'INSERTED_BY_ACTIVITY' "$SEARCH_ROWS" >/dev/null; then
    echo 'FAIL: legacy activity map remains in SearchExploreRows' >&2
    exit 1
fi

JAVAC_BIN="${JAVAC:-}"
if [ -z "$JAVAC_BIN" ] && command -v javac >/dev/null 2>&1; then
    JAVAC_BIN="$(command -v javac)"
fi
if [ -z "$JAVAC_BIN" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC_BIN="$JAVA_HOME/bin/javac"
fi
if [ -z "$JAVAC_BIN" ]; then
    echo 'FAIL: javac not found; run with a JDK 17 environment' >&2
    exit 1
fi

mkdir -p "$TMP/classes"
"$JAVAC_BIN" -Werror -Xlint:all -d "$TMP/classes" "$PRODUCTION" "$TEST"
java -ea -cp "$TMP/classes" \
    app.morphe.extension.boostforreddit.search.SearchInsertedRowsTrackerContractTest
