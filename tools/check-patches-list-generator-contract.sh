#!/usr/bin/env bash

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GENERATOR="$ROOT/patches/src/main/kotlin/util/PatchListGenerator.kt"
GRADLE="$ROOT/patches/build.gradle.kts"
FEED="$ROOT/tools/check-patches-list-feed.sh"
FAIL=0

fail() {
    printf 'FAIL=%s\n' "$*"
    FAIL=1
}

printf 'CONTRACT=PATCHES_LIST_GENERATOR_V1\n'

grep -Fq 'MORPHE_PATCH_LIST_GENERATOR_EXACT_MPP_V1' "$GENERATOR" ||
    fail "GENERATOR_EXACT_MPP_MARKER_MISSING"
grep -Eq 'listFiles|\.first\(\)' "$GENERATOR" &&
    fail "GENERATOR_SELECTS_BUNDLE_BY_DIRECTORY_ORDER"
grep -Fq 'archiveFile' "$GRADLE" ||
    fail "GRADLE_TASK_DOES_NOT_PASS_EXACT_BUNDLE"
grep -Fq 'MORPHE_PATCHES_LIST_NO_SILENT_REMOVAL_V1' "$FEED" ||
    fail "FEED_REMOVED_PATCH_GUARD_MISSING"
grep -Fq 'MORPHE_ALLOW_REMOVED_PATCHES' "$FEED" ||
    fail "FEED_REMOVED_PATCH_ALLOWLIST_MISSING"

if [ "$FAIL" -eq 0 ]; then
    printf 'RESULT=PATCHES_LIST_GENERATOR_CONTRACT_PASS\n'
else
    printf 'RESULT=PATCHES_LIST_GENERATOR_CONTRACT_FAIL\n'
fi

exit "$FAIL"
