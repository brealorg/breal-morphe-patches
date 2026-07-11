package app.morphe.patches.reddit.customclients.boostforreddit.fix.sidebartrending

import app.morphe.patcher.Fingerprint

internal val trendingTodayAsyncLoaderFingerprint = Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("[Ljava/lang/Void;"),
    custom = { method, classDef ->
        classDef.type == "Lod/a;" && method.name == "a"
    },
)

internal val trendingTodayFragmentOnViewCreatedFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/rubenmayayo/reddit/ui/sidebar/today/TrendingTodayFragment;" &&
            method.name == "onViewCreated"
    },
)

internal val trendingTodayViewHolderBindFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Lcom/rubenmayayo/reddit/models/reddit/m;",
        "Lcom/bumptech/glide/k;",
    ),
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/rubenmayayo/reddit/ui/sidebar/today/TrendingTodayAdapter\$TrendingTodayViewHolder;" &&
            method.name == "e"
    },
)
