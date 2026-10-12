/*
 * Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.net.Uri;
import android.util.Log;

import java.util.Locale;

/**
 * @noinspection unused
 */
public final class RedditMediaLinks {
    private static final String TAG = "MorpheMediaLinks";
    private static final String MARKER = "morphe_boost_open_reddit_media_links";

    private RedditMediaLinks() {
    }

    /**
     * Called by DeepLinkingActivity before Boost's Reddit link router, which only knows threads,
     * subreddits and users and sends i.redd.it images to a web view. True means the link should
     * go through Boost's in-app link opener, the one used for links in comments.
     */
    public static boolean isRedditMediaLink(String url) {
        try {
            if (url == null) return false;
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            if (scheme == null || host == null || path == null || path.length() < 2) return false;
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            boolean media = ("https".equals(scheme) || "http".equals(scheme))
                    && "i.redd.it".equals(host);
            if (media) {
                Log.i(TAG, MARKER + " host=" + host);
            }
            return media;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
