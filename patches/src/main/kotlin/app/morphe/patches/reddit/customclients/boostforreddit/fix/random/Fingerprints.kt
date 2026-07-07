package app.morphe.patches.reddit.customclients.boostforreddit.fix.random

import app.morphe.patcher.Fingerprint

internal val subredditActivityIntentSubredditExtraFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/submissions/subreddit/SubredditActivity;",
    strings = listOf("widget_id", "subreddit")
)
