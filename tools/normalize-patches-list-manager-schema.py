#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

APP_META: dict[str, dict[str, Any]] = {
    "com.rubenmayayo.reddit": {
        "name": "Boost for Reddit",
        "description": None,
        "apkFileType": "APK_REQUIRED",
        "appIconColor": "#FF4500",
        "signatures": None,
        "minSdk": 21,
    },
    "com.rubenmayayo.reddit.dev": {
        "name": "Boost for Reddit Dev",
        "description": None,
        "apkFileType": "APK_REQUIRED",
        "appIconColor": "#FF4500",
        "signatures": None,
        "minSdk": 21,
    },
    "com.rubenmayayo.lemmy": {
        "name": "Boost for Lemmy",
        "description": None,
        "apkFileType": "APK_REQUIRED",
        "appIconColor": "#00AEEF",
        "signatures": None,
        "minSdk": 21,
    },
    "com.imgur.mobile": {
        "name": "Imgur",
        "description": None,
        "apkFileType": "APK_REQUIRED",
        "appIconColor": "#1BB76E",
        "signatures": None,
        "minSdk": 21,
    },
}

PATCH_ORDER = ["name", "description", "default", "dependencies", "compatiblePackages", "options"]
PACKAGE_ORDER = [
    "packageName",
    "name",
    "description",
    "apkFileType",
    "appIconColor",
    "signatures",
    "targets",
]
TARGET_ORDER = ["version", "versionCodes", "isExperimental", "minSdk", "description"]
OPTION_ORDER = ["key", "title", "description", "required", "type", "default", "values"]


def ordered(obj: dict[str, Any], order: list[str]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for key in order:
        if key in obj:
            out[key] = obj[key]
    for key in sorted(obj):
        if key not in out:
            out[key] = obj[key]
    return out


def normalize_target(raw: Any, *, min_sdk: int) -> dict[str, Any]:
    if isinstance(raw, dict):
        target = dict(raw)
    else:
        target = {"version": str(raw)}

    target.setdefault("version", "")
    target.setdefault("versionCodes", None)
    target.setdefault("isExperimental", False)
    target.setdefault("minSdk", min_sdk)
    target.setdefault("description", None)

    if target["version"] is None:
        target["version"] = ""
    else:
        target["version"] = str(target["version"])

    if target["versionCodes"] is not None and not isinstance(target["versionCodes"], list):
        target["versionCodes"] = [target["versionCodes"]]

    target["isExperimental"] = bool(target["isExperimental"])

    try:
        target["minSdk"] = int(target["minSdk"])
    except Exception:
        target["minSdk"] = min_sdk

    return ordered(target, TARGET_ORDER)


def normalize_package(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raw = {"packageName": str(raw)}

    pkg = dict(raw)
    package_name = str(pkg.get("packageName") or "")
    meta = APP_META.get(package_name, {})

    pkg["packageName"] = package_name
    pkg["name"] = pkg.get("name") or meta.get("name") or package_name
    pkg.setdefault("description", meta.get("description"))
    pkg.setdefault("apkFileType", meta.get("apkFileType", "APK_REQUIRED"))
    pkg.setdefault("appIconColor", meta.get("appIconColor", "#607D8B"))
    pkg.setdefault("signatures", meta.get("signatures"))

    min_sdk = int(meta.get("minSdk", 21))
    targets = pkg.get("targets")
    if targets is None:
        targets = []
    elif not isinstance(targets, list):
        targets = [targets]

    pkg["targets"] = [normalize_target(target, min_sdk=min_sdk) for target in targets]

    return ordered(pkg, PACKAGE_ORDER)


def normalize_option(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raw = {"key": str(raw)}

    opt = dict(raw)
    opt.setdefault("key", "")
    opt.setdefault("title", opt.get("key") or "")
    opt.setdefault("description", None)
    opt.setdefault("required", False)
    opt.setdefault("type", "kotlin.String")
    opt.setdefault("default", None)
    opt.setdefault("values", None)
    opt["required"] = bool(opt["required"])

    return ordered(opt, OPTION_ORDER)


def normalize_patch(raw: Any) -> dict[str, Any]:
    if not isinstance(raw, dict):
        raw = {"name": str(raw)}

    patch = dict(raw)

    if "default" not in patch:
        patch["default"] = bool(patch.get("use", False))

    patch.pop("use", None)

    patch.setdefault("name", "")
    patch.setdefault("description", None)

    deps = patch.get("dependencies")
    if deps is None:
        deps = []
    elif not isinstance(deps, list):
        deps = [deps]
    patch["dependencies"] = [str(dep) for dep in deps]

    cps = patch.get("compatiblePackages")
    if cps is not None:
        if not isinstance(cps, list):
            cps = [cps]
        patch["compatiblePackages"] = [normalize_package(pkg) for pkg in cps]
    else:
        patch["compatiblePackages"] = None

    opts = patch.get("options")
    if opts is None:
        opts = []
    elif not isinstance(opts, list):
        opts = [opts]
    patch["options"] = [normalize_option(opt) for opt in opts]

    return ordered(patch, PATCH_ORDER)


def normalize_file(path: Path, *, version: str | None = None) -> tuple[dict[str, Any], dict[str, int]]:
    data = json.loads(path.read_text())
    if version is not None:
        data["version"] = version

    patches = data.get("patches")
    if not isinstance(patches, list):
        patches = []

    normalized = [normalize_patch(patch) for patch in patches]
    data["patches"] = normalized

    stats = {
        "patches": len(normalized),
        "packages": sum(len(p.get("compatiblePackages") or []) for p in normalized),
        "targets": sum(
            len(pkg.get("targets") or [])
            for patch in normalized
            for pkg in (patch.get("compatiblePackages") or [])
        ),
        "options": sum(len(p.get("options") or []) for p in normalized),
    }

    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    return data, stats


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default="patches-list.json")
    parser.add_argument("--version")
    args = parser.parse_args()

    data, stats = normalize_file(Path(args.path), version=args.version)

    print(f"version={data.get('version')!r}")
    for key, value in stats.items():
        print(f"{key}={value}")
    print("RESULT=PATCHES_LIST_MANAGER_SCHEMA_NORMALIZED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
