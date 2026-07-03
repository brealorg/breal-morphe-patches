# Breal Morphe Patches

## Add to Morphe

Preferred install/source link:

https://morphe.software/add-source?github=brealorg/breal-morphe-patches

Manual fallback:

https://github.com/brealorg/breal-morphe-patches

1.4.43 fixes Boost comment code block rendering so Reddit fenced code preserves line breaks and surrounding prose stays outside code blocks.

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
| Version | `1.4.46` |
| Release tag | `v1.4.46` |
| Asset | `patches-1.4.46.mpp` |
| SHA256 | `43c41f59733c4805ec7e0fd0815ded1d49deda7179e65464c5a9abe10593acdf` |

SHA256: `43c41f59733c4805ec7e0fd0815ded1d49deda7179e65464c5a9abe10593acdf`
| Manager JSON | `https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/v1.4.46/patches-1.4.46.mpp` |

## What this bundle does

The current bundle is focused on practical hotfixes for tested app versions, especially Boost for Reddit behavior on newer Android versions.

### Boost for Reddit

Tested against Boost for Reddit `1.12.12` / versionCode `210011212`.

Included in `1.4.44`:

1.4.44 fixes a Boost crash when hiding a post from a feed if Boost receives an invalid list index.

Also included from `1.4.43`:

1.4.43 fixes Boost comment rendering for Reddit fenced code blocks. Multiline code is rendered as Boost native code blocks, while prose between adjacent code blocks stays outside the code background.

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

### Patches list

<!-- PATCHES_START -->
> **Patch source version:** `v1.4.46` • `main` • 30 unique patches • 82 package entries

<details>
<summary><strong>Boost for Reddit</strong> • 21 patches</summary>



**Supported versions:**

| 1.12.12 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Add archive links to context menu](#add-archive-links-to-context-menu) |  |  |
| [Automatically undelete Imgur images](#automatically-undelete-imgur-images) |  |  |
| [Automatically undelete Reddit content](#automatically-undelete-reddit-content) |  |  |
| [Boost Morphe settings](#boost-morphe-settings) | Adds Boost Morphe settings for inline media previews, source text visibility, and preview alignment. |  |
| [Disable ads](#disable-ads) |  |  |
| [Disable Boost Crashlytics startup network calls](#disable-boost-crashlytics-startup-network-calls) | Disables Boost's Crashlytics startup initialization while keeping Firebase Analytics and other Firebase components. |  |
| [Fix /r/all](#fix-r-all) |  |  |
| [Fix /s/ links](#fix-s-links) |  |  |
| [Fix Boost code block rendering](#fix-boost-code-block-rendering) | Preserves multiline Reddit code blocks by normalizing inline multiline <code> HTML to Boost's native <pre> renderer path. |  |
| [Fix Boost comments Lemmy-style toolbar UI](#fix-boost-comments-lemmy-style-toolbar-ui) | Removes the duplicate native comments title by disabling the SlidrTheme window title/actionbar layer while preserving Boost's selected light/dark theme, toolbar title, and dynamic sort subtitle. |  |
| [Fix Boost navigation bar overlap](#fix-boost-navigation-bar-overlap) | Adds runtime system bar inset handling for Boost bottom controls and drawer content on Android 15+ target SDK builds. |  |
| [Fix Boost target SDK 35 compatibility](#fix-boost-target-sdk-35-compatibility) | Sets Boost for Reddit's target SDK to 35 and fixes BillingClient receiver registration for newer Android versions. |  |
| [Fix download completed notification visibility](#fix-download-completed-notification-visibility) | Moves completed download notifications to a separate default-importance channel so download completion is visible while progress notifications remain low-priority. |  |
| [Fix Hide crash](#fix-hide-crash) | Prevents Boost from crashing when Hide receives an invalid feed/list index. |  |
| [Fix missing audio in video downloads](#fix-missing-audio-in-video-downloads) | Fixes audio missing in videos downloaded from v.redd.it. |  |
| [Fix Redgifs API](#fix-redgifs-api) |  |  |
| [Fix slow Giphy loading](#fix-slow-giphy-loading) | Bypasses Boost's slow Giphy API resolver and uses Boost's direct media.giphy.com MP4 fallback for Giphy posts. |  |
| [Hook exception handler](#hook-exception-handler) | Hook the exception handler in Boost. Don't enable except for development purposes |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Show inline Giphy previews in comments](#show-inline-giphy-previews-in-comments) | Adds inline animated Giphy previews below Boost comment text for Reddit Giphy markdown and Giphy links. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

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



| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) |  |  |
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>io.syncapps.lemmy_sync</strong> • 1 patch</summary>



| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) |  |  |

</details>

<details>
<summary><strong>com.andrewshu.android.reddit</strong> • 4 patches</summary>



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
<summary><strong>free.reddit.news</strong> • 3 patches</summary>



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
<summary><strong>reddit.news</strong> • 3 patches</summary>



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
<summary><strong>com.onelouder.baconreader</strong> • 3 patches</summary>



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
<summary><strong>com.onelouder.baconreader.premium</strong> • 3 patches</summary>



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
<summary><strong>ml.docilealligator.infinityforreddit.plus</strong> • 2 patches</summary>



| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>ml.docilealligator.infinityforreddit.patreon</strong> • 2 patches</summary>



| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>o.o.joey.pro</strong> • 2 patches</summary>



| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>o.o.joey.dev</strong> • 2 patches</summary>



| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Modify the WebView used for logging into reddit to prevent login issues |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>com.andrewshu.android.redditdonation</strong> • 3 patches</summary>



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
<summary><strong>org.cygnusx1.continuum</strong> • 1 patch</summary>



| Patch | Description | Options |
|---|---|---|
| [Spoof client](#spoof-client) | Allows modifying Continuum's client ID, redirect URI and user agent in API Keys settings menu. Patch options will modify default values. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>me.edgan.redditslide</strong> • 1 patch</summary>



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

Release `1.4.45` is prepared and locally verified with:

- Release tag `morphe-patches-45`.
- Local built MPP SHA256 matching README.
`5af6aa523d3373cf5222ce04ff8f859a3397ec59892088e3fbb1b9f2655b24eb`
- `patches-bundle.json` returning version `1.4.45`.
- `patches-bundle.json` pointing to the `morphe-patches-45` asset.
- Expected release asset:
`patches-1.4.45.mpp`
- `5af6aa523d3373cf5222ce04ff8f859a3397ec59892088e3fbb1b9f2655b24eb  patches-1.4.45.mpp`

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
