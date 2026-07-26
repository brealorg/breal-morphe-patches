#!/usr/bin/env python3
"""Audit Morphe Settings V5 source against the semantically corrected target."""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import unicodedata
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = (
    ROOT
    / "extensions/boostforreddit/src/main/java/app/morphe/extension/"
    / "boostforreddit/settings"
)
CONTRACT_PATH = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
RUNTIME_EVIDENCE_PATH = (
    ROOT / "tools/contracts/boost-settings-v5-runtime-evidence-v1.json"
)
V4_FRAGMENT_PATH = SETTINGS / "MorpheSettingsV4Fragment.java"
V4_ROOT_CHECKER_PATH = ROOT / "tools/check_boost_settings_root_shell_phase1_contract.py"

PAGE_PATTERN = re.compile(
    r'new\s+V5PageSpec\s*\(\s*'
    r'"(?P<page_id>v5/[^"]+)"\s*,\s*'
    r'"(?P<renderer>[^"]+)"\s*,\s*'
    r'new\s+String\s*\[\]\s*\{(?P<keys>.*?)\}\s*\)',
    re.S,
)
WITHHELD_PATTERN = re.compile(
    r'new\s+V5WithheldSpec\s*\(\s*'
    r'"(?P<key>[^"]+)"\s*,\s*'
    r'"(?P<reason>[^"]+)"\s*\)',
    re.S,
)
KEY_PATTERN = re.compile(r'"([^"]+)"')
VISIBLE_PATTERN = re.compile(
    r'\bV5_VISIBLE_BY_DEFAULT\s*=\s*(true|false)\b'
)
CLASS_PATTERN = re.compile(
    r'\bclass\s+([A-Za-z_$][A-Za-z0-9_$]*)\b'
)

