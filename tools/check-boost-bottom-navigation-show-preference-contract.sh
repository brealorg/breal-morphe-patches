#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

test -f "$SOURCE"

rg -q -F \
  'MORPHE_BOOST_BOTTOM_NAV_SHOW_PREFERENCE_STABILITY_GUARD_ISSUE97_V1' \
  "$SOURCE"

GUARD_BLOCK="$(
  sed -n \
    '/private static void attachHomeStabilityGuard(/,/private static void repairHomeCanonicalState(/p' \
    "$SOURCE"
)"

REPAIR_BLOCK="$(
  sed -n \
    '/private static void repairHomeCanonicalState(/,/private static boolean canonicalSelectionMatches(/p' \
    "$SOURCE"
)"

test -n "$GUARD_BLOCK"
test -n "$REPAIR_BLOCK"

rg -q -F 'boolean navigationStateMismatch =' <<< "$GUARD_BLOCK"
rg -q -F 'boolean navigationMismatch =' <<< "$GUARD_BLOCK"
rg -q -U \
  '(?s)navigationStateMismatch\s*&&\s*isBottomNavigationPreferenceEnabled\(\s*activity\s*\)' \
  <<< "$GUARD_BLOCK"

rg -q -F 'boolean navigationRepairAllowed =' <<< "$REPAIR_BLOCK"
rg -q -U \
  '(?s)navigationMismatch\s*&&\s*isBottomNavigationPreferenceEnabled\(\s*activity\s*\)' \
  <<< "$REPAIR_BLOCK"
rg -q -F 'if (navigationRepairAllowed) {' <<< "$REPAIR_BLOCK"
rg -q -F 'navigation.setVisibility(View.VISIBLE);' <<< "$REPAIR_BLOCK"

if rg -q -U \
  '(?s)if \(navigationMismatch\) \{.*?navigation\.setVisibility\(View\.VISIBLE\);' \
  <<< "$REPAIR_BLOCK"; then
  echo 'BOTTOM_NAV_UNGATED_VISIBILITY_REPAIR=FAIL'
  exit 1
fi

echo 'BOTTOM_NAV_SHOW_PREFERENCE_GUARD_CALLBACK=PASS'
echo 'BOTTOM_NAV_SHOW_PREFERENCE_REPAIR_DEFENSE=PASS'
echo 'BOTTOM_NAV_UNGATED_VISIBILITY_REPAIR=ABSENT'
echo 'RESULT=MORPHE_BOTTOM_NAV_SHOW_PREFERENCE_CONTRACT_OK'
