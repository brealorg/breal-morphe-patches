package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.text.TextUtils;

/** Titles and consequence-oriented supporting text for V5 Reading pages. */
final class MorpheSettingsV5ReadingMetadata {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_READING_METADATA_ISSUE121_V1";
    static final String SUPPORTING_TEXT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_READING_STATUS_VALUE_CONSEQUENCE_ISSUE121_V1";

    private MorpheSettingsV5ReadingMetadata() {
    }

    static String titleFor(Context context, String key) {
        switch (key) {
            case "pref_comments_buttons":
                return "Buttons always visible";
            case "pref_swipe_threshold_percentage":
                return "Distance threshold";
            case "pref_comments_buttons_collapse_after_action":
                return "Hide buttons after voting/saving";
            case "pref_comments_button_save":
                return "Save";
            case "pref_comments_floating_button":
                return "Show floating button";
            case "pref_info_post_upvote_percentage":
                return "Show post upvote percentage";
            case "pref_swipe_sensitivity_percentage":
                return "Swipe sensitivity";
            case "pref_swipe_back":
                return "Swipe to close";
            case "pref_comments_button_parent":
                return "Up button";
            case "pref_comments_color_pattern":
                return "Color pattern";
            case "pref_user_flair_emojis":
                return "Show emojis";
            case "pref_user_flair_colors":
                return "Show flair colors";
            case "pref_comments_show_avatar":
                return "Show user avatar";
            case "pref_comments_user_flair":
                return "Show user flair";
            case "pref_clickable_awards_comments":
                return "Clickable awards";
            case "pref_default_comment_sorting":
                return "Default sort";
            case "pref_comments_highlight_mine":
                return "Highlight my username";
            case "pref_comments_image":
                return "Post media preview size";
            case "pref_show_awards_comments":
                return "Show awards";
            case "pref_comments_clickable_username":
                return "Tap username to view profile";
            case "pref_suggested_comment_sorting":
                return "Use suggested sort";
            case "pref_comments_scroll_animation":
                return "Animate navigation";
            case "pref_comments_click":
                return "Click to collapse comment";
            case "pref_comments_collapse_automoderator":
                return "Collapse AutoModerator";
            case "pref_comments_collapse_collapsed":
                return "Collapse disruptive comments";
            case "pref_comments_default_navigation":
                return "Default navigation mode";
            case "pref_comments_animation":
                return "Expand/collapse animation";
            case "pref_comments_full_collapse":
                return "Hide text of collapsed comments";
            case "pref_comments_highlight_new_comments":
                return "Highlight new comments";
            case "pref_comments_load_collapsed":
                return "Load collapsed by default";
            case "pref_comments_navigation_bar":
                return "Show navigation bar";
            case "pref_indent_style":
                return "Thread level indicator";
            case "pref_comments_volume_scroll":
                return "Volume key navigation";
            case "pref_manage_drafts":
                return "Post drafts";
            case "pref_drafts":
                return "Save drafts";
            case "pref_use_advanced_editor":
                return "Advanced fullscreen editor";
            case "pref_imgur_uploads":
                return "Uploaded images";
            case "pref_edit_subscriptions":
                return "Subscriptions";
            case "pref_default_sort":
                return "Default sort";
            case "pref_sort_per_subscription":
                return "Remember per community";
            case "pref_frontpage_sort":
                return "Sort Home posts by";
            case "pref_post_show_comments_button":
                return "Comments";
            case "pref_show_hide":
                return "Hide";
            case "pref_sort_per_sub":
                return "Manage saved sorts";
            case "pref_show_read":
                return "Mark read";
            case "pref_post_show_open_button":
                return "Open in external app";
            case "pref_post_show_share_button":
                return "Share";
            case "pref_posts_floating_button":
                return "Show floating button";
            case "pref_upvote_on_save":
                return "Upvote on save";
            case "pref_clickable_awards_posts":
                return "Clickable awards";
            case "pref_info_username":
                return "Show author";
            case "pref_show_awards_posts":
                return "Show awards";
            case "pref_flair_emojis":
                return "Show emojis";
            case "pref_flair_colors":
                return "Show flair colors";
            case "pref_info_post_flair":
                return "Show post flair";
            case "pref_post_clickable_subreddit":
                return "Tap community to visit";
            case "pref_flair_clickable":
                return "Tap flair to search";
            case "pref_post_clickable_username":
                return "Tap username to view profile";
            case "synncit_config":
                return "Configure Synccit";
            case "pref_mark_read_dim_images":
                return "Dim images in read posts";
            case "pref_hide_read_permanently":
                return "Hide read permanently";
            case "pref_mark_read_on_scroll":
                return "Mark as read on scroll";
            case "pref_mark_read":
                return "Mark posts as read";
            case "pref_history_delete_read":
                return "Reset read posts";
            case "pref_blur_nsfw_images":
                return "Blur images in NSFW posts";
            case "pref_load_nsfw_images":
                return "Show images in NSFW posts";
            case "pref_nsfw":
                return "Show NSFW";
            case "pref_filter_subreddit":
                return "Communities";
            case "pref_filter_domain":
                return "Domains";
            case "pref_filter_flair":
                return "Flairs";
            case "pref_filter_username":
                return "Users";
            case "pref_filter_keyword":
                return "Words";
            case "pref_album":
                return "Albums";
            case "pref_gif":
                return "Gifs";
            case "pref_image":
                return "Images";
            case "pref_link":
                return "Links";
            case "pref_self":
                return "Text";
            case "pref_video":
                return "Videos";
            case "pref_search_advanced_help":
                return "Field search help";
            case "pref_default_search_period":
                return "Default period";
            case "pref_default_search_sorting":
                return "Default sort";
            case "morphe_boost_search_open_keyboard_on_entry":
                return "Open keyboard when entering Search";
            case "pref_saved_searches":
                return "Saved searches";
            case "pref_search_show_random":
                return "Show random";
            case "pref_search_show_random_nsfw":
                return "Show random NSFW";
            case "pref_search_show_trending":
                return "Show trending communities";
            case "pref_search_show_trending_searches":
                return "Show trending today";
            default:
                return key == null ? "" : key;
        }
    }

