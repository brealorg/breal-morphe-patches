package app.morphe.patches.reddit.customclients.sync.syncforreddit.annoyances.startup

import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.sync.SyncForRedditCompatible
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val disableSyncForLemmyBottomSheetPatch = bytecodePatch(
    name = "Disable Sync for Lemmy bottom sheet",
    description = "Hides the startup prompt that asks you to sign up for Sync for Lemmy.",
    default = true
) {
    category(PatchCategory.ADS)
    compatibleWith(*SyncForRedditCompatible)

    execute {
        mainActivityOnCreateFingerprint.method.apply {
            val showBottomSheetIndex = implementation!!.instructions.lastIndex - 1

            removeInstruction(showBottomSheetIndex)
        }
    }
}
