# Breal Morphe Patches

> **Boost for Reddit is officially back.** Ruben Mayayo now publishes signed Boost releases on [GitHub](https://github.com/rubenmayayo/boost-for-reddit/releases). The Boost patches in this bundle are frozen at `1.12.12` and will not be ported to 1.14 or later. Please report bugs in the official version on [Ruben's issue tracker](https://github.com/rubenmayayo/boost-for-reddit/issues).

<details>
<summary>Switching from patched Boost to the official version</summary>

Patched Boost is signed with a different key than Ruben's APK, so his version cannot be installed over it. You have to uninstall first, which deletes local data.

1. In patched Boost, export your settings: Settings → Backup settings → Export. The backup includes settings, filters, user tags, themes, and views and sort per subscription. It does *not* include accounts, drafts, read posts, or visited-communities history.
2. Uninstall patched Boost.
3. Install the latest APK from [Ruben's releases](https://github.com/rubenmayayo/boost-for-reddit/releases/latest) and verify the signing certificate as described in his README.
4. Set up your own Reddit client ID ([guide](https://github.com/rubenmayayo/boost-for-reddit/blob/main/REDDIT_API_SETUP.md)), log in, and import the backup.

</details>

<details>
<summary>Legacy: about this bundle</summary>

Unofficial Morphe patch bundle for Boost for Reddit, Imgur, and related Android app fixes.

## What this bundle does

### Boost for Reddit

**Status: frozen at `1.12.12`.**

Ruben Mayayo has brought Boost back, with official, signed releases on [GitHub](https://github.com/rubenmayayo/boost-for-reddit/releases). Boost is his app and it is not open source, so this project will not build a patched variant of his new versions.

- The Boost patches are frozen at `1.12.12`. They will not be ported to 1.14 or later.
- Instead, the work goes into helping Boost itself: reporting bugs to Ruben, and fixes where they're welcome. Many of the problems these patches fixed are already solved in 1.14.0.
- The bundle stays available for `1.12.12` for now, but there will be no new Boost features or fixes here.

Report bugs in the official version on [Ruben's issue tracker](https://github.com/rubenmayayo/boost-for-reddit/issues), not in this repository. If you like the app, consider [supporting Ruben](https://ko-fi.com/mayayo).

#### What the 1.12.12 patches cover

Tested against Boost for Reddit `1.12.12` / versionCode `210011212`.

Boost coverage includes keyboard GIF insertion through Android receive-content / Gboard, restored manual GIF URL insertion, code block rendering fixes, comments toolbar cleanup, media tap-action settings, Android 15+ / target SDK 35 compatibility, navigation bar inset fixes, download notification/audio fixes, direct `i.redd.it` GIF handling, Giphy preview/loading fixes, archive/undelete behavior, gallery metadata recovery, Hide crash prevention, and subreddit listing fallback handling.

#### NSFW and mature-content scope

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

</details>

<details>
<summary>Legacy: add to Morphe</summary>

## Add to Morphe

Since 1.4.122 this bundle requires **Morphe Manager 2.0** or newer. Manager 1.x cannot load it.

Paste this value into Morphe Manager when adding a remote source:

```text
github.com/brealorg/breal-morphe-patches
```

Or open **[➕ Add to Morphe](https://morphe.software/add-source?github=brealorg/breal-morphe-patches)** from a browser on your phone.

Raw JSON source:

```text
https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json
```

Legacy source URL, kept working through GitHub redirect:

```text
https://raw.githubusercontent.com/brealorg/breal-boost-hotfixes/main/patches-bundle.json
```

Legacy repository URLs under `breal-boost-hotfixes` redirect here. Do not recreate a repository with the old name, because that may break existing redirects.

### Spoof client

The Reddit client patches need an OAuth client ID. `Spoof client` has no default value, so patching fails with `The option client-id requires a value` if it is left empty.

Reddit no longer issues new client IDs, so registering your own app at reddit.com/prefs/apps does not work. Use RedReader's client ID instead:

1. Install RedReader from the Play Store and log in.
2. Reddit sends an email titled *"You've authorized a new app in your Reddit account"*. Copy the **App ID** from it. RedReader can be uninstalled afterwards.
3. In Morphe Manager, set these `Spoof client` options, with no trailing spaces or slashes:
   - **OAuth client ID:** the App ID from the email
   - **Redirect URI:** `redreader://rr_oauth_redir`
   - **User agent:** `org.quantumbadger.redreader/1.25.1` (ignore the format hint in the option description)
4. Select your other patches and patch as normal.

Full guide and login troubleshooting: [patcheddit — What if I don't have a client ID?](https://github.com/wchill/patcheddit#what-if-i-dont-have-a-client-id)

</details>

<details>
<summary>Legacy: patches list</summary>

### Patches list

<!-- PATCHES_START -->
> **Patch source version:** `1.4.123` • `main` • 57 unique patches • 109 package entries

<details>
<summary><strong>Boost for Reddit</strong> • 48 patches</summary>



> **Frozen at 1.12.12.** Boost is officially back with signed releases from Ruben Mayayo. These patches will not be ported to 1.14 or later. See the top of this README for how to switch.

**Supported versions:**

| 1.12.12 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Add archive links to context menu](#add-archive-links-to-context-menu) | Adds 'Open with Wayback Machine' and 'Open with Archive.is' to the link context menu. |  |
| [Add Boost Search bottom navigation](#add-boost-search-bottom-navigation) | Shows Boost's native Home, Search, Subscriptions, Inbox and Profile menu in Search and Go To. |  |
| [Anchor Boost Snackbars above bottom navigation](#anchor-boost-snackbars-above-bottom-navigation) | Shows Boost Snackbars above the visible bottom navigation instead of covering it. |  |
| [Animate media in Boost gallery previews](#animate-media-in-boost-gallery-previews) | Plays GIFs and videos in Reddit gallery previews, following Boost's data saving settings. Tapping still opens the full-screen viewer. |  |
| [Auto-load next page after filtered results](#auto-load-next-page-after-filtered-results) | Loads the next page automatically when content filters hide every post on a page, instead of leaving the feed stuck behind a "Results were filtered" Snackbar. |  |
| [Automatically undelete Imgur images](#automatically-undelete-imgur-images) | Loads deleted Imgur images from the Wayback Machine. |  |
| [Automatically undelete Reddit content](#automatically-undelete-reddit-content) | Restores deleted and removed posts and comments from Arctic Shift, and their media from the Wayback Machine. |  |
| [Boost Morphe settings](#boost-morphe-settings) | Adds a Morphe section and a reorganized settings screen with search. The classic settings stay available. |  |
| [Boost search default discovery](#boost-search-default-discovery) | Opens Search right away on a list of active subreddits. |  |
| [Custom Synccit URL](#custom-synccit-url) | Allows Boost to use a custom self-hosted Synccit API endpoint. | • Synccit API URL |
| [Disable ads](#disable-ads) | Removes ads from feeds and comments and keeps the ad SDKs from starting. |  |
| [Disable Boost Crashlytics startup network calls](#disable-boost-crashlytics-startup-network-calls) | Stops Boost's crash reporting (Crashlytics) from connecting at startup. Other Firebase services keep working. |  |
| [Fix /r/all](#fix-r-all) | Loads /r/all through Reddit's .json endpoint so the feed shows posts. |  |
| [Fix /s/ links](#fix-s-links) | Opens Reddit share links (/s/) by resolving them to the post they point to. |  |
| [Fix Boost code block rendering](#fix-boost-code-block-rendering) | Shows Reddit code blocks correctly and fixes links whose URL contains parentheses. |  |
| [Fix Boost comments Lemmy-style toolbar UI](#fix-boost-comments-lemmy-style-toolbar-ui) | Removes the duplicate title above comments, keeping Boost's theme, toolbar title and sort label. |  |
| [Fix Boost FAB nested scroll](#fix-boost-fab-nested-scroll) | Hides and shows the floating action button in step with scrolling, also on pages with a collapsing header. |  |
| [Fix Boost image widget click target](#fix-boost-image-widget-click-target) | Makes the image widget open the post it shows instead of an older one. |  |
| [Fix Boost native image upload](#fix-boost-native-image-upload) | Uploads image posts and galleries to Reddit, and images in comments or text posts to Imgur, or ImgBB if you choose it. GIF and video uploads are unchanged. |  |
| [Fix Boost navigation bar overlap](#fix-boost-navigation-bar-overlap) | Keeps Boost's bottom controls and drawer clear of the system navigation bar on Android 15 and newer. |  |
| [Fix Boost target SDK 35 compatibility](#fix-boost-target-sdk-35-compatibility) | Makes Boost target Android 15 (SDK 35) and fixes billing setup on newer Android versions. |  |
| [Fix Boost video rewind back buffer](#fix-boost-video-rewind-back-buffer) | Keeps the last 30 seconds of a video, so rewinding plays right away instead of reloading. |  |
| [Fix Boost YouTube playback fallback](#fix-boost-youtube-playback-fallback) | Opens the original YouTube link externally when Boost's legacy embedded YouTube player is unavailable. |  |
| [Fix download completed notification visibility](#fix-download-completed-notification-visibility) | Shows a visible notification when a download finishes, while progress notifications stay quiet. |  |
| [Fix Hide crash](#fix-hide-crash) | Fixes crashes and jumps in the feed when hiding read posts. |  |
| [Fix missing audio in video downloads](#fix-missing-audio-in-video-downloads) | Fixes audio missing in videos downloaded from v.redd.it. |  |
| [Fix Random subreddit](#fix-random-subreddit) | Fixes the Random button by picking an active, non-NSFW subreddit through Reddit search. |  |
| [Fix Redgifs API](#fix-redgifs-api) | Fetches a fresh Redgifs API token so Redgifs videos load. |  |
| [Fix slow Giphy loading](#fix-slow-giphy-loading) | Loads Giphy posts faster by playing the MP4 directly instead of waiting for the Giphy API. |  |
| [Hook exception handler](#hook-exception-handler) | Catches Boost's crashes for debugging. Only enable this for development. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Open Reddit image links in Boost](#open-reddit-image-links-in-boost) | Opens i.redd.it image links in Boost's image viewer instead of a web page. |  |
| [Prefer high refresh rate](#prefer-high-refresh-rate) | Requests a high display refresh rate for Boost on adaptive-refresh devices. |  |
| [Readable Boost flair labels](#readable-boost-flair-labels) | Uses black or white flair text when Reddit's flair text color is unreadable on its background. |  |
| [Remove obsolete profile tabs](#remove-obsolete-profile-tabs) | Removes the legacy Gilded and Friends tabs from Boost profiles. |  |
| [Restore Boost sidebar Trending today](#restore-boost-sidebar-trending-today) | Restores the sidebar's Trending today section, falling back to hot posts, and fixes its community limit setting. |  |
| [Restore GIF URL insertion](#restore-gif-url-insertion) | Adds an option to the image menu to insert a GIF from its URL. |  |
| [Show end of feed](#show-end-of-feed) | Shows an "End of feed" row with a Refresh button below the last post when a feed has no more posts, instead of silently stopping. Home sorted by Best ends after a few hundred posts. |  |
| [Show inline Giphy previews in comments](#show-inline-giphy-previews-in-comments) | Adds inline media previews below Boost comment text for Giphy links and direct static image URLs. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Stabilize Boost subreddit bottom navigation surface](#stabilize-boost-subreddit-bottom-navigation-surface) | Gives the bottom navigation in subreddits proper contrast and room for gesture navigation. |  |
| [Standardize Boost bottom navigation](#standardize-boost-bottom-navigation) | Makes the bottom navigation look and behave the same on every screen. |  |
| [Standardize Boost bottom-navigation FAB clearance](#standardize-boost-bottom-navigation-fab-clearance) | Keeps the floating action buttons on Home, subreddits, Random, Inbox and Profile clear of the bottom navigation. |  |
| [Standardize Boost Home bottom navigation](#standardize-boost-home-bottom-navigation) | Shows the same five-tab bottom navigation on Home, with Home highlighted. |  |
| [Standardize Boost Inbox and Profile bottom navigation](#standardize-boost-inbox-and-profile-bottom-navigation) | Shows the same five-tab bottom navigation in Inbox and Profile. |  |
| [Support keyboard GIF insertion](#support-keyboard-gif-insertion) | Lets you insert GIFs and images from your keyboard while writing, when the keyboard provides a public link. |  |
| [Theme Boost settings icons](#theme-boost-settings-icons) | Colors the settings icons to match the active theme. |  |
| [Tune Boost image dismissal](#tune-boost-image-dismissal) | Lets you close the image viewer with a quick flick, without changing GIF, video or other gestures. |  |

</details>

<details>
<summary><strong>Sync for Reddit</strong> • 9 patches</summary>



**Supported versions:**

| v23.06.30-13:39 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) | Removes ads. |  |
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Hides the startup prompt that asks you to sign up for Sync for Lemmy. |  |
| [Fix /s/ links](#fix-s-links) | Opens Reddit share links (/s/) by resolving them to the post they point to. |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix Redgifs API](#fix-redgifs-api) | Fetches a fresh Redgifs API token so Redgifs videos load. |  |
| [Fix video downloads](#fix-video-downloads) | Fixes video downloads that saved only the audio track. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Opens user profiles through /user/ instead of the deprecated /u/ endpoint. |  |

</details>

<details>
<summary><strong>Sync for Reddit Pro</strong> • 8 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Hides the startup prompt that asks you to sign up for Sync for Lemmy. |  |
| [Fix /s/ links](#fix-s-links) | Opens Reddit share links (/s/) by resolving them to the post they point to. |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix Redgifs API](#fix-redgifs-api) | Fetches a fresh Redgifs API token so Redgifs videos load. |  |
| [Fix video downloads](#fix-video-downloads) | Fixes video downloads that saved only the audio track. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Opens user profiles through /user/ instead of the deprecated /u/ endpoint. |  |

</details>

<details>
<summary><strong>Sync for Reddit Dev</strong> • 8 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable Sync for Lemmy bottom sheet](#disable-sync-for-lemmy-bottom-sheet) | Hides the startup prompt that asks you to sign up for Sync for Lemmy. |  |
| [Fix /s/ links](#fix-s-links) | Opens Reddit share links (/s/) by resolving them to the post they point to. |  |
| [Fix post thumbnails](#fix-post-thumbnails) | Fixes loading post thumbnails by correcting their URLs. |  |
| [Fix Redgifs API](#fix-redgifs-api) | Fetches a fresh Redgifs API token so Redgifs videos load. |  |
| [Fix video downloads](#fix-video-downloads) | Fixes video downloads that saved only the audio track. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use /user/ endpoint](#use-user-endpoint) | Opens user profiles through /user/ instead of the deprecated /u/ endpoint. |  |

</details>

<details>
<summary><strong>Joey for Reddit</strong> • 3 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) | Removes ads by treating the user as ad-free. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Sync for Lemmy</strong> • 1 patch</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Disable ads](#disable-ads) | Removes ads. |  |

</details>

<details>
<summary><strong>rif is fun</strong> • 4 patches</summary>



**Supported versions:**

| 5.6.22 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fake reddit premium](#fake-reddit-premium) | Allows using pro features without ads. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use public imgur API](#use-public-imgur-api) | Fixes Imgur albums not loading. |  |

</details>

<details>
<summary><strong>Relay for Reddit</strong> • 3 patches</summary>



**Supported versions:**

| 10.2.40 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix /s/ links](#fix-s-links) | Opens Reddit share links (/s/) by resolving them to the post they point to. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Relay for Reddit Pro</strong> • 3 patches</summary>



**Supported versions:**

| 10.2.40 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix /s/ links](#fix-s-links) | Opens Reddit share links (/s/) by resolving them to the post they point to. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>BaconReader</strong> • 3 patches</summary>



**Supported versions:**

| 6.1.4 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix Redgifs API](#fix-redgifs-api) | Fetches a fresh Redgifs API token so Redgifs videos load. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>BaconReader Premium</strong> • 3 patches</summary>



**Supported versions:**

| 6.1.4 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Fix Redgifs API](#fix-redgifs-api) | Fetches a fresh Redgifs API token so Redgifs videos load. |  |
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Infinity for Reddit+</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Infinity for Reddit (Patreon)</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Joey for Reddit Pro</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>Joey for Reddit Dev</strong> • 2 patches</summary>



**Supported versions:**

|  |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |

</details>

<details>
<summary><strong>rif is fun golden platinum</strong> • 3 patches</summary>



**Supported versions:**

| 5.6.22 |
| :---: |

| Patch | Description | Options |
|---|---|---|
| [Modify login WebView](#modify-login-webview) | Adjusts the Reddit login page to prevent sign-in problems. |  |
| [Spoof client](#spoof-client) | Restores functionality of the app by using custom client ID. | • OAuth client ID<br>• Redirect URI<br>• User agent |
| [Use public imgur API](#use-public-imgur-api) | Fixes Imgur albums not loading. |  |

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

</details>

<details>
<summary>Legacy: current release and tested app versions</summary>

## Current release

| Field | Value |
|---|---|
| Version | `1.4.123` |
| Release tag | `morphe-patches-123` |
| Asset | `patches-1.4.123.mpp` |
| SHA256 | `cee4fb7f7a084260c4a9a8a63b1decdfd4f6358b75d54f7d8990afba6e886eec` |
| Manager JSON | `https://raw.githubusercontent.com/brealorg/breal-morphe-patches/main/patches-bundle.json` |
| Download URL | `https://github.com/brealorg/breal-morphe-patches/releases/download/morphe-patches-123/patches-1.4.123.mpp` |

SHA256: `cee4fb7f7a084260c4a9a8a63b1decdfd4f6358b75d54f7d8990afba6e886eec`

## Tested app versions

| App | Package | Version |
|---|---|---|
| Boost for Reddit | `com.rubenmayayo.reddit` | `1.12.12` / versionCode `210011212` |
| Imgur | `com.imgur.mobile` | `7.33.0.0` / versionCode `73300` |

Compatibility with other app versions is not guaranteed.

</details>

<details>
<summary>Legacy: release verification and development notes</summary>

## Verification

Release `1.4.123` is prepared and locally verified with:

- Release tag `morphe-patches-123`.
- Local built MPP SHA256 matching README.
`cee4fb7f7a084260c4a9a8a63b1decdfd4f6358b75d54f7d8990afba6e886eec`
- `patches-bundle.json` returning version `1.4.123`.
- `patches-bundle.json` pointing to the `morphe-patches-123` asset.
- Expected release asset:
`patches-1.4.123.mpp`
- `cee4fb7f7a084260c4a9a8a63b1decdfd4f6358b75d54f7d8990afba6e886eec  patches-1.4.123.mpp`

## Development notes

This repository contains experimental and release-candidate work in local/work branches. Only the published release JSON and GitHub release asset should be treated as the current user-facing bundle.

### Deferred work

16K / WL04R builder and runtime work is intentionally **not included** in the current release.

That work is preserved separately for a later scope.

Release process notes:

- [Release validation policy](docs/release-validation-policy.md)
- [Branch policy](docs/branch-policy.md)
- [Final snapshot template](docs/final-snapshot-template.md)
- [Supported app catalog](docs/supported-app-catalog.md)

</details>

<details>
<summary>Attribution, license and disclaimer</summary>

## Attribution and license

This repository is derived from Patcheddit/Morphe patch work.

Author metadata in the patch bundle currently credits:

```text
wchill + brealorg
```

Additional license conditions under GPL section 7 apply. See `LICENSE` for attribution and project name restrictions.

## Disclaimer

This is an unofficial community patch bundle. It is provided as-is and is intended for tested app versions only. Use at your own risk.

</details>
