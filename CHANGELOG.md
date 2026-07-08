# Changelog

<!-- MORPHE_MANAGER_CHANGELOG_START -->
## [1.4.63](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-62...morphe-patches-63) (2026-07-08)

* **Boost for Reddit:** Boost: add runtime settings for Reddit/Imgur undelete and restore deleted Reddit media loaded through Glide/Wayback.

## [1.4.62](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-61...morphe-patches-62) (2026-07-08)

* **Boost for Reddit:** Bug Fixes: Boost comments now fail open when Arctic Shift returns empty/invalid data, preventing intermittent comment-load errors.

## [1.4.61](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-60...morphe-patches-61) (2026-07-08)

* **Boost for Reddit:** Boost: fix Inline Media Previews so mixed link/media comments keep the original tappable URL while still rendering the inline preview. Runtime validated on Issue #29 repro: inline preview visible, original x.com link opens, v3 skip marker absent, no fatal or ActivityNotFound exceptions.

## [1.4.60](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-59...morphe-patches-60) (2026-07-08)

* **Boost for Reddit:** Add optional Boost high refresh-rate preference that requests Android preferredRefreshRate=120.0 on adaptive-refresh displays. Runtime validated in Boost Dev on Android 17 with MainActivity, MediaVideoActivity, and CommentsActivity logging preferredRefreshRate=120.0 and no real Boost Dev hard crash observed; exact panel Hz was not independently measured.

## [1.4.59](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-58...morphe-patches-59) (2026-07-07)

* **Boost for Reddit:** Boost for Reddit: Make Disable ads tolerate already-stripped AppLovin/Google ad manifest entries so j-hc/revanced-magisk-module can patch stripped APK inputs.

## [1.4.58](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-57...morphe-patches-58) (2026-07-07)

* **Boost for Reddit:** Fix Boost feed actions crashing when an invalid list index is produced. Guard the Hide/Mark-as-read feed action path before Boost reads or removes the item.

## [1.4.57](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-56...morphe-patches-57) (2026-07-07)

* **Boost for Reddit:** Fix image widget click target so the widget opens the visible post instead of a stale cached post. Runtime validated from real Kotlin/MPP-derived Boost Dev build with `dat=morphe-widget-image://open/...` and runtime classifier PASS.

## [1.4.56](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-55...morphe-patches-56) (2026-07-07)

* **Boost for Reddit:** Fix Boost Random subreddit by resolving a live non-NSFW subreddit through Reddit search with subscriber-count filtering.

## [1.4.55](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-54...morphe-patches-55) (2026-07-07)

* **Boost for Reddit:** Fix Random subreddit crash caused by Jackson JsonNode runtime API mismatch.

## [1.4.54](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-53...morphe-patches-54) (2026-07-07)

* **Boost for Reddit:** Ignore Reddit profile/avatar image URLs in comment preview extraction so profile pictures no longer render as large inline media.

## [1.4.53](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-52...morphe-patches-53) (2026-07-07)

* **Boost for Reddit:** Render direct `i.imgur.com` and `i.redd.it` static image URLs inline in comment bodies, including uploaded Imgur image links after submit/refresh.

## [1.4.52](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-51...morphe-patches-52) (2026-07-06)

* **Boost for Reddit:** Preserve Morphe Manager update detection with Manager-compatible Boost changelog scope for the 1.4.52 bundle.

## [1.4.51](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-50...morphe-patches-51) (2026-07-06)

* **Boost for Reddit:** Fix native image upload so single-image posts submit as Reddit native image posts instead of uploaded-media link posts.

## [1.4.50](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-49...morphe-patches-50) (2026-07-06)

* **Boost for Reddit:** Refresh Breal patch source metadata for Morphe Manager update detection.

## [1.4.49](https://github.com/brealorg/breal-morphe-patches/compare/morphe-patches-48...morphe-patches-49) (2026-07-06)

* **Boost for Reddit:** Prepare Breal patch source update-available validation metadata.

<!-- MORPHE_MANAGER_CHANGELOG_END -->

