package app.morphe.patches.reddit.customclients.boostforreddit.fix.synccit

import app.morphe.patcher.Fingerprint

internal const val LEGACY_SYNCCIT_API_URL =
    "https://api.synccit.com/api.php"

private const val SYNCCIT_CLIENT_CLASS = "Lzd/a;"

internal val synccitUpdateFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Lcom/rubenmayayo/reddit/models/synccit/LinkModel;",
    ),
    strings = listOf(LEGACY_SYNCCIT_API_URL),
    custom = { method, classDef ->
        classDef.type == SYNCCIT_CLIENT_CLASS &&
            method.name == "d"
    },
)

internal val synccitAddFingerprint = Fingerprint(
    returnType =
        "Lcom/rubenmayayo/reddit/models/synccit/AddModel;",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    strings = listOf(LEGACY_SYNCCIT_API_URL),
    custom = { method, classDef ->
        classDef.type == SYNCCIT_CLIENT_CLASS &&
            method.name == "a"
    },
)

internal val synccitAuthenticateFingerprint = Fingerprint(
    returnType =
        "Lcom/rubenmayayo/reddit/models/synccit/AddModel;",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    strings = listOf(LEGACY_SYNCCIT_API_URL),
    custom = { method, classDef ->
        classDef.type == SYNCCIT_CLIENT_CLASS &&
            method.name == "b"
    },
)

internal val synccitReadFingerprint = Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf(
        "Ljava/util/List;",
    ),
    strings = listOf(LEGACY_SYNCCIT_API_URL),
    custom = { method, classDef ->
        classDef.type == SYNCCIT_CLIENT_CLASS &&
            method.name == "c"
    },
)
