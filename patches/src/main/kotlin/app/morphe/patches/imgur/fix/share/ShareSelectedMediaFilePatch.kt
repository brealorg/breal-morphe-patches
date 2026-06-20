package app.morphe.patches.imgur.fix.share

import app.morphe.patches.imgur.ImgurCompatible
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.imgur.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/imgur/ShareMediaFile;"

@Suppress("unused")
val shareSelectedMediaFilePatch = bytecodePatch(
    name = "Share selected media file",
    description = "Replaces Imgur's Download share action with direct file sharing. " +
        "The selected media is cached privately, shared with Android's share sheet, " +
        "and is not saved permanently to /sdcard/Download/Imgur.",
    default = true
) {
    compatibleWith(*ImgurCompatible)

    dependsOn(sharedExtensionPatch)

    execute {
        val downloadMethod = shareActionsOnDownloadImageIntentFingerprint.method

        val insertIndex = downloadMethod.indexOfFirstInstructionOrThrow {
            val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false

            reference.definingClass == "Lcom/imgur/mobile/common/ui/share/ShareActionsActivity;" &&
                reference.name == "trackShareSelected" &&
                reference.parameterTypes.size == 1
        } + 1

        downloadMethod.addInstructions(
            insertIndex,
            """
                # breal patch: replace Imgur's broken public Download flow with direct file sharing.
                invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

                move-result-object v0

                const-string v1, "android.intent.extra.TEXT"

                invoke-virtual {v0, v1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

                move-result-object v0

                invoke-static {p0, v0}, $EXTENSION_CLASS_DESCRIPTOR->share(Landroid/app/Activity;Ljava/lang/String;)V

                return-void
            """
        )

        val initialIntentsMethod = getDirectImageLinkInitialIntentsFingerprint.method

        val labeledIntentConstructorIndex = initialIntentsMethod.indexOfFirstInstructionOrThrow {
            val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false

            reference.definingClass == "Landroid/content/pm/LabeledIntent;" &&
                reference.name == "<init>" &&
                reference.parameterTypes.size == 4
        }

        val downloadLabelIndex = labeledIntentConstructorIndex - 2

        initialIntentsMethod.replaceInstruction(
            downloadLabelIndex,
            "const-string p3, \"Share media file\""
        )

        initialIntentsMethod.replaceInstruction(
            labeledIntentConstructorIndex,
            "invoke-direct {p1, v2, v1, p3, v0}, Landroid/content/pm/LabeledIntent;-><init>(Landroid/content/Intent;Ljava/lang/String;Ljava/lang/CharSequence;I)V"
        )
    }
}
