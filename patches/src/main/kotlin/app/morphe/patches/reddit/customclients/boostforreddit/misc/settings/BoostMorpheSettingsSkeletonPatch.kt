package app.morphe.patches.reddit.customclients.boostforreddit.misc.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.utf8Writer
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible
import app.morphe.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SETTINGS_LAYOUT_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/settings/MorpheSettingsLayout;"

private const val SETTINGS_V4_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/settings/MorpheSettingsV4;"

private const val SET_PREFERENCES_FROM_RESOURCE_REFERENCE =
    "Landroidx/preference/PreferenceFragmentCompat;->setPreferencesFromResource(ILjava/lang/String;)V"

private const val GET_INTENT_REFERENCE =
    "Landroid/app/Activity;->getIntent()Landroid/content/Intent;"

private val settingsHeaderOnCreatePreferencesFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;", "Ljava/lang/String;"),
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/rubenmayayo/reddit/ui/preferences/v2/SettingsActivityCompat\$HeaderFragment;" &&
            method.name == "onCreatePreferences"
    },
)

private val settingsActivityOnCreateFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    custom = { method, classDef ->
        classDef.type ==
            "Lcom/rubenmayayo/reddit/ui/preferences/v2/SettingsActivityCompat;" &&
            method.name == "onCreate"
    },
)

