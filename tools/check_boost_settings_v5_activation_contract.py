#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

COMPLETENESS = ROOT / "tools/contracts/boost-settings-v5-completeness-v2.json"
ROOT_WAVE_CONTRACT = (
    ROOT / "tools/contracts/boost-settings-v5-root-overview-wave-v1.json"
)
HISTORICAL_AUDIT = ROOT / "tools/audit_boost_settings_v5_implementation.py"
ROOT_WAVE_CHECKER = (
    ROOT / "tools/check_boost_settings_v5_root_overview_wave_v1.py"
)
EVIDENCE = ROOT / "tools/contracts/boost-settings-v5-runtime-validation-v1.json"
MPP = ROOT / "patches/build/libs/patches-1.4.94.mpp"

SETTINGS = (
    ROOT
    / "extensions/boostforreddit/src/main/java/"
    / "app/morphe/extension/boostforreddit/settings"
)
V4 = SETTINGS / "MorpheSettingsV4.java"
REGISTRY = SETTINGS / "MorpheSettingsV5Registry.java"
ROOT_FRAGMENT = SETTINGS / "MorpheSettingsV5RootFragment.java"

EXPECTED_HISTORICAL = {
    COMPLETENESS: "f1d6a9ac6c27f71eec61b300e5f36d0785d831fde14cb982450e21b3ae238682",
    ROOT_WAVE_CONTRACT: "68712a6fd73aebda4e5a24a013517191e4abbecc1eb8b7b46d6280546b93d5b7",
    HISTORICAL_AUDIT: "3472dfaea9972d47d49267f28f2bc7f390b95a5fc055ea3be2d4849c8a093845",
    ROOT_WAVE_CHECKER: "19e673a0fd415117c87ebd49557a21d328800273d6d1de4e395c3a53b4fb075d",
}
EXPECTED_EVIDENCE_SHA = (
    "d6b2c47b59202aa035e4e339cc89b3095087715dfb60feaad7a5478c1100dcf3"
)
EXPECTED_MPP_SHA = (
    "0c52634ea6519c720896624e3f13706efa29c15d2c36d2d8f6fa88df0b1cc7cb"
)

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL={message}")

def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def load(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), f"JSON_OBJECT_REQUIRED_{path.name}")
    return value

for path, expected in EXPECTED_HISTORICAL.items():
    require(path.is_file(), f"HISTORICAL_FILE_MISSING_{path.name}")
    require(sha(path) == expected, f"HISTORICAL_SHA_{path.name}")

require(sha(EVIDENCE) == EXPECTED_EVIDENCE_SHA, "RUNTIME_EVIDENCE_SHA")
require(sha(MPP) == EXPECTED_MPP_SHA, "ACTIVATED_MPP_SHA")

completeness = load(COMPLETENESS)
revision = completeness.get("semantic_revision")
require(isinstance(revision, dict), "SEMANTIC_REVISION_OBJECT")
require(revision.get("revision") == "V4", "SEMANTIC_REVISION_V4")
require("activation_contract" not in completeness, "HISTORICAL_COMPLETENESS_MUTATED")

evidence = load(EVIDENCE)
require(evidence.get("schema_version") == 1, "EVIDENCE_SCHEMA")
require(evidence.get("issue") == 121, "EVIDENCE_ISSUE")
require(evidence.get("semantic_revision") == "V4", "EVIDENCE_REVISION")
require(evidence.get("validation_state") == "complete", "EVIDENCE_COMPLETE")

activation = evidence["activation"]
require(activation["v5_visible_by_default"] is True, "VISIBLE_DEFAULT")
require(activation["material_settings_true_route"] == "v5", "TRUE_ROUTE")
require(activation["material_settings_false_route"] == "classic", "FALSE_ROUTE")
require(
    activation["normal_settings_launch_fragment_override"] is False,
    "NORMAL_LAUNCH_OVERRIDE",
)

expected_coverage = {
    "classic_fallback_runtime": 1,
    "root_destinations_click": 7,
    "root_destinations_render": 7,
    "root_destinations_back": 7,
    "visible_root_labels": 8,
    "morphe_configurable_controls": 12,
}
for key, target in expected_coverage.items():
    require(
        evidence["coverage"][key] == {"covered": target, "target": target},
        f"COVERAGE_{key}",
    )

safety = evidence["safety"]
require(safety["relevant_fatal_count"] == 0, "RELEVANT_FATALS")
require(
    safety["critical_settings_exception_count"] == 0,
    "CRITICAL_SETTINGS_EXCEPTIONS",
)
require(safety["normal_boost_untouched"] is True, "NORMAL_BOOST_UNTOUCHED")
require(
    safety["setting_value_mutations_by_harness"] is False,
    "SETTING_MUTATIONS",
)

v4_text = V4.read_text(encoding="utf-8")
registry_text = REGISTRY.read_text(encoding="utf-8")
root_text = ROOT_FRAGMENT.read_text(encoding="utf-8")

require('"MorpheSettingsV5RootFragment"' in v4_text, "V5_ENTRY_ROUTE")
require(
    re.search(r"V5_VISIBLE_BY_DEFAULT\s*=\s*true", registry_text)
    is not None,
    "V5_VISIBLE_SOURCE",
)
require("Open classic Boost settings" in root_text, "CLASSIC_FALLBACK_SOURCE")

root_children_match = re.search(
    r'case\s+"v5/root"\s*:\s*'
    r'return\s+new\s+String\[\]\s*\{([^}]*)\}',
    registry_text,
    re.S,
)
require(root_children_match is not None, "ROOT_CHILDREN_SOURCE")
require(
    len(re.findall(r'"v5/[^"]+"', root_children_match.group(1))) == 7,
    "ROOT_CHILD_COUNT",
)

artifact_roles = {item["role"] for item in evidence["artifacts"]}
require(len(artifact_roles) == 10, "RUNTIME_ARTIFACT_ROLE_COUNT")
for item in evidence["artifacts"]:
    require(
        re.fullmatch(r"[0-9a-f]{64}", item["sha256"]) is not None,
        f"ARTIFACT_SHA_{item['role']}",
    )

print("CONTRACT_ARCHITECTURE=IMMUTABLE_HISTORICAL_WAVES_PLUS_FINAL_ACTIVATION_EVIDENCE")
print(f"EVIDENCE={EVIDENCE}")
print(f"EVIDENCE_SHA256={sha(EVIDENCE)}")
print("SEMANTIC_REVISION=V4")
print("HISTORICAL_COMPLETENESS_IMMUTABLE=YES")
print("HISTORICAL_ROOT_WAVE_IMMUTABLE=YES")
print("HISTORICAL_IMPLEMENTATION_AUDIT_IMMUTABLE=YES")
print("V5_RUNTIME_ROOT_COVERAGE=7/7")
print("V5_VISIBLE_ROOT_LABEL_COVERAGE=8/8")
print("V5_CLASSIC_FALLBACK_RUNTIME_COVERAGE=1/1")
print("V5_MORPHE_CONFIGURABLE_CONTROL_COVERAGE=12/12")
print("V5_VISIBLE_STATE=TRUE")
print("ACTIVATION_PERFORMED=YES")
print("RELEVANT_FATAL_COUNT=0")
print("CRITICAL_SETTINGS_EXCEPTION_COUNT=0")
print("NORMAL_BOOST_UNTOUCHED=YES")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_FINAL_ACTIVATION_CONTRACT_PASS")
