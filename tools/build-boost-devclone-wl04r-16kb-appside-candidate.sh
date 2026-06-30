#!/usr/bin/env bash

# Build a Boost DEV-clone candidate with WL-04R app-side 16 KB-page-size remediation.
#
# Remediation:
# - he.c / BlurTransformation.b(Bitmap) redirects RenderScript blur to existing ze.a FastBlur.
# - com.google.android.gms.ads.internal.overlay.c.zza() is no-op.
# - librsjni_androidx.so and libRSSupport.so are removed from all APK ABIs.
# - non-arm64 native ABI directories are pruned so 16 KB runtimes do not select 0x1000-aligned x86/x86_64/armeabi libs.
#
# This wrapper does not install anything. It delegates signing/package rewrite to
# tools/build-boost-devclone-candidate.sh after injecting the decoded-APK mutation.

set -uo pipefail

usage() {
  cat <<USAGE
Usage:
  $0 --source-apk PATH [--name NAME] [--dev-package PKG] [--normal-package PKG] [--label LABEL] [--out-root DIR]

Defaults:
  --name wl04r-16kb-appside-dev
  --dev-package com.rubenmayayo.reddit.dev
  --normal-package com.rubenmayayo.reddit
  --label "Boost Dev"
  --out-root local-artifacts/boost-dev-overwrite-candidates
USAGE
}

SOURCE_APK=""
NAME="wl04r-16kb-appside-dev"
DEV_PACKAGE="com.rubenmayayo.reddit.dev"
NORMAL_PACKAGE="com.rubenmayayo.reddit"
LABEL="Boost Dev"
OUT_ROOT="local-artifacts/boost-dev-overwrite-candidates"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --source-apk) SOURCE_APK="${2:-}"; shift 2 ;;
    --name) NAME="${2:-}"; shift 2 ;;
    --dev-package) DEV_PACKAGE="${2:-}"; shift 2 ;;
    --normal-package) NORMAL_PACKAGE="${2:-}"; shift 2 ;;
    --label) LABEL="${2:-}"; shift 2 ;;
    --out-root) OUT_ROOT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ -z "$SOURCE_APK" ]; then
  echo "FAIL: --source-apk is required" >&2
  usage >&2
  exit 2
fi

if [ ! -f "$SOURCE_APK" ]; then
  echo "FAIL: source APK missing: $SOURCE_APK" >&2
  exit 1
fi

BASE_BUILDER="tools/build-boost-devclone-candidate.sh"
if [ ! -f "$BASE_BUILDER" ]; then
  echo "FAIL: missing base builder: $BASE_BUILDER" >&2
  exit 1
fi

TMP_BUILDER="/tmp/boost-devclone-builder-wl04r-16kb-appside.$(date +%Y%m%d-%H%M%S).sh"

python3 - "$BASE_BUILDER" "$TMP_BUILDER" <<'PY'
from pathlib import Path
import sys

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
text = src.read_text()

needle = '  apktool b "$DECODED" -o "$UNSIGNED_APK"'
if needle not in text:
    print("FAIL: injection point not found in devclone builder", file=sys.stderr)
    sys.exit(1)

