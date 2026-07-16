package app.morphe.extension.boostforreddit.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.WeakHashMap;

public final class OpenByDefaultRecovery {
    private static final String LOG_TAG = "morphe";
    private static final String MARKER = "MORPHE_BOOST_OPEN_BY_DEFAULT_RECOVERY_V1";
    private static final String PREFS = "morphe_boost_open_by_default_recovery";
    private static final String KEY_PROMPTED_LAST_UPDATE_TIME = "prompted_last_update_time";
    private static final String ACTION_APP_OPEN_BY_DEFAULT_SETTINGS =
            "android.settings.APP_OPEN_BY_DEFAULT_SETTINGS";

    private static final WeakHashMap<Application, Boolean> INSTALLED = new WeakHashMap<>();
    private static boolean promptInFlight;

    private OpenByDefaultRecovery() {}

    public static void install(final Application application) {
        try {
            if (application == null) return;

            synchronized (INSTALLED) {
                if (INSTALLED.containsKey(application)) return;
                INSTALLED.put(application, Boolean.TRUE);
            }

            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

                @Override
                public void onActivityStarted(Activity activity) {}

                @Override
                public void onActivityResumed(final Activity activity) {
                    maybePrompt(activity);
                }

                @Override
                public void onActivityPaused(Activity activity) {}

                @Override
                public void onActivityStopped(Activity activity) {}

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                @Override
                public void onActivityDestroyed(Activity activity) {}
            });

            Log.i(LOG_TAG, MARKER + ": installed for " + application.getPackageName());
        } catch (Throwable t) {
            Log.w(LOG_TAG, MARKER + ": install failed", t);
        }
    }

    public static void openSettings(Context context) {
        if (context == null) return;

        try {
            Intent intent = new Intent(ACTION_APP_OPEN_BY_DEFAULT_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            Log.i(LOG_TAG, MARKER + ": opened app open-by-default settings");
            return;
        } catch (Throwable t) {
            Log.w(LOG_TAG, MARKER + ": app open-by-default settings failed, using app details", t);
        }

        try {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + context.getPackageName()));
            if (!(context instanceof Activity)) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(fallback);
            Log.i(LOG_TAG, MARKER + ": opened app details fallback");
        } catch (Throwable t) {
            Log.w(LOG_TAG, MARKER + ": settings fallback failed", t);
        }
    }

    private static void maybePrompt(final Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) return;
            if (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;

            final Context appContext = activity.getApplicationContext();
            if (appContext == null) return;

            final long lastUpdateTime = getLastUpdateTime(appContext);
            if (lastUpdateTime <= 0L) return;

            final SharedPreferences preferences =
                    appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            final long prompted = preferences.getLong(KEY_PROMPTED_LAST_UPDATE_TIME, -1L);
            if (prompted == lastUpdateTime) return;

            synchronized (OpenByDefaultRecovery.class) {
                if (promptInFlight) return;
                promptInFlight = true;
            }

            preferences.edit()
                    .putLong(KEY_PROMPTED_LAST_UPDATE_TIME, lastUpdateTime)
                    .apply();

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    showPrompt(activity);
                }
            }, 1200L);
        } catch (Throwable t) {
            synchronized (OpenByDefaultRecovery.class) {
                promptInFlight = false;
            }
            Log.w(LOG_TAG, MARKER + ": prompt check failed", t);
        }
    }

    private static void showPrompt(final Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) return;
            if (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;

            new AlertDialog.Builder(activity)
                    .setTitle("Open Reddit links in Boost")
                    .setMessage("Android may clear Boost's Open by default links after patch installs. Open Android settings and enable the supported Reddit links for Boost.")
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        synchronized (OpenByDefaultRecovery.class) {
                            promptInFlight = false;
                        }
                        openSettings(activity);
                    })
                    .setNegativeButton("Not now", (dialog, which) -> {
                        synchronized (OpenByDefaultRecovery.class) {
                            promptInFlight = false;
                        }
                        Log.i(LOG_TAG, MARKER + ": user skipped prompt");
                    })
                    .setOnCancelListener(dialog -> {
                        synchronized (OpenByDefaultRecovery.class) {
                            promptInFlight = false;
                        }
                        Log.i(LOG_TAG, MARKER + ": prompt cancelled");
                    })
                    .show();

            Log.i(LOG_TAG, MARKER + ": prompt shown");
        } catch (Throwable t) {
            synchronized (OpenByDefaultRecovery.class) {
                promptInFlight = false;
            }
            Log.w(LOG_TAG, MARKER + ": prompt failed", t);
        }
    }

    private static long getLastUpdateTime(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (Throwable t) {
            Log.w(LOG_TAG, MARKER + ": failed to read package info", t);
            return -1L;
        }
    }
}
