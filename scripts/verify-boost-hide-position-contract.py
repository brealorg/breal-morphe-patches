#!/usr/bin/env python3

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import zipfile


EXPECTED_CLASSES = (
    "Lcom/rubenmayayo/reddit/ui/fragments/g;",
    "Lcom/rubenmayayo/reddit/ui/fragments/h;",
    "Lcom/rubenmayayo/reddit/ui/fragments/j;",
)

MARKER_TEXT = "morphe_boost_feed_position_contract_v2"
MARKER_BYTES = MARKER_TEXT.encode("utf-8")


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    raise SystemExit(1)


def require_unique(text: str, needle: str, label: str) -> int:
    count = text.count(needle)

    if count != 1:
        fail(
            f"{label}: expected exactly one occurrence, "
            f"found {count}: {needle}"
        )

    return text.index(needle)


def extract_balanced(
    text: str,
    opening_index: int,
    opening: str,
    closing: str,
    label: str,
) -> str:
    if opening_index < 0 or opening_index >= len(text):
        fail(f"{label}: invalid opening index")

    if text[opening_index] != opening:
        fail(
            f"{label}: expected {opening!r} at index "
            f"{opening_index}"
        )

    depth = 0
    in_string = False
    escaped = False

    for index in range(opening_index, len(text)):
        char = text[index]

        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False

            continue

        if char == '"':
            in_string = True
            continue

        if char == opening:
            depth += 1
        elif char == closing:
            depth -= 1

            if depth == 0:
                return text[opening_index:index + 1]

    fail(f"{label}: unbalanced {opening}{closing}")
    raise AssertionError("unreachable")


def extract_braced_declaration(
    text: str,
    anchor: str,
    label: str,
) -> str:
    anchor_index = require_unique(text, anchor, label)
    opening_index = text.find("{", anchor_index)

    if opening_index == -1:
        fail(f"{label}: opening brace not found")

    return extract_balanced(
        text,
        opening_index,
        "{",
        "}",
        label,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mpp", type=Path)
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]

    fingerprints_path = root / (
        "patches/src/main/kotlin/app/morphe/patches/reddit/"
        "customclients/boostforreddit/fix/hide/Fingerprints.kt"
    )

    patch_path = root / (
        "patches/src/main/kotlin/app/morphe/patches/reddit/"
        "customclients/boostforreddit/fix/hide/"
        "FixHideInvalidIndexPatch.kt"
    )

    fingerprints = fingerprints_path.read_text()
    patch = patch_path.read_text()

    print("===== BOOST HIDE POSITION CONTRACT VERIFIER =====")

    class_anchor = (
        "private val feedPositionResolverClassTypes = listOf("
    )

    class_anchor_index = require_unique(
        fingerprints,
        class_anchor,
        "resolver class-list declaration",
    )

    class_open_index = fingerprints.find("(", class_anchor_index)

    class_block = extract_balanced(
        fingerprints,
        class_open_index,
        "(",
        ")",
        "resolver class list",
    )

    actual_classes = tuple(
        re.findall(
            r'"(Lcom/rubenmayayo/reddit/ui/fragments/[ghj];)"',
            class_block,
        )
    )

    if actual_classes != EXPECTED_CLASSES:
        fail(
            "resolver class list mismatch: "
            f"expected={EXPECTED_CLASSES}, "
            f"actual={actual_classes}"
        )

    resolver_block = extract_braced_declaration(
        fingerprints,
        "internal val feedPositionResolverFingerprints =",
        "resolver fingerprint declaration",
    )

    resolver_requirements = (
        'returnType = "I"',
        'parameters = listOf("I")',
        "classDef.type == classType",
        'method.name == "O2"',
    )

    for requirement in resolver_requirements:
        if requirement not in resolver_block:
            fail(
                "resolver fingerprint missing contract: "
                f"{requirement}"
            )

    contract_block = extract_braced_declaration(
        patch,
        "feedPositionResolverFingerprints.forEach",
        "central position-contract patch",
    )

    contract_requirements = (
        "indexOfFirstInstructionReversedOrThrow",
        "opcode == Opcode.RETURN",
        "fallbackReturnIndex - 1",
        "Opcode.CONST_4",
        "replaceInstruction(",
        MARKER_TEXT,
    )

    for requirement in contract_requirements:
        if requirement not in contract_block:
            fail(
                "central position contract missing: "
                f"{requirement}"
            )

    if contract_block.count(MARKER_TEXT) != 1:
        fail(
            "expected exactly one marker in contract block, "
            f"found {contract_block.count(MARKER_TEXT)}"
        )

    s1_block = extract_braced_declaration(
        patch,
        "feedActionS1InvalidIndexFingerprint.method.apply",
        "s1 bounds guard",
    )

    s1_requirements = (
        "indexOfFirstInstructionOrThrow",
        "indexOfFirstInstructionReversedOrThrow",
        "if-ltz v0, :morphe_skip_s1_invalid_index",
        "if-ge v0, v2, :morphe_skip_s1_invalid_index",
        'ExternalLabel("morphe_skip_s1_invalid_index"',
    )

    for requirement in s1_requirements:
        if requirement not in s1_block:
            fail(f"s1 safety guard missing: {requirement}")

    forbidden_fragments = (
        "morphe_boost_hide_read_position_fallback_v1",
        ":morphe_s1_check_bounds",
        "move v0, p1",
    )

    for fragment in forbidden_fragments:
        if fragment in patch:
            fail(f"obsolete V1 logic remains: {fragment}")

    if args.mpp is not None:
        if not args.mpp.is_file():
            fail(f"MPP does not exist: {args.mpp}")

        with zipfile.ZipFile(args.mpp) as archive:
            entries = set(archive.namelist())

            for required_entry in (
                "classes.dex",
                "extensions/boostforreddit.mpe",
            ):
                if required_entry not in entries:
                    fail(
                        "MPP missing required entry: "
                        f"{required_entry}"
                    )

            classes_dex = archive.read("classes.dex")

        if MARKER_BYTES not in classes_dex:
            fail(
                "position-contract marker missing "
                "from MPP classes.dex"
            )

        print(f"MPP={args.mpp}")
        print("MPP_REQUIRED_ENTRIES=OK")
        print("MPP_MARKER=OK")

    print("RESOLVER_CLASSES=g,h,j")
    print("RESOLVER_FINGERPRINT=STRUCTURALLY_VALID")
    print("POSITION_CONTRACT=STRUCTURALLY_VALID")
    print("S1_BOUNDS_GUARD=PRESENT")
    print("OLD_V1_LOGIC=ABSENT")
    print("RESULT=MORPHE_BOOST_HIDE_POSITION_CONTRACT_V2_OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
