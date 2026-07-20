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
    static final String MORPHE_FRAGMENT =
            "app.morphe.extension.boostforreddit.settings.MorpheSettingsFragment";
    static final String BACKUP_ACTIVITY =
            "com.rubenmayayo.reddit.BackupActivity";

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
            MORPHE_FRAGMENT,
            "morphe_boost_settings_skeleton"
    );

    private static final Category[] CATEGORIES = new Category[]{
            new Category(
                    "appearance_layout",
                    "Appearance & layout",
                    "Theme, post layout, and fonts",
                    "ic_color_lens_24dp",
                    leaf("Theme & appearance", "Theme, dark mode, and app icon", "ic_color_lens_24dp", "PreferenceFragmentAppearanceCompat", "pref_appearance_v2"),
                    leaf("Post views", "Cards, lists, thumbnails, and density", "ic_view_carousel_24dp", "PreferenceFragmentViewsCompat", "pref_views_v2"),
                    leaf("Fonts", "Font family, size, and style", "ic_format_size_24dp", "PreferenceFragmentFontsCompat", "pref_fonts_v2")
            ),
            new Category(
                    "posts_comments",
                    "Posts & comments",
                    "Post and comment behavior",
                    "ic_post_24dp",
                    leaf("Posts", "Post display and interaction behavior", "ic_post_24dp", "PreferenceFragmentPostsCompat", "pref_posts_v2"),
                    leaf("Comments", "Comment display and interaction behavior", "ic_comment_outline_white_24dp", "PreferenceFragmentCommentsCompat", "pref_comments_v2")
            ),
            new Category(
                    "navigation",
                    "Navigation",
                    "Toolbar, bottom navigation, and drawer",
                    "ic_toolbar_24dp",
                    leaf("Toolbar", "Toolbar buttons and behavior", "ic_toolbar_24dp", "PreferenceFragmentToolbarCompat", "pref_toolbar_v2"),
                    leaf("Bottom navigation", "Tabs and bottom navigation behavior", "ic_bottomnav_24dp", "PreferenceFragmentBottomNavigationCompat", "pref_bottom_navigation_v2"),
                    leaf("Navigation drawer", "Drawer items and ordering", "ic_dock_left_24dp", "PreferenceFragmentDrawerCompat", "pref_drawer_v2")
            ),
            new Category(
                    "media_links",
                    "Media & links",
                    "Viewers, players, and link handling",
                    "ic_photo_outline_24dp",
                    leaf("Media viewer", "Images, GIFs, video, and viewer behavior", "ic_photo_outline_24dp", "PreferenceFragmentMediaCompat", "pref_media_v2"),
                    leaf("Link handling", "Browser and in-app link behavior", "ic_link_24dp", "PreferenceFragmentLinksCompat", "pref_links_v2")
            ),
            new Category(
                    "search_filters",
                    "Search & filters",
                    "Search behavior and content filters",
                    "ic_search_color_24dp",
                    leaf("Search", "Search behavior and defaults", "ic_search_color_24dp", "PreferenceFragmentSearchCompat", "pref_search_v2"),
                    leaf("Content filters", "Keywords, domains, and content rules", "ic_filter_list_24dp", "PreferenceFragmentFiltersCompat", "pref_filters_v2")
            ),
            new Category(
                    "notifications",
                    "Notifications",
                    "Check interval, notification tone, and inbox",
                    "ic_notifications_black_24dp",
                    leaf("Notifications", "Messages and notification behavior", "ic_notifications_black_24dp", "PreferenceFragmentMessagesCompat", "pref_messages_v2")
            ),
            new Category(
                    "data_storage",
                    "Data & storage",
                    "Bandwidth, cache, downloads, and backup",
                    "outline_data_usage_24",
                    leaf("Data usage", "Bandwidth, cache, and data-saving behavior", "outline_data_usage_24", "PreferenceFragmentDataSavingCompat", "pref_data_v2"),
                    leaf("Downloads", "Download folder and file behavior", "ic_file_download_24dp", "PreferenceFragmentDownloadsCompat", "pref_downloads_v2"),
                    Leaf.activity("Backup", "Export or import Boost settings", "ic_save_24dp", BACKUP_ACTIVITY)
            ),
            new Category(
                    "account_privacy",
                    "Account & privacy",
                    "Reddit preferences, history, and privacy",
                    "ic_person_24dp",
                    Leaf.fragment("Reddit preferences", "Website and account preferences", "ic_subreddit_24dp", FRAGMENT_PREFIX + "PreferenceFragmentAccountCompat", null),
                    leaf("History & privacy", "History, recent items, and privacy controls", "ic_restore_black_24dp", "PreferenceFragmentPrivacyCompat", "pref_privacy_v2")
            ),
            new Category(
                    "app_legacy",
                    "App behavior & legacy",
                    "General behavior and older features",
                    "ic_settings_24dp",
                    leaf("General", "General app behavior", "ic_settings_24dp", "PreferenceFragmentGeneralCompat", "pref_general_v2"),
                    leaf("Legacy features", "Older Boost features and compatibility", "ic_restore_black_24dp", "PreferenceFragmentMiscCompat", "pref_misc_v2")
            ),
            new Category(
                    "about",
                    "About",
                    "Support, privacy, licenses, and app information",
                    "ic_help_24dp",
                    leaf("About Boost", "Support, licenses, privacy, and version", "ic_help_24dp", "PreferenceFragmentAboutCompat", "pref_about_v2")
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
        return result;
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
                        : nestedFragment;

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
}
