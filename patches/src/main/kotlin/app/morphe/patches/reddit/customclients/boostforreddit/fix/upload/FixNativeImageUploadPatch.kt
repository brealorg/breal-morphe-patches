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
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsSkeletonPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val REDDIT_UPLOAD_PROVIDER = "reddit"

private const val UPLOAD_SETTINGS_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/upload/ExternalImageUploadSettings;"

private const val UPLOAD_FACTORY_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/upload/ExternalImageUploadFactory;"

private const val NATIVE_REDDIT_UPLOAD_MARKER =
    "MORPHE_BOOST_NATIVE_REDDIT_IMAGE_UPLOAD_ISSUE66_V2"

private const val EXTERNAL_IMAGE_HOST_MARKER =
    "MORPHE_BOOST_EXTERNAL_IMAGE_HOST_ISSUE66_V1"

private const val IMAGE_HOST_POLICY_MARKER =
    "MORPHE_BOOST_IMAGE_HOST_POLICY_ISSUE66_V3"

@Suppress("unused")
val fixNativeImageUploadPatch = bytecodePatch(
    name = "Fix Boost native image upload",
    description =
        "Keeps Boost image posts and galleries on Reddit's native uploader " +
            "and routes images inserted into comments or text posts through " +
            "Imgur by default, with ImgBB as a manually selected alternative. " +
            "GIF and video upload behavior is unchanged.",
    default = false
) {
    dependsOn(sharedExtensionPatch, boostMorpheSettingsSkeletonPatch)
    compatibleWith(*BoostCompatible)

    execute {
        check(NATIVE_REDDIT_UPLOAD_MARKER.endsWith("ISSUE66_V2"))
        check(EXTERNAL_IMAGE_HOST_MARKER.endsWith("ISSUE66_V1"))
        check(IMAGE_HOST_POLICY_MARKER.endsWith("ISSUE66_V3"))

        nativeRedditUploaderFormatFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $UPLOAD_SETTINGS_DESCRIPTOR->getEditorProvider()Ljava/lang/String;
                move-result-object v0
                return-object v0
            """.trimIndent(),
        )

        arrayOf(
            nativeRedditUploaderSubmitFingerprint,
            nativeRedditUploaderSubmitMultipleFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.addInstructions(
                0,
                """
                    const-string v0, "$REDDIT_UPLOAD_PROVIDER"
                    return-object v0
                """.trimIndent(),
            )
        }

        mediaUploaderFactoryFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $UPLOAD_FACTORY_DESCRIPTOR->create(Ljava/lang/String;)Lde/b;
                move-result-object v0
                return-object v0
            """.trimIndent(),
        )

        nativeRedditSubmitAsImageKindFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent(),
        )

        editorImagePreviewFormattingBarSetEditTextFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, Lapp/morphe/extension/boostforreddit/upload/EditorImagePreview;->bind(Landroid/widget/EditText;)V
            """.trimIndent(),
        )

        submitGallerySubmissionKindFingerprint.method.apply {
            val submitAsImageRemoteConfigCallIndex =
                indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_STATIC &&
                        getReference<MethodReference>()?.toString() ==
                        "Lsb/a;->f0()Z"
                }

            val moveResultIndex = submitAsImageRemoteConfigCallIndex + 1
            val moveResultInstruction =
                implementation!!.instructions[moveResultIndex] as
                    OneRegisterInstruction
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
                    const-string v0, "$EXTERNAL_IMAGE_HOST_MARKER"
                    const-string v0, "$IMAGE_HOST_POLICY_MARKER"
                """.trimIndent(),
            )
        }
    }
}
