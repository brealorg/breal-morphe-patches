/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.reddit.customclients.boostforreddit.fix.homefab

import app.morphe.patcher.Fingerprint

internal val fabNestedScrollFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABBehavior;",
    name = "M",
    returnType = "V",
    parameters = listOf(
        "Landroidx/coordinatorlayout/widget/CoordinatorLayout;",
        "Lcom/google/android/material/floatingactionbutton/FloatingActionButton;",
        "Landroid/view/View;",
        "I", "I", "I", "I",
    ),
)

internal val fabMenuNestedScrollFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMenuBehavior;",
    name = "H",
    returnType = "V",
    parameters = listOf(
        "Landroidx/coordinatorlayout/widget/CoordinatorLayout;",
        "Lcom/rubenmayayo/reddit/ui/customviews/fab/FloatingActionMenu;",
        "Landroid/view/View;",
        "I", "I", "I", "I",
    ),
)

internal val fabMiniNestedScrollFingerprint = Fingerprint(
    definingClass = "Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMiniBehavior;",
    name = "H",
    returnType = "V",
    parameters = listOf(
        "Landroidx/coordinatorlayout/widget/CoordinatorLayout;",
        "Lcom/google/android/material/floatingactionbutton/FloatingActionButton;",
        "Landroid/view/View;",
        "I", "I", "I", "I",
    ),
)