## [1.4.46](https://github.com/brealorg/breal-morphe-patches/compare/v1.4.45...v1.4.46) (2026-07-03)

## [1.4.1](https://github.com/brealorg/breal-morphe-patches/compare/v1.4.0...v1.4.1) (2026-07-02)


### Bug Fixes

* **boost:** guard hide invalid index crash ([1f28da3](https://github.com/brealorg/breal-morphe-patches/commit/1f28da393ef53185af4a2831f4f15c410ba4a803))
* include app names in patches list feed ([85a8164](https://github.com/brealorg/breal-morphe-patches/commit/85a816451b987e67e49c8f80025781a2e927792d))

# Changelog

## 1.4.43

- Fix Boost comment code block rendering so Reddit fenced code preserves line breaks.
- Split multiline Reddit <code> HTML into separate Boost native code blocks so prose such as 'Or:' stays outside the code block.

## 1.4.30 - morphe-patches-30

- Add Boost Morphe settings under Advanced settings.
- Add inline media preview toggle.
- Add source text visibility toggle for inline previews.
- Add preview alignment setting: left, center, or right.
- Remove the duplicate gray `Source:` URL label under inline previews.
- Includes previous Boost fixes from 1.4.27.

## 1.4.27 - morphe-patches-27

- Fix direct `i.redd.it/*.gif` links so they open through Boost's image viewer instead of being treated as broken video/GIF media.
- Restore safer URL handling for Boost media/link flows.
- Includes previous target SDK and inline media fixes.

## 1.4.26 - morphe-patches-26

- Add Boost target SDK / newer Android compatibility fixes.
- Keep Boost stable on newer Android notification/runtime behavior.
- Includes previous Boost media preview and download notification fixes.

## 1.4.24 - morphe-patches-24

- Make tapped inline Giphy/GIF previews open in Boost's viewer where possible.
- Make static Reddit previews open in Boost's image viewer.
- Includes previous Boost fixes for Giphy loading, direct GIF previews, v.redd.it audio, and completed download notifications.

## 1.4.23 - morphe-patches-23

- Add Imgur 7.33.0.0 post-detail long-press file sharing: long-pressing an image or video now opens Android's share sheet with the actual selected media file instead of only sharing the parent Imgur link.
- Replace Imgur's Download share action with direct private cached file sharing.
- Keep Imgur URL/link sharing separate from file sharing; selected-media URL sharing remains available.
- Includes previous Boost fixes from 1.4.22.

## 1.4.22 - morphe-patches-22

- Fix Boost opening Reddit comment video player links in WebView by normalizing reddit.com/link/.../video/.../player URLs to v.redd.it so they open in Boost's native video player.
- Includes previous Boost fixes for v.redd.it audio in downloads/shares, faster Giphy loading, inline Giphy previews, direct GIF previews, preview layout handling, and completed download notifications.
- Imgur compatibility is included for Imgur 7.33.0.0.

---

## Upstream Patcheddit changelog

# [1.4.0](https://github.com/wchill/patcheddit/compare/v1.3.1...v1.4.0) (2026-04-07)


### Bug Fixes

* Don't show fake premium patch for reddit is fun golden platinum ([15d372e](https://github.com/wchill/patcheddit/commit/15d372e09cd36252483cf468a7d0a09188d2d4d1))
* Make /r/all patch available ([9740e3d](https://github.com/wchill/patcheddit/commit/9740e3d2ba1c67ae5ba7247f992f3724e8837dbe))
* reddit is fun imgur album loading ([66c73c9](https://github.com/wchill/patcheddit/commit/66c73c969acbbfa038d52cd020533eb39f95f224))


### Features

* /r/all patch for Boost ([60b8743](https://github.com/wchill/patcheddit/commit/60b8743b6fe47aea55fe10d87124b416d692c355))
* Add experimental patch to help resolve some login issues ([3a1025e](https://github.com/wchill/patcheddit/commit/3a1025e0370e9d7f5f5f0c8a5f4202e81f434979))

# [1.4.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.4.0-dev.2...v1.4.0-dev.3) (2026-04-06)


### Features

* Add experimental patch to help resolve some login issues ([3a1025e](https://github.com/wchill/patcheddit/commit/3a1025e0370e9d7f5f5f0c8a5f4202e81f434979))

# [1.4.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.4.0-dev.1...v1.4.0-dev.2) (2026-04-06)


### Bug Fixes

* Make /r/all patch available ([9740e3d](https://github.com/wchill/patcheddit/commit/9740e3d2ba1c67ae5ba7247f992f3724e8837dbe))

# [1.4.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.3.1...v1.4.0-dev.1) (2026-04-06)


### Bug Fixes

* Don't show fake premium patch for reddit is fun golden platinum ([15d372e](https://github.com/wchill/patcheddit/commit/15d372e09cd36252483cf468a7d0a09188d2d4d1))
* reddit is fun imgur album loading ([66c73c9](https://github.com/wchill/patcheddit/commit/66c73c969acbbfa038d52cd020533eb39f95f224))


### Features

* /r/all patch for Boost ([60b8743](https://github.com/wchill/patcheddit/commit/60b8743b6fe47aea55fe10d87124b416d692c355))

## [1.3.1](https://github.com/wchill/patcheddit/compare/v1.3.0...v1.3.1) (2026-04-01)


### Bug Fixes

* Attempt to fix compatibility with Manager 1.14.0 ([174d6ad](https://github.com/wchill/patcheddit/commit/174d6ad6420ba6172f41948053592fea9097d49f))
* Remove dependency on transformInstructions patch ([ed2697d](https://github.com/wchill/patcheddit/commit/ed2697d3ab6eb796c505627b5da1d36b4b09b563))
* **Sync for reddit:** User endpoint patch failing to match ([8cab251](https://github.com/wchill/patcheddit/commit/8cab2512a8d5d9fbebcd4d392384fc9774f1d84c))
* Use replaceStringsPatch with comparison type contains for Sync spoof patch ([b966d81](https://github.com/wchill/patcheddit/commit/b966d8140f9f626bc5919ff84b519e6cce011771))

## [1.3.1-dev.4](https://github.com/wchill/patcheddit/compare/v1.3.1-dev.3...v1.3.1-dev.4) (2026-04-01)


### Bug Fixes

* Use replaceStringsPatch with comparison type contains for Sync spoof patch ([b966d81](https://github.com/wchill/patcheddit/commit/b966d8140f9f626bc5919ff84b519e6cce011771))

## [1.3.1-dev.3](https://github.com/wchill/patcheddit/compare/v1.3.1-dev.2...v1.3.1-dev.3) (2026-04-01)


### Bug Fixes

* Remove dependency on transformInstructions patch ([ed2697d](https://github.com/wchill/patcheddit/commit/ed2697d3ab6eb796c505627b5da1d36b4b09b563))

## [1.3.1-dev.2](https://github.com/wchill/patcheddit/compare/v1.3.1-dev.1...v1.3.1-dev.2) (2026-04-01)


### Bug Fixes

* Attempt to fix compatibility with Manager 1.14.0 ([174d6ad](https://github.com/wchill/patcheddit/commit/174d6ad6420ba6172f41948053592fea9097d49f))

## [1.3.1-dev.1](https://github.com/wchill/patcheddit/compare/v1.3.0...v1.3.1-dev.1) (2026-04-01)


### Bug Fixes

* **Sync for reddit:** User endpoint patch failing to match ([8cab251](https://github.com/wchill/patcheddit/commit/8cab2512a8d5d9fbebcd4d392384fc9774f1d84c))

# [1.3.0](https://github.com/wchill/patcheddit/compare/v1.2.0...v1.3.0) (2026-04-01)


### Bug Fixes

* Bump gradle plugin version ([6435231](https://github.com/wchill/patcheddit/commit/6435231016b9799e978f059d3f35f6cd69add853))
* Check for HTTP 5xx when loading posts in Boost ([511fae0](https://github.com/wchill/patcheddit/commit/511fae08a7380343a26675cfa873588ecde1d714))
* Client ID validation not working ([d6a5e50](https://github.com/wchill/patcheddit/commit/d6a5e500accb41dd001c1f3a6e9bb320c53a41c8))
* Continuum crashing when cold opening reddit links ([7848ebd](https://github.com/wchill/patcheddit/commit/7848ebdea255379b306fa570712cff0a586a8955))
* Crash in Infinity+ settings ([3ff1829](https://github.com/wchill/patcheddit/commit/3ff1829bbe0f6f6834bff32e176a1d37a979a79c))
* Fix issues loading patch bundle ([d644b35](https://github.com/wchill/patcheddit/commit/d644b3582ba7f6f6267f2239d9bee87ffa39c885))
* Improper regex for client ID ([707b2ba](https://github.com/wchill/patcheddit/commit/707b2ba95edfcf784138f4fa5fe0702978cf0d81))
* Infinity crashing when cold opening reddit links ([688f574](https://github.com/wchill/patcheddit/commit/688f5744818ef8be5386c7a7097ef14c2a9fc399))
* Sync for reddit patch errored with "Collection is empty" ([bdb71c4](https://github.com/wchill/patcheddit/commit/bdb71c4349c4e9d4b6c1ff15720814a3c94079e6))
* Use separate names for pro/dev versions of apps ([c060aef](https://github.com/wchill/patcheddit/commit/c060aefbbc6ce28f274278b8d0649bcb7cba30f6))


### Features

* Add support for Infinity+ ([9e28199](https://github.com/wchill/patcheddit/commit/9e28199844e770d3c6b56eaaab6b2417c30560a2))
* Support reddit is fun golden platinum ([a768745](https://github.com/wchill/patcheddit/commit/a768745cdc1a1d703dd5a31b8e026e61a5454cb8))
* Update to Morphe patcher 1.3.0 ([ded92dc](https://github.com/wchill/patcheddit/commit/ded92dc15a062f0defd9eb655e567c24b0373c98))
* Use sqlite-backed cache implementation for Boost ([2cfea64](https://github.com/wchill/patcheddit/commit/2cfea642704aef15793d3f0c3548d870ff021690))

# [1.3.0-dev.5](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.4...v1.3.0-dev.5) (2026-04-01)


### Bug Fixes

* Bump gradle plugin version ([6435231](https://github.com/wchill/patcheddit/commit/6435231016b9799e978f059d3f35f6cd69add853))
* Client ID validation not working ([d6a5e50](https://github.com/wchill/patcheddit/commit/d6a5e500accb41dd001c1f3a6e9bb320c53a41c8))
* Continuum crashing when cold opening reddit links ([7848ebd](https://github.com/wchill/patcheddit/commit/7848ebdea255379b306fa570712cff0a586a8955))
* Crash in Infinity+ settings ([3ff1829](https://github.com/wchill/patcheddit/commit/3ff1829bbe0f6f6834bff32e176a1d37a979a79c))
* Improper regex for client ID ([707b2ba](https://github.com/wchill/patcheddit/commit/707b2ba95edfcf784138f4fa5fe0702978cf0d81))
* Infinity crashing when cold opening reddit links ([688f574](https://github.com/wchill/patcheddit/commit/688f5744818ef8be5386c7a7097ef14c2a9fc399))


### Features

* Add support for Infinity+ ([9e28199](https://github.com/wchill/patcheddit/commit/9e28199844e770d3c6b56eaaab6b2417c30560a2))

# [1.3.0-dev.9](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.8...v1.3.0-dev.9) (2026-04-01)


### Bug Fixes

* Crash in Infinity+ settings ([6bfa378](https://github.com/wchill/patcheddit/commit/6bfa378f303eeb952117e5c108dcb6185c7bab48))

# [1.3.0-dev.8](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.7...v1.3.0-dev.8) (2026-03-31)


### Bug Fixes

* Continuum crashing when cold opening reddit links ([36250c9](https://github.com/wchill/patcheddit/commit/36250c9eddaf382cc25fb7fecb5e3cffe29609a1))
* Infinity crashing when cold opening reddit links ([1e3ef91](https://github.com/wchill/patcheddit/commit/1e3ef919f1aa1c4371ed140def1b3f8fdc933342))

# [1.3.0-dev.7](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.6...v1.3.0-dev.7) (2026-03-31)


### Bug Fixes

* Improper regex for client ID ([90b8161](https://github.com/wchill/patcheddit/commit/90b816125a3ec5c9ac99b636f5570aacdc86fa5e))

# [1.3.0-dev.6](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.5...v1.3.0-dev.6) (2026-03-31)


### Bug Fixes

* Client ID validation not working ([00545a8](https://github.com/wchill/patcheddit/commit/00545a8b925b12578d2e113f6481eddaa93dfeef))

# [1.3.0-dev.5](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.4...v1.3.0-dev.5) (2026-03-31)


### Features

* Add support for Infinity+ ([9c62c1c](https://github.com/wchill/patcheddit/commit/9c62c1cae2f1ffdfcb4f0f488f38dff910355f57))

# [1.3.0-dev.4](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.3...v1.3.0-dev.4) (2026-03-31)


### Bug Fixes

* Sync for reddit patch errored with "Collection is empty" ([bdb71c4](https://github.com/wchill/patcheddit/commit/bdb71c4349c4e9d4b6c1ff15720814a3c94079e6))


### Features

* Support reddit is fun golden platinum ([a768745](https://github.com/wchill/patcheddit/commit/a768745cdc1a1d703dd5a31b8e026e61a5454cb8))

# [1.3.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.2...v1.3.0-dev.3) (2026-03-22)


### Bug Fixes

* Use separate names for pro/dev versions of apps ([c060aef](https://github.com/wchill/patcheddit/commit/c060aefbbc6ce28f274278b8d0649bcb7cba30f6))

# [1.3.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.3.0-dev.1...v1.3.0-dev.2) (2026-03-22)


### Bug Fixes

* Fix issues loading patch bundle ([d644b35](https://github.com/wchill/patcheddit/commit/d644b3582ba7f6f6267f2239d9bee87ffa39c885))

# [1.3.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.2.0...v1.3.0-dev.1) (2026-03-22)


### Bug Fixes

* Check for HTTP 5xx when loading posts in Boost ([511fae0](https://github.com/wchill/patcheddit/commit/511fae08a7380343a26675cfa873588ecde1d714))


### Features

* Update to Morphe patcher 1.3.0 ([ded92dc](https://github.com/wchill/patcheddit/commit/ded92dc15a062f0defd9eb655e567c24b0373c98))
* Use sqlite-backed cache implementation for Boost ([2cfea64](https://github.com/wchill/patcheddit/commit/2cfea642704aef15793d3f0c3548d870ff021690))

# [1.2.0](https://github.com/wchill/patcheddit/compare/v1.1.0...v1.2.0) (2026-01-20)


### Bug Fixes

* Fix broken Sync for Reddit ad removal patch ([673825c](https://github.com/wchill/patcheddit/commit/673825c91cca62c6596c0709a8886b9c5672385f))
* Remove Infinity for Reddit patches (use Continuum for Reddit instead) ([32e92a0](https://github.com/wchill/patcheddit/commit/32e92a07ed33696ee2c92c51eb2f916420803f48))


### Features

* Add client ID patching support for Continuum for Reddit ([#16](https://github.com/wchill/patcheddit/issues/16)) ([32e5408](https://github.com/wchill/patcheddit/commit/32e5408fadc61503d4a528828474f4aeb514fbdc))
* Add client ID patching support for Slide for Reddit fork ([#18](https://github.com/wchill/patcheddit/issues/18)) ([cf0f0f1](https://github.com/wchill/patcheddit/commit/cf0f0f18f984c9ad367f790fca7203325ed29d25))
* Add support for /s/ links in Relay ([#21](https://github.com/wchill/patcheddit/issues/21)) ([eb0ba9b](https://github.com/wchill/patcheddit/commit/eb0ba9b08b8e45ca31a03a3354e82ebc3eed9987))

# [1.2.0-dev.4](https://github.com/wchill/patcheddit/compare/v1.2.0-dev.3...v1.2.0-dev.4) (2026-01-20)


### Features

* Add support for /s/ links in Relay ([#21](https://github.com/wchill/patcheddit/issues/21)) ([eb0ba9b](https://github.com/wchill/patcheddit/commit/eb0ba9b08b8e45ca31a03a3354e82ebc3eed9987))

# [1.2.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.2.0-dev.2...v1.2.0-dev.3) (2026-01-19)


### Bug Fixes

* Fix broken Sync for Reddit ad removal patch ([673825c](https://github.com/wchill/patcheddit/commit/673825c91cca62c6596c0709a8886b9c5672385f))

# [1.2.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.2.0-dev.1...v1.2.0-dev.2) (2026-01-19)


### Features

* Add client ID patching support for Slide for Reddit fork ([#18](https://github.com/wchill/patcheddit/issues/18)) ([cf0f0f1](https://github.com/wchill/patcheddit/commit/cf0f0f18f984c9ad367f790fca7203325ed29d25))

# [1.2.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.1.0...v1.2.0-dev.1) (2026-01-19)


### Bug Fixes

* Remove Infinity for Reddit patches (use Continuum for Reddit instead) ([32e92a0](https://github.com/wchill/patcheddit/commit/32e92a07ed33696ee2c92c51eb2f916420803f48))


### Features

* Add client ID patching support for Continuum for Reddit ([#16](https://github.com/wchill/patcheddit/issues/16)) ([32e5408](https://github.com/wchill/patcheddit/commit/32e5408fadc61503d4a528828474f4aeb514fbdc))

# [1.1.0](https://github.com/wchill/patcheddit/compare/v1.0.1...v1.1.0) (2026-01-09)


### Features

* Add imgur album patch for rif (from null-dev/revanced-patches@1dabe26) ([d7cc5a1](https://github.com/wchill/patcheddit/commit/d7cc5a1a653991c759d53171b09a6b6a69c08518))
* Add rif premium unlock patch (from null-dev/revanced-patches@1dabe26) ([aff0385](https://github.com/wchill/patcheddit/commit/aff0385b158f883b2bbd445a16b1d9c5b5175b2d))
* Add support for patching BaconReader with custom redirect URI ([a99d598](https://github.com/wchill/patcheddit/commit/a99d5980b22a25843f97d88045874c73435940dd))
* Add support for patching Joey with custom redirect URI ([bc7bc1a](https://github.com/wchill/patcheddit/commit/bc7bc1a7acf05eacf7fff8f62a217cfdeb027049))
* Add support for patching Relay with custom redirect URI ([55a1958](https://github.com/wchill/patcheddit/commit/55a1958008086651e7d97a1588c728325a08a7aa))
* Add support for patching rif with custom redirect URI (from altherat/revanced-patches@fe01d43) ([e2aa03a](https://github.com/wchill/patcheddit/commit/e2aa03ae1bee92a499166647e9d19e5492904a8d))
* Add support for patching Sync with custom redirect URI ([8822a60](https://github.com/wchill/patcheddit/commit/8822a605d4c41cd3ba49fe60f85b50531daff4d5))

# [1.1.0-dev.4](https://github.com/wchill/patcheddit/compare/v1.1.0-dev.3...v1.1.0-dev.4) (2026-01-09)


### Features

* Add support for patching Joey with custom redirect URI ([bc7bc1a](https://github.com/wchill/patcheddit/commit/bc7bc1a7acf05eacf7fff8f62a217cfdeb027049))
* Add support for patching Sync with custom redirect URI ([8822a60](https://github.com/wchill/patcheddit/commit/8822a605d4c41cd3ba49fe60f85b50531daff4d5))

# [1.1.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.1.0-dev.2...v1.1.0-dev.3) (2026-01-09)


### Features

* Add support for patching BaconReader with custom redirect URI ([a99d598](https://github.com/wchill/patcheddit/commit/a99d5980b22a25843f97d88045874c73435940dd))

# [1.1.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.1.0-dev.1...v1.1.0-dev.2) (2026-01-09)


### Features

* Add support for patching Relay with custom redirect URI ([55a1958](https://github.com/wchill/patcheddit/commit/55a1958008086651e7d97a1588c728325a08a7aa))

# [1.1.0-dev.1](https://github.com/wchill/patcheddit/compare/v1.0.1...v1.1.0-dev.1) (2026-01-09)


### Features

* Add imgur album patch for rif (from null-dev/revanced-patches@1dabe26) ([d7cc5a1](https://github.com/wchill/patcheddit/commit/d7cc5a1a653991c759d53171b09a6b6a69c08518))
* Add rif premium unlock patch (from null-dev/revanced-patches@1dabe26) ([aff0385](https://github.com/wchill/patcheddit/commit/aff0385b158f883b2bbd445a16b1d9c5b5175b2d))
* Add support for patching rif with custom redirect URI (from altherat/revanced-patches@fe01d43) ([e2aa03a](https://github.com/wchill/patcheddit/commit/e2aa03ae1bee92a499166647e9d19e5492904a8d))

# 1.0.0 (2026-01-08)


### Bug Fixes

* Copy all patches from previous repo and make them compile ([d6f98de](https://github.com/wchill/patcheddit/commit/d6f98de407fff1a8a58c4e89e5c11970a45e35dc))
* Fix extension class namespaces in patch code ([d14a92e](https://github.com/wchill/patcheddit/commit/d14a92ea494af721ff8c17e844492b1a0835d690))
* Refresh patches.api ([ac4a2ee](https://github.com/wchill/patcheddit/commit/ac4a2ee6a0ac39dcb51786403564e6dccd746bec))
* Update extension code to new namespace and fix build ([2c484a6](https://github.com/wchill/patcheddit/commit/2c484a614123a19880a3080250563d6657aea10a))
* use strings parameter instead of string filters ([951e1bd](https://github.com/wchill/patcheddit/commit/951e1bdee6a7b8307c226e43dbed5ef4216a54ae))


### Features

* Migrate all fingerprints to declarative API ([56374b8](https://github.com/wchill/patcheddit/commit/56374b80e1ad6ea3fe67da9ae9d871b1d94161a3))

# [1.0.0-dev.3](https://github.com/wchill/patcheddit/compare/v1.0.0-dev.2...v1.0.0-dev.3) (2026-01-03)


### Bug Fixes

* Fix extension class namespaces in patch code ([d14a92e](https://github.com/wchill/patcheddit/commit/d14a92ea494af721ff8c17e844492b1a0835d690))

# [1.0.0-dev.2](https://github.com/wchill/patcheddit/compare/v1.0.0-dev.1...v1.0.0-dev.2) (2026-01-03)


### Bug Fixes

* use strings parameter instead of string filters ([951e1bd](https://github.com/wchill/patcheddit/commit/951e1bdee6a7b8307c226e43dbed5ef4216a54ae))

# 1.0.0-dev.1 (2026-01-03)


### Bug Fixes

* Copy all patches from previous repo and make them compile ([d6f98de](https://github.com/wchill/patcheddit/commit/d6f98de407fff1a8a58c4e89e5c11970a45e35dc))
* Refresh patches.api ([ac4a2ee](https://github.com/wchill/patcheddit/commit/ac4a2ee6a0ac39dcb51786403564e6dccd746bec))
* Update extension code to new namespace and fix build ([2c484a6](https://github.com/wchill/patcheddit/commit/2c484a614123a19880a3080250563d6657aea10a))


### Features

* Migrate all fingerprints to declarative API ([56374b8](https://github.com/wchill/patcheddit/commit/56374b80e1ad6ea3fe67da9ae9d871b1d94161a3))
