#!/usr/bin/env bash
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ "$#" -ge 2 ] && [ "${1#-}" = "$1" ] && [ "${2#-}" = "$2" ]; then
  VERSION="$1"
  TAG="$2"
  shift 2
  exec python3 "$SCRIPT_DIR/verify-remote-release.py" --version "$VERSION" --tag "$TAG" "$@"
fi

exec python3 "$SCRIPT_DIR/verify-remote-release.py" "$@"
