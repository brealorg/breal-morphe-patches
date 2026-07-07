package app.morphe.patches.reddit.customclients.boostforreddit.fix.widget

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PENDING_INTENT_GET_ACTIVITY_REFERENCE =
    "Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"

private const val BOOST_PENDING_INTENT_FLAGS_REFERENCE =
    "Lhe/h0;->D()I"

@Suppress("unused")
val fixImageWidgetClickTargetPatch = bytecodePatch(
    name = "Fix Boost image widget click target",
    description = "Prevents Boost's image widget from opening a stale post by making the CommentsActivity PendingIntent data unique per widget update.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        imageWidgetProviderUpdateWithSubmissionFingerprint.method.apply {
            val getActivityIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() == PENDING_INTENT_GET_ACTIVITY_REFERENCE
            }

            val pendingIntentFlagsIndex = implementation!!.instructions
                .withIndex()
                .filter { (index, instruction) ->
                    index < getActivityIndex &&
                        instruction.opcode == Opcode.INVOKE_STATIC &&
                        instruction.getReference<MethodReference>()?.toString() == BOOST_PENDING_INTENT_FLAGS_REFERENCE
                }
                .lastOrNull()
                ?.index
                ?: throw IllegalStateException(
                    "Could not find ImageWidgetProvider h0.D flags call before CommentsActivity PendingIntent.getActivity"
                )

            if (pendingIntentFlagsIndex >= getActivityIndex) {
                throw IllegalStateException(
                    "Unexpected ImageWidgetProvider instruction order: h0.D=$pendingIntentFlagsIndex getActivity=$getActivityIndex"
                )
            }

            addInstructions(
                pendingIntentFlagsIndex,
                """
                    new-instance v4, Ljava/lang/StringBuilder;
                    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V

                    const-string v5, "morphe-widget-image://open/"
                    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

                    invoke-virtual {v4, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

                    const-string v5, "/"
                    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

                    invoke-virtual {p2}, Ljava/lang/Object;->hashCode()I
                    move-result v5

                    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

                    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
                    move-result-object v4

                    invoke-static {v4}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
                    move-result-object v4

                    invoke-virtual {v2, v4}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;
                """,
            )
        }
    }
}
