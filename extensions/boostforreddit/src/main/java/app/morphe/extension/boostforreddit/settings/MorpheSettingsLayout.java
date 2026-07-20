package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import androidx.preference.PreferenceFragmentCompat;

public final class MorpheSettingsLayout {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_LAYOUT_ISSUE106_V2_1";

    public static final String ENABLED_KEY =
            "morphe_boost_settings_layout_v2_enabled";

    private static final String V4_ENABLED_KEY =
            "morphe_boost_settings_v4_enabled";

    private static final String RESOURCE_NAME =
            "morphe_boost_settings_layout_v2";
    private static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";

    private MorpheSettingsLayout() {
    }

    public static int resolveRootResource(
            PreferenceFragmentCompat fragment,
            int originalResourceId
    ) {
        Context context = fragment.requireContext();
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);

        // Once the v4 choice has been made, OFF means the original Boost root.
        // Existing v2 preview users are migrated by MorpheSettingsV4 before the
        // HeaderFragment is created.
        if (preferences.contains(V4_ENABLED_KEY)) {
            return originalResourceId;
        }

        if (!preferences.getBoolean(ENABLED_KEY, false)) {
            return originalResourceId;
        }

        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(
                RESOURCE_NAME,
                "xml",
                context.getPackageName()
        );

        if (resourceId == 0) {
            resourceId = resources.getIdentifier(
                    RESOURCE_NAME,
                    "xml",
                    BOOST_PACKAGE
            );
        }

        return resourceId == 0 ? originalResourceId : resourceId;
    }
}
