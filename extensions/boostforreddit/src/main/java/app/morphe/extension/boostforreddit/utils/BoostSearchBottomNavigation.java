package app.morphe.extension.boostforreddit.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.WeakHashMap;

/**
 * Configures the BottomNavigationView inflated from activity_search_generic.
 * No Material view is constructed dynamically.
 */
public final class BoostSearchBottomNavigation {
    private static final String MARKER =
            "MORPHE_BOOST_SEARCH_BOTTOM_NAV_V7_XML_HOST";
    private static final String ALIGNMENT_MARKER =
            "MORPHE_BOOST_SEARCH_BOTTOM_NAV_V71_INSET_SELECTION";
    private static final String SYSTEM_SURFACE_MARKER =
            "MORPHE_BOOST_BOTTOM_NAV_SYSTEM_SURFACE_V72";
    private static final String DECOR_UNDERLAY_MARKER =
            "MORPHE_BOOST_BOTTOM_NAV_DECOR_UNDERLAY_V73";
    private static final String INBOX_PROFILE_LIFECYCLE_MARKER =
            "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LIFECYCLE_V74";
    private static final String INBOX_PROFILE_LATE_RETRY_MARKER =
            "MORPHE_BOOST_INBOX_PROFILE_UNDERLAY_LATE_RETRY_V74B";
    private static final String VISIBLE_NAV_FALLBACK_MARKER =
            "MORPHE_BOOST_VISIBLE_BOTTOM_NAV_FALLBACK_V751";
    private static volatile String SEARCH_ROUTE_MARKER =
            "MORPHE_BOOST_GOTO_SEARCH_ROUTE_V761";
    private static volatile String UNIFIED_MATERIAL_MARKER =
            "MORPHE_BOOST_UNIFIED_MATERIAL_BOTTOM_NAV_V771";
    private static final String CANONICAL_NAV_BASE_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V8";
    private static final String CANONICAL_NAV_SELECTION_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10001_HOME_SINGLE_TARGET";
    private static final String CANONICAL_NAV_FIXED_BLACK_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10002_FIXED_BLACK_ICONS";
    private static final String CANONICAL_NAV_THEME_CONTRAST_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10003_THEME_CONTRAST_ICONS";
    private static final String CANONICAL_NAV_HOME_GUARD_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10005_HOME_STATE_UNDERLAY_GUARD";
    private static final String CANONICAL_NAV_SUBREDDIT_SURFACE_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10007_SUBREDDIT_SCOPE_SURFACE";
    private static final String CANONICAL_NAV_ROUTE_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10008_SUBREDDIT_CANONICAL_ROUTES";
    private static final String CANONICAL_NAV_RENDERED_ICON_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10010_RENDERED_ICON_STATE";
    private static final String CANONICAL_NAV_PREVIOUS_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10011_REMOVE_HOME_UNDERLAY_LOOP";
    private static volatile String CANONICAL_NAV_MARKER =
            "MORPHE_BOOST_CANONICAL_BOTTOM_NAV_V10027_CUTOUT_SURFACE_FILL";
    private static final String HOME_RESELECT_TOP_MARKER =
            "MORPHE_BOOST_HOME_RESELECT_FAB_GO_TOP_V2";
    private static final java.util.WeakHashMap<View, Boolean>
            SUBREDDIT_SURFACE_GUARDS =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, Boolean>
            HOME_STABILITY_GUARDS =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, Boolean>
            MAIN_LANDSCAPE_INSET_GUARDS =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, Boolean>
            OWNED_MENU_INSTALLED =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Activity, FrameLayout>
            DECOR_NAVIGATION_CONTAINERS =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Activity, View>
            DECOR_NAVIGATION_VIEWS =
                    new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<Activity, Integer>
            INBOX_BADGE_COUNTS =
                    new java.util.WeakHashMap<>();
    private static final String DECOR_NAVIGATION_CONTAINER_TAG =
            "morphe_boost_decor_owned_navigation_container";
    private static final int UNDERLAY_RETRY_LIMIT = 24;
    private static final long UNDERLAY_RETRY_DELAY_MS = 75L;
    private static final String DECOR_UNDERLAY_TAG =
            "morphe_boost_bottom_navigation_decor_underlay";
    private static final String TAG = "MorpheSearchNav";

    private static final String SEARCH_ACTIVITY =
            "com.rubenmayayo.reddit.ui.search.SearchGenericActivity";
    private static final String GO_TO_ACTIVITY =
            "com.rubenmayayo.reddit.ui.search.GoToGenericActivity";

    private static final String MAIN_ACTIVITY =
            "com.rubenmayayo.reddit.ui.submissions.subreddit.MainActivity";
    private static final String SUBREDDIT_ACTIVITY =
            "com.rubenmayayo.reddit.ui.submissions.subreddit.SubredditActivity";
    private static final String SUBSCRIPTIONS_ACTIVITY =
            "com.rubenmayayo.reddit.ui.subscriptions.SubscriptionsActivity";
    private static final String MESSAGES_ACTIVITY =
            "com.rubenmayayo.reddit.ui.messages.MessagesActivity";

    private static final String USER_ACTIVITY =
            "com.rubenmayayo.reddit.ui.profile.UserActivity";
    private static final String NAVIGATION_UTILITY =
            "com.rubenmayayo.reddit.ui.activities.i";
    private static final String BASE_ACTIVITY =
            "com.rubenmayayo.reddit.ui.activities.b";
    private static final String ACCOUNT_MANAGER = "xb.l";

    private static final WeakHashMap<Activity, Boolean> INSTALLED =
            new WeakHashMap<>();

    private static volatile ColorStateList CANONICAL_ITEM_TINT;

    private BoostSearchBottomNavigation() {
    }

    public static void install(Activity activity) {
        if (!isTarget(activity)) {
            return;
        }

        int fallbackIndex = GO_TO_ACTIVITY.equals(
                activity.getClass().getName()
        ) ? 2 : 1;

        standardizeMaterialNavigation(
                activity,
                fallbackIndex
        );

        synchronized (INSTALLED) {
            INSTALLED.put(
                    activity,
                    Boolean.TRUE
            );
        }
    }

    private static void normalizeBottomInset(
            final View navigation
    ) {
        /*
         * activity_search_generic already ends above the gesture-navigation
         * area. Material NavigationBarView adds the same bottom inset once
         * more, moving the icons one system inset too high.
         */
        if (Build.VERSION.SDK_INT >= 21) {
            navigation.setOnApplyWindowInsetsListener(
                    new View.OnApplyWindowInsetsListener() {
                        @Override
                        public WindowInsets onApplyWindowInsets(
                                View view,
                                WindowInsets insets
                        ) {
                            view.setPadding(
                                    view.getPaddingLeft(),
                                    view.getPaddingTop(),
                                    view.getPaddingRight(),
                                    0
                            );
                            return insets;
                        }
                    }
            );
            navigation.requestApplyInsets();
        }

        navigation.post(new Runnable() {
            @Override
            public void run() {
                navigation.setPadding(
                        navigation.getPaddingLeft(),
                        navigation.getPaddingTop(),
                        navigation.getPaddingRight(),
                        0
                );
                navigation.requestLayout();
            }
        });
    }

    public static void syncSystemNavigationBar(
            final Activity activity
    ) {
        scheduleSystemNavigationBarSync(activity, 0);
    }

