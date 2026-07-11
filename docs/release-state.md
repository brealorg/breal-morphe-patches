# Authoritative release state

`scripts/releasectl.py` is the canonical read-only state checker for Morphe
releases. Git and GitHub state are observed and reconciled; no outer wrapper
status is treated as authoritative.

## Immutable release identity

Every invocation must provide the exact release identity. The command does not
infer values from similarly named files or search for artifacts outside the
canonical path.

Required identity values:

- version;
- release tag;
- full release commit SHA-1;
- expected MPP SHA-256;
- full signing-key fingerprint.

The canonical artifacts are derived from the version and only accepted at:

```text
patches/build/libs/patches-VERSION.mpp
patches/build/libs/patches-VERSION.mpp.asc
```

## Commands

Inspect and classify:

```bash
python3 scripts/releasectl.py inspect \
  --version VERSION \
  --tag TAG \
  --release-commit FULL_COMMIT_SHA \
  --mpp-sha256 FULL_MPP_SHA256 \
  --signing-identity FULL_GPG_FINGERPRINT
```

Generate the deterministic next-operation plan:

```bash
python3 scripts/releasectl.py plan \
  --version VERSION \
  --tag TAG \
  --release-commit FULL_COMMIT_SHA \
  --mpp-sha256 FULL_MPP_SHA256 \
  --signing-identity FULL_GPG_FINGERPRINT
```

Require a fully published and verified release:

```bash
python3 scripts/releasectl.py verify \
  --version VERSION \
  --tag TAG \
  --release-commit FULL_COMMIT_SHA \
  --mpp-sha256 FULL_MPP_SHA256 \
  --signing-identity FULL_GPG_FINGERPRINT
```

Add `--json` to any command for normalized machine-readable output. Text and
JSON output are generated from the same inspection object and must report the
same `STATE` and `NEXT_ACTION`.

## Mandatory release gates

Run `inspect` and `plan` immediately before every mutating release phase:

1. before local finalization;
2. before local branch or tag alignment;
3. before the atomic release-ref push;
4. before draft release creation;
5. before each asset upload;
6. before draft publication.

Re-run the command after every mutation. Run `verify` after publication and
before the release is considered complete. Issue #44 must consume the generated
plan and re-observe state between operations; it must not duplicate classification
logic in shell wrappers.

## State and exit contract

The authoritative states are:

```text
NOT_FINALIZED
LOCAL_FINALIZED
READY_TO_PUBLISH
PARTIALLY_PUBLISHED
PUBLISHED_NOT_VERIFIED
PUBLISHED_AND_VERIFIED
INCONSISTENT_ABORT
```

Exit codes:

- `0`: observation succeeded; for `verify`, the state is
  `PUBLISHED_AND_VERIFIED`;
- `2`: invalid command-line identity or arguments;
- `3`: concrete conflict (`INCONSISTENT_ABORT`);
- `4`: one or more required observations were unavailable;
- `5`: `verify` completed, but the release is not yet fully verified.

A completed release is a successful no-op with `NEXT_ACTION=NONE`. Matching
existing refs, release metadata, assets and digests are not treated as errors.

## Read-only guarantees

The checker may read local files, run read-only Git commands, list GitHub
release metadata, and download release assets into an ephemeral temporary
directory for SHA-256, ZIP-structure and detached-signature verification. It
must not build, checkout, merge, reset, create or move refs, push, upload,
publish, replace assets, or alter the production repository.
