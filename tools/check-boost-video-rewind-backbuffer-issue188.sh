#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATCH="$ROOT/patches/src/main/kotlin/app/morphe/patches/reddit/customclients/boostforreddit/fix/media/VideoBackBufferRewindPatch.kt"

fail() {
    printf 'FAIL=%s\n' "$1" >&2
    exit 1
}

[ -f "$PATCH" ] || fail "ISSUE188_PATCH_MISSING"

python3 - "$PATCH" <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
source = path.read_text()

checks = {
    "marker": r'MORPHE_BOOST_VIDEO_REWIND_BACKBUFFER_ISSUE188_V1',
    "patch_name": r'name\s*=\s*"Fix Boost video rewind back buffer"',
    "default_enabled": r'default\s*=\s*true',
    "boost_compatibility": r'compatibleWith\(\*BoostCompatible\)',
    "load_control_class": r'definingClass\s*=\s*"Ls3/c;"',
    "retain_getter": (
        r'retainBackBufferFromKeyframeFingerprint\s*=\s*Fingerprint\('
        r'(?s:.*?)name\s*=\s*"b"(?s:.*?)returnType\s*=\s*"Z"'
    ),
    "duration_getter": (
        r'backBufferDurationUsFingerprint\s*=\s*Fingerprint\('
        r'(?s:.*?)name\s*=\s*"c"(?s:.*?)returnType\s*=\s*"J"'
    ),
    "thirty_seconds_us": r'const-wide/32 v\$returnRegister,\s*0x1c9c380',
    "retain_true": r'const/4 v\$returnRegister,\s*0x1',
    "single_wide_return_guard": r'Opcode\.RETURN_WIDE',
    "single_bool_return_guard": r'Opcode\.RETURN',
}

for name, pattern in checks.items():
    if not re.search(pattern, source):
        raise SystemExit(f"FAIL={name.upper()}_CONTRACT_MISSING")

if source.count('definingClass = "Ls3/c;"') != 2:
    raise SystemExit("FAIL=EXPECTED_TWO_LOAD_CONTROL_FINGERPRINTS")

if source.count('"const-wide/32 v$returnRegister, 0x1c9c380"') != 1:
    raise SystemExit("FAIL=BACK_BUFFER_DURATION_NOT_EXACTLY_ONCE")

if source.count('"const/4 v$returnRegister, 0x1"') != 1:
    raise SystemExit("FAIL=RETAIN_TRUE_NOT_EXACTLY_ONCE")

for forbidden in (
    "CacheDataSink",
    "CacheDataSource",
    "OkHttp",
    "Interceptor",
    "Redgifs",
    "boost_expires",
):
    if forbidden in source:
        raise SystemExit(f"FAIL=FORBIDDEN_SCOPE_{forbidden}")

print("ISSUE188_PATCH_IDENTITY=PASS")
print("ISSUE188_LOADCONTROL_FINGERPRINTS=PASS")
print("ISSUE188_BACK_BUFFER_30_SECONDS=PASS")
print("ISSUE188_RETAIN_FROM_KEYFRAME=PASS")
print("ISSUE188_NETWORK_AND_CACHE_SCOPE=UNCHANGED")
print("RESULT=PASS")
PY
