/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.SubredditPaginator;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Shows an "End of feed" row below the last post when a feed has no more posts.
 *
 * When Reddit ends a listing ({@code after=null}), Boost asks for one more
 * page, gets nothing back and silently stops loading, so the feed looks broken
 * (issue #244). This is common on Home sorted by Best: Reddit builds Best as a
 * finite personalized set (a few hundred posts) and then ends it, while Hot and
 * New keep going.
 *
 * Boost's adapter has no footer, and its obfuscated RecyclerView API cannot be
 * used from here, so the row is a plain view: the list reserves room for it as
 * extra bottom padding after the last post, and the row is placed over that
 * room in the nearest FrameLayout above the list while the list is scrolled to
 * its end. It stays until the feed is refreshed or replaced.
 */
@SuppressWarnings("unused")
public final class BoostFeedEndFooter {
    private static final String TAG = "MorpheFeedEndFooter";
    private static final String MARKER = "MORPHE_BOOST_FEED_END_FOOTER_ISSUE244_V1";

    private static final String MESSAGE = "You've reached the end of this feed";
    private static final String MESSAGE_FRONT_PAGE_BEST =
            "You've reached the end of the Best feed.\nSort by Hot or New for more posts.";
    private static final String ACTION_REFRESH = "Refresh";

    private static final int FOOTER_HEIGHT_DP = 112;

    /** Paginator of the most recent submissions load. */
    private static volatile Paginator lastPaginator;

    private static final Map<View, Footer> FOOTERS = new WeakHashMap<>();

    private BoostFeedEndFooter() {
    }

    /** Called at the start of RedditJRAW.loadSubmissions(Paginator, boolean), off the main thread. */
    public static void onLoadPage(Paginator paginator) {
        lastPaginator = paginator;
    }

    /** Called at the start of SubmissionRecyclerViewFragment.appendSubmissions(ArrayList). */
    public static void onAppendPage(Object fragment, View list, List<?> page) {
        if (list == null || page == null) {
            return;
        }
        if (!page.isEmpty()) {
            hide(list);
            return;
        }

        Paginator paginator = lastPaginator;
        // An empty page with no further cursor: the listing ended, and Boost will not load again.
        if (paginator == null || paginator.hasNext()) {
            return;
        }

        boolean frontPageBest = isFrontPageBest(paginator);
        try {
            footerFor(list).show(fragment, frontPageBest ? MESSAGE_FRONT_PAGE_BEST : MESSAGE);
            Log.i(TAG, "listing ended, showing footer marker=" + MARKER
                    + " frontPageBest=" + frontPageBest);
        } catch (Throwable t) {
            Log.e(TAG, "showing end-of-feed footer failed", t);
        }
    }

    /** Called at the start of SubmissionRecyclerViewFragment.setSubmissions(ArrayList). */
    public static void onReplacePage(View list) {
        if (list != null) {
            hide(list);
        }
    }

    /** Extra bottom padding the list needs for a visible footer; read by BoostNavContentClearance. */
    public static int reservedHeight(View list) {
        Footer footer;
        synchronized (FOOTERS) {
            footer = FOOTERS.get(list);
        }
        return footer != null && footer.reserved ? footer.height : 0;
    }

    private static void hide(View list) {
        Footer footer;
        synchronized (FOOTERS) {
            footer = FOOTERS.get(list);
        }
        if (footer != null) {
            footer.hide();
        }
    }

    private static Footer footerFor(View list) {
        synchronized (FOOTERS) {
            Footer footer = FOOTERS.get(list);
            if (footer == null) {
                footer = new Footer(list);
                FOOTERS.put(list, footer);
            }
            return footer;
        }
    }

    private static final class Footer implements
            ViewTreeObserver.OnScrollChangedListener,
            ViewTreeObserver.OnGlobalLayoutListener {
        private final View list;
        private final int height;
        private LinearLayout row;
        private TextView message;
        private FrameLayout host;
        private ViewTreeObserver observer;
        private Object fragment;
        boolean reserved;

        Footer(View list) {
            this.list = list;
            this.height = dp(list.getContext(), FOOTER_HEIGHT_DP);
        }

        void show(Object fragment, String text) {
            this.fragment = fragment;
            if (row == null) {
                buildRow();
            }
            message.setText(text);

            if (row.getParent() == null) {
                host = findHost(list);
                if (host == null) {
                    Log.w(TAG, "no FrameLayout above the list marker=" + MARKER);
                    return;
                }
                host.addView(row, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP));
            }

            if (!reserved) {
                reserved = true;
                adjustBottomPadding(height);
            }

            if (observer == null || !observer.isAlive()) {
                observer = list.getViewTreeObserver();
                observer.addOnScrollChangedListener(this);
                observer.addOnGlobalLayoutListener(this);
            }
            position();
        }

        void hide() {
            if (!reserved) {
                return;
            }
            reserved = false;
            adjustBottomPadding(-height);
            if (observer != null && observer.isAlive()) {
                observer.removeOnScrollChangedListener(this);
                observer.removeOnGlobalLayoutListener(this);
            }
            observer = null;
            if (row != null && row.getParent() instanceof ViewGroup) {
                ((ViewGroup) row.getParent()).removeView(row);
            }
        }

        @Override
        public void onScrollChanged() {
            position();
        }

        @Override
        public void onGlobalLayout() {
            position();
        }

        /** Places the row over the reserved room after the last post, while it is on screen. */
        private void position() {
            if (row == null || host == null || !reserved) {
                return;
            }
            // The reserved room is only on screen once the list cannot scroll further down.
            boolean atEnd = list.isShown() && !list.canScrollVertically(1);
            row.setVisibility(atEnd ? View.VISIBLE : View.INVISIBLE);
            if (!atEnd) {
                return;
            }

            int[] listLocation = new int[2];
            int[] hostLocation = new int[2];
            list.getLocationInWindow(listLocation);
            host.getLocationInWindow(hostLocation);
            int roomTop = listLocation[1] - hostLocation[1]
                    + list.getHeight() - list.getPaddingBottom();
            row.setTranslationY(roomTop);
        }

        /**
         * BoostNavContentClearance re-applies the bar's height plus
         * {@link #reservedHeight(View)}, so both agree on the result.
         */
        private void adjustBottomPadding(int delta) {
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                    Math.max(0, list.getPaddingBottom() + delta));
        }

        private void buildRow() {
            Context context = list.getContext();
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER);
            int horizontal = dp(context, 16);
            row.setPadding(horizontal, dp(context, 8), horizontal, dp(context, 8));

            message = new TextView(context);
            message.setGravity(Gravity.CENTER);
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            message.setTextColor(themeColor(context, android.R.attr.textColorSecondary,
                    message.getCurrentTextColor()));
            row.addView(message, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Button refresh = new Button(context, null, android.R.attr.borderlessButtonStyle);
            refresh.setText(ACTION_REFRESH);
            refresh.setTextColor(themeColor(context, android.R.attr.colorAccent,
                    refresh.getCurrentTextColor()));
            refresh.setOnClickListener(v -> refresh());
            row.addView(refresh, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private void refresh() {
            Object target = fragment;
            hide();
            if (target == null) {
                return;
            }
            try {
                // SwipeRefreshLayout.OnRefreshListener.onRefresh(): reloads the feed from the top.
                target.getClass().getMethod("onRefresh").invoke(target);
            } catch (Throwable t) {
                Log.e(TAG, "refresh from end-of-feed footer failed", t);
            }
        }
    }

    /** The nearest FrameLayout above the list; SwipeRefreshLayout takes a single child. */
    private static FrameLayout findHost(View list) {
        ViewParent parent = list.getParent();
        while (parent instanceof View) {
            if (parent instanceof FrameLayout) {
                return (FrameLayout) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private static boolean isFrontPageBest(Paginator paginator) {
        return paginator instanceof SubredditPaginator
                && TextUtils.isEmpty(((SubredditPaginator) paginator).getSubreddit())
                && paginator.getSorting() == Sorting.BEST;
    }

    private static int themeColor(Context context, int attr, int fallback) {
        TypedArray values = context.obtainStyledAttributes(new int[]{attr});
        try {
            return values.getColor(0, fallback);
        } finally {
            values.recycle();
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
