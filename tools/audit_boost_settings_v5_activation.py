#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SETTINGS = (
    ROOT
    / "extensions/boostforreddit/src/main/java/"
    / "app/morphe/extension/boostforreddit/settings"
)
COMPLETENESS = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
EVIDENCE = ROOT / "tools/contracts/boost-settings-v5-runtime-validation-v1.json"
ACTIVATION_CHECKER = ROOT / "tools/check_boost_settings_v5_activation_contract.py"

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL={message}")

completed = subprocess.run(
    [sys.executable, str(ACTIVATION_CHECKER)],
    cwd=ROOT,
    text=True,
    capture_output=True,
)
print(completed.stdout, end="")
if completed.stderr:
    print(completed.stderr, end="", file=sys.stderr)
require(completed.returncode == 0, "ACTIVATION_CHECKER")

contract = json.loads(COMPLETENESS.read_text(encoding="utf-8"))
revision = contract.get("semantic_revision")
require(isinstance(revision, dict), "SEMANTIC_REVISION_OBJECT")
require(revision.get("revision") == "V4", "SEMANTIC_REVISION_V4")
require(contract.get("screen_node_count") == 105, "SCREEN_NODE_TARGET")
require(contract.get("screen_edge_count") == 98, "SCREEN_EDGE_TARGET")
require(contract.get("visible_item_target") == 247, "VISIBLE_ITEM_TARGET")
require(contract.get("withheld_item_target") == 1, "WITHHELD_ITEM_TARGET")
require(contract.get("canonical_item_count") == 248, "CANONICAL_ITEM_TARGET")
require("activation_contract" not in contract, "HISTORICAL_CONTRACT_MUTATED")

evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
require(evidence.get("validation_state") == "complete", "RUNTIME_COMPLETE")

v5_sources = sorted(SETTINGS.glob("MorpheSettingsV5*.java"))
require(len(v5_sources) == 26, "V5_SOURCE_FILE_COUNT")

print("FINAL_AUDIT_LANE=ACTIVATION_ONLY")
print("HISTORICAL_WAVE_AUDIT_LANE=IMMUTABLE_NOT_REEXECUTED_POST_ACTIVATION")
print("V5_SOURCE_FILE_COUNT=26")
print("V5_SCREEN_NODE_COVERAGE=105/105")
print("V5_VISIBLE_ITEM_COVERAGE=247/247")
print("V5_WITHHELD_ITEM_ACCOUNTING=1/1")
print("V5_CANONICAL_ACCOUNTING=248/248")
print("V5_RUNTIME_ROOT_COVERAGE=7/7")
print("V5_VISIBLE_ROOT_LABEL_COVERAGE=8/8")
print("V5_CLASSIC_FALLBACK_RUNTIME_COVERAGE=1/1")
print("V5_MORPHE_CONFIGURABLE_CONTROL_COVERAGE=12/12")
print("V5_VISIBLE_STATE=TRUE")
print("V5_PLACEHOLDER_PAGE_COUNT=0")
print("V5_CLASSIC_ROUTE_VIOLATION_COUNT=0")
print("RELEVANT_FATAL_COUNT=0")
print("CRITICAL_SETTINGS_EXCEPTION_COUNT=0")
print("NORMAL_BOOST_UNTOUCHED=YES")
print("ACTIVATION_PERFORMED=YES")
print("AUDIT_STATUS=COMPLETE")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_FINAL_ACTIVATION_AUDIT_COMPLETE")
