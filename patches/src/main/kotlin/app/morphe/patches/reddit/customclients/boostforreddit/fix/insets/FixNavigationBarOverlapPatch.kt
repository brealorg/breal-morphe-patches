/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.insets

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.all.misc.PatchCategory

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSystemBarInsetsFix;"

@Suppress("unused")
val fixNavigationBarOverlapPatch = bytecodePatch(
    name = "Fix Boost navigation bar overlap",
    description = "Keeps Boost's bottom controls and drawer clear of the system navigation bar on Android 15 and newer.",
    default = true
) {
    category(PatchCategory.NAVIGATION)
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        myApplicationOnCreateFingerprint.method.apply {
            val superOnCreateIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_SUPER &&
                    getReference<MethodReference>()?.name == "onCreate"
            }

            addInstructions(
                superOnCreateIndex + 1,
                """
                    invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->install(Landroid/app/Application;)V
                    """
            )
        }

        arrayOf(
            mediaImageActivityOnCreateFingerprint,
            mediaVideoActivityOnCreateFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                val setContentViewIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_VIRTUAL &&
                        getReference<MethodReference>()?.name == "setContentView"
                }

                addInstructions(
                    setContentViewIndex + 1,
                    """
                        invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->applyMediaInsets(Landroid/app/Activity;)V
                        """
                )
            }
        }

        mainActivityOnResumeFingerprint.method.apply {
            val returnIndex = implementation!!.instructions
                .withIndex()
                .lastOrNull { (_, instruction) -> instruction.opcode == Opcode.RETURN_VOID }
                ?.index
                ?: error("Could not find return-void in MainActivity.onResume")

            addInstructions(
                returnIndex,
                """
                    invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->applyMainInsets(Landroid/app/Activity;)V
                    """
            )
        }
    }
}
