#!/usr/bin/env bash
set -euo pipefail

SOURCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/giphy/InlineGiphyCommentPreview.java'
PREFERENCE='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/giphy/PreviewSizePreference.java'
SETTINGS='patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/misc/settings/BoostMorpheSettingsSkeletonPatch.kt'

test -f "$SOURCE"
test -f "$PREFERENCE"
test -f "$SETTINGS"

rg -q -F \
  'MORPHE_BOOST_INLINE_MEDIA_ADAPTIVE_SIZE_ISSUE70_V4' \
  "$SOURCE"

rg -q -F \
  'DEFAULT_INLINE_MEDIA_PREVIEW_SIZE = PREVIEW_SIZE_BALANCED' \
  "$SOURCE"

rg -q -F 'widthFraction = 0.65f;' "$SOURCE"
rg -q -F 'widthFraction = 0.85f;' "$SOURCE"
rg -q -F 'widthFraction = 1.00f;' "$SOURCE"

rg -q -F \
  'resolvePreviewWidthPx(context, itemView, previewSize)' \
  "$SOURCE"

rg -q -F \
  'resolveAvailablePreviewWidthPx(' \
  "$SOURCE"

rg -q -U \
  'new LinearLayout\.LayoutParams\(\s*previewWidthPx,\s*ViewGroup\.LayoutParams\.WRAP_CONTENT\s*\)' \
  "$SOURCE"

rg -q -F \
  'imageView.setMaxWidth(previewWidthPx);' \
  "$SOURCE"

rg -q -F \
  'imageView.setMaxHeight(maxPreviewHeightPx);' \
  "$SOURCE"

rg -q -F \
  'ImageView.ScaleType.FIT_CENTER' \
  "$SOURCE"

rg -q -F \
  'imageView.setAdjustViewBounds(true);' \
  "$SOURCE"

rg -q -F \
  'targetWidthPx=' \
  "$SOURCE"

rg -q -F \
  '": measured mode="' \
  "$SOURCE"

rg -q -F \
  'Math.round(windowHeightDp * 0.30f)' \
  "$SOURCE"

rg -q -F \
  'Math.round(windowHeightDp * 0.50f)' \
  "$SOURCE"

rg -q -F \
  'Math.round(windowHeightDp * 0.70f)' \
  "$SOURCE"

if rg -q -F 'dp(context, 170)' "$SOURCE"; then
  echo 'FAIL: fixed 170 dp preview height remains' >&2
  exit 1
fi

if rg -q -F 'imageWidthForAlignment(' "$SOURCE"; then
  echo 'FAIL: obsolete alignment-based width remains' >&2
  exit 1
fi

if rg -q -F 'invokeGlideOverride(' "$SOURCE"; then
  echo 'FAIL: dead Glide override reflection remains' >&2
  exit 1
fi

if rg -q -F 'no Glide override method found' "$SOURCE"; then
  echo 'FAIL: obsolete Glide warning remains' >&2
  exit 1
fi

INLINE_MEDIA_PREVIEW_SIZE_KEY_COUNT="$(
  rg -c -F \
    'android:key="morphe_boost_inline_media_preview_size"' \
    "$SETTINGS" ||
    true
)"

if test "$INLINE_MEDIA_PREVIEW_SIZE_KEY_COUNT" -ne 1; then
  echo "FAIL: expected one canonical inline media preview size preference, found $INLINE_MEDIA_PREVIEW_SIZE_KEY_COUNT" >&2
  exit 1
fi

INLINE_MEDIA_BALANCED_DEFAULT_COUNT="$(
  rg -c -F \
    'android:defaultValue="balanced"' \
    "$SETTINGS" ||
    true
)"

if test "$INLINE_MEDIA_BALANCED_DEFAULT_COUNT" -ne 1; then
  echo "FAIL: expected one canonical balanced inline media default, found $INLINE_MEDIA_BALANCED_DEFAULT_COUNT" >&2
  exit 1
fi

rg -q -F '"Compact"' "$PREFERENCE"
rg -q -F '"Balanced"' "$PREFERENCE"
rg -q -F '"Large"' "$PREFERENCE"

echo 'FIXED_170_DP_REMOVED=PASS'
echo 'WIDTH_PRESETS_65_85_100=PASS'
echo 'HEIGHT_LIMITS_260_420_620=PASS'
echo 'ASPECT_RATIO_PRESERVATION=PASS'
echo 'DEAD_GLIDE_OVERRIDE_REMOVED=PASS'
echo 'SETTINGS_CONTRACT=PASS'
echo 'RESULT=MORPHE_ISSUE70_INLINE_MEDIA_SIZE_V4_CONTRACT_OK'
