#!/usr/bin/env bash
FAIL=0
CHECK_ONLY=0

usage() {
  cat <<'USAGE'
Usage:
  tools/restore-tracked-build-artifacts.sh [--check]

Restores deleted tracked release artifacts under patches/build/libs after Gradle clean.

Options:
  --check     Report what would be restored, but do not mutate the worktree.
  -h, --help  Show this help.
USAGE
}

mark_fail() {
  echo "FAIL: $*"
  FAIL=1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --check)
      CHECK_ONLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "FAIL: not inside a git repo"
  exit 1
}
cd "$ROOT" || exit 1

TMP="$(mktemp)"
git ls-files -d -- patches/build/libs \
  | grep -E '\.(mpp|mpp\.asc|asc)$' > "$TMP" || true

COUNT="$(wc -l < "$TMP" | tr -d ' ')"
echo "TRACKED_BUILD_ARTIFACT_DELETIONS=$COUNT"

if [ "$COUNT" -eq 0 ]; then
  echo "RESULT=MORPHE_RESTORE_TRACKED_BUILD_ARTIFACTS_NOOP"
  exit 0
fi

cat "$TMP"

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "RESULT=MORPHE_RESTORE_TRACKED_BUILD_ARTIFACTS_CHECK_ONLY"
  exit 0
fi

while IFS= read -r path; do
  [ -n "$path" ] || continue
  git restore -- "$path" || mark_fail "failed to restore $path"
done < "$TMP"

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_RESTORE_TRACKED_BUILD_ARTIFACTS_OK"
else
  echo "RESULT=MORPHE_RESTORE_TRACKED_BUILD_ARTIFACTS_FAIL"
fi
exit "$FAIL"
