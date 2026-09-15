#!/usr/bin/env python3
"""Check direct Jackson member references in the #196 recovery classes.

Input is smali freshly decoded from the signed APK. This is a member-resolution
check, not a substitute for ART verification or recovery/rendering runtime tests.
"""
from __future__ import annotations
import argparse
from dataclasses import dataclass, field
import json
from pathlib import Path
import re
import sys
import tempfile

JACKSON = 'Lcom/fasterxml/jackson/'
BASE = 'Lapp/morphe/extension/boostforreddit/'
EDITABLE = BASE + 'utils/EditableObjectNode;'
INTERCEPTOR = BASE + 'http/reddit/RedditSubmissionUndeleteInterceptor;'
SCOPES = (INTERCEPTOR, EDITABLE, BASE + 'http/arcticshift/ArcticShift;',
          BASE + 'http/reddit/RedditApiUtils;')
SET = 'set(Ljava/lang/String;Lcom/fasterxml/jackson/databind/JsonNode;)Lcom/fasterxml/jackson/databind/JsonNode;'
OBJECT = JACKSON + 'databind/node/ObjectNode;'
INVOKE = re.compile(r'^\s*(invoke-(?:virtual|super|direct|static|interface)(?:/range)?)\s+\{[^}]*\},\s*(L[^;\s]+;)->([^\s#]+)')
FIELD = re.compile(r'^\s*((?:[is]get|[is]put)(?:-[a-z]+)?)\s+[^#]*?,\s*(L[^;\s]+;)->([^\s#]+)')

@dataclass
class Klass:
    name: str
    parent: str | None
    interfaces: list[str]
    methods: dict[str, set[str]] = field(default_factory=dict)
    fields: dict[str, set[str]] = field(default_factory=dict)
    lines: list[str] = field(default_factory=list)


def scoped(name: str) -> bool:
    return any(name == c or name.startswith(c[:-1] + '$') for c in SCOPES)


def parse(text: str) -> Klass:
    lines = text.splitlines()
    headers = [x.strip().split()[-1] for x in lines if x.strip().startswith('.class ')]
    if len(headers) != 1:
        raise ValueError('SMALI_CLASS_HEADER_INVALID')
    parents = [x.strip().split()[-1] for x in lines if x.strip().startswith('.super ')]
    interfaces = [x.strip().split()[-1] for x in lines if x.strip().startswith('.implements ')]
    k = Klass(headers[0], parents[0] if parents else None, interfaces, lines=lines)
    for line in lines:
        line = line.strip()
        if line.startswith('.method '):
            words = line.split('#', 1)[0].split()
            if words[-1] in k.methods:
                raise ValueError('DUPLICATE_METHOD:' + k.name)
            k.methods[words[-1]] = set(words[1:-1])
        elif line.startswith('.field '):
            words = line.split(' = ', 1)[0].split('#', 1)[0].split()
            if words[-1] in k.fields:
                raise ValueError('DUPLICATE_FIELD:' + k.name)
            k.fields[words[-1]] = set(words[1:-1])
    return k


def load(root: Path) -> dict[str, Klass]:
    classes = {}
    roots = [p for p in root.iterdir() if p.is_dir() and re.fullmatch(r'smali(?:_classes\d+)?', p.name)]
    if not roots:
        raise ValueError('NO_SMALI_ROOTS')
    for sr in sorted(roots):
        paths = list((sr / 'com/fasterxml/jackson').rglob('*.smali'))
        for c in SCOPES:
            rel = Path(c[1:-1])
            paths += list((sr / rel.parent).glob(rel.name + '*.smali'))
        for path in sorted(set(paths)):
            k = parse(path.read_text(encoding='utf-8'))
            if not (k.name.startswith(JACKSON) or scoped(k.name)):
                continue
            if k.name in classes:
                raise ValueError('DUPLICATE_CLASS:' + k.name)
            classes[k.name] = k
    return classes


def resolve(classes: dict[str, Klass], owner: str, member: str, kind: str,
            seen: set[str] | None = None):
    visited = set() if seen is None else seen
    if owner in visited or owner not in classes:
        return None
    visited.add(owner)
    k = classes[owner]
    entries = k.methods if kind == 'method' else k.fields
    if member in entries:
        return owner, entries[member]
    if kind == 'method' and member.startswith('<init>('):
        return None
    next_classes = ([k.parent] if k.parent else []) + k.interfaces
    for parent in next_classes:
        found = resolve(classes, parent, member, kind, visited)
        if found is not None:
            return found
    return None


