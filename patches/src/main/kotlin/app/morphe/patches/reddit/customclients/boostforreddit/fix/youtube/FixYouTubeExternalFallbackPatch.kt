/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.youtube

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val fixYouTubeExternalFallbackPatch = bytecodePatch(
    name = "Fix Boost YouTube playback fallback",
    description =
        "Opens the original YouTube link externally when Boost's legacy embedded YouTube player is unavailable.",
    default = true,
) {
    compatibleWith(*BoostCompatible)

    execute {
        videoActivityInitializationFailureFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "MorpheBoost"
                const-string v1, "MORPHE_BOOST_YOUTUBE_EXTERNAL_FALLBACK_V1"
                invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
                move-result v0

                const/4 v0, 0x0
                invoke-direct {p0, v0}, Lcom/rubenmayayo/reddit/ui/activities/VideoActivity;->l(Z)V
                return-void
            """,
        )
    }
}
