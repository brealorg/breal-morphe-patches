#!/usr/bin/env python3
"""Fail when a patched Boost APK references members that do not exist at runtime.

The extension is compiled against stock libraries (OkHttp 5, okio, Jackson,
androidx), but runs against Boost's bundled copies, which R8 has stripped and
partly obfuscated. A call that compiles can then fail with NoSuchMethodError
(issue #219: okhttp3.Call.timeout() returns an obfuscated okio type in Boost).

Every method and field reference in the candidate APK is resolved against the
classes the candidate itself defines, walking superclasses and interfaces.
References that are already unresolved in the stock APK are Boost's own and
are ignored. A referenced class outside the Android framework packages that the
APK does not define is reported as missing. Framework classes are not modeled:
a walk that reaches a framework superclass is accepted, except java.lang.Object,
which only provides its own methods. Framework interfaces are not trusted, so
library classes that only implement e.g. Cloneable still fail the lookup.
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path

NO_INDEX = 0xFFFFFFFF
OBJECT = "Ljava/lang/Object;"
# Packages the device provides. Everything else (including androidx and kotlin) must be in the APK.
FRAMEWORK_PREFIXES = (
    "Ljava/", "Ljavax/", "Landroid/", "Ldalvik/", "Lorg/json/", "Lorg/xml/", "Lorg/w3c/",
    "Lorg/apache/http/", "Lsun/", "Llibcore/", "Lcom/android/",
    # Annotation types: the runtime ignores annotations whose class is missing.
    "Landroidx/annotation/", "Lorg/jetbrains/annotations/",
)
OBJECT_METHODS = {
    ("equals", "(Ljava/lang/Object;)Z"), ("hashCode", "()I"),
    ("toString", "()Ljava/lang/String;"), ("getClass", "()Ljava/lang/Class;"),
    ("notify", "()V"), ("notifyAll", "()V"), ("wait", "()V"), ("wait", "(J)V"),
    ("wait", "(JI)V"), ("clone", "()Ljava/lang/Object;"), ("finalize", "()V"),
    ("<init>", "()V"),
}


@dataclass
class ClassDef:
    superclass: str | None
    interfaces: list[str]
    methods: set[tuple[str, str]] = field(default_factory=set)
    fields: set[tuple[str, str]] = field(default_factory=set)


@dataclass
class Model:
    classes: dict[str, ClassDef] = field(default_factory=dict)
    # (kind, owner, name, descriptor); kind is "method", "field" or "class" (name/descriptor empty).
    refs: set[tuple[str, str, str, str]] = field(default_factory=set)


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, offset
        shift += 7


def _string(data: bytes, offset: int) -> str:
    _, offset = _uleb128(data, offset)  # UTF-16 length
    end = data.index(b"\x00", offset)
    return data[offset:end].decode("utf-8", errors="surrogateescape")


# Size in 16-bit code units of each Dalvik opcode, from its instruction format.
_INSN_UNITS = [1] * 256
for _op, _units in (
    ((0x02, 0x05, 0x08), 2), ((0x03, 0x06, 0x09), 3),
    ((0x13, 0x15, 0x16, 0x19, 0x1a, 0x1c, 0x1f, 0x20, 0x22, 0x23, 0x29), 2),
    ((0x14, 0x17, 0x1b, 0x24, 0x25, 0x26, 0x2a, 0x2b, 0x2c), 3),
    ((0x18,), 5),
    (range(0x2d, 0x32), 2), (range(0x32, 0x38), 2), (range(0x38, 0x3e), 2),
    (range(0x44, 0x52), 2), (range(0x52, 0x6e), 2),
    (range(0x6e, 0x73), 3), (range(0x74, 0x79), 3),
    (range(0x90, 0xb0), 2), (range(0xd0, 0xe3), 2),
    ((0xfa, 0xfb), 4), ((0xfc, 0xfd), 3), ((0xfe, 0xff), 2),
):
    for _code in _op:
        _INSN_UNITS[_code] = _units

# Opcodes whose first index operand is a field or a method.
_FIELD_OPS = frozenset(range(0x52, 0x6e))
_METHOD_OPS = frozenset([*range(0x6e, 0x73), *range(0x74, 0x79), 0xfa, 0xfb])


def _code_refs(data: bytes, code_off: int, fields: set[int], methods: set[int]) -> None:
    """Collects the field and method ids that the instructions of a code item use."""
    insns_size = struct.unpack_from("<I", data, code_off + 12)[0]
    start = code_off + 16
    pc = 0
    while pc < insns_size:
        unit = struct.unpack_from("<H", data, start + 2 * pc)[0]
        op = unit & 0xff
        if op == 0x00 and unit == 0x0100:  # packed-switch payload
            size = struct.unpack_from("<H", data, start + 2 * pc + 2)[0]
            pc += size * 2 + 4
        elif op == 0x00 and unit == 0x0200:  # sparse-switch payload
            size = struct.unpack_from("<H", data, start + 2 * pc + 2)[0]
            pc += size * 4 + 2
        elif op == 0x00 and unit == 0x0300:  # fill-array-data payload
            width, size = struct.unpack_from("<HI", data, start + 2 * pc + 2)
            pc += (size * width + 1) // 2 + 4
        else:
            if op in _FIELD_OPS:
                fields.add(struct.unpack_from("<H", data, start + 2 * pc + 2)[0])
            elif op in _METHOD_OPS:
                methods.add(struct.unpack_from("<H", data, start + 2 * pc + 2)[0])
            pc += _INSN_UNITS[op]
    if pc != insns_size:
        raise ValueError(f"code item at {code_off:#x} does not end on an instruction boundary")


def parse_dex(data: bytes, model: Model) -> None:
    if not data.startswith(b"dex\n"):
        raise ValueError("not a dex file")
    (string_ids_size, string_ids_off, type_ids_size, type_ids_off,
     proto_ids_size, proto_ids_off, field_ids_size, field_ids_off,
     method_ids_size, method_ids_off, class_defs_size, class_defs_off) = struct.unpack_from("<12I", data, 0x38)

    strings = [_string(data, struct.unpack_from("<I", data, string_ids_off + 4 * i)[0])
               for i in range(string_ids_size)]
    types = [strings[struct.unpack_from("<I", data, type_ids_off + 4 * i)[0]] for i in range(type_ids_size)]

    def type_list(offset: int) -> list[str]:
        if offset == 0:
            return []
        size = struct.unpack_from("<I", data, offset)[0]
        return [types[struct.unpack_from("<H", data, offset + 4 + 2 * i)[0]] for i in range(size)]

    protos = []
    for i in range(proto_ids_size):
        _, return_idx, params_off = struct.unpack_from("<3I", data, proto_ids_off + 12 * i)
        protos.append("(" + "".join(type_list(params_off)) + ")" + types[return_idx])

    field_ids = []
    for i in range(field_ids_size):
        class_idx, type_idx, name_idx = struct.unpack_from("<HHI", data, field_ids_off + 8 * i)
        field_ids.append((types[class_idx], strings[name_idx], types[type_idx]))
    method_ids = []
    for i in range(method_ids_size):
        class_idx, proto_idx, name_idx = struct.unpack_from("<HHI", data, method_ids_off + 8 * i)
        method_ids.append((types[class_idx], strings[name_idx], protos[proto_idx]))

    model.refs.update(("class", t.lstrip("["), "", "") for t in types if t.lstrip("[").startswith("L"))
    # Only members that code uses can fail to link. A copied DEX file keeps the ids of the
    # classes dropped from it, which nothing calls, so the id tables alone overreport.
    used_fields: set[int] = set()
    used_methods: set[int] = set()

    for i in range(class_defs_size):
        (class_idx, _, superclass_idx, interfaces_off, _, _, class_data_off, _) = struct.unpack_from(
            "<8I", data, class_defs_off + 32 * i)
        name = types[class_idx]
        definition = ClassDef(
            superclass=None if superclass_idx == NO_INDEX else types[superclass_idx],
            interfaces=type_list(interfaces_off),
        )
        if class_data_off:
            offset = class_data_off
            sizes = []
            for _ in range(4):
                value, offset = _uleb128(data, offset)
                sizes.append(value)
            for count, target, ids in ((sizes[0], definition.fields, field_ids),
                                       (sizes[1], definition.fields, field_ids),
                                       (sizes[2], definition.methods, method_ids),
                                       (sizes[3], definition.methods, method_ids)):
                index = 0
                for _ in range(count):
                    diff, offset = _uleb128(data, offset)
                    _, offset = _uleb128(data, offset)  # access flags
                    if ids is method_ids:
                        code_off, offset = _uleb128(data, offset)
                        if code_off:
                            _code_refs(data, code_off, used_fields, used_methods)
                    index += diff
                    _, member_name, descriptor = ids[index]
                    target.add((member_name, descriptor))
        # Multidex APKs must not define a class twice; keep the first like ART does.
        model.classes.setdefault(name, definition)

    model.refs.update(("field",) + field_ids[i] for i in used_fields)
    model.refs.update(("method",) + method_ids[i] for i in used_methods)


def load_apk(path: Path) -> Model:
    model = Model()
    with zipfile.ZipFile(path) as apk:
        dex_names = sorted((n for n in apk.namelist() if n.startswith("classes") and n.endswith(".dex")),
                           key=lambda n: (len(n), n))
        if not dex_names:
            raise ValueError(f"{path}: no classes*.dex")
        for name in dex_names:
            parse_dex(apk.read(name), model)
    return model


def resolves(model: Model, kind: str, owner: str, name: str, descriptor: str) -> bool:
    member = (name, descriptor)
    pending = [(owner, True)]
    seen = set()
    while pending:
        current, via_superclass = pending.pop()
        if current in seen:
            continue
        seen.add(current)
        definition = model.classes.get(current)
        if definition is None:
            if current == OBJECT:
                if kind == "method" and member in OBJECT_METHODS:
                    return True
            elif via_superclass:
                return True  # Android framework superclass; not modeled here.
            continue
        if member in (definition.methods if kind == "method" else definition.fields):
            return True
        if definition.superclass:
            pending.append((definition.superclass, True))
        pending.extend((interface, False) for interface in definition.interfaces)
    return False


def unresolved(model: Model) -> set[tuple[str, str, str, str]]:
    missing = set()
    for ref in model.refs:
        kind, owner = ref[0], ref[1]
        if owner.startswith("["):
            continue
        if owner not in model.classes:
            if kind == "class" and not owner.startswith(FRAMEWORK_PREFIXES):
                missing.add(ref)
        elif kind != "class" and not resolves(model, *ref):
            missing.add(ref)
    return missing


def check(base: Model, candidate: Model) -> list[tuple[str, str, str, str]]:
    return sorted(unresolved(candidate) - unresolved(base))


def format_ref(ref: tuple[str, str, str, str]) -> str:
    kind, owner, name, descriptor = ref
    if kind == "class":
        return f"{owner} (class)"
    return f"{owner}->{name}{'' if kind == 'method' else ':'}{descriptor}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-apk", required=True, type=Path, help="Stock Boost APK the candidate was patched from.")
    parser.add_argument("--candidate-apk", required=True, type=Path, help="Patched APK to check.")
    parser.add_argument("--report", type=Path, help="Optional JSON report path.")
    args = parser.parse_args(argv)

    try:
        base = load_apk(args.base_apk)
        candidate = load_apk(args.candidate_apk)
    except (OSError, ValueError, zipfile.BadZipFile, struct.error, IndexError) as error:
        print(f"LINKAGE_GATE=FAIL could not read APK: {error}")
        return 2

    missing = check(base, candidate)
    print(f"LINKAGE_REFS_CHECKED={len(candidate.refs)}")
    print(f"LINKAGE_MISSING={len(missing)}")
    for ref in missing:
        print(f"MISSING {format_ref(ref)}")
    if args.report:
        args.report.write_text(json.dumps({"missing": [format_ref(r) for r in missing]}, indent=2) + "\n")
    print("LINKAGE_GATE=" + ("FAIL" if missing else "PASS"))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