def audit(classes: dict[str, Klass], require_recovery: bool = True) -> dict:
    if require_recovery:
        missing = [name for name in SCOPES if name not in classes]
        if missing:
            raise ValueError('RECOVERY_CLASSES_MISSING:' + ','.join(missing))
        if OBJECT not in classes or EDITABLE not in classes:
            raise ValueError('JACKSON_RUNTIME_OR_WRAPPER_MISSING')
    findings, references = [], []
    for name, k in sorted(classes.items()):
        if not scoped(name):
            continue
        context = '<outside-method>'
        for n, line in enumerate(k.lines, 1):
            if line.strip().startswith('.method '):
                context = line.strip().split()[-1]
            elif line.strip() == '.end method':
                context = '<outside-method>'
            match = INVOKE.match(line)
            kind = 'method'
            if match is None:
                match = FIELD.match(line)
                kind = 'field'
            if match is None:
                continue
            op, owner, member = match.groups()
            if not (owner.startswith(JACKSON) or owner == EDITABLE):
                continue
            ref = dict(caller=name, method=context, line=n, opcode=op,
                       target=owner + '->' + member, kind=kind)
            result = resolve(classes, owner, member, kind)
            if result is None:
                findings.append({**ref, 'reason': 'MEMBER_NOT_FOUND'})
            else:
                declared, flags = result
                expected_static = op.startswith('invoke-static') if kind == 'method' else op[0] == 's'
                if ('static' in flags) != expected_static:
                    findings.append({**ref, 'reason': 'STATIC_INSTANCE_MISMATCH'})
                if 'private' in flags and name != declared:
                    findings.append({**ref, 'reason': 'PRIVATE_MEMBER_ACCESS'})
                ref['resolved_in'] = declared
            references.append(ref)
    if require_recovery and not references:
        raise ValueError('NO_RECOVERY_MEMBER_REFERENCES')
    wrapper_owned = EDITABLE in classes and SET in classes[EDITABLE].methods
    if require_recovery and not wrapper_owned:
        findings.append({'reason': 'OWNED_EDITABLE_SET_MISSING', 'target': EDITABLE + '->' + SET})
    return dict(result='PASS' if not findings else 'FAIL',
                scope='Direct Jackson/EditableObjectNode members in #196 interceptor and three helpers',
                references_checked=len(references), findings=findings,
                wrapper_owns_set=wrapper_owned, references=references)


def selftest() -> int:
    passed = 0
    def fixture(calls: str, additions: str = ''):
        cls = [parse('.class public ' + OBJECT + '\n.super Ljava/lang/Object;\n' + additions),
               parse('.class public ' + EDITABLE + '\n.super ' + OBJECT + '\n.method public ' + SET + '\n.end method'),
               parse('.class public ' + INTERCEPTOR + '\n.super Ljava/lang/Object;\n.method private static test()V\n' + calls + '\n.end method')]
        return {k.name: k for k in cls}
    def check(label, calls, wanted, additions=''):
        nonlocal passed
        r = audit(fixture(calls, additions), require_recovery=False)
        if r['result'] != wanted:
            raise AssertionError(label + ': ' + json.dumps(r))
        print('PASS ' + label)
        passed += 1
    for name in ('isObject', 'isContainerNode'):
        check('reject_missing_' + name, 'invoke-virtual {v0}, ' + OBJECT + '->' + name + '()Z', 'FAIL')
    check('reject_ObjectNode_set_even_with_subclass_set', 'invoke-virtual {v0, v1, v2}, ' + OBJECT + '->' + SET, 'FAIL')
    check('allow_owned_EditableObjectNode_set', 'invoke-virtual {v0, v1, v2}, ' + EDITABLE + '->' + SET, 'PASS')
    check('allow_range_owned_set', 'invoke-virtual/range {v0 .. v2}, ' + EDITABLE + '->' + SET, 'PASS')
    check('reject_return_descriptor_mismatch', 'invoke-virtual {v0, v1, v2}, ' + EDITABLE + '->' + SET.replace(')Lcom/fasterxml/jackson/databind/JsonNode;', ')V'), 'FAIL')
    check('reject_static_instance_mismatch', 'invoke-static {v0, v1, v2}, ' + EDITABLE + '->' + SET, 'FAIL')
    check('allow_superclass_method', 'invoke-virtual {v0}, ' + EDITABLE + '->size()I', 'PASS', '.method public size()I\n.end method')
    check('reject_missing_field', 'sget-object v0, ' + OBJECT + '->missing:Ljava/lang/Object;', 'FAIL')
    check('allow_static_field', 'sget-object v0, ' + OBJECT + '->present:Ljava/lang/Object;', 'PASS', '.field public static present:Ljava/lang/Object;')
    check('allow_inherited_protected_field', 'iget-object v0, v1, ' + EDITABLE + '->_children:Ljava/util/Map;', 'PASS', '.field protected _children:Ljava/util/Map;')
    check('ignore_diagnostic_string', 'const-string v0, "' + OBJECT + '->' + SET + '"', 'PASS')
    try:
        audit(fixture(''), require_recovery=True)
        raise AssertionError('Missing classes accepted')
    except ValueError:
        print('PASS reject_missing_scope_classes')
        passed += 1
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        classes = fixture('invoke-virtual {v0, v1, v2}, ' + EDITABLE + '->' + SET)
        for i, (name, k) in enumerate(classes.items(), 1):
            path = root / ('smali' if i == 1 else f'smali_classes{i}') / (name[1:-1] + '.smali')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('\n'.join(k.lines))
        if audit(load(root), require_recovery=False)['result'] != 'PASS':
            raise AssertionError('Multidex loading failed')
        print('PASS multidex_cross_class_resolution')
        passed += 1
    print(f'JACKSON_ABI_SELFTESTS_PASS={passed}')
    return passed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--decoded', type=Path)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--selftest', action='store_true')
    args = parser.parse_args()
    if args.selftest:
        selftest()
        return 0
    if args.decoded is None:
        parser.error('--decoded or --selftest is required')
    report = audit(load(args.decoded))
    if args.report:
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print('JACKSON_ABI_REFERENCES_CHECKED=' + str(report['references_checked']))
    print('JACKSON_ABI_FINDINGS=' + str(len(report['findings'])))
    for f in report['findings']:
        print(json.dumps(f))
    print('JACKSON_ABI=' + report['result'])
    return 0 if report['result'] == 'PASS' else 1

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (OSError, ValueError) as exc:
        print('STOP=JACKSON_ABI_INPUT_ERROR:' + str(exc), file=sys.stderr)
        sys.exit(2)
