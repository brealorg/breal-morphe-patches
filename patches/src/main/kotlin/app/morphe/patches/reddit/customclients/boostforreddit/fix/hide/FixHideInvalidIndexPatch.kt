/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.hide

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val fixHideInvalidIndexPatch = bytecodePatch(
    name = "Fix Hide crash",
    description = "Prevents Boost from crashing when Hide receives an invalid feed/list index.",
    default = true
) {
    compatibleWith(*BoostCompatible)

    execute {
        // Activity-level guard for SubredditBaseActivity.O(index, submission, hidden).
        //
        // Preserve the backend hide/unhide call, but skip the local ArrayList
        // mutation when Boost passes an invalid feed index.
        subredditBaseHideToggleFingerprint.method.apply {
            val removeIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.toString() ==
                    "Ljava/util/ArrayList;->remove(I)Ljava/lang/Object;"
            }

            addInstructionsWithLabels(
                removeIndex,
                """
                    if-ltz p1, :morphe_skip_activity_hide_remove
                    invoke-virtual {p2}, Ljava/util/ArrayList;->size()I
                    move-result p3
                    if-ge p1, p3, :morphe_skip_activity_hide_remove
                """,
                ExternalLabel("morphe_skip_activity_hide_remove", getInstruction(removeIndex + 1)),
            )
        }

        // Fragment-level guard for C0(adapterPosition, submission).
        //
        // Preserve O(...) first. For local fragment list mutation:
        // - valid v0: fall through to original remove(index)
        // - invalid v0: remove(submissionModel), then skip original remove(index)
        //
        // Important: do not ExternalLabel-jump to the original remove(I).
        // Earlier V4D decoded as object-remove -> original remove(I), which can
        // still crash when v0 == -1. V5 inserts an explicit after-remove NOP
        // and only object-path jumps to that post-remove anchor.
        hideFragmentC0Fingerprints.forEach { fingerprint ->
            fingerprint.method.apply {
                val callbackIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.INVOKE_INTERFACE &&
                        getReference<MethodReference>()?.toString()
                            ?.endsWith("->O(ILcom/rubenmayayo/reddit/models/reddit/SubmissionModel;Z)V") == true
                }

                val removeIndex = indexOfFirstInstructionReversedOrThrow {
                    opcode == Opcode.INVOKE_VIRTUAL &&
                        getReference<MethodReference>()?.toString() ==
                        "Ljava/util/ArrayList;->remove(I)Ljava/lang/Object;"
                }

                if (callbackIndex >= removeIndex) {
                    throw IllegalStateException(
                        "Unexpected Hide C0 instruction order: callback=$callbackIndex remove=$removeIndex"
                    )
                }

                // Stable post-remove target. Valid path executes original remove(I)
                // and then this NOP. Object path jumps directly here, skipping the
                // original index-based remove.
                addInstructionsWithLabels(
                    removeIndex + 1,
                    """
                        nop
                    """,
                )

                val afterOriginalRemove = getInstruction(removeIndex + 1)

                addInstructionsWithLabels(
                    removeIndex,
                    """
                        if-ltz v0, :morphe_fragment_remove_by_object
                        invoke-virtual {v1}, Ljava/util/ArrayList;->size()I
                        move-result v2
                        if-ge v0, v2, :morphe_fragment_remove_by_object
                        goto :morphe_fragment_do_original_index_remove
                    :morphe_fragment_remove_by_object
                        invoke-virtual {v1, p2}, Ljava/util/ArrayList;->remove(Ljava/lang/Object;)Z
                        move-result v2
                        goto :morphe_after_fragment_original_remove
                    :morphe_fragment_do_original_index_remove
                        nop
                    """,
                    ExternalLabel("morphe_after_fragment_original_remove", afterOriginalRemove),
                )
            }
        }

        // Feed/list action guard for SubmissionRecyclerViewLinearMiniCardsAdsFragment.s1(index).
        //
        // Issue #19 logcat shows Boost can pass an invalid converted list index
        // from O2(index), notably -1, into both ArrayList.get(index) and
        // ArrayList.remove(index). The older Hide guards cover O(...) and C0(...),
        // but this click-path reaches s1(I) directly.
        feedActionS1InvalidIndexFingerprint.method.apply {
            val settingsIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() == "Lid/b;->v0()Lid/b;"
            }
            val returnIndex = indexOfFirstInstructionReversedOrThrow {
                opcode == Opcode.RETURN_VOID
            }

            addInstructionsWithLabels(
                settingsIndex,
                """
                    if-ltz v0, :morphe_skip_s1_invalid_index
                    iget-object v1, p0, Lcom/rubenmayayo/reddit/ui/fragments/b;->d:Ljava/util/ArrayList;
                    invoke-virtual {v1}, Ljava/util/ArrayList;->size()I
                    move-result v2
                    if-ge v0, v2, :morphe_skip_s1_invalid_index
                """,
                ExternalLabel("morphe_skip_s1_invalid_index", getInstruction(returnIndex)),
            )
        }

    }
}
