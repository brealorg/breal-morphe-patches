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
import app.morphe.patches.all.misc.PatchCategory

private const val SETTINGS_V4_EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/boostforreddit/settings/MorpheSettingsV4;"

private const val GET_INTENT_REFERENCE =
    "Landroid/app/Activity;->getIntent()Landroid/content/Intent;"

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
                <PreferenceScreen
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto">
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

                    <PreferenceCategory android:title="Image viewer">
                        <androidx.preference.SeekBarPreference
                            android:key="morphe_boost_image_dismiss_sensitivity"
                            android:title="Swipe image away faster"
                            android:summary="Default 100 enables quick flicks and a moderate 10% drag threshold. 50 keeps Boost&#39;s 15% threshold with quick flicks; 12 restores original Boost behavior."
                            android:defaultValue="100"
                            android:min="12"
                            android:max="100"
                            app:min="12"
                            app:showSeekBarValue="true" />
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

                    <PreferenceCategory android:title="Media uploads">
                        <Preference
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:key="morphe_boost_image_hosting_settings"
                            android:title="Image hosting"
                            android:summary="Images added to text: Imgur — default"
                            android:fragment="app.morphe.extension.boostforreddit.settings.MorpheSettingsV5ImageHostingFragment" />
                    </PreferenceCategory>

                    <PreferenceCategory android:title="Search">
                        <CheckBoxPreference
                            android:key="morphe_boost_search_open_keyboard_on_entry"
                            android:title="Open keyboard when entering Search"
                            android:summary="Focus the search field and show the keyboard immediately. When disabled, tap Search again to start typing."
                            android:defaultValue="false" />
                        <CheckBoxPreference
                            android:key="morphe_boost_search_show_active_subreddits"
                            android:title="Show active subreddits"
                            android:summary="List currently active subreddits when Search opens."
                            android:defaultValue="true" />
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
                            android:summary="Restore removed Reddit posts and comments from Arctic Shift and the Wayback Machine. When a thread has removed content, its post and comment IDs are sent to these third-party archives. Off by default."
                            android:defaultValue="false" />

                        <CheckBoxPreference
                            android:key="morphe_boost_imgur_undelete_enabled"
                            android:title="Automatically undelete Imgur images"
                            android:summary="Restore missing Imgur images and albums from the Wayback Machine. Links to missing Imgur media you open are sent to this third-party archive. Off by default."
                            android:defaultValue="false" />
                    </PreferenceCategory>
                    <PreferenceCategory android:title="Settings">
                        <CheckBoxPreference
                            android:key="morphe_boost_settings_v4_enabled"
                            android:title="Material settings"
                            android:summary="Use Morphe's modern task-based settings. Reopen Settings to apply."
                            android:defaultValue="true" />
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

@Suppress("unused")
val boostMorpheSettingsSkeletonPatch = bytecodePatch(
    name = "Boost Morphe settings",
    description = "Adds a Morphe section and a reorganized settings screen with search. The classic settings stay available.",
    default = false,
) {
    category(PatchCategory.INTERFACE)
    dependsOn(sharedExtensionPatch, boostMorpheSettingsResourcesPatch)
    compatibleWith(*BoostCompatible)

    execute {
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
