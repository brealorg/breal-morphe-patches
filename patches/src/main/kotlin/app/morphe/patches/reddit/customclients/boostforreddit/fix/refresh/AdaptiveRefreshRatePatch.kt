package app.morphe.patches.reddit.customclients.boostforreddit.fix.refresh

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.patches.reddit.customclients.boostforreddit.misc.settings.boostMorpheSettingsSkeletonPatch

private const val ADAPTIVE_REFRESH_RATE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/AdaptiveRefreshRate;"

private val boostApplicationOnCreateFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/MyApplication;",
    name = "onCreate",
)

@Suppress("unused")
val preferHighRefreshRatePatch = bytecodePatch(
    name = "Prefer high refresh rate",
    description = "Requests a high display refresh rate for Boost on adaptive-refresh devices.",
    default = false,
) {
    dependsOn(sharedExtensionPatch, boostMorpheSettingsSkeletonPatch)
    compatibleWith(*BoostCompatible)

    execute {
        boostApplicationOnCreateFingerprint.method.addInstructions(
            1,
            """
                invoke-static {p0}, $ADAPTIVE_REFRESH_RATE_EXTENSION_DESCRIPTOR->install(Landroid/app/Application;)V
            """.trimIndent(),
        )
    }
}
