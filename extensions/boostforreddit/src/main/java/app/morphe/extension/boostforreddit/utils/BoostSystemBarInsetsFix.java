/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.app.Activity;
import android.app.Application;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import java.util.WeakHashMap;

/**
 * @noinspection unused
 */
public final class BoostSystemBarInsetsFix {
    private static final int TARGET_SDK_EDGE_TO_EDGE = 35;
    private static final String MARKER = "MORPHE_BOOST_NAV_BAR_INSETS_FIX_V6";
    private static final String COMMENTS_SYSTEM_BAR_MARKER = "MORPHE_BOOST_COMMENTS_SYSTEM_BAR_SURFACE_V1";
    private static final String COMMENTS_ACTIVITY_NAME = "com.rubenmayayo.reddit.ui.comments.CommentsActivity";

    private static final WeakHashMap<Application, Boolean> INSTALLED = new WeakHashMap<>();
    private static final WeakHashMap<View, Padding> ORIGINAL_PADDING = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> WATCHERS = new WeakHashMap<>();

    private BoostSystemBarInsetsFix() {
    }

    public static void install(final Application application) {
        try {
            if (application == null || INSTALLED.containsKey(application)) return;
            INSTALLED.put(application, Boolean.TRUE);

            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
                    scheduleMainInsets(activity);
                    scheduleCommentsSystemBars(activity);
                }

                @Override
                public void onActivityResumed(final Activity activity) {
                    scheduleMainInsets(activity);
                    scheduleCommentsSystemBars(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
        } catch (Throwable ignored) {
        }
    }

    public static void applyMediaInsets(Activity activity) {
        try {
            if (!shouldApply(activity)) return;

            View bottomBar = findViewByName(activity, "bottom_bar");
            if (bottomBar != null) {
                applyBottomInsetPadding(bottomBar, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applyMainInsets(Activity activity) {
        scheduleMainInsets(activity);
    }

    private static void scheduleMainInsets(final Activity activity) {
        try {
            if (!shouldApply(activity)) return;

            final View decor = activity.getWindow().getDecorView();
            if (decor == null) return;

            installLayoutWatcher(activity, decor);

            Runnable apply = new Runnable() {
                @Override
                public void run() {
                    try {
                        applyMainInsetsNow(activity);
                    } catch (Throwable ignored) {
                    }
                }
            };

            decor.post(apply);
            decor.postDelayed(apply, 100L);
            decor.postDelayed(apply, 350L);
            decor.postDelayed(apply, 1000L);
            decor.postDelayed(apply, 2000L);
        } catch (Throwable ignored) {
        }
    }

    private static void installLayoutWatcher(final Activity activity, View decor) {
        if (WATCHERS.containsKey(decor)) return;
        WATCHERS.put(decor, Boolean.TRUE);

        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                try {
                    applyMainInsetsNow(activity);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void applyMainInsetsNow(Activity activity) {
        View drawerRecycler = findViewByName(activity, "material_drawer_recycler_view");
        if (drawerRecycler != null) {
            applyBottomInsetPadding(drawerRecycler, true);
            nudgeBottomVisibleChildAboveNavigationBar(drawerRecycler);
        }
    }

    private static boolean shouldApply(Activity activity) {
        return activity != null
                && Build.VERSION.SDK_INT >= 23
                && activity.getApplicationInfo() != null
                && activity.getApplicationInfo().targetSdkVersion >= TARGET_SDK_EDGE_TO_EDGE;
    }

    private static View findViewByName(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        if (id == 0) return null;
        return activity.findViewById(id);
    }

    private static void applyBottomInsetPadding(final View view, final boolean allowScrollIntoPadding) {
        if (view == null) return;

        saveOriginalPadding(view);

        if (allowScrollIntoPadding && view instanceof ViewGroup) {
            ((ViewGroup) view).setClipToPadding(false);
        }

        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                try {
                    applyBottomInsetPaddingNow(v, insets);
                } catch (Throwable ignored) {
                }
                return insets;
            }
        });

        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    applyBottomInsetPaddingNow(view, getBestCurrentInsets(view));
                    view.requestApplyInsets();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void saveOriginalPadding(View view) {
        if (!ORIGINAL_PADDING.containsKey(view)) {
            ORIGINAL_PADDING.put(view, new Padding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            ));
        }
    }

    private static void applyBottomInsetPaddingNow(View view, WindowInsets insets) {
        if (view == null) return;

        saveOriginalPadding(view);

        Padding original = ORIGINAL_PADDING.get(view);
        if (original == null) return;

        int bottomInset = getEffectiveNavigationBottomInset(view, insets);

        view.setPadding(
                original.left,
                original.top,
                original.right,
                original.bottom + Math.max(0, bottomInset)
        );

        view.requestLayout();
        view.invalidate();
    }

    private static void nudgeBottomVisibleChildAboveNavigationBar(final View view) {
        if (!(view instanceof ViewGroup)) return;

        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (view.canScrollVertically(1)) return;

                    ViewGroup group = (ViewGroup) view;
                    int childCount = group.getChildCount();
                    if (childCount <= 0) return;

                    View lastChild = group.getChildAt(childCount - 1);
                    if (lastChild == null) return;

                    int bottomInset = getEffectiveNavigationBottomInset(view, getBestCurrentInsets(view));
                    if (bottomInset <= 0) return;

                    int targetBottom = view.getHeight() - bottomInset;
                    int overlap = lastChild.getBottom() - targetBottom;

                    if (overlap > 0) {
                        view.scrollBy(0, overlap);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static int getEffectiveNavigationBottomInset(View view, WindowInsets insets) {
        int bottomInset = getNavigationBottomInset(insets);
        if (bottomInset <= 0) {
            bottomInset = getNavigationBottomInset(getBestCurrentInsets(view));
        }

        if (bottomInset <= 0 && isThreeButtonNavigation(view)) {
            bottomInset = getNavigationBarHeight(view);
        }

        return bottomInset;
    }

    private static WindowInsets getBestCurrentInsets(View view) {
        if (view == null || Build.VERSION.SDK_INT < 23) return null;

        WindowInsets insets = view.getRootWindowInsets();
        if (insets != null) return insets;

        View root = view.getRootView();
        if (root != null) {
            return root.getRootWindowInsets();
        }

        return null;
    }

    private static int getNavigationBottomInset(WindowInsets insets) {
        if (insets == null) return 0;

        if (Build.VERSION.SDK_INT >= 30) {
            Insets navigationInsets = insets.getInsets(WindowInsets.Type.navigationBars());
            return navigationInsets == null ? 0 : navigationInsets.bottom;
        }

        return insets.getSystemWindowInsetBottom();
    }


    public static void applyCommentsSystemBars(Activity activity) {
        scheduleCommentsSystemBars(activity);
    }

    private static void scheduleCommentsSystemBars(final Activity activity) {
        try {
            if (!shouldApply(activity) || !isCommentsActivity(activity)) return;

            final View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (decor == null) return;

            Runnable apply = new Runnable() {
                @Override
                public void run() {
                    try {
                        applyCommentsSystemBarsNow(activity);
                    } catch (Throwable ignored) {
                    }
                }
            };

            decor.post(apply);
            decor.postDelayed(apply, 100L);
            decor.postDelayed(apply, 350L);
            decor.postDelayed(apply, 1000L);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isCommentsActivity(Activity activity) {
        if (activity == null) return false;

        String className = activity.getClass().getName();
        return COMMENTS_ACTIVITY_NAME.equals(className)
                || className.endsWith(".ui.comments.CommentsActivity");
    }

    private static void applyCommentsSystemBarsNow(Activity activity) {
        try {
            if (!shouldApply(activity) || !isCommentsActivity(activity)) return;

            Window window = activity.getWindow();
            if (window == null) return;

            View decor = window.getDecorView();
            if (decor == null) return;

            int statusHeight = getStatusBarTopInset(decor);
            if (statusHeight <= 0) {
                statusHeight = getStatusBarHeight(activity);
            }
            if (statusHeight <= 0) return;

            int color = findCommentsToolbarSurfaceColor(activity, decor, statusHeight);

            installStatusBarSurface(decor, statusHeight, color);

            if (Build.VERSION.SDK_INT >= 21) {
                window.setStatusBarColor(color);
            }
            applyLightStatusBarFlag(decor, color);
        } catch (Throwable ignored) {
        }
    }

    private static void installStatusBarSurface(View decor, int statusHeight, int color) {
        if (!(decor instanceof ViewGroup)) return;

        ViewGroup group = (ViewGroup) decor;
        View surface = decor.findViewWithTag(COMMENTS_SYSTEM_BAR_MARKER);

        if (surface == null) {
            surface = new View(decor.getContext());
            surface.setTag(COMMENTS_SYSTEM_BAR_MARKER);
            surface.setClickable(false);
            surface.setFocusable(false);
            if (Build.VERSION.SDK_INT >= 16) {
                surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }

            if (group instanceof FrameLayout) {
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        statusHeight,
                        Gravity.TOP
                );
                ((FrameLayout) group).addView(surface, params);
            } else {
                group.addView(surface, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        statusHeight
                ));
            }
        }

        ViewGroup.LayoutParams params = surface.getLayoutParams();
        if (params != null && params.height != statusHeight) {
            params.height = statusHeight;
            surface.setLayoutParams(params);
        }

        surface.setBackgroundColor(color);
        surface.bringToFront();
        surface.invalidate();
    }

    private static int findCommentsToolbarSurfaceColor(Activity activity, View decor, int statusHeight) {
        int color = findNamedToolbarColor(activity);
        if (isOpaque(color)) return color;

        color = findTopSurfaceColor(decor, statusHeight);
        if (isOpaque(color)) return color;

        color = resolveThemeColor(activity, "colorSurface", 0);
        if (isOpaque(color)) return color;

        color = resolveThemeColor(activity, "colorBackground", 0);
        if (isOpaque(color)) return color;

        color = resolveAndroidThemeColor(activity, android.R.attr.colorBackground, 0);
        if (isOpaque(color)) return color;

        return isNightMode(activity) ? Color.BLACK : Color.WHITE;
    }

    private static int findNamedToolbarColor(Activity activity) {
        String[] names = new String[] {
                "toolbar",
                "action_bar",
                "actionbar",
                "appbar",
                "app_bar",
                "comments_toolbar",
                "toolbar_comments"
        };

        for (String name : names) {
            View view = findViewByName(activity, name);
            int color = findSolidBackgroundColor(view);
            if (isOpaque(color)) return color;

            View parent = view == null ? null : view.getParent() instanceof View ? (View) view.getParent() : null;
            color = findSolidBackgroundColor(parent);
            if (isOpaque(color)) return color;
        }

        return 0;
    }

    private static int findTopSurfaceColor(View root, int statusHeight) {
        if (root == null) return 0;

        int[] best = new int[] {0, Integer.MAX_VALUE};
        collectTopSurfaceColor(root, statusHeight, best);
        return best[0];
    }

    private static void collectTopSurfaceColor(View view, int statusHeight, int[] best) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        int width = view.getWidth();
        int height = view.getHeight();

        if (width > 0 && height > 0) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);

            int top = location[1];
            int bottom = top + height;
            int screenWidth = view.getRootView() == null ? width : view.getRootView().getWidth();

            boolean nearToolbar =
                    screenWidth > 0
                            && width >= (screenWidth * 2 / 3)
                            && top <= statusHeight + 260
                            && bottom >= statusHeight + 20;

            if (nearToolbar) {
                int color = findSolidBackgroundColor(view);
                if (isOpaque(color)) {
                    int score = Math.abs(top - statusHeight);
                    if (score < best[1]) {
                        best[0] = color;
                        best[1] = score;
                    }
                }
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTopSurfaceColor(group.getChildAt(i), statusHeight, best);
            }
        }
    }

    private static int findSolidBackgroundColor(View view) {
        if (view == null) return 0;

        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) {
            int color = ((ColorDrawable) background).getColor();
            if (isOpaque(color)) return color;
        }

        return 0;
    }

    private static int resolveThemeColor(Activity activity, String attrName, int fallback) {
        try {
            int attr = activity.getResources().getIdentifier(attrName, "attr", activity.getPackageName());
            if (attr == 0) return fallback;
            return resolveAndroidThemeColor(activity, attr, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int resolveAndroidThemeColor(Activity activity, int attr, int fallback) {
        try {
            TypedValue value = new TypedValue();
            if (!activity.getTheme().resolveAttribute(attr, value, true)) return fallback;

            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }

            if (value.resourceId != 0) {
                if (Build.VERSION.SDK_INT >= 23) {
                    return activity.getResources().getColor(value.resourceId, activity.getTheme());
                }
                return activity.getResources().getColor(value.resourceId);
            }
        } catch (Throwable ignored) {
        }

        return fallback;
    }

    private static boolean isOpaque(int color) {
        return Color.alpha(color) >= 200;
    }

    private static boolean isNightMode(Activity activity) {
        try {
            int mode = activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applyLightStatusBarFlag(View decor, int color) {
        if (decor == null || Build.VERSION.SDK_INT < 23) return;

        int flags = decor.getSystemUiVisibility();
        if (isLightColor(color)) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static boolean isLightColor(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;

        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);

        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 0.5;
    }

    private static int getStatusBarTopInset(View view) {
        try {
            WindowInsets insets = getBestCurrentInsets(view);
            if (insets == null) return 0;

            if (Build.VERSION.SDK_INT >= 30) {
                Insets statusInsets = insets.getInsets(WindowInsets.Type.statusBars());
                return statusInsets == null ? 0 : statusInsets.top;
            }

            return insets.getSystemWindowInsetTop();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int getStatusBarHeight(Activity activity) {
        try {
            int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) {
                return activity.getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable ignored) {
        }

        return 0;
    }


    private static boolean isThreeButtonNavigation(View view) {
        try {
            return android.provider.Settings.Secure.getInt(
                    view.getContext().getContentResolver(),
                    "navigation_mode",
                    -1
            ) == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int getNavigationBarHeight(View view) {
        try {
            int id = view.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) {
                return view.getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable ignored) {
        }

        return 0;
    }

    private static final class Padding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Padding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
