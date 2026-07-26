package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.preference.PreferenceFragmentCompat;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

/** V5-owned renderer for configurable Morphe feature preferences. */
public final class MorpheSettingsV5MorpheFragment
        extends PreferenceFragmentCompat {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_MORPHE_CONTROLS_ISSUE121_V1";
    public static final String REFERENCE_COUNT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_MORPHE_CONFIGURABLE_REFERENCE_COUNT_12_ISSUE121_V1";
    public static final String ROOT_FLATTEN_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_MORPHE_ROOT_FLATTEN_ISSUE121_V1";

    private static final String RESOURCE_NAME =
            "morphe_boost_settings_skeleton";
    private static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";

    private MorpheSettingsV4Theme.Tokens tokens;

    public MorpheSettingsV5MorpheFragment() {
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setHasOptionsMenu(true);
        Context context = requireContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
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
                    "Missing Morphe patch-feature resource: " + RESOURCE_NAME
            );
        }
        setPreferencesFromResource(resourceId, rootKey);
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHost();
    }

    @Override
    public void onPause() {
        Activity activity = hostActivity();
        if (activity != null) {
            BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);
        }
        super.onPause();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MorpheSettingsV5Search.prepareMenu(this, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MorpheSettingsV5Search.handleMenuItem(this, item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }

        activity.setTitle("Morphe");
        BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(
                activity,
                tokens.background,
                tokens.dark
        );

        int toolbarId = MorpheSettingsV4Catalog.resourceId(
                activity,
                "id",
                "toolbar"
        );
        View toolbar = toolbarId == 0 ? null : activity.findViewById(toolbarId);
        if (toolbar != null) {
            toolbar.setBackgroundColor(tokens.background);
            toolbar.setElevation(0);
        }
    }

    private Activity hostActivity() {
        Context context = getContext();
        return context instanceof Activity ? (Activity) context : null;
    }
}
