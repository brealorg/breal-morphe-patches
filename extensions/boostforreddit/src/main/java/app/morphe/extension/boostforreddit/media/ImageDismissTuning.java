/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 section 7 terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies issue #96 tuning only to Boost image viewers.
 *
 * Value 12 preserves Boost exactly. Value 50 enables only the
 * library's native fling-back decision while retaining Boost's
 * 15 percent distance threshold. Values above 50 keep fling-back
 * enabled and interpolate the threshold down to 10 percent.
 */
public final class ImageDismissTuning {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_IMAGE_DISMISS_TUNING_ISSUE96_V4_1";

    private static final String LOG_TAG = "MorpheImageDismiss";
    private static final String PREF_KEY =
            "morphe_boost_image_dismiss_sensitivity";

    private static final int NATIVE_VALUE = 12;
    private static final int DEFAULT_VALUE = 100;
    private static final int FLING_ONLY_VALUE = 50;
    private static final int MAX_VALUE = 100;

    private static final float NATIVE_BACK_FACTOR = 0.15f;
    private static final float MAX_BACK_FACTOR = 0.10f;

    private static final String SWIPE_BACK_LAYOUT_CLASS =
            "com.liuguangqiang.swipeback.SwipeBackLayout";
    private static final String SET_ENABLE_FLING_BACK_METHOD =
            "setEnableFlingBack";
    private static final String SET_BACK_FACTOR_METHOD =
            "setBackFactor";

    private ImageDismissTuning() {
    }

    public static void apply(Object owner) {
        if (owner == null) {
            return;
        }

        try {
            Object swipeBackLayout = findSwipeBackLayout(owner);
            if (!(swipeBackLayout instanceof ViewGroup)) {
                Log.w(
                        LOG_TAG,
                        CONTRACT_MARKER
                                + ": swipe layout unavailable"
                );
                return;
            }

            Context context =
                    ((ViewGroup) swipeBackLayout).getContext();
            if (context == null) {
                Log.w(
                        LOG_TAG,
                        CONTRACT_MARKER + ": context unavailable"
                );
                return;
            }

            int selected = selectedValue(context);
            if (selected <= NATIVE_VALUE) {
                Log.d(
                        LOG_TAG,
                        CONTRACT_MARKER
                                + ": native behavior value="
                                + selected
                                + " flingBack=false"
                                + " backFactor="
                                + NATIVE_BACK_FACTOR
                );
                return;
            }

            Method setEnableFlingBack =
                    swipeBackLayout
                            .getClass()
                            .getMethod(
                                    SET_ENABLE_FLING_BACK_METHOD,
                                    Boolean.TYPE
                            );
            setEnableFlingBack.invoke(
                    swipeBackLayout,
                    true
            );

            float backFactor = backFactor(selected);

            Method setBackFactor =
                    swipeBackLayout
                            .getClass()
                            .getMethod(
                                    SET_BACK_FACTOR_METHOD,
                                    Float.TYPE
                            );
            setBackFactor.invoke(
                    swipeBackLayout,
                    backFactor
            );

            String mode =
                    selected <= FLING_ONLY_VALUE
                            ? "FLING_ONLY"
                            : "FLING_PLUS_DISTANCE";

            Log.i(
                    LOG_TAG,
                    CONTRACT_MARKER
                            + ": applied value="
                            + selected
                            + " flingBack=true"
                            + " backFactor="
                            + backFactor
                            + " dismissDistancePercent="
                            + Math.round(backFactor * 100.0f)
                            + " mode="
                            + mode
            );
        } catch (Throwable throwable) {
            Log.w(
                    LOG_TAG,
                    CONTRACT_MARKER + ": fail-open",
                    throwable
            );
        }
    }

    private static int selectedValue(Context context) {
        SharedPreferences preferences =
                PreferenceManager
                        .getDefaultSharedPreferences(context);
        int value =
                preferences.getInt(PREF_KEY, DEFAULT_VALUE);

        return Math.max(
                NATIVE_VALUE,
                Math.min(MAX_VALUE, value)
        );
    }

    private static float backFactor(int selected) {
        if (selected <= FLING_ONLY_VALUE) {
            return NATIVE_BACK_FACTOR;
        }

        float fraction =
                (selected - FLING_ONLY_VALUE)
                        / (float) (
                                MAX_VALUE - FLING_ONLY_VALUE
                        );

        return NATIVE_BACK_FACTOR
                - fraction
                * (
                        NATIVE_BACK_FACTOR
                                - MAX_BACK_FACTOR
                );
    }

    private static Object findSwipeBackLayout(Object owner)
            throws IllegalAccessException {
        List<Object> matches = new ArrayList<>();

        for (Field field : allFields(owner.getClass())) {
            if (!SWIPE_BACK_LAYOUT_CLASS.equals(
                    field.getType().getName()
            )) {
                continue;
            }

            field.setAccessible(true);
            Object value = field.get(owner);

            if (value != null) {
                matches.add(value);
            }
        }

        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected one SwipeBackLayout, found "
                            + matches.size()
            );
        }

        return matches.get(0);
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;

        while (current != null
                && current != Object.class) {
            Field[] declared = current.getDeclaredFields();

            for (Field field : declared) {
                fields.add(field);
            }

            current = current.getSuperclass();
        }

        return fields;
    }
}
