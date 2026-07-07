/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.hide

import app.morphe.patcher.Fingerprint

internal val subredditBaseHideToggleFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf(
        "I",
        "Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;",
        "Z",
    ),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/submissions/subreddit/SubredditBaseActivity;" &&
            method.name == "O"
    },
)

private val hideFragmentC0ClassTypes = listOf(
    "Lcom/rubenmayayo/reddit/ui/fragments/c;",
    "Lcom/rubenmayayo/reddit/ui/fragments/e;",
    "Lcom/rubenmayayo/reddit/ui/fragments/g;",
    "Lcom/rubenmayayo/reddit/ui/fragments/h;",
    "Lcom/rubenmayayo/reddit/ui/fragments/j;",
)

internal val hideFragmentC0Fingerprints = hideFragmentC0ClassTypes.map { classType ->
    Fingerprint(
        returnType = "V",
        parameters = listOf(
            "I",
            "Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;",
        ),
        custom = { method, classDef ->
            classDef.type == classType && method.name == "C0"
        },
    )
}

internal val feedActionS1InvalidIndexFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("I"),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/fragments/j;" &&
            method.name == "s1"
    },
)

