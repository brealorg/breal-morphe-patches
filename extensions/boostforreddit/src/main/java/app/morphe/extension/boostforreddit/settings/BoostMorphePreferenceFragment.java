package app.morphe.extension.boostforreddit.settings;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

public class BoostMorphePreferenceFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        int resourceId = requireContext()
                .getResources()
                .getIdentifier(
                        "morphe_boost_settings",
                        "xml",
                        requireContext().getPackageName()
                );

        if (resourceId != 0) {
            setPreferencesFromResource(resourceId, rootKey);
        }
    }
}
