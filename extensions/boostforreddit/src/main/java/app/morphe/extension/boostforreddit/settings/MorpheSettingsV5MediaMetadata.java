package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.text.TextUtils;

/** Titles and supporting text for the hidden Settings V5 Media wave. */
final class MorpheSettingsV5MediaMetadata {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_MEDIA_METADATA_ISSUE121_V1";

    private MorpheSettingsV5MediaMetadata() {
    }

    static String titleFor(Context context, String key) {
        switch (key) {
            case "pref_load_readability":
                return "Preview external links";
            case "pref_download_folder_per_subreddit":
                return "Community subfolders";
            case "pref_download_folder_default":
                return "Default folder";
            case "pref_download_folder_gif":
                return "GIFs";
            case "pref_download_folder_img":
                return "Images";
            case "pref_download_folder_mp4":
                return "Videos";
            case "pref_image_loader":
                return "Image loader";
            case "pref_images_deepzoom":
                return "Load HQ images";
            case "pref_images_fit":
                return "Load zoomed in";
            case "pref_swipe_up_to_dismiss_image":
                return "Swipe to dismiss";
            case "pref_swipe_dismiss_direction_mode_image":
                return "Swipe to dismiss direction";
            case "pref_tap_to_dismiss_image":
                return "Tap to dismiss";
            case "morphe_boost_direct_reddit_gif_tap_action":
                return "Direct Reddit GIF tap action";
            case "morphe_boost_giphy_preview_tap_action":
                return "Giphy preview tap action";
            case "morphe_boost_static_preview_tap_action":
                return "Static preview tap action";
            case "morphe_boost_inline_media_previews_enabled":
                return "Enable inline media previews";
            case "morphe_boost_inline_media_preview_show_source_text":
                return "Show source text with preview";
            case "morphe_boost_inline_media_preview_alignment":
                return "Preview alignment";
            case "morphe_boost_inline_media_preview_size":
                return "Preview size";
            case "pref_browser":
                return "Default browser";
            case "pref_domain_exceptions":
                return "Domain exceptions";
            case "pref_link_album":
                return "Albums";
            case "pref_link_deviant":
                return "Deviant Art";
            case "pref_link_gif":
                return "Gifs";
            case "pref_link_image":
                return "Images";
            case "pref_link_neatclip":
                return "NeatClip";
            case "pref_link_vreddit":
                return "Reddit videos";
            case "pref_link_streamable":
                return "Streamable";
            case "pref_link_xkcd":
                return "XKCD";
            case "pref_link_video":
                return "Internal Youtube player";
            case "pref_video_audio_start_muted":
                return "Mute audio";
            case "pref_autoplay_cards":
                return "Autoplay videos";
            case "pref_media_viewer":
                return "Media viewer";
            case "pref_media_overlay_visibility":
                return "Overlay elements";
            case "pref_tap_to_dismiss_youtube_videos":
                return "Tap to dismiss Youtube";
            case "pref_video_player":
                return "Video player";
            default:
                return key == null ? "" : key;
        }
    }

    static String toggleSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_load_readability":
                return "Show a readable preview for supported external links.";
            case "pref_download_folder_per_subreddit":
                return "Organize downloaded media into community folders.";
            case "pref_images_deepzoom":
                return "Load high-resolution images in Boost\u2019s native viewer.";
            case "pref_images_fit":
                return "Open native images zoomed to fill the available space.";
            case "pref_swipe_up_to_dismiss_image":
                return "Swipe the native image viewer to close it.";
            case "pref_tap_to_dismiss_image":
                return "Tap the image to close the native viewer.";
            case "morphe_boost_inline_media_previews_enabled":
                return "Show supported media directly inside comments.";
            case "morphe_boost_inline_media_preview_show_source_text":
                return "Keep the original source text visible with the preview.";
            case "pref_link_album":
                return "Open supported album links inside Boost.";
            case "pref_link_deviant":
                return "Open supported Deviant Art links inside Boost.";
            case "pref_link_gif":
                return "Open supported GIF links inside Boost.";
            case "pref_link_image":
                return "Open supported image links inside Boost.";
            case "pref_link_neatclip":
                return "Open supported NeatClip links inside Boost.";
            case "pref_link_vreddit":
                return "Open Reddit-hosted videos inside Boost.";
            case "pref_link_streamable":
                return "Open supported Streamable links inside Boost.";
            case "pref_link_xkcd":
                return "Open XKCD links inside Boost.";
            case "pref_link_video":
                return "Open supported YouTube links with Boost\u2019s internal player.";
            case "pref_video_audio_start_muted":
                return "Start video playback with audio muted.";
            case "pref_tap_to_dismiss_youtube_videos":
                return "Tap an internal YouTube video to close the player.";
            default:
                return TextUtils.isEmpty(currentSummary)
                        ? ""
                        : currentSummary;
        }
    }

    static String actionSummaryFor(String key, String currentSummary) {
        switch (key) {
            case "pref_download_folder_default":
                return "Choose the fallback destination for downloaded media.";
            case "pref_download_folder_img":
                return "Choose the destination used for downloaded images.";
            case "pref_download_folder_mp4":
                return "Choose the destination used for downloaded videos.";
            case "pref_download_folder_gif":
                return "Choose the destination used for downloaded GIFs.";
            case "pref_domain_exceptions":
                return "Choose domains that bypass Boost\u2019s in-app link handling.";
            default:
                return TextUtils.isEmpty(currentSummary)
                        ? ""
                        : currentSummary;
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
        switch (key) {
            case "pref_image_loader":
                return "Choose the image-loading implementation used by Boost.";
            case "pref_swipe_dismiss_direction_mode_image":
                return "Choose which swipe direction closes the native image viewer.";
            case "morphe_boost_direct_reddit_gif_tap_action":
                return "Choose what opens when a direct Reddit GIF is tapped.";
            case "morphe_boost_giphy_preview_tap_action":
                return "Choose what opens when an inline Giphy preview is tapped.";
            case "morphe_boost_static_preview_tap_action":
                return "Choose what opens when a static inline preview is tapped.";
            case "morphe_boost_inline_media_preview_alignment":
                return "Choose the horizontal alignment of inline previews.";
            case "morphe_boost_inline_media_preview_size":
                return "Choose the size of inline media previews.";
            case "pref_browser":
                return "Choose the browser used for links opened outside Boost.";
            case "pref_autoplay_cards":
                return "Choose when videos autoplay while browsing feeds.";
            case "pref_media_viewer":
                return "Choose the viewer used for supported media.";
            case "pref_media_overlay_visibility":
                return "Choose when controls and metadata are shown over media.";
            case "pref_video_player":
                return "Choose the player used for video playback.";
            default:
                String pageId = MorpheSettingsV5Registry.pageIdForKey(key);
                return TextUtils.isEmpty(pageId)
                        ? ""
                        : MorpheSettingsV5Registry.titleFor(pageId);
        }
    }
}
