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
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val COMMENT_VIEW_HOLDER_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;"

private const val INLINE_GIPHY_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/giphy/InlineGiphyCommentPreview;"

private const val TABLE_TEXT_VIEW_DESCRIPTOR =
    "Lcom/rubenmayayo/reddit/ui/customviews/TableTextView;"

@Suppress("unused")
val inlineGiphyCommentPreviewPatch = bytecodePatch(
    name = "Show inline Giphy previews in comments",
    description = "Adds inline media previews below Boost comment text for Giphy links and direct static image URLs.",
    default = true
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        commentViewHolderBindFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static {p0, p1}, $INLINE_GIPHY_EXTENSION_DESCRIPTOR->cleanCommentHtml(Ljava/lang/Object;Ljava/lang/Object;)V
                    invoke-static {p0, p1, p5}, $INLINE_GIPHY_EXTENSION_DESCRIPTOR->bind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
                    """
            )

            // Issue #36 v16:
            //
            // CommentViewHolder.commentTv is a public TableTextView reference,
            // but its LinkTextView field b is package-private and cannot be
            // accessed legally from CommentViewHolder's package.
            //
            // Pass the TableTextView container to the extension. The extension
            // resolves its persistent first child through ViewGroup.getChildAt,
            // using Android's public API rather than direct field access.
            //
            // The sequence remains attached to the return control-flow label,
            // so every incoming branch executes the typed reload and hook.
            val returnIndices = implementation!!.instructions
                .withIndex()
                .mapNotNull { (index, instruction) ->
                    if (instruction.opcode == Opcode.RETURN_VOID) index else null
                }

            check(returnIndices.isNotEmpty()) {
                "CommentViewHolder bind method has no RETURN_VOID"
            }

            returnIndices.asReversed().forEach { index ->
                addInstructionsAtControlFlowLabel(
                    index,
                    """
                        iget-object p1, p0, $COMMENT_VIEW_HOLDER_DESCRIPTOR->o:Lcom/rubenmayayo/reddit/models/reddit/CommentModel;
                        iget-object p2, p0, $COMMENT_VIEW_HOLDER_DESCRIPTOR->commentTv:$TABLE_TEXT_VIEW_DESCRIPTOR
                        invoke-static {p0, p1, p2}, $INLINE_GIPHY_EXTENSION_DESCRIPTOR->applySourceTextPolicyAfterBind(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V
                        """
                )
            }
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
