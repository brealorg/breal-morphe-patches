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

/** Material navigation hub for the split drawer settings pages. */
public final class MorpheSettingsV4NavigationDrawerHubFragment
        extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_NAVIGATION_DRAWER_HUB_ISSUE121_V1";
    public static final String MATERIAL_VISUAL_FOUNDATION_MARKER =
            "MORPHE_BOOST_SETTINGS_NAVIGATION_DRAWER_MATERIAL_"
                    + "ISSUE121_PHASE2_1_V1";
    public static final String ANDROID_SETTINGS_GUIDANCE_PILOT_MARKER =
            "MORPHE_BOOST_SETTINGS_NAVIGATION_DRAWER_STANDARD_LIST_"
                    + "ISSUE121_PHASE2_2_V1";

    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";

    private static final Destination[] DESTINATIONS = new Destination[]{
            new Destination(
                    "Destinations",
                    "Feeds & library",
                    "Home, feeds, saved posts, and history",
                    "ic_subreddit_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_FEEDS_LIBRARY_FRAGMENT
            ),
            new Destination(
                    "Destinations",
                    "Account & tools",
                    "Profile, inbox, drafts, moderation, and search",
                    "ic_person_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_ACCOUNT_TOOLS_FRAGMENT
            ),
            new Destination(
                    "Shortcuts",
                    "Go-to shortcuts",
                    "Community, user, and combined go-to shortcuts",
                    "ic_search_color_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_GO_TO_FRAGMENT
            ),
            new Destination(
                    "Shortcuts",
                    "Quick toggles",
                    "Dark mode and NSFW controls in the drawer",
                    "ic_settings_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_QUICK_TOGGLES_FRAGMENT
            ),
            new Destination(
                    "Personalization",
                    "Subscriptions",
                    "Subscription list visibility, icons, and favorites",
                    "ic_subreddit_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_SUBSCRIPTIONS_FRAGMENT
            ),
            new Destination(
                    "Personalization",
                    "Account switcher",
                    "Avatar and username visibility",
                    "ic_person_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_ACCOUNT_SWITCHER_FRAGMENT
            ),
            new Destination(
                    "Personalization",
                    "Drawer behavior",
                    "Sticky Settings and which edge opens the drawer",
                    "ic_toolbar_24dp",
                    MorpheSettingsV4Catalog.V4_DRAWER_BEHAVIOR_FRAGMENT
            ),
    };

    private MorpheSettingsV4Theme.Tokens tokens;

    public MorpheSettingsV4NavigationDrawerHubFragment() {
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

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(36));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        String currentSection = null;
        LinearLayout group = null;
        for (Destination destination : DESTINATIONS) {
            if (!destination.section.equals(currentSection)) {
                if (currentSection != null) {
                    addSpace(content, 20);
                }
                currentSection = destination.section;
                content.addView(MorpheSettingsV14Ui.sectionLabel(
                        context,
                        tokens,
                        currentSection
                ));
                addSpace(content, 6);
                group = MorpheSettingsV14Ui.standardList(context);
                content.addView(
                        group,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );
            }

            LinearLayout row = createDestinationRow(destination);
            MorpheSettingsV14Ui.addStandardListRow(group, row, tokens);
        }

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
        MorpheSettingsV4Search.prepareMenu(this, menu, true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MorpheSettingsV4Search.handleMenuItem(this, item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private LinearLayout createDestinationRow(
            Destination destination
    ) {
        Context context = requireContext();
        LinearLayout row = MorpheSettingsV14Ui.standardListRow(
                context,
                tokens,
                false
        );
        LinearLayout labels = MorpheSettingsV14Ui.standardListLabels(
                context,
                tokens,
                destination.title,
                ""
        );
        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));
        row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
        row.setOnClickListener(
                view -> openFragment(destination.fragmentName)
        );
        return row;
    }

    private void openFragment(String fragmentName) {
        Activity activity = hostActivity();
        if (activity == null || fragmentName == null) {
            return;
        }
        Intent intent = new Intent(activity, activity.getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        activity.startActivity(intent);
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Navigation drawer");
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
        View toolbar = activity.findViewById(toolbarId);
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

    private static final class Destination {
        final String section;
        final String title;
        final String summary;
        final String iconName;
        final String fragmentName;

        Destination(
                String section,
                String title,
                String summary,
                String iconName,
                String fragmentName
        ) {
            this.section = section;
            this.title = title;
            this.summary = summary;
            this.iconName = iconName;
            this.fragmentName = fragmentName;
        }
    }
}
