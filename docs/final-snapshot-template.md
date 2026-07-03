# Final snapshot template

Use this template before closing a user-visible bug or declaring a release
workstream complete.

## Summary

- Issue / workstream:
- Release version:
- Release tag:
- Final commit:
- Final result marker:
- Caveats:

## Validation matrix

| Layer | Status | Evidence | Result marker / log | Caveat |
|---|---|---|---|---|
| Source diff | TODO | | | |
| Build / MPP | TODO | | | |
| Static gate | TODO | | | |
| Local runtime | TODO | | | |
| Release publish | TODO | | | |
| Remote verification | TODO | | | |
| Manager normal verification | TODO | | | |
| Issue housekeeping | TODO | | | |

## Required close condition

For user-visible fixes, close the issue only when:

- release is published;
- remote release verifier is green;
- Morphe Manager / normal Boost path is visually or functionally verified;
- final issue comment includes release version, evidence, and caveats.

## Closing comment skeleton

    Fixed in `<version>`.

    Validation:
    - Source/build/static gate: `<evidence>`
    - Remote release: `<REMOTE RELEASE OK / asset SHA / tag>`
    - Morphe Manager normal path: `<visual/runtime confirmation>`

    Caveats:
    - `<none or explicit caveat>`