    private static void scheduleSystemNavigationBarSync(
            final Activity activity,
            final int attempt
    ) {
        if (
                activity == null
                        || activity.isFinishing()
                        || activity.isDestroyed()
        ) {
            return;
        }

        final View decor = activity
                .getWindow()
                .getDecorView();

        decor.post(new Runnable() {
            @Override
            public void run() {
                if (
                        activity.isFinishing()
                                || activity.isDestroyed()
                ) {
                    return;
                }

                final View navigation =
                        findReadyBottomNavigation(activity);

                boolean ready = navigation != null;

                if (ready) {
                    applyDecorUnderlay(activity, navigation);
                    Log.i(
                            TAG,
                            "late underlay dispatch marker="
                                    + INBOX_PROFILE_LATE_RETRY_MARKER
                                    + " activity="
                                    + activity.getClass().getName()
                                    + " attempt="
                                    + attempt
                    );
                    return;
                }

                if (attempt < UNDERLAY_RETRY_LIMIT) {
                    decor.postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    scheduleSystemNavigationBarSync(
                                            activity,
                                            attempt + 1
                                    );
                                }
                            },
                            UNDERLAY_RETRY_DELAY_MS
                    );
                    return;
                }

                Log.w(
                        TAG,
                        "late underlay timeout marker="
                                + INBOX_PROFILE_LATE_RETRY_MARKER
                                + " activity="
                                + activity.getClass().getName()
                                + " navigation="
                                + (navigation == null
                                    ? "null"
                                    : navigation.getClass().getName())
                                + " visible="
                                + (navigation != null
                                    && navigation.getVisibility() == View.VISIBLE)
                                + " attached="
                                + (navigation != null
                                    && navigation.isAttachedToWindow())
                                + " height="
                                + (navigation == null
                                    ? -1
                                    : navigation.getHeight())
                );
            }
        });
    }

    private static View findReadyBottomNavigation(
            Activity activity
    ) {
        View material = findViewByResourceName(
                activity,
                "bottom_navigation_view"
        );

        if (isReadyBottomNavigation(material)) {
            Log.i(
                    TAG,
                    "visible navigation selected marker="
                            + VISIBLE_NAV_FALLBACK_MARKER
                            + " kind=material"
                            + " class="
                            + material.getClass().getName()
                            + " id="
                            + material.getId()
                            + " height="
                            + material.getHeight()
            );
            return material;
        }

        View legacy = findViewByResourceName(
                activity,
                "bottom_navigation"
        );

        if (isReadyBottomNavigation(legacy)) {
            Log.i(
                    TAG,
                    "visible navigation selected marker="
                            + VISIBLE_NAV_FALLBACK_MARKER
                            + " kind=legacy"
                            + " class="
                            + legacy.getClass().getName()
                            + " id="
                            + legacy.getId()
                            + " height="
                            + legacy.getHeight()
            );
            return legacy;
        }

        return null;
    }

    private static View findViewByResourceName(
            Activity activity,
            String name
    ) {
        int id = resourceId(
                activity,
                name,
                "id"
        );

        return id == 0
                ? null
                : activity.findViewById(id);
    }

    private static boolean isReadyBottomNavigation(
            View navigation
    ) {
        return navigation != null
                && navigation.getVisibility() == View.VISIBLE
                && navigation.isAttachedToWindow()
                && navigation.getHeight() > 0;
    }

    private static void applyDecorUnderlay(
            Activity activity,
            View navigation
    ) {
        Integer surfaceColor =
                resolveNavigationSurfaceColor(
                        activity,
                        navigation
                );

        if (surfaceColor == null) {
            Log.w(
                    TAG,
                    "bottom-navigation surface unresolved"
            );
            return;
        }

        View decor = activity
                .getWindow()
                .getDecorView();

        if (!(decor instanceof ViewGroup)) {
            return;
        }

        int insetBottom =
                resolveNavigationInsetBottom(
                        activity,
                        decor
                );

        if (insetBottom <= 0) {
            return;
        }

        ViewGroup decorGroup = (ViewGroup) decor;
        View underlay =
                decor.findViewWithTag(
                        DECOR_UNDERLAY_TAG
                );

        if (underlay == null) {
            underlay = new View(activity);
            underlay.setTag(DECOR_UNDERLAY_TAG);
            underlay.setClickable(false);
            underlay.setFocusable(false);
            underlay.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );

            ViewGroup.LayoutParams params;

            if (decorGroup instanceof FrameLayout) {
                FrameLayout.LayoutParams frameParams =
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                insetBottom
                        );
                frameParams.gravity = Gravity.BOTTOM;
                params = frameParams;
            } else {
                params = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        insetBottom
                );
            }

            decorGroup.addView(underlay, params);
        } else {
            ViewGroup.LayoutParams params =
                    underlay.getLayoutParams();

            if (params.height != insetBottom) {
                params.height = insetBottom;
                underlay.setLayoutParams(params);
            }
        }

        underlay.setBackgroundColor(surfaceColor);
        underlay.setVisibility(View.VISIBLE);
        underlay.bringToFront();

        Log.i(
                TAG,
                "decor underlay applied marker="
                        + DECOR_UNDERLAY_MARKER
                        + " lifecycle="
                        + INBOX_PROFILE_LIFECYCLE_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " inset="
                        + insetBottom
                        + " color=#"
                        + String.format(
                                "%08X",
                                surfaceColor
                        )
        );
    }

    private static void hideDecorUnderlay(
            Activity activity
    ) {
        try {
            View underlay = activity
                    .getWindow()
                    .getDecorView()
                    .findViewWithTag(
                            DECOR_UNDERLAY_TAG
                    );

            if (underlay != null) {
                underlay.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int resolveNavigationInsetBottom(
            Activity activity,
            View decor
    ) {
        if (Build.VERSION.SDK_INT >= 23) {
            WindowInsets insets =
                    decor.getRootWindowInsets();

            if (insets != null) {
                if (Build.VERSION.SDK_INT >= 30) {
                    return insets.getInsets(
                            WindowInsets.Type.navigationBars()
                    ).bottom;
                }

                return insets
                        .getSystemWindowInsetBottom();
            }
        }

        int resourceId = activity
                .getResources()
                .getIdentifier(
                        "navigation_bar_height",
                        "dimen",
                        "android"
                );

        return resourceId != 0
                ? activity.getResources()
                    .getDimensionPixelSize(resourceId)
                : 0;
    }

    private static Integer resolveNavigationSurfaceColor(
            Activity activity,
            View navigation
    ) {
        try {
            ColorStateList tint =
                    navigation.getBackgroundTintList();

            if (tint != null) {
                return tint.getColorForState(
                        navigation.getDrawableState(),
                        tint.getDefaultColor()
                );
            }
        } catch (Throwable ignored) {
        }

        Drawable background =
                navigation.getBackground();

        if (background instanceof ColorDrawable) {
            return ((ColorDrawable) background)
                    .getColor();
        }

        if (background != null) {
            try {
                Method getFillColor = background
                        .getClass()
                        .getMethod("getFillColor");

                Object value =
                        getFillColor.invoke(background);

                if (value instanceof ColorStateList) {
                    ColorStateList fill =
                            (ColorStateList) value;

                    return fill.getColorForState(
                            navigation.getDrawableState(),
                            fill.getDefaultColor()
                    );
                }
            } catch (Throwable ignored) {
            }
        }

        int colorSurfaceAttr = activity
                .getResources()
                .getIdentifier(
                        "colorSurface",
                        "attr",
                        activity.getPackageName()
                );

        if (colorSurfaceAttr != 0) {
            TypedValue value = new TypedValue();

            if (
                    activity.getTheme()
                            .resolveAttribute(
                                    colorSurfaceAttr,
                                    value,
                                    true
                            )
                            && value.type
                            >= TypedValue.TYPE_FIRST_COLOR_INT
                            && value.type
                            <= TypedValue.TYPE_LAST_COLOR_INT
            ) {
                return value.data;
            }
        }

        int solidColor =
                navigation.getSolidColor();

        return solidColor != 0
                ? solidColor
                : null;
    }

    private static View findNativeMaterialNavigation(
            Activity activity
    ) {
        int id = resourceId(
                activity,
                "bottom_navigation_view",
                "id"
        );

        return id == 0
                ? null
                : activity.findViewById(id);
    }

    private static void suppressNativeNavigation(
            Activity activity,
            View nativeMaterial
    ) {
        if (nativeMaterial != null) {
            nativeMaterial.clearAnimation();
            nativeMaterial.animate().cancel();
            nativeMaterial.setEnabled(false);
            nativeMaterial.setClickable(false);
            nativeMaterial.setAlpha(0.0f);
            nativeMaterial.setVisibility(View.INVISIBLE);
        }

        int legacyId = resourceId(
                activity,
                "bottom_navigation",
                "id"
        );

        View legacy = legacyId == 0
                ? null
                : activity.findViewById(legacyId);

        if (legacy != null && legacy != nativeMaterial) {
            legacy.setEnabled(false);
            legacy.setClickable(false);
            legacy.setVisibility(View.GONE);
        }
    }

    private static int ownedNavigationHeight(
            Activity activity
    ) {
        int dimensionId = activity
                .getResources()
                .getIdentifier(
                        "design_bottom_navigation_height",
                        "dimen",
                        activity.getPackageName()
                );

        if (dimensionId != 0) {
            return activity
                    .getResources()
                    .getDimensionPixelSize(dimensionId);
        }

        return Math.round(
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        56.0f,
                        activity.getResources()
                                .getDisplayMetrics()
                )
        );
    }

    private static void updateDecorNavigationGeometry(
            final Activity activity,
            final FrameLayout container,
            final View navigation,
            WindowInsets insets
    ) {
        int bottomInset = insets == null
                ? resolveNavigationInsetBottom(
                        activity,
                        activity.getWindow().getDecorView()
                )
                : (
                    Build.VERSION.SDK_INT >= 30
                            ? insets.getInsets(
                                WindowInsets.Type.navigationBars()
                            ).bottom
                            : insets.getSystemWindowInsetBottom()
                );

        int navigationHeight = ownedNavigationHeight(activity);

        FrameLayout.LayoutParams containerParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        navigationHeight + bottomInset,
                        Gravity.BOTTOM
                );

        container.setLayoutParams(containerParams);
        container.setPadding(0, 0, 0, bottomInset);

        FrameLayout.LayoutParams navigationParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        navigationHeight,
                        Gravity.TOP
                );

        navigation.setLayoutParams(navigationParams);
        navigation.setTranslationY(0.0f);
        navigation.setAlpha(1.0f);
        navigation.setVisibility(View.VISIBLE);

        Log.i(
                TAG,
                "decor navigation geometry marker="
                        + CANONICAL_NAV_MARKER
                        + " navigationHeight="
                        + navigationHeight
                        + " bottomInset="
                        + bottomInset
                        + " activity="
                        + activity.getClass().getName()
        );
    }

    private static void normalizeOwnedNavigationInsets(
            final Activity activity,
            final View navigation
    ) {
        if (activity == null || navigation == null) {
            return;
        }

        final int basePaddingLeft = navigation.getPaddingLeft();
        final int basePaddingTop = navigation.getPaddingTop();
        final int basePaddingRight = navigation.getPaddingRight();

        navigation.setFitsSystemWindows(false);
        navigation.setPadding(
                basePaddingLeft,
                basePaddingTop,
                basePaddingRight,
                0
        );

        navigation.setOnApplyWindowInsetsListener(
                new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(
                            View view,
                            WindowInsets insets
                    ) {
                        if (
                                view.getPaddingLeft() != basePaddingLeft
                                        || view.getPaddingTop()
                                            != basePaddingTop
                                        || view.getPaddingRight()
                                            != basePaddingRight
                                        || view.getPaddingBottom() != 0
                        ) {
                            view.setPadding(
                                    basePaddingLeft,
                                    basePaddingTop,
                                    basePaddingRight,
                                    0
                            );
                        }

                        return insets;
                    }
                }
        );

        Log.i(
                TAG,
                "owned navigation child inset listener installed marker="
                        + CANONICAL_NAV_MARKER
                        + " activity="
                        + activity.getClass().getName()
        );
    }

    private static void fillLandscapeCutoutSurface(
            Activity activity,
            View donor
    ) {
        if (activity == null || donor == null) {
            return;
        }

        if (
                activity.getResources()
                        .getConfiguration()
                        .orientation
                        != android.content.res.Configuration
                            .ORIENTATION_LANDSCAPE
        ) {
            return;
        }

        try {
            Window window = activity.getWindow();

            if (window == null) {
                return;
            }

            Integer surfaceColor =
                    resolveNavigationSurfaceColor(
                            activity,
                            donor
                    );

            if (surfaceColor == null) {
                return;
            }

            android.graphics.drawable.ColorDrawable fill =
                    new android.graphics.drawable.ColorDrawable(
                            surfaceColor
                    );

            /*
             * Do not modify content/root padding and do not replace Boost's
             * native inset listener. Only color the area exposed outside
             * Boost's inset content bounds.
             */
            window.setBackgroundDrawable(fill);

            View decor = window.getDecorView();

            if (decor != null) {
                decor.setBackgroundColor(surfaceColor);
                decor.invalidate();
            }

            Log.i(
                    TAG,
                    "landscape cutout surface filled marker="
                            + CANONICAL_NAV_MARKER
                            + " color="
                            + surfaceColor
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "landscape cutout surface fill failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    public static void syncInboxBadge(
            Activity activity,
            int count
    ) {
        if (activity == null) {
            return;
        }

        int normalizedCount = Math.max(0, count);

        synchronized (INBOX_BADGE_COUNTS) {
            INBOX_BADGE_COUNTS.put(
                    activity,
                    normalizedCount
            );
        }

        View navigation;

        synchronized (DECOR_NAVIGATION_VIEWS) {
            navigation = DECOR_NAVIGATION_VIEWS.get(activity);
        }

        if (navigation == null) {
            Log.i(
                    TAG,
                    "inbox badge count stored marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                            + " count="
                            + normalizedCount
                            + " activity="
                            + activity.getClass().getName()
            );
            return;
        }

        try {
            applyInboxBadge(
                    activity,
                    navigation,
                    normalizedCount
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "inbox badge synchronization failed marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                            + " count="
                            + normalizedCount,
                    error
            );
        }
    }

    private static void applyStoredInboxBadge(
            Activity activity,
            View navigation
    ) {
        Integer count;

        synchronized (INBOX_BADGE_COUNTS) {
            count = INBOX_BADGE_COUNTS.get(activity);
        }

        if (count == null) {
            return;
        }

        try {
            applyInboxBadge(
                    activity,
                    navigation,
                    count
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "stored inbox badge application failed marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK",
                    error
            );
        }
    }

    private static void applyInboxBadge(
            Activity activity,
            View navigation,
            int count
    ) throws Exception {
        int inboxId = resourceId(
                activity,
                "item_inbox",
                "id"
        );

        if (inboxId == 0) {
            throw new IllegalStateException(
                    "item_inbox resource unavailable"
            );
        }

        if (count <= 0) {
            Method removeBadge = findMethod(
                    navigation.getClass(),
                    "g",
                    int.class
            );

            if (removeBadge == null) {
                removeBadge = findMethod(
                        navigation.getClass(),
                        "removeBadge",
                        int.class
                );
            }

            if (removeBadge == null) {
                throw new NoSuchMethodException(
                        "NavigationBarView.removeBadge"
                );
            }

            removeBadge.setAccessible(true);
            removeBadge.invoke(
                    navigation,
                    inboxId
            );

            Log.i(
                    TAG,
                    "inbox badge removed marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                            + " activity="
                            + activity.getClass().getName()
            );
            return;
        }

        Method getOrCreateBadge = findMethod(
                navigation.getClass(),
                "e",
                int.class
        );

        if (getOrCreateBadge == null) {
            getOrCreateBadge = findMethod(
                    navigation.getClass(),
                    "getOrCreateBadge",
                    int.class
            );
        }

        if (getOrCreateBadge == null) {
            throw new NoSuchMethodException(
                    "NavigationBarView.getOrCreateBadge"
            );
        }

        getOrCreateBadge.setAccessible(true);

        Object badge = getOrCreateBadge.invoke(
                navigation,
                inboxId
        );

        if (badge == null) {
            throw new IllegalStateException(
                    "Inbox BadgeDrawable unavailable"
            );
        }

        Method setNumber = findMethod(
                badge.getClass(),
                "z",
                int.class
        );

        if (setNumber == null) {
            setNumber = findMethod(
                    badge.getClass(),
                    "setNumber",
                    int.class
            );
        }

        Method setVisible = findMethod(
                badge.getClass(),
                "C",
                boolean.class
        );

        if (setVisible == null) {
            setVisible = findMethod(
                    badge.getClass(),
                    "setVisible",
                    boolean.class
            );
        }

        if (setNumber == null || setVisible == null) {
            throw new NoSuchMethodException(
                    "BadgeDrawable number/visibility API"
            );
        }

        setNumber.setAccessible(true);
        setVisible.setAccessible(true);

        setNumber.invoke(
                badge,
                count
        );

        setVisible.invoke(
                badge,
                true
        );

        Log.i(
                TAG,
                "inbox badge synchronized marker=MORPHE_BOOST_INBOX_BADGE_SYNC_V2_ENTRY_HOOK"
                        + " count="
                        + count
                        + " activity="
                        + activity.getClass().getName()
        );
    }

    private static View ensureDecorOwnedNavigation(
            final Activity activity,
            final View nativeMaterial,
            boolean visible
    ) throws Exception {
        if (activity == null || nativeMaterial == null) {
            throw new IllegalStateException(
                    "Native Material navigation donor unavailable"
            );
        }

        suppressNativeNavigation(
                activity,
                nativeMaterial
        );

        final View decor = activity
                .getWindow()
                .getDecorView();

        if (!(decor instanceof ViewGroup)) {
            throw new IllegalStateException(
                    "Window decor is not a ViewGroup"
            );
        }

        FrameLayout container;
        View navigation;

        synchronized (DECOR_NAVIGATION_CONTAINERS) {
            container = DECOR_NAVIGATION_CONTAINERS.get(activity);
            navigation = DECOR_NAVIGATION_VIEWS.get(activity);
        }

        if (container == null || navigation == null) {
            Class<?> navigationClass = Class.forName(
                    "com.google.android.material.bottomnavigation.BottomNavigationView",
                    true,
                    activity.getClassLoader()
            );

            Object instance = navigationClass
                    .getConstructor(
                            Context.class,
                            android.util.AttributeSet.class
                    )
                    .newInstance(
                            activity,
                            null
                    );

            if (!(instance instanceof View)) {
                throw new IllegalStateException(
                        "Constructed BottomNavigationView is not a View"
                );
            }

            navigation = (View) instance;
            navigation.setId(View.generateViewId());
            navigation.setEnabled(true);
            navigation.setClickable(true);
            navigation.setAlpha(1.0f);
            navigation.setVisibility(View.VISIBLE);
            navigation.setElevation(nativeMaterial.getElevation());
            normalizeOwnedNavigationInsets(
                    activity,
                    navigation
            );


            fillLandscapeCutoutSurface(
                    activity,
                    nativeMaterial
            );
            Integer surfaceColor = resolveNavigationSurfaceColor(
                    activity,
                    nativeMaterial
            );

            if (surfaceColor != null) {
                navigation.setBackgroundColor(surfaceColor);
            }

            container = new FrameLayout(activity);
            container.setId(View.generateViewId());
            container.setTag(DECOR_NAVIGATION_CONTAINER_TAG);
            container.setClipChildren(false);
            container.setClipToPadding(false);
            container.setFitsSystemWindows(false);
            container.setElevation(nativeMaterial.getElevation());

            if (surfaceColor != null) {
                container.setBackgroundColor(surfaceColor);
            }

            final FrameLayout stableContainer = container;
            final View stableNavigation = navigation;

            container.setOnApplyWindowInsetsListener(
                    new View.OnApplyWindowInsetsListener() {
                        @Override
                        public WindowInsets onApplyWindowInsets(
                                View view,
                                WindowInsets insets
                        ) {
                            updateDecorNavigationGeometry(
                                    activity,
                                    stableContainer,
                                    stableNavigation,
                                    insets
                            );
                            return insets;
                        }
                    }
            );

            container.addView(navigation);

            View divider = new View(activity);
            divider.setBackgroundColor(
                    canonicalIconColor(navigation) == Color.WHITE
                            ? 0x33FFFFFF
                            : 0x22000000
            );

            FrameLayout.LayoutParams dividerParams =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.max(
                                    1,
                                    Math.round(
                                            TypedValue.applyDimension(
                                                    TypedValue.COMPLEX_UNIT_DIP,
                                                    1.0f,
                                                    activity.getResources()
                                                            .getDisplayMetrics()
                                            )
                                    )
                            ),
                            Gravity.TOP
                    );

            container.addView(
                    divider,
                    dividerParams
            );

            ((ViewGroup) decor).addView(
                    container,
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM
                    )
            );

            synchronized (DECOR_NAVIGATION_CONTAINERS) {
                DECOR_NAVIGATION_CONTAINERS.put(
                        activity,
                        container
                );
                DECOR_NAVIGATION_VIEWS.put(
                        activity,
                        navigation
                );
            }

            WindowInsets initialInsets =
                    Build.VERSION.SDK_INT >= 23
                            ? decor.getRootWindowInsets()
                            : null;

            updateDecorNavigationGeometry(
                    activity,
                    container,
                    navigation,
                    initialInsets
            );

            if (Build.VERSION.SDK_INT >= 21) {
                container.requestApplyInsets();
            }

            Log.i(
                    TAG,
                    "decor-owned navigation created marker="
                            + CANONICAL_NAV_MARKER
                            + " activity="
                            + activity.getClass().getName()
                            + " nativeSlotPreserved=true"
            );
        }

        Integer surfaceColor = resolveNavigationSurfaceColor(
                activity,
                nativeMaterial
        );

        if (surfaceColor != null) {
            container.setBackgroundColor(surfaceColor);
            navigation.setBackgroundColor(surfaceColor);
        }

        installOwnedCanonicalMenu(
                activity,
                navigation
        );

        applyStoredInboxBadge(
                activity,
                navigation
        );

        container.setVisibility(
                visible
                        ? View.VISIBLE
                        : View.GONE
        );

        if (visible) {
            container.bringToFront();
            container.requestLayout();
            container.invalidate();
            navigation.requestLayout();
            navigation.invalidate();
        }

        return navigation;
    }

    private static void installMainLandscapeCutoutInsetFix(
            final Activity activity
    ) {
        if (activity == null) {
            return;
        }

        if (
                activity.getResources()
                        .getConfiguration()
                        .orientation
                        != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) {
            Log.i(
                    TAG,
                    "landscape horizontal padding fix skipped marker="
                            + CANONICAL_NAV_MARKER
                            + " mode=portrait-native-only"
                            + " activity="
                            + activity.getClass().getName()
            );
            return;
        }

        try {
            View content = activity.findViewById(android.R.id.content);

            if (!(content instanceof ViewGroup)) {
                return;
            }

            ViewGroup group = (ViewGroup) content;

            if (group.getChildCount() == 0) {
                return;
            }

            final View root = group.getChildAt(0);

            synchronized (MAIN_LANDSCAPE_INSET_GUARDS) {
                if (MAIN_LANDSCAPE_INSET_GUARDS.containsKey(root)) {
                    return;
                }

                MAIN_LANDSCAPE_INSET_GUARDS.put(
                        root,
                        Boolean.TRUE
                );
            }

            final Runnable normalizeHorizontalPadding =
                    new Runnable() {
                        @Override
                        public void run() {
                            if (
                                    activity.getResources()
                                            .getConfiguration()
                                            .orientation
                                            != android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            ) {
                                return;
                            }

                            int top = root.getPaddingTop();
                            int bottom = root.getPaddingBottom();

                            if (
                                    root.getPaddingLeft() != 0
                                            || root.getPaddingRight() != 0
                            ) {
                                int oldLeft = root.getPaddingLeft();
                                int oldRight = root.getPaddingRight();

                                root.setPadding(
                                        0,
                                        top,
                                        0,
                                        bottom
                                );

                                Log.i(
                                        TAG,
                                        "landscape horizontal root padding normalized marker="
                                                + CANONICAL_NAV_MARKER
                                                + " oldLeft="
                                                + oldLeft
                                                + " oldRight="
                                                + oldRight
                                                + " topPreserved="
                                                + top
                                                + " bottomPreserved="
                                                + bottom
                                                + " activity="
                                                + activity
                                                    .getClass()
                                                    .getName()
                                );
                            }
                        }
                    };

            root.addOnLayoutChangeListener(
                    new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(
                                View view,
                                int left,
                                int top,
                                int right,
                                int bottom,
                                int oldLeft,
                                int oldTop,
                                int oldRight,
                                int oldBottom
                        ) {
                            normalizeHorizontalPadding.run();
                        }
                    }
            );

            root.post(normalizeHorizontalPadding);

            Log.i(
                    TAG,
                    "landscape horizontal padding observer installed marker="
                            + CANONICAL_NAV_MARKER
                            + " mode=preserve-native-inset-listener"
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "landscape horizontal padding fix failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    public static void enforceMaterialOnlyVisibility(
            Activity activity,
            boolean visible
    ) {
        if (activity == null) {
            return;
        }

        try {
            View nativeMaterial =
                    findNativeMaterialNavigation(activity);

            if (nativeMaterial == null) {
                throw new IllegalStateException(
                        "bottom_navigation_view absent"
                );
            }

            installMainLandscapeCutoutInsetFix(activity);

            View navigation = ensureDecorOwnedNavigation(
                    activity,
                    nativeMaterial,
                    true
            );

            Log.i(
                    TAG,
                    "decor-owned visibility marker="
                            + CANONICAL_NAV_MARKER
                            + " nativeRequestedVisible="
                            + visible
                            + " forcedVisible=true"
                            + " navigationVisible="
                            + navigation.getVisibility()
                            + " activity="
                            + activity.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "decor-owned visibility failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    private static void lockOwnedNavigationHost(
            View navigation
    ) {
        if (navigation == null) {
            return;
        }

        try {
            navigation.clearAnimation();
            navigation.animate().cancel();
            navigation.setTranslationY(0.0f);
            navigation.setAlpha(1.0f);

            ViewGroup.LayoutParams params = navigation.getLayoutParams();
            String behaviorBefore = "none";

            if (params != null) {
                for (Method method : params.getClass().getMethods()) {
                    if (
                            "getBehavior".equals(method.getName())
                                    && method.getParameterTypes().length == 0
                    ) {
                        Object behavior = method.invoke(params);
                        if (behavior != null) {
                            behaviorBefore = behavior.getClass().getName();
                        }
                        break;
                    }
                }

                for (Method method : params.getClass().getMethods()) {
                    if (
                            "setBehavior".equals(method.getName())
                                    && method.getParameterTypes().length == 1
                    ) {
                        method.invoke(params, new Object[]{null});
                        navigation.setLayoutParams(params);
                        break;
                    }
                }
            }

            navigation.setTranslationY(0.0f);
            navigation.requestLayout();
            navigation.invalidate();

            Log.i(
                    TAG,
                    "owned navigation host locked marker="
                            + CANONICAL_NAV_MARKER
                            + " behaviorBefore="
                            + behaviorBefore
                            + " translationY="
                            + navigation.getTranslationY()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "owned navigation host lock failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    private static void installOwnedCanonicalMenu(
            Activity activity,
            View navigation
    ) throws Exception {
        Method getMenu = findMethod(
                navigation.getClass(),
                "getMenu"
        );

        if (getMenu == null) {
            throw new NoSuchMethodException(
                    "BottomNavigationView.getMenu"
            );
        }

        getMenu.setAccessible(true);
        Object menuObject = getMenu.invoke(navigation);

        if (!(menuObject instanceof Menu)) {
            throw new IllegalStateException(
                    "BottomNavigationView menu unavailable"
            );
        }

        Menu menu = (Menu) menuObject;

        boolean rebuildOwnedMenu;

        synchronized (OWNED_MENU_INSTALLED) {
            rebuildOwnedMenu =
                    !OWNED_MENU_INSTALLED.containsKey(navigation);
        }

        if (rebuildOwnedMenu) {
            menu.clear();

            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_home",
                    "home",
                    "ic_home_round_24dp",
                    0
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_search",
                    "search",
                    "ic_search_24dp",
                    1
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_subs",
                    "title_activity_subscriptions",
                    "ic_list_24dp",
                    2
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_inbox",
                    "title_activity_inbox",
                    "ic_email_24dp",
                    3
            );
            addOwnedMenuItem(
                    activity,
                    menu,
                    "item_profile",
                    "action_profile",
                    "ic_person_24dp",
                    4
            );

            menu.setGroupCheckable(
                    0,
                    true,
                    true
            );

            synchronized (OWNED_MENU_INSTALLED) {
                OWNED_MENU_INSTALLED.put(
                        navigation,
                        Boolean.TRUE
                );
            }
        }

        invokeOptional(
                navigation,
                "setLabelVisibilityMode",
                new Class<?>[]{int.class},
                new Object[]{2}
        );

        Log.i(
                TAG,
                "Issue #54 shared Search host scoped marker="
                        + "MORPHE_BOOST_ISSUE54_SHARED_HOST_SCOPE_V8"
        );
        invokeOptional(
                navigation,
                "setItemHorizontalTranslationEnabled",
                new Class<?>[]{boolean.class},
                new Object[]{false}
        );

        int selectedItemId =
                selectedItemIdForActivity(
                        activity,
                        0
                );

        setCheckedItem(
                navigation,
                selectedItemId
        );
        applyCanonicalTint(navigation);

        String listeners =
                attachSelectedListener(
                        activity,
                        navigation
                )
                + "+"
                + attachReselectedListener(
                        activity,
                        navigation
                );

        Log.i(
                TAG,
                "owned canonical menu installed marker="
                        + CANONICAL_NAV_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " items="
                        + menu.size()
                        + " rebuilt="
                        + rebuildOwnedMenu
                        + " selectedItemId="
                        + selectedItemId
                        + " listeners="
                        + listeners
        );
    }

    private static void addOwnedMenuItem(
            Activity activity,
            Menu menu,
            String itemIdName,
            String titleName,
            String iconName,
            int order
    ) {
        int itemId = resourceId(
                activity,
                itemIdName,
                "id"
        );
        int titleId = resourceId(
                activity,
                titleName,
                "string"
        );
        int iconId = resourceId(
                activity,
                iconName,
                "drawable"
        );

        if (itemId == 0 || titleId == 0 || iconId == 0) {
            throw new IllegalStateException(
                    "Owned menu resource unresolved: "
                            + itemIdName
                            + "/"
                            + titleName
                            + "/"
                            + iconName
            );
        }

        MenuItem item = menu.add(
                0,
                itemId,
                order,
                activity.getString(titleId)
        );
        item.setIcon(iconId);
        item.setEnabled(true);
        item.setCheckable(true);
    }

    public static void standardizeHome(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                0
        );
    }

    public static void standardizeSubreddit(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                0
        );
    }

    public static void standardizeInbox(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                3
        );
    }

    public static void standardizeProfile(
            Activity activity
    ) {
        standardizeMaterialNavigation(
                activity,
                4
        );
    }

    public static void standardizeMaterialNavigation(
            final Activity activity,
            int fallbackIndex
    ) {
        if (activity == null) {
            return;
        }

        try {
            View nativeMaterial =
                    findNativeMaterialNavigation(activity);

            if (nativeMaterial == null) {
                throw new IllegalStateException(
                        "bottom_navigation_view absent"
                );
            }

            installMainLandscapeCutoutInsetFix(activity);

            View navigation = ensureDecorOwnedNavigation(
                    activity,
                    nativeMaterial,
                    true
            );

            int selectedItemId = selectedItemIdForActivity(
                    activity,
                    fallbackIndex
            );

            if (selectedItemId == 0) {
                throw new IllegalStateException(
                        "Selected destination unresolved"
                );
            }

            setCheckedItem(
                    navigation,
                    selectedItemId
            );
            applyCanonicalTint(navigation);
            attachSelectedListener(
                    activity,
                    navigation
            );
            attachReselectedListener(
                    activity,
                    navigation
            );

            navigation.setTranslationY(0.0f);
            navigation.setAlpha(1.0f);
            navigation.setVisibility(View.VISIBLE);
            navigation.requestLayout();
            navigation.invalidate();

            syncSystemNavigationBar(activity);

            Log.i(
                    TAG,
                    "decor-owned canonical navigation marker="
                            + CANONICAL_NAV_MARKER
                            + " activity="
                            + activity.getClass().getName()
                            + " selectedItemId="
                            + selectedItemId
                            + " fallbackIndex="
                            + fallbackIndex
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "decor-owned canonical navigation failed marker="
                            + CANONICAL_NAV_MARKER
                            + " activity="
                            + (
                                activity == null
                                        ? "null"
                                        : activity.getClass().getName()
                            ),
                    error
            );
        }
    }

    private static int selectedItemIdForActivity(
            Activity activity,
            int fallbackIndex
    ) {
        String activityName =
                activity.getClass().getName();

        if (MAIN_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_home",
                    "id"
            );
        }

        if (SEARCH_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_search",
                    "id"
            );
        }

        if (
                GO_TO_ACTIVITY.equals(activityName)
                        || SUBSCRIPTIONS_ACTIVITY.equals(
                                activityName
                        )
        ) {
            return resourceId(
                    activity,
                    "item_subs",
                    "id"
            );
        }

        if (MESSAGES_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_inbox",
                    "id"
            );
        }

        if (USER_ACTIVITY.equals(activityName)) {
            return resourceId(
                    activity,
                    "item_profile",
                    "id"
            );
        }

        switch (fallbackIndex) {
            case 0:
                return resourceId(
                        activity,
                        "item_home",
                        "id"
                );
            case 1:
                return resourceId(
                        activity,
                        "item_search",
                        "id"
                );
            case 2:
                return resourceId(
                        activity,
                        "item_subs",
                        "id"
                );
            case 3:
                return resourceId(
                        activity,
                        "item_inbox",
                        "id"
                );
            case 4:
                return resourceId(
                        activity,
                        "item_profile",
                        "id"
                );
            default:
                return 0;
        }
    }

    private static void setCheckedItem(
            View navigation,
            int selectedItemId
    ) throws Exception {
        Method getMenu = findMethod(
                navigation.getClass(),
                "getMenu"
        );

        if (getMenu == null) {
            throw new NoSuchMethodException(
                    "BottomNavigationView.getMenu"
            );
        }

        getMenu.setAccessible(true);

        Object menuObject =
                getMenu.invoke(navigation);

        if (!(menuObject instanceof Menu)) {
            throw new IllegalStateException(
                    "BottomNavigationView menu unavailable"
            );
        }

        Menu menu = (Menu) menuObject;
        MenuItem selectedItem =
                menu.findItem(selectedItemId);

        if (selectedItem == null) {
            throw new IllegalStateException(
                    "Selected menu item not found: "
                            + selectedItemId
            );
        }

        selectedItem.setCheckable(true);
        selectedItem.setChecked(true);

        Log.i(
                TAG,
                "single target checked marker="
                        + CANONICAL_NAV_MARKER
                        + " selectedItemId="
                        + selectedItemId
        );
    }

    private static void attachHomeStabilityGuard(
            final Activity activity,
            final View navigation,
            final int selectedItemId
    ) {
        if (
                activity == null
                        || navigation == null
                        || !MAIN_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return;
        }

        synchronized (HOME_STABILITY_GUARDS) {
            if (
                    Boolean.TRUE.equals(
                            HOME_STABILITY_GUARDS.get(navigation)
                    )
            ) {
                return;
            }

            HOME_STABILITY_GUARDS.put(
                    navigation,
                    Boolean.TRUE
            );
        }

        final android.view.ViewTreeObserver observer =
                navigation.getViewTreeObserver();

        final android.view.ViewTreeObserver.OnPreDrawListener[]
                listenerHolder =
                        new android.view.ViewTreeObserver
                                .OnPreDrawListener[1];

        listenerHolder[0] =
                new android.view.ViewTreeObserver
                        .OnPreDrawListener() {
                    private boolean repairing;

                    @Override
                    public boolean onPreDraw() {
                        if (
                                repairing
                                        || activity.isFinishing()
                                        || activity.isDestroyed()
                                        || !navigation
                                            .isAttachedToWindow()
                        ) {
                            return true;
                        }

                        boolean navigationMismatch =
                                navigation.getVisibility()
                                        != View.VISIBLE
                                        || navigation.getAlpha()
                                            != 1.0f
                                        || !navigation.isEnabled();

                        boolean selectionMismatch =
                                !canonicalSelectionMatches(
                                        navigation,
                                        selectedItemId
                                );

                        boolean tintMismatch =
                                !canonicalTintMatches(navigation);

                        if (
                                !navigationMismatch
                                        && !selectionMismatch
                                        && !tintMismatch
                        ) {
                            return true;
                        }

                        repairing = true;

                        try {
                            repairHomeCanonicalState(
                                    activity,
                                    navigation,
                                    selectedItemId,
                                    navigationMismatch,
                                    selectionMismatch,
                                    tintMismatch
                            );
                        } finally {
                            repairing = false;
                        }

                        return true;
                    }
                };

        if (observer.isAlive()) {
            observer.addOnPreDrawListener(
                    listenerHolder[0]
            );
        }

        navigation.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(
                            View view
                    ) {
                    }

                    @Override
                    public void onViewDetachedFromWindow(
                            View view
                    ) {
                        try {
                            if (observer.isAlive()) {
                                observer.removeOnPreDrawListener(
                                        listenerHolder[0]
                                );
                            }
                        } catch (Throwable ignored) {
                        }

                        synchronized (HOME_STABILITY_GUARDS) {
                            HOME_STABILITY_GUARDS.remove(view);
                        }

                        view.removeOnAttachStateChangeListener(this);
                    }
                }
        );

        navigation.post(
                new Runnable() {
                    @Override
                    public void run() {
                        repairHomeCanonicalState(
                                activity,
                                navigation,
                                selectedItemId,
                                false,
                                !canonicalSelectionMatches(
                                        navigation,
                                        selectedItemId
                                ),
                                !canonicalTintMatches(navigation)
                        );
                    }
                }
        );

        Log.i(
                TAG,
                "Home stability guard attached marker="
                        + CANONICAL_NAV_MARKER
                        + " activity="
                        + activity.getClass().getName()
                        + " selectedItemId="
                        + selectedItemId
        );
    }

    private static void repairHomeCanonicalState(
            Activity activity,
            View navigation,
            int selectedItemId,
            boolean navigationMismatch,
            boolean selectionMismatch,
            boolean tintMismatch
    ) {
        try {
            if (navigationMismatch) {
                navigation.setVisibility(View.VISIBLE);
                navigation.setAlpha(1.0f);
                navigation.setEnabled(true);
            }

            if (selectionMismatch) {
                setCheckedItem(
                        navigation,
                        selectedItemId
                );
            }

            if (tintMismatch) {
                applyCanonicalTint(navigation);
            }

            if (
                    navigationMismatch
                            || selectionMismatch
                            || tintMismatch
            ) {
                navigation.bringToFront();
                navigation.invalidate();

                Log.w(
                        TAG,
                        "Home stability guard repaired marker="
                                + CANONICAL_NAV_MARKER
                                + " navigation="
                                + navigationMismatch
                                + " selection="
                                + selectionMismatch
                                + " tint="
                                + tintMismatch
                                + " activity="
                                + activity.getClass().getName()
                );
            }
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Home stability guard repair failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    private static boolean canonicalSelectionMatches(
            View navigation,
            int selectedItemId
    ) {
        try {
            Method getMenu = findMethod(
                    navigation.getClass(),
                    "getMenu"
            );

            if (getMenu == null) {
                return false;
            }

            getMenu.setAccessible(true);

            Object menuObject =
                    getMenu.invoke(navigation);

            if (!(menuObject instanceof Menu)) {
                return false;
            }

            MenuItem selectedItem =
                    ((Menu) menuObject).findItem(
                            selectedItemId
                    );

            return selectedItem != null
                    && selectedItem.isChecked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int applyRenderedItemIconAppearance(
            View navigation,
            android.content.res.ColorStateList contrastTint,
            int iconColor
    ) throws Exception {
        Method getMenu = findMethod(
                navigation.getClass(),
                "getMenu"
        );

        if (getMenu == null) {
            throw new NoSuchMethodException(
                    "BottomNavigationView.getMenu"
            );
        }

        getMenu.setAccessible(true);

        Object menuObject =
                getMenu.invoke(navigation);

        if (!(menuObject instanceof Menu)) {
            throw new IllegalStateException(
                    "BottomNavigationView menu unavailable"
            );
        }

        Menu menu = (Menu) menuObject;
        int renderedIconCount = 0;

        for (int index = 0; index < menu.size(); index++) {
            MenuItem item = menu.getItem(index);

            if (item == null) {
                continue;
            }

            View itemView =
                    navigation.findViewById(
                            item.getItemId()
                    );

            if (itemView == null) {
                continue;
            }

            itemView.setAlpha(1.0f);

            invokeOptional(
                    itemView,
                    "setIconTintList",
                    new Class<?>[]{
                        android.content.res.ColorStateList.class
                    },
                    new Object[]{contrastTint}
            );

            renderedIconCount +=
                    applyRenderedImagesRecursively(
                            itemView,
                            itemView,
                            contrastTint,
                            iconColor
                    );
        }

        navigation.invalidate();
        return renderedIconCount;
    }

    private static int applyRenderedImagesRecursively(
            View root,
            View itemRoot,
            android.content.res.ColorStateList contrastTint,
            int iconColor
    ) {
        int count = 0;

        if (root instanceof android.widget.ImageView) {
            android.widget.ImageView imageView =
                    (android.widget.ImageView) root;

            imageView.setAlpha(1.0f);
            imageView.setImageTintList(contrastTint);

            android.graphics.drawable.Drawable drawable =
                    imageView.getDrawable();

            if (drawable != null) {
                android.graphics.drawable.Drawable mutable =
                        drawable.mutate();

                mutable.setTint(iconColor);
                mutable.setColorFilter(
                        iconColor,
                        android.graphics.PorterDuff.Mode.SRC_IN
                );

                imageView.setImageDrawable(mutable);
            }

            android.view.ViewParent parent =
                    imageView.getParent();

            while (
                    parent instanceof View
                            && parent != itemRoot
            ) {
                ((View) parent).setAlpha(1.0f);
                parent = parent.getParent();
            }

            count++;
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;

            for (
                    int index = 0;
                    index < group.getChildCount();
                    index++
            ) {
                count +=
                        applyRenderedImagesRecursively(
                                group.getChildAt(index),
                                itemRoot,
                                contrastTint,
                                iconColor
                        );
            }
        }

        return count;
    }

    private static boolean canonicalRenderedItemIconsMatch(
            View navigation,
            int expectedColor
    ) {
        try {
            Method getMenu = findMethod(
                    navigation.getClass(),
                    "getMenu"
            );

            if (getMenu == null) {
                return false;
            }

            getMenu.setAccessible(true);

            Object menuObject =
                    getMenu.invoke(navigation);

            if (!(menuObject instanceof Menu)) {
                return false;
            }

            Menu menu = (Menu) menuObject;
            int matchedItems = 0;

            for (
                    int index = 0;
                    index < menu.size();
                    index++
            ) {
                MenuItem item = menu.getItem(index);

                if (item == null) {
                    return false;
                }

                View itemView =
                        navigation.findViewById(
                                item.getItemId()
                        );

                if (
                        itemView == null
                                || itemView.getAlpha() != 1.0f
                ) {
                    return false;
                }

                int[] imageCount = new int[]{0};

                if (
                        !renderedImagesMatchRecursively(
                                itemView,
                                itemView,
                                expectedColor,
                                imageCount
                        )
                                || imageCount[0] == 0
                ) {
                    return false;
                }

                matchedItems++;
            }

            return matchedItems == menu.size()
                    && matchedItems == 5;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean renderedImagesMatchRecursively(
            View root,
            View itemRoot,
            int expectedColor,
            int[] imageCount
    ) {
        if (root instanceof android.widget.ImageView) {
            android.widget.ImageView imageView =
                    (android.widget.ImageView) root;

            imageCount[0]++;

            if (imageView.getAlpha() != 1.0f) {
                return false;
            }

            android.content.res.ColorStateList tint =
                    imageView.getImageTintList();

            if (
                    tint == null
                            || tint.getDefaultColor()
                                != expectedColor
            ) {
                return false;
            }

            android.view.ViewParent parent =
                    imageView.getParent();

            while (
                    parent instanceof View
                            && parent != itemRoot
            ) {
                if (((View) parent).getAlpha() != 1.0f) {
                    return false;
                }

                parent = parent.getParent();
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;

            for (
                    int index = 0;
                    index < group.getChildCount();
                    index++
            ) {
                if (
                        !renderedImagesMatchRecursively(
                                group.getChildAt(index),
                                itemRoot,
                                expectedColor,
                                imageCount
                        )
                ) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean canonicalTintMatches(
            View navigation
    ) {
        try {
            Method getItemIconTintList = findMethod(
                    navigation.getClass(),
                    "getItemIconTintList"
            );

            if (getItemIconTintList == null) {
                return false;
            }

            getItemIconTintList.setAccessible(true);

            Object tintObject =
                    getItemIconTintList.invoke(navigation);

            if (
                    !(tintObject
                            instanceof android.content.res
                                    .ColorStateList)
            ) {
                return false;
            }

            android.content.res.ColorStateList tint =
                    (android.content.res.ColorStateList)
                            tintObject;

            int expectedColor =
                    canonicalIconColor(navigation);

            int checkedColor =
                    tint.getColorForState(
                            new int[]{
                                android.R.attr.state_checked
                            },
                            tint.getDefaultColor()
                    );

            int uncheckedColor =
                    tint.getColorForState(
                            new int[]{
                                -android.R.attr.state_checked
                            },
                            tint.getDefaultColor()
                    );

            boolean parentTintMatches =
                    tint.getDefaultColor() == expectedColor
                            && checkedColor == expectedColor
                            && uncheckedColor == expectedColor;

            return parentTintMatches
                    && canonicalRenderedItemIconsMatch(
                            navigation,
                            expectedColor
                    );
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int canonicalIconColor(
            View navigation
    ) {
        int nightMode =
                navigation
                        .getResources()
                        .getConfiguration()
                        .uiMode
                        & android.content.res.Configuration
                                .UI_MODE_NIGHT_MASK;

        return nightMode
                == android.content.res.Configuration
                        .UI_MODE_NIGHT_YES
                ? android.graphics.Color.WHITE
                : android.graphics.Color.BLACK;
    }

    private static boolean canonicalDecorUnderlayMatches(
            Activity activity,
            View navigation
    ) {
        try {
            View decor = activity
                    .getWindow()
                    .getDecorView();

            if (!(decor instanceof ViewGroup)) {
                return false;
            }

            int insetBottom =
                    resolveNavigationInsetBottom(
                            activity,
                            decor
                    );

            if (insetBottom <= 0) {
                return true;
            }

            Integer expectedSurfaceColor =
                    resolveNavigationSurfaceColor(
                            activity,
                            navigation
                    );

            if (expectedSurfaceColor == null) {
                return false;
            }

            View underlay =
                    decor.findViewWithTag(
                            DECOR_UNDERLAY_TAG
                    );

            if (
                    underlay == null
                            || underlay.getVisibility()
                                != View.VISIBLE
                            || underlay.getParent() != decor
            ) {
                return false;
            }

            ViewGroup.LayoutParams params =
                    underlay.getLayoutParams();

            if (
                    params == null
                            || params.width
                                != ViewGroup.LayoutParams
                                    .MATCH_PARENT
                            || params.height != insetBottom
            ) {
                return false;
            }

            android.graphics.drawable.Drawable background =
                    underlay.getBackground();

            if (
                    !(background
                            instanceof android.graphics.drawable
                                    .ColorDrawable)
                            || (
                                (
                                    android.graphics.drawable
                                            .ColorDrawable
                                ) background
                            ).getColor()
                                != expectedSurfaceColor
            ) {
                return false;
            }

            // A valid underlay does not have to be the final decor child.
            // System/decor children can be appended after it.
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void attachSubredditSurfaceGuard(
            final Activity activity,
            final View navigation
    ) {
        synchronized (SUBREDDIT_SURFACE_GUARDS) {
            if (
                    Boolean.TRUE.equals(
                            SUBREDDIT_SURFACE_GUARDS.get(
                                    navigation
                            )
                    )
            ) {
                return;
            }

            SUBREDDIT_SURFACE_GUARDS.put(
                    navigation,
                    Boolean.TRUE
            );
        }

        final android.view.ViewTreeObserver observer =
                navigation.getViewTreeObserver();

        final android.view.ViewTreeObserver.OnPreDrawListener[]
                holder =
                        new android.view.ViewTreeObserver
                                .OnPreDrawListener[1];

        holder[0] =
                new android.view.ViewTreeObserver
                        .OnPreDrawListener() {
                    private boolean repairing;

                    @Override
                    public boolean onPreDraw() {
                        if (
                                repairing
                                        || activity.isFinishing()
                                        || activity.isDestroyed()
                                        || !navigation
                                            .isAttachedToWindow()
                        ) {
                            return true;
                        }

                        boolean navigationMismatch =
                                navigation.getVisibility()
                                        != View.VISIBLE
                                        || navigation.getAlpha()
                                            != 1.0f
                                        || !navigation.isEnabled();

                        boolean tintMismatch =
                                !canonicalTintMatches(navigation);

                        boolean underlayMismatch =
                                !canonicalDecorUnderlayMatches(
                                        activity,
                                        navigation
                                );

                        if (
                                !navigationMismatch
                                        && !tintMismatch
                                        && !underlayMismatch
                        ) {
                            return true;
                        }

                        repairing = true;

                        try {
                            if (navigationMismatch) {
                                navigation.setVisibility(
                                        View.VISIBLE
                                );
                                navigation.setAlpha(1.0f);
                                navigation.setEnabled(true);
                            }

                            if (tintMismatch) {
                                applyCanonicalTint(
                                        navigation
                                );
                            }

                            navigation.bringToFront();
                            navigation.invalidate();

                            applySubredditSystemSurface(
                                    activity,
                                    navigation
                            );

                            Log.w(
                                    TAG,
                                    "Subreddit surface guard repaired marker="
                                            + CANONICAL_NAV_MARKER
                                            + " navigation="
                                            + navigationMismatch
                                            + " tint="
                                            + tintMismatch
                                            + " underlay="
                                            + underlayMismatch
                            );
                        } catch (Throwable error) {
                            Log.e(
                                    TAG,
                                    "Subreddit surface guard repair failed marker="
                                            + CANONICAL_NAV_MARKER,
                                    error
                            );
                        } finally {
                            repairing = false;
                        }

                        return true;
                    }
                };

        if (observer.isAlive()) {
            observer.addOnPreDrawListener(
                    holder[0]
            );
        }

        navigation.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(
                            View view
                    ) {
                    }

                    @Override
                    public void onViewDetachedFromWindow(
                            View view
                    ) {
                        try {
                            if (observer.isAlive()) {
                                observer.removeOnPreDrawListener(
                                        holder[0]
                                );
                            }
                        } catch (Throwable ignored) {
                        }

                        synchronized (
                                SUBREDDIT_SURFACE_GUARDS
                        ) {
                            SUBREDDIT_SURFACE_GUARDS.remove(
                                    view
                            );
                        }

                        view.removeOnAttachStateChangeListener(
                                this
                        );
                    }
                }
        );

        Log.i(
                TAG,
                "Subreddit surface guard attached marker="
                        + CANONICAL_NAV_MARKER
                        + " activity="
                        + activity.getClass().getName()
        );
    }

    private static void applySubredditSystemSurface(
            Activity activity,
            View navigation
    ) {
        try {
            android.view.Window window =
                    activity.getWindow();

            Integer surfaceColor =
                    resolveNavigationSurfaceColor(
                            activity,
                            navigation
                    );

            if (surfaceColor != null) {
                window.setNavigationBarColor(
                        surfaceColor
                );

                if (
                        android.os.Build.VERSION.SDK_INT
                                >= android.os.Build.VERSION_CODES.P
                ) {
                    window.setNavigationBarDividerColor(
                            surfaceColor
                    );
                }
            }

            if (
                    android.os.Build.VERSION.SDK_INT
                            >= android.os.Build.VERSION_CODES.Q
            ) {
                window.setNavigationBarContrastEnforced(
                        false
                );
            }

            boolean lightTheme =
                    canonicalIconColor(navigation)
                            == android.graphics.Color.BLACK;

            if (
                    android.os.Build.VERSION.SDK_INT >= 30
                            && window.getInsetsController()
                                != null
            ) {
                int appearance =
                        android.view.WindowInsetsController
                                .APPEARANCE_LIGHT_NAVIGATION_BARS;

                window.getInsetsController()
                        .setSystemBarsAppearance(
                                lightTheme
                                        ? appearance
                                        : 0,
                                appearance
                        );
            } else if (
                    android.os.Build.VERSION.SDK_INT
                            >= android.os.Build.VERSION_CODES.O
            ) {
                View decor =
                        window.getDecorView();

                int flags =
                        decor.getSystemUiVisibility();

                if (lightTheme) {
                    flags |=
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags &=
                            ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }

                decor.setSystemUiVisibility(flags);
            }

            applyDecorUnderlay(
                    activity,
                    navigation
            );

            syncSystemNavigationBar(activity);

            Log.i(
                    TAG,
                    "Subreddit system surface applied marker="
                            + CANONICAL_NAV_MARKER
                            + " theme="
                            + (lightTheme ? "light" : "dark")
                            + " iconColor="
                            + (
                                lightTheme
                                        ? "#000000"
                                        : "#FFFFFF"
                            )
                            + " contrastEnforced=false"
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Subreddit system surface failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    private static void applyCanonicalTint(
            View navigation
    ) {
        if (navigation == null) return;

        try {
            int nightMode =
                    navigation
                            .getResources()
                            .getConfiguration()
                            .uiMode
                            & android.content.res.Configuration
                                    .UI_MODE_NIGHT_MASK;

            boolean darkTheme =
                    nightMode
                            == android.content.res.Configuration
                                    .UI_MODE_NIGHT_YES;

            int iconColor =
                    canonicalIconColor(navigation);

            android.content.res.ColorStateList contrastTint =
                    android.content.res.ColorStateList.valueOf(
                            iconColor
                    );

            // invokeOptional returns void. Do not assign its result.
            invokeOptional(
                    navigation,
                    "setItemIconTintList",
                    new Class<?>[]{
                        android.content.res.ColorStateList.class
                    },
                    new Object[]{contrastTint}
            );

            // Labels are hidden in the canonical bar, but keep their color
            // aligned if a device/library temporarily exposes them.
            invokeOptional(
                    navigation,
                    "setItemTextColor",
                    new Class<?>[]{
                        android.content.res.ColorStateList.class
                    },
                    new Object[]{contrastTint}
            );

            int renderedIconCount =
                    applyRenderedItemIconAppearance(
                            navigation,
                            contrastTint,
                            iconColor
                    );

            Log.i(
                    TAG,
                    "rendered icon appearance applied marker="
                            + CANONICAL_NAV_MARKER
                            + " iconCount="
                            + renderedIconCount
                            + " color="
                            + (
                                iconColor
                                        == android.graphics.Color.BLACK
                                        ? "#000000"
                                        : "#FFFFFF"
                            )
            );

            Log.i(
                    TAG,
                    "theme contrast icon tint marker="
                            + CANONICAL_NAV_MARKER
                            + " theme="
                            + (darkTheme ? "dark" : "light")
                            + " color="
                            + (darkTheme ? "#FFFFFF" : "#000000")
                            + " navigation="
                            + navigation.getClass().getName()
            );
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "theme contrast icon tint failed marker="
                            + CANONICAL_NAV_MARKER,
                    error
            );
        }
    }

    private static String attachSelectedListener(
            final Activity activity,
            final View navigation
    ) throws Exception {
        Method setter = findSingleArgumentMethod(
                navigation.getClass(),
                "setOnItemSelectedListener"
        );
        String api = "setOnItemSelectedListener";

        if (setter == null) {
            setter = findSingleArgumentMethod(
                    navigation.getClass(),
                    "setOnNavigationItemSelectedListener"
            );
            api = "setOnNavigationItemSelectedListener";
        }

        if (setter == null) {
            throw new NoSuchMethodException(
                    "No supported BottomNavigationView listener API"
            );
        }

        final Class<?> listenerType = setter.getParameterTypes()[0];

        Object listener = Proxy.newProxyInstance(
                navigation.getClass().getClassLoader(),
                new Class<?>[]{listenerType},
                new InvocationHandler() {
                    @Override
                    public Object invoke(
                            Object proxy,
                            Method method,
                            Object[] args
                    ) {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(method.getName())) {
                                return "MorpheSearchBottomNavigationListener";
                            }
                            if ("hashCode".equals(method.getName())) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(method.getName())) {
                                return args != null
                                        && args.length == 1
                                        && proxy == args[0];
                            }
                        }

                        if (
                                args != null
                                        && args.length == 1
                                        && args[0] instanceof MenuItem
                        ) {
                            return handleItem(
                                    activity,
                                    (MenuItem) args[0]
                            );
                        }

                        if (
                                method.getReturnType() == boolean.class
                                        || method.getReturnType() == Boolean.class
                        ) {
                            return false;
                        }

                        return null;
                    }
                }
        );

        setter.setAccessible(true);
        setter.invoke(navigation, listener);
        return api;
    }

    private static String attachSubredditNavigationListeners(
            final Activity activity,
            final View navigation
    ) throws Exception {
        String selectedApi =
                attachSelectedListener(
                        activity,
                        navigation
                );

        String reselectedApi =
                attachReselectedListener(
                        activity,
                        navigation
                );

        return selectedApi + "+" + reselectedApi;
    }

    private static String attachReselectedListener(
            final Activity activity,
            final View navigation
    ) throws Exception {
        Method setter = findSingleArgumentMethod(
                navigation.getClass(),
                "setOnItemReselectedListener"
        );

        if (setter == null) {
            return "reselect-unavailable";
        }

        final Class<?> listenerType =
                setter.getParameterTypes()[0];

        Object listener = Proxy.newProxyInstance(
                navigation.getClass().getClassLoader(),
                new Class<?>[]{listenerType},
                new InvocationHandler() {
                    @Override
                    public Object invoke(
                            Object proxy,
                            Method method,
                            Object[] args
                    ) {
                        if (
                                method.getDeclaringClass()
                                        == Object.class
                        ) {
                            if (
                                    "toString".equals(
                                            method.getName()
                                    )
                            ) {
                                return "MorpheSubredditReselectedListener";
                            }

                            if (
                                    "hashCode".equals(
                                            method.getName()
                                    )
                            ) {
                                return System.identityHashCode(
                                        proxy
                                );
                            }

                            if (
                                    "equals".equals(
                                            method.getName()
                                    )
                            ) {
                                return args != null
                                        && args.length == 1
                                        && proxy == args[0];
                            }
                        }

                        if (
                                args != null
                                        && args.length == 1
                                        && args[0]
                                            instanceof MenuItem
                        ) {
                            MenuItem item =
                                    (MenuItem) args[0];

                            boolean handled =
                                    dispatchHomeGoToTop(
                                            activity,
                                            item
                                    )
                                    || handleItem(
                                            activity,
                                            item
                                    );

                            Log.i(
                                    TAG,
                                    "Subreddit reselect routed marker="
                                            + CANONICAL_NAV_MARKER
                                            + " itemId="
                                            + item.getItemId()
                                            + " handled="
                                            + handled
                            );

                            if (
                                    method.getReturnType()
                                            == boolean.class
                                            || method.getReturnType()
                                                == Boolean.class
                            ) {
                                return handled;
                            }

                            return null;
                        }

                        if (
                                method.getReturnType()
                                        == boolean.class
                                        || method.getReturnType()
                                            == Boolean.class
                        ) {
                            return false;
                        }

                        return null;
                    }
                }
        );

        setter.setAccessible(true);
        setter.invoke(
                navigation,
                listener
        );

        return "setOnItemReselectedListener";
    }

    private static boolean dispatchHomeGoToTop(
            Activity activity,
            MenuItem item
    ) {
        if (
                activity == null
                        || item == null
                        || !MAIN_ACTIVITY.equals(
                                activity.getClass().getName()
                        )
        ) {
            return false;
        }

        int homeId = resourceId(
                activity,
                "item_home",
                "id"
        );

        if (
                homeId == 0
                        || item.getItemId() != homeId
        ) {
            return false;
        }

        try {
            int goTopId = resourceId(
                    activity,
                    "subreddit_fab_go_top",
                    "id"
            );

            if (goTopId == 0) {
                throw new IllegalStateException(
                        "subreddit_fab_go_top resource unavailable"
                );
            }

            View goTop = activity.findViewById(goTopId);

            if (goTop == null) {
                throw new IllegalStateException(
                        "subreddit_fab_go_top view unavailable"
                );
            }

            /*
             * Reuse Boost's existing and independently working Go to top
             * action. performClick() invokes the same listener as the FAB
             * submenu button without duplicating feed internals.
             */
            boolean clicked = goTop.performClick();

            Log.i(
                    TAG,
                    "Home reselect dispatched to FAB Go to top marker="
                            + HOME_RESELECT_TOP_MARKER
                            + " clicked="
                            + clicked
                            + " visibility="
                            + goTop.getVisibility()
                            + " enabled="
                            + goTop.isEnabled()
                            + " viewClass="
                            + goTop.getClass().getName()
            );

            return clicked;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Home reselect FAB Go to top failed marker="
                            + HOME_RESELECT_TOP_MARKER,
                    error
            );
            return false;
        }
    }

    private static boolean handleItem(
            Activity activity,
            MenuItem item
    ) {
        int selectedId = item.getItemId();

        int homeId = resourceId(activity, "item_home", "id");
        int searchId = resourceId(activity, "item_search", "id");
        int subscriptionsId = resourceId(activity, "item_subs", "id");
        int inboxId = resourceId(activity, "item_inbox", "id");
        int profileId = resourceId(activity, "item_profile", "id");


        int currentItemId =
                selectedItemIdForActivity(activity, -1);

        if (
                currentItemId != 0
                        && selectedId == currentItemId
        ) {
            return true;
        }
if (selectedId == searchId) {
            if (
                    SEARCH_ACTIVITY.equals(
                            activity.getClass().getName()
                    )
            ) {
                return true;
            }

            return openSearch(activity);
        }
        if (selectedId == homeId) return openHome(activity);
        if (selectedId == subscriptionsId) {
            return openSubscriptions(activity);
        }
        if (selectedId == inboxId) return openInbox(activity);
        if (selectedId == profileId) return openProfile(activity);

        return false;
    }

    private static boolean openSearch(
            Activity activity
    ) {
        try {
            if (
                    activity == null
                            || SEARCH_ACTIVITY.equals(
                                    activity.getClass().getName()
                            )
            ) {
                return true;
            }

            Class<?> destination = Class.forName(
                    SEARCH_ACTIVITY,
                    false,
                    activity.getClassLoader()
            );

            Intent intent = new Intent(
                    activity,
                    destination
            );
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            Log.i(
                    TAG,
                    "search route marker="
                            + SEARCH_ROUTE_MARKER
                            + " from="
                            + activity.getClass().getName()
                            + " to="
                            + SEARCH_ACTIVITY
            );

            activity.startActivity(intent);
            activity.finish();
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "search route failed marker="
                            + SEARCH_ROUTE_MARKER
                            + " from="
                            + (
                                activity == null
                                        ? "null"
                                        : activity
                                            .getClass()
                                            .getName()
                            ),
                    error
            );
            return false;
        }
    }

    private static boolean openHome(Activity activity) {
        try {
            Class<?> destination = Class.forName(
                    MAIN_ACTIVITY,
                    false,
                    activity.getClassLoader()
            );

            Intent intent = new Intent(activity, destination);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            activity.startActivity(intent);
            activity.finish();
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Home route failed", error);
            return false;
        }
    }

    private static boolean openSubscriptions(Activity activity) {
        try {
            Class<?> utility = Class.forName(
                    NAVIGATION_UTILITY,
                    false,
                    activity.getClassLoader()
            );

            Method route = findMethod(
                    utility,
                    "Z",
                    Context.class
            );

            if (route != null) {
                route.setAccessible(true);
                route.invoke(null, activity);
                activity.finish();
                return true;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Native subscriptions route failed", error);
        }

        return startDirectActivity(
                activity,
                SUBSCRIPTIONS_ACTIVITY
        );
    }

    private static boolean openInbox(Activity activity) {
        if (!isLoggedIn(activity)) {
            showSignInRequired(activity);
            return false;
        }

        return startDirectActivity(
                activity,
                MESSAGES_ACTIVITY
        );
    }

    private static boolean openProfile(Activity activity) {
        if (!isLoggedIn(activity)) {
            showSignInRequired(activity);
            return false;
        }

        try {
            String username = currentUsername(activity);
            if (username == null || username.length() == 0) {
                return false;
            }

            Method openProfile = findMethod(
                    activity.getClass(),
                    "openProfile",
                    String.class
            );

            if (openProfile == null) return false;

            openProfile.setAccessible(true);
            openProfile.invoke(activity, username);
            activity.finish();
            return true;
        } catch (Throwable error) {
            Log.e(TAG, "Profile route failed", error);
            return false;
        }
    }

    private static boolean startDirectActivity(
            Activity activity,
            String destinationClassName
    ) {
        try {
            Class<?> destination = Class.forName(
                    destinationClassName,
                    false,
                    activity.getClassLoader()
            );

            activity.startActivity(
                    new Intent(activity, destination)
            );
            activity.finish();
            return true;
        } catch (Throwable error) {
            Log.e(
                    TAG,
                    "Direct route failed: " + destinationClassName,
                    error
            );
            return false;
        }
    }

    private static boolean isLoggedIn(Activity activity) {
        try {
            Class<?> baseActivity = Class.forName(
                    BASE_ACTIVITY,
                    false,
                    activity.getClassLoader()
            );
            Method method = findMethod(baseActivity, "isLoggedIn");

            if (method == null) return false;

            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (Throwable error) {
            Log.w(TAG, "Login-state check failed", error);
            return false;
        }
    }

    private static String currentUsername(Activity activity) {
        try {
            Class<?> managerClass = Class.forName(
                    ACCOUNT_MANAGER,
                    false,
                    activity.getClassLoader()
            );
            Method getInstance = findMethod(managerClass, "V");

            if (getInstance == null) return null;

            getInstance.setAccessible(true);
            Object manager = getInstance.invoke(null);
            if (manager == null) return null;

            Method getUsername = findMethod(manager.getClass(), "b");
            if (getUsername == null) return null;

            getUsername.setAccessible(true);
            Object value = getUsername.invoke(manager);

            return value instanceof String
                    ? (String) value
                    : null;
        } catch (Throwable error) {
            Log.w(TAG, "Username lookup failed", error);
            return null;
        }
    }

    private static void showSignInRequired(Activity activity) {
        Toast.makeText(
                activity,
                "Sign in required",
                Toast.LENGTH_SHORT
        ).show();
    }

    private static boolean isTarget(Activity activity) {
        if (activity == null) return false;

        String name = activity.getClass().getName();
        return SEARCH_ACTIVITY.equals(name)
                || GO_TO_ACTIVITY.equals(name);
    }

    private static int resourceId(
            Activity activity,
            String name,
            String type
    ) {
        int id = activity.getResources().getIdentifier(
                name,
                type,
                activity.getPackageName()
        );

        if (id != 0) return id;

        return activity.getResources().getIdentifier(
                name,
                type,
                "com.rubenmayayo.reddit"
        );
    }

    private static Method findSingleArgumentMethod(
            Class<?> type,
            String name
    ) {
        Class<?> current = type;

        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (
                        name.equals(method.getName())
                                && method.getParameterTypes().length == 1
                ) {
                    return method;
                }
            }

            current = current.getSuperclass();
        }

        return null;
    }

    private static Method findMethod(
            Class<?> type,
            String name,
            Class<?>... parameterTypes
    ) {
        Class<?> current = type;

        while (current != null) {
            try {
                return current.getDeclaredMethod(
                        name,
                        parameterTypes
                );
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private static void invokeRequired(
            Object receiver,
            String name,
            Class<?>[] parameterTypes,
            Object[] arguments
    ) throws Exception {
        Method method = receiver
                .getClass()
                .getMethod(name, parameterTypes);

        method.setAccessible(true);
        method.invoke(receiver, arguments);
    }

    private static void invokeOptional(
            Object receiver,
            String name,
            Class<?>[] parameterTypes,
            Object[] arguments
    ) {
        try {
            invokeRequired(
                    receiver,
                    name,
                    parameterTypes,
                    arguments
            );
        } catch (Throwable ignored) {
        }
    }
}
