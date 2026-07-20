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

    public static final String ENABLED_KEY =
            "morphe_boost_settings_v4_enabled";

    private static final String LEGACY_V2_ENABLED_KEY =
            "morphe_boost_settings_layout_v2_enabled";
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
            return preferences.getBoolean(ENABLED_KEY, false);
        }

        // Preserve and migrate the choice made by existing issue #106 preview
        // testers so the new toggle accurately reflects the active UI.
        boolean legacyEnabled = preferences.getBoolean(
                LEGACY_V2_ENABLED_KEY,
                false
        );
        if (legacyEnabled) {
            preferences.edit().putBoolean(ENABLED_KEY, true).apply();
        }
        return legacyEnabled;
    }

    public static void setEnabled(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(ENABLED_KEY, enabled)
                .apply();
    }
}
