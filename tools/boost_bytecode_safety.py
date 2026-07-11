#!/usr/bin/env python3
"""Deterministic Smali bytecode safety analysis for patched Boost methods.

The analyzer compares apktool-decoded base and candidate trees. It identifies
changed methods in classes that existed in the base APK, isolates newly added or
replaced candidate instructions, validates member access/staticness, and runs a
conservative register/type-flow analysis around those instructions.

This is intentionally fail-closed for modified Boost application methods when a
new instruction consumes a register whose type cannot be established. It is a
pre-runtime gate, not a replacement for ART/runtime validation.
"""

from __future__ import annotations

import argparse
import dataclasses
import difflib
import json
import re
import sys
from collections import deque
from pathlib import Path
from typing import Iterable, Iterator, Sequence

CLASS_RE = re.compile(r"^\.class\s+(?P<flags>.*?)\s+(?P<descriptor>L[^;]+;)$")
SUPER_RE = re.compile(r"^\.super\s+(?P<descriptor>L[^;]+;)$")
FIELD_RE = re.compile(
    r"^\.field\s+(?P<prefix>.*?)\s*(?P<name>[^\s:=]+):(?P<type>[^\s=]+)"
)
METHOD_RE = re.compile(
    r"^\.method\s+(?P<prefix>.*?)\s*(?P<name>[^\s(]+)(?P<proto>\([^)]*\).+)$"
)
MEMBER_RE = re.compile(
    r"(?P<class>L[^;]+;)->(?P<name>[^\s:(]+)(?::(?P<field>[^\s,}]+)|(?P<method>\([^)]*\)[^\s,}]+))"
)
METHOD_DESCRIPTOR_RE = re.compile(r"L[^;]+;->[A-Za-z0-9_$<>]+\([^)]*\)[VZBSCIJFDL\[][^\s\"']*")
REGISTER_RE = re.compile(r"\b([vp]\d+)\b")
LABEL_RE = re.compile(r"^(:[A-Za-z0-9_.$-]+)")

ACCESS_FLAGS = {
    "public",
    "private",
    "protected",
    "static",
    "final",
    "synchronized",
    "bridge",
    "varargs",
    "native",
    "abstract",
    "strictfp",
    "synthetic",
    "constructor",
    "declared-synchronized",
    "volatile",
    "transient",
    "enum",
    "interface",
    "annotation",
}

PLATFORM_PREFIXES = (
    "Landroid/",
    "Landroidx/",
    "Ljava/",
    "Ljavax/",
    "Lkotlin/",
    "Lkotlinx/",
    "Ldalvik/",
    "Lorg/xml/",
    "Lorg/json/",
)

PRIMITIVES = set("ZBSCIF")
WIDE_PRIMITIVES = set("JD")


@dataclasses.dataclass(frozen=True)
class FieldDef:
    owner: str
    name: str
    descriptor: str
    access: frozenset[str]

    @property
    def key(self) -> tuple[str, str, str]:
        return (self.owner, self.name, self.descriptor)


@dataclasses.dataclass(frozen=True)
class MethodRef:
    owner: str
    name: str
    proto: str

    @property
    def key(self) -> tuple[str, str, str]:
        return (self.owner, self.name, self.proto)

    def display(self) -> str:
        return f"{self.owner}->{self.name}{self.proto}"


@dataclasses.dataclass
class Instruction:
    index: int
    line_no: int
    text: str
    opcode: str
    labels: tuple[str, ...]

    def normalized(self) -> str:
        # Preserve operands because register/member changes are semantically relevant.
        return re.sub(r"\s+", " ", self.text.strip())


@dataclasses.dataclass
class MethodDef:
    owner: str
    name: str
    proto: str
    access: frozenset[str]
    source: Path
    line_no: int
    instructions: list[Instruction]
    locals_count: int | None
    registers_count: int | None

    @property
    def key(self) -> tuple[str, str, str]:
        return (self.owner, self.name, self.proto)

    def display(self) -> str:
        return f"{self.owner}->{self.name}{self.proto}"

    @property
    def is_static(self) -> bool:
        return "static" in self.access

    def parameter_descriptors(self) -> list[str]:
        args, _ = split_proto(self.proto)
        return args

    def parameter_word_types(self) -> list[str]:
        values: list[str] = []
        if not self.is_static:
            values.append(self.owner)
        for descriptor in self.parameter_descriptors():
            values.append(descriptor)
            if descriptor in WIDE_PRIMITIVES:
                values.append("WIDE_HIGH")
        return values

    def total_registers(self) -> int | None:
        if self.registers_count is not None:
            return self.registers_count
        if self.locals_count is not None:
            return self.locals_count + len(self.parameter_word_types())
        return None

    def local_registers(self) -> int | None:
        if self.locals_count is not None:
            return self.locals_count
        total = self.total_registers()
        if total is None:
            return None
        return total - len(self.parameter_word_types())

    def absolute_register(self, register: str) -> int | None:
        number = int(register[1:])
        if register.startswith("v"):
            return number
        local_count = self.local_registers()
        if local_count is None:
            return None
        return local_count + number


