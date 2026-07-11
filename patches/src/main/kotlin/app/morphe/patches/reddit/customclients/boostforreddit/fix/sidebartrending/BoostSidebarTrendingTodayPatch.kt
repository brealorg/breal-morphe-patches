package app.morphe.patches.reddit.customclients.boostforreddit.fix.sidebartrending

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val boostSidebarTrendingTodayPatch = bytecodePatch(
    name = "Restore Boost sidebar Trending today",
    description = "Uses native trending data when renderable, otherwise supplies HOT post rows and fixes the global community-limit control.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        /*
         * TrendingTodayFragment -> TrendingTodayViewModel -> od.a -> xb.l.x0().
         * Keep Boost's background AsyncTask, but replace its body with a
         * native-first loader that falls back only when the legacy endpoint
         * returns no rows containing submissions.
         */
        trendingTodayAsyncLoaderFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/morphe/extension/boostforreddit/sidebar/SidebarTrendingPosts;->load()Ljava/util/List;
                move-result-object p1
                return-object p1
            """.trimIndent(),
        )

        /*
         * The sidebar is global on Home/All/Popular, where "Limit to Community"
         * has no valid context. Remove it there. When W1() resolves a concrete
         * subreddit, retain it as "Limit to r/<name>" with the value preloaded.
         */
        trendingTodayFragmentOnViewCreatedFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static {p0, p1}, Lapp/morphe/extension/boostforreddit/sidebar/SidebarTrendingPosts;->configureSearchOptions(Ljava/lang/Object;Landroid/view/View;)V
                """.trimIndent(),
            )

            val returnIndex = indexOfFirstInstructionReversedOrThrow {
                opcode == Opcode.RETURN_VOID
            }
            addInstructions(
                returnIndex,
                """
                    invoke-static {p0}, Lapp/morphe/extension/boostforreddit/sidebar/SidebarTrendingPosts;->triggerLoad(Ljava/lang/Object;)V
                """.trimIndent(),
            )
        }

        /*
         * Fallback rows use the post title as the native trending query while
         * the adapter also renders SubmissionModel.A1(). Record row identity at
         * bind entry, before Boost can reuse p1, then hide the secondary title
         * only for Morphe-created rows at every control-flow return.
         */
        trendingTodayViewHolderBindFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static {p0, p1}, Lapp/morphe/extension/boostforreddit/sidebar/SidebarTrendingPosts;->recordBoundRow(Ljava/lang/Object;Ljava/lang/Object;)V
                """.trimIndent(),
            )

            val returnIndices = implementation!!.instructions
                .withIndex()
                .mapNotNull { (index, instruction) ->
                    if (instruction.opcode == Opcode.RETURN_VOID) index else null
                }

            check(returnIndices.isNotEmpty()) {
                "TrendingTodayViewHolder.e() has no RETURN_VOID"
            }

            returnIndices.asReversed().forEach { index ->
                addInstructionsAtControlFlowLabel(
                    index,
                    """
                        invoke-static {p0}, Lapp/morphe/extension/boostforreddit/sidebar/SidebarTrendingPosts;->applyBoundTextPolicy(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
