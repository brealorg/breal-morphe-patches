package app.morphe.patches.reddit.customclients.boostforreddit.fix.openlinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch

private const val OPEN_BY_DEFAULT_RECOVERY_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/OpenByDefaultRecovery;"

private val boostApplicationOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/MyApplication;",
    name = "onCreate",
)

@Suppress("unused")
val boostOpenByDefaultRecoveryPatch = bytecodePatch(
    name = "Restore Boost Open by default prompt",
    description = "Prompts after Boost patch updates to reopen Android's Open by default settings so supported Reddit links can be re-enabled.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        boostApplicationOnCreateFingerprint.method.addInstructions(
            1,
            """
                invoke-static {p0}, $OPEN_BY_DEFAULT_RECOVERY_DESCRIPTOR->install(Landroid/app/Application;)V
            """.trimIndent(),
        )
    }
}
