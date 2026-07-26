package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.text.TextUtils;

/** Titles and supporting text for Settings V5 Notifications & account. */
final class MorpheSettingsV5NotificationsAccountMetadata {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_METADATA_ISSUE121_V1";

    private MorpheSettingsV5NotificationsAccountMetadata() {
    }

    static String titleFor(Context context, String key) {
        switch (key) {
            case "pref_all_delete":
                return "Clear all history";
            case "pref_history_delete":
                return "Clear community history";
            case "pref_recent_posts_delete":
                return "Clear post history";
            case "pref_searches_delete":
                return "Clear search history";
            case "pref_recent_posts_save_nsfw":
                return "Include NSFW posts";
            case "pref_history_save":
                return "Save recent communities";
            case "pref_recent_posts_save":
                return "Save recent posts";
            case "pref_search_history_save":
                return "Show recent searches";
            case "morphe_boost_imgur_undelete_enabled":
                return "Automatically undelete Imgur images";
            case "morphe_boost_reddit_undelete_enabled":
                return "Automatically undelete Reddit content";
            case "pref_check_messages_push_clear":
                return "Clear source notification";
            case "pref_check_messages_push":
                return "Reddit push";
            case "pref_check_messages_interval":
                return "Check interval";
            case "pref_check_modmail":
                return "Check moderator mail";
            case "pref_check_messages":
                return "Check notifications";
            case "pref_notifications_configure":
                return "Configure notifications";
            default:
                return key == null ? "" : key;
        }
    }

    static String toggleSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_recent_posts_save_nsfw":
                return "Include NSFW posts in local post history.";
            case "pref_history_save":
                return "Visited communities appear in autocomplete fields.";
            case "pref_recent_posts_save":
                return "Visited posts appear in Boost’s History screen.";
            case "pref_search_history_save":
                return "Keep recent searches available while Boost is running.";
            case "morphe_boost_imgur_undelete_enabled":
                return "Try to restore supported missing Imgur media from archive sources. Disabled by default.";
            case "morphe_boost_reddit_undelete_enabled":
                return "Try to restore supported deleted Reddit posts and comments from archive sources. Disabled by default.";
            case "pref_check_messages":
                return "Periodically check Reddit for new inbox notifications.";
            case "pref_check_modmail":
                return "Include moderator mail when checking for new inbox notifications.";
            case "pref_check_messages_push":
                return "Bridge notifications received by the official Reddit app into Boost. Android notification access is required.";
            case "pref_check_messages_push_clear":
                return "Remove the source Reddit notification after Boost creates its own.";
            default:
                return TextUtils.isEmpty(currentSummary)
                        ? ""
                        : currentSummary;
        }
    }

    static String actionSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_all_delete":
                return "Remove saved post, community, and search history from this device.";
            case "pref_history_delete":
                return "Remove saved community history from this device.";
            case "pref_recent_posts_delete":
                return "Remove saved post history from this device.";
            case "pref_searches_delete":
                return "Remove recent search history from this device.";
            case "pref_notifications_configure":
                return "Open Android notification settings for Boost.";
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
        if ("pref_check_messages_interval".equals(key)) {
            return "Choose how often Boost checks Reddit for new inbox notifications.";
        }
        String pageId = MorpheSettingsV5Registry.pageIdForKey(key);
        return TextUtils.isEmpty(pageId)
                ? ""
                : MorpheSettingsV5Registry.titleFor(pageId);
    }
}
