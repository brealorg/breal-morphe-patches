/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * @noinspection unused
 */
public final class AdaptiveRefreshRate {
    private static final String TAG = "MorpheRefresh";
    private static final String MARKER = "MORPHE_BOOST_ADAPTIVE_REFRESH_RATE_V2";
    private static final String PREF_KEY = "morphe_boost_prefer_high_refresh_rate";

    private static final float HIGH_REFRESH_RATE_HINT_HZ = 120.0f;

    private static final WeakHashMap<Application, Boolean> INSTALLED = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Boolean> LOGGED = new WeakHashMap<>();

    private AdaptiveRefreshRate() {
    }

    public static void install(final Application application) {
        try {
            if (application == null || INSTALLED.containsKey(application)) return;
            INSTALLED.put(application, Boolean.TRUE);

            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(final Activity activity, Bundle savedInstanceState) {
                    apply(activity);
                }

                @Override
                public void onActivityResumed(final Activity activity) {
                    apply(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {
                    LOGGED.remove(activity);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void apply(final Activity activity) {
        try {
            if (activity == null) return;

            applyNow(activity);

            final Window window = activity.getWindow();
            if (window == null) return;

            final View decor = window.getDecorView();
            if (decor == null) return;

            Runnable retry = new Runnable() {
                @Override
                public void run() {
                    applyNow(activity);
                }
            };

            decor.post(retry);
            decor.postDelayed(retry, 250L);
            decor.postDelayed(retry, 1000L);
        } catch (Throwable ignored) {
        }
    }

    private static void applyNow(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window == null) return;

            if (!isEnabled(activity)) {
                clearWindowPreference(activity, window);
                return;
            }

            WindowManager.LayoutParams attrs = window.getAttributes();
            if (attrs != null && Math.abs(attrs.preferredRefreshRate - HIGH_REFRESH_RATE_HINT_HZ) > 0.01f) {
                attrs.preferredRefreshRate = HIGH_REFRESH_RATE_HINT_HZ;
                window.setAttributes(attrs);
            }

            View decor = window.getDecorView();
            if (decor != null) {
                requestViewHighRefreshRate(decor);
            }

            logAppliedOnce(activity, attrs == null ? 0.0f : attrs.preferredRefreshRate);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isEnabled(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(PREF_KEY, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void clearWindowPreference(Activity activity, Window window) {
        try {
            WindowManager.LayoutParams attrs = window.getAttributes();
            if (attrs != null && attrs.preferredRefreshRate != 0.0f) {
                attrs.preferredRefreshRate = 0.0f;
                window.setAttributes(attrs);
            }

            View decor = window.getDecorView();
            if (decor != null) {
                requestViewNoPreference(decor);
            }

            LOGGED.remove(activity);
        } catch (Throwable ignored) {
        }
    }

    private static void requestViewHighRefreshRate(View view) {
        if (view == null) return;
        invokeRequestedFrameRate(view, requestedHighFrameRateCategory());
    }

    private static void requestViewNoPreference(View view) {
        if (view == null) return;
        invokeRequestedFrameRate(view, requestedNoPreferenceFrameRateCategory());
    }

    private static boolean invokeRequestedFrameRate(View view, float value) {
        try {
            Method method = View.class.getMethod("setRequestedFrameRate", Float.TYPE);
            method.invoke(view, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float requestedHighFrameRateCategory() {
        try {
            Field field = View.class.getField("REQUESTED_FRAME_RATE_CATEGORY_HIGH");
            return field.getFloat(null);
        } catch (Throwable ignored) {
            return HIGH_REFRESH_RATE_HINT_HZ;
        }
    }

    private static float requestedNoPreferenceFrameRateCategory() {
        try {
            Field field = View.class.getField("REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE");
            return field.getFloat(null);
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private static void logAppliedOnce(Activity activity, float preferredRefreshRate) {
        try {
            if (LOGGED.containsKey(activity)) return;
            LOGGED.put(activity, Boolean.TRUE);

            Log.i(TAG, MARKER
                    + " activity=" + activity.getClass().getName()
                    + " preferredRefreshRate=" + preferredRefreshRate);
        } catch (Throwable ignored) {
        }
    }
}
