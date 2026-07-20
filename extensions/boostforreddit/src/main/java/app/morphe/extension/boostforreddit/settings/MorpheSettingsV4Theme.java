package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.TypedValue;

final class MorpheSettingsV4Theme {
    private static final String KEY_DYNAMIC_COLORS = "pref_dynamic_colors";

    static final class Accent {
        final int color;
        final int container;
        final int onContainer;

        Accent(int color, int container, int onContainer) {
            this.color = color;
            this.container = container;
            this.onContainer = onContainer;
        }
    }

    static final class Tokens {
        final boolean dark;
        final int background;
        final int surface;
        final int surfaceContainer;
        final int surfaceContainerHigh;
        final int primary;
        final int primaryContainer;
        final int onPrimaryContainer;
        final int secondary;
        final int secondaryContainer;
        final int onSecondaryContainer;
        final int tertiary;
        final int tertiaryContainer;
        final int onTertiaryContainer;
        final int textPrimary;
        final int textSecondary;
        final int outline;

        Tokens(
                boolean dark,
                int background,
                int surface,
                int surfaceContainer,
                int surfaceContainerHigh,
                int primary,
                int primaryContainer,
                int onPrimaryContainer,
                int secondary,
                int secondaryContainer,
                int onSecondaryContainer,
                int tertiary,
                int tertiaryContainer,
                int onTertiaryContainer,
                int textPrimary,
                int textSecondary,
                int outline
        ) {
            this.dark = dark;
            this.background = background;
            this.surface = surface;
            this.surfaceContainer = surfaceContainer;
            this.surfaceContainerHigh = surfaceContainerHigh;
            this.primary = primary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
            this.secondary = secondary;
            this.secondaryContainer = secondaryContainer;
            this.onSecondaryContainer = onSecondaryContainer;
            this.tertiary = tertiary;
            this.tertiaryContainer = tertiaryContainer;
            this.onTertiaryContainer = onTertiaryContainer;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.outline = outline;
        }

        Accent navigationAccent() {
            return new Accent(
                    secondary,
                    secondaryContainer,
                    onSecondaryContainer
            );
        }
    }

    private MorpheSettingsV4Theme() {
    }

