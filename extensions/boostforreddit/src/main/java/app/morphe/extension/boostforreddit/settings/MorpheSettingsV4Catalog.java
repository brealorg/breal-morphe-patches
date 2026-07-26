package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MorpheSettingsV4Catalog {
    static final String BOOST_PACKAGE = "com.rubenmayayo.reddit";
    static final String BACKUP_ACTIVITY =
            "com.rubenmayayo.reddit.BackupActivity";
    static final String V4_APPEARANCE_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4AppearanceFragment";
    static final String V4_APP_ICON_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4AppIconFragment";
    static final String V4_POST_VIEWS_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4PostViewsFragment";
    static final String V4_SAVED_VIEWS_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4SavedViewsFragment";
    static final String V4_FONTS_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4FontsFragment";
    static final String V4_TOOLBAR_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4ToolbarFragment";
    static final String V4_NAVIGATION_TOOLBAR_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$NavigationToolbar";
    static final String V4_NAVIGATION_BOTTOM_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$NavigationBottom";
    static final String V4_NAVIGATION_DRAWER_HUB_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NavigationDrawerHubFragment";
    static final String V4_NAVIGATION_BACK_EXIT_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$NavigationBackExit";
    static final String V4_DRAWER_FEEDS_LIBRARY_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerFeedsLibrary";
    static final String V4_DRAWER_ACCOUNT_TOOLS_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerAccountTools";
    static final String V4_DRAWER_GO_TO_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerGoToShortcuts";
    static final String V4_DRAWER_QUICK_TOGGLES_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerQuickToggles";
    static final String V4_DRAWER_SUBSCRIPTIONS_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerSubscriptions";
    static final String V4_DRAWER_ACCOUNT_SWITCHER_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerAccountSwitcher";
    static final String V4_DRAWER_BEHAVIOR_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$DrawerBehavior";
    static final String V4_DATA_STORAGE_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4DataStorageFragment";
    static final String V4_DOWNLOADS_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4DownloadsFragment";
    private static final String V4_NATIVE_PAGES =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$";
    static final String CLASSIC_APPEARANCE_FRAGMENT =
            "com.rubenmayayo.reddit.ui.preferences.v2."
                    + "PreferenceFragmentAppearanceCompat";

    private static final String ANDROID_NAMESPACE =
            "http://schemas.android.com/apk/res/android";
    private static final String FRAGMENT_PREFIX =
            "com.rubenmayayo.reddit.ui.preferences.v2.";

    static final class Leaf {
        final String title;
        final String summary;
        final String iconName;
        final String fragmentName;
        final String resourceName;
        final String activityName;

        Leaf(
                String title,
                String summary,
                String iconName,
                String fragmentName,
                String resourceName,
                String activityName
        ) {
            this.title = title;
            this.summary = summary;
            this.iconName = iconName;
            this.fragmentName = fragmentName;
            this.resourceName = resourceName;
            this.activityName = activityName;
        }

        static Leaf fragment(
                String title,
                String summary,
                String iconName,
                String fragmentClass,
                String resourceName
        ) {
            return new Leaf(
                    title,
                    summary,
                    iconName,
                    fragmentClass,
                    resourceName,
                    null
            );
        }

        static Leaf activity(
                String title,
                String summary,
                String iconName,
                String activityClass
        ) {
            return new Leaf(
                    title,
                    summary,
                    iconName,
                    null,
                    null,
                    activityClass
            );
        }
    }

    static final class Category {
        final String id;
        final String title;
        final String summary;
        final String iconName;
        final Leaf[] leaves;

        Category(
                String id,
                String title,
                String summary,
                String iconName,
                Leaf... leaves
        ) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.iconName = iconName;
            this.leaves = leaves;
        }
    }

    static final class RootGroup {
        final String id;
        final String title;
        final String summary;
        final String iconName;
        final boolean includesMorphe;
        final String[] categoryIds;

        RootGroup(
                String id,
                String title,
                String summary,
                String iconName,
                boolean includesMorphe,
                String... categoryIds
        ) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.iconName = iconName;
            this.includesMorphe = includesMorphe;
            this.categoryIds = categoryIds;
        }
    }

    static final class SearchItem {
        final String title;
        final String summary;
        final String category;
        final String iconName;
        final String fragmentName;
        final String activityName;
        final String preferenceKey;
        final String pageId;

        SearchItem(
                String title,
                String summary,
                String category,
                String iconName,
                String fragmentName,
                String activityName,
                String preferenceKey
        ) {
            this(
                    title,
                    summary,
                    category,
                    iconName,
                    fragmentName,
                    activityName,
                    preferenceKey,
                    null
            );
        }

        SearchItem(
                String title,
                String summary,
                String category,
                String iconName,
                String fragmentName,
                String activityName,
                String preferenceKey,
                String pageId
        ) {
            this.title = title;
            this.summary = summary;
            this.category = category;
            this.iconName = iconName;
            this.fragmentName = fragmentName;
            this.activityName = activityName;
            this.preferenceKey = preferenceKey;
            this.pageId = pageId;
        }

        boolean matches(String normalizedQuery) {
            if (TextUtils.isEmpty(normalizedQuery)) {
                return true;
            }
            return normalize(title).contains(normalizedQuery)
                    || normalize(summary).contains(normalizedQuery)
                    || normalize(category).contains(normalizedQuery)
                    || normalize(preferenceKey).contains(normalizedQuery);
        }
    }

    private static final Leaf MORPHE = Leaf.fragment(
            "Patch features",
            "Media previews, recovery, search, performance, and Settings",
            "ic_puzzle_24dp",
            V4_NATIVE_PAGES + "Morphe",
            "morphe_boost_settings_skeleton"
    );

    private static final Category[] CATEGORIES = new Category[]{
            new Category(
                    "theme_colors",
                    "Theme & colors",
                    "Choose the app theme, color behavior, and system-bar appearance",
                    "ic_color_lens_24dp",
                    Leaf.fragment(
                            "Theme & colors",
                            "Theme, colors, app icon, and system bars",
                            "ic_color_lens_24dp",
                            V4_APPEARANCE_FRAGMENT,
                            null
                    )
            ),
            new Category(
                    "community_header",
                    "Community header",
                    "Control the title, description, and header shown for communities",
                    "ic_subreddit_24dp",
                    nativeLeaf(
                            "Community header",
                            "Community title, description, and header presentation",
                            "ic_subreddit_24dp",
                            "Headers",
                            "pref_headers_v2"
                    )
            ),
            new Category(
                    "post_layout",
                    "Post layout",
                    "Control how posts and feed cards are arranged",
                    "ic_view_carousel_24dp",
                    Leaf.fragment(
                            "Post layout",
                            "Cards, compact layouts, saved views, and tablet layout",
                            "ic_view_carousel_24dp",
                            V4_POST_VIEWS_FRAGMENT,
                            null
                    )
            ),
            new Category(
                    "typography",
                    "Typography",
                    "Adjust text size and font presentation throughout Boost",
                    "ic_format_size_24dp",
                    Leaf.fragment(
                            "Typography",
                            "Post and comment fonts with live previews",
                            "ic_format_size_24dp",
                            V4_FONTS_FRAGMENT,
                            null
                    )
            ),
            new Category(
                    "display_motion",
                    "Display & motion",
                    "Adjust display behavior, animation, and refresh-rate preferences",
                    "ic_toolbar_24dp"
            ),
            new Category(
                    "posts",
                    "Posts",
                    "Choose how posts behave while browsing and reading",
                    "ic_post_24dp",
                    nativeLeaf(
                            "Posts",
                            "Post display, actions, feeds, and reading state",
                            "ic_post_24dp",
                            "Posts",
                            "pref_posts_v2"
                    )
            ),
            new Category(
                    "comments",
                    "Comments",
                    "Control comment display, actions, and thread behavior",
                    "ic_comment_outline_white_24dp",
                    nativeLeaf(
                            "Comments",
                            "Comment display, actions, navigation, and threads",
                            "ic_comment_outline_white_24dp",
                            "Comments",
                            "pref_comments_v2"
                    )
            ),
            new Category(
                    "search_filters",
                    "Search & filters",
                    "Configure search entry, suggestions, and content filtering",
                    "ic_search_color_24dp",
                    nativeLeaf(
                            "Search",
                            "Search behavior, defaults, and suggestions",
                            "ic_search_color_24dp",
                            "Search",
                            "pref_search_v2"
                    ),
                    nativeLeaf(
                            "Content filters",
                            "Post matching, muted content, and filter behavior",
                            "ic_filter_list_24dp",
                            "Filters",
                            "pref_filters_v2"
                    )
            ),
            new Category(
                    "feeds_subscriptions",
                    "Feeds & subscriptions",
                    "Manage communities, custom feeds, and subscription behavior",
                    "ic_subreddit_24dp"
            ),
            new Category(
                    "composing_drafts",
                    "Composing & drafts",
                    "Configure editors, drafts, and uploaded composing media",
                    "ic_post_24dp"
            ),
            new Category(
                    "navigation",
                    "Navigation & gestures",
                    "Choose how you move around Boost and reach common destinations",
                    "ic_toolbar_24dp",
                    Leaf.fragment(
                            "Toolbar",
                            "Main action and hide-on-scroll behavior",
                            "ic_toolbar_24dp",
                            V4_NAVIGATION_TOOLBAR_FRAGMENT,
                            null
                    ),
                    Leaf.fragment(
                            "Bottom navigation",
                            "Visibility and hide-on-scroll behavior",
                            "ic_view_carousel_24dp",
                            V4_NAVIGATION_BOTTOM_FRAGMENT,
                            null
                    ),
                    Leaf.fragment(
                            "Navigation drawer",
                            "Destinations, shortcuts, subscriptions, and behavior",
                            "ic_settings_24dp",
                            V4_NAVIGATION_DRAWER_HUB_FRAGMENT,
                            null
                    ),
                    Leaf.fragment(
                            "Back & exit",
                            "Exit confirmation and double-back behavior",
                            "ic_restore_black_24dp",
                            V4_NAVIGATION_BACK_EXIT_FRAGMENT,
                            null
                    )
            ),
            new Category(
                    "playback_autoplay",
                    "Playback & autoplay",
                    "Control video, audio, autoplay, and player behavior",
                    "ic_photo_outline_24dp",
                    nativeLeaf(
                            "Playback & autoplay",
                            "Video, audio, autoplay, and player behavior",
                            "ic_photo_outline_24dp",
                            "Media",
                            "pref_media_v2"
                    )
            ),
            new Category(
                    "images_previews",
                    "Images, GIFs & previews",
                    "Configure media previews, images, GIFs, and tap actions",
                    "ic_photo_outline_24dp"
            ),
            new Category(
                    "links_browser",
                    "Links & browser",
                    "Choose how links open and which browser behavior Boost uses",
                    "ic_link_24dp",
                    nativeLeaf(
                            "Links & browser",
                            "Browser, video links, and in-app link handling",
                            "ic_link_24dp",
                            "Links",
                            "pref_links_v2"
                    )
            ),
            new Category(
                    "downloads_cache",
                    "Downloads & cache",
                    "Manage download locations and downloaded media behavior",
                    "ic_save_24dp",
                    Leaf.fragment(
                            "Downloads & cache",
                            "Download folders and folder organization",
                            "ic_save_24dp",
                            V4_DOWNLOADS_FRAGMENT,
                            "pref_downloads_v2"
                    )
            ),
            new Category(
                    "notifications_inbox",
                    "Notifications & inbox",
                    "Control notifications, messages, and inbox behavior",
                    "ic_notifications_black_24dp",
                    nativeLeaf(
                            "Notifications & inbox",
                            "Messages, notification checks, tone, and inbox behavior",
                            "ic_notifications_black_24dp",
                            "Messages",
                            "pref_messages_v2"
                    )
            ),
            new Category(
                    "reddit_account",
                    "Reddit account",
                    "Open account and Reddit-specific preferences",
                    "ic_person_24dp",
                    Leaf.fragment(
                            "Reddit account",
                            "Website and account preferences",
                            "ic_person_24dp",
                            FRAGMENT_PREFIX + "PreferenceFragmentAccountCompat",
                            null
                    )
            ),
            new Category(
                    "history_privacy_recovery",
                    "History, privacy & recovery",
                    "Manage history, privacy, and supported archive recovery",
                    "ic_restore_black_24dp",
                    nativeLeaf(
                            "History & privacy",
                            "History, recent items, and privacy controls",
                            "ic_restore_black_24dp",
                            "Privacy",
                            "pref_privacy_v2"
                    )
            ),
            new Category(
                    "storage_bandwidth",
                    "Storage & bandwidth",
                    "Control data usage, caching, and bandwidth-sensitive behavior",
                    "outline_data_usage_24",
                    Leaf.fragment(
                            "Storage & bandwidth",
                            "Data saver, quality, images, cache, and storage",
                            "outline_data_usage_24",
                            V4_DATA_STORAGE_FRAGMENT,
                            "pref_data_v2"
                    )
            ),
            new Category(
                    "backup_restore",
                    "Backup & restore",
                    "Export or restore supported Boost and Morphe preferences",
                    "ic_save_24dp",
                    Leaf.activity(
                            "Backup & restore",
                            "Export or import Boost settings",
                            "ic_save_24dp",
                            BACKUP_ACTIVITY
                    )
            ),
            new Category(
                    "app_behavior_compatibility",
                    "App behavior & compatibility",
                    "Manage app-wide compatibility options and global behavior",
                    "ic_settings_24dp",
                    nativeLeaf(
                            "App behavior & compatibility",
                            "Compatibility, global behavior, and remaining legacy options",
                            "ic_settings_24dp",
                            "General",
                            "pref_general_v2"
                    )
            ),
            new Category(
                    "settings_experience",
                    "Settings experience",
                    "Choose which Settings presentation Boost uses",
                    "ic_settings_24dp"
            ),
            new Category(
                    "about_support",
                    "About & support",
                    "View app information, legal information, and support actions",
                    "ic_help_24dp",
                    nativeLeaf(
                            "About & support",
                            "Support, licenses, privacy, and version information",
                            "ic_help_24dp",
                            "About",
                            "pref_about_v2"
                    )
            )
    };

    private static final RootGroup[] ROOT_GROUPS = new RootGroup[]{
            new RootGroup(
                    "root_morphe",
                    "Morphe",
                    "Features added by Morphe patches and the Settings experience",
                    "ic_puzzle_24dp",
                    true
            ),
            new RootGroup(
                    "root_appearance",
                    "Appearance",
                    "Themes, layout, typography, and visual presentation",
                    "ic_color_lens_24dp",
                    false,
                    "theme_colors",
                    "community_header",
                    "post_layout",
                    "typography",
                    "display_motion"
            ),
            new RootGroup(
                    "root_reading_interaction",
                    "Reading & interaction",
                    "Posts, comments, feeds, search, and composing behavior",
                    "ic_post_24dp",
                    false,
                    "posts",
                    "comments",
                    "search_filters",
                    "feeds_subscriptions",
                    "composing_drafts"
            ),
            new RootGroup(
                    "root_navigation",
                    "Navigation",
                    "Movement, destinations, drawer behavior, and gestures",
                    "ic_toolbar_24dp",
                    false,
                    "navigation"
            ),
            new RootGroup(
                    "root_media",
                    "Media",
                    "Playback, previews, links, downloads, and bandwidth",
                    "ic_photo_outline_24dp",
                    false,
                    "playback_autoplay",
                    "images_previews",
                    "links_browser",
                    "downloads_cache"
            ),
            new RootGroup(
                    "root_notifications_account",
                    "Notifications & account",
                    "Inbox behavior, account options, history, privacy, and recovery",
                    "ic_notifications_black_24dp",
                    false,
                    "notifications_inbox",
                    "reddit_account",
                    "history_privacy_recovery"
            ),
            new RootGroup(
                    "root_data_app",
                    "Data & app",
                    "Storage, backup, app behavior, Settings, and app information",
                    "ic_settings_24dp",
                    false,
                    "storage_bandwidth",
                    "backup_restore",
                    "app_behavior_compatibility",
                    "settings_experience",
                    "about_support"
            )
    };

    private MorpheSettingsV4Catalog() {
    }

    static Leaf morphe() {
        return MORPHE;
    }

    static Category[] categories() {
        return CATEGORIES.clone();
    }

    static RootGroup[] rootGroups() {
        return ROOT_GROUPS.clone();
    }

    static RootGroup findRootGroup(String id) {
        for (RootGroup rootGroup : ROOT_GROUPS) {
            if (rootGroup.id.equals(id)) {
                return rootGroup;
            }
        }
        return null;
    }

    static Category findCategory(String id) {
        for (Category category : CATEGORIES) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    static boolean opensDirectly(Category category) {
        return category != null
                && category.leaves.length == 1
                && category.title.equals(category.leaves[0].title);
    }

    static List<SearchItem> buildSearchIndex(Context context) {
        List<SearchItem> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addLeafAndXml(context, result, seen, "Morphe", MORPHE);
        for (Category category : CATEGORIES) {
            if (!opensDirectly(category)) {
                addTaskPageSearchItem(result, seen, category);
            }
            for (Leaf leaf : category.leaves) {
                addLeafAndXml(context, result, seen, category.title, leaf);
            }
        }
        addV4NavigationSearchItems(context, result, seen);
        addV4AppearanceSearchItems(result, seen);
        addV4PostViewsSearchItems(result, seen);
        addV4FontsSearchItems(result, seen);
        return result;
    }

    private static void addTaskPageSearchItem(
            List<SearchItem> result,
            Set<String> seen,
            Category category
    ) {
        addSearchItem(
                result,
                seen,
                new SearchItem(
                        category.title,
                        category.summary,
                        "Settings",
                        category.iconName,
                        null,
                        null,
                        null,
                        category.id
                )
        );
    }

    private static void addV4NavigationSearchItems(
            Context context,
            List<SearchItem> result,
            Set<String> seen
    ) {
        addV4NavigationXmlSearchItems(
                context, result, seen, "pref_toolbar_v2"
        );
        addV4NavigationXmlSearchItems(
                context, result, seen, "pref_bottom_navigation_v2"
        );
        addV4NavigationXmlSearchItems(
                context, result, seen, "pref_drawer_v2"
        );
        addV4NavigationXmlSearchItems(
                context, result, seen, "pref_general_v2"
        );
    }

    private static void addV4NavigationXmlSearchItems(
            Context context,
            List<SearchItem> result,
            Set<String> seen,
            String resourceName
    ) {
        Resources resources = context.getResources();
        int resourceId = resourceId(context, "xml", resourceName);
        if (resourceId == 0) {
            return;
        }

        XmlResourceParser parser = null;
        try {
            parser = resources.getXml(resourceId);
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }
                String tag = parser.getName();
                if ("PreferenceScreen".equals(tag)
                        || "PreferenceCategory".equals(tag)) {
                    continue;
                }

                String key = attributeText(resources, parser, "key");
                if (!isNavigationExposedKey(key)) {
                    continue;
                }

                String title = attributeText(resources, parser, "title");
                String section = navigationSectionForKey(key);
                String fragmentName = navigationFragmentForKey(key);
                if (TextUtils.isEmpty(title)
                        || TextUtils.isEmpty(section)
                        || TextUtils.isEmpty(fragmentName)) {
                    continue;
                }

                addSearchItem(
                        result,
                        seen,
                        new SearchItem(
                                title,
                                attributeText(resources, parser, "summary"),
                                navigationSearchCategory(
                                        fragmentName,
                                        section
                                ),
                                "ic_toolbar_24dp",
                                fragmentName,
                                null,
                                key
                        )
                );
            }
        } catch (Exception ignored) {
            // Search indexing is best-effort; navigation remains available.
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private static String navigationFragmentForKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return "";
        }
        switch (key) {
            case "pref_toolbar_main_action":
            case "pref_toolbar":
                return V4_NAVIGATION_TOOLBAR_FRAGMENT;
            case "pref_bottom_navigation":
            case "pref_bottom_navigation_hide_on_scroll":
                return V4_NAVIGATION_BOTTOM_FRAGMENT;
            case "pref_drawer_show_home":
            case "pref_drawer_show_frontpage":
            case "pref_drawer_show_popular":
            case "pref_drawer_show_all":
            case "pref_drawer_show_saved":
            case "pref_drawer_show_history":
                return V4_DRAWER_FEEDS_LIBRARY_FRAGMENT;
            case "pref_drawer_show_profile":
            case "pref_drawer_show_inbox":
            case "pref_drawer_show_drafts":
            case "pref_drawer_show_mod":
            case "pref_drawer_show_search_generic":
                return V4_DRAWER_ACCOUNT_TOOLS_FRAGMENT;
            case "pref_drawer_show_go_to":
            case "pref_drawer_show_go_to_subreddit":
            case "pref_drawer_show_go_to_user":
                return V4_DRAWER_GO_TO_FRAGMENT;
            case "pref_drawer_show_night_mode":
            case "pref_drawer_show_nsfw_switch":
            case "pref_drawer_show_blur_switch":
                return V4_DRAWER_QUICK_TOGGLES_FRAGMENT;
            case "pref_subscriptions_drawer":
            case "pref_subscriptions_drawer_show_icon":
            case "pref_subscriptions_only_casual":
                return V4_DRAWER_SUBSCRIPTIONS_FRAGMENT;
            case "pref_accounts_show_avatar":
            case "pref_accounts_show_username":
                return V4_DRAWER_ACCOUNT_SWITCHER_FRAGMENT;
            case "pref_drawer_sticky_settings":
            case "pref_drawer_end":
                return V4_DRAWER_BEHAVIOR_FRAGMENT;
            case "pref_ask_exit":
            case "pref_double_exit":
                return V4_NAVIGATION_BACK_EXIT_FRAGMENT;
            default:
                return "";
        }
    }

    private static String navigationSectionForKey(String key) {
        String fragmentName = navigationFragmentForKey(key);
        if (V4_NAVIGATION_TOOLBAR_FRAGMENT.equals(fragmentName)) {
            return "Toolbar";
        }
        if (V4_NAVIGATION_BOTTOM_FRAGMENT.equals(fragmentName)) {
            return "Bottom navigation";
        }
        if (V4_DRAWER_FEEDS_LIBRARY_FRAGMENT.equals(fragmentName)) {
            return "Feeds & library";
        }
        if (V4_DRAWER_ACCOUNT_TOOLS_FRAGMENT.equals(fragmentName)) {
            return "Account & tools";
        }
        if (V4_DRAWER_GO_TO_FRAGMENT.equals(fragmentName)) {
            return "Go-to shortcuts";
        }
        if (V4_DRAWER_QUICK_TOGGLES_FRAGMENT.equals(fragmentName)) {
            return "Quick toggles";
        }
        if (V4_DRAWER_SUBSCRIPTIONS_FRAGMENT.equals(fragmentName)) {
            return "Subscriptions";
        }
        if (V4_DRAWER_ACCOUNT_SWITCHER_FRAGMENT.equals(fragmentName)) {
            return "Account switcher";
        }
        if (V4_DRAWER_BEHAVIOR_FRAGMENT.equals(fragmentName)) {
            return "Drawer behavior";
        }
        if (V4_NAVIGATION_BACK_EXIT_FRAGMENT.equals(fragmentName)) {
            return "Back & exit";
        }
        return "";
    }

    private static String navigationSearchCategory(
            String fragmentName,
            String section
    ) {
        String category = "Navigation & gestures";
        if (fragmentName.startsWith(
                "app.morphe.extension.boostforreddit.settings."
                        + "MorpheSettingsV4NativePages$Drawer"
        )) {
            category += " · Navigation drawer";
        }
        return category + " · " + section;
    }

    private static boolean isNavigationExposedKey(String key) {
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        switch (key) {
            case "pref_toolbar_main_action":
            case "pref_toolbar":
            case "pref_bottom_navigation":
            case "pref_bottom_navigation_hide_on_scroll":
            case "pref_drawer_show_home":
            case "pref_drawer_show_frontpage":
            case "pref_drawer_show_popular":
            case "pref_drawer_show_all":
            case "pref_drawer_show_saved":
            case "pref_drawer_show_history":
            case "pref_drawer_show_profile":
            case "pref_drawer_show_inbox":
            case "pref_drawer_show_drafts":
            case "pref_drawer_show_mod":
            case "pref_drawer_show_search_generic":
            case "pref_drawer_show_go_to":
            case "pref_drawer_show_go_to_subreddit":
            case "pref_drawer_show_go_to_user":
            case "pref_drawer_show_night_mode":
            case "pref_drawer_show_nsfw_switch":
            case "pref_drawer_show_blur_switch":
            case "pref_drawer_sticky_settings":
            case "pref_subscriptions_drawer":
            case "pref_subscriptions_drawer_show_icon":
            case "pref_subscriptions_only_casual":
            case "pref_accounts_show_avatar":
            case "pref_accounts_show_username":
            case "pref_drawer_end":
            case "pref_ask_exit":
            case "pref_double_exit":
                return true;
            default:
                return false;
        }
    }

    private static void addV4AppearanceSearchItems(
            List<SearchItem> result,
            Set<String> seen
    ) {
        String[][] items = new String[][]{
                {"Dynamic color", "Use the color palette selected by Android", "pref_dynamic_colors"},
                {"App icon", "Choose the icon shown by your launcher", "pref_app_icon"},
                {"Colored status bar", "Match the status bar to Boost's toolbar", "pref_colored_status_bar"},
                {"Colored navigation bar", "Match the navigation area to Boost's toolbar", "pref_colored_nav_bar"},
        };
        for (String[] item : items) {
            addSearchItem(
                    result,
                    seen,
                    new SearchItem(
                            item[0],
                            item[1],
                            "Appearance & layout · Appearance",
                            "ic_color_lens_24dp",
                            V4_APPEARANCE_FRAGMENT,
                            null,
                            item[2]
                    )
            );
        }
    }

    private static void addV4PostViewsSearchItems(
            List<SearchItem> result,
            Set<String> seen
    ) {
        String[][] items = new String[][]{
                {"Default view", "Choose cards, compact, columns, images, or swipe", "pref_view"},
                {"Remember per community", "Use the last selected view for each community", "pref_view_per_subscription"},
                {"Manage saved views", "Review community-specific views", "pref_view_per_sub"},
                {"Thumbnails on left", "Place post thumbnails on the left side", "pref_left_handed"},
                {"Communities start with r/", "Show Reddit's prefix before community names", "pref_show_subreddit_prefix"},
                {"Rounded corners", "Round card image corners", "pref_cards_rounded_corners"},
                {"Full height images", "Use full-height card images", "pref_cards_full_preview"},
                {"Show community icon", "Show community icons on cards", "pref_cards_subreddit_icon"},
                {"Carousel for multiple images", "Swipe through gallery images on cards", "pref_cards_gallery_carousel"},
                {"Show thumbnails for link posts", "Use thumbnails instead of large previews", "pref_cards_links_as_thumbnails"},
                {"Preview text from posts", "Show text previews on cards", "pref_cards_preview_self"},
                {"Lines to preview", "Set the number of post-text preview lines", "pref_cards_preview_self_lines"},
                {"Small-card rounded corners", "Round small-card image corners", "pref_mini_cards_rounded_corners"},
                {"Truncate small-card titles", "Limit small-card titles to two lines", "pref_mini_cards_truncate_title"},
                {"Small-card buttons", "Keep small-card buttons visible", "pref_mini_cards_buttons_visible"},
                {"Dense-view buttons", "Keep dense-view buttons visible", "pref_dense_buttons_visible"},
                {"Preview external links", "Load text previews for external links", "pref_load_readability"},
                {"Lock sidebar", "Disable opening the sidebar with a swipe", "pref_lock_sidebar"},
        };
        for (String[] item : items) {
            addSearchItem(
                    result,
                    seen,
                    new SearchItem(
                            item[0],
                            item[1],
                            "Appearance & layout · Post views",
                            "ic_view_carousel_24dp",
                            V4_POST_VIEWS_FRAGMENT,
                            null,
                            item[2]
                    )
            );
        }
    }

    private static void addV4FontsSearchItems(
            List<SearchItem> result,
            Set<String> seen
    ) {
        String[][] items = new String[][]{
                {"Title font", "Choose the font used for post titles", "pref_title_font"},
                {"Title text size", "Choose the size used for post titles", "pref_font_size_title"},
                {"Comments font", "Choose the font used for comments and messages", "pref_comments_font"},
                {"Comments text size", "Choose the size used for comments and messages", "pref_font_size"},
        };
        for (String[] item : items) {
            addSearchItem(
                    result,
                    seen,
                    new SearchItem(
                            item[0],
                            item[1],
                            "Appearance & layout · Fonts",
                            "ic_format_size_24dp",
                            V4_FONTS_FRAGMENT,
                            null,
                            item[2]
                    )
            );
        }
    }

    private static void addLeafAndXml(
            Context context,
            List<SearchItem> result,
            Set<String> seen,
            String category,
            Leaf leaf
    ) {
        addSearchItem(
                result,
                seen,
                new SearchItem(
                        leaf.title,
                        leaf.summary,
                        category,
                        leaf.iconName,
                        leaf.fragmentName,
                        leaf.activityName,
                        null
                )
        );

        if (leaf.resourceName == null || leaf.fragmentName == null) {
            return;
        }

        Resources resources = context.getResources();
        int resourceId = resourceId(context, "xml", leaf.resourceName);
        if (resourceId == 0) {
            return;
        }

        XmlResourceParser parser = null;
        try {
            parser = resources.getXml(resourceId);
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) {
                    continue;
                }

                String tag = parser.getName();
                if ("PreferenceScreen".equals(tag)
                        || "PreferenceCategory".equals(tag)) {
                    continue;
                }

                String title = attributeText(resources, parser, "title");
                if (TextUtils.isEmpty(title)) {
                    continue;
                }

                String summary = attributeText(resources, parser, "summary");
                String key = attributeText(resources, parser, "key");
                if ("pref_general_v2".equals(leaf.resourceName)
                        && isNavigationExposedKey(key)) {
                    continue;
                }
                String nestedFragment = parser.getAttributeValue(
                        ANDROID_NAMESPACE,
                        "fragment"
                );
                String destination = TextUtils.isEmpty(nestedFragment)
                        ? leaf.fragmentName
                        : MorpheSettingsV4NativePages.nativeDestination(
                                nestedFragment
                        );

                addSearchItem(
                        result,
                        seen,
                        new SearchItem(
                                title,
                                summary,
                                category + " · " + leaf.title,
                                leaf.iconName,
                                destination,
                                null,
                                key
                        )
                );
            }
        } catch (Exception ignored) {
            // Search indexing is best-effort; navigation remains available.
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    private static void addSearchItem(
            List<SearchItem> result,
            Set<String> seen,
            SearchItem item
    ) {
        String signature = normalize(item.title)
                + "|" + normalize(item.fragmentName)
                + "|" + normalize(item.activityName)
                + "|" + normalize(item.preferenceKey)
                + "|" + normalize(item.pageId);
        if (seen.add(signature)) {
            result.add(item);
        }
    }

    static int resourceId(Context context, String type, String name) {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(
                name,
                type,
                context.getPackageName()
        );
        if (resourceId == 0) {
            resourceId = resources.getIdentifier(name, type, BOOST_PACKAGE);
        }
        return resourceId;
    }

    private static String attributeText(
            Resources resources,
            XmlResourceParser parser,
            String name
    ) {
        int resourceId = parser.getAttributeResourceValue(
                ANDROID_NAMESPACE,
                name,
                0
        );
        if (resourceId != 0) {
            try {
                return resources.getText(resourceId).toString();
            } catch (Resources.NotFoundException ignored) {
                return "";
            }
        }

        String value = parser.getAttributeValue(ANDROID_NAMESPACE, name);
        return value == null ? "" : value;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static Leaf leaf(
            String title,
            String summary,
            String iconName,
            String fragmentClass,
            String resourceName
    ) {
        return Leaf.fragment(
                title,
                summary,
                iconName,
                FRAGMENT_PREFIX + fragmentClass,
                resourceName
        );
    }

    private static Leaf nativeLeaf(
            String title,
            String summary,
            String iconName,
            String pageClass,
            String resourceName
    ) {
        return Leaf.fragment(
                title,
                summary,
                iconName,
                V4_NATIVE_PAGES + pageClass,
                resourceName
        );
    }
}
