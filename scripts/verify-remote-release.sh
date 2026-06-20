#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?Usage: scripts/verify-remote-release.sh <version> <tag>}"
TAG="${2:?Usage: scripts/verify-remote-release.sh <version> <tag>}"

MPP_NAME="patches-${VERSION}.mpp"
RAW_JSON="https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json"
TMP_MPP="/tmp/${MPP_NAME}"
EXPECTED_URL="https://github.com/brealorg/breal-morphe-patches/releases/download/${TAG}/${MPP_NAME}"

echo "===== fetch raw patches-bundle.json ====="
JSON="$(curl -fsSL "$RAW_JSON")"
printf '%s\n' "$JSON" | jq .

REMOTE_VERSION="$(printf '%s\n' "$JSON" | jq -r '.version')"
DOWNLOAD_URL="$(printf '%s\n' "$JSON" | jq -r '.download_url')"

echo
echo "===== json fields ====="
echo "version=$REMOTE_VERSION"
echo "download_url=$DOWNLOAD_URL"
echo "expected_url=$EXPECTED_URL"

if [[ "$REMOTE_VERSION" != "$VERSION" ]]; then
  echo "BAD: remote version mismatch"
  exit 1
fi

if [[ "$DOWNLOAD_URL" != "$EXPECTED_URL" ]]; then
  echo "BAD: remote download_url mismatch"
  exit 1
fi

echo
echo "===== download remote MPP ====="
rm -f "$TMP_MPP"
curl -fL -o "$TMP_MPP" "$DOWNLOAD_URL"
ls -lh "$TMP_MPP"
sha256sum "$TMP_MPP"

echo
echo "===== required entries ====="
unzip -l "$TMP_MPP" | grep -Ei 'classes.*\.dex|META-INF/MANIFEST.MF|extensions/boostforreddit\.mpe'

echo
echo "===== remote asset gate ====="
VERSION="$VERSION" TAG="$TAG" TMP_MPP="$TMP_MPP" python3 <<'PY'
from pathlib import Path
import hashlib
import os
import re
import zipfile

version = os.environ["VERSION"]
tag = os.environ["TAG"]
mpp = Path(os.environ["TMP_MPP"])
readme = Path("README.md").read_text(encoding="utf-8")

sha = hashlib.sha256(mpp.read_bytes()).hexdigest()
print("sha256:", sha)

match = re.search(r"(?ms)^SHA256:\s*`?([0-9a-f]{64})`?", readme)
if not match:
    raise SystemExit("BAD: README SHA256 not found")

readme_sha = match.group(1)
print("README sha256:", readme_sha)

if sha != readme_sha:
    raise SystemExit("BAD: downloaded MPP SHA does not match README")

with zipfile.ZipFile(mpp) as z:
    names = set(z.namelist())

    if "classes.dex" not in names:
        raise SystemExit("BAD: classes.dex missing")

    if "extensions/boostforreddit.mpe" not in names:
        raise SystemExit("BAD: boostforreddit.mpe missing")

    data = z.read("extensions/boostforreddit.mpe")
    markers = [
        b"REDDIT_PLAYER_URL",
        b"openRedditPlayerLink",
        b"https://v.redd.it/",
    ]
    found = [m.decode() for m in markers if m in data]
    missing = [m.decode() for m in markers if m not in data]

    print("classes.dex: OK")
    print("markers:", found)

    if missing:
        raise SystemExit(f"BAD: missing markers: {missing}")

print("REMOTE RELEASE OK")
PY
