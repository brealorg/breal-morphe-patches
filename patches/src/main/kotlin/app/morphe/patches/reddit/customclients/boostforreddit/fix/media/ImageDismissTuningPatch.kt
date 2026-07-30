/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.media

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/media/ImageDismissTuning;"

private const val CONTRACT_MARKER =
    "MORPHE_BOOST_IMAGE_DISMISS_TUNING_ISSUE96_V4_1"

private val mediaImageActivityOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/MediaImageActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private val legacyImageActivityOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ImageActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private val legacyImageActivity2OnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/activities/ImageActivity2;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

private val imageModelFragmentOnCreateViewFingerprint = Fingerprint(
    definingClass =
        "Lcom/rubenmayayo/reddit/ui/fragments/imagemodel/ImageModelFragment;",
    name = "onCreateView",
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;",
    ),
)

@Suppress("unused")
val imageDismissTuningPatch = bytecodePatch(
    name = "Tune Boost image dismissal",
    description =
        "Adds image-viewer-only quick-flick dismissal with a moderate distance option without changing GIF, video, or general navigation gestures.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        check(CONTRACT_MARKER.endsWith("ISSUE96_V4_1"))

        arrayOf(
            mediaImageActivityOnCreateFingerprint,
            legacyImageActivityOnCreateFingerprint,
            legacyImageActivity2OnCreateFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.apply {
                val returnIndex = implementation!!.instructions
                    .withIndex()
                    .lastOrNull { (_, instruction) ->
                        instruction.opcode == Opcode.RETURN_VOID
                    }
                    ?.index
                    ?: error("Could not find image activity return-void")

                // Preserve the native return instruction location so all
                // existing branch labels execute the hook before returning.
                replaceInstruction(
                    returnIndex,
                    "invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->apply(Ljava/lang/Object;)V",
                )
                addInstruction(returnIndex + 1, "return-void")
            }
        }

        imageModelFragmentOnCreateViewFingerprint.method.apply {
            val returnIndex = implementation!!.instructions
                .withIndex()
                .lastOrNull { (_, instruction) ->
                    instruction.opcode == Opcode.RETURN_OBJECT
                }
                ?.index
                ?: error("Could not find image fragment return-object")

            val returnRegister =
                (implementation!!.instructions[returnIndex] as OneRegisterInstruction)
                    .registerA

            // Preserve labels on the original return-object location,
            // then return the same object register from a new instruction.
            replaceInstruction(
                returnIndex,
                "invoke-static {p0}, $EXTENSION_CLASS_DESCRIPTOR->apply(Ljava/lang/Object;)V",
            )
            addInstruction(
                returnIndex + 1,
                "return-object v$returnRegister",
            )
        }
    }
}