injection = r'''
  echo "[WL04R] applying app-side RenderScript/16KB remediation before apktool build"

  python3 - "$DECODED" <<'PYWL04R'
from pathlib import Path
import sys

decoded = Path(sys.argv[1])

def fail(msg: str) -> None:
    print(f"[WL04R][FAIL] {msg}", file=sys.stderr)
    sys.exit(1)

def replace_method(path: Path, method_decl: str, body: str) -> None:
    if not path.exists():
        fail(f"missing smali file: {path}")
    text = path.read_text()
    start = text.find(method_decl)
    if start < 0:
        fail(f"method not found in {path}: {method_decl}")
    end = text.find(".end method", start)
    if end < 0:
        fail(f"method end not found in {path}: {method_decl}")
    end += len(".end method")
    path.write_text(text[:start] + body.strip() + "\n" + text[end:])
    print(f"[WL04R] patched method in {path.relative_to(decoded)}: {method_decl}")

he = decoded / "smali_classes4/he/c.smali"
ads = decoded / "smali_classes3/com/google/android/gms/ads/internal/overlay/c.smali"

replace_method(
    he,
    ".method public b(Landroid/graphics/Bitmap;)Landroid/graphics/Bitmap;",
    """
.method public b(Landroid/graphics/Bitmap;)Landroid/graphics/Bitmap;
    .locals 3

    iget v0, p0, Lhe/c;->b:F
    float-to-int v0, v0

    const/4 v1, 0x1

    invoke-static {p1, v0, v1}, Lze/a;->a(Landroid/graphics/Bitmap;IZ)Landroid/graphics/Bitmap;
    move-result-object v2

    if-eqz v2, :morphe_wl04r_return_original

    return-object v2

:morphe_wl04r_return_original
    return-object p1
.end method
"""
)

replace_method(
    ads,
    ".method public final zza()V",
    """
.method public final zza()V
    .locals 0

    return-void
.end method
"""
)

removed = []
lib_dir = decoded / "lib"
if lib_dir.exists():
    for so in list(lib_dir.glob("*/librsjni_androidx.so")) + list(lib_dir.glob("*/libRSSupport.so")):
        removed.append(str(so.relative_to(decoded)))
        so.unlink()

print("[WL04R] removed native libs:")
for item in sorted(removed):
    print(f"[WL04R]   {item}")

if not removed:
    print("[WL04R][WARN] no rsjni/RSSupport libs removed")
PYWL04R

  echo "[WL04R] post-remediation decoded checks"
  if grep -RInE 'Landroid/renderscript/|ScriptIntrinsicBlur|RenderScript;->create' "$DECODED"/smali* 2>/dev/null; then
    echo "[WL04R][FAIL] RenderScript smali references remain after remediation"
    exit 1
  else
    echo "[WL04R][PASS] no RenderScript smali references remain after remediation"
  fi

  if find "$DECODED/lib" -type f \( -name 'librsjni_androidx.so' -o -name 'libRSSupport.so' \) 2>/dev/null | grep -q .; then
    echo "[WL04R][FAIL] rsjni/RSSupport libs remain after remediation"
    find "$DECODED/lib" -type f \( -name 'librsjni_androidx.so' -o -name 'libRSSupport.so' \) 2>/dev/null
    exit 1
  else
    echo "[WL04R][PASS] rsjni/RSSupport libs removed from decoded APK"
  fi

  echo "[WL04E] pruning native ABIs to arm64-v8a only for clean 16KB runtime"
  if [ ! -d "$DECODED/lib" ]; then
    echo "[WL04E][FAIL] decoded lib dir missing before ABI prune"
    exit 1
  fi

  echo "[WL04E] native ABI dirs before prune:"
  find "$DECODED/lib" -mindepth 1 -maxdepth 1 -type d -printf '[WL04E]   %f\n' | sort || true

  find "$DECODED/lib" -mindepth 1 -maxdepth 1 -type d ! -name arm64-v8a -print -exec rm -rf {} +

  echo "[WL04E] native ABI dirs after prune:"
  find "$DECODED/lib" -mindepth 1 -maxdepth 1 -type d -printf '[WL04E]   %f\n' | sort || true

  if find "$DECODED/lib" -mindepth 1 -maxdepth 1 -type d ! -name arm64-v8a 2>/dev/null | grep -q .; then
    echo "[WL04E][FAIL] non-arm64 ABI dirs remain after prune"
    find "$DECODED/lib" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort
    exit 1
  fi

  if [ ! -d "$DECODED/lib/arm64-v8a" ]; then
    echo "[WL04E][FAIL] arm64-v8a ABI dir missing after prune"
    exit 1
  fi

  echo "[WL04E][PASS] native ABIs pruned to arm64-v8a only"
'''

text = text.replace(needle, injection + "\n" + needle, 1)
dst.write_text(text)
dst.chmod(0o755)
print(f"TEMP_BUILDER={dst}")
PY

bash "$TMP_BUILDER" \
  --source-apk "$SOURCE_APK" \
  --name "$NAME" \
  --dev-package "$DEV_PACKAGE" \
  --normal-package "$NORMAL_PACKAGE" \
  --label "$LABEL" \
  --out-root "$OUT_ROOT"
