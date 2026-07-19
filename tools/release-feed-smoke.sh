#!/usr/bin/env bash
set -euo pipefail

VERSION="$(python3 - <<'PY'
import json
from pathlib import Path
print(json.loads(Path("patches-list.json").read_text())["version"])
PY
)"

echo "VERSION=$VERSION"

./tools/check-project-contracts.sh

./tools/check-patches-list-feed.sh --write "$VERSION"
git diff --exit-code -- patches-list.json

./gradlew :patches:buildAndroid --no-daemon

MPP="$(tools/boost-resolve-mpp.sh --version "$VERSION")"
echo "MPP=$MPP"

test -n "$MPP"
test -f "$MPP"

tools/check-mpp-release-asset.sh "$MPP"
