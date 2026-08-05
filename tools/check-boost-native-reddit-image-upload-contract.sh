#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/upload"
FINGERPRINTS="$DIR/Fingerprints.kt"
PATCH="$DIR/FixNativeImageUploadPatch.kt"
DUPLICATE_PATCH="$DIR/UseNativeRedditImageUploadsPatch.kt"
BUILDER="$ROOT/tools/build-boost-candidate.sh"

test -f "$FINGERPRINTS"
test -f "$PATCH"
test ! -e "$DUPLICATE_PATCH"

python3 - "$FINGERPRINTS" "$PATCH" "$BUILDER" <<'PY'
from pathlib import Path
import re
import sys

fingerprints_path = Path(sys.argv[1])
patch_path = Path(sys.argv[2])
builder_path = Path(sys.argv[3])

fingerprints = fingerprints_path.read_text(encoding="utf-8")
patch = patch_path.read_text(encoding="utf-8")
builder = (
    builder_path.read_text(encoding="utf-8")
    if builder_path.is_file()
    else ""
)

assert fingerprints.count(
    "internal val submitGallerySubmissionKindFingerprint"
) == 1
assert (
    'classDef.type == '
    '"Lcom/rubenmayayo/reddit/ui/submit/v2/SubmitGalleryFragment;"'
    in fingerprints
)
assert 'method.name == "R1"' in fingerprints

expected_remote_config = (
    (
        "nativeRedditUploaderFormatFingerprint",
        "w",
        "Ljava/lang/String;",
        "uploader_format",
    ),
    (
        "nativeRedditUploaderSubmitFingerprint",
        "S",
        "Ljava/lang/String;",
        "uploader_submit",
    ),
    (
        "nativeRedditUploaderSubmitMultipleFingerprint",
        "T",
        "Ljava/lang/String;",
        "uploader_submit_multiple",
    ),
    (
        "nativeRedditSubmitAsImageKindFingerprint",
        "f0",
        "Z",
        "submit_reddit_as_image_kind",
    ),
)

assert fingerprints.count(
    'private const val BOOST_REMOTE_CONFIG_CLASS = "Lsb/a;"'
) == 1

for variable, method, return_type, key in expected_remote_config:
    pattern = re.compile(
        rf"internal val {re.escape(variable)} = Fingerprint\(\s*"
        rf"definingClass = BOOST_REMOTE_CONFIG_CLASS,\s*"
        rf'name = "{re.escape(method)}",\s*'
        rf'returnType = "{re.escape(return_type)}",\s*'
        rf"parameters = emptyList\(\),\s*"
        rf'strings = listOf\("{re.escape(key)}"\),\s*'
        rf"\)",
        re.S,
    )
    assert pattern.search(fingerprints), variable
    assert fingerprints.count(f'"{key}"') == 1, key

required_patch_tokens = (
    'name = "Fix Boost native image upload"',
    'private const val REDDIT_UPLOAD_PROVIDER = "reddit"',
    "MORPHE_BOOST_NATIVE_REDDIT_IMAGE_UPLOAD_ISSUE66_V2",
    "MORPHE_ISSUE17_NATIVE_IMAGE_UPLOAD_V1",
    "compatibleWith(*BoostCompatible)",
    "nativeRedditUploaderFormatFingerprint",
    "nativeRedditUploaderSubmitFingerprint",
    "nativeRedditUploaderSubmitMultipleFingerprint",
    "nativeRedditSubmitAsImageKindFingerprint",
    "submitGallerySubmissionKindFingerprint",
    'const-string v0, "$REDDIT_UPLOAD_PROVIDER"',
    "const/4 v0, 0x1",
    "return-object v0",
    "return v0",
    'getReference<MethodReference>()?.toString() == "Lsb/a;->f0()Z"',
    "replaceInstruction(",
    "default = false",
)

for token in required_patch_tokens:
    assert token in patch, token

assert re.search(
    r"arrayOf\(\s*"
    r"nativeRedditUploaderFormatFingerprint,\s*"
    r"nativeRedditUploaderSubmitFingerprint,\s*"
    r"nativeRedditUploaderSubmitMultipleFingerprint,\s*"
    r"\)\.forEach",
    patch,
    re.S,
)

assert patch.count("val fixNativeImageUploadPatch = bytecodePatch(") == 1
assert "val useNativeRedditImageUploadsPatch" not in patch
assert patch.count('const-string v0, "$REDDIT_UPLOAD_PROVIDER"') == 1
assert patch.count("const/4 v0, 0x1") >= 1

assert '"Reddit\'s bundled native uploader instead of Imgur while " +' in patch
assert '"GIF and video upload behavior is unchanged."' in patch

for forbidden in (
    "gifv_uploader_submit",
    "ImgurFreeUploader",
    "ImgurPaidUploader",
    "VgyImageUploader",
    "api.imgur.com",
    "imgbb",
    "imagechest",
    "postimages",
    "catbox",
    "OkHttpClient",
    "Retrofit",
):
    assert forbidden not in patch, forbidden

if builder:
    assert builder.count('"Fix Boost native image upload"') >= 1

print("PASS=ORIGINAL_ISSUE17_GALLERY_SAFEGUARD_RETAINED")
print("PASS=EDITOR_SINGLE_AND_GALLERY_PROVIDERS_FORCE_REDDIT")
print("PASS=NATIVE_IMAGE_SUBMISSION_KIND_ENABLED_GLOBALLY")
print("PASS=DUPLICATE_PATCH_REMOVED")
print("PASS=GIF_VIDEO_PROVIDER_UNCHANGED")
print("PASS=NO_EXTERNAL_UPLOAD_IMPLEMENTATION_ADDED")
print(
    "MARKER=MORPHE_BOOST_NATIVE_REDDIT_IMAGE_UPLOAD_ISSUE66_V2"
)
PY

echo "RESULT=MORPHE_ISSUE66_NATIVE_REDDIT_IMAGE_UPLOAD_V2_CONTRACT_PASS"
