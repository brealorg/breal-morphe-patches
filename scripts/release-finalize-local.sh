#!/usr/bin/env bash
FAIL=0
mark_fail() { echo "FAIL: $*"; FAIL=1; }

VERSION=""
TAG=""
NAME=""
CHANGELOG_ARGS=()

while [ "$#" -gt 0 ]; do
  case "$1" in
    --version) VERSION="${2:-}"; shift 2 ;;
    --tag) TAG="${2:-}"; shift 2 ;;
    --name) NAME="${2:-}"; shift 2 ;;
    --changelog) CHANGELOG_ARGS+=(--changelog "${2:-}"); shift 2 ;;
    *) echo "FAIL: unknown arg: $1"; exit 2 ;;
  esac
done

[ -n "$VERSION" ] || { echo "FAIL: --version required"; exit 2; }
[ -n "$TAG" ] || { echo "FAIL: --tag required"; exit 2; }
[ "${#CHANGELOG_ARGS[@]}" -gt 0 ] || { echo "FAIL: at least one --changelog required"; exit 2; }
[ -n "$NAME" ] || NAME="release-${VERSION}-local-finalize"

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 1
cd "$ROOT" || exit 1

OUT="$ROOT/local-artifacts/release-finalize-local-${VERSION}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT" || exit 1
exec > >(tee "$OUT/release-finalize-local.log") 2>&1

echo "===== release finalize local ====="
date -Is
echo "VERSION=$VERSION"
echo "TAG=$TAG"
echo "NAME=$NAME"
echo "OUT=$OUT"

echo
echo "===== precheck ====="
git --no-pager status -sb || mark_fail "git status failed"
git diff --quiet -- . || mark_fail "working tree dirty before finalize"
git diff --cached --quiet -- . || mark_fail "index dirty before finalize"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  mark_fail "local tag already exists: $TAG"
fi
if git ls-remote --tags origin "refs/tags/$TAG" | grep -q "$TAG"; then
  mark_fail "remote tag already exists: $TAG"
fi
if command -v gh >/dev/null 2>&1; then
  gh release view "$TAG" >/dev/null 2>&1
  GH_RC=$?
  echo "GH_RELEASE_VIEW_RC=$GH_RC"
  [ "$GH_RC" -ne 0 ] || mark_fail "GitHub release already exists: $TAG"
fi

echo
echo "===== prepare release metadata ====="
if [ "$FAIL" -eq 0 ]; then
  python3 scripts/prepare-release.py --version "$VERSION" --tag "$TAG" "${CHANGELOG_ARGS[@]}" \
    2>&1 | tee "$OUT/prepare-release.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "prepare-release failed rc=$RC"
fi

echo
echo "===== build final MPP once ====="
MPP="patches/build/libs/patches-${VERSION}.mpp"
ASC="${MPP}.asc"
if [ "$FAIL" -eq 0 ]; then
  ./gradlew :patches:buildAndroid --no-daemon 2>&1 | tee "$OUT/gradle-buildAndroid.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "buildAndroid failed rc=$RC"
fi

echo
echo "===== freeze final MPP SHA ====="
if [ "$FAIL" -eq 0 ]; then
  [ -f "$MPP" ] || mark_fail "MPP missing: $MPP"
  MPP_SHA="$(sha256sum "$MPP" | awk '{print $1}')"
  echo "$MPP_SHA  $MPP" | tee "$OUT/mpp.sha256"
  unzip -t "$MPP" | tee "$OUT/mpp-ziptest.txt" >/dev/null || mark_fail "MPP zip test failed"
  unzip -l "$MPP" | tee "$OUT/mpp-list.txt" >/dev/null || mark_fail "MPP list failed"
  grep -E 'classes.dex|extensions/boostforreddit.mpe' "$OUT/mpp-list.txt" || mark_fail "MPP required entries missing"

  rm -f "$ASC"
  gpg --batch --yes --armor --detach-sign --output "$ASC" "$MPP" || mark_fail "GPG signing failed"
  gpg --verify "$ASC" "$MPP" || mark_fail "GPG signature verify failed"
