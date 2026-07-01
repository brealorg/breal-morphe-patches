#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import zipfile
from pathlib import Path


BOOST_EXTENSION = "extensions/boostforreddit.mpe"

# These must be in the Boost extension payload, because they prove the runtime
# Java side and routing/action constants are present.
EXTENSION_REQUIRED_MARKERS = [
    "morphe_boost_direct_reddit_gif_tap_action",
    "morphe_boost_giphy_preview_tap_action",
    "morphe_boost_static_preview_tap_action",
    "MediaTapActionPreference",
    "InlineGiphyCommentPreview",
    "image_viewer",
    "video_viewer",
    "browser",
    "disabled",
    "openStaticImageViaBoost",
    "open direct i.redd.it gif via Boost image viewer",
]

# These may live in classes.dex, because settings XML/resource strings are
# packaged in the MPP classes/resources path rather than necessarily inside
# extensions/boostforreddit.mpe.
MPP_WIDE_REQUIRED_MARKERS = [
    "Direct Reddit GIF tap action",
    "Giphy preview tap action",
    "Static preview tap action",
]


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def mpp_name_for(version: str) -> str:
    return f"patches-{version}.mpp"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check Boost runtime media tap-action markers in a built Morphe .mpp."
    )
    parser.add_argument("--version", required=True, help="Bundle version, e.g. 1.4.x")
    parser.add_argument(
        "--mpp",
        help="Path to .mpp. Defaults to patches/build/libs/patches-<version>.mpp.",
    )
    parser.add_argument(
        "--extension-marker",
        action="append",
        default=[],
        help="Additional UTF-8 marker required inside extensions/boostforreddit.mpe.",
    )
    parser.add_argument(
        "--mpp-marker",
        action="append",
        default=[],
        help="Additional UTF-8 marker required anywhere in the .mpp archive entries.",
    )
    args = parser.parse_args()

    mpp = Path(args.mpp) if args.mpp else Path("patches") / "build" / "libs" / mpp_name_for(args.version)

    errors: list[str] = []

    print("===== Boost runtime media marker gate =====")
    print("version:", args.version)
    print("mpp:", mpp)

    if not mpp.exists():
        print()
        print("RUNTIME MEDIA MARKER GATE FAILED")
        print(f" - MPP does not exist: {mpp}")
        return 1

    print("mpp_sha256:", sha256_file(mpp))
    print("mpp_size:", mpp.stat().st_size)

    try:
        with zipfile.ZipFile(mpp) as z:
            names = set(z.namelist())

            if "classes.dex" not in names:
                errors.append("MPP missing classes.dex")

            if BOOST_EXTENSION not in names:
                errors.append(f"MPP missing {BOOST_EXTENSION}")
                extension_data = b""
            else:
                extension_data = z.read(BOOST_EXTENSION)

            mpp_data = b""
            for name in sorted(names):
                if name.endswith("/"):
                    continue
                try:
                    mpp_data += z.read(name) + b"\n"
                except Exception as exc:
                    errors.append(f"could not read MPP entry {name}: {exc}")

    except zipfile.BadZipFile:
        print()
        print("RUNTIME MEDIA MARKER GATE FAILED")
        print(f" - MPP is not a valid zip file: {mpp}")
        return 1

    extension_markers = EXTENSION_REQUIRED_MARKERS + args.extension_marker
    mpp_markers = MPP_WIDE_REQUIRED_MARKERS + args.mpp_marker

    print()
    print("===== extension-required markers =====")
    for marker in extension_markers:
        found = marker.encode("utf-8") in extension_data
        print(("OK      " if found else "MISSING ") + marker)
        if not found:
            errors.append(f"{BOOST_EXTENSION} missing marker: {marker!r}")

    print()
    print("===== MPP-wide required markers =====")
    for marker in mpp_markers:
        found = marker.encode("utf-8") in mpp_data
        print(("OK      " if found else "MISSING ") + marker)
        if not found:
            errors.append(f"MPP missing marker anywhere: {marker!r}")

    if errors:
        print()
        print("RUNTIME MEDIA MARKER GATE FAILED")
        for error in errors:
            print(f" - {error}")
        return 1

    print()
    print("RUNTIME MEDIA MARKER GATE OK")
    print("BOOST_RUNTIME_MEDIA_EXTENSION_MARKERS=OK")
    print("BOOST_RUNTIME_MEDIA_MPP_WIDE_MARKERS=OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
