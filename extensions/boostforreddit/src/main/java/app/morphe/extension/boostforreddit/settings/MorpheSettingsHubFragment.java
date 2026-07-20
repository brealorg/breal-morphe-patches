package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public final class MorpheSettingsHubFragment extends PreferenceFragmentCompat {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_HUBS_ISSUE106_V2_1";

    public static final String RESOURCE_ARGUMENT =
            "morphe_boost_settings_hub_resource";

    private static final String RESOURCE_PREFIX =
            "morphe_boost_settings_hub_";
    private static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";
    private static final String BACKUP_KEY =
            "morphe_boost_settings_backup";
    private static final String BACKUP_ACTIVITY =
            "com.rubenmayayo.reddit.BackupActivity";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Bundle arguments = getArguments();
        String resourceName = arguments == null
                ? null
                : arguments.getString(RESOURCE_ARGUMENT);

        if (resourceName == null || !resourceName.startsWith(RESOURCE_PREFIX)) {
            throw new IllegalArgumentException(
                    "Missing Morphe settings hub resource"
            );
        }

        Context context = requireContext();
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(
                resourceName,
                "xml",
                context.getPackageName()
        );

        if (resourceId == 0) {
            resourceId = resources.getIdentifier(
                    resourceName,
                    "xml",
                    BOOST_PACKAGE
            );
        }

        if (resourceId == 0) {
            throw new IllegalStateException(
                    "Missing Morphe settings hub resource: " + resourceName
            );
        }

        setPreferencesFromResource(resourceId, rootKey);
        installBackupRoute(context);
    }

    private void installBackupRoute(Context context) {
        Preference backup = findPreference(BACKUP_KEY);
        if (backup == null) {
            return;
        }

        backup.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), BACKUP_ACTIVITY);
            startActivity(intent);
            return true;
        });
    }
}
