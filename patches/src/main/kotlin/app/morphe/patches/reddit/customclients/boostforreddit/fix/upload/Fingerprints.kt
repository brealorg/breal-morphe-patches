/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.upload

import app.morphe.patcher.Fingerprint

private const val BOOST_REMOTE_CONFIG_CLASS = "Lsb/a;"

internal val editorImagePreviewFormattingBarSetEditTextFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/FormattingBar;",
    name = "setEditText",
    returnType = "V",
    parameters = listOf("Landroid/widget/EditText;"),
)

internal val submitGallerySubmissionKindFingerprint = Fingerprint(
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/rubenmayayo/reddit/ui/submit/v2/SubmitGalleryFragment;" &&
            method.name == "R1"
    }
)

internal val nativeRedditUploaderFormatFingerprint = Fingerprint(
    definingClass = BOOST_REMOTE_CONFIG_CLASS,
    name = "w",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings = listOf("uploader_format"),
)

internal val nativeRedditUploaderSubmitFingerprint = Fingerprint(
    definingClass = BOOST_REMOTE_CONFIG_CLASS,
    name = "S",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings = listOf("uploader_submit"),
)

internal val nativeRedditUploaderSubmitMultipleFingerprint = Fingerprint(
    definingClass = BOOST_REMOTE_CONFIG_CLASS,
    name = "T",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings = listOf("uploader_submit_multiple"),
)

internal val nativeRedditSubmitAsImageKindFingerprint = Fingerprint(
    definingClass = BOOST_REMOTE_CONFIG_CLASS,
    name = "f0",
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("submit_reddit_as_image_kind"),
)

internal val mediaUploaderFactoryFingerprint = Fingerprint(
    definingClass = "Lde/c;",
    name = "d",
    returnType = "Lde/b;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf(
        "imgur_free",
        "imgur_paid",
        "vgy",
        "reddit",
    ),
)
