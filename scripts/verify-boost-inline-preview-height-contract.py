#!/usr/bin/env python3
# Static contract for Boost issue #164 inline-preview height stability.

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / (
    "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    "boostforreddit/giphy/StableInlinePreviewHeight.java"
)
PATCH = ROOT / (
    "patches/src/main/kotlin/app/morphe/patches/reddit/customclients/"
    "boostforreddit/fix/giphy/InlineGiphyCommentPreviewPatch.kt"
)

helper = HELPER.read_text(encoding="utf-8")
patch = PATCH.read_text(encoding="utf-8")

required_helper_contracts = {
    "issue marker":
        "MORPHE_BOOST_INLINE_MEDIA_STABLE_RECYCLED_HEIGHT_ISSUE164_V1",
    "weak comment cache":
        "new WeakHashMap<>()",
    "existing preview bind delegation":
        "InlineGiphyCommentPreview.bind(",
    "synchronous cached-height restore":
        "restoreCachedGeometry(",
    "resolved drawable guard":
        "imageView.getDrawable() == null",
    "layout measurement listener":
        "addOnLayoutChangeListener(",
    "width-aware height scaling":
        "targetWidthPx / (float) geometry.widthPx",
    "adaptive maximum-height clamp":
        "imageView.getMaxHeight()",
    "comment-content invalidation":
        "geometry.contentSignature != contentSignature",
}

required_patch_contracts = {
    "stable helper descriptor":
        "StableInlinePreviewHeight;",
    "stable bind dispatch":
        "$STABLE_INLINE_PREVIEW_HEIGHT_DESCRIPTOR->bind",
    "original source cleanup":
        "$INLINE_GIPHY_EXTENSION_DESCRIPTOR->cleanCommentHtml",
    "original post-bind source policy":
        "$INLINE_GIPHY_EXTENSION_DESCRIPTOR->applySourceTextPolicyAfterBind",
    "original collapse synchronization":
        "$INLINE_GIPHY_EXTENSION_DESCRIPTOR->syncWithCommentState",
}

failures: list[str] = []

for name, marker in required_helper_contracts.items():
    if marker not in helper:
        failures.append(f"helper missing {name}: {marker}")

for name, marker in required_patch_contracts.items():
    if marker not in patch:
        failures.append(f"patch missing {name}: {marker}")

direct_old_bind = (
    "$INLINE_GIPHY_EXTENSION_DESCRIPTOR->bind"
    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"
)
if direct_old_bind in patch:
    failures.append("patch still dispatches directly to the unstable bind path")

if failures:
    for failure in failures:
        print(f"FAIL={failure}")
    raise SystemExit(1)

print("ISSUE=164")
print("SOURCE_CONTRACT=PASS")
print("CACHE_SCOPE=WEAK_COMMENT_MODEL")
print("HEIGHT_RESTORE=BEFORE_ASYNC_GLIDE_LAYOUT")
print("ADAPTIVE_SIZE_BEHAVIOR=PRESERVED")
print("RESULT=ISSUE164_INLINE_PREVIEW_HEIGHT_CONTRACT_PASS")
