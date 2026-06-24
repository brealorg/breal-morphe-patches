package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class BoostMediaPreferences {
    public static final String KEY_INLINE_PREVIEWS =
            "morphe_boost_inline_previews";
    public static final String KEY_LEFT_ALIGN_PREVIEWS =
            "morphe_boost_left_align_previews";
    public static final String KEY_HIDE_SOURCE_AFTER_PREVIEW =
            "morphe_boost_hide_source_after_preview";

    private BoostMediaPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static boolean inlinePreviewsEnabled(Context context) {
        return context == null || prefs(context).getBoolean(KEY_INLINE_PREVIEWS, true);
    }

    public static boolean leftAlignPreviews(Context context) {
        return context != null && prefs(context).getBoolean(KEY_LEFT_ALIGN_PREVIEWS, false);
    }

    public static boolean hideSourceAfterPreview(Context context) {
        return context != null && prefs(context).getBoolean(KEY_HIDE_SOURCE_AFTER_PREVIEW, false);
    }
}
