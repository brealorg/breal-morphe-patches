package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

/** Complete hidden Notifications & account leaves for Settings V5. */
public final class MorpheSettingsV5NotificationsAccountLeafFragment
        extends MorpheSettingsV5XmlPreferenceFragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_LEAF_ISSUE121_V1";
    public static final String CANONICAL_BINDINGS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_BINDINGS_ISSUE121_V1";
    public static final String HIDDEN_HISTORY_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_HIDDEN_HISTORY_ISSUE121_V1";

    private static final String DEFAULT_PAGE_ID =
            "v5/notifications_and_account/notifications_and_inbox/notifications";

    private String pageId = DEFAULT_PAGE_ID;
    private MorpheSettingsV5Registry.V5PageSpec page;

    public MorpheSettingsV5NotificationsAccountLeafFragment() {
        super(
                "pref_privacy_v2",
                "pref_messages_v2",
                "morphe_boost_settings_skeleton"
        );
    }

    @Override
    protected void preparePage() {
        resolvePageId();
        page = MorpheSettingsV5Registry.requirePage(pageId);
        if (!page.isLeaf()
                || !"MorpheSettingsV5NotificationsAccountLeafFragment".equals(
                page.renderer
        )
                || !pageId.startsWith("v5/notifications_and_account/")) {
            throw new IllegalArgumentException(
                    "Notifications & account leaf renderer cannot open " + pageId
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
        return "pref_privacy_v2".equals(resourceName)
                && ("pref_search_history_save".equals(key)
                || "pref_searches_delete".equals(key))
                && page != null
                && page.containsKey(key);
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
        return MorpheSettingsV5NotificationsAccountMetadata.toggleSummaryFor(
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
        return MorpheSettingsV5NotificationsAccountMetadata.actionSummaryFor(
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
