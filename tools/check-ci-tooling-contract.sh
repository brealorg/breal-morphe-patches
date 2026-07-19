#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SMOKE_WORKFLOW="$ROOT/.github/workflows/release-feed-smoke.yml"
RELEASE_WORKFLOW="$ROOT/.github/workflows/release.yml"
PROJECT_RUNNER="$ROOT/tools/check-project-contracts.sh"
FEED_RUNNER="$ROOT/tools/release-feed-smoke.sh"

test -f "$SMOKE_WORKFLOW"
test -f "$RELEASE_WORKFLOW"
test -f "$PROJECT_RUNNER"
test -f "$FEED_RUNNER"

rg -q -F "command -v rg" "$PROJECT_RUNNER"
rg -q -F "./tools/check-project-contracts.sh" "$FEED_RUNNER"

python3 - "$SMOKE_WORKFLOW" "$RELEASE_WORKFLOW" <<'PY_CHECK'
import sys
from pathlib import Path

for raw_path in sys.argv[1:]:
    path = Path(raw_path)
    text = path.read_text(encoding="utf-8")
    required = (
        "- name: Install ripgrep",
        "if ! command -v rg >/dev/null 2>&1; then",
        "sudo apt-get install --yes ripgrep",
        "rg --version",
    )
    for marker in required:
        assert marker in text, f"missing {marker!r}: {path}"

    install_index = text.index("- name: Install ripgrep")
    if path.name == "release-feed-smoke.yml":
        consumer_index = text.index("./tools/release-feed-smoke.sh")
    else:
        consumer_index = text.index("./tools/check-project-contracts.sh")
    assert install_index < consumer_index, f"ripgrep installed too late: {path}"
PY_CHECK

echo 'PROJECT_RUNNER_RIPGREP_PREFLIGHT=PASS'
echo 'RELEASE_FEED_SMOKE_PROVISIONS_RIPGREP=PASS'
echo 'RELEASE_WORKFLOW_PROVISIONS_RIPGREP=PASS'
echo 'RESULT=MORPHE_CI_TOOLING_CONTRACT_OK'
