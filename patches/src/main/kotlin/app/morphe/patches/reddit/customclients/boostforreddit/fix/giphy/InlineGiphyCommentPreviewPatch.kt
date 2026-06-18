/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.giphy

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val COMMENT_VIEW_HOLDER_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;"

private const val INLINE_GIPHY_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/giphy/InlineGiphyCommentPreview;"

@Suppress("unused")
val inlineGiphyCommentPreviewPatch = bytecodePatch(
    name = "Show inline Giphy previews in comments",
    description = "Adds inline animated Giphy previews below Boost comment text for Reddit Giphy markdown and Giphy links.",
    default = true
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        commentViewHolderBindFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static {p1}, $INLINE_GIPHY_EXTENSION_DESCRIPTOR->cleanCommentHtml(Ljava/lang/Object;)V
                    invoke-static {p0, p1, p5}, $INLINE_GIPHY_EXTENSION_DESCRIPTOR->bind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
                    """
            )
        }

        commentViewHolderCollapseFingerprint.method.apply {
            val syncIndices = implementation!!.instructions.withIndex().mapNotNull { (index, instruction) ->
                val methodReference = instruction.getReference<MethodReference>()
                if (
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                    methodReference?.definingClass == COMMENT_VIEW_HOLDER_DESCRIPTOR &&
                    methodReference.name == "J0"
                ) {
                    index
                } else {
                    null
                }
            }

            syncIndices.asReversed().forEach { index ->
                addInstructions(
                    index + 1,
                    """
                        invoke-static {p0}, $INLINE_GIPHY_EXTENSION_DESCRIPTOR->syncWithCommentState(Ljava/lang/Object;)V
                        """
                )
            }
        }
    }
}
