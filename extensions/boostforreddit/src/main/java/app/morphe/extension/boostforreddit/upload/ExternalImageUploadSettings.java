/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */
package app.morphe.extension.boostforreddit.upload;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;

import app.morphe.extension.shared.Utils;

/** Local settings for Boost editor image hosting. */
public final class ExternalImageUploadSettings {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_EXTERNAL_IMAGE_HOST_SETTINGS_ISSUE66_V1";

    public static final String PREF_EXTERNAL_IMAGE_HOST =
            "morphe_boost_external_image_host";
    public static final String PREF_IMGBB_API_KEY =
            "morphe_boost_imgbb_api_key";

    public static final String PROVIDER_REDDIT = "reddit";
    public static final String PROVIDER_IMGBB = "imgbb";
    public static final String PROVIDER_IMGUR = "imgur_free";

    private ExternalImageUploadSettings() {
    }

    public static String getEditorProvider() {
        SharedPreferences preferences = preferences();
        if (preferences == null) {
            return PROVIDER_IMGUR;
        }

        String stored = preferences.getString(
                PREF_EXTERNAL_IMAGE_HOST,
                PROVIDER_IMGUR
        );
        String normalized = normalizeProvider(stored);

        // Migrate the temporary Reddit-for-editor policy to final Imgur default.
        if (!TextUtils.equals(stored, normalized)) {
            preferences.edit()
                    .putString(PREF_EXTERNAL_IMAGE_HOST, normalized)
                    .apply();
        }

        return normalized;
    }

    public static String getImgBbApiKey() {
        SharedPreferences preferences = preferences();
        if (preferences == null) {
            return "";
        }
        String value = preferences.getString(PREF_IMGBB_API_KEY, "");
        return value == null ? "" : value.trim();
    }

    public static void save(String provider, String imgBbApiKey) {
        SharedPreferences preferences = preferences();
        if (preferences == null) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putString(
                        PREF_EXTERNAL_IMAGE_HOST,
                        normalizeProvider(provider)
                );

        if (imgBbApiKey != null) {
            editor.putString(PREF_IMGBB_API_KEY, imgBbApiKey.trim());
        }

        editor.apply();
    }

    public static String summary() {
        String provider = getEditorProvider();
        if (PROVIDER_IMGBB.equals(provider)) {
            return TextUtils.isEmpty(getImgBbApiKey())
                    ? "Comments and text posts: ImgBB — API key required"
                    : "Comments and text posts: ImgBB — configured";
        }
        return "Comments and text posts: Imgur — default";
    }

    public static String normalizeProvider(String value) {
        if (value == null) {
            return PROVIDER_IMGUR;
        }

        String normalized = value.trim().toLowerCase(Locale.US);
        if (PROVIDER_IMGBB.equals(normalized)
                || PROVIDER_IMGUR.equals(normalized)) {
            return normalized;
        }

        // Reddit is valid for image posts/galleries, not editor attachments.
        return PROVIDER_IMGUR;
    }

    private static SharedPreferences preferences() {
        try {
            Context context = Utils.getContext();
            if (context == null) {
                return null;
            }
            return context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );
        } catch (Throwable ignored) {
            return null;
        }
    }
}
