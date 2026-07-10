# ADR 0043: Reconciliation-based Morphe release flow

- Status: Accepted
- Date: 2026-07-10
- Issues: #43, #44 and #45

## Context

The current Morphe release flow spreads observation, validation, mutation and
verification across several shell scripts. Release 1.4.66 showed that this can
produce contradictory status reporting, undocumented handoffs, non-idempotent
retries and unsafe recovery assumptions.

The release process needs one authoritative model based on actual Git and
GitHub state rather than assumptions about which earlier command completed.

## Decision

Morphe will use a reconciliation-based release architecture with four phases:

```text
inspect -> plan -> apply -> verify
```

The implementation will be a testable Python tool:

```text
scripts/releasectl.py inspect
scripts/releasectl.py plan
scripts/releasectl.py apply
scripts/releasectl.py verify
```

Any shell entry point must only forward arguments. Release logic must not be
duplicated in shell wrappers.

## Release identity

A release is identified by an immutable tuple:

```text
version
release tag
release commit
MPP asset name
signature asset name
MPP SHA256
signing identity
```

Existing state is reconciled using these rules:

```text
missing object                 -> create or upload
existing matching object       -> success / no-op
existing conflicting object    -> hard conflict
unobservable object            -> observation failure
```

## Sources of truth

Authority is ordered as follows:

1. actual remote Git refs;
2. GitHub API release and asset data;
3. downloaded remote release assets;
4. cryptographic digests and signatures;
5. local Git refs and canonical local artifacts;
6. previous logs as diagnostic evidence only.

A wrapper result or transaction journal cannot override contradictory actual
remote state.

## Canonical artifacts

Only these local artifact paths are valid:

```text
patches/build/libs/patches-VERSION.mpp
patches/build/libs/patches-VERSION.mpp.asc
```

The tooling must not discover release artifacts through broad filesystem
searches or select files from rejected releases, candidates or temporary
folders.

## Inspect

`inspect` is strictly read-only. It produces normalized observations for local
and remote refs, tags, metadata, canonical artifacts, GitHub release state,
asset digests, signatures and MPP structure.

It must not build, checkout, merge, update refs, tag, push, upload, publish or
repair anything.

## Plan

`plan` is read-only. It compares the desired immutable release identity with
current observations and produces an explicit operation plan.

A planned operation must classify as one of:

```text
APPLY_REQUIRED
NOOP_ALREADY_SATISFIED
CONFLICT_ABORT
OBSERVATION_FAILED
```

A plan is not authoritative state and becomes stale when relevant observations
change.

## Apply

`apply` executes only operations contained in an approved plan. Before every
mutation it must re-observe the relevant state and re-check preconditions.

No operation may assume that the previous operation succeeded.

## Verify

`verify` is read-only and repeatable. A release is fully verified only when:

- remote `main` and `dev` equal the release commit;
- the remote tag peels to the release commit;
- the GitHub release is published and not a prerelease;
- the expected MPP and signature assets exist exactly once;
- the downloaded MPP digest matches the release identity;
- the detached signature is valid;
- the MPP contains the required DEX and Boost extension entries;
- README and bundle metadata match;
- the existing full remote verifier passes.

A completed release must keep returning success on later verification runs.

## State summary

Observed facts may be summarized as:

```text
NOT_FINALIZED
LOCAL_FINALIZED
READY_TO_PUBLISH
PARTIALLY_PUBLISHED
PUBLISHED_NOT_VERIFIED
PUBLISHED_AND_VERIFIED
INCONSISTENT_ABORT
```

These states are derived from observations and are never stored as a separate
source of truth.

Release state and local mutation safety are separate. A valid example is:

```text
STATE=PUBLISHED_AND_VERIFIED
SAFE_TO_MUTATE=false
```

when the remote release is correct but the local worktree is dirty.

## Git ref strategy

Remote `main`, `dev` and the release tag must be updated through one atomic Git
push. The tooling must not fall back to separate pushes when atomic push is not
supported.

Local branch refs should use transactional ref updates with explicit expected
old object IDs where practical.

## GitHub release strategy

GitHub Releases use a draft-first lifecycle:

1. push release refs atomically;
2. re-observe refs;
3. create a draft release;
4. upload the MPP;
5. upload the detached signature;
6. download and verify draft assets;
7. publish the draft;
8. perform final remote verification.

An existing asset with the same name and digest is a no-op. An existing asset
with the same name and a different digest is a hard conflict. Automatic asset
clobbering is prohibited.

## Immutability

Published tags and assets are immutable in normal operation. The normal release
and recovery flow must not use force-retagging, force-pushing release tags or
asset replacement.

A conflicting published tag or asset requires manual diagnosis.

## Validation order

Everything that can be validated locally must pass before the first external
mutation, including arguments, release notes, version/tag contract, canonical
artifacts, digest, signature, metadata, branch/tag relationships, candidate and
runtime gates, remote conflict checks and the generated plan.

## Concurrency

Only one release operation may mutate a repository at a time. Local operations
require an exclusive repository-and-tag lock. GitHub Actions must retain a
non-cancelling release concurrency group.

## Issue boundaries

### Issue #43

Issue #43 implements read-only architecture:

- release identity;
- observers;
- normalized JSON;
- state classification;
- plan generation;
- inspect, plan and verify;
- unit and read-only integration tests.

### Issue #44

Issue #44 implements mutations:

- apply engine;
- locking;
- atomic ref push;
- draft-first GitHub release;
- resumable asset upload;
- stale-plan protection;
- failure recovery;
- migration and deprecation of old release scripts.

### Issue #45

Issue #45 implements the Android bytecode access and register-safety gate. It
remains a separate release-blocking validation layer and does not replace
runtime testing.

## Testing requirements

Classifier and planner logic must be pure functions without subprocess,
filesystem or network access.

Integration tests must use temporary Git repositories, local bare remotes, fake
GitHub API or fake `gh`, test signing keys, fixture MPP files and failure
injection after every mutating phase.

No test may mutate the production repository or production GitHub releases.
Repeated `inspect`, `plan` and `verify` runs must cause no mutation.

## Consequences

Benefits:

- actual state becomes authoritative;
- retries become safe;
- matching existing state becomes success rather than failure;
- partial publication can be resumed;
- conflicts become explicit;
- shell orchestration is reduced;
- release behavior becomes testable without production access.

Costs:

- the initial implementation is larger than a narrow shell patch;
- the plan schema must be versioned;
- GitHub draft and asset behavior must be simulated in tests;
- existing release scripts require controlled migration and deprecation.

These costs are accepted because release correctness and recoverability take
precedence over implementation simplicity.
