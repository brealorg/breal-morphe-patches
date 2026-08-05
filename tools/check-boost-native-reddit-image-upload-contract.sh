#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
upload = root / 'extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/upload'
settings_dir = root / 'extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings'
patch_dir = root / 'patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/upload'

settings = (upload / 'ExternalImageUploadSettings.java').read_text(encoding='utf-8')
factory = (upload / 'ExternalImageUploadFactory.java').read_text(encoding='utf-8')
imgbb = (upload / 'ImgBbUploader.java').read_text(encoding='utf-8')
material = (settings_dir / 'MorpheSettingsV5ImageHostingFragment.java').read_text(encoding='utf-8')
comment = (root / 'extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/giphy/InlineGiphyCommentPreview.java').read_text(encoding='utf-8')
editor = (upload / 'EditorImagePreview.java').read_text(encoding='utf-8')
fingerprints = (patch_dir / 'Fingerprints.kt').read_text(encoding='utf-8')
patch = (patch_dir / 'FixNativeImageUploadPatch.kt').read_text(encoding='utf-8')

assert not (upload / 'ProgressRequestBody.java').exists()

for token in (
    'PROVIDER_REDDIT = "reddit"',
    'PROVIDER_IMGBB = "imgbb"',
    'PROVIDER_IMGUR = "imgur_free"',
    'return PROVIDER_IMGUR;',
    'PREF_EXTERNAL_IMAGE_HOST,\n                PROVIDER_IMGUR',
    'TextUtils.equals(stored, normalized)',
    'Comments and text posts: Imgur — default',
    'Comments and text posts: ImgBB — configured',
):
    assert token in settings, token

normalize = re.search(
    r'public static String normalizeProvider\(String value\) \{(.*?)\n    \}',
    settings,
    re.S,
)
assert normalize
assert 'PROVIDER_IMGBB.equals(normalized)' in normalize.group(1)
assert 'PROVIDER_IMGUR.equals(normalized)' in normalize.group(1)
assert 'PROVIDER_REDDIT.equals(normalized)' not in normalize.group(1)

for token in (
    '"Imgur"',
    '"Default for comments and text posts."',
    'ExternalImageUploadSettings.PROVIDER_IMGUR',
    '"ImgBB"',
    'ExternalImageUploadSettings.PROVIDER_IMGBB',
    '"Comments and text posts"',
    '"Image posts and galleries"',
    '"Reddit native"',
    '"Automatic fallback"',
):
    assert token in material, token
assert 'ExternalImageUploadSettings.PROVIDER_REDDIT' not in material
assert material.index('ExternalImageUploadSettings.PROVIDER_IMGUR') < material.index('ExternalImageUploadSettings.PROVIDER_IMGBB')

for token in ('"imgbb"', '"ee.a"', '"fe.b"', 'new ImgBbUploader()'):
    assert token in factory, token

for token in (
    'MORPHE_BOOST_IMGBB_FRAMEWORK_MULTIPART_ISSUE66_V2',
    'HttpURLConnection',
    'setRequestMethod("POST")',
    'multipart/form-data; boundary=',
    'appendQueryParameter("key", apiKey)',
    'data.optString("url", "")',
    'listener.c(exception, message)',
):
    assert token in imgbb, token
for forbidden in ('okhttp3.', 'okio.', 'ProgressRequestBody', 'BufferedSink'):
    assert forbidden not in imgbb, forbidden

for token in (
    r'i\\.imgur\\.com',
    r'i\\.ibb\\.co',
    r'image\\.ibb\\.co',
    r'i\\.redd\\.it',
):
    assert token in comment, token
assert 'reddit-uploaded-media' not in comment
assert 'MORPHE_BOOST_REDDIT_UPLOADED_MEDIA_INLINE_PREVIEW_ISSUE66_V1' not in comment

for token in (r'i\\.ibb\\.co', r'i\\.imgur\\.com', r'i\\.redd\\.it'):
    assert token in editor, token
assert 'reddit-uploaded-media' not in editor

for token in (
    'name = "w"',
    'name = "S"',
    'name = "T"',
    'name = "f0"',
    'editorImagePreviewFormattingBarSetEditTextFingerprint',
    'mediaUploaderFactoryFingerprint',
):
    assert token in fingerprints, token

for token in (
    'getEditorProvider()Ljava/lang/String;',
    'nativeRedditUploaderSubmitFingerprint',
    'nativeRedditUploaderSubmitMultipleFingerprint',
    'const-string v0, "$REDDIT_UPLOAD_PROVIDER"',
    '$UPLOAD_FACTORY_DESCRIPTOR->create',
    'MORPHE_BOOST_IMAGE_HOST_POLICY_ISSUE66_V3',
    'EditorImagePreview;->bind(Landroid/widget/EditText;)V',
):
    assert token in patch, token

assert re.search(
    r'arrayOf\(\s*nativeRedditUploaderSubmitFingerprint,\s*'
    r'nativeRedditUploaderSubmitMultipleFingerprint,\s*\)\.forEach',
    patch,
    re.S,
)

print('PASS=IMAGE_POSTS_AND_GALLERIES_FORCE_REDDIT_NATIVE')
print('PASS=COMMENT_AND_TEXT_IMAGES_DEFAULT_TO_IMGUR')
print('PASS=TEMPORARY_REDDIT_EDITOR_SELECTION_MIGRATES_TO_IMGUR')
print('PASS=IMGBB_REMAINS_MANUAL_ALTERNATIVE')
print('PASS=NO_AUTOMATIC_PROVIDER_FALLBACK')
print('PASS=RAW_REDDIT_S3_PREVIEW_REMOVED')
print('PASS=IMGBB_PUBLISHED_COMMENT_PREVIEW_SUPPORTED')
print('PASS=IMGBB_HTTPURLCONNECTION_TRANSPORT_RETAINED')
print('MARKER=MORPHE_BOOST_IMAGE_HOST_POLICY_ISSUE66_V3')
print('RESULT=MORPHE_ISSUE66_FINAL_PROVIDER_POLICY_CONTRACT_PASS')
PY
