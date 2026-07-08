#!/usr/bin/env bash
FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

VERSION=""
TAG=""

usage() {
  cat <<'EOF'
Usage:
  scripts/release-publish-existing.sh --version VERSION --tag TAG

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

OUT="$ROOT/local-artifacts/release-publish-existing-${VERSION}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT" || exit 1
exec > >(tee "$OUT/release-publish-existing.log") 2>&1

MPP="patches/build/libs/patches-${VERSION}.mpp"
ASC="${MPP}.asc"

echo "===== release publish existing ====="
date -Is
echo "VERSION=$VERSION"
echo "TAG=$TAG"
echo "MPP=$MPP"
echo "ASC=$ASC"
echo "OUT=$OUT"

echo
echo "===== precheck: no builds, no metadata writes ====="
git --no-pager status -sb || mark_fail "git status failed"
git diff --quiet -- . || mark_fail "working tree dirty"
git diff --cached --quiet -- . || mark_fail "index dirty"

BRANCH="$(git branch --show-current 2>/dev/null || true)"
[ "$BRANCH" = "main" ] || mark_fail "expected main, got $BRANCH"

[ -f "$MPP" ] || mark_fail "MPP missing: $MPP"
[ -f "$ASC" ] || mark_fail "ASC missing: $ASC"
gpg --verify "$ASC" "$MPP" || mark_fail "GPG signature invalid"

MPP_SHA="$(sha256sum "$MPP" | awk '{print $1}')"
echo "$MPP_SHA  $MPP" | tee "$OUT/mpp.sha256"
grep -q "$MPP_SHA" README.md || mark_fail "README does not contain MPP SHA"

LOCAL_MAIN="$(git rev-parse main)"
LOCAL_DEV="$(git rev-parse dev)"
TAG_COMMIT="$(git rev-list -n 1 "$TAG" 2>/dev/null || true)"
echo "LOCAL_MAIN=$LOCAL_MAIN"
echo "LOCAL_DEV=$LOCAL_DEV"
echo "TAG_COMMIT=$TAG_COMMIT"

[ "$LOCAL_MAIN" = "$LOCAL_DEV" ] || mark_fail "local main/dev not aligned"
[ "$TAG_COMMIT" = "$LOCAL_MAIN" ] || mark_fail "local tag not at main"

git fetch origin main dev --tags || mark_fail "fetch failed"
if git ls-remote --tags origin "refs/tags/$TAG" | grep -q "$TAG"; then
  mark_fail "remote tag already exists: $TAG"
fi
if gh release view "$TAG" >/dev/null 2>&1; then
  mark_fail "GitHub release already exists: $TAG"
fi

echo
echo "===== push refs ====="
if [ "$FAIL" -eq 0 ]; then
  git push origin main dev || mark_fail "push main/dev failed"
  git push origin "$TAG" || mark_fail "push tag failed"
fi

echo
echo "===== create GitHub release ====="
NOTES="$OUT/release-notes.md"
cat > "$NOTES" <<EOF
Morphe patch bundle ${VERSION}

Validation:
- Final local release gate passed before publish.
- README SHA is aligned to the published MPP.
- Assets: patches-${VERSION}.mpp and patches-${VERSION}.mpp.asc.
EOF

if [ "$FAIL" -eq 0 ]; then
  gh release create "$TAG" "$MPP" "$ASC" \
    --title "Morphe patch bundle ${VERSION}" \
    --notes-file "$NOTES" \
    2>&1 | tee "$OUT/gh-release-create.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "gh release create failed rc=$RC"
fi

echo
echo "===== remote verify ====="
if [ "$FAIL" -eq 0 ]; then
  python3 scripts/verify-remote-release.py --version "$VERSION" --tag "$TAG" \
    2>&1 | tee "$OUT/verify-remote-release.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "remote verification failed rc=$RC"
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_RELEASE_PUBLISH_EXISTING_OK"
  echo "VERSION=$VERSION"
  echo "TAG=$TAG"
  echo "MPP_SHA256=$MPP_SHA"
else
  echo "RESULT=MORPHE_RELEASE_PUBLISH_EXISTING_FAIL"
fi
echo "OUT=$OUT"
exit "$FAIL"
