# Release validation policy

This project treats Morphe patch releases as a release-critical Android patch
pipeline. A change is not complete merely because source code was patched or a
bundle was built.

## Release-state ladder

Use these states consistently in issues, comments, release notes, and final
snapshots:

| State | Meaning | Allowed issue action |
|---|---|---|
| `source_fixed` | Source code or patch logic has been changed and reviewed locally. | Keep issue open. |
| `local_candidate_verified` | A local APK/MPP candidate passed relevant static and runtime checks. | Keep issue open unless the issue was explicitly source-only. |
| `bundle_released` | A tagged release and release asset exist. | Keep user-visible issues open. |
| `remote_verified` | Remote tag, GitHub release, asset SHA, signature, MPP contents, and Manager metadata were verified. | Keep user-visible issues open until runtime verification is complete. |
| `manager_normal_verified` | The release was applied through Morphe Manager to the normal Boost package and the affected user-visible flow was visually/functionally verified. | User-visible fix may be closed. |
| `issue_closed` | The issue has a final comment with release version, verification evidence, and caveats. | Closed. |

## Issue closing policy

For user-visible Boost or Imgur bugs, do not close the issue at `source_fixed`,
`local_candidate_verified`, `bundle_released`, or `remote_verified`.

Pull requests for user-visible work must reference the issue with `Addresses #N`
or equivalent non-closing text. Do not use GitHub auto-close keywords in the PR.
Issue closure is a separate, manual closeout operation after the requirements
below have been verified.

Close only after all of the following are true:

1. The fix is included in a published release.
2. The remote release is verified.
3. The normal package was patched through Morphe Manager or the documented normal release path.
4. The affected visual/runtime flow was verified by screenshot, log, user confirmation, or another explicit evidence item.
5. The closing comment states the release version, verification path, and remaining caveats.

If runtime verification is unavailable, leave the issue open or mark it as
blocked/pending verification. Do not describe untested behavior as verified.

## Final snapshot matrix

Every final snapshot for a user-visible fix should include this matrix or an
equivalent table:

| Layer | Status | Evidence | Result marker / log | Caveat |
|---|---|---|---|---|
| Source diff | PASS/SKIP/FAIL | Commit, diff, or marker | `<commit>` / `<marker>` | |
| Build / MPP | PASS/SKIP/FAIL | `:patches:buildAndroid`, MPP path, SHA | `classes.dex`, `extensions/boostforreddit.mpe` | |
| Static gate | PASS/SKIP/FAIL | release-gate or APK static gate | `RELEASE GATE OK` / static-gate log | |
| Local runtime | PASS/SKIP/FAIL | dev/normal APK, logcat, screenshots | runtime result marker | |
| Release publish | PASS/SKIP/FAIL | tag, GitHub release, assets | release tag / commit | |
| Remote verification | PASS/SKIP/FAIL | `verify-remote-release.sh` | `REMOTE RELEASE OK` | raw cache warning if relevant |
| Manager normal verification | PASS/SKIP/FAIL | Morphe Manager normal path, visual/user confirmation | manual visual result / screenshot / log | |
| Issue housekeeping | PASS/SKIP/FAIL | final comment and close action | issue URL / command log | |

Use `SKIP` only with a reason. Do not omit untested layers.

## Manager-facing changelog policy

`patches-bundle.json.description` is Manager-facing text. Keep it short and
current-release-only.

Allowed shape:

    Bug Fixes

    - Fix inline media spacing in comment text.

or:

    Runtime

    - Keep direct i.redd.it GIFs in Boost image viewer.

Rules:

- Use one short category heading.
- Use one or a few bullets.
- Describe only the current release.
- Do not include generated compare headings with version links or compare URLs.
- Do not include historical "also includes" text.
- Do not use Manager description as a changelog archive.

Longer technical notes belong in GitHub release notes, issue comments, or final
snapshot artifacts.
