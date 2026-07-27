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

TAG="morphe-patches-${VERSION##*.}"
echo "TAG=$TAG"

GATE_MODE="FULL_RELEASE"
GATE_REASON="BASE_SHA_UNAVAILABLE_FAIL_CLOSED"
BASE_SHA="${RELEASE_FEED_BASE_SHA:-}"

if test -n "$BASE_SHA"; then
  if [[ "$BASE_SHA" =~ ^0+$ ]]; then
    GATE_REASON="ZERO_BASE_SHA_FAIL_CLOSED"
  else
    [[ "$BASE_SHA" =~ ^[0-9a-f]{40}$ ]]

    CLASSIFICATION="$(
      python3 scripts/classify-release-feed-change.py \
        --base "$BASE_SHA" \
        --head HEAD
    )"
    printf '%s\n' "$CLASSIFICATION"

    GATE_MODE="$(
      printf '%s\n' "$CLASSIFICATION" |
        awk -F= '$1 == "RELEASE_FEED_GATE_MODE" { print $2 }'
    )"
    test "$GATE_MODE" = "CODE_ONLY" || test "$GATE_MODE" = "FULL_RELEASE"
    GATE_REASON="CLASSIFIED_FROM_BASE_SHA"
  fi
fi

echo "RELEASE_FEED_GATE_MODE=$GATE_MODE"
echo "RELEASE_FEED_GATE_REASON=$GATE_REASON"

RELEASE_GATE_ARGS=(
  scripts/release-gate.py
  --version "$VERSION"
  --tag "$TAG"
  --mpp "$MPP"
)

if test "$GATE_MODE" = "CODE_ONLY"; then
  RELEASE_GATE_ARGS+=(--skip-readme-sha)
fi

python3 "${RELEASE_GATE_ARGS[@]}"

tools/check-mpp-release-asset.sh "$MPP"
