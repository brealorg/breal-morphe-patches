#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation.java'
FAB_PATCH='patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/homefab/FixFabNestedScrollFallbackPatch.kt'

test -f "$SOURCE"
test -f "$FAB_PATCH"

rg -q -F \
  'MORPHE_BOOST_NATIVE_BOTTOM_NAV_HIDE_PREFERENCE_ACTIVE_PATH_ISSUE97_V7' \
  "$SOURCE"

rg -q -U \
  '(?s)View navigation =\s*configureNativeCanonicalNavigation\(.*?\);\s*lockOwnedNavigationHost\(navigation\);\s*if \(appliedVisible\)' \
  "$SOURCE"

HOST_BLOCK="$(
  sed -n \
    '/private static void lockOwnedNavigationHost(/,/private static void scheduleNativeProfileLongPress(/p' \
    "$SOURCE"
)"

test -n "$HOST_BLOCK"
rg -q -F 'native bottom-navigation hide preference applied marker=' \
  <<< "$HOST_BLOCK"
rg -q -F '"id.b"' <<< "$HOST_BLOCK"
rg -q -F '"v0"' <<< "$HOST_BLOCK"
rg -q -F '"z"' <<< "$HOST_BLOCK"
rg -q -F 'boolean hideOnScroll = (Boolean) rawPreference;' \
  <<< "$HOST_BLOCK"
rg -q -F \
  'com.google.android.material.behavior.HideBottomViewOnScrollBehavior' \
  <<< "$HOST_BLOCK"
rg -q -F 'new Object[]{behaviorAfter}' <<< "$HOST_BLOCK"
rg -q -F 'new Object[]{null}' <<< "$HOST_BLOCK"
rg -q -F 'navigation.setTranslationY(0.0f);' <<< "$HOST_BLOCK"
rg -q -U \
  '(?s)if \(hideOnScroll\).*?new Object\[]\{behaviorAfter\}.*?else \{.*?new Object\[]\{null\}.*?navigation\.setTranslationY\(0\.0f\)' \
  <<< "$HOST_BLOCK"

if rg -q -F 'Lid/b;->z()Z' "$FAB_PATCH"; then
  echo 'FAB_INDEPENDENT_FROM_BOTTOM_NAV_PREFERENCE=FAIL'
  exit 1
fi

rg -q -F '${target.nestedScrollMethodName}' "$FAB_PATCH"
rg -q -F 'ScrollAwareTarget(' "$FAB_PATCH"

echo 'BOTTOM_NAV_PREFERENCE_ACTIVE_PATH=PASS'
echo 'BOTTOM_NAV_NATIVE_PREFERENCE_READ=PASS'
echo 'BOTTOM_NAV_HIDE_BEHAVIOR_ENABLED_WHEN_ON=PASS'
echo 'BOTTOM_NAV_HIDE_BEHAVIOR_REMOVED_WHEN_OFF=PASS'
echo 'BOTTOM_NAV_TRANSLATION_RESTORED_WHEN_OFF=PASS'
echo 'FAB_INDEPENDENT_FROM_BOTTOM_NAV_PREFERENCE=PASS'
echo 'UNIFORM_FAB_PRE_SCROLL_PRESERVED=PASS'
echo 'RESULT=MORPHE_BOTTOM_NAV_HIDE_PREFERENCE_CONTRACT_OK'
