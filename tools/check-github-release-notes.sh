#!/usr/bin/env bash
FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

REPO="${REPO:-brealorg/breal-morphe-patches}"
LIMIT="10"

usage() {
  cat <<'EOF'
Usage:
  tools/check-github-release-notes.sh [--repo OWNER/REPO] [--latest N]

Audits recent GitHub Releases for human-readable Morphe release notes.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --repo) REPO="${2:-}"; shift 2 ;;
    --latest) LIMIT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "FAIL: unknown arg: $1"; exit 2 ;;
  esac
done

command -v gh >/dev/null 2>&1 || { echo "FAIL: gh not found"; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "FAIL: jq not found"; exit 1; }

echo "===== GitHub release notes audit ====="
echo "REPO=$REPO"
echo "LIMIT=$LIMIT"

TMP="$(mktemp -d /tmp/morphe-release-notes-audit.XXXXXX)"
gh release list --repo "$REPO" --limit "$LIMIT" --json tagName,name,publishedAt \
  > "$TMP/releases.json" || mark_fail "gh release list failed"

COUNT="$(jq 'length' "$TMP/releases.json" 2>/dev/null || echo 0)"
echo "COUNT=$COUNT"

jq -r '.[] | [.tagName, .name, .publishedAt] | @tsv' "$TMP/releases.json" |
while IFS=$'\t' read -r TAG NAME PUBLISHED; do
  URL="https://github.com/$REPO/releases/tag/$TAG"
  echo
  echo "----- $TAG -----"
  VERSION="$(printf '%s\n' "$TAG" | sed -E 's/^morphe-patches-([0-9]+)$/1.4.\1/')"
  ASSET="patches-${VERSION}.mpp"

  gh release view "$TAG" --repo "$REPO" --json body \
    --jq '.body' > "$TMP/${TAG}.body.md" || {
      echo "FAIL: could not fetch release body for $TAG"
      exit 7
    }

  SHA="$(grep -Eo '[0-9a-f]{64}' "$TMP/${TAG}.body.md" | tail -1 || true)"

  echo "name=$NAME"
  echo "published=$PUBLISHED"
  echo "url=$URL"
  echo "chars=$(wc -c < "$TMP/${TAG}.body.md" | tr -d ' ')"

  if [ -n "$SHA" ]; then
    python3 scripts/validate-release-notes.py \
      --notes-file "$TMP/${TAG}.body.md" \
      --version "$VERSION" \
      --tag "$TAG" \
      --asset "$ASSET" \
      --sha256 "$SHA" \
      --require-sha || exit 8
  else
    python3 scripts/validate-release-notes.py \
      --notes-file "$TMP/${TAG}.body.md" \
      --version "$VERSION" \
      --tag "$TAG" \
      --asset "$ASSET" || exit 8
  fi
done

RC=${PIPESTATUS[1]}
if [ "$RC" -ne 0 ]; then
  mark_fail "one or more release notes failed audit"
fi

if [ "$FAIL" -eq 0 ]; then
  echo
  echo "RESULT=MORPHE_GITHUB_RELEASE_NOTES_AUDIT_OK"
else
  echo
  echo "RESULT=MORPHE_GITHUB_RELEASE_NOTES_AUDIT_FAIL"
fi
exit "$FAIL"
