package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

public final class MorpheSettingsFragment extends PreferenceFragmentCompat {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_TOP_LEVEL_SETTINGS_ISSUE106_V1";

    private static final String RESOURCE_NAME = "morphe_boost_settings_skeleton";
    private static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = requireContext();
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

        if (resourceId == 0) {
            throw new IllegalStateException(
                    "Missing Morphe settings resource: " + RESOURCE_NAME
            );
        }

        setPreferencesFromResource(resourceId, rootKey);
    }
}
