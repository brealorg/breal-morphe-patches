package app.morphe.patches.reddit.customclients.boostforreddit.fix.searchnav

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode

private const val SYSTEM_SURFACE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSearchBottomNavigation;"

@Suppress("unused")
val boostBottomNavigationSystemSurfacePatch = bytecodePatch(
    name = "Standardize Boost bottom navigation",
    description = "Applies one destination, listener, selection, tint, and system-surface contract to Boost's Material bottom navigation.",
    default = true,
) {
    compatibleWith(*BoostCompatible)
    dependsOn(sharedExtensionPatch)

    execute {
        drawerMaterialBottomNavigationFingerprint.method.apply {
            val returnIndices =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    .map { (index, _) -> index }

            require(returnIndices.isNotEmpty()) {
                "DrawerActivity.g3 has no RETURN_VOID instruction"
            }

            returnIndices.asReversed().forEach { returnIndex ->
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {p0, p1}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->standardizeMaterialNavigation(Landroid/app/Activity;I)V
                    """.trimIndent(),
                )
            }
        }

        drawerBottomNavigationVisibilityFingerprint.method.apply {
            val returns =
                implementation!!.instructions
                    .withIndex()
                    .filter { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    .map { (index, _) -> index }

            require(returns.size == 1) {
                "DrawerActivity.p3(boolean) expected one RETURN_VOID, found ${returns.size}"
            }

            addInstructions(
                returns.single(),
                """
                    invoke-static {p0, p1}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->enforceMaterialOnlyRuntimeVisibility(Landroid/app/Activity;Z)V
                """.trimIndent(),
            )
        }

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
                        invoke-static {p0}, $SYSTEM_SURFACE_EXTENSION_DESCRIPTOR->refreshMaterialNavigationPreference(Landroid/app/Activity;)V
                    """.trimIndent(),
                )
            }
        }

    }
}
