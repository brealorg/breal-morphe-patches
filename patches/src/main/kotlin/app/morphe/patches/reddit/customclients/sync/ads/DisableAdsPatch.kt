package app.morphe.patches.reddit.customclients.sync.ads

import app.morphe.patcher.patch.BytecodePatchBuilder
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly
import app.morphe.patches.all.misc.PatchCategory

fun disableAdsPatch(block: BytecodePatchBuilder.() -> Unit = {}) = bytecodePatch(
    name = "Disable ads",
    description = "Removes ads.",
    default = true
) {
    category(PatchCategory.ADS)
    execute {
        isAdsEnabledFingerprint.method.returnEarly(false)
    }

    block()
}