@dataclasses.dataclass
class ClassDef:
    descriptor: str
    access: frozenset[str]
    superclass: str | None
    fields: dict[tuple[str, str], FieldDef]
    methods: dict[tuple[str, str], MethodDef]
    source: Path


@dataclasses.dataclass(frozen=True, order=True)
class Finding:
    class_descriptor: str
    method: str
    instruction_index: int
    line_no: int
    instruction: str
    reason: str
    member: str = ""
    detail: str = ""

    def as_dict(self) -> dict[str, object]:
        return dataclasses.asdict(self)


@dataclasses.dataclass
class AnalysisResult:
    findings: list[Finding]
    modified_methods: int
    injected_instructions: int
    member_access_checks: int
    register_flow_checks: int
    recognized_patch_methods: int

    @property
    def passed(self) -> bool:
        return not self.findings

    def as_dict(self) -> dict[str, object]:
        return {
            "schema_version": 1,
            "bytecode_gate": "PASS" if self.passed else "FAIL",
            "modified_methods": self.modified_methods,
            "injected_instructions": self.injected_instructions,
            "member_access_checks": self.member_access_checks,
            "register_flow_checks": self.register_flow_checks,
            "recognized_patch_methods": self.recognized_patch_methods,
            "findings": [finding.as_dict() for finding in sorted(set(self.findings))],
        }


class SmaliIndex:
    def __init__(self) -> None:
        self.classes: dict[str, ClassDef] = {}
        self.fields: dict[tuple[str, str, str], FieldDef] = {}
        self.methods: dict[tuple[str, str, str], MethodDef] = {}

    @classmethod
    def from_root(cls, root: Path) -> "SmaliIndex":
        index = cls()
        roots = discover_smali_roots(root)
        if not roots:
            raise ValueError(f"no smali directories or files found below {root}")
        for smali_root in roots:
            for path in sorted(smali_root.rglob("*.smali")):
                parsed = parse_smali_file(path)
                if parsed is None:
                    continue
                if parsed.descriptor in index.classes:
                    raise ValueError(f"duplicate class definition: {parsed.descriptor}")
                index.classes[parsed.descriptor] = parsed
                for field in parsed.fields.values():
                    index.fields[field.key] = field
                for method in parsed.methods.values():
                    index.methods[method.key] = method
        return index

    def is_subclass(self, child: str, ancestor: str) -> bool:
        seen: set[str] = set()
        current = child
        while current and current not in seen:
            seen.add(current)
            if current == ancestor:
                return True
            class_def = self.classes.get(current)
            current = class_def.superclass if class_def else None
        return False


def strip_comment(line: str) -> str:
    # Smali strings may contain '#'; avoid stripping inside quotes.
    quoted = False
    escaped = False
    for idx, char in enumerate(line):
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == '"':
            quoted = not quoted
            continue
        if char == "#" and not quoted:
            return line[:idx]
    return line


def discover_smali_roots(root: Path) -> list[Path]:
    if root.is_file() and root.suffix == ".smali":
        return [root.parent]
    direct = sorted(path for path in root.glob("smali*") if path.is_dir())
    if direct:
        return direct
    if any(root.rglob("*.smali")):
        return [root]
    return []


def parse_access(prefix: str) -> frozenset[str]:
    return frozenset(token for token in prefix.split() if token in ACCESS_FLAGS)


def parse_smali_file(path: Path) -> ClassDef | None:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    descriptor: str | None = None
    class_access: frozenset[str] = frozenset()
    superclass: str | None = None
    fields: dict[tuple[str, str], FieldDef] = {}
    methods: dict[tuple[str, str], MethodDef] = {}

    for raw in lines:
        text = strip_comment(raw).strip()
        if not text:
            continue
        match = CLASS_RE.match(text)
        if match:
            descriptor = match.group("descriptor")
            class_access = parse_access(match.group("flags"))
            break
    if descriptor is None:
        return None

    idx = 0
    while idx < len(lines):
        text = strip_comment(lines[idx]).strip()
        super_match = SUPER_RE.match(text)
        if super_match:
            superclass = super_match.group("descriptor")
        field_match = FIELD_RE.match(text)
        if field_match:
            field = FieldDef(
                owner=descriptor,
                name=field_match.group("name"),
                descriptor=field_match.group("type"),
                access=parse_access(field_match.group("prefix")),
            )
            fields[(field.name, field.descriptor)] = field
        method_match = METHOD_RE.match(text)
        if method_match:
            method_start = idx
            name = method_match.group("name")
            proto = method_match.group("proto")
            access = parse_access(method_match.group("prefix"))
            idx += 1
            body: list[tuple[int, str]] = []
            while idx < len(lines):
                current = strip_comment(lines[idx]).strip()
                if current == ".end method":
                    break
                body.append((idx + 1, current))
                idx += 1
            instructions, locals_count, registers_count = parse_method_body(body)
            method = MethodDef(
                owner=descriptor,
                name=name,
                proto=proto,
                access=access,
                source=path,
                line_no=method_start + 1,
                instructions=instructions,
                locals_count=locals_count,
                registers_count=registers_count,
            )
            methods[(name, proto)] = method
        idx += 1

    return ClassDef(
        descriptor=descriptor,
        access=class_access,
        superclass=superclass,
        fields=fields,
        methods=methods,
        source=path,
    )


