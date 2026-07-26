#!/usr/bin/env python3
"""V5 font-preview contract for Morphe Issue #121."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/settings/MorpheSettingsV5AppearanceBindings.java"
CONTRACT = ROOT / "tools/contracts/boost-settings-v5-font-preview-v1.json"
EXPECTED_CONTRACT_SHA256 = "794eccd7afb4fef711ed7498465eb8cd0a821cd6dcecf7c7da48a792eac8aa3f"

raw = CONTRACT.read_bytes()
actual_sha = hashlib.sha256(raw).hexdigest()
assert actual_sha == EXPECTED_CONTRACT_SHA256, actual_sha
contract = json.loads(raw.decode("utf-8"))
source = SOURCE.read_text(encoding="utf-8")

assert contract["schema"] == 1
assert contract["issue"] == 121
assert contract["canonical_keys"] == ["pref_title_font", "pref_comments_font"]
assert contract["font_option_count"] == 13
assert contract["requirements"]["route_changes"] == 0
assert contract["requirements"]["preference_key_changes"] == 0

assert "MORPHE_BOOST_SETTINGS_V5_FONT_PREVIEW_ISSUE121_V1" in source
assert "MORPHE_BOOST_SETTINGS_V5_FONT_RESOLVER_ISSUE121_V1" in source
assert "private void showFontChoiceDialog(" in source
assert "row.setTitleTypeface(resolveFontTypeface(value));" in source
assert "binding.summary.setTypeface(resolveFontTypeface(" in source
assert 'Class.forName("id.b")' in source
assert 'getDeclaredMethod("v0")' in source
assert '"p4",' in source
assert "Context.class" in source
assert "String.class" in source
assert "Typeface.createFromAsset" in source
assert 'stringArray("font_options", FONT_TITLES)' in source
assert 'stringArray("font_values", FONT_VALUES)' in source
assert source.count('case "pref_comments_font":') == 1
assert source.count('case "pref_title_font":') == 1
assert source.count('"Roboto Slab"') == 1
assert source.count('"RobotoSlab-Regular.ttf"') == 1

font_dialog_start = source.index("private void showFontDialog(String key)")
font_dialog_end = source.index("private void showAppIconDialog()", font_dialog_start)
font_dialog = source[font_dialog_start:font_dialog_end]
assert "showFontChoiceDialog(" in font_dialog
assert "showChoiceDialog(" not in font_dialog

print(f"CONTRACT={CONTRACT}")
print(f"CONTRACT_SHA256={actual_sha}")
print("CANONICAL_FONT_KEY_COUNT=2")
print("FONT_OPTION_COUNT=13")
print("FONT_PICKER_ROW_TYPEFACE_PREVIEW=PASS")
print("FONT_CURRENT_VALUE_TYPEFACE_PREVIEW=PASS")
print("BOOST_CANONICAL_TYPEFACE_RESOLVER=PASS")
print("PREFERENCE_KEY_CHANGES=0")
print("ROUTE_CHANGES=0")
print("RESULT=MORPHE_ISSUE121_SETTINGS_V5_FONT_PREVIEW_V1_CONTRACT_PASS")
