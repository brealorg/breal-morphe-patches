#!/usr/bin/env python3
"""Contract checker for Morphe Issue #121 Settings blueprint v3."""

from __future__ import annotations

import hashlib
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

EXPECTED_SHA256 = "039a12c61443b584a65a9ee5fcac7c59e786f1d94b3ae042d60f2b9d89cbf832"
EXPECTED_SUMMARY = {
    "static_blueprint_item_count": 250,
    "root_group_count": 7,
    "task_page_count": 24,
    "intermediate_page_count": 5,
    "leaf_section_count": 71,
    "screen_graph_node_count": 107,
    "screen_graph_edge_count": 100,
    "explicit_mapping_count": 13,
    "semantic_move_count": 28,
    "leaf_control_violation_count": 0,
    "task_child_violation_count": 0,
    "intermediate_child_violation_count": 0,
    "forbidden_label_count": 0,
    "destination_only_page_count": 3,
}
EXPECTED_ROOT_ORDER = [
    "Morphe",
    "Appearance",
    "Reading & interaction",
    "Navigation",
    "Media",
    "Notifications & account",
    "Data & app",
]
EXPECTED_PLACEMENTS = {
    "pref_ask_exit": ("Navigation", "Navigation & gestures", "Back & exit"),
    "pref_autoplay_cards": ("Media", "Playback & autoplay", "Autoplay"),
    "pref_browser": ("Media", "Links & browser", "Browser"),
    "pref_double_exit": ("Navigation", "Navigation & gestures", "Back & exit"),
    "pref_drafts": ("Reading & interaction", "Composing & drafts", "Drafts"),
    "pref_edit_subscriptions": (
        "Reading & interaction",
        "Feeds & subscriptions",
        "Manage subscriptions",
    ),
    "pref_imgur_uploads": (
        "Reading & interaction",
        "Composing & drafts",
        "Uploads",
    ),
    "pref_link_video": ("Media", "Links & browser", "Video links"),
    "pref_manage_drafts": (
        "Reading & interaction",
        "Composing & drafts",
        "Drafts",
    ),
    "pref_split_screen": ("Appearance", "Post layout", "Tablet layout"),
    "pref_upvote_on_save": (
        "Reading & interaction",
        "Posts",
        "Post actions",
    ),
    "pref_use_advanced_editor": (
        "Reading & interaction",
        "Composing & drafts",
        "Editor",
    ),
    "pref_video_audio_start_muted": (
        "Media",
        "Playback & autoplay",
        "Audio",
    ),
}
FORBIDDEN_LABELS = {
    "Additional navigation behavior",
    "Additional link behavior",
    "More options",
    "Misc",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def contract_path() -> Path:
    if len(sys.argv) > 2:
        fail("usage: check_boost_settings_blueprint_v3.py [contract.json]")
    if len(sys.argv) == 2:
        return Path(sys.argv[1]).resolve()
    return (
        Path(__file__).resolve().parent
        / "contracts"
        / "boost-settings-blueprint-v3.json"
    )


def require_dict(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        fail(f"{name} must be an object")
    return value


def require_list(value: Any, name: str) -> list[Any]:
    if not isinstance(value, list):
        fail(f"{name} must be an array")
    return value


path = contract_path()
if not path.is_file():
    fail(f"contract missing: {path}")

raw = path.read_bytes()
actual_sha = hashlib.sha256(raw).hexdigest()
if actual_sha != EXPECTED_SHA256:
    fail(
        "contract sha256 mismatch: "
        f"expected {EXPECTED_SHA256}, got {actual_sha}"
    )

data = require_dict(json.loads(raw.decode("utf-8")), "contract")
if data.get("schema") != 3:
    fail(f"schema must be 3, got {data.get('schema')!r}")
if data.get("issue") != 121:
    fail(f"issue must be 121, got {data.get('issue')!r}")

summary = require_dict(data.get("summary"), "summary")
for key, expected in EXPECTED_SUMMARY.items():
    actual = summary.get(key)
    if actual != expected:
        fail(f"summary.{key}: expected {expected!r}, got {actual!r}")

root_order = require_list(data.get("root_order"), "root_order")
if root_order != EXPECTED_ROOT_ORDER:
    fail(f"root_order mismatch: {root_order!r}")

items = require_list(data.get("items"), "items")
if len(items) != EXPECTED_SUMMARY["static_blueprint_item_count"]:
    fail(f"item count mismatch: {len(items)}")

keys: list[str] = []
item_by_key: dict[str, dict[str, Any]] = {}
for index, raw_item in enumerate(items):
    item = require_dict(raw_item, f"items[{index}]")
    key = item.get("key")
    if not isinstance(key, str) or not key:
        fail(f"items[{index}].key must be a non-empty string")
    keys.append(key)
    item_by_key[key] = item

    root = item.get("root")
    page = item.get("page")
    subscreen = item.get("subscreen")
    screen_path = item.get("screen_path")
    help_treatment = item.get("help_treatment")

    if root not in EXPECTED_ROOT_ORDER:
        fail(f"{key}: unknown root {root!r}")
    if not isinstance(page, str) or not page:
        fail(f"{key}: page missing")
    if not isinstance(subscreen, str) or not subscreen:
        fail(f"{key}: subscreen missing")
    if not isinstance(screen_path, list) or len(screen_path) < 3:
        fail(f"{key}: screen_path must contain root, page, and leaf")
    if screen_path[0] != root or screen_path[1] != page:
        fail(f"{key}: screen_path does not match root/page")
    if not isinstance(help_treatment, str) or not help_treatment:
        fail(f"{key}: help_treatment missing")

    labels = {root, page, subscreen, *screen_path}
    forbidden = sorted(labels & FORBIDDEN_LABELS)
    if forbidden:
        fail(f"{key}: forbidden labels remain: {forbidden}")

duplicates = sorted(
    key for key, count in Counter(keys).items() if count != 1
)
if duplicates:
    fail(f"duplicate item keys: {duplicates}")

for key, expected in EXPECTED_PLACEMENTS.items():
    item = item_by_key.get(key)
    if item is None:
        fail(f"required key missing: {key}")
    actual = (item["root"], item["page"], item["subscreen"])
    if actual != expected:
        fail(f"{key}: expected placement {expected}, got {actual}")

explicit_mapping = require_dict(
    data.get("explicit_key_mapping"),
    "explicit_key_mapping",
)
if set(explicit_mapping) != set(EXPECTED_PLACEMENTS):
    fail(
        "explicit mapping keys mismatch: "
        f"{sorted(explicit_mapping)}"
    )

screens = require_list(data.get("screens"), "screens")
edges = require_list(data.get("graph_edges"), "graph_edges")
if len(screens) != EXPECTED_SUMMARY["screen_graph_node_count"]:
    fail(f"screen count mismatch: {len(screens)}")
if len(edges) != EXPECTED_SUMMARY["screen_graph_edge_count"]:
    fail(f"edge count mismatch: {len(edges)}")

screen_paths = []
for index, raw_screen in enumerate(screens):
    screen = require_dict(raw_screen, f"screens[{index}]")
    path_value = screen.get("path")
    if not isinstance(path_value, str) or not path_value:
        fail(f"screens[{index}].path missing")
    screen_paths.append(path_value)

if len(screen_paths) != len(set(screen_paths)):
    fail("duplicate screen graph paths")

print(f"CONTRACT={path}")
print(f"CONTRACT_SHA256={actual_sha}")
print(f"STATIC_BLUEPRINT_ITEM_COUNT={len(items)}")
print(f"ROOT_GROUP_COUNT={len(root_order)}")
print(f"SCREEN_GRAPH_NODE_COUNT={len(screens)}")
print(f"SCREEN_GRAPH_EDGE_COUNT={len(edges)}")
print(f"EXPLICIT_MAPPING_COUNT={len(explicit_mapping)}")
print("DUPLICATE_ITEM_KEY_COUNT=0")
print("FORBIDDEN_LABEL_COUNT=0")
print("RESULT=MORPHE_ISSUE121_SETTINGS_BLUEPRINT_V3_CONTRACT_PASS")