def parse_method_body(
    body: Sequence[tuple[int, str]],
) -> tuple[list[Instruction], int | None, int | None]:
    instructions: list[Instruction] = []
    pending_labels: list[str] = []
    locals_count: int | None = None
    registers_count: int | None = None
    in_payload = False
    for line_no, text in body:
        if not text:
            continue
        if text.startswith(".locals "):
            locals_count = int(text.split()[1], 0)
            continue
        if text.startswith(".registers "):
            registers_count = int(text.split()[1], 0)
            continue
        if text.startswith((".packed-switch", ".sparse-switch", ".array-data")):
            in_payload = True
            continue
        if in_payload:
            if text.startswith(".end "):
                in_payload = False
            continue
        if text.startswith(":"):
            label = LABEL_RE.match(text)
            if label:
                pending_labels.append(label.group(1))
            continue
        if text.startswith("."):
            continue
        opcode = text.split(None, 1)[0]
        instructions.append(
            Instruction(
                index=len(instructions),
                line_no=line_no,
                text=text,
                opcode=opcode,
                labels=tuple(pending_labels),
            )
        )
        pending_labels.clear()
    return instructions, locals_count, registers_count


def split_proto(proto: str) -> tuple[list[str], str]:
    if not proto.startswith("(") or ")" not in proto:
        raise ValueError(f"invalid method proto: {proto}")
    args_raw, return_type = proto[1:].split(")", 1)
    args: list[str] = []
    idx = 0
    while idx < len(args_raw):
        start = idx
        while idx < len(args_raw) and args_raw[idx] == "[":
            idx += 1
        if idx >= len(args_raw):
            raise ValueError(f"invalid argument descriptor in {proto}")
        if args_raw[idx] == "L":
            end = args_raw.find(";", idx)
            if end < 0:
                raise ValueError(f"invalid object descriptor in {proto}")
            idx = end + 1
        else:
            idx += 1
        args.append(args_raw[start:idx])
    return args, return_type


def package_of(descriptor: str) -> str:
    body = descriptor[1:-1] if descriptor.startswith("L") and descriptor.endswith(";") else descriptor
    return body.rsplit("/", 1)[0] if "/" in body else ""


def is_reference(descriptor: str) -> bool:
    return descriptor.startswith("L") or descriptor.startswith("[") or descriptor in {
        "REF",
        "NULL",
    } or descriptor.startswith("UNINIT:")


def type_category(descriptor: str) -> str:
    if descriptor in PRIMITIVES:
        return "PRIMITIVE"
    if descriptor in WIDE_PRIMITIVES:
        return "WIDE"
    if descriptor == "WIDE_HIGH":
        return "WIDE_HIGH"
    if is_reference(descriptor):
        return "REFERENCE"
    if descriptor in {"UNKNOWN", "CONFLICT"}:
        return descriptor
    if descriptor == "V":
        return "VOID"
    return "UNKNOWN"


def method_changed(base: MethodDef, candidate: MethodDef) -> bool:
    return [i.normalized() for i in base.instructions] != [
        i.normalized() for i in candidate.instructions
    ] or base.total_registers() != candidate.total_registers()


def changed_candidate_indices(base: MethodDef, candidate: MethodDef) -> set[int]:
    base_seq = [item.normalized() for item in base.instructions]
    candidate_seq = [item.normalized() for item in candidate.instructions]
    matcher = difflib.SequenceMatcher(a=base_seq, b=candidate_seq, autojunk=False)
    changed: set[int] = set()
    for tag, _i1, _i2, j1, j2 in matcher.get_opcodes():
        if tag in {"insert", "replace"}:
            changed.update(range(j1, j2))
    # A register frame change affects the entire candidate method.
    if base.total_registers() != candidate.total_registers():
        changed.update(range(len(candidate.instructions)))
    return changed


def parse_member(text: str) -> tuple[str, str, str, bool] | None:
    match = MEMBER_RE.search(text)
    if not match:
        return None
    if match.group("field") is not None:
        return (
            match.group("class"),
            match.group("name"),
            match.group("field"),
            False,
        )
    return (
        match.group("class"),
        match.group("name"),
        match.group("method"),
        True,
    )


