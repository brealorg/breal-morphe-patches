#!/usr/bin/env python3
"""
Generate README patch list from patches-list.json.

This repo-compatible generator supports:
- compatiblePackages missing/empty
- compatiblePackages as list[str]
- compatiblePackages as list[dict]
- options as list[str] or list[dict]

Usage:
  python3 .github/scripts/generate_patches_readme.py owner/repo branch [patches-list.json] [README.md]
"""
import json
import re
import sys
from pathlib import Path

AUTO_EXPAND_THRESHOLD = 20
KNOWN_PACKAGE_LABELS = {
    "com.rubenmayayo.reddit": "Boost for Reddit",
    "com.rubenmayayo.reddit.dev": "Boost for Reddit Dev",
    "com.rubenmayayo.lemmy": "Boost for Lemmy",
    "com.imgur.mobile": "Imgur",
    "com.google.android.youtube": "YouTube",
    "app.morphe.android.youtube": "Morphe YouTube",
}

START_PATTERN = r"<!--\s*PATCHES_START(?:_EXPANDED)?\s*-->"
END_MARKER = "<!-- PATCHES_END -->"

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)

if len(sys.argv) < 3:
    fail("Usage: generate_patches_readme.py owner/repo branch [patches-list.json] [README.md]")

repo_full = sys.argv[1]
branch = sys.argv[2]
json_path = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("patches-list.json")
readme_path = Path(sys.argv[4]) if len(sys.argv) > 4 else Path("README.md")

if "/" not in repo_full:
    fail(f"Invalid repo format: {repo_full!r}; expected owner/repo")
if not json_path.exists():
    fail(f"Missing {json_path}")
if not readme_path.exists():
    fail(f"Missing {readme_path}")

data = json.loads(json_path.read_text(encoding="utf-8"))
patches = data.get("patches") or []
raw_ver = str(data.get("version") or "").strip()

def anchor(name: str) -> str:
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", name.lower())).strip("-")

def safe_text(value) -> str:
    if value is None:
        return ""
    return str(value).replace("\n", "<br>")

def package_label(package_name: str, explicit: str = "") -> str:
    return explicit or KNOWN_PACKAGE_LABELS.get(package_name, package_name or "Unknown package")

def normalize_compatible_packages(value):
    if not value:
        return []

    out = []
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
                or item.get("name")
                or "unknown"
            )
            explicit_name = item.get("name") if item.get("packageName") else ""
            out.append({
                "packageName": package_name,
                "name": package_label(package_name, explicit_name or ""),
                "targets": item.get("targets") or item.get("versions") or [],
            })
        else:
            package_name = str(item)
            out.append({
                "packageName": package_name,
                "name": package_label(package_name),
                "targets": [],
            })
    return out

def option_titles(options) -> str:
    if not options:
        return ""

    titles = []
    for option in options:
        if isinstance(option, dict):
            titles.append(option.get("title") or option.get("key") or option.get("name") or "")
        else:
            titles.append(str(option))

    return "<br>".join(f"• {safe_text(title)}" for title in titles if title)

def patches_table(patch_items) -> str:
    rows = [
        "| Patch | Description | Options |",
        "|---|---|---|",
    ]

    for patch in sorted(patch_items, key=lambda x: str(x.get("name") or "").lower()):
        name = safe_text(patch.get("name") or "Unnamed patch")
        description = safe_text(patch.get("description") or "")
        options = option_titles(patch.get("options") or [])
        rows.append(f"| [{name}](#{anchor(name)}) | {description} | {options} |")

    return "\n".join(rows)

def versions_table(targets) -> str:
    if not targets:
        return ""

    versions = []
    descriptions = []
    for target in targets:
        if isinstance(target, dict):
            version_value = target.get("version")
            if version_value is None:
                continue
            label = f"⚠️ {version_value}" if target.get("isExperimental") else str(version_value)
            versions.append(label)
            descriptions.append(safe_text(target.get("description") or ""))
        else:
            versions.append(str(target))
            descriptions.append("")

    if not versions:
        return ""

    rows = [
        "| " + " | ".join(versions) + " |",
        "| " + " | ".join(":---:" for _ in versions) + " |",
    ]
    if any(descriptions):
        rows.append("| " + " | ".join(descriptions) + " |")
    return "\n".join(rows)

def details_block(label: str, patch_items, targets, expanded: bool) -> str:
    count = len(patch_items)
    noun = "patch" if count == 1 else "patches"
    open_attr = " open" if expanded else ""
    versions = versions_table(targets)
    versions_section = f"\n\n**Supported versions:**\n\n{versions}" if versions else ""

    return (
        f"<details{open_attr}>\n"
        f"<summary><strong>{safe_text(label)}</strong> • {count} {noun}</summary>\n\n"
        f"{versions_section}\n\n"
        f"{patches_table(patch_items)}\n\n"
        f"</details>"
    )

by_package = {}
universal = {}

for patch in patches:
    name = patch.get("name") or "Unnamed patch"
    compatible_packages = normalize_compatible_packages(patch.get("compatiblePackages"))

    if not compatible_packages:
        universal.setdefault(name, patch)
        continue

    for compatible_package in compatible_packages:
        package_name = compatible_package["packageName"]
        if package_name not in by_package:
            by_package[package_name] = {
                "name": compatible_package["name"],
                "targets": compatible_package.get("targets") or [],
                "patches": {},
            }

        by_package[package_name]["patches"].setdefault(name, patch)

        if not by_package[package_name]["targets"] and compatible_package.get("targets"):
            by_package[package_name]["targets"] = compatible_package.get("targets")

package_entries = sum(len(entry["patches"]) for entry in by_package.values()) + len(universal)
unique_patch_names = {str(patch.get("name") or "Unnamed patch") for patch in patches}
unique_total = len(unique_patch_names)

readme = readme_path.read_text(encoding="utf-8")
marker_match = re.search(START_PATTERN, readme)
if not marker_match or END_MARKER not in readme:
    fail(f"Markers PATCHES_START/PATCHES_END not found in {readme_path}")

actual_start = marker_match.group(0)
expanded = package_entries <= AUTO_EXPAND_THRESHOLD or "EXPANDED" in actual_start

lines = [
    f"> **Patch source version:** `{raw_ver or 'unknown'}` • `{branch}` • {unique_total} unique patches • {package_entries} package entries",
    "",
]

for _package_name, entry in by_package.items():
    lines.append(details_block(entry["name"], list(entry["patches"].values()), entry["targets"], expanded))
    lines.append("")

if universal:
    lines.append(details_block("Universal", list(universal.values()), [], expanded))
    lines.append("")

generated = "\n".join(lines).rstrip() + "\n"
new_readme = re.sub(
    rf"{START_PATTERN}.*?{re.escape(END_MARKER)}",
    f"{actual_start}\n{generated}{END_MARKER}",
    readme,
    flags=re.DOTALL,
)

readme_path.write_text(new_readme, encoding="utf-8")
print(
    f"Injected patches section into {readme_path} "
    f"({raw_ver}, branch={branch}, unique={unique_total}, package_entries={package_entries}, expanded={expanded})"
)
