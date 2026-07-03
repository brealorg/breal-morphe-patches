/*
 * Copyright 2026 brealorg.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patcher.Fingerprint

private const val FORMATTING_BAR_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/customviews/FormattingBar;"

internal val formattingBarInsertMarkdownFingerprint = Fingerprint(
    definingClass = FORMATTING_BAR_DESCRIPTOR,
    name = "p",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;")
)

internal val formattingBarOpenLinkDialogWithUrlFingerprint = Fingerprint(
    definingClass = FORMATTING_BAR_DESCRIPTOR,
    name = "l",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;")
)

internal val formattingBarOpenLinkDialogFingerprint = Fingerprint(
    definingClass = FORMATTING_BAR_DESCRIPTOR,
    name = "k",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;")
)

internal val formattingBarSetEditTextFingerprint = Fingerprint(
    definingClass = FORMATTING_BAR_DESCRIPTOR,
    name = "setEditText",
    returnType = "V",
    parameters = listOf("Landroid/widget/EditText;")
)
