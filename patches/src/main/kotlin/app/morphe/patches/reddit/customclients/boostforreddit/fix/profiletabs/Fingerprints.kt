package app.morphe.patches.reddit.customclients.boostforreddit.fix.profiletabs

import app.morphe.patcher.Fingerprint

internal val userActivityBuildTabsFingerprint = Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/profile/UserActivity;" &&
            method.name == "l4"
    },
)
