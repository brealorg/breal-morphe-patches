/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.imgur

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal val ImgurCompatible = arrayOf(
    Compatibility(
        name = "Imgur",
        packageName = "com.imgur.mobile",
        targets = listOf(AppTarget(version = "7.33.0.0"))
    )
)
