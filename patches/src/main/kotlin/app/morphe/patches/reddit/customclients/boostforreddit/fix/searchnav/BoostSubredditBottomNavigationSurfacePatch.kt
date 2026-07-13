package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode

private const val SUBREDDIT_NAV_EXTENSION =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation;"

@Suppress("unused")
val boostSubredditBottomNavigationSurfacePatch = bytecodePatch(
    name = "Stabilize Boost subreddit bottom navigation surface",
    description = "Applies theme contrast and gesture-inset surface handling to SubredditActivity while preserving native selection and routes.",
    default = true,
) {
    compatibleWith(*BoostCompatible)
    dependsOn(sharedExtensionPatch)

    execute {
        subredditActivityOnCreateFingerprint.method.apply {
            val returnIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    .map { (index, _) -> index }

            require(returnIndices.isNotEmpty()) {
                "SubredditActivity.onCreate has no RETURN_VOID"
            }

            returnIndices.asReversed().forEach { index ->
                addInstructions(
                    index,
                    """
                        invoke-static {p0}, $SUBREDDIT_NAV_EXTENSION->standardizeSubreddit(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
