# Authoritative release controller

`scripts/releasectl.py` is the canonical state checker and transaction
controller for Morphe releases. Git and GitHub state are re-observed before
every release mutation; no outer-wrapper status is authoritative.

## Immutable release identity

Every read-only invocation must provide the exact release identity:

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

The annotated tag is the exact immutable commit anchor. `main` and `dev` may
advance after publication; descendant branch heads still contain the release
target and must not invalidate an otherwise verified historical release. A
divergent branch remains a concrete conflict.

## Read-only commands

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

The read-only commands may inspect local files, run read-only Git commands,
list GitHub release metadata, and download release assets into an ephemeral
temporary directory. They must not build, alter refs, push, upload, publish, or
change the repository.

## Protected-main release preparation

Prepare release metadata and the deterministic MPP on a focused
`work/release-*` branch:

```bash
python3 scripts/prepare-release.py \
  --version VERSION \
  --tag TAG \
  --changelog "CURRENT RELEASE DESCRIPTION"
```

Review and commit only the canonical metadata files, push the work branch, and
merge it through a pull request after all required checks pass. No release tag,
`dev` update, GitHub Release, or direct `main` write belongs to this phase. The
MPP SHA committed in README is the immutable digest contract for publication.

## Idempotent publication commands

Publish or resume:

```bash
python3 scripts/releasectl.py publish \
  --version VERSION \
  --tag TAG \
  --release-notes-file PATH \
  --protected-main-commit FULL_MERGED_MAIN_SHA

python3 scripts/releasectl.py resume \
  --version VERSION \
  --tag TAG \
  --release-notes-file PATH \
  --protected-main-commit FULL_MERGED_MAIN_SHA
```

`publish` and `resume` use the same reconciliation engine. They validate the
dispatch commit, local `main`, remote `main`, committed metadata, rebuilt MPP,
signature, and release notes before refs are pushed. They then perform only
missing phases:

1. deterministic artifact rebuild and signing from the merged commit;
2. local annotated-tag and `dev` alignment;
3. atomic publication of `dev` and the annotated tag, with remote `main` as an
   exact immutable precondition rather than a push target;
4. draft GitHub release creation;
5. upload of only the missing MPP or signature asset;
6. remote digest, structure, and detached-signature verification;
7. draft publication;
8. final authoritative remote verification.

Existing matching state is a no-op. An existing tag at another commit,
duplicate or mismatching assets, an unexpected digest, or divergent refs cause
`INCONSISTENT_ABORT`. The controller never force-moves a tag and never uploads
with `--clobber`. It never writes `main`; release metadata must already be
present at the exact remote-main commit.

The remaining compatibility publication entry points are thin wrappers:

```text
scripts/release-publish-existing.sh
scripts/publish-release.py
```

They forward exact documented arguments to `releasectl.py`; wrappers contain no
independent classification or publication logic.

## Transaction log

Every mutating invocation appends JSON Lines records to:

```text
local-artifacts/release-transactions/TAG.jsonl
```

Each entry records the command, phase, status, timestamp, immutable identity
when known, and non-secret diagnostics. The log is an audit record, not the
source of truth. Actual Git, GitHub, metadata, and artifact state is re-read
before every mutation. A stale or incomplete log never authorizes a mutation.

Machine-readable completion output distinguishes the underlying result from
the postcheck:

```text
INNER_RESULT=MORPHE_RELEASE_PUBLISH_EXISTING_OK
POSTCHECK_RESULT=OK
FINAL_STATE=PUBLISHED_AND_VERIFIED
```

A fully completed retry is successful:

```text
RESULT=MORPHE_RELEASE_ALREADY_PUBLISHED_VERIFIED_OK
STATE=PUBLISHED_AND_VERIFIED
```

## Mandatory mutation gates

The controller re-inspects actual state:

1. before protected-main artifact reconstruction;
2. before local branch or tag alignment;
3. before the atomic release-ref push;
4. before draft release creation;
5. before each asset upload;
6. before draft publication.

Re-run the command after every mutation. Run `verify` after publication and
before the release is considered complete.

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

- `0`: command completed successfully, including an already-satisfied no-op;
- `2`: invalid command-line identity or arguments;
- `3`: concrete conflict, incomplete postcheck, or failed transaction;
- `4`: one or more read-only observations were unavailable;
- `5`: `verify` completed, but the release is not fully verified.

## Metadata repair separation

The former direct metadata-repair publisher is retired. A metadata correction
must use a focused work branch and the same pull-request checks as every other
`main` change. The immutable release tag and published assets remain unchanged;
automatic force-retag, force-push, and direct `main` repair are forbidden.

The unused semantic-release configuration and Node-only release dependencies
are removed. They are not a fallback publication path; `releasectl.py` remains
the single mutating release core.
