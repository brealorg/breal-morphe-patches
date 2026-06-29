# Breal Morphe Patches

Unofficial Morphe patch bundle for supported Android apps.

This repository currently includes Boost for Reddit hotfixes and Imgur selected media sharing patches. It is derived from Patcheddit/Morphe patch work and is not an official project for any of the patched apps.

## Project page

Canonical GitHub repository:

`https://github.com/brealorg/breal-morphe-patches`

Legacy repository URLs under `breal-boost-hotfixes` currently redirect here for backwards compatibility. Do not recreate a new repository with the old name, because that may break existing redirects.

## Morphe patch source

Use this source in Morphe:

`https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json`

Legacy source URL, kept working through GitHub redirect:

`https://raw.githubusercontent.com/brealorg/breal-boost-hotfixes/main/patches-bundle.json`

Morphe uses this JSON source to find and download the current `.mpp` patch bundle.

Do not use the normal GitHub project page as the Morphe source. The GitHub project page is for humans; the raw `patches-bundle.json` URL is for Morphe.

## Current release

Current public bundle:

`1.4.32`

Latest release asset:

`patches-1.4.32.mpp`

Release tag:

`morphe-patches-32`

SHA256:

`fa9c718df854022aaf834cdafb310ec68fcc2e5aa2f60941664d6b931e714d99`

## Included patches

### Boost for Reddit hotfixes

Tested against Boost for Reddit 1.12.12.

Included Boost fixes:

- v.redd.it video sharing/download audio fix
- faster Giphy loading via direct `media.giphy.com` MP4 fallback
- inline Giphy previews in comments
- inline previews for direct `.gif` links such as `i.redd.it/*.gif`
- improved inline preview layout/collapse behavior
- completed download notifications on a separate default-importance Android notification channel while progress notifications remain low-priority

### Imgur selected media sharing

Tested against Imgur 7.33.0.0.

Included Imgur patches:

- selected media file sharing:
  - long-pressing an image or video in post detail shares the actual selected media file
  - opens Android's share sheet with the real image/video file, not just the parent Imgur link
  - replaces Imgur's Download share action with direct private cached file sharing
  - caches selected media privately before opening Android's share sheet
  - does not permanently save the file to `/sdcard/Download/Imgur`
- selected media URL sharing:
  - default mode shares the raw media/download URL
  - optional mode shares the selected item permalink
  - keeps URL/link sharing separate from file sharing

## Compatibility and scope

This bundle is intended as a practical hotfix/source bundle for Morphe users. It is not guaranteed to be stable across app versions other than the versions tested.

Current known tested app versions:

- Boost for Reddit 1.12.12
- Imgur 7.33.0.0

## Attribution

This repository is derived from Patcheddit/Morphe patch work.

Author metadata in the patch bundle currently credits:

`wchill + brealorg`

Additional license conditions under GPL section 7 apply. See `LICENSE` for attribution and project name restrictions.
