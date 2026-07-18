package app.morphe.patches.reddit.customclients.boostforreddit.fix.profiletabs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/RemoveObsoleteProfileTabsPatch;"

@Suppress("unused")
val removeObsoleteProfileTabsPatch = bytecodePatch(
    name = "Remove obsolete profile tabs",
    description = "Removes the legacy Gilded and Friends tabs from Boost profiles.",
    default = true,
) {
    compatibleWith(*BoostCompatible)
    dependsOn(sharedExtensionPatch)

    execute {
        userActivityBuildTabsFingerprint.method.apply {
            val routeAssignmentIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.IPUT_OBJECT &&
                            (instruction as? ReferenceInstruction)
                                ?.reference
                                ?.toString() ==
                            "Lcom/rubenmayayo/reddit/ui/profile/UserActivity;->w:[Ljava/lang/String;"
                    }
                    .map { (index, _) -> index }

            require(routeAssignmentIndices.size == 2) {
                "UserActivity.l4 expected two route-array assignments, found ${routeAssignmentIndices.size}"
            }

            routeAssignmentIndices.asReversed().forEach { assignmentIndex ->
                addInstructions(
                    assignmentIndex + 1,
                    """
                        invoke-static/range {p0 .. p0}, $EXTENSION_DESCRIPTOR->removeObsoleteTabs(Ljava/lang/Object;)V
                    """.trimIndent(),
                )
            }
        }
    }
}
