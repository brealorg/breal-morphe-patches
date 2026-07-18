package app.morphe.extension.boostforreddit;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Removes profile tabs backed by Reddit features that are no longer usable in Boost. */
public final class RemoveObsoleteProfileTabsPatch {
    private static final String LOG_TAG = "MorpheProfileTabs";
    private static final String MARKER =
            "MORPHE_BOOST_REMOVE_OBSOLETE_PROFILE_TABS_V2";

    private RemoveObsoleteProfileTabsPatch() {
    }

    public static void removeObsoleteTabs(Object activity) {
        if (activity == null) {
            return;
        }

        try {
            Class<?> activityClass = activity.getClass();
            Field[] fields = activityClass.getDeclaredFields();

            String[] routes = null;
            int originalLength = -1;

            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) ||
                        field.getType() != String[].class) {
                    continue;
                }

                field.setAccessible(true);
                String[] values = (String[]) field.get(activity);
                if (values != null &&
                        contains(values, "overview") &&
                        contains(values, "gilded")) {
                    routes = values;
                    originalLength = values.length;
                    break;
                }
            }

            if (routes == null) {
                return;
            }

            boolean[] remove = new boolean[originalLength];
            int removedCount = 0;
            for (int index = 0; index < routes.length; index++) {
                if ("gilded".equals(routes[index]) ||
                        "friends".equals(routes[index])) {
                    remove[index] = true;
                    removedCount++;
                }
            }

            if (removedCount == 0) {
                return;
            }

            List<Field> tabArrayFields = new ArrayList<>();
            List<String[]> filteredArrays = new ArrayList<>();
            List<Field> countFields = new ArrayList<>();

            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                if (field.getType() == String[].class) {
                    String[] values = (String[]) field.get(activity);
                    if (values != null && values.length == originalLength) {
                        tabArrayFields.add(field);
                        filteredArrays.add(filter(values, remove, removedCount));
                    }
                } else if (field.getType() == int.class &&
                        field.getInt(activity) == originalLength) {
                    countFields.add(field);
                }
            }

            if (tabArrayFields.size() != 2 || countFields.size() != 1) {
                Log.e(
                        LOG_TAG,
                        MARKER + " refused arrays=" + tabArrayFields.size()
                                + " counts=" + countFields.size()
                                + " original=" + originalLength
                );
                return;
            }

            for (int index = 0; index < tabArrayFields.size(); index++) {
                tabArrayFields.get(index).set(activity, filteredArrays.get(index));
            }

            int newLength = originalLength - removedCount;
            countFields.get(0).setInt(activity, newLength);

            Log.d(
                    LOG_TAG,
                    MARKER + " old=" + originalLength
                            + " new=" + newLength
                            + " removed=" + removedCount
            );
        } catch (Throwable throwable) {
            Log.e(LOG_TAG, MARKER + " failed", throwable);
        }
    }

    private static boolean contains(String[] values, String expected) {
        for (String value : values) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String[] filter(
            String[] values,
            boolean[] remove,
            int removedCount
    ) {
        String[] filtered = new String[values.length - removedCount];
        int target = 0;
        for (int source = 0; source < values.length; source++) {
            if (!remove[source]) {
                filtered[target++] = values[source];
            }
        }
        return filtered;
    }
}
