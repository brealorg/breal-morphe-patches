#!/usr/bin/env bash

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FINGERPRINTS="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/homefab/Fingerprints.kt"
PATCH_SOURCE="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/homefab/FixFabNestedScrollFallbackPatch.kt"
FAIL=0

pass() {
  echo "$1=PASS"
}

fail() {
  echo "$1=FAIL"
  FAIL=1
}

check_fixed() {
  local label="$1"
  local needle="$2"
  local file="$3"

  if rg -q -F "$needle" "$file"; then
    pass "$label"
  else
    fail "$label"
  fi
}

check_fixed \
  STANDARD_FAB_BEHAVIOR_FINGERPRINT \
  'Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABBehavior;' \
  "$FINGERPRINTS"
check_fixed \
  FLOATING_ACTION_MENU_BEHAVIOR_FINGERPRINT \
  'Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMenuBehavior;' \
  "$FINGERPRINTS"
check_fixed \
  MINI_FAB_BEHAVIOR_FINGERPRINT \
  'Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMiniBehavior;' \
  "$FINGERPRINTS"

TARGET_COUNT="$(rg -c '^            ScrollAwareTarget\($' "$PATCH_SOURCE")"
if [ "$TARGET_COUNT" = 3 ]; then
  pass ALL_SCROLL_AWARE_TARGETS_REGISTERED
else
  echo "ALL_SCROLL_AWARE_TARGETS_REGISTERED=FAIL expected=3 actual=$TARGET_COUNT"
  FAIL=1
fi

check_fixed \
  STANDARD_NATIVE_SCROLL_METHOD \
  'fabNestedScrollFingerprint,' \
  "$PATCH_SOURCE"
check_fixed \
  MENU_NATIVE_SCROLL_METHOD \
  'fabMenuNestedScrollFingerprint,' \
  "$PATCH_SOURCE"
check_fixed \
  MINI_NATIVE_SCROLL_METHOD \
  'fabMiniNestedScrollFingerprint,' \
  "$PATCH_SOURCE"
check_fixed \
  NESTED_SCROLL_EDGE_FALLBACK \
  'move p5, p7' \
  "$PATCH_SOURCE"
check_fixed \
  COORDINATOR_PRE_SCROLL_OVERRIDE \
  'behaviorClass.methods.add(' \
  "$PATCH_SOURCE"
check_fixed \
  NATIVE_POLICY_REUSED \
  '${target.nestedScrollMethodName}' \
  "$PATCH_SOURCE"

if [ "$FAIL" -eq 0 ]; then
  echo 'RESULT=MORPHE_BOOST_FAB_UNIFORM_SCROLL_CONTRACT_OK'
else
  echo 'RESULT=MORPHE_BOOST_FAB_UNIFORM_SCROLL_CONTRACT_FAIL'
fi

exit "$FAIL"
