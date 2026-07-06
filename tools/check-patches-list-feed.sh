#!/usr/bin/env bash
set -Eeuo pipefail

MODE="${1:---check}"
EXPECTED_VERSION="${2:-}"

if [[ "$MODE" != "--check" && "$MODE" != "--write" ]]; then
  echo "Usage: $0 [--check|--write] [expected-version]" >&2
  exit 2
fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if [[ -z "$EXPECTED_VERSION" ]]; then
  BUNDLE_VERSION="$(jq -r '.version // empty' patches-bundle.json)"
  if [[ -z "$BUNDLE_VERSION" || "$BUNDLE_VERSION" == "null" ]]; then
    echo "FAIL: could not read .version from patches-bundle.json" >&2
    exit 1
  fi
  EXPECTED_VERSION="${BUNDLE_VERSION#v}"
fi

ORIGINAL="$(mktemp)"
DIFF_OUT="$(mktemp)"
cp patches-list.json "$ORIGINAL"

cleanup() {
  if [[ "$MODE" == "--check" ]]; then
    cp "$ORIGINAL" patches-list.json
  fi
  rm -f "$ORIGINAL" "$DIFF_OUT"
}
trap cleanup EXIT

echo "===== generate patches-list.json ====="
./gradlew :patches:generatePatchesList >/tmp/morphe-check-patches-list-feed.gradle.log

echo "===== normalize compatiblePackages schema ====="
python3 tools/normalize-patches-list-compatible-packages.py patches-list.json

TMP="$(mktemp)"
jq --arg v "$EXPECTED_VERSION" '.version=$v' patches-list.json > "$TMP"
mv "$TMP" patches-list.json

echo "===== normalize Manager patches-list schema ====="
python3 tools/normalize-patches-list-manager-schema.py patches-list.json ${WRITE_VERSION:+--version "$WRITE_VERSION"}

echo "===== validate patches-list.json ====="
python3 - "$EXPECTED_VERSION" <<'PY'
import json
import sys
from pathlib import Path

expected = sys.argv[1]
data = json.loads(Path("patches-list.json").read_text())
patches = data.get("patches", [])

print("version=", data.get("version"))
print("patches=", len(patches) if isinstance(patches, list) else "<invalid>")

if data.get("version") != expected:
    raise SystemExit(f"FAIL: expected version {expected}, got {data.get('version')}")

if not isinstance(patches, list) or len(patches) < 40:
    raise SystemExit("FAIL: patches-list has invalid or unexpectedly small patch list")

names = {str(p.get("name", "")) for p in patches if isinstance(p, dict)}
required_markers = {
    "Boost Morphe settings",
    "Fix Boost comments Lemmy-style toolbar UI",
    "Fix Boost navigation bar overlap",
    "Fix Boost target SDK 35 compatibility",
    "Share selected media",
    "Share selected media file",
}

missing = sorted(required_markers - names)
if missing:
    raise SystemExit("FAIL: missing required patch-list markers: " + ", ".join(missing))

print("marker_check=PASS")
PY

if ! diff -u "$ORIGINAL" patches-list.json > "$DIFF_OUT"; then
  echo
  echo "patches-list.json differs from generated output"
  echo "----- diff -----"
  cat "$DIFF_OUT"
  echo
  if [[ "$MODE" == "--write" ]]; then
    echo "MODE=--write, leaving regenerated patches-list.json in working tree"
    trap - EXIT
    rm -f "$ORIGINAL" "$DIFF_OUT"
    echo "RESULT=PATCHES_LIST_FEED_WRITE_OK"
    exit 0
  fi
  echo "FAIL: patches-list.json is stale relative to generated output"
  exit 1
fi

echo "RESULT=PATCHES_LIST_FEED_CHECK_OK"
