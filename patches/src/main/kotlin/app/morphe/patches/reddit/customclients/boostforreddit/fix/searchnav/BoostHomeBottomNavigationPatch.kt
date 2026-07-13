package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode

private const val HOME_NAV_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation;"

@Suppress("unused")
val boostHomeBottomNavigationPatch = bytecodePatch(
    name = "Standardize Boost Home bottom navigation",
    description = "Applies the canonical five-destination Material navigation and native Home tint when MainActivity resumes.",
    default = true,
) {
    compatibleWith(*BoostCompatible)
    dependsOn(sharedExtensionPatch)

    execute {
        mainActivityOnResumeFingerprint.method.apply {
            val returnIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    .map { (index, _) -> index }

            require(returnIndices.isNotEmpty()) {
                "MainActivity.onResume has no RETURN_VOID instruction"
            }

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {p0}, $HOME_NAV_EXTENSION_DESCRIPTOR->standardizeHome(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
