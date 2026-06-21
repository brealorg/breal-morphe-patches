/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.sdk

import app.morphe.patcher.Fingerprint

internal val billingClientReceiverRegistrationFingerprint = Fingerprint(
    parameters = listOf(
        "Landroid/content/Context;",
        "Landroid/content/IntentFilter;"
    ),
    custom = { method, classDef ->
        classDef.type == "Lcom/android/billingclient/api/v;" &&
            method.name == "c"
    }
)
