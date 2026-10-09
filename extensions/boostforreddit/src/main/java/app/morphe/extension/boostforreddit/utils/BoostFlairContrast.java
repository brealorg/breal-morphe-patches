/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import android.graphics.Color;

/**
 * Keeps flair labels readable.
 *
 * Boost uses Reddit's flair text_color ("light"/"dark") as is, so a "light"
 * flair on a pale background renders white on near-white (issue #197). When
 * the requested text color has less than 3:1 contrast against an opaque
 * background, use black or white, whichever is more readable. Readable
 * subreddit colors are left unchanged.
 */
@SuppressWarnings("unused")
public final class BoostFlairContrast {
    static final double MIN_CONTRAST = 3.0;

    private BoostFlairContrast() {
    }

    /** Called at the end of Boost's FlairColors constructor. */
    public static int readableTextColor(int textColor, int backgroundColor) {
        // Transparent or translucent background: the label sits on the
        // theme background, which Boost already handles.
        if (Color.alpha(backgroundColor) < 0xF0) {
            return textColor;
        }

        if (contrast(textColor, backgroundColor) >= MIN_CONTRAST) {
            return textColor;
        }

        return contrast(Color.BLACK, backgroundColor) >= contrast(Color.WHITE, backgroundColor)
                ? Color.BLACK
                : Color.WHITE;
    }

    static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel(Color.red(color))
                + 0.7152 * channel(Color.green(color))
                + 0.0722 * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
