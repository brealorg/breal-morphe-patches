#!/usr/bin/env bash
FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

VERSION=""
TAG=""

usage() {
  cat <<'EOF'
Usage:
  scripts/release-repair-metadata-only.sh --version VERSION --tag TAG

Options:
  --version VERSION   Release version, e.g. 1.4.62. Required.
  --tag TAG           Release tag, e.g. morphe-patches-62. Required.
  -h, --help          Show this help.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --version) VERSION="${2:-}"; shift 2 ;;
    --tag) TAG="${2:-}"; shift 2 ;;
    *) echo "FAIL: unknown arg: $1"; exit 2 ;;
  esac
done

[ -n "$VERSION" ] || { echo "FAIL: --version required"; exit 2; }
[ -n "$TAG" ] || { echo "FAIL: --tag required"; exit 2; }

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 1
cd "$ROOT" || exit 1

OUT="$ROOT/local-artifacts/release-repair-metadata-only-${VERSION}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT" || exit 1
exec > >(tee "$OUT/release-repair-metadata-only.log") 2>&1

ASSET="patches-${VERSION}.mpp"
ASC_ASSET="${ASSET}.asc"

echo "===== release repair metadata only ====="
date -Is
echo "VERSION=$VERSION"
echo "TAG=$TAG"
echo "ASSET=$ASSET"
echo "OUT=$OUT"

echo
echo "===== precheck ====="
git --no-pager status -sb || mark_fail "git status failed"
git diff --quiet -- . || mark_fail "working tree dirty"
git diff --cached --quiet -- . || mark_fail "index dirty"

BRANCH="$(git branch --show-current 2>/dev/null || true)"
[ "$BRANCH" = "main" ] || mark_fail "expected main, got $BRANCH"

git fetch origin main dev --tags || mark_fail "fetch failed"

LOCAL_MAIN="$(git rev-parse main)"
ORIGIN_MAIN="$(git rev-parse origin/main)"
ORIGIN_DEV="$(git rev-parse origin/dev)"
echo "LOCAL_MAIN=$LOCAL_MAIN"
echo "ORIGIN_MAIN=$ORIGIN_MAIN"
echo "ORIGIN_DEV=$ORIGIN_DEV"

[ "$LOCAL_MAIN" = "$ORIGIN_MAIN" ] || mark_fail "local main not aligned with origin/main"
[ "$ORIGIN_MAIN" = "$ORIGIN_DEV" ] || mark_fail "origin main/dev not aligned"

echo
echo "===== download existing release asset as source of truth ====="
REMOTE_DIR="$OUT/remote-assets"
mkdir -p "$REMOTE_DIR" || mark_fail "mkdir remote assets failed"

if [ "$FAIL" -eq 0 ]; then
  gh release view "$TAG" --json tagName,name,url,assets > "$OUT/gh-release-view-before.json" \
    || mark_fail "gh release view failed"

  jq -e --arg asset "$ASSET" '.assets[] | select(.name == $asset)' "$OUT/gh-release-view-before.json" >/dev/null \
    || mark_fail "release MPP asset missing"
  jq -e --arg asset "$ASC_ASSET" '.assets[] | select(.name == $asset)' "$OUT/gh-release-view-before.json" >/dev/null \
    || mark_fail "release ASC asset missing"

  gh release download "$TAG" -p "$ASSET" -p "$ASC_ASSET" -D "$REMOTE_DIR" \
    || mark_fail "release asset download failed"

  REMOTE_MPP="$REMOTE_DIR/$ASSET"
  REMOTE_ASC="$REMOTE_DIR/$ASC_ASSET"
  [ -f "$REMOTE_MPP" ] || mark_fail "downloaded MPP missing"
  [ -f "$REMOTE_ASC" ] || mark_fail "downloaded ASC missing"

  gpg --verify "$REMOTE_ASC" "$REMOTE_MPP" || mark_fail "remote asset signature invalid"
  REMOTE_SHA="$(sha256sum "$REMOTE_MPP" | awk '{print $1}')"
  echo "REMOTE_SHA=$REMOTE_SHA" | tee "$OUT/remote-sha.txt"
fi

echo
echo "===== update README metadata from remote asset SHA ====="
if [ "$FAIL" -eq 0 ]; then
  python3 scripts/update-readme-current-release-sha.py \
    --version "$VERSION" \
    --tag "$TAG" \
    --asset "$ASSET" \
    --sha256 "$REMOTE_SHA" \
    2>&1 | tee "$OUT/update-readme-sha.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "README SHA update failed rc=$RC"

  grep -q "$REMOTE_SHA" README.md || mark_fail "README does not contain remote SHA"
fi

echo
echo "===== commit metadata repair if needed ====="
if [ "$FAIL" -eq 0 ]; then
  git --no-pager diff --stat || true

  if git diff --quiet -- .; then
    echo "INFO: metadata already aligned; no commit needed"
  else
    git add README.md || mark_fail "git add README failed"
    git commit -m "fix: align release ${VERSION} asset sha [skip ci]" \
      || mark_fail "metadata repair commit failed"

    REPAIR_HEAD="$(git rev-parse main)"
    OLD_DEV="$(git rev-parse dev)"

    {
      echo start
      echo "update refs/heads/dev $REPAIR_HEAD $OLD_DEV"
      echo prepare
      echo commit
    } | git update-ref --stdin || mark_fail "local dev alignment transaction failed"

    if [ "$FAIL" -eq 0 ]; then
      git push --atomic origin \
        "refs/heads/main:refs/heads/main" \
        "refs/heads/dev:refs/heads/dev" \
        || mark_fail "atomic main/dev metadata push failed"
    fi

    echo "INFO: immutable release tag is intentionally unchanged"
  fi
fi

echo
echo "===== final remote verification ====="
if [ "$FAIL" -eq 0 ]; then
  VERIFY_OK=0
  for N in 1 2 3 4 5 6; do
    echo "VERIFY_ATTEMPT=$N"
    python3 scripts/verify-remote-release.py --version "$VERSION" --tag "$TAG" \
      2>&1 | tee "$OUT/verify-remote-release-attempt-${N}.txt"
    RC=${PIPESTATUS[0]}
    if [ "$RC" -eq 0 ]; then
      VERIFY_OK=1
      break
    fi
    sleep 7
  done
  [ "$VERIFY_OK" -eq 1 ] || mark_fail "remote verification failed after retries"
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_RELEASE_REPAIR_METADATA_ONLY_OK"
  echo "VERSION=$VERSION"
  echo "TAG=$TAG"
  echo "REMOTE_SHA256=$REMOTE_SHA"
else
  echo "RESULT=MORPHE_RELEASE_REPAIR_METADATA_ONLY_FAIL"
fi
echo "OUT=$OUT"
exit "$FAIL"
