package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;

/** In-app navigation between hidden V5 pages without a legacy Settings route. */
final class MorpheSettingsV5Navigation {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_NAVIGATION_ISSUE121_V1";

    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";
    private static final String SETTINGS_PACKAGE =
            "app.morphe.extension.boostforreddit.settings.";

    private MorpheSettingsV5Navigation() {
    }

    static void openPage(Fragment host, String targetPageId) {
        if (host == null || TextUtils.isEmpty(targetPageId)) {
            return;
        }
        MorpheSettingsV5Registry.V5PageSpec page =
                MorpheSettingsV5Registry.findPage(targetPageId);
        if (page == null) {
            return;
        }

        Context context;
        try {
            context = host.requireContext();
        } catch (Throwable ignored) {
            return;
        }
        if (!(context instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) context;
        String renderer = page.renderer;
        String className = renderer.indexOf('.') >= 0
                ? renderer
                : SETTINGS_PACKAGE + renderer;
        Intent intent = new Intent(activity, activity.getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, className);
        intent.putExtra(MorpheSettingsV5Registry.EXTRA_PAGE_ID, targetPageId);
        activity.startActivity(intent);
    }
}
