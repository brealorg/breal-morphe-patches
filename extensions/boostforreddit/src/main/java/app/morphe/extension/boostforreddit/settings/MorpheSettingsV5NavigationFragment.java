package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

/** Hidden Navigation hubs for the parallel Settings V5 tree. */
public final class MorpheSettingsV5NavigationFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_FRAGMENT_ISSUE121_V1";
    public static final String HIDDEN_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_HIDDEN_WAVE_ISSUE121_V1";

    private static final String DEFAULT_PAGE_ID = "v5/navigation";

    private MorpheSettingsV4Theme.Tokens tokens;
    private MorpheSettingsV5Registry.V5PageSpec page;
    private String pageId = DEFAULT_PAGE_ID;

    public MorpheSettingsV5NavigationFragment() {
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
        resolvePageId();
        page = MorpheSettingsV5Registry.requirePage(pageId);
        if (page.isLeaf()
                || !"MorpheSettingsV5NavigationFragment".equals(
                page.renderer
        )) {
            throw new IllegalArgumentException(
                    "Navigation hub renderer cannot open " + pageId
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

        String intro = MorpheSettingsV5Registry.introFor(pageId);
        if (!TextUtils.isEmpty(intro)) {
            content.addView(MorpheSettingsV14Ui.pageIntro(
                    context,
                    tokens,
                    intro
            ));
            addSpace(content, 16);
        }

        String[] children = MorpheSettingsV5Registry.childrenFor(pageId);
        if (children.length == 0) {
            throw new IllegalStateException(
                    "Navigation hub has no children: " + pageId
            );
        }
        renderDestinations(content, children);
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

    void openPage(String targetPageId) {
        MorpheSettingsV5Navigation.openPage(this, targetPageId);
    }

    String currentPageId() {
        return pageId;
    }

    private void renderDestinations(
            LinearLayout content,
            String[] children
    ) {
        LinearLayout list = MorpheSettingsV14Ui.standardList(requireContext());
        content.addView(
                list,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        for (String childId : children) {
            LinearLayout row = MorpheSettingsV14Ui.standardListRow(
                    requireContext(),
                    tokens,
                    false
            );
            row.addView(
                    MorpheSettingsV14Ui.standardListLabels(
                            requireContext(),
                            tokens,
                            MorpheSettingsV5Registry.titleFor(childId),
                            ""
                    ),
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );
            row.addView(MorpheSettingsV14Ui.chevron(requireContext(), tokens));
            row.setOnClickListener(view -> openPage(childId));
            MorpheSettingsV14Ui.addStandardListRow(list, row, tokens);
        }
    }

    private void resolvePageId() {
        Bundle arguments = getArguments();
        if (arguments != null) {
            String requested = arguments.getString(
                    MorpheSettingsV5Registry.EXTRA_PAGE_ID
            );
            if (!TextUtils.isEmpty(requested)) {
                pageId = requested;
                return;
            }
        }
        Activity activity = hostActivity();
        Intent intent = activity == null ? null : activity.getIntent();
        String requested = intent == null
                ? null
                : intent.getStringExtra(MorpheSettingsV5Registry.EXTRA_PAGE_ID);
        if (!TextUtils.isEmpty(requested)) {
            pageId = requested;
        }
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle(MorpheSettingsV5Registry.titleFor(pageId));
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
