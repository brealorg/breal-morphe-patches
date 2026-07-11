#!/usr/bin/env bash
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 1
exec python3 "$ROOT/scripts/releasectl.py" publish "$@"
