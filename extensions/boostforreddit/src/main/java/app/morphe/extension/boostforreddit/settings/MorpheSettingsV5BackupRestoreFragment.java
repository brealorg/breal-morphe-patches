package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

/** V5-owned route into Boost's existing backup and restore activity. */
public final class MorpheSettingsV5BackupRestoreFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_BACKUP_RESTORE_ROUTE_ISSUE121_V1";
    public static final String ZERO_CANONICAL_CONTROLS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_BACKUP_RESTORE_ZERO_CONTROLS_ISSUE121_V1";

    private static final String PAGE_ID =
            "v5/data_and_app/backup_and_restore";

    private MorpheSettingsV4Theme.Tokens tokens;
    private boolean launched;

    public MorpheSettingsV5BackupRestoreFragment() {
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Context context = inflater.getContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
        setHasOptionsMenu(true);

        MorpheSettingsV5Registry.V5PageSpec page =
                MorpheSettingsV5Registry.requirePage(PAGE_ID);
        if (!"MorpheSettingsV5BackupRestoreFragment".equals(page.renderer)
                || page.keys.length != 0
                || !page.isLeaf()) {
            throw new IllegalStateException(
                    "Backup & restore V5 page contract is invalid"
            );
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(tokens.background);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(36));
        scrollView.addView(content);
        content.addView(MorpheSettingsV14Ui.pageIntro(
                context,
                tokens,
                MorpheSettingsV5Registry.introFor(PAGE_ID)
        ));
        content.addView(MorpheSettingsV14Ui.supportingText(
                context,
                tokens,
                "Returning from Boost's backup tool brings you back here."
        ));
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHost();
        if (!launched) {
            launched = true;
            openBackupActivity();
        }
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

    private void openBackupActivity() {
        try {
            Intent intent = new Intent();
            intent.setClassName(
                    requireContext().getPackageName(),
                    "com.rubenmayayo.reddit.BackupActivity"
            );
            startActivity(intent);
        } catch (Throwable throwable) {
            launched = false;
        }
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle(MorpheSettingsV5Registry.titleFor(PAGE_ID));
        BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(
                activity,
                tokens.background,
                tokens.dark
        );
    }

    private Activity hostActivity() {
        Context context = getContext();
        return context instanceof Activity ? (Activity) context : null;
    }

    private int dp(float value) {
        return MorpheSettingsV4Theme.dp(requireContext(), value);
    }
}
