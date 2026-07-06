/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.upload

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val fixNativeImageUploadPatch = bytecodePatch(
    name = "Fix Boost native image upload",
    description = "Forces Boost's single-image submit flow to use Reddit's native image submission kind instead of creating external uploaded-media link posts.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        submitGallerySubmissionKindFingerprint.method.apply {
            val submitAsImageRemoteConfigCallIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() == "Lsb/a;->f0()Z"
            }

            val moveResultIndex = submitAsImageRemoteConfigCallIndex + 1
            val moveResultInstruction = implementation!!.instructions[moveResultIndex] as OneRegisterInstruction
            val moveResultRegister = moveResultInstruction.registerA

            replaceInstruction(
                moveResultIndex,
                "const/4 v$moveResultRegister, 0x1"
            )

            addInstructions(
                0,
                """
                    const-string v0, "MORPHE_ISSUE17_NATIVE_IMAGE_UPLOAD_V1"
                """
            )
        }
    }
}
