#!/usr/bin/env bash
set -u

REQUIRE_CHANGELOG_VERSION=""

while [ "$#" -gt 0 ]; do
  case "$1" in
    --require-changelog-version)
      REQUIRE_CHANGELOG_VERSION="${2:-}"
      shift 2
      ;;
    --require-current-bundle-version)
      REQUIRE_CHANGELOG_VERSION="$(jq -r '.version // empty' patches-bundle.json 2>/dev/null || true)"
      shift
      ;;
    *)
      echo "FAIL: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

FAIL=0
bad() { echo "FAIL: $*"; FAIL=1; }

echo "===== semantic release contract ====="

python3 - <<'PY' || FAIL=1
import json
from pathlib import Path

data = json.loads(Path(".releaserc").read_text())
plugins = data.get("plugins", [])

def find(name):
    for p in plugins:
        if isinstance(p, list) and p and p[0] == name:
            return p[1] if len(p) > 1 else {}
        if p == name:
            return {}
    return None

ca = find("@semantic-release/commit-analyzer")
rn = find("@semantic-release/release-notes-generator")
git = find("@semantic-release/git")
chg = find("@MorpheApp/changelog")

required_rules = {
    ("fix", None, "patch"),
    ("feat", None, "minor"),
    ("bump", None, "patch"),
    ("perf", None, "patch"),
    ("build", "Needs bump", "patch"),
}

rules = set()
for r in (ca or {}).get("releaseRules", []):
    rules.add((r.get("type"), r.get("scope"), r.get("release")))

missing = sorted(required_rules - rules)
if missing:
    print("FAIL: missing releaseRules:", missing)
    raise SystemExit(1)

if not rn or rn.get("preset") != "conventionalcommits":
    print("FAIL: release-notes-generator missing preset=conventionalcommits")
    raise SystemExit(1)

types = (rn.get("presetConfig") or {}).get("types") or []
visible = {t.get("type"): t for t in types if not t.get("hidden", False)}
for t in ["fix", "feat", "bump", "perf"]:
    if t not in visible:
        print(f"FAIL: release-notes-generator type {t!r} is not visible")
        raise SystemExit(1)

msg = (git or {}).get("message", "")
if not msg.startswith("chore: Release v${nextRelease.version} [skip ci]"):
    print("FAIL: @semantic-release/git release commit message is not conventional chore release")
    raise SystemExit(1)

release_json = (chg or {}).get("releaseJson") or {}
sig = release_json.get("signatureUrlTemplate", "")
if not sig.endswith(".mpp.asc"):
    print("FAIL: signatureUrlTemplate does not point to .mpp.asc")
    raise SystemExit(1)

print("SEMANTIC_RELEASE_CONTRACT=OK")
PY

if [ -n "$REQUIRE_CHANGELOG_VERSION" ]; then
  echo
  echo "===== changelog required version check ====="
  if grep -Eq "(^#|^##) .*${REQUIRE_CHANGELOG_VERSION//./\\.}" CHANGELOG.md; then
    echo "CHANGELOG_HAS_REQUIRED_VERSION=YES version=$REQUIRE_CHANGELOG_VERSION"
  else
    bad "CHANGELOG.md missing required version $REQUIRE_CHANGELOG_VERSION"
  fi
else
  echo
  echo "CHANGELOG_REQUIRED_VERSION_CHECK=SKIPPED"
fi

if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=SEMANTIC_RELEASE_CONTRACT_OK"
else
  echo "RESULT=SEMANTIC_RELEASE_CONTRACT_FAIL"
fi
exit "$FAIL"
