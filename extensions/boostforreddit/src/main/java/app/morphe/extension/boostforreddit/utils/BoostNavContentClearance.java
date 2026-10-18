/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Owns the bottom padding that keeps Boost's lists clear of the bottom
 * navigation.
 *
 * Boost's EmptyRecyclerView.F1() pads a list by a fixed 56dp, only while
 * "Hide on scroll" is off, and only once when the list is built. A list built
 * while the preference was on therefore keeps zero padding after it is turned
 * off, and the bar covers its last items until the app restarts. 56dp is also
 * shorter than the bar under edge-to-edge, where it includes the gesture inset.
 *
 * Replace F1() with padding that follows the bar's measured height whenever the
 * bar is enabled, independent of "Hide on scroll". clipToPadding=false keeps
 * this to extra space after the last item, so a hidden bar still uncovers the
 * full list while scrolling. A list laid out at its top also brings back a bar
 * that hide-on-scroll moved away.
 */
@SuppressWarnings("unused")
public final class BoostNavContentClearance {
    private static final String TAG = "MorpheNavClearance";
    private static final String MARKER =
            "MORPHE_BOOST_NAV_CONTENT_CLEARANCE_V1";

    private static final Map<View, Tracker> TRACKERS =
            new WeakHashMap<>();

    private BoostNavContentClearance() {
    }

    /** Called in place of EmptyRecyclerView.F1(). */
    public static void apply(View list) {
        if (!(list instanceof ViewGroup)) {
            return;
        }

        try {
            // Track the list even while the bar is disabled: turning it on
            // from Settings shows it in activities that are not recreated.
            ((ViewGroup) list).setClipToPadding(false);

            if (isBottomNavigationEnabled(list.getContext())) {
                setBottomPadding(
                        list,
                        fallbackHeight(list) + BoostFeedEndFooter.reservedHeight(list),
                        null
                );
            }

            synchronized (TRACKERS) {
                if (TRACKERS.containsKey(list)) {
                    return;
                }

                Tracker tracker = new Tracker(list);
                TRACKERS.put(list, tracker);
                list.addOnAttachStateChangeListener(tracker);

                if (list.isAttachedToWindow()) {
                    tracker.onViewAttachedToWindow(list);
                }
            }
        } catch (Throwable error) {
            Log.e(TAG, "clearance install failed marker=" + MARKER, error);
        }
    }

    private static final class Tracker implements
            View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        private final View list;
        private ViewTreeObserver observer;

        Tracker(View list) {
            this.list = list;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            detachObserver();
            observer = list.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(this);
            onGlobalLayout();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            detachObserver();
        }

        @Override
        public void onGlobalLayout() {
            try {
                View navigation = findNavigation(list);
                setBottomPadding(
                        list,
                        clearanceFor(list, navigation)
                                + BoostFeedEndFooter.reservedHeight(list),
                        navigation
                );
                revealAtTop(list, navigation);
            } catch (Throwable error) {
                Log.e(TAG, "clearance update failed marker=" + MARKER, error);
            }
        }

        private void detachObserver() {
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(this);
            }
            observer = null;
        }
    }

    /**
     * The bar's measured height while it takes part in layout, zero once it is
     * GONE (bar disabled at runtime or hidden by the swipe post viewer), and
     * Boost's 56dp before the bar has been measured or where there is none.
     */
    private static int clearanceFor(View list, View navigation) {
        if (!isBottomNavigationEnabled(list.getContext())) {
            return 0;
        }

        if (navigation == null) {
            return fallbackHeight(list);
        }

        if (navigation.getVisibility() == View.GONE) {
            return 0;
        }

        int height = navigation.getHeight();
        return height > 0 ? height : fallbackHeight(list);
    }

    /**
     * Switching feed from the drawer reloads the same list without p3(), so a
     * bar scrolled away in the old feed stayed hidden over the new one. At the
     * top of a list there is nothing to make room for: show the bar.
     */
    private static void revealAtTop(View list, View navigation) {
        if (
                navigation == null
                        || navigation.getVisibility() != View.VISIBLE
                        || navigation.getTranslationY() == 0.0f
                        || !list.isShown()
                        || list.canScrollVertically(-1)
        ) {
            return;
        }

        BoostSearchBottomNavigation.revealScrolledNavigation(navigation);

        Log.i(
                TAG,
                "scrolled bar revealed at list top marker=" + MARKER
        );
    }

    private static View findNavigation(View list) {
        int id = resourceId(list.getContext(), "bottom_navigation_view", "id");
        return id == 0 ? null : list.getRootView().findViewById(id);
    }

    private static int fallbackHeight(View list) {
        int id = resourceId(
                list.getContext(),
                "bottom_navigation_height",
                "dimen"
        );
        return id == 0
                ? 0
                : list.getResources().getDimensionPixelSize(id);
    }

    private static void setBottomPadding(
            View list,
            int bottom,
            View navigation
    ) {
        if (list.getPaddingBottom() == bottom) {
            return;
        }

        list.setPadding(
                list.getPaddingLeft(),
                list.getPaddingTop(),
                list.getPaddingRight(),
                bottom
        );

        Log.i(
                TAG,
                "content clearance applied marker="
                        + MARKER
                        + " paddingBottom="
                        + bottom
                        + " navHeight="
                        + (navigation == null ? -1 : navigation.getHeight())
                        + " navVisibility="
                        + (navigation == null ? -1 : navigation.getVisibility())
                        + " fallback="
                        + fallbackHeight(list)
                        + " activity="
                        + list.getContext().getClass().getName()
        );
    }

    private static boolean isBottomNavigationEnabled(Context context) {
        return android.preference.PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean("pref_bottom_navigation", false);
    }

    private static int resourceId(Context context, String name, String type) {
        Resources resources = context.getResources();
        int id = resources.getIdentifier(name, type, context.getPackageName());

        if (id != 0) {
            return id;
        }

        return resources.getIdentifier(name, type, "com.rubenmayayo.reddit");
    }
}
