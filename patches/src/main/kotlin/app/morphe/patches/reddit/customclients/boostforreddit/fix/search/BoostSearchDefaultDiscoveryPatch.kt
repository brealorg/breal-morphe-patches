package app.morphe.patches.reddit.customclients.boostforreddit.fix.search

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val boostSearchDefaultDiscoveryPatch = bytecodePatch(
    name = "Boost search default discovery",
    description = "Starts Boost search on an instant cached active-subreddit landing with Reddit-native labels.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        /*
         * Normal Boost search opens SearchGenericActivity.
         * Q1() builds the empty-state "Go to..." section and already adds Random.
         * v5m keeps Random, displays cached active subreddits instantly, refreshes source-specific popular/HOT posts, and renames the Reddit search filter from Community to Subreddit.
         */
        searchGenericActivityDefaultGoToFingerprint.method.apply {
            /*
             * Insert at Q1 method entry. The previous tail insertion was still
             * branch-skippable because Boost's cond label stayed attached to
             * return-void. Entry injection proves whether Q1 is the active path.
             */
            addInstructions(
                0,
                """
                    iget-object v0, p0, Lcom/rubenmayayo/reddit/ui/search/SearchAbstractActivity;->b:Ljava/util/ArrayList;
                    invoke-static {p0, v0}, Lapp/morphe/extension/boostforreddit/search/SearchExploreRows;->appendOrRefresh(Landroid/app/Activity;Ljava/util/ArrayList;)V
                """.trimIndent(),
            )
        }

        /*
         * Explore/default state should not open with the keyboard covering half
         * the landing content. Tapping the search field still focuses normally.
         */
        searchGenericActivityOnCreateFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionReversedOrThrow {
                opcode == Opcode.RETURN_VOID
            }

            addInstructions(
                returnIndex,
                """
                    invoke-virtual {p0}, Landroid/app/Activity;->getWindow()Landroid/view/Window;
                    move-result-object v0
                    const/4 v1, 0x3
                    invoke-virtual {v0, v1}, Landroid/view/Window;->setSoftInputMode(I)V
                    iget-object v0, p0, Lcom/rubenmayayo/reddit/ui/search/SearchAbstractActivity;->searchEditText:Landroid/widget/EditText;
                    invoke-virtual {v0}, Landroid/view/View;->clearFocus()V
                """.trimIndent(),
            )
        }

    }
}
