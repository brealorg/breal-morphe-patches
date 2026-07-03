/*
 * Copyright 2026 brealorg.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.gifsearch

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val restoreGifSearchIntegrationProbePatch = bytecodePatch(
    name = "Restore GIF search integration probe",
    description = "Fingerprint-only probe for Boost compose/reply GIF insertion through FormattingBar. No runtime behavior change.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        // Deliberately no bytecode mutation.
        //
        // This probe validates that Boost 1.12.12 still exposes the shared
        // FormattingBar insertion surface used by compose, reply, and submit UI:
        //
        // - p(String): replace active selection with markdown/text
        // - l(String): open existing link dialog with URL prefilled
        // - k(String, String): open existing link dialog with text+URL
        // - setEditText(EditText): bind active editor
        //
        // Actual GIF insertion/search behavior must be added in a later patch
        // after this fingerprint-only probe has compiled and resolved.
        formattingBarInsertMarkdownFingerprint.method
        formattingBarOpenLinkDialogWithUrlFingerprint.method
        formattingBarOpenLinkDialogFingerprint.method
        formattingBarSetEditTextFingerprint.method
    }
}
