package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.text.TextUtils;

/** Titles and supporting text for Settings V5 Data & app. */
final class MorpheSettingsV5DataAppMetadata {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_DATA_APP_METADATA_ISSUE121_V1";

    private MorpheSettingsV5DataAppMetadata() {
    }

    static String titleFor(Context context, String key) {
        switch (key) {
            case "buy_pro":
                return "Boost PRO";
            case "remove_ads":
                return "Remove ads";
            case "about_version":
                return "Changelog";
            case "rate_app":
                return "Do you like this app?";
            case "about_subreddit":
                return "Feedback and support";
            case "about_faq":
                return "Help/FAQ";
            case "support_launch":
                return "Launch the rocket";
            case "licenses_preference":
                return "Licenses";
            case "contact_dev_key":
                return "Email";
            case "about_reddit":
                return "reddit";
            case "about_twitter":
                return "Twitter";
            case "privacy_policy":
                return "Privacy policy";
            case "pref_gdpr_revoke":
                return "Revoke GDPR consent";
            case "pref_statistics":
                return "Send statistics";
            case "pref_shortcut_icon_crop":
                return "Crop community icon";
            case "pref_autoplay_swipe":
                return "Autoplay videos in Swipe view";
            case "pref_info_color_indicators":
                return "Colored link type indicators";
            case "pref_go_to_sub_dialog":
                return "Go to community in a dialog";
            case "pref_cards_preview_top":
                return "Previews on top";
            case "pref_info_post_shorten_score":
                return "Shorten score";
            case "pref_send_floating_button":
                return "Show floating send button";
            case "pref_info_post_self_image":
                return "Show images from text posts";
            case "pref_info_post_view_count":
                return "Show view count";
            case "pref_toolbar_dropdown":
                return "Subscriptions dropdown in toolbar";
            case "pref_toolbar_tap_to_go":
                return "Tap toolbar to show subs";
            case "pref_cards_legacy":
                return "Use legacy cards layout";
            case "pref_ad_format":
                return "Preferred Ad format";
            case "reset_tips":
                return "Reset tips";
            case "morphe_boost_settings_v4_enabled":
                return "Material settings";
            case "pref_cache_current_size":
                return "Clear cache";
            case "pref_cache_max_size":
                return "Maximum cache size";
            case "pref_reduce_mobile":
                return "Mobile data saver";
            case "pref_reduce_wifi":
                return "Wifi data saver";
            case "pref_download_folders":
                return "Configure downloads";
            case "pref_load_images":
                return "Load images";
            case "pref_video_quality_max":
                return "Maximum quality";
            case "pref_video_quality_min":
                return "Minimum quality";
            case "pref_video_quality":
                return "Video quality";
            default:
                return key == null ? "" : key;
        }
    }

    static String toggleSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_statistics":
                return "Automatically send usage statistics and crash reports to help improve Boost.";
            case "pref_shortcut_icon_crop":
                return "Crop community launcher shortcut icons into circles.";
            case "pref_info_color_indicators":
                return "Color image tags to indicate the detected link type.";
            case "pref_go_to_sub_dialog":
                return "Show the community chooser in a dialog.";
            case "pref_cards_preview_top":
                return "Show image previews above post titles.";
            case "pref_info_post_shorten_score":
                return "Use shortened vote scores in post metadata.";
            case "pref_send_floating_button":
                return "Show a floating send button while composing.";
            case "pref_info_post_self_image":
                return "Show images found inside text posts.";
            case "pref_info_post_view_count":
                return "Show view counts on your own and moderated posts.";
            case "pref_toolbar_dropdown":
                return "Show subscriptions from the toolbar dropdown.";
            case "pref_toolbar_tap_to_go":
                return "Tap the toolbar to open subscriptions.";
            case "pref_cards_legacy":
                return "Use the legacy card layout with metadata below the title.";
            case "morphe_boost_settings_v4_enabled":
                return "Use Morphe's Material task-based settings. Reopen Settings to apply.";
            case "pref_reduce_mobile":
                return "Load lower-size media while using mobile data.";
            case "pref_reduce_wifi":
                return "Load lower-size media while using Wi-Fi.";
            default:
                return TextUtils.isEmpty(currentSummary)
                        ? ""
                        : currentSummary;
        }
    }

    static String actionSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "buy_pro":
                return "Unlock Boost PRO.";
            case "remove_ads":
                return "Support Boost and remove advertising.";
            case "about_version":
                return "View the installed version and changelog.";
            case "rate_app":
                return "Rate Boost in Google Play.";
            case "about_subreddit":
                return "Open r/BoostForReddit for feedback and support.";
            case "about_faq":
                return "Open Boost help and frequently asked questions.";
            case "support_launch":
                return "Give Boost some love.";
            case "licenses_preference":
                return "View open-source licenses used by Boost.";
            case "contact_dev_key":
                return "Email mayayo.dev@gmail.com.";
            case "about_reddit":
                return "Open u/rmayayo on Reddit.";
            case "about_twitter":
                return "Open @rmayayo on Twitter.";
            case "privacy_policy":
                return "Open the Boost privacy policy.";
            case "pref_gdpr_revoke":
                return "Withdraw consent and reopen the privacy consent flow.";
            case "reset_tips":
                return "Restore Boost guidance tips that were previously dismissed.";
            case "pref_cache_current_size":
                return "Delete cached images and videos.";
            case "pref_download_folders":
                return "Open Boost download-folder configuration.";
            default:
                return TextUtils.isEmpty(currentSummary)
                        ? ""
                        : currentSummary;
        }
    }

    static String searchSummaryFor(Context context, String key) {
        String summary = actionSummaryFor(key, "");
        if (TextUtils.isEmpty(summary)) {
            summary = toggleSummaryFor(key, "");
        }
        if (!TextUtils.isEmpty(summary)) {
            return summary;
        }
        switch (key) {
            case "pref_autoplay_swipe":
                return "Choose when videos autoplay in Swipe view.";
            case "pref_ad_format":
                return "Choose the preferred advertisement layout.";
            case "pref_cache_max_size":
                return "Choose the maximum disk space used by cached media.";
            case "pref_load_images":
                return "Choose when Boost loads images.";
            case "pref_video_quality_max":
                return "Choose the upper bound used by automatic video quality.";
            case "pref_video_quality_min":
                return "Choose the lower bound used by automatic video quality.";
            case "pref_video_quality":
                return "Choose fixed or automatic video quality behavior.";
            default:
                String pageId = MorpheSettingsV5Registry.pageIdForKey(key);
                return TextUtils.isEmpty(pageId)
                        ? ""
                        : MorpheSettingsV5Registry.titleFor(pageId);
        }
    }
}
