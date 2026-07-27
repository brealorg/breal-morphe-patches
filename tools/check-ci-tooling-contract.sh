#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SMOKE_WORKFLOW="$ROOT/.github/workflows/release-feed-smoke.yml"
RELEASE_WORKFLOW="$ROOT/.github/workflows/release.yml"
PROJECT_RUNNER="$ROOT/tools/check-project-contracts.sh"
FEED_RUNNER="$ROOT/tools/release-feed-smoke.sh"
COMPOSER="$ROOT/scripts/compose-release-notes.py"
VALIDATOR="$ROOT/scripts/validate-release-notes.py"

for path in \
  "$SMOKE_WORKFLOW" \
  "$RELEASE_WORKFLOW" \
  "$PROJECT_RUNNER" \
  "$FEED_RUNNER" \
  "$COMPOSER" \
  "$VALIDATOR"
do
  test -f "$path"
done

rg -q -F "command -v rg" "$PROJECT_RUNNER"
rg -q -F "./tools/check-project-contracts.sh" "$FEED_RUNNER"
rg -q -F "python3 scripts/release-gate.py" "$FEED_RUNNER"
rg -q -F -- '--version "$VERSION"' "$FEED_RUNNER"
rg -q -F -- '--tag "$TAG"' "$FEED_RUNNER"
rg -q -F -- '--mpp "$MPP"' "$FEED_RUNNER"

python3 - "$SMOKE_WORKFLOW" "$RELEASE_WORKFLOW" <<'PY_CHECK'
import sys
from pathlib import Path

smoke_path = Path(sys.argv[1])
release_path = Path(sys.argv[2])
smoke_text = smoke_path.read_text(encoding="utf-8")
release_text = release_path.read_text(encoding="utf-8")

for path, text in ((smoke_path, smoke_text), (release_path, release_text)):
    required = (
        "- name: Install ripgrep",
        "if ! command -v rg >/dev/null 2>&1; then",
        "sudo apt-get install --yes ripgrep",
        "rg --version",
        "java-version: '17.0.19+10'",
    )
    for marker in required:
        assert marker in text, f"missing {marker!r}: {path}"

    install_index = text.index("- name: Install ripgrep")
    if path.name == "release-feed-smoke.yml":
        consumer_index = text.index("./tools/release-feed-smoke.sh")
    else:
        consumer_index = text.index("./tools/check-project-contracts.sh")
    assert install_index < consumer_index, f"ripgrep installed too late: {path}"

for input_name in ("app_area", "changes", "user_impact", "issue_number"):
    assert f"      {input_name}:" in release_text, (
        f"missing structured release input {input_name!r}"
    )

assert "      release_notes:" not in release_text
assert "RELEASE_NOTES: ${{ inputs.release_notes }}" not in release_text

notes_required = (
    "- name: Compose and validate release notes",
    "python3 scripts/compose-release-notes.py",
    "python3 scripts/validate-release-notes.py",
    "--human-input-only",
    "RELEASE_NOTES_INPUT_VALIDATION=PASS",
)
for marker in notes_required:
    assert release_text.count(marker) == 1, (
        f"expected exactly one release-notes marker {marker!r}"
    )

checkout_index = release_text.index("- name: Checkout exact main")
notes_index = release_text.index("- name: Compose and validate release notes")
java_index = release_text.index("- name: Setup Java")
gpg_index = release_text.index("- name: Import GPG key")
publish_index = release_text.index("- name: Publish from protected main")
assert checkout_index < notes_index < java_index < gpg_index < publish_index

identity_required = (
    "- name: Configure release Git identity",
    'git config --local user.name "github-actions[bot]"',
    'git config --local user.email "41898282+github-actions[bot]@users.noreply.github.com"',
    'test "$(git config --local user.name)" = "github-actions[bot]"',
    'test "$(git config --local user.email)" = "41898282+github-actions[bot]@users.noreply.github.com"',
    "git var GIT_COMMITTER_IDENT",
)
for marker in identity_required:
    assert release_text.count(marker) == 1, (
        f"expected exactly one release Git identity marker {marker!r}"
    )

identity_index = release_text.index("- name: Configure release Git identity")
assert gpg_index < identity_index < publish_index
PY_CHECK

echo 'PROJECT_RUNNER_RIPGREP_PREFLIGHT=PASS'
echo 'RELEASE_FEED_SMOKE_PROVISIONS_RIPGREP=PASS'
echo 'RELEASE_FEED_MPP_SHA_GATE=PASS'
echo 'RELEASE_WORKFLOW_PROVISIONS_RIPGREP=PASS'
echo 'RELEASE_WORKFLOW_STRUCTURED_NOTES=PASS'
echo 'RELEASE_WORKFLOW_EXACT_TEMURIN17=PASS'
echo 'RELEASE_WORKFLOW_GIT_IDENTITY=PASS'
echo 'RESULT=MORPHE_CI_TOOLING_CONTRACT_OK'