def parse_registers(text: str) -> list[str]:
    brace = re.search(r"\{([^}]*)\}", text)
    if not brace:
        return REGISTER_RE.findall(text)
    content = brace.group(1).strip()
    if ".." in content:
        start_raw, end_raw = [part.strip() for part in content.split("..", 1)]
        if start_raw[0] != end_raw[0]:
            return []
        start = int(start_raw[1:])
        end = int(end_raw[1:])
        return [f"{start_raw[0]}{number}" for number in range(start, end + 1)]
    return REGISTER_RE.findall(content)


def branch_target(instruction: Instruction) -> str | None:
    labels = re.findall(r":[A-Za-z0-9_.$-]+", instruction.text)
    return labels[-1] if labels else None


def successors(method: MethodDef) -> list[list[int]]:
    label_map: dict[str, int] = {}
    for instruction in method.instructions:
        for label in instruction.labels:
            label_map[label] = instruction.index
    result: list[list[int]] = []
    for instruction in method.instructions:
        opcode = instruction.opcode
        next_index = instruction.index + 1
        items: list[int] = []
        if opcode.startswith("goto"):
            target = branch_target(instruction)
            if target in label_map:
                items.append(label_map[target])
        elif opcode.startswith("if-"):
            if next_index < len(method.instructions):
                items.append(next_index)
            target = branch_target(instruction)
            if target in label_map:
                items.append(label_map[target])
        elif opcode.startswith(("return", "throw")):
            pass
        else:
            if next_index < len(method.instructions):
                items.append(next_index)
        result.append(sorted(set(items)))
    return result


def predecessor_map(successor_list: Sequence[Sequence[int]]) -> list[list[int]]:
    result = [[] for _ in successor_list]
    for source, targets in enumerate(successor_list):
        for target in targets:
            result[target].append(source)
    return result


def merge_type(left: str, right: str) -> str:
    if left == right:
        return left
    if left == "UNKNOWN" or right == "UNKNOWN":
        return "UNKNOWN"
    if left == "NULL" and is_reference(right):
        return right
    if right == "NULL" and is_reference(left):
        return left
    if is_reference(left) and is_reference(right):
        if left.startswith("UNINIT:") or right.startswith("UNINIT:"):
            return "CONFLICT"
        return "REF"
    return "CONFLICT"


def merge_states(states: Sequence[tuple[str, ...]], width: int) -> tuple[str, ...]:
    if not states:
        return tuple("UNKNOWN" for _ in range(width))
    merged = list(states[0])
    for state in states[1:]:
        for idx in range(width):
            merged[idx] = merge_type(merged[idx], state[idx])
    return tuple(merged)


def type_compatible(actual: str, expected: str, *, constructor_receiver: bool = False) -> tuple[bool, str]:
    expected_category = type_category(expected)
    actual_category = type_category(actual)
    if actual == "CONFLICT":
        return False, "CONTROL_FLOW_JOIN_TYPE_CONFLICT"
    if actual == "UNKNOWN":
        return False, "REGISTER_TYPE_UNKNOWN"
    if expected_category == "REFERENCE":
        if actual.startswith("UNINIT:"):
            if constructor_receiver and actual.startswith(f"UNINIT:{expected}@"):
                return True, ""
            return False, "UNINITIALIZED_REFERENCE"
        if actual_category == "REFERENCE":
            return True, ""
        return False, "OBJECT_USE_FROM_PRIMITIVE_REGISTER"
    if expected_category == "WIDE":
        if actual_category == "WIDE":
            return True, ""
        return False, "INVOKE_ARGUMENT_TYPE_MISMATCH"
    if expected_category == "PRIMITIVE":
        if actual_category == "PRIMITIVE":
            return True, ""
        return False, "INVOKE_ARGUMENT_TYPE_MISMATCH"
    return True, ""


def member_access_reason(
    caller: MethodDef,
    owner: str,
    access: frozenset[str],
    index: SmaliIndex,
) -> str | None:
    if "public" in access:
        return None
    if "private" in access:
        return None if caller.owner == owner else "PRIVATE_MEMBER_INACCESSIBLE"
    if "protected" in access:
        if package_of(caller.owner) == package_of(owner):
            return None
        if index.is_subclass(caller.owner, owner):
            return None
        return "PROTECTED_MEMBER_INACCESSIBLE"
    if package_of(caller.owner) != package_of(owner):
        return "PACKAGE_PRIVATE_MEMBER_INACCESSIBLE"
    return None


def field_opcode_expected(opcode: str, descriptor: str) -> str | None:
    if opcode.endswith("-object"):
        return None if is_reference(descriptor) else "FIELD_OPCODE_DESCRIPTOR_MISMATCH"
    if opcode.endswith("-wide"):
        return None if descriptor in WIDE_PRIMITIVES else "FIELD_OPCODE_DESCRIPTOR_MISMATCH"
    if descriptor in WIDE_PRIMITIVES or is_reference(descriptor):
        return "FIELD_OPCODE_DESCRIPTOR_MISMATCH"
    return None


