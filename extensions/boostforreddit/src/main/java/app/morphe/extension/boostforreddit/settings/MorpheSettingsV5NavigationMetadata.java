package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.text.TextUtils;

/** Titles and supporting text for Settings V5 Navigation. */
final class MorpheSettingsV5NavigationMetadata {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_METADATA_ISSUE121_V1";

    private MorpheSettingsV5NavigationMetadata() {
    }

    static String titleFor(Context context, String key) {
        switch (key) {
            case "pref_lock_sidebar":
                return "Lock sidebar";
            case "pref_ask_exit":
                return "Confirm exit";
            case "pref_double_exit":
                return "Double tap to exit";
            case "pref_bottom_navigation_hide_on_scroll":
                return "Hide on scroll";
            case "pref_bottom_navigation":
                return "Show bottom navigation";
            case "pref_accounts_show_avatar":
                return "Show avatar";
            case "pref_accounts_show_username":
                return "Show username";
            case "pref_drawer_show_drafts":
                return "Drafts";
            case "pref_drawer_show_history":
                return "History";
            case "pref_drawer_show_frontpage":
                return "Home feed";
            case "pref_drawer_show_inbox":
                return "Inbox";
            case "pref_drawer_show_mod":
                return "Moderation";
            case "pref_drawer_show_popular":
                return "Popular";
            case "pref_drawer_show_profile":
                return "Profile";
            case "pref_drawer_show_saved":
                return "Saved";
            case "pref_drawer_show_search_generic":
                return "Search";
            case "pref_drawer_show_all":
                return "All";
            case "pref_drawer_sticky_settings":
                return "Sticky Settings";
            case "pref_drawer_end":
                return "Switch side";
            case "pref_drawer_show_go_to_subreddit":
                return "Go to community";
            case "pref_drawer_show_go_to":
                return "Go to dropdown";
            case "pref_drawer_show_go_to_user":
                return "Go to user";
            case "pref_drawer_show_blur_switch":
                return "Blur NSFW";
            case "pref_drawer_show_night_mode":
                return "Dark mode";
            case "pref_drawer_show_nsfw_switch":
                return "Show NSFW";
            case "pref_drawer_show_home":
                return "Home";
            case "pref_subscriptions_drawer_show_icon":
                return "Show icons";
            case "pref_subscriptions_drawer":
                return "Show in menu";
            case "pref_subscriptions_only_casual":
                return "Show only favorites";
            case "pref_toolbar":
                return "Hide on scroll";
            case "pref_toolbar_main_action":
                return "Main action";
            default:
                return key == null ? "" : key;
        }
    }

    static String toggleSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_lock_sidebar":
                return "Disable opening the navigation drawer with a swipe.";
            case "pref_ask_exit":
                return "Show a confirmation dialog before leaving Boost.";
            case "pref_double_exit":
                return "Require two Back presses to leave Boost.";
            case "pref_bottom_navigation":
                return "Show the bottom navigation bar on supported Boost screens.";
            case "pref_bottom_navigation_hide_on_scroll":
                return "Hide the bottom navigation while scrolling down.";
            case "pref_accounts_show_avatar":
                return "Show the active account avatar in the account switcher.";
            case "pref_accounts_show_username":
                return "Show the active Reddit username in the account switcher.";
            case "pref_drawer_show_home":
                return "Show the selected default feed in the navigation drawer.";
            case "pref_drawer_show_frontpage":
                return "Show the feed built from your Reddit subscriptions.";
            case "pref_drawer_show_go_to":
                return "Show the combined community, user, and random shortcut.";
            case "pref_drawer_show_go_to_subreddit":
                return "Show a direct shortcut for opening a community.";
            case "pref_drawer_show_go_to_user":
                return "Show a direct shortcut for opening a user profile.";
            case "pref_drawer_sticky_settings":
                return "Keep Settings and the theme toggle pinned to the drawer footer.";
            case "pref_drawer_end":
                return "Open the navigation drawer from the opposite edge.";
            case "pref_subscriptions_drawer":
                return "Show the subscriptions section in the navigation drawer.";
            case "pref_subscriptions_drawer_show_icon":
                return "Show community icons in the subscriptions section.";
            case "pref_subscriptions_only_casual":
                return "Limit the subscriptions section to favorite communities.";
            case "pref_toolbar":
                return "Hide the toolbar while scrolling down.";
            case "pref_toolbar_main_action":
                return "Choose the primary toolbar shortcut.";
            default:
                return TextUtils.isEmpty(currentSummary)
                        ? ""
                        : currentSummary;
        }
    }

    static String searchSummaryFor(Context context, String key) {
        String summary = toggleSummaryFor(key, "");
        if (!TextUtils.isEmpty(summary)) {
            return summary;
        }
        String pageId = MorpheSettingsV5Registry.pageIdForKey(key);
        return TextUtils.isEmpty(pageId)
                ? ""
                : MorpheSettingsV5Registry.titleFor(pageId);
    }
}