    static String toggleSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_swipe_back":
                return "Swipe right from the edge to return.";
            case "pref_suggested_comment_sorting":
                return "Use the sort recommended by the community or post.";
            case "pref_comments_click":
                return "When disabled, long-press a comment to collapse it.";
            case "pref_comments_collapse_collapsed":
                return "Includes crowd-controlled, downvoted, and deleted comments.";
            case "pref_comments_full_collapse":
                return "Show only the comment header while collapsed.";
            case "pref_comments_load_collapsed":
                return "Start top-level comments with their replies collapsed.";
            case "pref_comments_volume_scroll":
                return "Use volume keys to move between comments.";
            case "pref_comments_scroll_animation":
                return "Animate movement between comments.";
            case "pref_use_advanced_editor":
                return "Disable to use the compact reply dialog.";
            case "pref_sort_per_subscription":
                return "Each community remembers the most recently selected sort.";
            case "pref_mark_read":
                return "Opening a post records it as read.";
            case "pref_mark_read_on_scroll":
                return "Record a post as read after it leaves the screen.";
            case "pref_hide_read_permanently":
                return "Hide read posts through Reddit\u2019s hidden-post list; login required.";
            case "pref_mark_read_dim_images":
                return "Dim media after a post is marked read.";
            case "pref_nsfw":
                return "Show content labeled Not Safe For Work.";
            case "morphe_boost_search_open_keyboard_on_entry":
                return "Focus Search and show the keyboard immediately.";
            default:
                return "";
        }
    }

    static String actionSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_manage_drafts":
                return "Open drafts saved on Reddit.";
            case "pref_imgur_uploads":
                return "Open images uploaded to Imgur through Boost.";
            case "pref_edit_subscriptions":
                return "Manage communities and custom feeds.";
            case "pref_default_sort":
                return "Choose the default sort used outside Home.";
            case "pref_frontpage_sort":
                return "Choose the default sort used for Home.";
            case "pref_sort_per_sub":
                return "Edit sorts remembered for individual communities.";
            case "synncit_config":
                return "Sync read-post state between supported clients.";
            case "pref_history_delete_read":
                return "Clear the read-post history stored on this device.";
            case "pref_saved_searches":
                return "View and manage searches saved in Boost.";
            case "pref_search_advanced_help":
                return "See supported field prefixes and search examples.";
            case "pref_comments_color_pattern":
                return "Choose colors used to show comment nesting.";
            case "pref_filter_subreddit":
                return "Hide posts from selected communities.";
            case "pref_filter_domain":
                return "Hide posts from selected domains.";
            case "pref_filter_username":
                return "Hide posts from selected users.";
            case "pref_filter_keyword":
                return "Hide posts containing selected words in the title.";
            case "pref_filter_flair":
                return "Hide posts with selected flairs.";
            default:
                return TextUtils.isEmpty(currentSummary) ? "" : currentSummary;
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
        String pageId = MorpheSettingsV5Registry.pageIdForKey(key);
        return TextUtils.isEmpty(pageId)
                ? ""
                : MorpheSettingsV5Registry.titleFor(pageId);
    }
}
