#!/usr/bin/env python3
"""Integrity checker for the semantically corrected Morphe Settings V5 target."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

EXPECTED_BLUEPRINT_SHA256 = "039a12c61443b584a65a9ee5fcac7c59e786f1d94b3ae042d60f2b9d89cbf832"
EXPECTED_NAVIGATION_SHA256 = "0a1be51e15f3e914d78ebe04fd832895f5d0fe8f326b78048c8474fce282e624"
EXPECTED_PREVIOUS_CONTRACT_SHA256 = (
    "30745994dd0aec9f8c5d9b118dfaaee26d4522c5ca6d55a01da27a97961896f7"
)
EXPECTED_CONTRACT_SHA256 = (
    "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682"
)

EXPECTED_ROOT_ORDER = [
    "Morphe",
    "Appearance",
    "Reading & interaction",
    "Navigation",
    "Media",
    "Notifications & account",
    "Data & app",
]
EXPECTED_ROLE_COUNTS = Counter({
    "root_group": 7,
    "task_page": 22,
    "intermediate_page": 5,
    "leaf_section": 71,
})
EXPECTED_ACTIVATION_GATE = {
    "activation_policy": "parallel_hidden_until_complete",
    "allowed_classic_fallbacks": 1,
    "allowed_external_action_destinations": 1,
    "allowed_legacy_fragments": 0,
    "allowed_legacy_xml_routes": 0,
    "allowed_placeholder_pages": 0,
    "allowed_title_route_mismatches": 0,
    "allowed_unmapped_keys": 0,
    "canonical_item_accounting": 248,
    "classic_fallback_location": "root_only",
    "external_action_destination_allowlist": ["BackupActivity"],
    "partial_completion_must_not_replace_current_visible_tree": True,
    "required_complete_screen_nodes": 105,
    "required_complete_visible_items": 247,
    "root_overview_shell_count": 1,
    "root_overview_shell_must_be_complete": True,
    "target_screen_edge_count": 98,
    "target_screen_node_count": 105,
    "visible_item_target": 247,
    "withheld_controls_must_have_evidence": True,
    "withheld_item_target": 1,
}

EXPECTED_CORRECTIONS = {
    "buy_pro": (
        "Appearance / Community header / Community header",
        "Data & app / About & support / About Boost",
    ),
    "remove_ads": (
        "Appearance / Community header / Community header",
        "Data & app / About & support / About Boost",
    ),
    "pref_app_icon": (
        "Appearance / Theme & colors / Theme",
        "Appearance / Theme & colors / Personalization",
    ),
    "pref_load_readability": (
        "Appearance / Post layout / Swipe",
        "Media / Links & browser / Link handling",
    ),
    "pref_lock_sidebar": (
        "Appearance / Post layout / Swipe",
        "Navigation / Navigation drawer / Drawer behavior",
    ),
}
EXPECTED_ALIAS_REMOVALS = {
    "action:appearance:app_icon": "pref_app_icon",
    "action:post_layout:manage_saved_views": "pref_view_per_sub",
}
REMOVED_SCREEN_PATHS = {
    "Appearance / Post layout / Swipe",
    "Morphe / Patch features",
}
STABLE_ITEM_FIELDS = (
    "title",
    "source_type",
    "logical_kind",
    "storage_type",
    "default_value",
    "dependency",
    "help_treatment",
    "proposed_summary",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        type=Path,
        help="Repository root. Defaults to the parent of tools/.",
    )
    parser.add_argument(
        "--contract",
        type=Path,
        help="Corrected V5 contract path. Defaults to the installed v2 contract.",
    )
    parser.add_argument(
        "--previous-contract",
        type=Path,
        help="Superseded V5 v1 contract path.",
    )
    return parser.parse_args()


def fail(message: str) -> None:
    raise AssertionError(message)


def load_bound(
    path: Path,
    expected_sha: str,
    label: str,
) -> tuple[dict[str, Any], str]:
    if not path.is_file():
        fail(f"{label} missing: {path}")
    raw = path.read_bytes()
    actual = hashlib.sha256(raw).hexdigest()
    if actual != expected_sha:
        fail(f"{label} sha mismatch: expected {expected_sha}, got {actual}")
    data = json.loads(raw.decode("utf-8"))
    if not isinstance(data, dict):
        fail(f"{label} must be an object")
    return data, actual


def screen_path_for_navigation_page(page: dict[str, Any]) -> list[str]:
    parent = page.get("parent")
    title = page.get("title")
    if parent == "Navigation & gestures":
        return ["Navigation", title]
    if parent == "Navigation drawer":
        return [
            "Navigation",
            "Navigation drawer",
            title,
        ]
    fail(f"unexpected Navigation parent: {parent!r}")


args = parse_args()
script_root = Path(__file__).resolve().parents[1]
root = (args.root or script_root).resolve()
blueprint_path = root / "tools/contracts/boost-settings-blueprint-v3.json"
navigation_path = root / "tools/contracts/boost-settings-navigation-phase2-v1.json"
previous_contract_path = (
    args.previous_contract
    or root / "tools/contracts/boost-settings-v5-completeness-v1.json"
).resolve()
contract_path = (
    args.contract
    or root / "tools/contracts/boost-settings-v5-completeness-v2.json"
).resolve()

blueprint, blueprint_sha = load_bound(
    blueprint_path,
    EXPECTED_BLUEPRINT_SHA256,
    "blueprint",
)
navigation, navigation_sha = load_bound(
    navigation_path,
    EXPECTED_NAVIGATION_SHA256,
    "navigation contract",
)
previous_contract, previous_sha = load_bound(
    previous_contract_path,
    EXPECTED_PREVIOUS_CONTRACT_SHA256,
    "superseded V5 v1 contract",
)
contract, contract_sha = load_bound(
    contract_path,
    EXPECTED_CONTRACT_SHA256,
    "corrected V5 v2 contract",
)

if blueprint.get("schema") != 3 or blueprint.get("issue") != 121:
    fail("unexpected blueprint identity")
if navigation.get("schema") != 1 or navigation.get("issue") != 121:
    fail("unexpected Navigation contract identity")
if previous_contract.get("schema") != 1 or previous_contract.get("issue") != 121:
    fail("unexpected superseded V5 v1 contract identity")

if contract.get("schema") != 1 or contract.get("issue") != 121:
    fail("unexpected corrected V5 contract identity")
if contract.get("target") != (
    "Morphe Settings V5 semantically corrected complete tree"
):
    fail("unexpected corrected V5 target name")
if contract.get("root_order") != EXPECTED_ROOT_ORDER:
    fail("V5 root order mismatch")
if contract.get("canonical_item_count") != 248:
    fail("canonical logical-item target is not 248")
if contract.get("visible_item_target") != 247:
    fail("visible item target is not 247")
if contract.get("withheld_item_target") != 1:
    fail("withheld item target is not 1")
if contract.get("screen_node_count") != 105:
    fail("screen node target is not 105")
if contract.get("screen_edge_count") != 98:
    fail("screen edge target is not 98")
if contract.get("morphe_configurable_feature_reference_count") != 12:
    fail("Morphe configurable-feature reference count is not 12")
if contract.get("root_overview_shell") != {
    "classic_fallback_location": "root_only",
    "count": 1,
    "status_required": "COMPLETE",
}:
    fail("root overview shell policy mismatch")
if contract.get("activation_gate") != EXPECTED_ACTIVATION_GATE:
    fail("activation gate differs from the semantically corrected target")

revision = contract.get("semantic_revision")
if not isinstance(revision, dict) or revision.get("schema") != 2:
    fail("semantic revision metadata missing")
if revision.get("revision") != "V4":
    fail("semantic revision identifier is not V4")
if revision.get("supersedes_contract_sha256") != ("627f8850c2d8051fe03b3a07accb512f5392d6d54b1c708bf25e328e0112cac3"):
    fail("semantic V4 does not supersede the locked semantic V3 contract")
if revision.get("original_v1_contract_sha256") != previous_sha:
    fail("semantic V4 does not preserve the original V1 lineage")
if revision.get("principles") != [
    "task-based placement",
    "one visible control per user action",
    "route aliases are graph metadata rather than duplicate controls",
    "empty leaf screens are removed",
    "single-task root wrappers are flattened into their root page",
]:
    fail("semantic revision principles mismatch")

flattening = revision.get("hierarchy_flattening")
if flattening != [
    {
        "action": "REMOVE_REDUNDANT_SINGLE_TASK_WRAPPER",
        "from_path": "Navigation / Navigation & gestures",
        "reason": (
            "The Navigation root contained only one task destination, adding a "
            "click without adding information."
        ),
        "result": (
            "The Navigation root renders the Navigation & gestures destinations "
            "directly."
        ),
    },
    {
        "action": "REMOVE_REDUNDANT_SINGLE_TASK_WRAPPER",
        "from_path": "Morphe / Patch features",
        "reason": (
            "The Morphe root contained only one task destination, adding a click "
            "without adding information and implying an incomplete patch catalog."
        ),
        "result": (
            "The Morphe root renders the 12 configurable Morphe feature "
            "references directly."
        ),
    },
]:
    fail("semantic V4 hierarchy-flattening metadata mismatch")

corrections = revision.get("classification_corrections")
if not isinstance(corrections, list) or len(corrections) != 5:
    fail("semantic revision must contain five placement corrections")
correction_map: dict[str, tuple[str, str]] = {}
for correction in corrections:
    if correction.get("action") != "MOVE":
        fail("semantic correction action must be MOVE")
    key = correction.get("key")
    pair = (correction.get("from_path"), correction.get("to_path"))
    if not isinstance(key, str) or not all(isinstance(value, str) for value in pair):
        fail("malformed semantic correction")
    correction_map[key] = pair
    if not correction.get("reason") or not correction.get("title"):
        fail(f"{key}: semantic correction lacks reason/title")
if correction_map != EXPECTED_CORRECTIONS:
    fail("semantic placement corrections differ from the audited proposal")

alias_removals = revision.get("route_alias_removals")
if not isinstance(alias_removals, list) or len(alias_removals) != 2:
    fail("semantic revision must contain two route-alias removals")
alias_map: dict[str, str] = {}
for removal in alias_removals:
    key = removal.get("key")
    replacement = removal.get("replacement_key")
    if not isinstance(key, str) or not isinstance(replacement, str):
        fail("malformed route-alias removal")
    if not removal.get("reason") or not removal.get("title"):
        fail(f"{key}: route-alias removal lacks reason/title")
    alias_map[key] = replacement
if alias_map != EXPECTED_ALIAS_REMOVALS:
    fail("route-alias removal set differs from the audited proposal")

withheld = contract.get("withheld_controls")
if not isinstance(withheld, list) or len(withheld) != 1:
    fail("exactly one withheld control is required")
if withheld[0].get("key") != "pref_drawer_show_friends":
    fail("unexpected withheld key")
if withheld[0].get("target_path") != (
    "Navigation / Navigation drawer / Account & tools"
):
    fail("withheld Friends target path mismatch")
if not withheld[0].get("reason") or not withheld[0].get("required_resolution"):
    fail("withheld Friends evidence policy is incomplete")

screens = contract.get("screens")
edges = contract.get("graph_edges")
items = contract.get("items")
if not isinstance(screens, list) or len(screens) != 105:
    fail("V5 screen list must contain 105 entries")
if not isinstance(edges, list) or len(edges) != 98:
    fail("V5 edge list must contain 98 entries")
if not isinstance(items, list) or len(items) != 248:
    fail("V5 item list must contain 248 entries")

screen_by_path: dict[str, dict[str, Any]] = {}
for index, screen in enumerate(screens):
    if not isinstance(screen, dict):
        fail(f"screens[{index}] must be an object")
    path = screen.get("path")
    if not isinstance(path, str) or not path:
        fail(f"screens[{index}].path missing")
    if path in screen_by_path:
        fail(f"duplicate screen path: {path}")
    parts = path.split(" / ")
    if screen.get("root") != parts[0]:
        fail(f"screen root mismatch: {path}")
    if screen.get("title") != parts[-1]:
        fail(f"screen title mismatch: {path}")
    if screen.get("depth") != len(parts) - 1:
        fail(f"screen depth mismatch: {path}")
    if screen.get("root") == "Navigation" and len(parts) > 1:
        expected_task = "Navigation & gestures"
    else:
        expected_task = parts[1] if len(parts) > 1 else ""
    if screen.get("task_page") != expected_task:
        fail(f"screen task-page mismatch: {path}")
    screen_by_path[path] = screen

if Counter(screen["role"] for screen in screens) != EXPECTED_ROLE_COUNTS:
    fail("V5 screen role counts differ from the corrected target")
if set(screen["root"] for screen in screens) != set(EXPECTED_ROOT_ORDER):
    fail("V5 screen roots differ from the locked root set")
for removed_path in REMOVED_SCREEN_PATHS:
    if removed_path in screen_by_path:
        fail(f"semantically empty screen remains: {removed_path}")

edge_pairs: set[tuple[str, str]] = set()
children_by_parent: dict[str, set[str]] = defaultdict(set)
for index, edge in enumerate(edges):
    if not isinstance(edge, dict):
        fail(f"graph_edges[{index}] must be an object")
    parent = edge.get("parent_path")
    child = edge.get("child_path")
    title = edge.get("child_title")
    if parent not in screen_by_path or child not in screen_by_path:
        fail(f"edge references missing screen: {parent!r} -> {child!r}")
    child_parts = child.split(" / ")
    if parent != " / ".join(child_parts[:-1]):
        fail(f"edge is not an immediate parent relation: {parent} -> {child}")
    if title != child_parts[-1]:
        fail(f"edge child title mismatch: {parent} -> {child}")
    pair = (parent, child)
    if pair in edge_pairs:
        fail(f"duplicate edge: {pair}")
    edge_pairs.add(pair)
    children_by_parent[parent].add(child)

expected_edges = {
    (" / ".join(path.split(" / ")[:-1]), path)
    for path in screen_by_path
    if " / " in path
}
if edge_pairs != expected_edges:
    fail("V5 edge set differs from the path-derived graph")

item_by_key: dict[str, dict[str, Any]] = {}
items_by_path: dict[str, list[str]] = defaultdict(list)
visibility_counts: Counter[str] = Counter()
for index, item in enumerate(items):
    if not isinstance(item, dict):
        fail(f"items[{index}] must be an object")
    key = item.get("key")
    if not isinstance(key, str) or not key:
        fail(f"items[{index}].key missing")
    if key in item_by_key:
        fail(f"duplicate item key: {key}")
    path_value = item.get("screen_path")
    if not isinstance(path_value, list) or len(path_value) < 2:
        fail(f"{key}: invalid screen_path")
    path = " / ".join(path_value)
    if path not in screen_by_path:
        fail(f"{key}: target screen is absent: {path}")
    if item.get("root") != path_value[0]:
        fail(f"{key}: root does not match screen_path")
    expected_page = (
        "Navigation & gestures"
        if path_value[0] == "Navigation"
        else path_value[1]
    )
    if item.get("page") != expected_page:
        fail(f"{key}: page does not match screen_path")
    if item.get("leaf_section") != path_value[-1]:
        fail(f"{key}: leaf_section does not match screen_path")
    if item.get("screen_depth") != len(path_value) - 1:
        fail(f"{key}: screen_depth does not match screen_path")
    visibility = item.get("v5_visibility")
    if visibility not in {"VISIBLE", "WITHHELD_RUNTIME_UNAVAILABLE"}:
        fail(f"{key}: invalid V5 visibility")
    if visibility == "WITHHELD_RUNTIME_UNAVAILABLE":
        if key != "pref_drawer_show_friends":
            fail(f"unexpected withheld item: {key}")
        if not item.get("v5_visibility_reason"):
            fail("withheld Friends item lacks a reason")
    elif item.get("v5_visibility_reason"):
        fail(f"{key}: visible item unexpectedly has a withholding reason")
    visibility_counts[visibility] += 1
    item_by_key[key] = item
    items_by_path[path].append(key)

if visibility_counts != Counter({
    "VISIBLE": 247,
    "WITHHELD_RUNTIME_UNAVAILABLE": 1,
}):
    fail(f"V5 visibility accounting mismatch: {visibility_counts}")

for removed_alias in EXPECTED_ALIAS_REMOVALS:
    if removed_alias in item_by_key:
        fail(f"route alias still renders as a control: {removed_alias}")
for replacement in EXPECTED_ALIAS_REMOVALS.values():
    if replacement not in item_by_key:
        fail(f"route-alias replacement control missing: {replacement}")

for path, screen in screen_by_path.items():
    expected_control_count = len(items_by_path.get(path, []))
    if screen.get("control_count") != expected_control_count:
        fail(
            f"screen control count mismatch for {path}: "
            f"{screen.get('control_count')} != {expected_control_count}"
        )
    expected_child_count = len(children_by_parent.get(path, set()))
    if screen.get("child_count") != expected_child_count:
        fail(
            f"screen child count mismatch for {path}: "
            f"{screen.get('child_count')} != {expected_child_count}"
        )

blueprint_items = {item["key"]: item for item in blueprint.get("items", [])}
if len(blueprint_items) != 250:
    fail("blueprint v3 does not contain 250 source items")
expected_key_set = set(blueprint_items) - set(EXPECTED_ALIAS_REMOVALS)
if set(item_by_key) != expected_key_set:
    fail("corrected V5 logical key set differs from blueprint minus route aliases")

navigation_paths: dict[str, list[str]] = {}
for page in navigation.get("leaf_pages", []):
    path = screen_path_for_navigation_page(page)
    for key in page.get("keys", []):
        if key in navigation_paths:
            fail(f"duplicate key in Navigation contract: {key}")
        navigation_paths[key] = path
navigation_paths["pref_drawer_show_friends"] = [
    "Navigation",
    "Navigation drawer",
    "Account & tools",
]
if len(navigation_paths) != 31:
    fail("Navigation Phase 2 accounting is not 31")
navigation_paths["pref_lock_sidebar"] = [
    "Navigation",
    "Navigation drawer",
    "Drawer behavior",
]

explicit_target_paths = {
    key: to_path.split(" / ")
    for key, (_, to_path) in EXPECTED_CORRECTIONS.items()
}

for key, target in item_by_key.items():
    source = blueprint_items[key]
    if key in explicit_target_paths:
        expected_path = explicit_target_paths[key]
    elif key in navigation_paths:
        expected_path = navigation_paths[key]
    else:
        expected_path = source.get("screen_path")

    if target.get("screen_path") != expected_path:
        fail(f"{key}: corrected target path mismatch")
    if target.get("root") != expected_path[0]:
        fail(f"{key}: corrected root mismatch")
    expected_page = (
        "Navigation & gestures"
        if target.get("root") == "Navigation"
        else expected_path[1]
    )
    if target.get("page") != expected_page:
        fail(f"{key}: corrected page mismatch")
    expected_subscreen = (
        expected_path[-1]
        if key in explicit_target_paths or key in navigation_paths
        else source.get("subscreen")
    )
    if target.get("subscreen") != expected_subscreen:
        fail(f"{key}: corrected subscreen mismatch")
    if target.get("leaf_section") != expected_path[-1]:
        fail(f"{key}: corrected leaf_section mismatch")

    for field in STABLE_ITEM_FIELDS:
        if target.get(field) != source.get(field):
            fail(f"{key}: stable field {field} differs from blueprint")

# Validate the corrected screen topology:
# - preserve every non-Navigation blueprint screen except the empty Swipe leaf;
# - replace Navigation with the validated Phase 2 topology.
blueprint_non_navigation_paths = {
    screen["path"]
    for screen in blueprint.get("screens", [])
    if screen.get("root") != "Navigation"
} - REMOVED_SCREEN_PATHS

expected_navigation_paths = {
    "Navigation",
    "Navigation / Toolbar",
    "Navigation / Bottom navigation",
    "Navigation / Back & exit",
    "Navigation / Navigation drawer",
}
for page in navigation.get("leaf_pages", []):
    if page.get("parent") == "Navigation drawer":
        expected_navigation_paths.add(
            "Navigation / Navigation drawer / " + page["title"]
        )

expected_screen_paths = (
    blueprint_non_navigation_paths | expected_navigation_paths
)
if set(screen_by_path) != expected_screen_paths:
    missing = sorted(expected_screen_paths - set(screen_by_path))
    extra = sorted(set(screen_by_path) - expected_screen_paths)
    fail(f"corrected V5 screen topology mismatch: missing={missing}, extra={extra}")

print(f"BLUEPRINT={blueprint_path}")
print(f"BLUEPRINT_SHA256={blueprint_sha}")
print(f"NAVIGATION_CONTRACT={navigation_path}")
print(f"NAVIGATION_CONTRACT_SHA256={navigation_sha}")
print(f"SUPERSEDED_V5_CONTRACT={previous_contract_path}")
print(f"SUPERSEDED_V5_CONTRACT_SHA256={previous_sha}")
print(f"V5_CONTRACT={contract_path}")
print(f"V5_CONTRACT_SHA256={contract_sha}")
print("SEMANTIC_REVISION=V4")
print("CLASSIFICATION_CORRECTION_COUNT=5")
print("ROUTE_ALIAS_REMOVAL_COUNT=2")
print("EMPTY_SCREEN_REMOVAL_COUNT=1")
print("REDUNDANT_NAVIGATION_WRAPPER_REMOVAL_COUNT=1")
print("REDUNDANT_MORPHE_WRAPPER_REMOVAL_COUNT=1")
print("CANONICAL_LOGICAL_ITEM_TARGET=248")
print("VISIBLE_ITEM_TARGET=247")
print("WITHHELD_ITEM_TARGET=1")
print("SCREEN_NODE_TARGET=105")
print("SCREEN_EDGE_TARGET=98")
print("ROOT_OVERVIEW_SHELL_TARGET=1")
print("ROOT_GROUP_TARGET=7")
print("TASK_PAGE_TARGET=22")
print("INTERMEDIATE_PAGE_TARGET=5")
print("LEAF_PAGE_TARGET=71")
print("PLACEHOLDER_PAGE_TARGET=0")
print("LEGACY_FRAGMENT_ROUTE_TARGET=0")
print("LEGACY_XML_ROUTE_TARGET=0")
print("ROOT_CLASSIC_FALLBACK_TARGET=1")
print("V5_VISIBLE_BEFORE_COMPLETE=NO")
print("WITHHELD_FRIENDS_ACCOUNTING=PASS")
print("NAVIGATION_OVERRIDE_ACCOUNTING=PASS")
print("SEMANTIC_PLACEMENT_ACCOUNTING=PASS")
print("ROUTE_ALIAS_DEDUPLICATION=PASS")
print("EMPTY_SWIPE_SCREEN_REMOVAL=PASS")
print("NAVIGATION_ROOT_FLATTENING=PASS")
print("MORPHE_ROOT_FLATTENING=PASS")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_SEMANTIC_COMPLETENESS_TARGET_V4_PASS")