def finding(
    method: MethodDef,
    instruction: Instruction,
    reason: str,
    *,
    member: str = "",
    detail: str = "",
) -> Finding:
    return Finding(
        class_descriptor=method.owner,
        method=f"{method.name}{method.proto}",
        instruction_index=instruction.index,
        line_no=instruction.line_no,
        instruction=instruction.text,
        reason=reason,
        member=member,
        detail=detail,
    )


def validate_member_instruction(
    method: MethodDef,
    instruction: Instruction,
    candidate_index: SmaliIndex,
) -> list[Finding]:
    parsed = parse_member(instruction.text)
    if parsed is None:
        return []
    owner, name, descriptor, is_method = parsed
    opcode = instruction.opcode
    findings: list[Finding] = []
    member_display = f"{owner}->{name}{descriptor if is_method else ':' + descriptor}"

    if is_method:
        definition = candidate_index.methods.get((owner, name, descriptor))
        opcode_static = opcode.startswith("invoke-static")
        if definition is not None:
            definition_static = "static" in definition.access
            if opcode_static != definition_static:
                findings.append(
                    finding(
                        method,
                        instruction,
                        "STATIC_INSTANCE_METHOD_MISMATCH",
                        member=member_display,
                    )
                )
            reason = member_access_reason(method, owner, definition.access, candidate_index)
            if reason:
                findings.append(finding(method, instruction, reason, member=member_display))
        elif not owner.startswith(PLATFORM_PREFIXES):
            findings.append(
                finding(
                    method,
                    instruction,
                    "UNRESOLVED_METHOD_REFERENCE",
                    member=member_display,
                )
            )
    else:
        definition = candidate_index.fields.get((owner, name, descriptor))
        opcode_static = opcode.startswith(("sget", "sput"))
        if definition is not None:
            definition_static = "static" in definition.access
            if opcode_static != definition_static:
                findings.append(
                    finding(
                        method,
                        instruction,
                        "STATIC_INSTANCE_FIELD_MISMATCH",
                        member=member_display,
                    )
                )
            reason = member_access_reason(method, owner, definition.access, candidate_index)
            if reason:
                specific = reason.replace("MEMBER", "FIELD")
                findings.append(finding(method, instruction, specific, member=member_display))
            mismatch = field_opcode_expected(opcode, descriptor)
            if mismatch:
                findings.append(finding(method, instruction, mismatch, member=member_display))
        elif not owner.startswith(PLATFORM_PREFIXES):
            findings.append(
                finding(
                    method,
                    instruction,
                    "UNRESOLVED_FIELD_REFERENCE",
                    member=member_display,
                )
            )
    return findings