private val boostMorpheSettingsResourcesPatch = resourcePatch(
    name = "Boost Morphe settings resources",
    description = "Adds resources for Morphe settings, Settings v4, and its compatibility fallback.",
    default = false
) {
    compatibleWith(*BoostCompatible)

    execute {
        get("res/xml/morphe_boost_settings_skeleton.xml").utf8Writer().use { writer ->
            writer.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <PreferenceCategory android:title="Settings">
                        <CheckBoxPreference
                            android:key="morphe_boost_settings_v4_enabled"
                            android:title="Settings v4 (preview)"
                            android:summary="Use Morphe's modern task-based settings. Close and reopen Settings to apply."
                            android:defaultValue="false" />
                    </PreferenceCategory>

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

        mapOf(
            "morphe_boost_settings_layout_v2" to
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
                        android:icon="@drawable/ic_color_lens_24dp"
                        android:key="morphe_boost_settings_v2_appearance_layout"
                        android:title="Appearance &amp; layout"
                        android:summary="Theme, post layout, and fonts"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment"
                        app:allowDividerAbove="true">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_appearance_layout" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_post_24dp"
                        android:key="morphe_boost_settings_v2_posts_comments"
                        android:title="Posts &amp; comments"
                        android:summary="Post and comment behavior"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_posts_comments" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_menu_24dp"
                        android:key="morphe_boost_settings_v2_navigation"
                        android:title="Navigation"
                        android:summary="Toolbar, bottom navigation, and drawer"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_navigation" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_photo_outline_24dp"
                        android:key="morphe_boost_settings_v2_media_links"
                        android:title="Media &amp; links"
                        android:summary="Viewers, players, and link handling"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_media_links" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_search_color_24dp"
                        android:key="morphe_boost_settings_v2_search_filters"
                        android:title="Search &amp; filters"
                        android:summary="Search behavior and content filters"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_search_filters" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_notifications_black_24dp"
                        android:key="morphe_boost_settings_v2_notifications"
                        android:title="@string/pref_header_messages"
                        android:summary="@string/pref_header_messages_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentMessagesCompat" />
                    <Preference
                        android:icon="@drawable/outline_data_usage_24"
                        android:key="morphe_boost_settings_v2_data_storage"
                        android:title="Data &amp; storage"
                        android:summary="Bandwidth, cache, downloads, and backup"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_data_storage" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_person_24dp"
                        android:key="morphe_boost_settings_v2_account_privacy"
                        android:title="Account &amp; privacy"
                        android:summary="Reddit preferences, history, and privacy"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_account_privacy" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_settings_24dp"
                        android:key="morphe_boost_settings_v2_app_legacy"
                        android:title="App behavior &amp; legacy"
                        android:summary="General and old features"
                        android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsHubFragment">
                        <extra
                            android:name="morphe_boost_settings_hub_resource"
                            android:value="morphe_boost_settings_hub_app_legacy" />
                    </Preference>
                    <Preference
                        android:icon="@drawable/ic_help_24dp"
                        android:key="morphe_boost_settings_v2_about"
                        android:title="@string/pref_header_about"
                        android:summary="@string/pref_header_about_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAboutCompat" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_appearance_layout" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_color_lens_24dp"
                        android:title="@string/theme"
                        android:summary="Theme, dark mode, and app icon"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAppearanceCompat" />
                    <Preference
                        android:icon="@drawable/ic_view_carousel_24dp"
                        android:title="@string/view_settings"
                        android:summary="@string/view_settings_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentViewsCompat" />
                    <Preference
                        android:icon="@drawable/ic_format_size_24dp"
                        android:title="@string/pref_appearance_category_fonts"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentFontsCompat" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_posts_comments" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_post_24dp"
                        android:title="@string/pref_header_posts"
                        android:summary="@string/pref_header_posts_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentPostsCompat" />
                    <Preference
                        android:icon="@drawable/ic_comment_outline_white_24dp"
                        android:title="@string/pref_header_comments"
                        android:summary="@string/pref_header_comments_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentCommentsCompat" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_navigation" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_toolbar_24dp"
                        android:title="@string/toolbar"
                        android:summary="@string/pref_toolbar_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentToolbarCompat" />
                    <Preference
                        android:icon="@drawable/ic_bottomnav_24dp"
                        android:title="@string/pref_bottom_navigation_title"
                        android:summary="@string/pref_bottom_navigation_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentBottomNavigationCompat" />
                    <Preference
                        android:icon="@drawable/ic_dock_left_24dp"
                        android:title="@string/pref_drawer_items"
                        android:summary="@string/pref_header_drawer_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentDrawerCompat" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_media_links" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
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
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_search_filters" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_search_color_24dp"
                        android:title="@string/search"
                        android:summary="@string/search_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentSearchCompat" />
                    <Preference
                        android:icon="@drawable/ic_filter_list_24dp"
                        android:title="@string/content_filters"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentFiltersCompat" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_data_storage" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/outline_data_usage_24"
                        android:title="@string/pref_category_data_usage"
                        android:summary="@string/pref_header_bandwidth_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentDataSavingCompat" />
                    <Preference
                        android:icon="@drawable/ic_save_24dp"
                        android:key="morphe_boost_settings_backup"
                        android:title="@string/pref_header_backup"
                        android:summary="@string/pref_header_backup_summary" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_account_privacy" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_subreddit_24dp"
                        android:title="@string/pref_header_account"
                        android:summary="@string/pref_header_account_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentAccountCompat" />
                    <Preference
                        android:icon="@drawable/ic_restore_black_24dp"
                        android:title="@string/history"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentPrivacyCompat" />
                </PreferenceScreen>
                """,
            "morphe_boost_settings_hub_app_legacy" to
                """
                <?xml version="1.0" encoding="utf-8"?>
                <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
                    <Preference
                        android:icon="@drawable/ic_settings_24dp"
                        android:title="@string/pref_header_general"
                        android:summary="@string/pref_header_general_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentGeneralCompat" />
                    <Preference
                        android:icon="@drawable/ic_restore_black_24dp"
                        android:title="@string/pref_header_legacy"
                        android:summary="@string/pref_header_legacy_summary"
                        android:fragment="com.rubenmayayo.reddit.ui.preferences.v2.PreferenceFragmentMiscCompat" />
                </PreferenceScreen>
                """,
        ).forEach { (resourceName, xml) ->
            get("res/xml/$resourceName.xml").utf8Writer().use { writer ->
                writer.write(xml.trimIndent())
            }
        }
    }
}

@Suppress("unused")
val boostMorpheSettingsSkeletonPatch = bytecodePatch(
    name = "Boost Morphe settings",
    description = "Adds dedicated Morphe settings and an optional Morphe-owned Settings v4 interface.",
    default = false,
) {
    dependsOn(sharedExtensionPatch, boostMorpheSettingsResourcesPatch)
    compatibleWith(*BoostCompatible)

    execute {
        settingsHeaderOnCreatePreferencesFingerprint.method.apply {
            val loadPreferencesIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.toString() ==
                    SET_PREFERENCES_FROM_RESOURCE_REFERENCE
            }

            addInstructions(
                loadPreferencesIndex,
                """
                    invoke-static {p0, p1}, $SETTINGS_LAYOUT_EXTENSION_DESCRIPTOR->resolveRootResource(Landroidx/preference/PreferenceFragmentCompat;I)I

                    move-result p1
                """.trimIndent(),
            )
        }

        settingsActivityOnCreateFingerprint.method.apply {
            val getIntentIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_VIRTUAL &&
                    getReference<MethodReference>()?.toString() ==
                    GET_INTENT_REFERENCE
            }

            addInstructions(
                getIntentIndex,
                """
                    invoke-static {p0}, $SETTINGS_V4_EXTENSION_DESCRIPTOR->prepareIntent(Landroid/app/Activity;)V
                """.trimIndent(),
            )
        }
    }
}
