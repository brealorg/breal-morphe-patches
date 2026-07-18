# Breal Morphe Patches

## Add to Morphe

### Morphe Manager source URL field

Paste this value into Morphe Manager when adding a remote source:

```text
github.com/brealorg/breal-morphe-patches

Alternative raw JSON URL:

https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json
Browser deep link

Use this only from a browser/link handler, not inside the Morphe Manager source URL text field:

https://morphe.software/add-source?github=brealorg/breal-morphe-patches

Manual fallback / repository page:

https://github.com/brealorg/breal-morphe-patches

1.4.47 adds Boost keyboard GIF insertion through Android receive-content / Gboard and restores manual GIF URL insertion.

Unofficial Morphe patch bundle for Boost for Reddit, Imgur, and related Android app fixes.

This repository publishes a Morphe-compatible `.mpp` bundle and a small JSON feed used by Morphe Manager to discover the current release.

Release process notes:

- [Release validation policy](docs/release-validation-policy.md)
- [Final snapshot template](docs/final-snapshot-template.md)

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

Preferred Morphe source setup is the deep link below. Raw `patches-bundle.json` links are kept only for release/debug verification, not as the primary user-facing Manager instruction.

## Current release

| Field | Value |
|---|---|
| Version | `1.4.87` |
| Release tag | `morphe-patches-87` |
| Asset | `patches-1.4.87.mpp` |
| SHA256 | `e51a1c95e748e94fd1213e488d0aeb7b7878e7bd2d0dfdb37efd99cfdbf02b7b` |

SHA256: `e51a1c95e748e94fd1213e488d0aeb7b7878e7bd2d0dfdb37efd99cfdbf02b7b`
| Manager JSON | `https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/morphe-patches-87/patches-1.4.87.mpp` |

## What this bundle does

The current bundle is focused on practical hotfixes for tested app versions, especially Boost for Reddit behavior on newer Android versions.

### Boost for Reddit

Tested against Boost for Reddit `1.12.12` / versionCode `210011212`.

Current Boost coverage includes keyboard GIF insertion through Android receive-content / Gboard, restored manual GIF URL insertion, code block rendering fixes, comments toolbar cleanup, media tap-action settings, Android 15+ / target SDK 35 compatibility, navigation bar inset fixes, download notification/audio fixes, direct `i.redd.it` GIF handling, Giphy preview/loading fixes, archive/undelete behavior, gallery metadata recovery, Hide crash prevention, and subreddit listing fallback handling.


### NSFW and mature-content scope

Restoring or bypassing access to Reddit NSFW communities is not currently within the scope of this project. This is a technical and maintenance decision, not a judgement about legal adult content or the people who use it.

Reddit controls mature-content availability through its servers, API, account state, and age-assurance systems. Boost can only display content that Reddit returns to the authenticated client. Local "show mature content" settings cannot override an API response that rejects, filters, or omits the content.

This project will not implement age-verification bypasses, moderator-status or client-identity workarounds, scraping or proxy services for restricted Reddit content, a replacement random-NSFW discovery service, or dedicated ongoing maintenance of adult-oriented media providers.

The inherited `Fix Redgifs API` patch provides best-effort compatibility with an obsolete client API flow. It does not guarantee access to NSFW communities, availability or reliable playback of individual Redgifs media, or future provider compatibility. See [Issue #12](https://github.com/brealorg/breal-morphe-patches/issues/12) and [Issue #13](https://github.com/brealorg/breal-morphe-patches/issues/13).

For the complete rationale and support boundaries, see [NSFW and mature-content support policy](docs/nsfw-support-policy.md).

An issue is not automatically rejected merely because the affected post is marked NSFW. Generic crashes, layout problems, viewer bugs, and media-routing regressions may still be investigated when the same problem can be reproduced with non-NSFW content, or when Reddit has returned the content successfully and Boost handles it incorrectly.

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

### Patches list

<!-- PATCHES_START -->
> **Patch source version:** `1.4.87` • `main` • 50 unique patches • 102 package entries

<details>
<summary><strong>Boost for Reddit</strong> • 41 patches</summary>



**Supported versions:**

| 1.12.12 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Add archive links to context menu](#add-archive-links-to-context-menu) |  |  |
| [Add Boost Search bottom navigation](#add-boost-search-bottom-navigation) | Shows Boost's native Home, Search, Subscriptions, Inbox and Profile menu in Search and Go To. |  |
| [Animate media in Boost gallery previews](#animate-media-in-boost-gallery-previews) | Autoplays selected Reddit gallery GIF and video media while preserving Boost's poster, data preferences, and full-screen media route. |  |
| [Automatically undelete Imgur images](#automatically-undelete-imgur-images) |  |  |
| [Automatically undelete Reddit content](#automatically-undelete-reddit-content) |  |  |
| [Boost Morphe settings](#boost-morphe-settings) | Adds Boost Morphe settings for inline media previews, undelete toggles, adaptive refresh rate, source text visibility, and preview alignment. |  |
| [Boost search default discovery](#boost-search-default-discovery) | Starts Boost search on an instant cached active-subreddit landing with Reddit-native labels. |  |
| [Custom Synccit URL](#custom-synccit-url) | Allows Boost to use a custom self-hosted Synccit API endpoint. | • Synccit API URL |
| [Disable ads](#disable-ads) |  |  |
| [Disable Boost Crashlytics startup network calls](#disable-boost-crashlytics-startup-network-calls) | Disables Boost's Crashlytics startup initialization while keeping Firebase Analytics and other Firebase components. |  |
| [Fix /r/all](#fix-r-all) |  |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix Boost code block rendering](#fix-boost-code-block-rendering) | Preserves Reddit code blocks by normalizing multiline <code> HTML and malformed fenced selftext to Boost's native <pre> renderer path. |  |
| [Fix Boost comments Lemmy-style toolbar UI](#fix-boost-comments-lemmy-style-toolbar-ui) | Removes the duplicate native comments title by disabling the SlidrTheme window title/actionbar layer while preserving Boost's selected light/dark theme, toolbar title, and dynamic sort subtitle. |  |
| [Fix Boost image widget click target](#fix-boost-image-widget-click-target) | Prevents Boost's image widget from opening a stale post by making the CommentsActivity PendingIntent data unique per widget update. |  |
| [Fix Boost native image upload](#fix-boost-native-image-upload) | Forces Boost's single-image submit flow to use Reddit's native image submission kind instead of creating external uploaded-media link posts. |  |
| [Fix Boost navigation bar overlap](#fix-boost-navigation-bar-overlap) | Adds runtime system bar inset handling for Boost bottom controls and drawer content on Android 15+ target SDK builds. |  |
| [Fix Boost target SDK 35 compatibility](#fix-boost-target-sdk-35-compatibility) | Sets Boost for Reddit's target SDK to 35 and fixes BillingClient receiver registration for newer Android versions. |  |
| [Fix Boost YouTube playback fallback](#fix-boost-youtube-playback-fallback) | Opens the original YouTube link externally when Boost's legacy embedded YouTube player is unavailable. |  |
| [Fix download completed notification visibility](#fix-download-completed-notification-visibility) | Moves completed download notifications to a separate default-importance channel so download completion is visible while progress notifications remain low-priority. |  |
| [Fix Hide crash](#fix-hide-crash) | Stabilizes Boost feed position handling and prevents invalid-index crashes when hiding read posts. |  |
| [Fix missing audio in video downloads](#fix-missing-audio-in-video-downloads) | Fixes audio missing in videos downloaded from v.redd.it. |  |
| [Fix Random subreddit](#fix-random-subreddit) | Normalizes Boost's broken r/random route by resolving a live non-NSFW subreddit through Reddit search and subscriber-count filtering. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix slow Giphy loading](#fix-slow-giphy-loading) | Bypasses Boost's slow Giphy API resolver and uses Boost's direct media.giphy.com MP4 fallback for Giphy posts. |  |
| [Hook exception handler](#hook-exception-handler) | Hook the exception handler in Boost. Don't enable except for development purposes |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Prefer high refresh rate](#prefer-high-refresh-rate) | Requests a high display refresh rate for Boost on adaptive-refresh devices. |  |
| [Restore Boost Open by default prompt](#restore-boost-open-by-default-prompt) | Prompts after Boost patch updates to reopen Android's Open by default settings so supported Reddit links can be re-enabled. |  |
| [Restore Boost sidebar Trending today](#restore-boost-sidebar-trending-today) | Uses native trending data when renderable, otherwise supplies HOT post rows and fixes the global community-limit control. |  |
| [Restore GIF search integration probe](#restore-gif-search-integration-probe) | Fingerprint-only probe for Boost compose/reply GIF insertion through FormattingBar. No runtime behavior change. |  |
| [Restore GIF URL insertion](#restore-gif-url-insertion) | Adds provider-independent manual GIF URL insertion to Boost's existing image menu. |  |
| [Show inline Giphy previews in comments](#show-inline-giphy-previews-in-comments) | Adds inline media previews below Boost comment text for Giphy links and direct static image URLs. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Stabilize Boost subreddit bottom navigation surface](#stabilize-boost-subreddit-bottom-navigation-surface) | Applies theme contrast and gesture-inset surface handling to SubredditActivity while preserving native selection and routes. |  |
| [Standardize Boost bottom navigation](#standardize-boost-bottom-navigation) | Applies one destination, listener, selection, tint, and system-surface contract to Boost's Material bottom navigation. |  |
| [Standardize Boost bottom-navigation FAB clearance](#standardize-boost-bottom-navigation-fab-clearance) | Keeps Home/Subreddit, Random, Inbox and Profile FABs 16 dp above the canonical bottom navigation. |  |
| [Standardize Boost Home bottom navigation](#standardize-boost-home-bottom-navigation) | Applies the canonical five-destination Material navigation and native Home tint when MainActivity resumes. |  |
| [Standardize Boost Inbox and Profile bottom navigation](#standardize-boost-inbox-and-profile-bottom-navigation) | Applies the canonical five-destination Material bottom-navigation contract after Inbox and Profile complete lifecycle setup. |  |
| [Support keyboard GIF insertion](#support-keyboard-gif-insertion) | Enables keyboard GIF/image rich content insertion through Android receive-content when a public URL is available. |  |
| [Theme Boost settings icons](#theme-boost-settings-icons) | Uses the active theme primary text color for Boost preference icons. |  |

</details>

<details>
<summary><strong>Sync for Reddit</strong> • 9 patches</summary>



**Supported versions:**

| v23.06.30-13:39 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) |  |  |
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Disables the bottom sheet at the startup that asks you to signup to "Sync for Lemmy". |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix video downloads](#fix-video-downloads) | Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug. |  |

</details>

<details>
<summary><strong>Sync for Reddit Pro</strong> • 8 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Disables the bottom sheet at the startup that asks you to signup to "Sync for Lemmy". |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix video downloads](#fix-video-downloads) | Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug. |  |

</details>

<details>
<summary><strong>Sync for Reddit Dev</strong> • 8 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Disables the bottom sheet at the startup that asks you to signup to "Sync for Lemmy". |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix video downloads](#fix-video-downloads) | Fixes a bug in Sync's MPD parser resulting in only the audio-track being saved. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Replaces the deprecated endpoint for viewing user profiles /u with /user, that used to fix a bug. |  |

</details>

<details>
<summary><strong>Joey for Reddit</strong> • 3 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) |  |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Sync for Lemmy</strong> • 1 patch</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) |  |  |

</details>

<details>
<summary><strong>rif is fun</strong> • 4 patches</summary>



**Supported versions:**

| 5.6.22 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fake reddit premium](#fake-reddit-premium) | Allows using pro features without ads. |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use public imgur API](#use-public-imgur-api) | Fix imgur albums not loading. |  |

</details>

<details>
<summary><strong>Relay for Reddit</strong> • 3 patches</summary>



**Supported versions:**

| 10.2.40 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix /s/ links](#fix-s-links) |  |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Relay for Reddit Pro</strong> • 3 patches</summary>



**Supported versions:**

| 10.2.40 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix /s/ links](#fix-s-links) |  |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>BaconReader</strong> • 3 patches</summary>



**Supported versions:**

| 6.1.4 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>BaconReader Premium</strong> • 3 patches</summary>



**Supported versions:**

| 6.1.4 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Infinity for Reddit+</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Infinity for Reddit (Patreon)</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Joey for Reddit Pro</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Joey for Reddit Dev</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>rif is fun golden platinum</strong> • 3 patches</summary>



**Supported versions:**

| 5.6.22 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use public imgur API](#use-public-imgur-api) | Fix imgur albums not loading. |  |

</details>

<details>
<summary><strong>Imgur</strong> • 2 patches</summary>



**Supported versions:**

| 7.33.0.0 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Share selected media](#share-selected-media) | Makes Imgur direct media sharing use the selected media item instead of the parent gallery. Can share either the raw media/download URL or the Imgur item permalink. | • Link mode |
| [Share selected media file](#share-selected-media-file) | Makes Imgur post-detail media long-press share the selected media file. Also replaces Imgur's Download share action with direct file sharing. The selected media is cached privately, shared with Android's share sheet, and is not saved permanently to /sdcard/Download/Imgur. |  |

</details>

<details>
<summary><strong>Continuum</strong> • 1 patch</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Spoof client](#spoof-client) | Allows modifying Continuum's client ID, redirect URI and user agent in API Keys settings menu. Patch options will modify default values. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Slide (fork)</strong> • 1 patch</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Spoof client](#spoof-client) | Allows modifying Slide's client ID, redirect URI and user agent in settings. Patch options will modify default values. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Universal</strong> • 1 patch</summary>



| Patch | Description | Options |
|---|---|---|
| [Enable Android debugging](#enable-android-debugging) | Enables Android developer debugging capabilities. Including this patch can slow down the app. |  |

</details>
<!-- PATCHES_END -->

## Tested app versions

| App | Package | Version |
|---|---|---|
| Boost for Reddit | `com.rubenmayayo.reddit` | `1.12.12` / versionCode `210011212` |
| Imgur | `com.imgur.mobile` | `7.33.0.0` / versionCode `73300` |

Compatibility with other app versions is not guaranteed.

## Verification

Release `1.4.87` is prepared and locally verified with:

- Release tag `morphe-patches-87`.
- Local built MPP SHA256 matching README.
`e51a1c95e748e94fd1213e488d0aeb7b7878e7bd2d0dfdb37efd99cfdbf02b7b`
- `patches-bundle.json` returning version `1.4.87`.
- `patches-bundle.json` pointing to the `morphe-patches-87` asset.
- Expected release asset:
`patches-1.4.87.mpp`
- `e51a1c95e748e94fd1213e488d0aeb7b7878e7bd2d0dfdb37efd99cfdbf02b7b  patches-1.4.87.mpp`

## Development notes

This repository contains experimental and release-candidate work in local/work branches. Only the published release JSON and GitHub release asset should be treated as the current user-facing bundle.

### Deferred work

16K / WL04R builder and runtime work is intentionally **not included** in `1.4.43`.

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
