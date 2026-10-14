#!/usr/bin/env bash
set -euo pipefail

# Issue #97: the bar must follow "Show bottom navigation" and Boost's own
# visibility decisions (p3(false) for the swipe post viewer). A per-frame
# stability guard used to force it VISIBLE; it never logged a repair on device
# and was removed in favor of the resume/p3 paths. Keep forced repairs out.

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'

test -f "$SOURCE"

if rg -q -F 'OnPreDrawListener' "$SOURCE"; then
  echo 'BOTTOM_NAV_PER_FRAME_REPAIR=FAIL'
  exit 1
fi

if rg -q 'attachHomeStabilityGuard|repairHomeCanonicalState' "$SOURCE"; then
  echo 'BOTTOM_NAV_STABILITY_GUARD_REMOVED=FAIL'
  exit 1
fi

# Every visible state is gated by the preference.
rg -q -U \
  '(?s)boolean appliedVisible =\s*visible\s*&& isBottomNavigationPreferenceEnabled\(\s*activity\s*\);' \
  "$SOURCE"
rg -q -U \
  '(?s)requestedVisible =\s*requestedVisible && preferenceEnabled;' \
  "$SOURCE"

echo 'BOTTOM_NAV_PER_FRAME_REPAIR=ABSENT'
echo 'BOTTOM_NAV_STABILITY_GUARD_REMOVED=PASS'
echo 'BOTTOM_NAV_VISIBILITY_PREFERENCE_GATED=PASS'
echo 'RESULT=MORPHE_BOTTOM_NAV_SHOW_PREFERENCE_CONTRACT_OK'
