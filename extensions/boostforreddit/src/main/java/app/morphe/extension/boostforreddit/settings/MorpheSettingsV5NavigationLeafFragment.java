package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

/** Complete hidden Navigation leaves for Settings V5. */
public final class MorpheSettingsV5NavigationLeafFragment
        extends MorpheSettingsV5XmlPreferenceFragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_LEAF_ISSUE121_V1";
    public static final String CANONICAL_BINDINGS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_BINDINGS_ISSUE121_V1";
    public static final String WITHHELD_FRIENDS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_WITHHELD_FRIENDS_ISSUE121_V1";

    private static final String DEFAULT_PAGE_ID =
            "v5/navigation/bottom_navigation";

    private String pageId = DEFAULT_PAGE_ID;
    private MorpheSettingsV5Registry.V5PageSpec page;

    public MorpheSettingsV5NavigationLeafFragment() {
        super(
                "pref_views_v2",
                "pref_general_v2",
                "pref_bottom_navigation_v2",
                "pref_drawer_v2",
                "pref_toolbar_v2"
        );
    }

    @Override
    protected void preparePage() {
        resolvePageId();
        page = MorpheSettingsV5Registry.requirePage(pageId);
        if (!page.isLeaf()
                || !"MorpheSettingsV5NavigationLeafFragment".equals(
                page.renderer
        )
                || !pageId.startsWith("v5/navigation/")) {
            throw new IllegalArgumentException(
                    "Navigation leaf renderer cannot open " + pageId
            );
        }
        setPageTitle(MorpheSettingsV5Registry.titleFor(pageId));
    }

    @Override
    protected boolean includeControl(String resourceName, String key) {
        return page != null && page.containsKey(key);
    }

    @Override
    protected String sectionFor(
            String resourceName,
            String key,
            String currentSection
    ) {
        return MorpheSettingsV5Registry.titleFor(pageId);
    }

    @Override
    protected String pageIntro() {
        return MorpheSettingsV5Registry.introFor(pageId);
    }

    @Override
    protected String displaySummary(
            String key,
            String currentSummary
    ) {
        return MorpheSettingsV5NavigationMetadata.toggleSummaryFor(
                key,
                currentSummary
        );
    }

    String currentPageId() {
        return pageId;
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
        Context context = getContext();
        Activity activity = context instanceof Activity
                ? (Activity) context
                : null;
        Intent intent = activity == null ? null : activity.getIntent();
        String requested = intent == null
                ? null
                : intent.getStringExtra(MorpheSettingsV5Registry.EXTRA_PAGE_ID);
        if (!TextUtils.isEmpty(requested)) {
            pageId = requested;
        }
    }
}
