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

    /**
     * Whether the patch behind a toggle was applied. Each undelete patch adds a string resource;
     * the settings skeleton lists both toggles regardless. Unknown keys count as applied.
     */
    public static boolean isTogglePatchApplied(Context context, String key) {
        String marker;
        if (PREF_REDDIT_UNDELETE_ENABLED.equals(key)) {
            marker = "morphe_boost_undelete_reddit_applied";
        } else if (PREF_IMGUR_UNDELETE_ENABLED.equals(key)) {
            marker = "morphe_boost_undelete_imgur_applied";
        } else {
            return true;
        }
        try {
            return context.getResources().getIdentifier(marker, "string", context.getPackageName()) != 0;
        } catch (Throwable ignored) {
            return true;
        }
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
