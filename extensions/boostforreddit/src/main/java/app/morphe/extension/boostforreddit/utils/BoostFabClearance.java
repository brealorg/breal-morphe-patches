/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns the vertical clearance of Boost's scroll-aware FAB menu and
 * bottom-navigation FABs.
 *
 * Boost's native behaviors depend on both the Snackbar and the bottom
 * navigation, but each callback writes translationY from only the view that
 * changed. Removing a Snackbar therefore resets the FAB to translationY=0 and
 * drops it behind the visible navigation (issue #179). Recompute from every
 * visible dependency instead so the highest one always wins.
 */
@SuppressWarnings("unused")
public final class BoostFabClearance {
    private static final String TAG = "MorpheFabClearance";
    private static final String MARKER =
            "MORPHE_BOOST_FAB_DEPENDENCY_CLEARANCE_ISSUE179_V2";

    /** Same dependency set as Boost's native layoutDependsOn checks. */
    private static final String[] DEPENDENCY_CLASSES = {
            "com.google.android.material.snackbar.Snackbar$SnackbarLayout",
            "com.google.android.material.bottomnavigation.BottomNavigationView",
            "com.aurelhubert.ahbottomnavigation.AHBottomNavigation",
            "com.rubenmayayo.reddit.ui.customviews.NavigationArrowsView",
    };

    private static final Map<Class<?>, Boolean> DEPENDENCY_CACHE =
            new HashMap<>();

    private BoostFabClearance() {
    }

    /** Replaces onDependentViewChanged. */
    public static void onDependentViewChanged(
            ViewGroup coordinator,
            View child
    ) {
        apply(coordinator, child, null);
    }

    /** Replaces onDependentViewRemoved. */
    public static void onDependentViewRemoved(
            ViewGroup coordinator,
            View child,
            View removed
    ) {
        float translation = apply(coordinator, child, removed);

        Log.i(
                TAG,
                "dependency removed marker="
                        + MARKER
                        + " removed="
                        + (removed == null ? "null" : removed.getClass().getName())
                        + " translationY="
                        + translation
        );
    }

    private static float apply(
            ViewGroup coordinator,
            View child,
            View excluded
    ) {
        if (coordinator == null || child == null) {
            return 0.0f;
        }

        // The edge the FAB is laid out against. Using the coordinator height
        // would add its bottom padding (system inset) to the lift.
        int anchorBottom = child.getBottom();
        ViewGroup.LayoutParams params = child.getLayoutParams();

        if (params instanceof ViewGroup.MarginLayoutParams) {
            anchorBottom += ((ViewGroup.MarginLayoutParams) params).bottomMargin;
        }

        float translation = 0.0f;

        for (int i = 0, count = coordinator.getChildCount(); i < count; i++) {
            View dependency = coordinator.getChildAt(i);

            if (
                    dependency == null
                            || dependency == child
                            || dependency == excluded
                            || !dependency.isShown()
                            || !isDependency(dependency.getClass())
            ) {
                continue;
            }

            // Equals Boost's (translationY - height) for bottom-aligned views,
            // and also clears a Snackbar anchored above the navigation.
            float visibleTop =
                    dependency.getTop() + dependency.getTranslationY();
            translation = Math.min(
                    translation,
                    visibleTop - anchorBottom
            );
        }

        child.setTranslationY(translation);
        return translation;
    }

    private static boolean isDependency(Class<?> type) {
        Boolean cached = DEPENDENCY_CACHE.get(type);

        if (cached != null) {
            return cached;
        }

        boolean result = false;

        for (Class<?> current = type; current != null && !result; current = current.getSuperclass()) {
            String name = current.getName();

            for (String dependencyClass : DEPENDENCY_CLASSES) {
                if (dependencyClass.equals(name)) {
                    result = true;
                    break;
                }
            }
        }

        DEPENDENCY_CACHE.put(type, result);
        return result;
    }
}
