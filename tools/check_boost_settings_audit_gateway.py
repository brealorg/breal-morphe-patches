#!/usr/bin/env python3
"""Validate the exact Boost settings audit gateway manifest component."""

from __future__ import annotations

import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path


DEFAULT_ACTIVITY = (
    "com.rubenmayayo.reddit.ui.preferences.v2.SettingsActivityCompat"
)
DEFAULT_PERMISSION = "android.permission.DUMP"
ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"


def _state_from_xml(
    path: Path, activity_name: str, permission: str
) -> tuple[int, bool, bool]:
    root = ET.parse(path).getroot()
    android_name = f"{{{ANDROID_NAMESPACE}}}name"
    android_exported = f"{{{ANDROID_NAMESPACE}}}exported"
    android_permission = f"{{{ANDROID_NAMESPACE}}}permission"
    matches = [
        node
        for node in root.iter("activity")
        if node.attrib.get(android_name) == activity_name
    ]
    if len(matches) != 1:
        return len(matches), False, False
    activity = matches[0]
    return (
        1,
        activity.attrib.get(android_exported) == "true",
        activity.attrib.get(android_permission) == permission,
    )


def _activity_attribute_blocks(lines: list[str]) -> list[list[str]]:
    blocks: list[list[str]] = []
    for index, line in enumerate(lines):
        stripped = line.lstrip()
        if not (stripped == "E: activity" or stripped.startswith("E: activity ")):
            continue
        indent = len(line) - len(stripped)
        attributes: list[str] = []
        for candidate in lines[index + 1 :]:
            candidate_stripped = candidate.lstrip()
            candidate_indent = len(candidate) - len(candidate_stripped)
            if candidate_stripped.startswith("E:"):
                break
            if candidate_indent <= indent:
                break
            if candidate_stripped.startswith("A:"):
                attributes.append(candidate_stripped)
        blocks.append(attributes)
    return blocks


def _state_from_xmltree(
    path: Path, activity_name: str, permission: str
) -> tuple[int, bool, bool]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    name_marker = f'"{activity_name}"'
    matches = [
        attributes
        for attributes in _activity_attribute_blocks(lines)
        if any(
            line.startswith("A: android:name") and name_marker in line
            for line in attributes
        )
    ]
    if len(matches) != 1:
        return len(matches), False, False

    attributes = matches[0]
    exported_true = any(
        line.startswith("A: android:exported")
        and re.search(r"\(type 0x12\)0xffffffff(?:\s|$)", line)
        for line in attributes
    )
    permission_marker = f'"{permission}"'
    dump_protected = any(
        line.startswith("A: android:permission") and permission_marker in line
        for line in attributes
    )
    return 1, exported_true, dump_protected


def inspect_gateway(
    path: Path, activity_name: str, permission: str
) -> tuple[int, bool, bool]:
    first_non_space = path.read_text(
        encoding="utf-8", errors="replace"
    ).lstrip()[:1]
    if first_non_space == "<":
        return _state_from_xml(path, activity_name, permission)
    return _state_from_xmltree(path, activity_name, permission)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--expect", required=True, choices=("present", "absent"))
    parser.add_argument("--activity", default=DEFAULT_ACTIVITY)
    parser.add_argument("--permission", default=DEFAULT_PERMISSION)
    args = parser.parse_args()

    if not args.manifest.is_file():
        parser.error(f"manifest does not exist: {args.manifest}")

    count, exported_true, dump_protected = inspect_gateway(
        args.manifest, args.activity, args.permission
    )
    print(f"SETTINGS_ACTIVITY_BLOCK_COUNT={count}")
    if count != 1:
        print("SETTINGS_AUDIT_GATEWAY=INVALID_ACTIVITY_COUNT")
        return 1

    present = exported_true and dump_protected
    partial = exported_true != dump_protected
    print(f"SETTINGS_ACTIVITY_EXPORTED_TRUE={int(exported_true)}")
    print(f"SETTINGS_ACTIVITY_DUMP_PROTECTED={int(dump_protected)}")
    print(f"SETTINGS_AUDIT_GATEWAY={'PRESENT' if present else 'ABSENT'}")

    expected_present = args.expect == "present"
    if partial or present != expected_present:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