class MethodFlowAnalyzer:
    def __init__(
        self,
        method: MethodDef,
        changed: set[int],
        index: SmaliIndex,
    ) -> None:
        self.method = method
        self.changed = changed
        self.index = index
        self.findings: list[Finding] = []
        self.checks = 0
        self.total = method.total_registers()
        self.successors = successors(method)
        self.predecessors = predecessor_map(self.successors)
        self.in_states: list[tuple[str, ...] | None] = [None] * len(method.instructions)
        self.out_states: list[tuple[str, ...] | None] = [None] * len(method.instructions)

    def initial_state(self) -> tuple[str, ...]:
        if self.total is None or self.total < 0:
            return ()
        values = ["UNKNOWN"] * self.total
        local_count = self.method.local_registers()
        if local_count is None or local_count < 0:
            return tuple(values)
        params = self.method.parameter_word_types()
        for offset, descriptor in enumerate(params):
            absolute = local_count + offset
            if absolute < self.total:
                values[absolute] = descriptor
        return tuple(values)

    def reg_index(self, register: str) -> int | None:
        absolute = self.method.absolute_register(register)
        if absolute is None or self.total is None or absolute < 0 or absolute >= self.total:
            return None
        return absolute

    def read(self, state: list[str], register: str, instruction: Instruction) -> str:
        idx = self.reg_index(register)
        if idx is None:
            if instruction.index in self.changed:
                self.findings.append(
                    finding(
                        self.method,
                        instruction,
                        "REGISTER_OUT_OF_BOUNDS",
                        detail=f"register={register} total={self.total}",
                    )
                )
            return "UNKNOWN"
        return state[idx]

    def write(self, state: list[str], register: str, value: str, instruction: Instruction) -> None:
        idx = self.reg_index(register)
        if idx is None:
            if instruction.index in self.changed:
                self.findings.append(
                    finding(
                        self.method,
                        instruction,
                        "REGISTER_OUT_OF_BOUNDS",
                        detail=f"register={register} total={self.total}",
                    )
                )
            return
        state[idx] = value
        if value in WIDE_PRIMITIVES and idx + 1 < len(state):
            state[idx + 1] = "WIDE_HIGH"

    def validate_use(
        self,
        actual: str,
        expected: str,
        instruction: Instruction,
        register: str,
        *,
        constructor_receiver: bool = False,
        reason_override: str | None = None,
    ) -> None:
        if instruction.index not in self.changed:
            return
        self.checks += 1
        ok, reason = type_compatible(
            actual,
            expected,
            constructor_receiver=constructor_receiver,
        )
        if not ok:
            self.findings.append(
                finding(
                    self.method,
                    instruction,
                    reason_override or reason,
                    detail=f"register={register} actual={actual} expected={expected}",
                )
            )

    def invoke_transfer(self, state: list[str], instruction: Instruction) -> None:
        parsed = parse_member(instruction.text)
        if parsed is None or not parsed[3]:
            if instruction.index in self.changed:
                self.findings.append(finding(self.method, instruction, "MALFORMED_INVOKE_REFERENCE"))
            return
        owner, name, proto, _ = parsed
        registers = parse_registers(instruction.text)
        args, _return_type = split_proto(proto)
        static = instruction.opcode.startswith("invoke-static")
        expected: list[str] = list(args)
        if not static:
            expected.insert(0, owner)
        expected_words: list[str] = []
        for descriptor in expected:
            expected_words.append(descriptor)
            if descriptor in WIDE_PRIMITIVES:
                expected_words.append("WIDE_HIGH")
        if len(registers) != len(expected_words):
            if instruction.index in self.changed:
                self.findings.append(
                    finding(
                        self.method,
                        instruction,
                        "INVOKE_REGISTER_COUNT_MISMATCH",
                        member=f"{owner}->{name}{proto}",
                        detail=f"registers={len(registers)} expected_words={len(expected_words)}",
                    )
                )
            return
        uninit_token: str | None = None
        for position, (register, descriptor) in enumerate(zip(registers, expected_words)):
            if descriptor == "WIDE_HIGH":
                continue
            actual = self.read(state, register, instruction)
            constructor_receiver = name == "<init>" and position == 0
            self.validate_use(
                actual,
                descriptor,
                instruction,
                register,
                constructor_receiver=constructor_receiver,
            )
            if constructor_receiver and actual.startswith("UNINIT:"):
                uninit_token = actual
        if name == "<init>" and uninit_token is not None:
            initialized = uninit_token.removeprefix("UNINIT:").split("@", 1)[0]
            for idx, value in enumerate(state):
                if value == uninit_token:
                    state[idx] = initialized

    def field_transfer(self, state: list[str], instruction: Instruction) -> None:
        parsed = parse_member(instruction.text)
        if parsed is None or parsed[3]:
            return
        owner, _name, descriptor, _ = parsed
        registers = parse_registers(instruction.text)
        opcode = instruction.opcode
        is_get = opcode.startswith(("iget", "sget"))
        is_static = opcode.startswith(("sget", "sput"))
        if is_get:
            if not registers:
                return
            destination = registers[0]
            if not is_static:
                if len(registers) < 2:
                    return
                receiver = registers[1]
                actual = self.read(state, receiver, instruction)
                self.validate_use(actual, owner, instruction, receiver)
            self.write(state, destination, descriptor, instruction)
        else:
            if not registers:
                return
            value_reg = registers[0]
            value = self.read(state, value_reg, instruction)
            self.validate_use(value, descriptor, instruction, value_reg)
            if not is_static and len(registers) >= 2:
                receiver = registers[1]
                actual = self.read(state, receiver, instruction)
                self.validate_use(actual, owner, instruction, receiver)

    def transfer(self, incoming: tuple[str, ...], instruction: Instruction) -> tuple[str, ...]:
        state = list(incoming)
        opcode = instruction.opcode
        registers = parse_registers(instruction.text)

        if opcode.startswith("const-string") and registers:
            self.write(state, registers[0], "Ljava/lang/String;", instruction)
        elif opcode.startswith("const-class") and registers:
            self.write(state, registers[0], "Ljava/lang/Class;", instruction)
        elif opcode.startswith("const-wide") and registers:
            self.write(state, registers[0], "J", instruction)
        elif opcode.startswith("const") and registers:
            self.write(state, registers[0], "I", instruction)
        elif opcode.startswith("move-object") and len(registers) >= 2:
            value = self.read(state, registers[1], instruction)
            self.write(state, registers[0], value, instruction)
        elif opcode.startswith("move-wide") and len(registers) >= 2:
            value = self.read(state, registers[1], instruction)
            self.write(state, registers[0], value, instruction)
        elif opcode.startswith("move") and not opcode.startswith("move-result") and len(registers) >= 2:
            value = self.read(state, registers[1], instruction)
            self.write(state, registers[0], value, instruction)
        elif opcode == "new-instance" and registers:
            match = re.search(r"(L[^;]+;)", instruction.text)
            descriptor = match.group(1) if match else "L?;"
            token = f"UNINIT:{descriptor}@{instruction.index}"
            self.write(state, registers[0], token, instruction)
        elif opcode == "check-cast" and registers:
            match = re.search(r",\s*(L[^;]+;|\[[^\s]+)$", instruction.text)
            expected = match.group(1) if match else "REF"
            actual = self.read(state, registers[0], instruction)
            self.validate_use(actual, "REF", instruction, registers[0])
            self.write(state, registers[0], expected, instruction)
        elif opcode == "instance-of" and len(registers) >= 2:
            actual = self.read(state, registers[1], instruction)
            self.validate_use(actual, "REF", instruction, registers[1])
            self.write(state, registers[0], "Z", instruction)
        elif opcode.startswith(("iget", "iput", "sget", "sput")):
            self.field_transfer(state, instruction)
        elif opcode.startswith("invoke-"):
            self.invoke_transfer(state, instruction)
        elif opcode.startswith("move-result") and registers:
            previous = self.method.instructions[instruction.index - 1] if instruction.index > 0 else None
            if previous is None or not previous.opcode.startswith("invoke-"):
                if instruction.index in self.changed:
                    self.findings.append(finding(self.method, instruction, "MOVE_RESULT_WITHOUT_INVOKE"))
            else:
                parsed = parse_member(previous.text)
                return_type = "UNKNOWN"
                if parsed and parsed[3]:
                    _args, return_type = split_proto(parsed[2])
                if return_type == "V":
                    if instruction.index in self.changed or previous.index in self.changed:
                        self.findings.append(finding(self.method, instruction, "MOVE_RESULT_AFTER_VOID_INVOKE"))
                expected_opcode = (
                    "move-result-object"
                    if is_reference(return_type)
                    else "move-result-wide"
                    if return_type in WIDE_PRIMITIVES
                    else "move-result"
                )
                if opcode != expected_opcode and (instruction.index in self.changed or previous.index in self.changed):
                    self.findings.append(
                        finding(
                            self.method,
                            instruction,
                            "MOVE_RESULT_DESCRIPTOR_MISMATCH",
                            detail=f"expected={expected_opcode} return={return_type}",
                        )
                    )
                self.write(state, registers[0], return_type, instruction)
        elif opcode.startswith(("add-", "sub-", "mul-", "div-", "rem-", "and-", "or-", "xor-", "shl-", "shr-", "ushr-", "neg-", "not-", "cmp")) and registers:
            self.write(state, registers[0], "I", instruction)
        elif opcode.startswith(("int-to-", "float-to-", "double-to-", "long-to-")) and registers:
            target = opcode.split("-to-", 1)[1]
            descriptor = {"int": "I", "float": "F", "long": "J", "double": "D", "byte": "B", "char": "C", "short": "S"}.get(target, "I")
            self.write(state, registers[0], descriptor, instruction)
        elif opcode == "move-exception" and registers:
            self.write(state, registers[0], "Ljava/lang/Throwable;", instruction)
        elif opcode.startswith("return-object") and registers:
            actual = self.read(state, registers[0], instruction)
            _args, expected = split_proto(self.method.proto)
            self.validate_use(actual, expected, instruction, registers[0])
        elif opcode.startswith("return-wide") and registers:
            actual = self.read(state, registers[0], instruction)
            _args, expected = split_proto(self.method.proto)
            self.validate_use(actual, expected, instruction, registers[0])
        elif opcode == "return" and registers:
            actual = self.read(state, registers[0], instruction)
            _args, expected = split_proto(self.method.proto)
            self.validate_use(actual, expected, instruction, registers[0])
        elif opcode == "throw" and registers:
            actual = self.read(state, registers[0], instruction)
            self.validate_use(actual, "Ljava/lang/Throwable;", instruction, registers[0])
        elif opcode in {"monitor-enter", "monitor-exit"} and registers:
            actual = self.read(state, registers[0], instruction)
            self.validate_use(actual, "REF", instruction, registers[0])

        return tuple(state)

    def run(self) -> tuple[list[Finding], int]:
        if self.total is None or self.total <= 0:
            if self.changed and self.method.instructions:
                instruction = self.method.instructions[min(self.changed)]
                return [finding(self.method, instruction, "REGISTER_FRAME_UNAVAILABLE")], 0
            return [], 0
        if not self.method.instructions:
            return [], 0

        self.in_states[0] = self.initial_state()
        work: deque[int] = deque([0])
        iterations = 0
        limit = max(100, len(self.method.instructions) * 40)
        while work:
            index = work.popleft()
            iterations += 1
            if iterations > limit:
                instruction = self.method.instructions[min(self.changed or {0})]
                self.findings.append(finding(self.method, instruction, "DATAFLOW_DID_NOT_CONVERGE"))
                break
            incoming = self.in_states[index]
            if incoming is None:
                continue
            outgoing = self.transfer(incoming, self.method.instructions[index])
            if outgoing == self.out_states[index]:
                continue
            self.out_states[index] = outgoing
            for target in self.successors[index]:
                predecessor_states = [
                    self.out_states[pred]
                    for pred in self.predecessors[target]
                    if self.out_states[pred] is not None
                ]
                merged = merge_states(
                    [state for state in predecessor_states if state is not None], self.total
                )
                if merged != self.in_states[target]:
                    self.in_states[target] = merged
                    work.append(target)

        return self.findings, self.checks


