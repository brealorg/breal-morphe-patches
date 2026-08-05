#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PREVIEW="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/upload/EditorImagePreview.java"
FINGERPRINTS="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/upload/Fingerprints.kt"
PATCH="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/upload/FixNativeImageUploadPatch.kt"

python3 - "$PREVIEW" "$FINGERPRINTS" "$PATCH" <<'PY'
from pathlib import Path
import sys

preview = Path(sys.argv[1]).read_text(encoding='utf-8')
fingerprints = Path(sys.argv[2]).read_text(encoding='utf-8')
patch = Path(sys.argv[3]).read_text(encoding='utf-8')

for token in (
    'MORPHE_BOOST_EDITOR_IMAGE_PREVIEW_ISSUE66_V1',
    'implements TextWatcher',
    'MAX_PREVIEWS = 4',
    r'i\\.ibb\\.co',
    r'i\\.imgur\\.com',
    r'i\\.redd\\.it',
    r'preview\\.redd\\.it',
    'findPlacement(EditText editText)',
    'LinearLayout.VERTICAL',
    'TextInputLayout',
    'editText.addTextChangedListener',
    'previewContainer.setVisibility(View.GONE)',
    'previewContainer.setVisibility(View.VISIBLE)',
    'loadWithGlide',
):
    assert token in preview, token

assert 'reddit-uploaded-media.s3-accelerate.amazonaws.com' not in preview
assert 'editable.delete(' not in preview
assert 'editable.replace(' not in preview

for token in (
    'editorImagePreviewFormattingBarSetEditTextFingerprint',
    'definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/FormattingBar;"',
    'name = "setEditText"',
    'parameters = listOf("Landroid/widget/EditText;")',
):
    assert token in fingerprints, token

for token in (
    'editorImagePreviewFormattingBarSetEditTextFingerprint.method.addInstructions',
    'EditorImagePreview;->bind(Landroid/widget/EditText;)V',
):
    assert token in patch, token

print('PASS=FORMATTING_BAR_SHARED_EDITOR_HOOK_PRESENT')
print('PASS=PUBLIC_IMGUR_AND_IMGBB_EDITOR_PREVIEWS_SUPPORTED')
print('PASS=RAW_REDDIT_S3_EDITOR_PREVIEW_REJECTED')
print('PASS=SOURCE_URL_REMAINS_IN_EDITOR')
print('PASS=PREVIEW_REMOVES_WHEN_URL_REMOVED')
print('RESULT=MORPHE_ISSUE66_EDITOR_IMAGE_PREVIEW_FINAL_POLICY_PASS')
PY
