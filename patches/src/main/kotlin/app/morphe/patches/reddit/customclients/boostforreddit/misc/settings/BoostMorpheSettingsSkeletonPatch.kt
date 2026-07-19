package app.morphe.patches.reddit.customclients.boostforreddit.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.utf8Writer
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val boostMorpheSettingsSkeletonPatch = resourcePatch(
    name = "Boost Morphe settings",
    description = "Adds Boost Morphe settings for Search keyboard behavior, inline media previews, undelete toggles, adaptive refresh rate, source text visibility, and preview alignment.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        get("res/xml/morphe_boost_settings_skeleton.xml").utf8Writer().use { writer ->
            writer.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <PreferenceCategory android:title="Morphe">
                        <CheckBoxPreference
                            android:key="morphe_boost_prefer_high_refresh_rate"
                            android:title="Prefer high refresh rate"
                            android:summary="Ask Android to use a high refresh rate for Boost windows on adaptive-refresh displays."
                            android:defaultValue="true" />

                        <CheckBoxPreference
                            android:key="morphe_boost_search_open_keyboard_on_entry"
                            android:title="Open keyboard when entering Search"
                            android:summary="Focus the search field and show the keyboard immediately. When disabled, tap Search again to start typing."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_previews_enabled"
                            android:title="Enable inline media previews"
                            android:summary="Show supported media previews directly in comments."
                            android:defaultValue="true" />

                        <app.morphe.extension.boostforreddit.giphy.PreviewAlignmentPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_inline_media_preview_alignment"
                            android:title="Preview alignment"
                            android:summary="Center"
                            android:dialogTitle="Preview alignment"
                            android:defaultValue="center" />

                        <app.morphe.extension.boostforreddit.giphy.MediaTapActionPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_direct_reddit_gif_tap_action"
                            android:title="Direct Reddit GIF tap action"
                            android:summary="Image viewer"
                            android:dialogTitle="Direct Reddit GIF tap action"
                            android:defaultValue="image_viewer" />

                        <app.morphe.extension.boostforreddit.giphy.MediaTapActionPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_giphy_preview_tap_action"
                            android:title="Giphy preview tap action"
                            android:summary="Video viewer"
                            android:dialogTitle="Giphy preview tap action"
                            android:defaultValue="video_viewer" />

                        <app.morphe.extension.boostforreddit.giphy.MediaTapActionPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_static_preview_tap_action"
                            android:title="Static preview tap action"
                            android:summary="Image viewer"
                            android:dialogTitle="Static preview tap action"
                            android:defaultValue="image_viewer" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_preview_show_source_text"
                            android:title="Show source text with preview"
                            android:summary="Keep the original link text visible with the preview."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_reddit_undelete_enabled"
                            android:title="Automatically undelete Reddit content"
                            android:summary="Try to restore supported deleted Reddit posts/comments from archive sources. Disabled by default to keep normal browsing stable."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_imgur_undelete_enabled"
                            android:title="Automatically undelete Imgur images"
                            android:summary="Try to restore supported missing Imgur media from archive sources. Disabled by default."
                            android:defaultValue="false" />
                    </PreferenceCategory>
                </PreferenceScreen>
                """.trimIndent()
            )
        }

        get("res/xml/pref_advanced_v2.xml").utf8Writer().use { writer ->
            writer.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen
                  xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_photo_outline_24dp"
                        android:title="@string/pref_media_viewer_title"
                        android:summary="@string/pref_header_media_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentMediaCompat" />
                    <Preference
                        android:icon="@drawable/ic_link_24dp"
                        android:title="@string/pref_header_links"
                        android:summary="@string/pref_header_links_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentLinksCompat" />
                    <Preference
                        android:icon="@drawable/ic_subreddit_24dp"
                        android:title="@string/pref_header_account"
                        android:summary="@string/pref_header_account_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAccountCompat" />
                    <Preference
                        android:icon="@drawable/ic_search_color_24dp"
                        android:title="@string/search"
                        android:summary="@string/search_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentSearchCompat" />
                    <Preference
                        android:icon="@drawable/ic_save_24dp"
                        android:title="@string/pref_header_backup"
                        android:summary="@string/pref_header_backup_summary">
                        <intent
                            android:targetPackage="com.rubenmayayo.reddit"
                            android:targetClass="com.rubenmayayo.reddit.BackupActivity" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_restore_black_24dp"
                        android:title="@string/pref_header_legacy"
                        android:summary="@string/pref_header_legacy_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentMiscCompat" />

                    <PreferenceCategory android:title="Morphe">
                        <CheckBoxPreference
                            android:key="morphe_boost_prefer_high_refresh_rate"
                            android:title="Prefer high refresh rate"
                            android:summary="Ask Android to use a high refresh rate for Boost windows on adaptive-refresh displays."
                            android:defaultValue="true" />

                        <CheckBoxPreference
                            android:key="morphe_boost_search_open_keyboard_on_entry"
                            android:title="Open keyboard when entering Search"
                            android:summary="Focus the search field and show the keyboard immediately. When disabled, tap Search again to start typing."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_previews_enabled"
                            android:title="Enable inline media previews"
                            android:summary="Show supported media previews directly in comments."
                            android:defaultValue="true" />

                        <app.morphe.extension.boostforreddit.giphy.PreviewAlignmentPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_inline_media_preview_alignment"
                            android:title="Preview alignment"
                            android:summary="Center"
                            android:dialogTitle="Preview alignment"
                            android:defaultValue="center" />

                        <app.morphe.extension.boostforreddit.giphy.MediaTapActionPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_direct_reddit_gif_tap_action"
                            android:title="Direct Reddit GIF tap action"
                            android:summary="Image viewer"
                            android:dialogTitle="Direct Reddit GIF tap action"
                            android:defaultValue="image_viewer" />

                        <app.morphe.extension.boostforreddit.giphy.MediaTapActionPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_giphy_preview_tap_action"
                            android:title="Giphy preview tap action"
                            android:summary="Video viewer"
                            android:dialogTitle="Giphy preview tap action"
                            android:defaultValue="video_viewer" />

                        <app.morphe.extension.boostforreddit.giphy.MediaTapActionPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_static_preview_tap_action"
                            android:title="Static preview tap action"
                            android:summary="Image viewer"
                            android:dialogTitle="Static preview tap action"
                            android:defaultValue="image_viewer" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_preview_show_source_text"
                            android:title="Show source text with preview"
                            android:summary="Keep the original link text visible with the preview."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_reddit_undelete_enabled"
                            android:title="Automatically undelete Reddit content"
                            android:summary="Try to restore supported deleted Reddit posts/comments from archive sources. Disabled by default to keep normal browsing stable."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_imgur_undelete_enabled"
                            android:title="Automatically undelete Imgur images"
                            android:summary="Try to restore supported missing Imgur media from archive sources. Disabled by default."
                            android:defaultValue="false" />
                    </PreferenceCategory>
                </PreferenceScreen>
                """.trimIndent()
            )
        }
    }
}
