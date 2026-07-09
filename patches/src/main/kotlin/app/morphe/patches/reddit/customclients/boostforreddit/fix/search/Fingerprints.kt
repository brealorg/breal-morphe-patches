package app.morphe.patches.reddit.customclients.boostforreddit.fix.search

import app.morphe.patcher.Fingerprint

internal val searchSubredditActivityOnCreateFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/subreddits/SearchSubredditActivity;" &&
            method.name == "onCreate"
    },
)

internal val searchGenericActivityOnCreateFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/search/SearchGenericActivity;" &&
            method.name == "onCreate"
    },
)

internal val searchGenericActivityDefaultGoToFingerprint = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("random", "randnsfw"),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/search/SearchGenericActivity;" &&
            method.name == "Q1"
    },
)
