package app.morphe.patches.imgur.fix.share

import app.morphe.patches.imgur.ImgurCompatible
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.imgur.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patches.all.misc.PatchCategory

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/imgur/ShareMediaFile;"

@Suppress("unused")
val shareSelectedMediaFilePatch = bytecodePatch(
    name = "Share selected media file",
    description = "Makes Imgur post-detail media long-press share the selected media file. " +
        "Also replaces Imgur's Download share action with direct file sharing. " +
        "The selected media is cached privately, shared with Android's share sheet, " +
        "and is not saved permanently to /sdcard/Download/Imgur.",
    default = true
) {
    category(PatchCategory.MEDIA)
    compatibleWith(*ImgurCompatible)

    dependsOn(sharedExtensionPatch)

    execute {
        // breal patch: patch long-press callsites directly instead of ShareUtils$Companion.
        // MediaItemsActions.onLongPress registers at ShareUtils call:
        // v1 = Context, v7 = mediaLink / raw selected media URL.
        val mediaItemsActionsMethod = mediaItemsActionsOnLongPressFingerprint.method
        val mediaItemsActionsShareCallIndex = mediaItemsActionsMethod.indexOfFirstInstructionOrThrow {
            val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false
            reference.definingClass == "Lcom/imgur/mobile/common/ui/share/ShareUtils${'$'}Companion;" &&
                reference.name == "shareDirectImageLink" &&
                reference.parameterTypes.size == 12
        }

        mediaItemsActionsMethod.addInstructions(
            mediaItemsActionsShareCallIndex,
            """
                # breal patch: MediaItemsActions long-press shares the actual selected media file.
                invoke-static {v1, v7}, $EXTENSION_CLASS_DESCRIPTOR->share(Landroid/content/Context;Ljava/lang/String;)V

                return-void
            """
        )

        // MediaViewHolder.onLongPress registers at ShareUtils call:
        // v3 = Context, v9 = mediaLink / raw selected media URL.
        val mediaViewHolderMethod = mediaViewHolderOnLongPressFingerprint.method
        val mediaViewHolderShareCallIndex = mediaViewHolderMethod.indexOfFirstInstructionOrThrow {
            val reference = getReference<MethodReference>() ?: return@indexOfFirstInstructionOrThrow false
            reference.definingClass == "Lcom/imgur/mobile/common/ui/share/ShareUtils${'$'}Companion;" &&
                reference.name == "shareDirectImageLink" &&
                reference.parameterTypes.size == 12
        }

        mediaViewHolderMethod.addInstructions(
            mediaViewHolderShareCallIndex,
            """
                # breal patch: MediaViewHolder long-press shares the actual selected media file.
                invoke-static {v3, v9}, $EXTENSION_CLASS_DESCRIPTOR->share(Landroid/content/Context;Ljava/lang/String;)V

                return-void
            """
        )

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
