#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PATCH='patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/api/SpoofClientPatch.kt'
SHARED='patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/misc/extension/SharedExtensionPatch.kt'
HTTP='extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/http/HttpUtils.java'

test -f "$PATCH"
test -f "$SHARED"
test -f "$HTTP"

test "$(rg -c -F \
    'import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch' \
    "$PATCH")" -eq 1

test "$(rg -c -F \
    'dependsOn(sharedExtensionPatch)' \
    "$PATCH")" -eq 1

test "$(rg -c -F \
    'const val JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/boostforreddit/http/HttpUtils;"' \
    "$PATCH")" -eq 1

test "$(rg -c -F \
    'invoke-static       { p0 }, $JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR->createUrl(Ljava/lang/String;)Ljava/net/URL;' \
    "$PATCH")" -eq 1

test "$(rg -c -F \
    'val sharedExtensionPatch = sharedExtensionPatch("boostforreddit", initHook)' \
    "$SHARED")" -eq 1

test "$(rg -c -F \
    'package app.morphe.extension.boostforreddit.http;' \
    "$HTTP")" -eq 1

test "$(rg -c -F \
    'public class HttpUtils {' \
    "$HTTP")" -eq 1

test "$(rg -c -F \
    'public static URL createUrl(String href) {' \
    "$HTTP")" -eq 1

PATCH_FILE="$PATCH" python3 <<'PY'
from pathlib import Path
import os

text = Path(os.environ["PATCH_FILE"]).read_text(encoding="utf-8")

dependency = text.index("    dependsOn(sharedExtensionPatch)\n")
compatibility = text.index("    compatibleWith(*BoostCompatible)\n")
execute = text.index("    execute {\n")
descriptor = text.index(
    'const val JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR = '
    '"Lapp/morphe/extension/boostforreddit/http/HttpUtils;"'
)
call = text.index(
    "invoke-static       { p0 }, "
    "$JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR"
    "->createUrl(Ljava/lang/String;)Ljava/net/URL;"
)

assert dependency < compatibility < execute
assert descriptor < execute < call

print("DEPENDENCY_PRECEDES_PATCH_EXECUTION=PASS")
print("HTTPUTILS_DESCRIPTOR_AND_CALL_MATCH=PASS")
PY

echo 'BOOST_SPOOF_CLIENT_SHARED_EXTENSION_IMPORT=PASS'
echo 'BOOST_SPOOF_CLIENT_SHARED_EXTENSION_DEPENDENCY=PASS'
echo 'BOOST_HTTPUTILS_CREATEURL_SOURCE_CONTRACT=PASS'
echo 'RESULT=MORPHE_BOOST_SPOOF_CLIENT_EXTENSION_CONTRACT_OK'
