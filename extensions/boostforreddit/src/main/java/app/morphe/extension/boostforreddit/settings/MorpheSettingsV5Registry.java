package app.morphe.extension.boostforreddit.settings;
import android.text.TextUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hidden Settings V5 registry for parallel migration waves. */
final class MorpheSettingsV5Registry {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_REGISTRY_ISSUE121_V1";
    static final String APPEARANCE_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_WAVE_ISSUE121_V1";
    static final String READING_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_READING_WAVE_ISSUE121_V1";
    static final String MEDIA_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_MEDIA_WAVE_ISSUE121_V1";
    static final String NOTIFICATIONS_ACCOUNT_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NOTIFICATIONS_ACCOUNT_WAVE_ISSUE121_V1";
    static final String NAVIGATION_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_WAVE_ISSUE121_V1";
    static final String NAVIGATION_ROOT_FLATTEN_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ROOT_FLATTEN_ISSUE121_V1";
    static final String DATA_APP_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_DATA_APP_WAVE_ISSUE121_V1";
    static final String ROOT_OVERVIEW_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_ROOT_OVERVIEW_WAVE_ISSUE121_V1";
    static final String MORPHE_ROOT_FLATTEN_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_MORPHE_ROOT_FLATTEN_ISSUE121_V1";
    static final String WITHHELD_FRIENDS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_WITHHELD_FRIENDS_ISSUE121_V1";
    static final boolean V5_VISIBLE_BY_DEFAULT = true;
    static final String EXTRA_PAGE_ID =
            "morphe_boost_settings_v5_page_id";
    static final String DEFAULT_PAGE_ID = "v5/appearance";

