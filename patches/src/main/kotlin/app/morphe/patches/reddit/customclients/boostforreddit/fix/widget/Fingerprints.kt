package app.morphe.patches.reddit.customclients.boostforreddit.fix.widget

import app.morphe.patcher.Fingerprint

internal val imageWidgetProviderUpdateWithSubmissionFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/widget/image/ImageWidgetProvider;",
    name = "b",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "I",
        "Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;",
        "Ljava/lang/String;",
    ),
)
