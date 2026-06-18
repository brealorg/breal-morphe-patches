/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.downloads

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val DOWNLOAD_CHANNEL_ID = "80_downloads_channel"
private const val COMPLETED_DOWNLOAD_CHANNEL_ID = "81_downloads_completed_channel"

// R.string.notifications_channel_downloads in Boost for Reddit 1.12.12.
private const val DOWNLOAD_CHANNEL_NAME_RESOURCE_ID = "0x7f1303b6"

// android.app.NotificationManager.IMPORTANCE_DEFAULT
private const val IMPORTANCE_DEFAULT = "0x3"

@Suppress("unused")
val fixDownloadCompletedNotificationVisibilityPatch = bytecodePatch(
    name = "Fix download completed notification visibility",
    description = "Moves completed download notifications to a separate default-importance channel so download completion is visible while progress notifications remain low-priority.",
    default = true
) {
    compatibleWith(*BoostCompatible)

    execute {
        notificationChannelsHelperFingerprint.method.apply {
            val notificationServiceStringIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.CONST_STRING &&
                    getReference<StringReference>()?.string == "notification"
            }

            addInstructions(
                notificationServiceStringIndex,
                """
                    new-instance v6, Landroid/app/NotificationChannel;

                    const v2, $DOWNLOAD_CHANNEL_NAME_RESOURCE_ID

                    invoke-virtual {p0, v2}, Landroid/content/Context;->getString(I)Ljava/lang/String;

                    move-result-object v2

                    const/4 v3, $IMPORTANCE_DEFAULT

                    const-string v5, "$COMPLETED_DOWNLOAD_CHANNEL_ID"

                    invoke-direct {v6, v5, v2, v3}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

                    const/4 v2, 0x0

                    invoke-static {v6, v2}, Lr5/f;->a(Landroid/app/NotificationChannel;Z)V

                    invoke-static {v6, v2}, Lhd/e;->a(Landroid/app/NotificationChannel;Z)V

                    invoke-static {v6, v2}, Lhd/f;->a(Landroid/app/NotificationChannel;Z)V

                    const/4 v2, -0x1

                    invoke-static {v6, v2}, Lhd/g;->a(Landroid/app/NotificationChannel;I)V
                    """
            )

            val createDownloadChannelIndex = indexOfFirstInstructionReversedOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.toString() ==
                    "Lr5/g;->a(Landroid/app/NotificationManager;Landroid/app/NotificationChannel;)V"
            }

            addInstructions(
                createDownloadChannelIndex + 1,
                """
                    invoke-static {p0, v6}, Lr5/g;->a(Landroid/app/NotificationManager;Landroid/app/NotificationChannel;)V
                    """
            )
        }

        downloadCompletedNotificationFingerprint.method.apply {
            val completedNotificationChannelStringIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.CONST_STRING &&
                    getReference<StringReference>()?.string == DOWNLOAD_CHANNEL_ID
            }

            replaceInstruction(
                completedNotificationChannelStringIndex,
                """const-string v2, "$COMPLETED_DOWNLOAD_CHANNEL_ID""""
            )
        }
    }
}