    private static final V5PageSpec[] PAGES = new V5PageSpec[]{
            new V5PageSpec("v5/morphe", "MorpheSettingsV5MorpheFragment", new String[]{}),
            new V5PageSpec("v5/appearance", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/community_header", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/display_and_motion", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/post_layout", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/theme_and_colors", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/typography", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/community_header/community_header", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_toolbar_header_type", "pref_header_show_description", "pref_show_subreddit_header"}),
            new V5PageSpec("v5/appearance/display_and_motion/display_performance", "MorpheSettingsV5AppearanceFragment", new String[]{"morphe_boost_prefer_high_refresh_rate"}),
            new V5PageSpec("v5/appearance/post_layout/cards", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_cards_gallery_carousel", "pref_cards_full", "pref_cards_full_preview", "pref_cards_preview_self_lines", "pref_cards_preview_self", "pref_cards_rounded_corners", "pref_cards_subreddit_icon", "pref_cards_links_as_thumbnails"}),
            new V5PageSpec("v5/appearance/post_layout/compact_layouts", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/post_layout/layout_and_saved_views", "MorpheSettingsV5AppearanceFragment", new String[]{}),
            new V5PageSpec("v5/appearance/post_layout/tablet_layout", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_split_screen"}),
            new V5PageSpec("v5/appearance/theme_and_colors/advanced_appearance", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_colored_nav_bar", "pref_colored_status_bar"}),
            new V5PageSpec("v5/appearance/theme_and_colors/personalization", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_app_icon"}),
            new V5PageSpec("v5/appearance/theme_and_colors/theme", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_theme", "pref_theme_night_start_minutes", "pref_theme_night", "pref_dynamic_colors", "pref_theme_night_end_minutes", "pref_theme_mode_type"}),
            new V5PageSpec("v5/appearance/typography/comments", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_comments_font", "pref_font_size"}),
            new V5PageSpec("v5/appearance/typography/posts", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_title_font", "pref_font_size_title"}),
            new V5PageSpec("v5/appearance/typography/reset", "MorpheSettingsV5AppearanceFragment", new String[]{"action:typography:restore_defaults"}),
            new V5PageSpec("v5/appearance/post_layout/compact_layouts/dense", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_dense_buttons_visible"}),
            new V5PageSpec("v5/appearance/post_layout/compact_layouts/small_cards", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_mini_cards_buttons_visible", "pref_mini_cards_full", "pref_mini_cards_rounded_corners", "pref_mini_cards_truncate_title"}),
            new V5PageSpec("v5/appearance/post_layout/layout_and_saved_views/post_layout", "MorpheSettingsV5AppearanceFragment", new String[]{"pref_show_subreddit_prefix", "pref_view", "pref_view_per_sub", "pref_view_per_subscription", "pref_left_handed"}),
            new V5PageSpec("v5/appearance/post_layout/layout_and_saved_views/saved_community_views", "MorpheSettingsV5AppearanceFragment", new String[]{"action:saved_views:add", "action:saved_views:clear_all"}),
            new V5PageSpec("v5/reading_and_interaction", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/comments", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/composing_and_drafts", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/feeds_and_subscriptions", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/posts", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/comments/comment_actions", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_comments_buttons", "pref_swipe_threshold_percentage", "pref_comments_buttons_collapse_after_action", "pref_comments_button_save", "pref_comments_floating_button", "pref_info_post_upvote_percentage", "pref_swipe_sensitivity_percentage", "pref_swipe_back", "pref_comments_button_parent"}),
            new V5PageSpec("v5/reading_and_interaction/comments/comment_appearance", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_comments_color_pattern", "pref_user_flair_emojis", "pref_user_flair_colors", "pref_comments_show_avatar", "pref_comments_user_flair"}),
            new V5PageSpec("v5/reading_and_interaction/comments/comment_behavior", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_clickable_awards_comments", "pref_default_comment_sorting", "pref_comments_highlight_mine", "pref_comments_image", "pref_show_awards_comments", "pref_comments_clickable_username", "pref_suggested_comment_sorting"}),
            new V5PageSpec("v5/reading_and_interaction/comments/thread_navigation", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_comments_scroll_animation", "pref_comments_click", "pref_comments_collapse_automoderator", "pref_comments_collapse_collapsed", "pref_comments_default_navigation", "pref_comments_animation", "pref_comments_full_collapse", "pref_comments_highlight_new_comments", "pref_comments_load_collapsed", "pref_comments_navigation_bar", "pref_indent_style", "pref_comments_volume_scroll"}),
            new V5PageSpec("v5/reading_and_interaction/composing_and_drafts/drafts", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_manage_drafts", "pref_drafts"}),
            new V5PageSpec("v5/reading_and_interaction/composing_and_drafts/editor", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_use_advanced_editor"}),
            new V5PageSpec("v5/reading_and_interaction/composing_and_drafts/uploads", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_imgur_uploads"}),
            new V5PageSpec("v5/reading_and_interaction/feeds_and_subscriptions/manage_subscriptions", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_edit_subscriptions"}),
            new V5PageSpec("v5/reading_and_interaction/posts/feed_behavior", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_default_sort", "pref_sort_per_subscription", "pref_frontpage_sort"}),
            new V5PageSpec("v5/reading_and_interaction/posts/post_actions", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_post_show_comments_button", "pref_show_hide", "pref_sort_per_sub", "pref_show_read", "pref_post_show_open_button", "pref_post_show_share_button", "pref_posts_floating_button", "pref_upvote_on_save"}),
            new V5PageSpec("v5/reading_and_interaction/posts/post_behavior", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_clickable_awards_posts", "pref_info_username", "pref_show_awards_posts", "pref_flair_emojis", "pref_flair_colors", "pref_info_post_flair", "pref_post_clickable_subreddit", "pref_flair_clickable", "pref_post_clickable_username"}),
            new V5PageSpec("v5/reading_and_interaction/posts/reading_state", "MorpheSettingsV5ReadingLeafFragment", new String[]{"synncit_config", "pref_mark_read_dim_images", "pref_hide_read_permanently", "pref_mark_read_on_scroll", "pref_mark_read", "pref_history_delete_read"}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/content_filters", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/search", "MorpheSettingsV5ReadingFragment", new String[]{}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/content_filters/filter_behavior", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_blur_nsfw_images", "pref_load_nsfw_images", "pref_nsfw"}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/content_filters/muted_content", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_filter_subreddit", "pref_filter_domain", "pref_filter_flair", "pref_filter_username", "pref_filter_keyword"}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/content_filters/post_matching", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_album", "pref_gif", "pref_image", "pref_link", "pref_self", "pref_video"}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/search/advanced_search", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_search_advanced_help"}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/search/search_behavior", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_default_search_period", "pref_default_search_sorting", "morphe_boost_search_open_keyboard_on_entry"}),
            new V5PageSpec("v5/reading_and_interaction/search_and_filters/search/suggestions", "MorpheSettingsV5ReadingLeafFragment", new String[]{"pref_saved_searches", "pref_search_show_random", "pref_search_show_random_nsfw", "pref_search_show_trending", "pref_search_show_trending_searches"}),
            new V5PageSpec("v5/navigation", "MorpheSettingsV5NavigationFragment", new String[]{}),
            new V5PageSpec("v5/navigation/back_and_exit", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_ask_exit", "pref_double_exit"}),
            new V5PageSpec("v5/navigation/bottom_navigation", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_bottom_navigation_hide_on_scroll", "pref_bottom_navigation"}),
            new V5PageSpec("v5/navigation/navigation_drawer", "MorpheSettingsV5NavigationFragment", new String[]{}),
            new V5PageSpec("v5/navigation/toolbar", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_toolbar", "pref_toolbar_main_action"}),
            new V5PageSpec("v5/navigation/navigation_drawer/account_and_tools", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_drawer_show_drafts", "pref_drawer_show_inbox", "pref_drawer_show_mod", "pref_drawer_show_profile", "pref_drawer_show_search_generic"}),
            new V5PageSpec("v5/navigation/navigation_drawer/account_switcher", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_accounts_show_avatar", "pref_accounts_show_username"}),
            new V5PageSpec("v5/navigation/navigation_drawer/drawer_behavior", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_lock_sidebar", "pref_drawer_sticky_settings", "pref_drawer_end"}),
            new V5PageSpec("v5/navigation/navigation_drawer/feeds_and_library", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_drawer_show_history", "pref_drawer_show_frontpage", "pref_drawer_show_popular", "pref_drawer_show_saved", "pref_drawer_show_all", "pref_drawer_show_home"}),
            new V5PageSpec("v5/navigation/navigation_drawer/go_to_shortcuts", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_drawer_show_go_to_subreddit", "pref_drawer_show_go_to", "pref_drawer_show_go_to_user"}),
            new V5PageSpec("v5/navigation/navigation_drawer/quick_toggles", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_drawer_show_blur_switch", "pref_drawer_show_night_mode", "pref_drawer_show_nsfw_switch"}),
            new V5PageSpec("v5/navigation/navigation_drawer/subscriptions", "MorpheSettingsV5NavigationLeafFragment", new String[]{"pref_subscriptions_drawer_show_icon", "pref_subscriptions_drawer", "pref_subscriptions_only_casual"}),
            new V5PageSpec("v5/data_and_app", "MorpheSettingsV5DataAppFragment", new String[]{}),
            new V5PageSpec("v5/data_and_app/about_and_support", "MorpheSettingsV5DataAppFragment", new String[]{}),
            new V5PageSpec("v5/data_and_app/app_behavior_and_compatibility", "MorpheSettingsV5DataAppFragment", new String[]{}),
            new V5PageSpec("v5/data_and_app/backup_and_restore", "MorpheSettingsV5BackupRestoreFragment", new String[]{}),
            new V5PageSpec("v5/data_and_app/settings_experience", "MorpheSettingsV5DataAppFragment", new String[]{}),
            new V5PageSpec("v5/data_and_app/storage_and_bandwidth", "MorpheSettingsV5DataAppFragment", new String[]{}),
            new V5PageSpec("v5/data_and_app/about_and_support/about_boost", "MorpheSettingsV5DataAppLeafFragment", new String[]{"buy_pro", "remove_ads", "about_version", "rate_app", "about_subreddit", "about_faq", "support_launch", "licenses_preference"}),
            new V5PageSpec("v5/data_and_app/about_and_support/author", "MorpheSettingsV5DataAppLeafFragment", new String[]{"contact_dev_key", "about_reddit", "about_twitter"}),
            new V5PageSpec("v5/data_and_app/about_and_support/privacy", "MorpheSettingsV5DataAppLeafFragment", new String[]{"privacy_policy", "pref_gdpr_revoke", "pref_statistics"}),
            new V5PageSpec("v5/data_and_app/app_behavior_and_compatibility/community_shortcuts", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_shortcut_icon_crop"}),
            new V5PageSpec("v5/data_and_app/app_behavior_and_compatibility/compatibility_and_legacy", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_autoplay_swipe", "pref_info_color_indicators", "pref_go_to_sub_dialog", "pref_cards_preview_top", "pref_info_post_shorten_score", "pref_send_floating_button", "pref_info_post_self_image", "pref_info_post_view_count", "pref_toolbar_dropdown", "pref_toolbar_tap_to_go", "pref_cards_legacy"}),
            new V5PageSpec("v5/data_and_app/app_behavior_and_compatibility/other_app_behavior", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_ad_format", "reset_tips"}),
            new V5PageSpec("v5/data_and_app/settings_experience/settings_presentation", "MorpheSettingsV5DataAppLeafFragment", new String[]{"morphe_boost_settings_v4_enabled"}),
            new V5PageSpec("v5/data_and_app/storage_and_bandwidth/cache", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_cache_current_size", "pref_cache_max_size"}),
            new V5PageSpec("v5/data_and_app/storage_and_bandwidth/data_saver", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_reduce_mobile", "pref_reduce_wifi"}),
            new V5PageSpec("v5/data_and_app/storage_and_bandwidth/data_usage", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_download_folders"}),
            new V5PageSpec("v5/data_and_app/storage_and_bandwidth/images", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_load_images"}),
            new V5PageSpec("v5/data_and_app/storage_and_bandwidth/videos", "MorpheSettingsV5DataAppLeafFragment", new String[]{"pref_video_quality_max", "pref_video_quality_min", "pref_video_quality"}),
            new V5PageSpec("v5/media", "MorpheSettingsV5MediaFragment", new String[]{}),
            new V5PageSpec("v5/media/downloads_and_cache", "MorpheSettingsV5MediaFragment", new String[]{}),
            new V5PageSpec("v5/media/images_gifs_and_previews", "MorpheSettingsV5MediaFragment", new String[]{}),
            new V5PageSpec("v5/media/links_and_browser", "MorpheSettingsV5MediaFragment", new String[]{}),
            new V5PageSpec("v5/media/playback_and_autoplay", "MorpheSettingsV5MediaFragment", new String[]{}),
            new V5PageSpec("v5/media/downloads_and_cache/download_folders", "MorpheSettingsV5MediaDownloadFoldersFragment", new String[]{"pref_download_folder_default", "pref_download_folder_gif", "pref_download_folder_img", "pref_download_folder_mp4", "pref_download_folder_per_subreddit"}),
            new V5PageSpec("v5/media/images_gifs_and_previews/native_image_behavior", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_image_loader", "pref_images_deepzoom", "pref_images_fit", "morphe_boost_image_dismiss_sensitivity", "pref_swipe_up_to_dismiss_image", "pref_swipe_dismiss_direction_mode_image", "pref_tap_to_dismiss_image"}),
            new V5PageSpec("v5/media/images_gifs_and_previews/open_behavior", "MorpheSettingsV5MediaLeafFragment", new String[]{"morphe_boost_direct_reddit_gif_tap_action", "morphe_boost_giphy_preview_tap_action", "morphe_boost_static_preview_tap_action"}),
            new V5PageSpec("v5/media/images_gifs_and_previews/preview_behavior", "MorpheSettingsV5MediaLeafFragment", new String[]{"morphe_boost_inline_media_previews_enabled", "morphe_boost_inline_media_preview_show_source_text"}),
            new V5PageSpec("v5/media/images_gifs_and_previews/preview_layout", "MorpheSettingsV5MediaLeafFragment", new String[]{"morphe_boost_inline_media_preview_alignment", "morphe_boost_inline_media_preview_size"}),
            new V5PageSpec("v5/media/links_and_browser/browser", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_browser"}),
            new V5PageSpec("v5/media/links_and_browser/link_handling", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_domain_exceptions", "pref_load_readability"}),
            new V5PageSpec("v5/media/links_and_browser/links_to_open_in_app", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_link_album", "pref_link_deviant", "pref_link_gif", "pref_link_image", "pref_link_neatclip", "pref_link_streamable", "pref_link_vreddit", "pref_link_xkcd"}),
            new V5PageSpec("v5/media/links_and_browser/video_links", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_link_video"}),
            new V5PageSpec("v5/media/playback_and_autoplay/audio", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_video_audio_start_muted"}),
            new V5PageSpec("v5/media/playback_and_autoplay/autoplay", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_autoplay_cards"}),
            new V5PageSpec("v5/media/playback_and_autoplay/media_behavior", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_media_overlay_visibility", "pref_media_viewer"}),
            new V5PageSpec("v5/media/playback_and_autoplay/playback_and_autoplay", "MorpheSettingsV5MediaLeafFragment", new String[]{"pref_tap_to_dismiss_youtube_videos", "pref_video_player"}),
            new V5PageSpec("v5/notifications_and_account", "MorpheSettingsV5NotificationsAccountFragment", new String[]{}),
            new V5PageSpec("v5/notifications_and_account/history_privacy_and_recovery", "MorpheSettingsV5NotificationsAccountFragment", new String[]{}),
            new V5PageSpec("v5/notifications_and_account/notifications_and_inbox", "MorpheSettingsV5NotificationsAccountFragment", new String[]{}),
            new V5PageSpec("v5/notifications_and_account/reddit_account", "MorpheSettingsV5RedditAccountFragment", new String[]{}),
            new V5PageSpec("v5/notifications_and_account/history_privacy_and_recovery/history", "MorpheSettingsV5NotificationsAccountLeafFragment", new String[]{"pref_all_delete", "pref_history_delete", "pref_recent_posts_delete", "pref_searches_delete", "pref_recent_posts_save_nsfw", "pref_history_save", "pref_recent_posts_save", "pref_search_history_save"}),
            new V5PageSpec("v5/notifications_and_account/history_privacy_and_recovery/recovery_and_archives", "MorpheSettingsV5NotificationsAccountLeafFragment", new String[]{"morphe_boost_imgur_undelete_enabled", "morphe_boost_reddit_undelete_enabled"}),
            new V5PageSpec("v5/notifications_and_account/notifications_and_inbox/advanced", "MorpheSettingsV5NotificationsAccountLeafFragment", new String[]{"pref_check_messages_push_clear", "pref_check_messages_push"}),
            new V5PageSpec("v5/notifications_and_account/notifications_and_inbox/notifications", "MorpheSettingsV5NotificationsAccountLeafFragment", new String[]{"pref_check_messages_interval", "pref_check_modmail", "pref_check_messages", "pref_notifications_configure"}),
    };

    private static final Map<String, V5PageSpec> PAGE_MAP;

    static {
        Map<String, V5PageSpec> pages = new LinkedHashMap<>();
        for (V5PageSpec page : PAGES) {
            pages.put(page.pageId, page);
        }
        PAGE_MAP = Collections.unmodifiableMap(pages);
    }

    private MorpheSettingsV5Registry() {
    }

    static V5PageSpec findPage(String pageId) {
        return PAGE_MAP.get(pageId);
    }

    static V5PageSpec requirePage(String pageId) {
        V5PageSpec page = findPage(pageId);
        if (page == null) {
            throw new IllegalArgumentException("Unknown V5 page " + pageId);
        }
        return page;
    }

    private static final V5WithheldSpec[] WITHHELD = new V5WithheldSpec[]{
            new V5WithheldSpec(
                    "pref_drawer_show_friends",
                    "Static implementation exists, but the Friends destination was not proven to return functional Reddit data at runtime."
            )
    };

    static V5WithheldSpec[] allWithheld() {
        return WITHHELD.clone();
    }

    static V5PageSpec[] allPages() {
        return PAGES.clone();
    }

    static String pageIdForKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        for (V5PageSpec page : PAGES) {
            for (String pageKey : page.keys) {
                if (key.equals(pageKey)) {
                    return page.pageId;
                }
            }
        }
        return null;
    }

    static String titleFor(String pageId) {
        switch (pageId) {
            case "v5/root":
                return "Settings";
            case "v5/morphe":
                return "Morphe";
            case "v5/appearance":
                return "Appearance";
            case "v5/appearance/community_header":
                return "Community header";
            case "v5/appearance/display_and_motion":
                return "Display & motion";
            case "v5/appearance/post_layout":
                return "Post layout";
            case "v5/appearance/theme_and_colors":
                return "Theme & colors";
            case "v5/appearance/typography":
                return "Typography";
            case "v5/appearance/community_header/community_header":
                return "Community header";
            case "v5/appearance/display_and_motion/display_performance":
                return "Display performance";
            case "v5/appearance/post_layout/cards":
                return "Cards";
            case "v5/appearance/post_layout/compact_layouts":
                return "Compact layouts";
            case "v5/appearance/post_layout/layout_and_saved_views":
                return "Layout & saved views";
            case "v5/appearance/post_layout/tablet_layout":
                return "Tablet layout";
            case "v5/appearance/theme_and_colors/advanced_appearance":
                return "Advanced appearance";
            case "v5/appearance/theme_and_colors/personalization":
                return "Personalization";
            case "v5/appearance/theme_and_colors/theme":
                return "Theme";
            case "v5/appearance/typography/comments":
                return "Comments";
            case "v5/appearance/typography/posts":
                return "Posts";
            case "v5/appearance/typography/reset":
                return "Reset";
            case "v5/appearance/post_layout/compact_layouts/dense":
                return "Dense";
            case "v5/appearance/post_layout/compact_layouts/small_cards":
                return "Small cards";
            case "v5/appearance/post_layout/layout_and_saved_views/post_layout":
                return "Post layout";
            case "v5/appearance/post_layout/layout_and_saved_views/saved_community_views":
                return "Saved community views";
            case "v5/reading_and_interaction":
                return "Reading & interaction";
            case "v5/reading_and_interaction/comments":
                return "Comments";
            case "v5/reading_and_interaction/composing_and_drafts":
                return "Composing & drafts";
            case "v5/reading_and_interaction/feeds_and_subscriptions":
                return "Feeds & subscriptions";
            case "v5/reading_and_interaction/posts":
                return "Posts";
            case "v5/reading_and_interaction/search_and_filters":
                return "Search & filters";
            case "v5/reading_and_interaction/comments/comment_actions":
                return "Comment actions";
            case "v5/reading_and_interaction/comments/comment_appearance":
                return "Comment appearance";
            case "v5/reading_and_interaction/comments/comment_behavior":
                return "Comment behavior";
            case "v5/reading_and_interaction/comments/thread_navigation":
                return "Thread navigation";
            case "v5/reading_and_interaction/composing_and_drafts/drafts":
                return "Drafts";
            case "v5/reading_and_interaction/composing_and_drafts/editor":
                return "Editor";
            case "v5/reading_and_interaction/composing_and_drafts/uploads":
                return "Uploads";
            case "v5/reading_and_interaction/feeds_and_subscriptions/manage_subscriptions":
                return "Manage subscriptions";
            case "v5/reading_and_interaction/posts/feed_behavior":
                return "Feed behavior";
            case "v5/reading_and_interaction/posts/post_actions":
                return "Post actions";
            case "v5/reading_and_interaction/posts/post_behavior":
                return "Post behavior";
            case "v5/reading_and_interaction/posts/reading_state":
                return "Reading state";
            case "v5/reading_and_interaction/search_and_filters/content_filters":
                return "Content filters";
            case "v5/reading_and_interaction/search_and_filters/search":
                return "Search";
            case "v5/reading_and_interaction/search_and_filters/content_filters/filter_behavior":
                return "Filter behavior";
            case "v5/reading_and_interaction/search_and_filters/content_filters/muted_content":
                return "Muted content";
            case "v5/reading_and_interaction/search_and_filters/content_filters/post_matching":
                return "Post matching";
            case "v5/reading_and_interaction/search_and_filters/search/advanced_search":
                return "Advanced search";
            case "v5/reading_and_interaction/search_and_filters/search/search_behavior":
                return "Search behavior";
            case "v5/reading_and_interaction/search_and_filters/search/suggestions":
                return "Suggestions";
            case "v5/navigation":
                return "Navigation & gestures";
            case "v5/navigation/back_and_exit":
                return "Back & exit";
            case "v5/navigation/bottom_navigation":
                return "Bottom navigation";
            case "v5/navigation/navigation_drawer":
                return "Navigation drawer";
            case "v5/navigation/toolbar":
                return "Toolbar";
            case "v5/navigation/navigation_drawer/account_and_tools":
                return "Account & tools";
            case "v5/navigation/navigation_drawer/account_switcher":
                return "Account switcher";
            case "v5/navigation/navigation_drawer/drawer_behavior":
                return "Drawer behavior";
            case "v5/navigation/navigation_drawer/feeds_and_library":
                return "Feeds & library";
            case "v5/navigation/navigation_drawer/go_to_shortcuts":
                return "Go-to shortcuts";
            case "v5/navigation/navigation_drawer/quick_toggles":
                return "Quick toggles";
            case "v5/navigation/navigation_drawer/subscriptions":
                return "Subscriptions";
            case "v5/data_and_app":
                return "Data & app";
            case "v5/data_and_app/about_and_support":
                return "About & support";
            case "v5/data_and_app/app_behavior_and_compatibility":
                return "App behavior & compatibility";
            case "v5/data_and_app/backup_and_restore":
                return "Backup & restore";
            case "v5/data_and_app/settings_experience":
                return "Settings experience";
            case "v5/data_and_app/storage_and_bandwidth":
                return "Storage & bandwidth";
            case "v5/data_and_app/about_and_support/about_boost":
                return "About Boost";
            case "v5/data_and_app/about_and_support/author":
                return "Author";
            case "v5/data_and_app/about_and_support/privacy":
                return "Privacy";
            case "v5/data_and_app/app_behavior_and_compatibility/community_shortcuts":
                return "Community shortcuts";
            case "v5/data_and_app/app_behavior_and_compatibility/compatibility_and_legacy":
                return "Compatibility & legacy";
            case "v5/data_and_app/app_behavior_and_compatibility/other_app_behavior":
                return "Other app behavior";
            case "v5/data_and_app/settings_experience/settings_presentation":
                return "Settings presentation";
            case "v5/data_and_app/storage_and_bandwidth/cache":
                return "Cache";
            case "v5/data_and_app/storage_and_bandwidth/data_saver":
                return "Data saver";
            case "v5/data_and_app/storage_and_bandwidth/data_usage":
                return "Data usage";
            case "v5/data_and_app/storage_and_bandwidth/images":
                return "Images";
            case "v5/data_and_app/storage_and_bandwidth/videos":
                return "Videos";
            case "v5/media":
                return "Media";
            case "v5/media/downloads_and_cache":
                return "Downloads & cache";
            case "v5/media/images_gifs_and_previews":
                return "Images, GIFs & previews";
            case "v5/media/links_and_browser":
                return "Links & browser";
            case "v5/media/playback_and_autoplay":
                return "Playback & autoplay";
            case "v5/media/downloads_and_cache/download_folders":
                return "Download folders";
            case "v5/media/images_gifs_and_previews/native_image_behavior":
                return "Native image behavior";
            case "v5/media/images_gifs_and_previews/open_behavior":
                return "Open behavior";
            case "v5/media/images_gifs_and_previews/preview_behavior":
                return "Preview behavior";
            case "v5/media/images_gifs_and_previews/preview_layout":
                return "Preview layout";
            case "v5/media/links_and_browser/browser":
                return "Browser";
            case "v5/media/links_and_browser/link_handling":
                return "Link handling";
            case "v5/media/links_and_browser/links_to_open_in_app":
                return "Links to open in app";
            case "v5/media/links_and_browser/video_links":
                return "Video links";
            case "v5/media/playback_and_autoplay/audio":
                return "Audio";
            case "v5/media/playback_and_autoplay/autoplay":
                return "Autoplay";
            case "v5/media/playback_and_autoplay/media_behavior":
                return "Media behavior";
            case "v5/media/playback_and_autoplay/playback_and_autoplay":
                return "Playback & autoplay";
            case "v5/notifications_and_account":
                return "Notifications & account";
            case "v5/notifications_and_account/history_privacy_and_recovery":
                return "History, privacy & recovery";
            case "v5/notifications_and_account/notifications_and_inbox":
                return "Notifications & inbox";
            case "v5/notifications_and_account/reddit_account":
                return "Reddit account";
            case "v5/notifications_and_account/history_privacy_and_recovery/history":
                return "History";
            case "v5/notifications_and_account/history_privacy_and_recovery/recovery_and_archives":
                return "Recovery & archives";
            case "v5/notifications_and_account/notifications_and_inbox/advanced":
                return "Advanced";
            case "v5/notifications_and_account/notifications_and_inbox/notifications":
                return "Notifications";
            default:
                return "Appearance";
        }
    }

    static String introFor(String pageId) {
        switch (pageId) {
            case "v5/root":
                return "Browse every Boost and Morphe setting from one complete overview.";
            case "v5/morphe":
                return "Configure user-facing behavior added by Morphe and the Settings experience.";
            case "v5/appearance":
                return "Control themes, layout, typography, and visual presentation.";
            case "v5/appearance/community_header":
                return "Control the identity and description shown above community feeds.";
            case "v5/appearance/display_and_motion":
                return "Adjust display performance and motion-related behavior.";
            case "v5/appearance/post_layout":
                return "Choose how posts and feed cards are arranged.";
            case "v5/appearance/theme_and_colors":
                return "Choose theme mode, colors, system bars, and app personalization.";
            case "v5/appearance/typography":
                return "Adjust post and comment fonts and text sizes.";
            case "v5/appearance/community_header/community_header":
                return "Choose what community identity appears above community feeds.";
            case "v5/appearance/display_and_motion/display_performance":
                return "Control refresh-rate behavior for smoother scrolling on supported displays.";
            case "v5/appearance/post_layout/cards":
                return "Configure the standard card layout used in feeds.";
            case "v5/appearance/post_layout/compact_layouts":
                return "Configure Boost’s compact feed layouts.";
            case "v5/appearance/post_layout/layout_and_saved_views":
                return "Choose the default layout and per-community layout behavior.";
            case "v5/appearance/post_layout/tablet_layout":
                return "Control the landscape master-detail layout on larger screens.";
            case "v5/appearance/theme_and_colors/advanced_appearance":
                return "Control how app colors extend into Android system bars.";
            case "v5/appearance/theme_and_colors/personalization":
                return "Choose the launcher icon used for Boost.";
            case "v5/appearance/theme_and_colors/theme":
                return "Choose light and dark theme behavior, schedules, and colors.";
            case "v5/appearance/typography/comments":
                return "Choose the font and size used for comment text.";
            case "v5/appearance/typography/posts":
                return "Choose the font and size used for post titles.";
            case "v5/appearance/typography/reset":
                return "Restore all typography settings to their defaults.";
            case "v5/appearance/post_layout/compact_layouts/dense":
                return "Configure controls shown in the Dense layout.";
            case "v5/appearance/post_layout/compact_layouts/small_cards":
                return "Configure the Small cards layout.";
            case "v5/appearance/post_layout/layout_and_saved_views/post_layout":
                return "Choose the default feed layout and whether each community remembers its own layout.";
            case "v5/appearance/post_layout/layout_and_saved_views/saved_community_views":
                return "Add, edit, or remove layouts saved for individual communities and custom feeds.";
            case "v5/reading_and_interaction":
                return "Choose how posts, comments, feeds, search, and composing behave.";
            case "v5/reading_and_interaction/comments":
                return "Control comment actions, appearance, behavior, and thread navigation.";
            case "v5/reading_and_interaction/composing_and_drafts":
                return "Configure editors, drafts, and uploaded composing media.";
            case "v5/reading_and_interaction/feeds_and_subscriptions":
                return "Manage communities, custom feeds, and subscription behavior.";
            case "v5/reading_and_interaction/posts":
                return "Choose how posts behave while browsing and reading.";
            case "v5/reading_and_interaction/search_and_filters":
                return "Configure search behavior and which content appears in feeds.";
            case "v5/reading_and_interaction/comments/comment_actions":
                return "Choose visible comment actions and swipe-to-close behavior.";
            case "v5/reading_and_interaction/comments/comment_appearance":
                return "Choose identity and thread-color details shown with comments.";
            case "v5/reading_and_interaction/comments/comment_behavior":
                return "Control sorting, media previews, awards, and profile actions.";
            case "v5/reading_and_interaction/comments/thread_navigation":
                return "Control collapsing, navigation controls, and movement through a thread.";
            case "v5/reading_and_interaction/composing_and_drafts/drafts":
                return "Control draft saving and open drafts stored by Boost or Reddit.";
            case "v5/reading_and_interaction/composing_and_drafts/editor":
                return "Choose the composing interface used for posts and comments.";
            case "v5/reading_and_interaction/composing_and_drafts/uploads":
                return "Open media previously uploaded while composing.";
            case "v5/reading_and_interaction/feeds_and_subscriptions/manage_subscriptions":
                return "Open community and custom-feed management.";
            case "v5/reading_and_interaction/posts/feed_behavior":
                return "Choose default sorting and whether communities remember their own sort.";
            case "v5/reading_and_interaction/posts/post_actions":
                return "Choose actions available directly from post rows and menus.";
            case "v5/reading_and_interaction/posts/post_behavior":
                return "Choose post identity, flair, awards, and tap behavior.";
            case "v5/reading_and_interaction/posts/reading_state":
                return "Control read-state tracking, hiding, Synccit, and local reset.";
            case "v5/reading_and_interaction/search_and_filters/content_filters":
                return "Control NSFW visibility, muted content, and matching post types.";
            case "v5/reading_and_interaction/search_and_filters/search":
                return "Configure search defaults, suggestions, and field-search help.";
            case "v5/reading_and_interaction/search_and_filters/content_filters/filter_behavior":
                return "Control whether NSFW content and its images are shown or blurred.";
            case "v5/reading_and_interaction/search_and_filters/content_filters/muted_content":
                return "Hide posts by community, domain, user, word, or flair.";
            case "v5/reading_and_interaction/search_and_filters/content_filters/post_matching":
                return "Choose which post types are included by content filters.";
            case "v5/reading_and_interaction/search_and_filters/search/advanced_search":
                return "See the field prefixes supported by Boost search.";
            case "v5/reading_and_interaction/search_and_filters/search/search_behavior":
                return "Choose search defaults and keyboard behavior.";
            case "v5/reading_and_interaction/search_and_filters/search/suggestions":
                return "Choose saved, random, and trending suggestions shown in Search.";
            case "v5/navigation":
                return "Configure back behavior, bars, and the navigation drawer.";
            case "v5/navigation/back_and_exit":
                return "Choose how Boost confirms or handles leaving the app.";
            case "v5/navigation/bottom_navigation":
                return "Control whether the bottom navigation is shown and how it behaves while scrolling.";
            case "v5/navigation/navigation_drawer":
                return "Choose the drawer layout, shortcuts, feeds, account display, and quick toggles.";
            case "v5/navigation/toolbar":
                return "Control toolbar visibility and its primary action.";
            case "v5/navigation/navigation_drawer/account_and_tools":
                return "Choose account and utility destinations shown in the navigation drawer.";
            case "v5/navigation/navigation_drawer/account_switcher":
                return "Choose the identity details shown in the account switcher.";
            case "v5/navigation/navigation_drawer/drawer_behavior":
                return "Control drawer gestures, placement, and the pinned Settings footer.";
            case "v5/navigation/navigation_drawer/feeds_and_library":
                return "Choose feed and library destinations shown in the navigation drawer.";
            case "v5/navigation/navigation_drawer/go_to_shortcuts":
                return "Choose direct navigation shortcuts shown in the drawer.";
            case "v5/navigation/navigation_drawer/quick_toggles":
                return "Choose display and content toggles shown in the drawer.";
            case "v5/navigation/navigation_drawer/subscriptions":
                return "Configure the subscriptions section in the navigation drawer.";
            case "v5/data_and_app":
                return "Manage storage, compatibility, app behavior, backup, and support.";
            case "v5/data_and_app/about_and_support":
                return "View app information, legal information, and support actions.";
            case "v5/data_and_app/app_behavior_and_compatibility":
                return "Manage app-wide compatibility options and remaining global behavior.";
            case "v5/data_and_app/backup_and_restore":
                return "Open Boost's existing backup and restore tool.";
            case "v5/data_and_app/settings_experience":
                return "Choose which Settings presentation Boost uses.";
            case "v5/data_and_app/storage_and_bandwidth":
                return "Control data usage, caching, and bandwidth-sensitive behavior.";
            case "v5/data_and_app/about_and_support/about_boost":
                return "View Boost information, support the app, and open legal details.";
            case "v5/data_and_app/about_and_support/author":
                return "Contact the developer and open the official social profiles.";
            case "v5/data_and_app/about_and_support/privacy":
                return "Control privacy reporting and consent-related actions.";
            case "v5/data_and_app/app_behavior_and_compatibility/community_shortcuts":
                return "Configure the icon used by community launcher shortcuts.";
            case "v5/data_and_app/app_behavior_and_compatibility/compatibility_and_legacy":
                return "Manage remaining compatibility options retained from classic Boost.";
            case "v5/data_and_app/app_behavior_and_compatibility/other_app_behavior":
                return "Configure remaining app-wide behavior and reset guidance tips.";
            case "v5/data_and_app/settings_experience/settings_presentation":
                return "Choose between Morphe Material settings and the classic Boost presentation.";
            case "v5/data_and_app/storage_and_bandwidth/cache":
                return "Clear cached media and choose the maximum cache size.";
            case "v5/data_and_app/storage_and_bandwidth/data_saver":
                return "Reduce media size on mobile data or Wi-Fi.";
            case "v5/data_and_app/storage_and_bandwidth/data_usage":
                return "Open Boost download-folder configuration.";
            case "v5/data_and_app/storage_and_bandwidth/images":
                return "Choose when images are loaded.";
            case "v5/data_and_app/storage_and_bandwidth/videos":
                return "Choose automatic and bounded video quality behavior.";
            case "v5/media":
                return "Control downloads, image previews, link handling, and playback.";
            case "v5/media/downloads_and_cache":
                return "Choose where downloaded media is stored and organized.";
            case "v5/media/images_gifs_and_previews":
                return "Control native image behavior and inline media previews.";
            case "v5/media/links_and_browser":
                return "Choose how links and supported domains open.";
            case "v5/media/playback_and_autoplay":
                return "Configure media viewers, video playback, audio, and autoplay.";
            case "v5/media/downloads_and_cache/download_folders":
                return "Choose destinations for images, videos, GIFs, and other downloads.";
            case "v5/media/images_gifs_and_previews/native_image_behavior":
                return "Control loading, zoom, and dismissal in Boost’s native image viewer.";
            case "v5/media/images_gifs_and_previews/open_behavior":
                return "Choose what opens when supported inline previews and GIFs are tapped.";
            case "v5/media/images_gifs_and_previews/preview_behavior":
                return "Choose whether inline media previews and their source text are shown.";
            case "v5/media/images_gifs_and_previews/preview_layout":
                return "Choose the size and horizontal alignment of inline previews.";
            case "v5/media/links_and_browser/browser":
                return "Choose the browser used when a link opens outside Boost.";
            case "v5/media/links_and_browser/link_handling":
                return "Control readable previews and domains excluded from in-app handling.";
            case "v5/media/links_and_browser/links_to_open_in_app":
                return "Choose which supported link types open directly inside Boost.";
            case "v5/media/links_and_browser/video_links":
                return "Choose whether supported YouTube links use Boost’s internal player.";
            case "v5/media/playback_and_autoplay/audio":
                return "Choose the initial audio state for video playback.";
            case "v5/media/playback_and_autoplay/autoplay":
                return "Choose when videos start automatically while browsing.";
            case "v5/media/playback_and_autoplay/media_behavior":
                return "Choose the media viewer and when overlay controls are visible.";
            case "v5/media/playback_and_autoplay/playback_and_autoplay":
                return "Choose the video player and tap-to-dismiss behavior.";
            case "v5/notifications_and_account":
                return "Control inbox checks, Android notifications, local history, and archive recovery.";
            case "v5/notifications_and_account/history_privacy_and_recovery":
                return "Manage local browsing history and supported archive recovery.";
            case "v5/notifications_and_account/notifications_and_inbox":
                return "Control inbox polling, Reddit push integration, and Android notifications.";
            case "v5/notifications_and_account/reddit_account":
                return "Review how Reddit-hosted account preferences relate to Boost.";
            case "v5/notifications_and_account/history_privacy_and_recovery/history":
                return "Choose which local history Boost keeps and clear stored history when needed.";
            case "v5/notifications_and_account/history_privacy_and_recovery/recovery_and_archives":
                return "Choose whether supported missing Reddit and Imgur content is restored from archives.";
            case "v5/notifications_and_account/notifications_and_inbox/advanced":
                return "Configure optional Reddit push bridging and source-notification cleanup.";
            case "v5/notifications_and_account/notifications_and_inbox/notifications":
                return "Choose how Boost checks the inbox and open Android notification settings.";
            default:
                return "";
        }
    }

    static String[] childrenFor(String pageId) {
        switch (pageId) {
            case "v5/root":
                return new String[]{"v5/morphe", "v5/appearance", "v5/reading_and_interaction", "v5/navigation", "v5/media", "v5/notifications_and_account", "v5/data_and_app"};
            case "v5/appearance":
                return new String[]{"v5/appearance/community_header", "v5/appearance/display_and_motion", "v5/appearance/post_layout", "v5/appearance/theme_and_colors", "v5/appearance/typography"};
            case "v5/appearance/community_header":
                return new String[]{"v5/appearance/community_header/community_header"};
            case "v5/appearance/display_and_motion":
                return new String[]{"v5/appearance/display_and_motion/display_performance"};
            case "v5/appearance/post_layout":
                return new String[]{"v5/appearance/post_layout/cards", "v5/appearance/post_layout/compact_layouts", "v5/appearance/post_layout/layout_and_saved_views", "v5/appearance/post_layout/tablet_layout"};
            case "v5/appearance/theme_and_colors":
                return new String[]{"v5/appearance/theme_and_colors/advanced_appearance", "v5/appearance/theme_and_colors/personalization", "v5/appearance/theme_and_colors/theme"};
            case "v5/appearance/typography":
                return new String[]{"v5/appearance/typography/comments", "v5/appearance/typography/posts", "v5/appearance/typography/reset"};
            case "v5/appearance/post_layout/compact_layouts":
                return new String[]{"v5/appearance/post_layout/compact_layouts/dense", "v5/appearance/post_layout/compact_layouts/small_cards"};
            case "v5/appearance/post_layout/layout_and_saved_views":
                return new String[]{"v5/appearance/post_layout/layout_and_saved_views/post_layout", "v5/appearance/post_layout/layout_and_saved_views/saved_community_views"};
            case "v5/reading_and_interaction":
                return new String[]{"v5/reading_and_interaction/comments", "v5/reading_and_interaction/composing_and_drafts", "v5/reading_and_interaction/feeds_and_subscriptions", "v5/reading_and_interaction/posts", "v5/reading_and_interaction/search_and_filters"};
            case "v5/reading_and_interaction/comments":
                return new String[]{"v5/reading_and_interaction/comments/comment_actions", "v5/reading_and_interaction/comments/comment_appearance", "v5/reading_and_interaction/comments/comment_behavior", "v5/reading_and_interaction/comments/thread_navigation"};
            case "v5/reading_and_interaction/composing_and_drafts":
                return new String[]{"v5/reading_and_interaction/composing_and_drafts/drafts", "v5/reading_and_interaction/composing_and_drafts/editor", "v5/reading_and_interaction/composing_and_drafts/uploads"};
            case "v5/reading_and_interaction/feeds_and_subscriptions":
                return new String[]{"v5/reading_and_interaction/feeds_and_subscriptions/manage_subscriptions"};
            case "v5/reading_and_interaction/posts":
                return new String[]{"v5/reading_and_interaction/posts/feed_behavior", "v5/reading_and_interaction/posts/post_actions", "v5/reading_and_interaction/posts/post_behavior", "v5/reading_and_interaction/posts/reading_state"};
            case "v5/reading_and_interaction/search_and_filters":
                return new String[]{"v5/reading_and_interaction/search_and_filters/content_filters", "v5/reading_and_interaction/search_and_filters/search"};
            case "v5/reading_and_interaction/search_and_filters/content_filters":
                return new String[]{"v5/reading_and_interaction/search_and_filters/content_filters/filter_behavior", "v5/reading_and_interaction/search_and_filters/content_filters/muted_content", "v5/reading_and_interaction/search_and_filters/content_filters/post_matching"};
            case "v5/reading_and_interaction/search_and_filters/search":
                return new String[]{"v5/reading_and_interaction/search_and_filters/search/advanced_search", "v5/reading_and_interaction/search_and_filters/search/search_behavior", "v5/reading_and_interaction/search_and_filters/search/suggestions"};
            case "v5/navigation":
                return new String[]{"v5/navigation/back_and_exit", "v5/navigation/bottom_navigation", "v5/navigation/navigation_drawer", "v5/navigation/toolbar"};
            case "v5/navigation/navigation_drawer":
                return new String[]{"v5/navigation/navigation_drawer/account_and_tools", "v5/navigation/navigation_drawer/account_switcher", "v5/navigation/navigation_drawer/drawer_behavior", "v5/navigation/navigation_drawer/feeds_and_library", "v5/navigation/navigation_drawer/go_to_shortcuts", "v5/navigation/navigation_drawer/quick_toggles", "v5/navigation/navigation_drawer/subscriptions"};
            case "v5/data_and_app":
                return new String[]{"v5/data_and_app/about_and_support", "v5/data_and_app/app_behavior_and_compatibility", "v5/data_and_app/backup_and_restore", "v5/data_and_app/settings_experience", "v5/data_and_app/storage_and_bandwidth"};
            case "v5/data_and_app/about_and_support":
                return new String[]{"v5/data_and_app/about_and_support/about_boost", "v5/data_and_app/about_and_support/author", "v5/data_and_app/about_and_support/privacy"};
            case "v5/data_and_app/app_behavior_and_compatibility":
                return new String[]{"v5/data_and_app/app_behavior_and_compatibility/community_shortcuts", "v5/data_and_app/app_behavior_and_compatibility/compatibility_and_legacy", "v5/data_and_app/app_behavior_and_compatibility/other_app_behavior"};
            case "v5/data_and_app/settings_experience":
                return new String[]{"v5/data_and_app/settings_experience/settings_presentation"};
            case "v5/data_and_app/storage_and_bandwidth":
                return new String[]{"v5/data_and_app/storage_and_bandwidth/cache", "v5/data_and_app/storage_and_bandwidth/data_saver", "v5/data_and_app/storage_and_bandwidth/data_usage", "v5/data_and_app/storage_and_bandwidth/images", "v5/data_and_app/storage_and_bandwidth/videos"};
            case "v5/media":
                return new String[]{"v5/media/downloads_and_cache", "v5/media/images_gifs_and_previews", "v5/media/links_and_browser", "v5/media/playback_and_autoplay"};
            case "v5/media/downloads_and_cache":
                return new String[]{"v5/media/downloads_and_cache/download_folders"};
            case "v5/media/images_gifs_and_previews":
                return new String[]{"v5/media/images_gifs_and_previews/native_image_behavior", "v5/media/images_gifs_and_previews/open_behavior", "v5/media/images_gifs_and_previews/preview_behavior", "v5/media/images_gifs_and_previews/preview_layout"};
            case "v5/media/links_and_browser":
                return new String[]{"v5/media/links_and_browser/browser", "v5/media/links_and_browser/link_handling", "v5/media/links_and_browser/links_to_open_in_app", "v5/media/links_and_browser/video_links"};
            case "v5/media/playback_and_autoplay":
                return new String[]{"v5/media/playback_and_autoplay/audio", "v5/media/playback_and_autoplay/autoplay", "v5/media/playback_and_autoplay/media_behavior", "v5/media/playback_and_autoplay/playback_and_autoplay"};
            case "v5/notifications_and_account":
                return new String[]{"v5/notifications_and_account/history_privacy_and_recovery", "v5/notifications_and_account/notifications_and_inbox", "v5/notifications_and_account/reddit_account"};
            case "v5/notifications_and_account/history_privacy_and_recovery":
                return new String[]{"v5/notifications_and_account/history_privacy_and_recovery/history", "v5/notifications_and_account/history_privacy_and_recovery/recovery_and_archives"};
            case "v5/notifications_and_account/notifications_and_inbox":
                return new String[]{"v5/notifications_and_account/notifications_and_inbox/notifications", "v5/notifications_and_account/notifications_and_inbox/advanced"};
            default:
                return new String[0];
        }
    }

    static final class V5WithheldSpec {
        final String key;
        final String reason;

        V5WithheldSpec(String key, String reason) {
            this.key = key;
            this.reason = reason;
        }
    }

    static final class V5PageSpec {
        final String pageId;
        final String renderer;
        final String[] keys;

        V5PageSpec(String pageId, String renderer, String[] keys) {
            this.pageId = pageId;
            this.renderer = renderer;
            this.keys = keys == null ? new String[0] : keys.clone();
        }

        boolean isLeaf() {
            return childrenFor(pageId).length == 0;
        }

        boolean containsKey(String key) {
            if (TextUtils.isEmpty(key)) {
                return false;
            }
            for (String pageKey : keys) {
                if (key.equals(pageKey)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return pageId + ":" + Arrays.toString(keys);
        }
    }
}
