#!/usr/bin/env bash

set -u

usage() {
  cat <<'EOF'
Usage:
  scripts/run-local-logged-task.sh TASK_NAME -- /path/to/task.sh [args...]

Runs the task with bash, logs to:
  <repo>/local-artifacts/logs/<task-name>-<timestamp>.log

Prints:
  SCRIPT EXIT CODE
  LOG FILE
EOF
}

if [ "$#" -lt 3 ]; then
  usage
  exit 2
fi

TASK_NAME="$1"
SEP="$2"
shift 2

if [ "$SEP" != "--" ]; then
  usage
  exit 2
fi

REPO="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
SAFE_NAME="$(printf '%s' "$TASK_NAME" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9._-]+/-/g; s/^-+|-+$//g')"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG_DIR="$REPO/local-artifacts/logs"
LOG="$LOG_DIR/${SAFE_NAME}-${STAMP}.log"

mkdir -p "$LOG_DIR"

echo "===== local logged task ====="
echo "REPO=$REPO"
echo "TASK_NAME=$TASK_NAME"
echo "LOG=$LOG"
echo

bash "$@" 2>&1 | tee "$LOG"
RC=${PIPESTATUS[0]}

echo
echo "===== SCRIPT EXIT CODE ====="
echo "$RC"

echo
echo "===== LOG FILE ====="
echo "$LOG"

echo
echo "Terminal is still alive."

exit "$RC"
