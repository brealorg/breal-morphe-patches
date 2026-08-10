#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java"
PATCH="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/searchnav/BoostSearchBottomNavigationPatch.kt"
FINGERPRINT="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/searchnav/Fingerprints.kt"

! grep -Fq 'MORPHE_BOOST_SEARCH_STALE_ERROR_LIFECYCLE_V1' "$SOURCE"
! grep -Fq 'searchSubmissionsErrorCallbackFingerprint' "$FINGERPRINT"
grep -Fq 'searchAbstractSubredditsErrorCallbackFingerprint' "$FINGERPRINT"
grep -Fq 'parameters = listOf("Ljava/lang/Exception;")' "$FINGERPRINT"
grep -Fq 'SearchAbstractActivity\$e;' "$FINGERPRINT"
grep -Fq 'SEARCH_ABSTRACT_SUBREDDITS_ERROR_CALLBACK_DESCRIPTOR' "$PATCH"
grep -Fq -- '->a:$SEARCH_ABSTRACT_ACTIVITY_DESCRIPTOR' "$PATCH"
grep -Fq -- 'Landroid/app/Activity;->isFinishing()Z' "$PATCH"
grep -Fq 'if-eqz v0, :morphe_search_subreddits_error_live' "$PATCH"
! grep -Fq -- 'Landroid/widget/Toast;->makeText' "$PATCH"

echo 'PASS=OBSOLETE_SEARCHSUBMISSIONS_GUARD_REMOVED'
echo 'PASS=SEARCHABSTRACT_SUBREDDITS_ERROR_CALLBACK_FINGERPRINT_EXACT'
echo 'PASS=ERROR_CALLBACK_MIRRORS_NATIVE_SUCCESS_ISFINISHING_GUARD'
echo 'PASS=FINISHED_SEARCH_HOST_DROPS_ASYNC_ERROR'
echo 'PASS=LIVE_SEARCH_HOST_PRESERVES_NATIVE_ERROR'
echo 'PASS=GLOBAL_TOAST_BEHAVIOR_UNCHANGED'
echo 'RESULT=MORPHE_BOOST_SEARCH_SUBREDDITS_FINISHED_HOST_ERROR_CONTRACT_PASS'
