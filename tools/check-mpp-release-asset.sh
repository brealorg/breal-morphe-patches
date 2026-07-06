#!/usr/bin/env bash
MPP="${1:-}"

if [ -z "$MPP" ]; then
  echo "usage: $0 <patches-x.y.z.mpp>"
  exit 2
fi

if [ ! -f "$MPP" ]; then
  echo "FAIL: missing MPP: $MPP"
  exit 1
fi

python3 - "$MPP" <<'PY'
import sys
import zipfile
from pathlib import Path

mpp = Path(sys.argv[1])
required = {
    "classes.dex",
    "extensions/boostforreddit.mpe",
}

with zipfile.ZipFile(mpp) as z:
    names = set(z.namelist())

missing = sorted(required - names)
print("MPP=", str(mpp))
print("has_classes_dex=", int("classes.dex" in names))
print("has_boostforreddit_mpe=", int("extensions/boostforreddit.mpe" in names))

if missing:
    print("FAIL: missing required MPP entries:", ", ".join(missing))
    raise SystemExit(1)

print("MPP_RELEASE_ASSET_STRUCTURE_OK")
PY