ROOT_FILE = "MorpheSettingsV5RootFragment.java"
REGISTRY_FILE = "MorpheSettingsV5Registry.java"
ROOT_FALLBACK_TOKEN = "MORPHE_V5_ROOT_CLASSIC_FALLBACK"
ROOT_OVERVIEW_MARKER = "MORPHE_BOOST_SETTINGS_V5_ROOT_OVERVIEW_ISSUE121_V1"
COMPLETENESS_MARKER = "MORPHE_BOOST_SETTINGS_V5_COMPLETE_ISSUE121_V1"
PLACEHOLDER_MARKER = "MORPHE_V5_PLACEHOLDER_PAGE"
FORBIDDEN_ROUTE_TOKENS = (
    "SettingsActivityCompat$",
    "PreferenceFragmentAdvancedCompat",
    "MorpheSettingsV4NativePages",
    "MorpheSettingsV4AppearanceFragment",
    "MorpheSettingsV4PostViewsFragment",
    "MorpheSettingsV4FontsFragment",
    "MorpheSettingsV4DownloadsFragment",
    "MorpheSettingsV4NavigationDrawerHubFragment",
    "MorpheSettingsFragment.class.getName()",
)
RUNTIME_GATES = (
    "all_expected_keys_rendered",
    "no_extra_keys",
    "dependencies_preserved",
    "current_values_shown",
    "side_effects_preserved",
    "global_search_route_pass",
    "back_stack_pass",
    "runtime_visual_audit_pass",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--require-complete", action="store_true")
    return parser.parse_args()


def slug(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_value = normalized.encode("ascii", "ignore").decode("ascii")
    ascii_value = ascii_value.replace("&", " and ")
    return re.sub(r"[^a-zA-Z0-9]+", "_", ascii_value).strip("_").lower()


def page_id(path: list[str]) -> str:
    return "v5/" + "/".join(slug(part) for part in path)


args = parse_args()
contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
target_screens = {
    page_id(screen["path"].split(" / ")): screen
    for screen in contract["screens"]
}
target_items = {item["key"]: item for item in contract["items"]}
target_page_ids = set(target_screens)
visible_target_keys = {
    key
    for key, item in target_items.items()
    if item["v5_visibility"] == "VISIBLE"
}
withheld_target_keys = {
    key
    for key, item in target_items.items()
    if item["v5_visibility"] == "WITHHELD_RUNTIME_UNAVAILABLE"
}
target_screen_count = contract["screen_node_count"]
target_visible_count = contract["visible_item_target"]
target_withheld_count = contract["withheld_item_target"]
target_canonical_count = contract["canonical_item_count"]
if target_screen_count != 105 or len(target_page_ids) != target_screen_count:
    raise AssertionError("target contract does not contain 105 screens")
if target_visible_count != 247 or len(visible_target_keys) != target_visible_count:
    raise AssertionError("target contract does not contain 247 visible keys")
if target_withheld_count != 1 or withheld_target_keys != {"pref_drawer_show_friends"}:
    raise AssertionError("unexpected V5 withheld-key target")
if target_canonical_count != 248:
    raise AssertionError("target contract does not contain 248 logical items")

v5_files = sorted(SETTINGS.glob("MorpheSettingsV5*.java"))
source_texts = {
    path.name: path.read_text(encoding="utf-8", errors="replace")
    for path in v5_files
}
all_v5_text = "\n".join(source_texts.values())
registry_text = source_texts.get(REGISTRY_FILE, "")

declared_pages: list[dict[str, Any]] = []
for match in PAGE_PATTERN.finditer(registry_text):
    declared_pages.append(
        {
            "page_id": match.group("page_id"),
            "renderer": match.group("renderer"),
            "keys": KEY_PATTERN.findall(match.group("keys")),
        }
    )
declared_page_ids = [entry["page_id"] for entry in declared_pages]
declared_visible_keys = [
    key for entry in declared_pages for key in entry["keys"]
]
page_counts = Counter(declared_page_ids)
key_counts = Counter(declared_visible_keys)

declared_withheld = [
    {
        "key": match.group("key"),
        "reason": match.group("reason"),
    }
    for match in WITHHELD_PATTERN.finditer(registry_text)
]
declared_withheld_keys = [entry["key"] for entry in declared_withheld]
withheld_counts = Counter(declared_withheld_keys)

implemented_page_ids = set(declared_page_ids) & target_page_ids
implemented_visible_keys = set(declared_visible_keys) & visible_target_keys
accounted_withheld_keys = set(declared_withheld_keys) & withheld_target_keys

missing_page_ids = sorted(target_page_ids - set(declared_page_ids))
extra_page_ids = sorted(set(declared_page_ids) - target_page_ids)
missing_visible_keys = sorted(visible_target_keys - set(declared_visible_keys))
extra_visible_keys = sorted(set(declared_visible_keys) - visible_target_keys)
missing_withheld_keys = sorted(withheld_target_keys - set(declared_withheld_keys))
extra_withheld_keys = sorted(set(declared_withheld_keys) - withheld_target_keys)
duplicate_pages = sorted(key for key, count in page_counts.items() if count != 1)
duplicate_visible_keys = sorted(
    key for key, count in key_counts.items() if count != 1
)
duplicate_withheld_keys = sorted(
    key for key, count in withheld_counts.items() if count != 1
)

target_visible_keys_by_page: dict[str, set[str]] = {
    target_page_id: set() for target_page_id in target_page_ids
}
for key, item in target_items.items():
    if item["v5_visibility"] != "VISIBLE":
        continue
    target_visible_keys_by_page[page_id(item["screen_path"])].add(key)
page_key_mismatches: list[str] = []
for entry in declared_pages:
    declared_set = set(entry["keys"])
    expected_set = target_visible_keys_by_page.get(entry["page_id"])
    if expected_set is None:
        continue
    if declared_set != expected_set:
        page_key_mismatches.append(
            f"{entry['page_id']}:declared={sorted(declared_set)}:"
            f"expected={sorted(expected_set)}"
        )

declared_classes = set(CLASS_PATTERN.findall(all_v5_text))
missing_renderer_classes = sorted(
    {
        entry["renderer"].split(".")[-1]
        for entry in declared_pages
        if entry["renderer"].split(".")[-1] not in declared_classes
    }
)
external_renderer_names = sorted(
    {
        entry["renderer"]
        for entry in declared_pages
        if not entry["renderer"].split(".")[-1].startswith("MorpheSettingsV5")
    }
)

placeholder_count = all_v5_text.count(PLACEHOLDER_MARKER)

root_fallback_count = 0
classic_route_violations: list[str] = []
for filename, text in source_texts.items():
    fallback_count = text.count(ROOT_FALLBACK_TOKEN)
    if filename == ROOT_FILE:
        root_fallback_count += fallback_count
    elif fallback_count:
        classic_route_violations.extend(
            [f"{filename}:{ROOT_FALLBACK_TOKEN}"] * fallback_count
        )
    if filename == ROOT_FILE:
        continue
    for token in FORBIDDEN_ROUTE_TOKENS:
        count = text.count(token)
        if count:
            classic_route_violations.extend([f"{filename}:{token}"] * count)

root_overview_marker_present = ROOT_OVERVIEW_MARKER in source_texts.get(
    ROOT_FILE, ""
)
completeness_marker_present = COMPLETENESS_MARKER in all_v5_text
registry_consumed = (
    "MorpheSettingsV5Registry.findPage(" in all_v5_text
    or "MorpheSettingsV5Registry.requirePage(" in all_v5_text
)

visible_values = [
    value == "true" for value in VISIBLE_PATTERN.findall(all_v5_text)
]
if not visible_values:
    visible_state = "ABSENT"
elif len(set(visible_values)) > 1:
    visible_state = "CONFLICT"
else:
    visible_state = "TRUE" if visible_values[0] else "FALSE"

runtime_pass_roots = 0
runtime_missing: list[str] = []
if RUNTIME_EVIDENCE_PATH.is_file():
    runtime_evidence = json.loads(
        RUNTIME_EVIDENCE_PATH.read_text(encoding="utf-8")
    )
    roots = runtime_evidence.get("roots", {})
    for root in contract["root_order"]:
        root_data = roots.get(root, {})
        missing = [
            gate for gate in RUNTIME_GATES if root_data.get(gate) != "PASS"
        ]
        if missing:
            runtime_missing.append(f"{root}: {','.join(missing)}")
        else:
            runtime_pass_roots += 1
else:
    runtime_missing = [
        f"{root}: evidence_file_absent" for root in contract["root_order"]
    ]

root_progress: list[dict[str, Any]] = []
for root in contract["root_order"]:
    expected_pages = {
        target_page_id
        for target_page_id, screen in target_screens.items()
        if screen["root"] == root
    }
    expected_visible_keys = {
        key
        for key, item in target_items.items()
        if item["root"] == root and item["v5_visibility"] == "VISIBLE"
    }
    expected_withheld_keys = {
        key
        for key, item in target_items.items()
        if item["root"] == root
        and item["v5_visibility"] == "WITHHELD_RUNTIME_UNAVAILABLE"
    }
    root_progress.append(
        {
            "root": root,
            "implemented_pages": len(expected_pages & implemented_page_ids),
            "expected_pages": len(expected_pages),
            "implemented_visible_items": len(
                expected_visible_keys & implemented_visible_keys
            ),
            "expected_visible_items": len(expected_visible_keys),
            "accounted_withheld_items": len(
                expected_withheld_keys & accounted_withheld_keys
            ),
            "expected_withheld_items": len(expected_withheld_keys),
        }
    )

static_complete = all(
    (
        len(implemented_page_ids) == target_screen_count,
        len(implemented_visible_keys) == target_visible_count,
        len(accounted_withheld_keys) == target_withheld_count,
        not missing_page_ids,
        not extra_page_ids,
        not missing_visible_keys,
        not extra_visible_keys,
        not missing_withheld_keys,
        not extra_withheld_keys,
        not duplicate_pages,
        not duplicate_visible_keys,
        not duplicate_withheld_keys,
        not page_key_mismatches,
        not missing_renderer_classes,
        not external_renderer_names,
        placeholder_count == 0,
        not classic_route_violations,
        root_fallback_count == 1,
        root_overview_marker_present,
        completeness_marker_present,
        registry_consumed,
    )
)
runtime_complete = runtime_pass_roots == len(contract["root_order"])
complete = static_complete and runtime_complete
visibility_violation = visible_state == "TRUE" and not complete
if visibility_violation:
    complete = False

v4_fragment = (
    V4_FRAGMENT_PATH.read_text(encoding="utf-8", errors="replace")
    if V4_FRAGMENT_PATH.is_file()
    else ""
)
v4_root_checker = (
    V4_ROOT_CHECKER_PATH.read_text(encoding="utf-8", errors="replace")
    if V4_ROOT_CHECKER_PATH.is_file()
    else ""
)
v4_placeholder_copy_present = (
    "classic Boost settings while this task page is organized" in v4_fragment
)
v4_classic_fallback_reference_count = v4_fragment.count(
    "Open classic Boost settings"
)
v4_placeholder_policy_present = (
    "PLACEHOLDER_TASK_PAGE_POLICY=PASS" in v4_root_checker
)
v4_direct_route_policy_present = (
    "DIRECT_EXISTING_ROUTE_POLICY=PASS" in v4_root_checker
)

if complete:
    status = "PASS_COMPLETE"
elif not v5_files:
    status = "INCOMPLETE_EXPECTED_BASELINE"
else:
    status = "INCOMPLETE"

report = {
    "schema": 1,
    "generated_at": datetime.now(timezone.utc).isoformat(),
    "status": status,
    "target": {
        "screen_nodes": target_screen_count,
        "visible_items": target_visible_count,
        "withheld_items": target_withheld_count,
        "canonical_items": target_canonical_count,
        "runtime_roots": len(contract["root_order"]),
    },
    "current": {
        "v5_source_file_count": len(v5_files),
        "declared_page_count": len(declared_page_ids),
        "implemented_target_page_count": len(implemented_page_ids),
        "declared_visible_item_count": len(declared_visible_keys),
        "implemented_visible_item_count": len(implemented_visible_keys),
        "declared_withheld_item_count": len(declared_withheld_keys),
        "accounted_withheld_item_count": len(accounted_withheld_keys),
        "runtime_pass_root_count": runtime_pass_roots,
        "visible_state": visible_state,
        "root_overview_marker_present": root_overview_marker_present,
        "completeness_marker_present": completeness_marker_present,
        "registry_consumed": registry_consumed,
    },
    "violations": {
        "missing_page_ids": missing_page_ids,
        "extra_page_ids": extra_page_ids,
        "missing_visible_keys": missing_visible_keys,
        "extra_visible_keys": extra_visible_keys,
        "missing_withheld_keys": missing_withheld_keys,
        "extra_withheld_keys": extra_withheld_keys,
        "duplicate_page_ids": duplicate_pages,
        "duplicate_visible_keys": duplicate_visible_keys,
        "duplicate_withheld_keys": duplicate_withheld_keys,
        "page_key_mismatches": page_key_mismatches,
        "missing_renderer_classes": missing_renderer_classes,
        "external_renderer_names": external_renderer_names,
        "placeholder_count": placeholder_count,
        "classic_route_violations": classic_route_violations,
        "root_fallback_count": root_fallback_count,
        "visibility_violation": visibility_violation,
        "runtime_missing": runtime_missing,
    },
    "root_progress": root_progress,
    "historical_v4": {
        "baseline_complete_items": 30,
        "baseline_complete_screens": 13,
        "placeholder_copy_present": v4_placeholder_copy_present,
        "classic_fallback_reference_count": v4_classic_fallback_reference_count,
        "placeholder_policy_present": v4_placeholder_policy_present,
        "direct_route_policy_present": v4_direct_route_policy_present,
        "acceptable_as_v5_completion": False,
    },
}

output_dir = args.output_dir
if output_dir is not None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "settings-v5-implementation-audit.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    progress_columns = [
        "root",
        "implemented_pages",
        "expected_pages",
        "implemented_visible_items",
        "expected_visible_items",
        "accounted_withheld_items",
        "expected_withheld_items",
    ]
    with (
        output_dir / "settings-v5-root-progress.tsv"
    ).open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=progress_columns,
            delimiter="\t",
        )
        writer.writeheader()
        writer.writerows(root_progress)

    markdown = [
        "# Morphe Issue #121 — Settings V5 implementation audit",
        "",
        f"- Status: **{status}**",
        f"- V5 screen coverage: **{len(implemented_page_ids)}/{target_screen_count}**",
        (
            "- V5 visible-item coverage: "
            f"**{len(implemented_visible_keys)}/{target_visible_count}**"
        ),
        (
            "- V5 withheld-item accounting: "
            f"**{len(accounted_withheld_keys)}/{target_withheld_count}**"
        ),
        (
            "- Runtime-evidenced roots: "
            f"**{runtime_pass_roots}/{len(contract['root_order'])}**"
        ),
        f"- V5 visible state: **{visible_state}**",
        "",
        "## Root progress",
        "",
    ]
    for row in root_progress:
        markdown.append(
            "- **{root}** — pages {implemented_pages}/{expected_pages}; "
            "visible items {implemented_visible_items}/{expected_visible_items}; "
            "withheld {accounted_withheld_items}/{expected_withheld_items}".format(
                **row
            )
        )
    markdown += [
        "",
        "## Current V4 finding",
        "",
        (
            "The visible V4 tree remains a mixed legacy/placeholder tree. "
            "Its validated Navigation slice is reusable implementation input, "
            "but it is not accepted as V5 completion."
        ),
        "",
    ]
    (output_dir / "settings-v5-implementation-audit.md").write_text(
        "\n".join(markdown),
        encoding="utf-8",
    )

