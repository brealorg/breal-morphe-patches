# Breal Morphe Patches

1.4.42 disables the automatic Imgur/Reddit undelete patches by default for Boost. This avoids slow comment loading in the normal/default patch flow while keeping undelete available as an opt-in patch.

Unofficial Morphe patch bundle for Boost for Reddit, Imgur, and related Android app fixes.

This repository publishes a Morphe-compatible `.mpp` bundle and a small JSON feed used by Morphe Manager to discover the current release.

## Project page

Canonical GitHub repository:

```text
https://github.com/brealorg/breal-morphe-patches
```

Legacy repository URLs under `breal-boost-hotfixes` currently redirect here for backwards compatibility. Do not recreate a new repository with the old name, because that may break existing redirects.

## Morphe patch source

Use this raw JSON source in Morphe Manager:

```text
https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json
```

Legacy source URL, kept working through GitHub redirect:

```text
https://raw.githubusercontent.com/brealorg/breal-boost-hotfixes/main/patches-bundle.json
```

Do not use the normal GitHub project page as the Morphe source. The GitHub project page is for humans; the raw `patches-bundle.json` URL is for Morphe.

## Current release

| Field | Value |
|---|---|
| Version | `1.4.42` |
| Release tag | `morphe-patches-42` |
| Asset | `patches-1.4.42.mpp` |
| SHA256 | `b3032facb27e2e763e3c11d9257e0dfb15cc9de7bea6ad19eaad80e46e2955d8` |
| Manager JSON | `https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/morphe-patches-42/patches-1.4.42.mpp` |

## What this bundle does

The current bundle is focused on practical hotfixes for tested app versions, especially Boost for Reddit behavior on newer Android versions.

### Boost for Reddit

Tested against Boost for Reddit `1.12.12` / versionCode `210011212`.

Included in `1.4.42`:

1.4.42 is a hotfix for the normal Morphe Manager/default-flow path: the Comments UI fix is now enabled by default, so the duplicate native `Comments` title row is removed without requiring users to manually select that patch.

- **Comments UI**
  - Removes the duplicate native `Comments` title row.
  - Preserves Boost's toolbar title.
  - Preserves the dynamic sort subtitle, such as `Best`, `Hot`, and other sort modes.
  - Preserves dark theme behavior in comments.

- **Runtime media tap-action settings**
  - Adds configurable handling for direct Reddit GIFs.
  - Adds configurable handling for Giphy previews.
  - Adds configurable handling for static previews.
  - Keeps media opening behavior closer to Boost's internal viewer flow.

- **Deleted Reddit gallery metadata restore**
  - Improves recovery of deleted Reddit gallery metadata where possible.

- **Safer subreddit listing fallback**
  - Improves fallback handling for subreddit listing edge cases.

Earlier Boost fixes carried forward:

- Android 15+ / target SDK 35 compatibility work.
- Navigation bar overlap fixes for media viewer and drawer.
- Completed-download notification visibility fixes.
- Separate default-importance notification channel for completed downloads.
- v.redd.it audio sharing/download fixes.
- Direct `i.redd.it` GIF handling.
- Inline Giphy and direct GIF preview fixes.
- Slow Giphy loading fix.
- Archive / undelete related Boost fixes where applicable.

### Imgur selected media sharing

Tested against Imgur `7.33.0.0`.

Included Imgur patches:

- **Selected media file sharing**
  - Long-pressing an image or video in post detail shares the actual selected media file.
  - Opens Android's share sheet with the real image/video file, not just the parent Imgur link.
  - Replaces Imgur's Download share action with direct private cached file sharing.
  - Caches selected media privately before opening Android's share sheet.
  - Does not permanently save the file to `/sdcard/Download/Imgur`.

- **Selected media URL sharing**
  - Default mode shares the raw media/download URL.
  - Optional mode shares the selected item permalink.
  - Keeps URL/link sharing separate from file sharing.

## Tested app versions

| App | Package | Version |
|---|---|---|
| Boost for Reddit | `com.rubenmayayo.reddit` | `1.12.12` / versionCode `210011212` |
| Imgur | `com.imgur.mobile` | `7.33.0.0` / versionCode `73300` |

Compatibility with other app versions is not guaranteed.

## Verification

Release `1.4.42` was published and verified with:

- Release tag `morphe-patches-42` pointing to the 1.4.42 release commit.
- GitHub release asset SHA256 matching the local built MPP.
- Raw `patches-bundle.json` returning version `1.4.42`.
- Raw `patches-bundle.json` pointing to the `morphe-patches-42` asset.
- Downloaded release asset SHA256 matching:

```text
5844fea46e784e5d7c3430e58768ae794bed91a93774592ea1e857436dab51db
```

## Development notes

This repository contains experimental and release-candidate work in local/work branches. Only the published release JSON and GitHub release asset should be treated as the current user-facing bundle.

### Deferred work

16K / WL04R builder and runtime work is intentionally **not included** in `1.4.42`.

That work is preserved separately for a later scope.

## Attribution and license

This repository is derived from Patcheddit/Morphe patch work.

Author metadata in the patch bundle currently credits:

```text
wchill + brealorg
```

Additional license conditions under GPL section 7 apply. See `LICENSE` for attribution and project name restrictions.

## Disclaimer

This is an unofficial community patch bundle. It is provided as-is and is intended for tested app versions only. Use at your own risk.
