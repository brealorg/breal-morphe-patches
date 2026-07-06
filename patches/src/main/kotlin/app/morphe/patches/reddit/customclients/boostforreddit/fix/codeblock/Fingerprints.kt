/*
 * Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.codeblock

import app.morphe.patcher.Fingerprint

internal val boostHtmlSanitizerFingerprint = Fingerprint(
    definingClass = "Lhe/h0;",
    name = "d",
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("<code>", "<tt>", "<pre>", "</pre>")
)

internal val boostTableTextViewSetTextHtmlFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/TableTextView;",
    name = "setTextHtml",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;")
)

internal val boostLinkTextViewSetTextHtmlParsedFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/LinkTextView;",
    name = "setTextHtmlParsed",
    parameters = listOf("Ljava/lang/String;"),
    returnType = "V",
)

internal val boostSubmissionViewHolderSelfPreviewHtmlFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/adapters/SubmissionViewHolder;",
    name = "H0",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)
