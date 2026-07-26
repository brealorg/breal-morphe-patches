package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

/** Complete hidden Data & app leaves for Settings V5. */
public final class MorpheSettingsV5DataAppLeafFragment
        extends MorpheSettingsV5XmlPreferenceFragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_DATA_APP_LEAF_ISSUE121_V1";
    public static final String CANONICAL_BINDINGS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_DATA_APP_BINDINGS_ISSUE121_V1";
    public static final String SPECIAL_HANDLER_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_DATA_APP_HANDLER_BINDINGS_ISSUE121_V1";

    private static final String DEFAULT_PAGE_ID =
            "v5/data_and_app/storage_and_bandwidth/cache";

    private String pageId = DEFAULT_PAGE_ID;
    private MorpheSettingsV5Registry.V5PageSpec page;

    public MorpheSettingsV5DataAppLeafFragment() {
        super(
                "pref_headers_v2",
                "pref_about_v2",
                "pref_misc_v2",
                "pref_data_v2",
                "morphe_boost_settings_skeleton"
        );
    }

    @Override
    protected void preparePage() {
        resolvePageId();
        page = MorpheSettingsV5Registry.requirePage(pageId);
        if (!page.isLeaf()
                || !"MorpheSettingsV5DataAppLeafFragment".equals(
                page.renderer
        )
                || !pageId.startsWith("v5/data_and_app/")) {
            throw new IllegalArgumentException(
                    "Data & app leaf renderer cannot open " + pageId
            );
        }
        setPageTitle(MorpheSettingsV5Registry.titleFor(pageId));
    }

    @Override
    protected boolean includeControl(String resourceName, String key) {
        return page != null && page.containsKey(key);
    }

    @Override
    protected boolean includeHiddenControl(
            String resourceName,
            String key
    ) {
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
        return MorpheSettingsV5DataAppMetadata.toggleSummaryFor(
                key,
                currentSummary
        );
    }

    @Override
    protected String displayActionSummary(
            String key,
            String currentSummary,
            String tag
    ) {
        return MorpheSettingsV5DataAppMetadata.actionSummaryFor(
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
