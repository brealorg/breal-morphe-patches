/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.feed

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val FILTERED_PAGE_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostFilteredPageAutoLoad;"

private const val THING_FRAGMENT =
    "Lcom/rubenmayayo/reddit/ui/fragments/l;"

// Native "Load next" Snackbar action: calls Callbacks.T0() (next page).
private const val LOAD_NEXT_LISTENER =
    "Lcom/rubenmayayo/reddit/ui/fragments/l\$b;"

// ThingFragment.showFilteredNextPageSnackbar(View): shown when content-type
// filters removed every post on a page.
private val filteredNextPageSnackbarFingerprint = Fingerprint(
    definingClass = THING_FRAGMENT,
    name = "E1",
    accessFlags = listOf(AccessFlags.PRIVATE),
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

@Suppress("unused")
val autoLoadFilteredPagePatch = bytecodePatch(
    name = "Auto-load next page after filtered results",
    description =
        "Loads the next page automatically when content filters hide every post on a page, " +
            "instead of leaving the feed stuck behind a \"Results were filtered\" Snackbar.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        filteredNextPageSnackbarFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    new-instance v0, $LOAD_NEXT_LISTENER
                    invoke-direct {v0, p0}, $LOAD_NEXT_LISTENER-><init>($THING_FRAGMENT)V
                    invoke-static {p1, v0}, $FILTERED_PAGE_EXTENSION_DESCRIPTOR->onPageFiltered(Landroid/view/View;Landroid/view/View${'$'}OnClickListener;)Z
                    move-result v0
                    if-eqz v0, :morphe_show_native_snackbar
                    return-void
                """,
                ExternalLabel("morphe_show_native_snackbar", getInstruction(0)),
            )
        }
    }
}
