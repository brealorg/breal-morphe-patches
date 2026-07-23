package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

/** Entry-point and compatibility policy for the Morphe-owned settings UI. */
public final class MorpheSettingsV4 {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_ISSUE106_V1";
    public static final String DEFAULT_ON_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_DEFAULT_ON_CLASSIC_FALLBACK_V1";

    public static final String ENABLED_KEY =
            "morphe_boost_settings_v4_enabled";

    private static final String LEGACY_V2_ENABLED_KEY =
            "morphe_boost_settings_layout_v2_enabled";
    private static final String THEME_MODE_KEY = "pref_theme_mode_type";
    private static final String SYSTEM_THEME_MODE = "system";
    private static final String EXTRA_SHOW_FRAGMENT =
            "extra_show_fragment";
    private static final String FRAGMENT_NAME =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4Fragment";

    private MorpheSettingsV4() {
    }

    /**
     * Selects the v4 fragment only for a normal Settings launch. Explicit Boost
     * deep links keep their requested destination.
     */
    public static void prepareIntent(Activity activity) {
        if (activity == null || !isEnabled(activity)) {
            return;
        }

        Intent intent = activity.getIntent();
        if (intent == null) {
            return;
        }

        String requestedFragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        if (!TextUtils.isEmpty(requestedFragment)) {
            return;
        }

        intent.putExtra(EXTRA_SHOW_FRAGMENT, FRAGMENT_NAME);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.contains(ENABLED_KEY)) {
            boolean enabled = preferences.getBoolean(ENABLED_KEY, true);
            if (enabled) {
                forceSystemThemeMode(preferences);
            }
            return enabled;
        }

        // Preserve the explicit choice made by issue #106 preview testers.
        if (preferences.contains(LEGACY_V2_ENABLED_KEY)) {
            boolean legacyEnabled = preferences.getBoolean(
                    LEGACY_V2_ENABLED_KEY,
                    false
            );
            SharedPreferences.Editor editor = preferences.edit()
                    .putBoolean(ENABLED_KEY, legacyEnabled);
            if (legacyEnabled) {
                editor.putString(THEME_MODE_KEY, SYSTEM_THEME_MODE);
            }
            editor.apply();
            return legacyEnabled;
        }

        // Material settings is the shipping default. Classic Boost settings
        // remains available as the final action on the Material home page.
        preferences.edit()
                .putBoolean(ENABLED_KEY, true)
                .putString(THEME_MODE_KEY, SYSTEM_THEME_MODE)
                .apply();
        return true;
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(ENABLED_KEY, enabled);
        if (enabled) {
            editor.putString(THEME_MODE_KEY, SYSTEM_THEME_MODE);
        }
        editor.apply();
    }

    private static void forceSystemThemeMode(SharedPreferences preferences) {
        if (!SYSTEM_THEME_MODE.equals(
                preferences.getString(THEME_MODE_KEY, SYSTEM_THEME_MODE)
        )) {
            preferences.edit()
                    .putString(THEME_MODE_KEY, SYSTEM_THEME_MODE)
                    .apply();
        }
    }
}