def read_patch_result(path: Path) -> tuple[object, set[str]]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ValueError(f"patch result is unavailable: {path}: {exc}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"patch result is invalid JSON: {path}: {exc}") from exc
    recognized: set[str] = set()

    def walk(value: object) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                walk(key)
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)
        elif isinstance(value, str):
            recognized.update(METHOD_DESCRIPTOR_RE.findall(value))

    walk(payload)
    return payload, recognized


def analyze(
    base_root: Path,
    candidate_root: Path,
    patch_result: Path,
    *,
    critical_prefixes: Sequence[str],
) -> AnalysisResult:
    _payload, recognized = read_patch_result(patch_result)
    base_index = SmaliIndex.from_root(base_root)
    candidate_index = SmaliIndex.from_root(candidate_root)

    findings: list[Finding] = []
    modified_methods = 0
    injected_instructions = 0
    member_checks = 0
    flow_checks = 0

    for key in sorted(candidate_index.methods):
        candidate_method = candidate_index.methods[key]
        if not candidate_method.owner.startswith(tuple(critical_prefixes)):
            continue
        base_method = base_index.methods.get(key)
        if base_method is None:
            # Analyze methods added to a class that existed in the base APK. New
            # Morphe extension classes use their own namespace and are resolved as
            # callees, not treated as injected Boost methods.
            if candidate_method.owner not in base_index.classes:
                continue
            changed = set(range(len(candidate_method.instructions)))
        else:
            if not method_changed(base_method, candidate_method):
                continue
            changed = changed_candidate_indices(base_method, candidate_method)
            if not changed and base_method.total_registers() == candidate_method.total_registers():
                continue
        modified_methods += 1
        injected_instructions += len(changed)

        for index in sorted(changed):
            if index >= len(candidate_method.instructions):
                continue
            instruction = candidate_method.instructions[index]
            if parse_member(instruction.text) is not None:
                member_checks += 1
                findings.extend(
                    validate_member_instruction(candidate_method, instruction, candidate_index)
                )

        flow = MethodFlowAnalyzer(candidate_method, changed, candidate_index)
        flow_findings, checks = flow.run()
        findings.extend(flow_findings)
        flow_checks += checks

    unique = sorted(set(findings))
    return AnalysisResult(
        findings=unique,
        modified_methods=modified_methods,
        injected_instructions=injected_instructions,
        member_access_checks=member_checks,
        register_flow_checks=flow_checks,
        recognized_patch_methods=len(recognized),
    )


