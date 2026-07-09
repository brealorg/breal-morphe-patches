#!/usr/bin/env bash
FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

VERSION=""
TAG=""
RELEASE_NOTES_FILE=""

usage() {
  cat <<'EOF'
Usage:
  scripts/release-publish-existing.sh --version VERSION --tag TAG --release-notes-file PATH

Options:
  --version VERSION             Release version, e.g. 1.4.62. Required.
  --tag TAG                     Release tag, e.g. morphe-patches-62. Required.
  --release-notes-file PATH     Human-readable GitHub release notes. Required.
                                Must include app/area, Changes, User impact.
                                Validation details are appended automatically.
  -h, --help                    Show this help.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --version) VERSION="${2:-}"; shift 2 ;;
    --tag) TAG="${2:-}"; shift 2 ;;
    --release-notes-file) RELEASE_NOTES_FILE="${2:-}"; shift 2 ;;
    *) echo "FAIL: unknown arg: $1"; exit 2 ;;
  esac
done

[ -n "$VERSION" ] || { echo "FAIL: --version required"; exit 2; }
[ -n "$TAG" ] || { echo "FAIL: --tag required"; exit 2; }
[ -n "$RELEASE_NOTES_FILE" ] || { echo "FAIL: --release-notes-file required"; exit 2; }
[ -f "$RELEASE_NOTES_FILE" ] || { echo "FAIL: release notes file not found: $RELEASE_NOTES_FILE"; exit 2; }

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
echo "RELEASE_NOTES_FILE=$RELEASE_NOTES_FILE"

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
echo "===== compose and validate GitHub release notes ====="
NOTES="$OUT/release-notes.md"
if [ "$FAIL" -eq 0 ]; then
  cat "$RELEASE_NOTES_FILE" > "$NOTES" || mark_fail "could not copy release notes file"
  {
    echo
    echo "### Validation"
    echo
    echo "- Final local release gate passed before publish."
    echo "- README SHA is aligned to the published MPP."
    echo "- Release tag: \`$TAG\`."
    echo "- Release asset: \`patches-${VERSION}.mpp\`."
    echo "- Signature asset: \`patches-${VERSION}.mpp.asc\`."
    echo "- MPP SHA256: \`$MPP_SHA\`."
  } >> "$NOTES"

  python3 scripts/validate-release-notes.py \
    --notes-file "$NOTES" \
    --version "$VERSION" \
    --tag "$TAG" \
    --asset "patches-${VERSION}.mpp" \
    --sha256 "$MPP_SHA" \
    --require-sha \
    2>&1 | tee "$OUT/validate-release-notes.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "release notes validation failed rc=$RC"
fi

echo
echo "===== create GitHub release ====="
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
