package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Field;

/** Morphe-owned appearance controls for the Settings v4 preview. */
public final class MorpheSettingsV4AppearanceFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_APPEARANCE_ISSUE106_V1";
    public static final String COMPACT_GROUPS_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_COMPACT_GROUPS_ISSUE106_V1";
    public static final String SYSTEM_THEME_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_SYSTEM_THEME_ISSUE106_V1";

    private static final String KEY_DYNAMIC_COLORS = "pref_dynamic_colors";
    private static final String KEY_COLORED_STATUS_BAR =
            "pref_colored_status_bar";
    private static final String KEY_COLORED_NAV_BAR =
            "pref_colored_nav_bar";
    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";

    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences preferences;

    public MorpheSettingsV4AppearanceFragment() {
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Context context = inflater.getContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        setHasOptionsMenu(true);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(16);
        content.setPadding(horizontal, dp(10), horizontal, dp(32));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSectionLabel(content, "Appearance");
        addSpace(content, 8);

        LinearLayout appearanceGroup = addGroup(content);
        if (Build.VERSION.SDK_INT >= 31) {
            addSwitchRow(
                    appearanceGroup,
                    "Dynamic color",
                    "Use Android's selected color palette",
                    preferences.getBoolean(KEY_DYNAMIC_COLORS, false),
                    checked -> updateDynamicColors(checked)
            );
        }

        addSpace(content, 24);
        addSectionLabel(content, "Personalization");
        addSpace(content, 8);

        LinearLayout personalizationGroup = addGroup(content);
        addNavigationRow(
                personalizationGroup,
                "App icon",
                "Choose the icon shown by your launcher",
                true,
                view -> openMorpheFragment(
                        MorpheSettingsV4Catalog.V4_APP_ICON_FRAGMENT
                )
        );

        addSpace(content, 24);
        addSectionLabel(content, "System bars");
        addSpace(content, 8);

        LinearLayout systemBarsGroup = addGroup(content);
        addSwitchRow(
                systemBarsGroup,
                "Colored status bar",
                "Match the status bar to Boost's toolbar",
                preferences.getBoolean(KEY_COLORED_STATUS_BAR, true),
                checked -> updateSystemBarPreference(
                        KEY_COLORED_STATUS_BAR,
                        checked
                )
        );
        addSwitchRow(
                systemBarsGroup,
                "Colored navigation bar",
                "Match the system navigation area to Boost's toolbar",
                preferences.getBoolean(KEY_COLORED_NAV_BAR, false),
                checked -> updateSystemBarPreference(
                        KEY_COLORED_NAV_BAR,
                        checked
                )
        );

        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHost();
    }

    @Override
    public void onPause() {
        Activity activity = hostActivity();
        if (activity != null) {
            BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);
        }
        super.onPause();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        hideMenuItem(menu, "action_generic_search");
        hideMenuItem(menu, "action_search");
        hideMenuItem(menu, "search");
    }

    private LinearLayout addGroup(LinearLayout parent) {
        LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
        parent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return group;
    }

    private void addNavigationRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            boolean enabled,
            View.OnClickListener listener
    ) {
        Context context = requireContext();
        LinearLayout row = baseRow(context);
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1.0f : 0.44f);
        if (enabled) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(listener);
        }

        row.addView(createLabels(titleValue, summaryValue), labelsParams());

        row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
        addGroupedRow(parent, row);
    }

    private void addSwitchRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            boolean checked,
            CheckedChange change
    ) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = baseRow(context);
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(createLabels(titleValue, summaryValue), labelsParams());

        MorpheSettingsV14Ui.Toggle toggle =
                new MorpheSettingsV14Ui.Toggle(context, tokens, checked);
        toggle.setOnCheckedChangeListener(
                (button, value) -> change.onChanged(value)
        );
        row.setOnClickListener(view -> toggle.toggle());
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addGroupedRow(parent, row);
    }

    private LinearLayout baseRow(Context context) {
        return MorpheSettingsV14Ui.baseRow(context, tokens);
    }

    private void addGroupedRow(LinearLayout group, LinearLayout row) {
        MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
    }

    private void addGroupDivider(LinearLayout group) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(MorpheSettingsV4Theme.blend(
                tokens.surfaceContainer,
                tokens.outline,
                tokens.dark ? 0.22f : 0.16f
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        params.setMarginStart(dp(18));
        params.setMarginEnd(dp(18));
        group.addView(divider, params);
    }

    private LinearLayout createLabels(String titleValue, String summaryValue) {
        return MorpheSettingsV14Ui.labels(
                requireContext(),
                tokens,
                titleValue,
                summaryValue
        );
    }

    private void updateDynamicColors(boolean enabled) {
        applyDynamicColors(preferences, enabled);
        recreateHost();
    }

    static void applyDynamicColors(
            SharedPreferences preferences,
            boolean enabled
    ) {
        preferences.edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply();
        setBoostStaticBoolean("i");
    }

    private void updateSystemBarPreference(String key, boolean enabled) {
        applySystemBarPreference(preferences, key, enabled);
        recreateHost();
    }

    static void applySystemBarPreference(
            SharedPreferences preferences,
            String key,
            boolean enabled
    ) {
        if (!KEY_COLORED_STATUS_BAR.equals(key)
                && !KEY_COLORED_NAV_BAR.equals(key)) {
            throw new IllegalArgumentException("unsupported system bar key " + key);
        }
        preferences.edit().putBoolean(key, enabled).apply();
        setBoostStaticBoolean("h");
    }

    private static void setBoostStaticBoolean(String fieldName) {
        try {
            Class<?> settings = Class.forName("id.b");
            Field field = settings.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }
    }

    private void openMorpheFragment(String fragmentName) {
        Activity activity = hostActivity();
        if (activity == null) {
            showUnavailable();
            return;
        }
        try {
            Intent intent = new Intent(activity, activity.getClass());
            intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
            activity.startActivity(intent);
        } catch (Throwable ignored) {
            showUnavailable();
        }
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Appearance");
        BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(
                activity,
                tokens.background,
                tokens.dark
        );
        int toolbarId = MorpheSettingsV4Catalog.resourceId(
                activity,
                "id",
                "toolbar"
        );
        View toolbar = activity.findViewById(toolbarId);
        if (toolbar != null) {
            toolbar.setBackgroundColor(tokens.background);
            toolbar.setElevation(0);
        }
    }

    private Activity hostActivity() {
        Context context = requireContext();
        return context instanceof Activity ? (Activity) context : null;
    }

    private void recreateHost() {
        Activity activity = hostActivity();
        if (activity != null) {
            activity.recreate();
        }
    }

    private void hideMenuItem(Menu menu, String resourceName) {
        int resourceId = MorpheSettingsV4Catalog.resourceId(
                requireContext(),
                "id",
                resourceName
        );
        if (resourceId == 0) {
            return;
        }
        MenuItem item = menu.findItem(resourceId);
        if (item != null) {
            item.setVisible(false);
        }
    }

    private void addSectionLabel(LinearLayout parent, String value) {
        parent.addView(MorpheSettingsV14Ui.sectionLabel(
                requireContext(),
                tokens,
                value
        ));
    }

    private TextView textView(String value, int sizeSp, int color) {
        TextView textView = new TextView(requireContext());
        textView.setText(value);
        textView.setTextSize(sizeSp);
        textView.setTextColor(color);
        return textView;
    }

    private LinearLayout.LayoutParams labelsParams() {
        return new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(requireContext());
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(float value) {
        return MorpheSettingsV4Theme.dp(requireContext(), value);
    }

    private void showUnavailable() {
        Toast.makeText(
                requireContext(),
                "This appearance control is unavailable in the current Boost build.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private interface CheckedChange {
        void onChanged(boolean checked);
    }
}