def descriptor_to_dot(descriptor: str) -> str:
    if descriptor.startswith("L") and descriptor.endswith(";"):
        return descriptor[1:-1].replace("/", ".")
    return descriptor


def print_result(result: AnalysisResult) -> None:
    if result.passed:
        print("BYTECODE_GATE=PASS")
    else:
        print("BYTECODE_GATE=FAIL")
        for item in result.findings:
            print(f"CLASS={descriptor_to_dot(item.class_descriptor)}")
            print(f"METHOD={item.method}")
            print(f"INSTRUCTION_INDEX={item.instruction_index}")
            print(f"LINE={item.line_no}")
            print(f"INSTRUCTION={item.instruction}")
            print(f"REASON={item.reason}")
            if item.member:
                print(f"MEMBER={item.member}")
            if item.detail:
                print(f"DETAIL={item.detail}")
            print("---")
    print(f"MODIFIED_METHODS={result.modified_methods}")
    print(f"INJECTED_INSTRUCTIONS={result.injected_instructions}")
    print(f"MEMBER_ACCESS_CHECKS={result.member_access_checks}")
    print(f"REGISTER_FLOW_CHECKS={result.register_flow_checks}")
    print(f"PATCH_RESULT_METHOD_HINTS={result.recognized_patch_methods}")
    print(f"FINDING_COUNT={len(result.findings)}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Analyze decoded base/candidate Smali deltas")
    parser.add_argument("--base-smali", required=True, type=Path)
    parser.add_argument("--candidate-smali", required=True, type=Path)
    parser.add_argument("--patch-result", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument(
        "--critical-prefix",
        action="append",
        default=[],
        help="Class descriptor prefix to analyze; repeatable",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    prefixes = args.critical_prefix or ["Lcom/rubenmayayo/reddit/"]
    try:
        result = analyze(
            args.base_smali.resolve(),
            args.candidate_smali.resolve(),
            args.patch_result.resolve(),
            critical_prefixes=prefixes,
        )
    except (OSError, ValueError) as exc:
        print("BYTECODE_GATE=FAIL")
        print("REASON=ANALYSIS_UNAVAILABLE")
        print(f"DETAIL={exc}")
        return 2
    print_result(result)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(
            json.dumps(result.as_dict(), indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    return 0 if result.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
