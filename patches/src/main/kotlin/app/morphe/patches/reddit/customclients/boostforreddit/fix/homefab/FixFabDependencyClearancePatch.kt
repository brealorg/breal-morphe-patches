/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode

private const val FAB_CLEARANCE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostFabClearance;"

/**
 * Boost's FAB menu and mini FAB behaviors write translationY from only the
 * dependency that changed, so dismissing a Snackbar resets the FAB behind the
 * visible bottom navigation (issue #179). Route both callbacks through one
 * extension that clears every visible dependency.
 *
 * Applied through [fixBoostHomeFloatingActionMenuOverlapPatch], which removes
 * the static FAB margin and therefore relies on this translation.
 */
internal val fixFabDependencyClearancePatch = bytecodePatch {
    dependsOn(sharedExtensionPatch)

    execute {
        listOf(
            fabMenuDependentViewChangedFingerprint,
            fabMiniDependentViewChangedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    invoke-static {p1, p2}, $FAB_CLEARANCE_EXTENSION_DESCRIPTOR->onDependentViewChanged(Landroid/view/ViewGroup;Landroid/view/View;)V
                    const/4 p0, 0x1
                    return p0
                """,
            )
        }

        listOf(
            fabMenuDependentViewRemovedFingerprint,
            fabMiniDependentViewRemovedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                // Keep the native super call, then replace the translation reset.
                val superIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_SUPER
                }
                addInstructions(
                    superIndex + 1,
                    """
                        invoke-static {p1, p2, p3}, $FAB_CLEARANCE_EXTENSION_DESCRIPTOR->onDependentViewRemoved(Landroid/view/ViewGroup;Landroid/view/View;Landroid/view/View;)V
                        return-void
                    """,
                )
            }
        }
    }
}
