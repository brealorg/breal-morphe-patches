package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
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

/** V5-owned status page for Reddit-hosted account preferences. */
public final class MorpheSettingsV5RedditAccountFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_REDDIT_ACCOUNT_ISSUE121_V1";
    public static final String ZERO_CANONICAL_CONTROLS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_REDDIT_ACCOUNT_ZERO_CANONICAL_CONTROLS_ISSUE121_V1";

    private static final String PAGE_ID =
            "v5/notifications_and_account/reddit_account";

    private MorpheSettingsV4Theme.Tokens tokens;

    public MorpheSettingsV5RedditAccountFragment() {
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
        if (!"MorpheSettingsV5RedditAccountFragment".equals(page.renderer)
                || page.keys.length != 0
                || !page.isLeaf()) {
            throw new IllegalStateException(
                    "Reddit account V5 page contract is invalid"
            );
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(36));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        content.addView(MorpheSettingsV14Ui.pageIntro(
                context,
                tokens,
                MorpheSettingsV5Registry.introFor(PAGE_ID)
        ));
        addSpace(content, 20);
        content.addView(MorpheSettingsV14Ui.sectionLabel(
                context,
                tokens,
                "Account preferences"
        ));
        content.addView(MorpheSettingsV14Ui.supportingText(
                context,
                tokens,
                "Boost has no app-local controls in this category. "
                        + "Reddit-hosted preferences follow the active account "
                        + "and are managed through Reddit."
        ));
        return scrollView;
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
        activity.setTitle(MorpheSettingsV5Registry.titleFor(PAGE_ID));
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

    private void addSpace(LinearLayout parent, int heightDp) {
        parent.addView(
                new View(requireContext()),
                new LinearLayout.LayoutParams(1, dp(heightDp))
        );
    }

    private int dp(float value) {
        return MorpheSettingsV4Theme.dp(requireContext(), value);
    }
}
