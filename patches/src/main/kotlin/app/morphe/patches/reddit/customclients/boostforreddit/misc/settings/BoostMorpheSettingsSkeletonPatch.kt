package app.morphe.patches.reddit.customclients.boostforreddit.misc.settings

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.utf8Writer
import app.morphe.patches.reddit.customclients.boostforreddit.BoostCompatible

@Suppress("unused")
val boostMorpheSettingsSkeletonPatch = resourcePatch(
    name = "Boost Morphe settings skeleton",
    description = "Dev-only Boost Morphe settings UI with inline preview controls and no duplicate source label.",
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
                            android:key="morphe_boost_inline_media_previews_enabled"
                            android:title="Enable inline media previews"
                            android:summary="Show supported media previews directly in comments."
                            android:defaultValue="true" />

                        <EditTextPreference
                            android:key="morphe_boost_inline_media_preview_alignment"
                            android:title="Preview alignment"
                            android:summary="Type left, center, or right. Default: center."
                            android:dialogTitle="Preview alignment"
                            android:dialogMessage="Use one of: left, center, right."
                            android:defaultValue="center" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_preview_show_source_text"
                            android:title="Show source text with preview"
                            android:summary="Keep the original link text visible with the preview."
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
                            android:key="morphe_boost_inline_media_previews_enabled"
                            android:title="Enable inline media previews"
                            android:summary="Show supported media previews directly in comments."
                            android:defaultValue="true" />

                        <EditTextPreference
                            android:key="morphe_boost_inline_media_preview_alignment"
                            android:icon="@drawable/ic_photo_outline_24dp"
                            android:title="Preview alignment"
                            android:summary="Type left, center, or right. Default: center."
                            android:dialogTitle="Preview alignment"
                            android:dialogMessage="Use one of: left, center, right."
                            android:defaultValue="center" />

                        <CheckBoxPreference
                            android:key="morphe_boost_inline_media_preview_show_source_text"
                            android:title="Show source text with preview"
                            android:summary="Keep the original link text visible with the preview."
                            android:defaultValue="false" />
                    </PreferenceCategory>
                </PreferenceScreen>
                """.trimIndent()
            )
        }
    }
}
