/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.feed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.all.misc.PatchCategory

private const val FEED_END_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostFeedEndFooter;"

private const val SUBMISSION_FRAGMENT =
    "Lcom/rubenmayayo/reddit/ui/fragments/SubmissionRecyclerViewFragment;"

private const val SUBMISSION_LIST =
    "$SUBMISSION_FRAGMENT->mRecyclerView:Lcom/rubenmayayo/reddit/ui/customviews/EmptyRecyclerView;"

// RedditJRAW.loadSubmissions(Paginator, boolean): loads one submissions page.
private val loadSubmissionsFingerprint = Fingerprint(
    definingClass = "Lxb/l;",
    name = "s0",
    returnType = "Ljava/util/ArrayList;",
    parameters = listOf("Lnet/dean/jraw/paginators/Paginator;", "Z"),
)

// SubmissionRecyclerViewFragment.appendSubmissions(ArrayList): adds a further page.
private val appendSubmissionsFingerprint = Fingerprint(
    definingClass = SUBMISSION_FRAGMENT,
    name = "W",
    returnType = "V",
    parameters = listOf("Ljava/util/ArrayList;"),
)

// SubmissionRecyclerViewFragment.setSubmissions(ArrayList): loads a feed from the top.
private val setSubmissionsFingerprint = Fingerprint(
    definingClass = SUBMISSION_FRAGMENT,
    name = "o0",
    returnType = "V",
    parameters = listOf("Ljava/util/ArrayList;"),
)

@Suppress("unused")
val feedEndFooterPatch = bytecodePatch(
    name = "Show end of feed",
    description =
        "Shows an \"End of feed\" row with a Refresh button below the last post when a feed has " +
            "no more posts, instead of silently stopping. Home sorted by Best ends after a few " +
            "hundred posts.",
    default = true,
) {
    category(PatchCategory.FEED)
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        loadSubmissionsFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $FEED_END_EXTENSION_DESCRIPTOR->onLoadPage(Lnet/dean/jraw/paginators/Paginator;)V",
        )

        appendSubmissionsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $SUBMISSION_LIST
                invoke-static {p0, v0, p1}, $FEED_END_EXTENSION_DESCRIPTOR->onAppendPage(Ljava/lang/Object;Landroid/view/View;Ljava/util/List;)V
            """,
        )

        setSubmissionsFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, $SUBMISSION_LIST
                invoke-static {v0}, $FEED_END_EXTENSION_DESCRIPTOR->onReplacePage(Landroid/view/View;)V
            """,
        )
    }
}
