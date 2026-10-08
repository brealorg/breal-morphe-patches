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

private fun dependentViewFingerprint(
    definingClass: String,
    name: String,
    childType: String,
    returnType: String,
) = Fingerprint(
    definingClass = definingClass,
    name = name,
    returnType = returnType,
    parameters = listOf(
        "Landroidx/coordinatorlayout/widget/CoordinatorLayout;",
        childType,
        "Landroid/view/View;",
    ),
)

private const val SCROLL_AWARE_FAB_MENU_BEHAVIOR =
    "Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABMenuBehavior;"
private const val SCROLL_AWARE_FAB_BOTTOM_NAVIGATION_BEHAVIOR =
    "Lcom/rubenmayayo/reddit/ui/customviews/ScrollAwareFABBehaviorBottomNavigation;"
private const val FLOATING_ACTION_MENU_TYPE =
    "Lcom/rubenmayayo/reddit/ui/customviews/fab/FloatingActionMenu;"
private const val FLOATING_ACTION_BUTTON_TYPE =
    "Lcom/google/android/material/floatingactionbutton/FloatingActionButton;"

internal val fabMenuDependentViewChangedFingerprint = dependentViewFingerprint(
    SCROLL_AWARE_FAB_MENU_BEHAVIOR, "F", FLOATING_ACTION_MENU_TYPE, "Z",
)

internal val fabMenuDependentViewRemovedFingerprint = dependentViewFingerprint(
    SCROLL_AWARE_FAB_MENU_BEHAVIOR, "G", FLOATING_ACTION_MENU_TYPE, "V",
)

// Inbox/Profile fab + fab_submit, Search fab_save and Home fab_random.
internal val fabBottomNavigationDependentViewChangedFingerprint = dependentViewFingerprint(
    SCROLL_AWARE_FAB_BOTTOM_NAVIGATION_BEHAVIOR, "H", FLOATING_ACTION_BUTTON_TYPE, "Z",
)

internal val fabBottomNavigationDependentViewRemovedFingerprint = dependentViewFingerprint(
    SCROLL_AWARE_FAB_BOTTOM_NAVIGATION_BEHAVIOR, "P", FLOATING_ACTION_BUTTON_TYPE, "V",
)
