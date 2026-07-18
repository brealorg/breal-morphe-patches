#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION=""

usage() {
  cat <<'EOF'
Usage:
  tools/boost-resolve-mpp.sh [--version VERSION]

Resolve the canonical Android MPP for the current repository version.
The selected archive must contain both classes.dex and the Boost extension.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "FAIL: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [ -z "$VERSION" ]; then
  VERSION="$(
    sed -nE 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*([^[:space:]]+).*/\1/p' \
      "$ROOT/gradle.properties" |
      head -1
  )"
fi

if [ -z "$VERSION" ]; then
  echo "FAIL: could not resolve project version from gradle.properties" >&2
  exit 1
fi

MPP="$ROOT/patches/build/libs/patches-${VERSION}.mpp"

if [ ! -s "$MPP" ]; then
  echo "FAIL: canonical Android MPP missing: $MPP" >&2
  echo "Run: ./gradlew :patches:buildAndroid --no-daemon" >&2
  exit 1
fi

if ! "$ROOT/tools/check-mpp-release-asset.sh" "$MPP" >&2; then
  echo "FAIL: $MPP is not a canonical Android MPP" >&2
  echo "Run: ./gradlew :patches:buildAndroid --no-daemon" >&2
  exit 1
fi

printf '%s\n' "$MPP"