    static Tokens resolve(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        int fallbackBackground = dark
                ? Color.rgb(28, 27, 30)
                : Color.rgb(255, 251, 254);
        int fallbackTextPrimary = dark
                ? Color.rgb(231, 225, 229)
                : Color.rgb(29, 27, 30);
        int fallbackTextSecondary = dark
                ? Color.rgb(202, 196, 208)
                : Color.rgb(73, 69, 79);
        int fallbackPrimary = dark
                ? Color.rgb(208, 188, 255)
                : Color.rgb(103, 80, 164);

        int paletteBackground = paletteColor(
                context,
                dark ? "system_neutral1_900" : "system_neutral1_10",
                fallbackBackground
        );
        int background = blend(
                dark ? Color.rgb(18, 18, 18) : Color.rgb(250, 249, 252),
                paletteBackground,
                dark ? 0.28f : 0.18f
        );
        int paletteSurface = paletteColor(
                context,
                dark ? "system_neutral1_900" : "system_neutral1_10",
                fallbackBackground
        );
        int surface = blend(background, paletteSurface, 0.10f);
        int paletteSurfaceContainer = paletteColor(
                context,
                dark ? "system_neutral1_800" : "system_neutral1_50",
                blend(background, fallbackTextPrimary, dark ? 0.075f : 0.055f)
        );
        int surfaceContainer = blend(
                blend(
                        background,
                        dark ? Color.WHITE : Color.BLACK,
                        dark ? 0.07f : 0.035f
                ),
                paletteSurfaceContainer,
                dark ? 0.20f : 0.15f
        );
        int paletteSurfaceContainerHigh = paletteColor(
                context,
                dark ? "system_neutral1_700" : "system_neutral1_100",
                blend(background, fallbackTextPrimary, dark ? 0.12f : 0.09f)
        );
        int surfaceContainerHigh = blend(
                blend(
                        background,
                        dark ? Color.WHITE : Color.BLACK,
                        dark ? 0.11f : 0.065f
                ),
                paletteSurfaceContainerHigh,
                dark ? 0.20f : 0.15f
        );
        int paletteTextPrimary = paletteColor(
                context,
                dark ? "system_neutral1_100" : "system_neutral1_900",
                fallbackTextPrimary
        );
        int textPrimary = blend(
                dark ? Color.rgb(242, 239, 242) : Color.rgb(33, 31, 33),
                paletteTextPrimary,
                dark ? 0.18f : 0.12f
        );
        int paletteTextSecondary = paletteColor(
                context,
                dark ? "system_neutral2_200" : "system_neutral2_700",
                fallbackTextSecondary
        );
        int textSecondary = blend(
                dark ? Color.rgb(199, 195, 199) : Color.rgb(91, 87, 92),
                paletteTextSecondary,
                dark ? 0.20f : 0.14f
        );
        int paletteOutline = paletteColor(
                context,
                dark ? "system_neutral2_400" : "system_neutral2_500",
                withAlpha(fallbackTextSecondary, dark ? 150 : 135)
        );
        int outline = blend(
                dark ? Color.rgb(146, 142, 147) : Color.rgb(120, 116, 121),
                paletteOutline,
                0.18f
        );

        int primary = paletteColor(
                context,
                dark ? "system_accent1_200" : "system_accent1_600",
                fallbackPrimary
        );
        int primaryContainer = paletteColor(
                context,
                dark ? "system_accent1_700" : "system_accent1_100",
                blend(background, primary, dark ? 0.34f : 0.18f)
        );
        int onPrimaryContainer = paletteColor(
                context,
                dark ? "system_accent1_100" : "system_accent1_900",
                bestTextColor(primaryContainer, textPrimary)
        );

        int secondary = paletteColor(
                context,
                dark ? "system_accent2_200" : "system_accent2_600",
                dark ? Color.rgb(204, 194, 220) : Color.rgb(98, 91, 113)
        );
        int secondaryContainer = paletteColor(
                context,
                dark ? "system_accent2_700" : "system_accent2_100",
                dark ? Color.rgb(74, 68, 88) : Color.rgb(232, 222, 248)
        );
        int onSecondaryContainer = paletteColor(
                context,
                dark ? "system_accent2_100" : "system_accent2_900",
                bestTextColor(secondaryContainer, textPrimary)
        );

        int tertiary = paletteColor(
                context,
                dark ? "system_accent3_200" : "system_accent3_600",
                dark ? Color.rgb(239, 184, 200) : Color.rgb(125, 82, 96)
        );
        int tertiaryContainer = paletteColor(
                context,
                dark ? "system_accent3_700" : "system_accent3_100",
                dark ? Color.rgb(99, 59, 72) : Color.rgb(255, 216, 228)
        );
        int onTertiaryContainer = paletteColor(
                context,
                dark ? "system_accent3_100" : "system_accent3_900",
                bestTextColor(tertiaryContainer, textPrimary)
        );

        return new Tokens(
                dark,
                background,
                surface,
                surfaceContainer,
                surfaceContainerHigh,
                primary,
                primaryContainer,
                onPrimaryContainer,
                secondary,
                secondaryContainer,
                onSecondaryContainer,
                tertiary,
                tertiaryContainer,
                onTertiaryContainer,
                textPrimary,
                textSecondary,
                outline
        );
    }

    static Drawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static Drawable interactive(
            Context context,
            int color,
            float radiusDp,
            int rippleColor
    ) {
        Drawable content = rounded(context, color, radiusDp);
        if (Build.VERSION.SDK_INT < 21) {
            return content;
        }
        return new RippleDrawable(
                ColorStateList.valueOf(withAlpha(rippleColor, 46)),
                content,
                null
        );
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        ));
    }

    static int blend(int base, int overlay, float amount) {
        float inverse = 1.0f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * inverse + Color.red(overlay) * amount),
                Math.round(Color.green(base) * inverse + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount)
        );
    }

    static int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private static int paletteColor(
            Context context,
            String resourceName,
            int fallback
    ) {
        if (Build.VERSION.SDK_INT < 31
                || !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_DYNAMIC_COLORS, false)) {
            return fallback;
        }

        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(resourceName, "color", "android");
        if (resourceId == 0) {
            return fallback;
        }
        return resources.getColor(resourceId, context.getTheme());
    }

    private static int bestTextColor(int background, int preferred) {
        double backgroundLuminance = luminance(background);
        double preferredContrast = contrast(backgroundLuminance, luminance(preferred));
        int alternative = backgroundLuminance > 0.45 ? Color.BLACK : Color.WHITE;
        double alternativeContrast = contrast(backgroundLuminance, luminance(alternative));
        return preferredContrast >= alternativeContrast ? preferred : alternative;
    }

    private static double luminance(int color) {
        double red = channel(Color.red(color) / 255.0);
        double green = channel(Color.green(color) / 255.0);
        double blue = channel(Color.blue(color) / 255.0);
        return red * 0.2126 + green * 0.7152 + blue * 0.0722;
    }

    private static double channel(double value) {
        return value <= 0.03928
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double first, double second) {
        double light = Math.max(first, second);
        double dark = Math.min(first, second);
        return (light + 0.05) / (dark + 0.05);
    }
}
