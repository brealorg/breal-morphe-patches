#!/usr/bin/env python3
import json
import sys
from pathlib import Path

KNOWN_PACKAGE_LABELS = {
    "com.rubenmayayo.reddit": "Boost for Reddit",
    "com.rubenmayayo.reddit.dev": "Boost for Reddit Dev",
    "com.rubenmayayo.lemmy": "Boost for Lemmy",
    "com.imgur.mobile": "Imgur",
    "com.google.android.youtube": "YouTube",
    "app.morphe.android.youtube": "Morphe YouTube",
    "com.laurencedawson.reddit_sync": "Sync for Reddit",
    "com.laurencedawson.reddit_sync.dev": "Sync for Reddit Dev",
    "com.laurencedawson.reddit_sync.pro": "Sync for Reddit Pro",
    "o.o.joey": "Joey for Reddit",
}

def package_label(package_name: str, explicit: str = "") -> str:
    return explicit or KNOWN_PACKAGE_LABELS.get(package_name, package_name or "Unknown package")

def normalize_targets(value):
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]

def normalize_compatible_packages(value):
    if not value:
        return []

    out = []

    if isinstance(value, dict):
        for package_name, targets in value.items():
            out.append({
                "packageName": str(package_name),
                "name": package_label(str(package_name)),
                "targets": normalize_targets(targets),
            })
        return out

    if isinstance(value, list):
        for item in value:
            if isinstance(item, str):
                out.append({
                    "packageName": item,
                    "name": package_label(item),
                    "targets": [],
                })
            elif isinstance(item, dict):
                package_name = (
                    item.get("packageName")
                    or item.get("id")
                    or item.get("package")
                    or ""
                )
                explicit_name = item.get("name") or item.get("displayName") or item.get("appName") or ""
                targets = item.get("targets") or item.get("versions") or []
                out.append({
                    "packageName": str(package_name),
                    "name": package_label(str(package_name), str(explicit_name)),
                    "targets": normalize_targets(targets),
                })
            else:
                package_name = str(item)
                out.append({
                    "packageName": package_name,
                    "name": package_label(package_name),
                    "targets": [],
                })
        return out

    package_name = str(value)
    return [{
        "packageName": package_name,
        "name": package_label(package_name),
        "targets": [],
    }]

def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("patches-list.json")
    data = json.loads(path.read_text(encoding="utf-8"))

    patches = data.get("patches")
    if not isinstance(patches, list):
        raise SystemExit("FAIL: patches-list.json has no valid patches array")

    changed = 0
    for patch in patches:
        if not isinstance(patch, dict):
            continue
        before = patch.get("compatiblePackages")
        after = normalize_compatible_packages(before)
        if before != after:
            patch["compatiblePackages"] = after
            changed += 1

    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"normalized_compatiblePackages={changed}")
    print("RESULT=PATCHES_LIST_COMPATIBLE_PACKAGES_NORMALIZED")

if __name__ == "__main__":
    main()
