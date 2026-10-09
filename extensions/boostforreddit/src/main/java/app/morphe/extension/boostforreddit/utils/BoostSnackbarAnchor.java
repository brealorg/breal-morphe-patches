/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/**
 * Anchors Boost Snackbars above the visible bottom navigation.
 *
 * Most Boost Snackbars are shown without an anchor (or anchored to the hidden
 * legacy navigation), so they are laid out in the navigation's slot and cover
 * it. Material's own anchor API keeps them above the navigation.
 */
@SuppressWarnings("unused")
public final class BoostSnackbarAnchor {
    private static final String TAG = "MorpheSnackbarAnchor";
    private static final String MARKER =
            "MORPHE_BOOST_SNACKBAR_NAV_ANCHOR_V1";
    private static final String NAVIGATION_ID_NAME = "bottom_navigation_view";
    private static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";

    private BoostSnackbarAnchor() {
    }

    /**
     * Called at the start of BaseTransientBottomBar.show().
     *
     * @return the navigation to anchor to, or null to keep the native anchor.
     */
    public static View anchorFor(
            ViewGroup targetParent,
            View currentAnchor,
            View snackbarView
    ) {
        if (targetParent == null) {
            return null;
        }

        View navigation = findNavigation(targetParent);

        if (navigation == null || !isOnScreen(navigation)) {
            return null;
        }

        ViewParent coordinator = navigation.getParent();

        if (coordinator != targetParent && coordinator instanceof ViewGroup) {
            // Not a dependency of the FABs next to the navigation.
            BoostFabClearance.trackForeignObstruction(
                    (ViewGroup) coordinator,
                    snackbarView
            );
        }

        if (currentAnchor != null && currentAnchor.isShown()) {
            return null;
        }

        Log.i(
                TAG,
                "anchored to navigation marker="
                        + MARKER
                        + " replacedHiddenAnchor="
                        + (currentAnchor != null)
        );
        return navigation;
    }

    private static View findNavigation(ViewGroup targetParent) {
        int id = navigationId(targetParent);

        if (id == 0) {
            return null;
        }

        View navigation = targetParent.findViewById(id);

        if (navigation == null) {
            // Snackbars hosted below the coordinator (e.g. a content frame).
            navigation = targetParent.getRootView().findViewById(id);
        }

        return navigation;
    }

    private static boolean isOnScreen(View navigation) {
        // Hidden by scroll (HideBottomViewOnScrollBehavior translates it out).
        return navigation.isShown()
                && navigation.getHeight() > 0
                && navigation.getTranslationY() < navigation.getHeight() / 2f;
    }

    private static int navigationId(View view) {
        Resources resources = view.getResources();
        int id = resources.getIdentifier(
                NAVIGATION_ID_NAME,
                "id",
                view.getContext().getPackageName()
        );

        return id != 0
                ? id
                : resources.getIdentifier(NAVIGATION_ID_NAME, "id", BOOST_PACKAGE);
    }
}
