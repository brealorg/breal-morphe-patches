/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.giphy

import app.morphe.patcher.Fingerprint

internal val resolveGiphyApiFingerprint = Fingerprint(
    strings = listOf("dc6zaTOxFJmzC"),
    custom = { _, classDef -> classDef.sourceFile == "GifUrlExtractorCancelable.java" }
)

internal val commentViewHolderBindFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;",
    name = "o",
    returnType = "V",
    parameters = listOf(
        "Lcom/rubenmayayo/reddit/models/reddit/CommentModel;",
        "Z",
        "Z",
        "Z",
        "Lcom/bumptech/glide/k;"
    )
)

internal val commentViewHolderCollapseFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;",
    name = "w",
    returnType = "V",
    parameters = listOf("I")
)