fi

echo
echo "===== update README from frozen MPP SHA ====="
if [ "$FAIL" -eq 0 ]; then
  python3 scripts/update-readme-current-release-sha.py \
    --version "$VERSION" \
    --tag "$TAG" \
    --asset "patches-${VERSION}.mpp" \
    --sha256 "$MPP_SHA" \
    2>&1 | tee "$OUT/update-readme-sha.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "README SHA update failed rc=$RC"

  grep -q "$MPP_SHA" README.md || mark_fail "README does not contain frozen MPP SHA"
fi

echo
echo "===== build Boost candidate from same frozen MPP ====="
if [ "$FAIL" -eq 0 ]; then
  tools/build-boost-candidate.sh \
    --mpp "$MPP" \
    --name "$NAME" \
    --no-verify-with-sdk \
    2>&1 | tee "$OUT/build-boost-candidate.txt"
  RC=${PIPESTATUS[0]}
  [ "$RC" -eq 0 ] || mark_fail "build-boost-candidate failed rc=$RC"
fi

echo
echo "===== candidate gate ====="
if [ "$FAIL" -eq 0 ]; then
  CANDIDATE_DIR="$(grep -E '^DIR:' "$OUT/build-boost-candidate.txt" | tail -1 | sed -E 's/^DIR:[[:space:]]*//')"
  CANDIDATE_APK="$(grep -E '^APK:' "$OUT/build-boost-candidate.txt" | tail -1 | sed -E 's/^APK:[[:space:]]*//')"
  STATIC_GATE_LOG="$CANDIDATE_DIR/static-gate.log"
  PATCH_LOG="$CANDIDATE_DIR/morphe-patch.log"

  echo "CANDIDATE_DIR=$CANDIDATE_DIR"
  echo "CANDIDATE_APK=$CANDIDATE_APK"

  [ -f "$CANDIDATE_APK" ] || mark_fail "candidate apk missing"
  [ -f "$STATIC_GATE_LOG" ] || mark_fail "static gate log missing"
  [ -f "$PATCH_LOG" ] || mark_fail "patch log missing"
  grep -q 'RESULT: PASS' "$STATIC_GATE_LOG" || mark_fail "static gate did not pass"
  grep -E 'Applied: Modify login WebView|Applied: Spoof client' "$PATCH_LOG" \
    | tee "$OUT/baseline-patch-grep.txt" || mark_fail "baseline patches missing"
fi

echo
echo "===== commit metadata and create local tag only ====="
if [ "$FAIL" -eq 0 ]; then
  git --no-pager status -sb
  git --no-pager diff --stat

  if git diff --quiet -- .; then
    mark_fail "no release metadata diff to commit"
  else
    git add -A || mark_fail "git add failed"
    git commit -m "Release Morphe patch bundle ${VERSION} [skip ci]" || mark_fail "release metadata commit failed"
  fi

  RELEASE_HEAD="$(git rev-parse HEAD)"
  git tag -a "$TAG" -m "Morphe patch bundle ${VERSION}" HEAD || mark_fail "local tag create failed"
  TAG_COMMIT="$(git rev-list -n 1 "$TAG")"
  [ "$TAG_COMMIT" = "$RELEASE_HEAD" ] || mark_fail "local tag does not point at release HEAD"
fi

echo
echo "===== final state ====="
git --no-pager status -sb || true
git --no-pager log --oneline --decorate -12 || true

echo
if [ "$FAIL" -eq 0 ]; then
  echo "RESULT=MORPHE_RELEASE_FINALIZE_LOCAL_OK"
  echo "VERSION=$VERSION"
  echo "TAG=$TAG"
  echo "MPP=$MPP"
  echo "ASC=$ASC"
  echo "MPP_SHA256=$MPP_SHA"
  echo "CANDIDATE_APK=$CANDIDATE_APK"
else
  echo "RESULT=MORPHE_RELEASE_FINALIZE_LOCAL_FAIL"
fi
echo "OUT=$OUT"
exit "$FAIL"
