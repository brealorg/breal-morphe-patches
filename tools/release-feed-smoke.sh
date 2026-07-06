#!/usr/bin/env bash
set -euo pipefail

VERSION="$(python3 - <<'PY'
import json
from pathlib import Path
print(json.loads(Path("patches-list.json").read_text())["version"])
PY
)"

echo "VERSION=$VERSION"

./tools/check-patches-list-feed.sh --write "$VERSION"
git diff --exit-code -- patches-list.json

./gradlew :patches:buildAndroid --no-daemon

MPP="$(find patches/build/libs -maxdepth 1 -name '*.mpp' ! -name '*sources*' ! -name '*javadoc*' | sort | tail -1)"
echo "MPP=$MPP"

test -n "$MPP"
test -f "$MPP"

unzip -l "$MPP" | tee /tmp/morphe-mpp-list.txt
grep -q 'classes.dex' /tmp/morphe-mpp-list.txt
grep -q 'extensions/boostforreddit.mpe' /tmp/morphe-mpp-list.txt