print(f"V5_SOURCE_FILE_COUNT={len(v5_files)}")
print(f"V5_SCREEN_NODE_COVERAGE={len(implemented_page_ids)}/{target_screen_count}")
print(f"V5_VISIBLE_ITEM_COVERAGE={len(implemented_visible_keys)}/{target_visible_count}")
print(f"V5_WITHHELD_ITEM_ACCOUNTING={len(accounted_withheld_keys)}/{target_withheld_count}")
print(
    "V5_CANONICAL_ACCOUNTING="
    f"{len(implemented_visible_keys) + len(accounted_withheld_keys)}/{target_canonical_count}"
)
print(
    "V5_RUNTIME_ROOT_COVERAGE="
    f"{runtime_pass_roots}/{len(contract['root_order'])}"
)
print(f"V5_VISIBLE_STATE={visible_state}")
print(f"V5_PLACEHOLDER_PAGE_COUNT={placeholder_count}")
print(
    "V5_CLASSIC_ROUTE_VIOLATION_COUNT="
    f"{len(classic_route_violations)}"
)
print(f"V5_ROOT_CLASSIC_FALLBACK_COUNT={root_fallback_count}")
print(
    "CURRENT_V4_PLACEHOLDER_POLICY_PRESENT="
    f"{'YES' if v4_placeholder_policy_present else 'NO'}"
)
print(
    "CURRENT_V4_DIRECT_ROUTE_POLICY_PRESENT="
    f"{'YES' if v4_direct_route_policy_present else 'NO'}"
)
print("CURRENT_V4_BASELINE_COMPLETE_ITEM_COUNT=30")
print("CURRENT_V4_BASELINE_COMPLETE_SCREEN_COUNT=13")
print("CURRENT_V4_ACCEPTABLE_AS_V5_COMPLETION=NO")
print(f"AUDIT_STATUS={status}")
if output_dir is not None:
    print(f"REPORT_DIRECTORY={output_dir}")

if args.require_complete and not complete:
    sys.exit(1)
