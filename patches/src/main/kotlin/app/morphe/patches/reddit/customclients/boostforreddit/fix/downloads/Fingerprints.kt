/*
 * Copyright 2026 wchill.
 * Modifications Copyright 2026 brealorg.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.Fingerprint

internal val downloadAudioFingerprint = Fingerprint(
    strings = listOf("/DASH_audio.mp4", "/audio")
)

internal val notificationChannelsHelperFingerprint = Fingerprint(
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf(
        "20_inbox_channel",
        "50_modmail_channel",
        "80_downloads_channel"
    )
)

internal val downloadCompletedNotificationFingerprint = Fingerprint(
    parameters = listOf(
        "Landroid/content/Context;",
        "Landroid/net/Uri;",
        "Z",
        "I",
        "Ljava/lang/String;",
        "J",
        "Ljava/lang/String;"
    ),
    strings = listOf("80_downloads_channel")
)
