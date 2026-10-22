/*
 * Copyright 2026 wchill.
 * Modifications Copyright 2026 brealorg.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.giphy

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.all.misc.PatchCategory

@Suppress("unused")
val fixSlowGiphyLoadingPatch = bytecodePatch(
    name = "Fix slow Giphy loading",
    description = "Loads Giphy posts faster by playing the MP4 directly instead of waiting for the Giphy API.",
    default = true
) {
    category(PatchCategory.MEDIA)
    compatibleWith(*BoostCompatible)

    execute {
        resolveGiphyApiFingerprint.method.addInstructions(
            0,
            """
                invoke-direct { p0 }, Lhe/r;->x()V
                return-void
                """
        )
    }
}
