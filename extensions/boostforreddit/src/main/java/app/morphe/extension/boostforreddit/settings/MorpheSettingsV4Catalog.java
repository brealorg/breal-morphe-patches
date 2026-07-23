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

    static final class SearchItem {
        final String title;
        final String summary;
        final String category;
        final String iconName;
        final String fragmentName;
        final String activityName;
        final String preferenceKey;

        SearchItem(
                String title,
                String summary,
                String category,
                String iconName,
                String fragmentName,
                String activityName,
                String preferenceKey
        ) {
            this.title = title;
            this.summary = summary;
            this.category = category;
            this.iconName = iconName;
            this.fragmentName = fragmentName;
            this.activityName = activityName;
            this.preferenceKey = preferenceKey;
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
            "Morphe",
            "Features added by Morphe patches",
            "ic_puzzle_24dp",
            V4_NATIVE_PAGES + "Morphe",
            "morphe_boost_settings_skeleton"
    );

    private static final Category[] CATEGORIES = new Category[]{
            new Category(
                    "appearance_layout",
                    "Appearance & layout",
                    "Colors, post layout, and fonts",
                    "ic_color_lens_24dp",
                    Leaf.fragment("Appearance", "Dynamic color, app icon, and system bars", "ic_color_lens_24dp", V4_APPEARANCE_FRAGMENT, null),
                    Leaf.fragment("Post views", "Cards, lists, thumbnails, and density", "ic_view_carousel_24dp", V4_POST_VIEWS_FRAGMENT, null),
                    Leaf.fragment("Fonts", "Font family, size, and style", "ic_format_size_24dp", V4_FONTS_FRAGMENT, null)
            ),
            new Category(
                    "posts_comments",
                    "Posts & comments",
                    "Post and comment behavior",
                    "ic_post_24dp",
                    nativeLeaf("Posts", "Post display and interaction behavior", "ic_post_24dp", "Posts", "pref_posts_v2"),
                    nativeLeaf("Comments", "Comment display and interaction behavior", "ic_comment_outline_white_24dp", "Comments", "pref_comments_v2")
            ),
            new Category(
                    "navigation",
                    "Navigation",
                    "Toolbar, bottom navigation, and drawer",
                    "ic_toolbar_24dp",
                    Leaf.fragment("Toolbar", "Toolbar buttons and behavior", "ic_toolbar_24dp", V4_TOOLBAR_FRAGMENT, "pref_toolbar_v2"),
                    nativeLeaf("Bottom navigation", "Tabs and bottom navigation behavior", "ic_bottomnav_24dp", "BottomNavigation", "pref_bottom_navigation_v2"),
                    nativeLeaf("Navigation drawer", "Drawer items and ordering", "ic_dock_left_24dp", "Drawer", "pref_drawer_v2")
            ),
            new Category(
                    "media_links",
                    "Media & links",
                    "Viewers, players, and link handling",
                    "ic_photo_outline_24dp",
                    nativeLeaf("Media viewer", "Images, GIFs, video, and viewer behavior", "ic_photo_outline_24dp", "Media", "pref_media_v2"),
                    nativeLeaf("Link handling", "Browser and in-app link behavior", "ic_link_24dp", "Links", "pref_links_v2")
            ),
            new Category(
                    "search_filters",
                    "Search & filters",
                    "Search behavior and content filters",
                    "ic_search_color_24dp",
                    nativeLeaf("Search", "Search behavior and defaults", "ic_search_color_24dp", "Search", "pref_search_v2"),
                    nativeLeaf("Content filters", "Keywords, domains, and content rules", "ic_filter_list_24dp", "Filters", "pref_filters_v2")
            ),
            new Category(
                    "notifications",
                    "Notifications",
                    "Check interval, notification tone, and inbox",
                    "ic_notifications_black_24dp",
                    nativeLeaf("Notifications", "Messages and notification behavior", "ic_notifications_black_24dp", "Messages", "pref_messages_v2")
            ),
            new Category(
                    "data_storage",
                    "Backup",
                    "Data storage, export, and import",
                    "ic_save_24dp",
                    Leaf.fragment("Data storage", "Bandwidth, media quality, downloads, and cache", "outline_data_usage_24", V4_DATA_STORAGE_FRAGMENT, "pref_data_v2"),
                    Leaf.activity("Backup", "Export or import Boost settings", "ic_save_24dp", BACKUP_ACTIVITY)
            ),
            new Category(
                    "account_privacy",
                    "Account & privacy",
                    "Reddit preferences, history, and privacy",
                    "ic_person_24dp",
                    Leaf.fragment("Reddit preferences", "Website and account preferences", "ic_subreddit_24dp", FRAGMENT_PREFIX + "PreferenceFragmentAccountCompat", null),
                    nativeLeaf("History & privacy", "History, recent items, and privacy controls", "ic_restore_black_24dp", "Privacy", "pref_privacy_v2")
            ),
            new Category(
                    "app_legacy",
                    "App behavior",
                    "Composing, subscriptions, and exit behavior",
                    "ic_settings_24dp",
                    nativeLeaf("General", "Unique app behavior and composing options", "ic_settings_24dp", "General", "pref_general_v2")
            ),
            new Category(
                    "about",
                    "About",
                    "Support, privacy, licenses, and app information",
                    "ic_help_24dp",
                    nativeLeaf("About Boost", "Support, licenses, privacy, and version", "ic_help_24dp", "About", "pref_about_v2")
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

    static Category findCategory(String id) {
        for (Category category : CATEGORIES) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    static List<SearchItem> buildSearchIndex(Context context) {
        List<SearchItem> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        addLeafAndXml(context, result, seen, "Morphe", MORPHE);
        for (Category category : CATEGORIES) {
            for (Leaf leaf : category.leaves) {
                addLeafAndXml(context, result, seen, category.title, leaf);
            }
        }
        addV4AppearanceSearchItems(result, seen);
        addV4PostViewsSearchItems(result, seen);
        addV4FontsSearchItems(result, seen);
        return result;
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
                + "|" + normalize(item.preferenceKey);
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
