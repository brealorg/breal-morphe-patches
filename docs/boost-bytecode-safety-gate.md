# Boost bytecode safety gate

`tools/boost-bytecode-safety-gate.sh` is the pre-runtime safety gate for patched
Boost DEX methods. It supplements Morphe compilation, APK packaging, SDK
verification and device runtime validation. It does not replace any of them.

## Required invocation

```bash
tools/boost-bytecode-safety-gate.sh \
  --base-apk /path/to/boost-1.12.12.apk \
  --candidate-apk /path/to/boost-candidate.apk \
  --patch-result /path/to/patch-result.json \
  --report /path/to/bytecode-safety-report.json
```

The shell entry point decodes both APKs with apktool and invokes the deterministic
Python analyzer in `tools/boost_bytecode_safety.py`. Missing inputs, invalid JSON,
decode failures and unavailable Smali data fail closed with exit code `2`.
Confirmed bytecode defects return exit code `1`; a clean analysis returns `0`.

Decoded-Smali mode exists for regression fixtures and diagnosis:

```bash
tools/boost-bytecode-safety-gate.sh \
  --base-smali tests/fixtures/bytecode-safety/valid_public_getchildat/base \
  --candidate-smali tests/fixtures/bytecode-safety/valid_public_getchildat/candidate \
  --patch-result tests/fixtures/bytecode-safety/valid_public_getchildat/patch-result.json
```

## Analysis contract

The base and candidate class indexes are built from every `smali*` directory.
For classes present in both APKs under `Lcom/rubenmayayo/reddit/`, the analyzer:

1. identifies methods whose instruction stream or register frame changed;
2. isolates inserted and replaced candidate instructions using a deterministic
   sequence delta;
3. resolves newly introduced field and method references against the candidate
   class index;
4. validates public, private, protected and package-private access;
5. validates static versus instance opcodes and field opcode/descriptor shape;
6. computes a conservative register state from the method entry across branches;
7. checks object/primitive use, invoke register counts and argument categories,
   uninitialized references, move-result pairing, register bounds and incompatible
   control-flow joins.

The generated JSON report has schema version `1` and records all findings plus
counts for changed methods, injected instructions, member checks and type-flow
checks. Text output is stable and machine-readable.

A failure includes the exact caller, method, instruction and reason, for example:

```text
BYTECODE_GATE=FAIL
CLASS=com.rubenmayayo.reddit.ui.adapters.CommentViewHolder
METHOD=bind(Lcom/rubenmayayo/reddit/models/reddit/CommentModel;)V
INSTRUCTION_INDEX=1
INSTRUCTION=invoke-static {p1}, Lapp/morphe/extension/TestHooks;->consume(Ljava/lang/Object;)V
REASON=OBJECT_USE_FROM_PRIMITIVE_REGISTER
```

A successful run includes:

```text
BYTECODE_GATE=PASS
MODIFIED_METHODS=...
MEMBER_ACCESS_CHECKS=...
REGISTER_FLOW_CHECKS=...
```

## Issue #36 regression coverage

The fixture suite under `tests/fixtures/bytecode-safety/` covers:

- reused `p1` after it became a primitive value — rejected;
- direct cross-package access to package-private `TableTextView.b` — rejected;
- public `ViewGroup.getChildAt(0)` access — accepted;
- stable object reload from a holder field before object use — accepted;
- incompatible primitive/reference control-flow join — rejected.

Run it with:

```bash
python3 -m unittest -q tests/tools/test_boost_bytecode_safety.py
```

## Tooling integration

The gate is mandatory in these paths:

- `tools/build-boost-candidate.sh`, after the existing candidate static gate;
- `tools/build-boost-devclone-candidate.sh`, against the patched source APK before
  DEV-clone decode or rewrite;
- `tools/boost-dev-from-mpp.sh`, which passes the canonical base APK and the
  normal candidate's `patch-result.json` to the DEV-clone builder;
- `scripts/releasectl.py finalize`, which requires both the PASS log and PASS JSON
  report before creating the release commit or annotated tag;
- `.github/workflows/boost-bytecode-safety.yml`, which runs the regression fixture
  suite whenever Boost patch or bytecode-gate code changes.

A failed gate prevents the normal candidate builder from returning `RESULT: PASS`.
Consequently the supported DEV preparation and release-finalize paths cannot
continue to installation, release commit or tag creation.

## Known limits and mandatory runtime coverage

The analyzer intentionally checks newly introduced behavior in modified Boost
methods rather than claiming to be a complete ART verifier. In particular:

- reference assignability is category-based for external framework/library types;
- protected-member receiver restrictions are approximated using package and
  inheritance relationships;
- exception-handler edges and complex switch payload semantics are not a complete
  replacement for ART verification;
- native code, reflection, dynamic class loading and runtime API behavior are out
  of scope;
- the patch-result schema is validated and recognizable method descriptors are
  recorded, while the actual base/candidate bytecode delta remains authoritative.

Morphe SDK verification should remain enabled where it is usable. A real Boost
DEV runtime test remains mandatory before public release, including logcat checks
for `VerifyError`, `IllegalAccessError`, fatal exceptions and process death.
