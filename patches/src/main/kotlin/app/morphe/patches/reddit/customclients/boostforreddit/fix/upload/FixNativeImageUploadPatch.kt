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

private const val REDDIT_UPLOAD_PROVIDER = "reddit"

private const val NATIVE_REDDIT_UPLOAD_MARKER =
    "MORPHE_BOOST_NATIVE_REDDIT_IMAGE_UPLOAD_ISSUE66_V2"

@Suppress("unused")
val fixNativeImageUploadPatch = bytecodePatch(
    name = "Fix Boost native image upload",
    description =
        "Routes Boost editor images, image posts, and galleries through " +
            "Reddit's bundled native uploader instead of Imgur while " +
            "retaining the original gallery submission-kind safeguard. " +
            "GIF and video upload behavior is unchanged.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        check(NATIVE_REDDIT_UPLOAD_MARKER.endsWith("ISSUE66_V2"))

        arrayOf(
            nativeRedditUploaderFormatFingerprint,
            nativeRedditUploaderSubmitFingerprint,
            nativeRedditUploaderSubmitMultipleFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    const-string v0, "$REDDIT_UPLOAD_PROVIDER"
                    return-object v0
                """,
            )
        }

        nativeRedditSubmitAsImageKindFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )

        submitGallerySubmissionKindFingerprint.method.apply {
            val submitAsImageRemoteConfigCallIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() == "Lsb/a;->f0()Z"
            }

            val moveResultIndex = submitAsImageRemoteConfigCallIndex + 1
            val moveResultInstruction =
                implementation!!.instructions[moveResultIndex] as OneRegisterInstruction
            val moveResultRegister = moveResultInstruction.registerA

            replaceInstruction(
                moveResultIndex,
                "const/4 v$moveResultRegister, 0x1"
            )

            addInstructions(
                0,
                """
                    const-string v0, "MORPHE_ISSUE17_NATIVE_IMAGE_UPLOAD_V1"
                    const-string v0, "$NATIVE_REDDIT_UPLOAD_MARKER"
                """,
            )
        }
    }
}
