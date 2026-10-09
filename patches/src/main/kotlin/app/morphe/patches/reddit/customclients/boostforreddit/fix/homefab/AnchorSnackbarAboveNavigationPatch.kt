/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch

private const val SNACKBAR_ANCHOR_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/utils/BoostSnackbarAnchor;"

private const val BASE_TRANSIENT_BOTTOM_BAR =
    "Lcom/google/android/material/snackbar/BaseTransientBottomBar;"

// Holds the WeakReference to the anchor view; b() returns it.
private const val ANCHOR_HOLDER =
    "Lcom/google/android/material/snackbar/BaseTransientBottomBar\$q;"

// SnackbarBaseLayout: the Snackbar's own view.
private const val SNACKBAR_VIEW =
    "Lcom/google/android/material/snackbar/BaseTransientBottomBar\$t;"

// BaseTransientBottomBar.show()
private val snackbarShowFingerprint = Fingerprint(
    definingClass = BASE_TRANSIENT_BOTTOM_BAR,
    name = "a0",
    returnType = "V",
    parameters = emptyList(),
)

// AHBottomNavigationBehavior.updateSnackbar(View child, View snackbar): runs
// from layoutDependsOn on every layout and sets the Snackbar's bottom margin
// to the legacy AHBottomNavigation height. That navigation is hidden by the
// canonical bottom navigation patches, so the margin is forced to 0 (dropping
// Material's anchor offset) and a layout is requested on every frame.
private val legacyNavigationSnackbarMarginFingerprint = Fingerprint(
    definingClass = "Lcom/aurelhubert/ahbottomnavigation/AHBottomNavigationBehavior;",
    name = "Q",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/view/View;"),
)

@Suppress("unused")
val anchorSnackbarAboveNavigationPatch = bytecodePatch(
    name = "Anchor Boost Snackbars above bottom navigation",
    description =
        "Shows Boost Snackbars above the visible bottom navigation instead of covering it.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*BoostCompatible)

    execute {
        legacyNavigationSnackbarMarginFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-virtual {p1}, Landroid/view/View;->isShown()Z
                    move-result v0
                    if-nez v0, :morphe_update_legacy_snackbar
                    return-void
                """,
                ExternalLabel("morphe_update_legacy_snackbar", getInstruction(0)),
            )
        }

        snackbarShowFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, p0, $BASE_TRANSIENT_BOTTOM_BAR->m:$ANCHOR_HOLDER
                    const/4 v1, 0x0
                    if-eqz v0, :morphe_anchor_resolved
                    invoke-virtual {v0}, $ANCHOR_HOLDER->b()Landroid/view/View;
                    move-result-object v1
                    :morphe_anchor_resolved
                    iget-object v0, p0, $BASE_TRANSIENT_BOTTOM_BAR->g:Landroid/view/ViewGroup;
                    iget-object v2, p0, $BASE_TRANSIENT_BOTTOM_BAR->i:$SNACKBAR_VIEW
                    invoke-static {v0, v1, v2}, $SNACKBAR_ANCHOR_EXTENSION_DESCRIPTOR->anchorFor(Landroid/view/ViewGroup;Landroid/view/View;Landroid/view/View;)Landroid/view/View;
                    move-result-object v0
                    if-eqz v0, :morphe_show_snackbar
                    invoke-virtual {p0, v0}, $BASE_TRANSIENT_BOTTOM_BAR->V(Landroid/view/View;)$BASE_TRANSIENT_BOTTOM_BAR
                """,
                ExternalLabel("morphe_show_snackbar", getInstruction(0)),
            )
        }
    }
}
