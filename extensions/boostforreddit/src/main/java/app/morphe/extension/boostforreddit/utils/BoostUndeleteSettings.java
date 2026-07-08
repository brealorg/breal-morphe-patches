package app.morphe.extension.boostforreddit.utils;

import android.content.Context;
import android.content.SharedPreferences;
import app.morphe.extension.shared.Utils;

/**
 * Runtime feature flags for Boost undelete behavior.
 *
 * Defaults must stay false: undelete is optional archive-recovery behavior and
 * must not affect normal feed/comment/media browsing unless the user opts in.
 */
public final class BoostUndeleteSettings {
    public static final String PREF_REDDIT_UNDELETE_ENABLED =
            "morphe_boost_reddit_undelete_enabled";
    public static final String PREF_IMGUR_UNDELETE_ENABLED =
            "morphe_boost_imgur_undelete_enabled";

    private static final boolean DEFAULT_REDDIT_UNDELETE_ENABLED = false;
    private static final boolean DEFAULT_IMGUR_UNDELETE_ENABLED = false;

    private BoostUndeleteSettings() {
    }

    public static boolean isRedditUndeleteEnabled() {
        return getBoolean(PREF_REDDIT_UNDELETE_ENABLED, DEFAULT_REDDIT_UNDELETE_ENABLED);
    }

    public static boolean isImgurUndeleteEnabled() {
        return getBoolean(PREF_IMGUR_UNDELETE_ENABLED, DEFAULT_IMGUR_UNDELETE_ENABLED);
    }

    private static boolean getBoolean(String key, boolean fallback) {
        try {
            Context context = Utils.getContext();
            if (context == null) {
                return fallback;
            }

            SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            return preferences.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
