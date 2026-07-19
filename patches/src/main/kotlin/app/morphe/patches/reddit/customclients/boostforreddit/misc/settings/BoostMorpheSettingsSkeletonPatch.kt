package app.morphe.patches.reddit.customclients.boostforreddit.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.utf8Writer
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val boostMorpheSettingsSkeletonPatch = resourcePatch(
    name = "Boost Morphe settings",
    description = "Adds a dedicated top-level Morphe settings screen for Search behavior, inline media previews, undelete toggles, and adaptive refresh rate.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        get("res/xml/morphe_boost_settings_skeleton.xml").utf8Writer().use { writer ->
            writer.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <PreferenceCategory android:title="Media previews">
                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_previews_enabled"
                            android:title="Enable inline media previews"
                            android:summary="Show supported media previews directly in comments."
                            android:defaultValue="true" />

                        <app.morphe.extension.boostforreddit.giphy.PreviewSizePreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_inline_media_preview_size"
                            android:title="Preview size"
                            android:summary="Balanced"
                            android:dialogTitle="Preview size"
                            android:defaultValue="balanced" />

                        <app.morphe.extension.boostforreddit.giphy.PreviewAlignmentPreference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_inline_media_preview_alignment"
                            android:title="Preview alignment"
                            android:summary="Center"
                            android:dialogTitle="Preview alignment"
                            android:defaultValue="center" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_preview_show_source_text"
                            android:title="Show source text with preview"
                            android:summary="Keep the original link text visible with the preview."
                            android:defaultValue="false" />
                    </PreferenceCategory>

                    <PreferenceCategory android:title="Open behavior">
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
                    </PreferenceCategory>

                    <PreferenceCategory android:title="Search">
                        <CheckBoxPreference
                            android:key="morphe_boost_search_open_keyboard_on_entry"
                            android:title="Open keyboard when entering Search"
                            android:summary="Focus the search field and show the keyboard immediately. When disabled, tap Search again to start typing."
                            android:defaultValue="false" />
                    </PreferenceCategory>

                    <PreferenceCategory android:title="Display &amp; performance">
                        <CheckBoxPreference
                            android:key="morphe_boost_prefer_high_refresh_rate"
                            android:title="Prefer high refresh rate"
                            android:summary="Ask Android to use a high refresh rate for Boost windows on adaptive-refresh displays."
                            android:defaultValue="true" />
                    </PreferenceCategory>

                    <PreferenceCategory android:title="Recovery &amp; archives">
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

        get("res/xml/pref_headers_v2.xml").utf8Writer().use { writer ->
            writer.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen
                  xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto">
                    <Preference
                        android:icon="@drawable/ic_puzzle_24dp"
                        android:key="morphe_boost_settings_entry"
                        android:title="Morphe"
                        android:summary="Features added by Morphe patches"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsFragment"
                        app:allowDividerBelow="true" />
                    <Preference
                        android:icon="@drawable/ic_settings_24dp"
                        android:title="@string/pref_header_general"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentGeneralCompat"
                        app:allowDividerAbove="true" />
                    <Preference
                        android:icon="@drawable/ic_color_lens_24dp"
                        android:title="@string/theme"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAppearanceCompat" />
                    <Preference
                        android:icon="@drawable/ic_notifications_black_24dp"
                        android:title="@string/pref_header_messages"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentMessagesCompat" />
                    <Preference
                        android:icon="@drawable/ic_filter_list_24dp"
                        android:title="@string/content_filters"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentFiltersCompat" />
                    <Preference
                        android:icon="@drawable/outline_data_usage_24"
                        android:title="@string/pref_category_data_usage"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentDataSavingCompat" />
                    <Preference
                        android:icon="@drawable/ic_restore_black_24dp"
                        android:title="@string/history"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentPrivacyCompat" />
                    <Preference
                        android:icon="@drawable/ic_settings_24dp"
                        android:title="@string/pref_category_advanced"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAdvancedCompat" />
                    <Preference
                        android:icon="@drawable/ic_help_24dp"
                        android:title="@string/pref_header_about"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAboutCompat" />
                    <Preference
                        android:icon="@drawable/ic_baseline_favorite_border_24"
                        android:title="@string/action_remove_ads"
                        android:key="remove_ads"
                        android:summary="@string/support_boost"
                        app:allowDividerAbove="true"
                        app:allowDividerBelow="true" />
                    <Preference
                        android:icon="@drawable/ic_baseline_favorite_border_24"
                        android:title="@string/action_buy_pro"
                        android:key="buy_pro"
                        app:allowDividerAbove="true"
                        app:allowDividerBelow="true" />
                    <Preference
                        android:icon="@drawable/ic_gas_station"
                        android:visible="false"
                        android:title="@string/support_pref"
                        android:key="support_launch"
                        android:summary="@string/support_explanation"
                        app:allowDividerAbove="true"
                        app:allowDividerBelow="true" />
                    <Preference
                        android:icon="@drawable/ic_info_outline_24dp"
                        android:title="@string/about_privacy_policy"
                        android:key="privacy_policy"
                        app:allowDividerAbove="true" />
                </PreferenceScreen>
                """.trimIndent()
            )
        }
    }
}
