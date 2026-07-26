#!/usr/bin/env python3
"""Historical Phase 2.1 visual contract, superseded by the guidance pilot."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD = ROOT / "tools/contracts/boost-settings-material-visual-foundation-v1.json"
NEW = ROOT / "tools/contracts/boost-settings-android-guidance-pilot-v1.json"

old = json.loads(OLD.read_text(encoding="utf-8"))
new = json.loads(NEW.read_text(encoding="utf-8"))
assert old["status"] == "superseded"
assert old["superseded_by"] == NEW.name
assert old["compliance_claim"] == "none"
assert new["claim"] == "alignment_pilot_not_google_certification"
assert new["preference_key_changes"] == 0
assert new["route_changes"] == 0

print("LEGACY_VISUAL_CONTRACT=SUPERSEDED")
print("SUPERSEDED_BY=ANDROID_SETTINGS_GUIDANCE_PILOT_V1")
print("COMPLIANCE_CLAIM=NONE")
print("RESULT=MORPHE_ISSUE121_MATERIAL_VISUAL_FOUNDATION_V1_SUPERSEDED_PASS")
