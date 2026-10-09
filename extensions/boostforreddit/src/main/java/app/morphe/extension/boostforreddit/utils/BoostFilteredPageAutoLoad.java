/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Loads the next page when content-type filters remove every post on a page.
 *
 * Boost otherwise shows an indefinite "Results were filtered / Load next"
 * Snackbar. Its action is the only way to continue: no items were added, so
 * scrolling cannot trigger pagination, and swiping the Snackbar away leaves
 * the feed stuck (issue #179). After a bounded number of consecutive filtered
 * pages the native Snackbar is shown again, so heavily filtered feeds do not
 * page through the API indefinitely.
 */
@SuppressWarnings("unused")
public final class BoostFilteredPageAutoLoad {
    private static final String TAG = "MorpheFilteredAutoLoad";
    private static final String MARKER =
            "MORPHE_BOOST_FILTERED_PAGE_AUTOLOAD_ISSUE179_V1";

    /** Consecutive fully filtered pages loaded without user action. */
    static final int MAX_CONSECUTIVE_AUTO_LOADS = 5;

    /** A filtered page arriving later than this starts a new streak. */
    static final long STREAK_WINDOW_MS = 20_000L;

    private static final Map<View, long[]> STREAKS = new WeakHashMap<>();

    private BoostFilteredPageAutoLoad() {
    }

    /**
     * Called instead of showing the "Load next" Snackbar.
     *
     * @return true when the next page was scheduled and the Snackbar must be skipped.
     */
    public static boolean onPageFiltered(
            View view,
            View.OnClickListener loadNext
    ) {
        if (view == null || loadNext == null) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        long[] streak = STREAKS.get(view);

        if (streak == null || now - streak[1] > STREAK_WINDOW_MS) {
            streak = new long[]{0L, now};
            STREAKS.put(view, streak);
        }

        if (streak[0] >= MAX_CONSECUTIVE_AUTO_LOADS) {
            Log.i(
                    TAG,
                    "limit reached, showing native Snackbar marker="
                            + MARKER
                            + " pages="
                            + streak[0]
            );
            STREAKS.remove(view);
            return false;
        }

        streak[0]++;
        streak[1] = now;

        Log.i(
                TAG,
                "loading next page marker="
                        + MARKER
                        + " page="
                        + streak[0]
        );

        // Leave the presenter callback that delivered the filtered page first.
        view.post(() -> loadNext.onClick(view));
        return true;
    }
}
