package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Field;

/**
 * Morphe-native pilot for Boost's toolbar preferences.
 *
 * <p>This fragment deliberately owns only the five controls from
 * {@code pref_toolbar_v2}. It writes Boost's canonical preference keys and
 * mirrors the refresh flags used by PreferenceFragmentToolbarCompat.</p>
 */
public final class MorpheSettingsV4ToolbarFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_TOOLBAR_PILOT_ISSUE106_V1";
    public static final String SIDE_EFFECT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_TOOLBAR_REFRESH_FLAGS_ISSUE106_V1";

    private static final String KEY_MAIN_ACTION = "pref_toolbar_main_action";
    private static final String KEY_HIDE_ON_SCROLL = "pref_toolbar";
    private static final String KEY_SHOW_HEADER = "pref_show_subreddit_header";
    private static final String KEY_HEADER_TYPE = "pref_toolbar_header_type";
    private static final String KEY_SHOW_DESCRIPTION =
            "pref_header_show_description";

    private static final String[] MAIN_ACTION_TITLES = new String[]{
            "Search",
            "Post view",
            "Refresh",
    };
    private static final String[] MAIN_ACTION_VALUES = new String[]{
            "0",
            "1",
            "2",
    };
    private static final String[] HEADER_TYPE_TITLES = new String[]{
            "Center",
            "Left",
    };
    private static final String[] HEADER_TYPE_VALUES = new String[]{
            "center",
            "left",
    };

    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences preferences;
    private TextView mainActionSummary;
    private TextView headerTypeSummary;
    private LinearLayout headerTypeRow;
    private LinearLayout showDescriptionRow;

    public MorpheSettingsV4ToolbarFragment() {
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
        content.setPadding(dp(16), dp(10), dp(16), dp(32));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addSectionLabel(content, "Toolbar actions");
        addSpace(content, 8);
        LinearLayout actions = addGroup(content);

        LinearLayout mainActionRow = addChoiceRow(
                actions,
                "Main action",
                mainActionTitle(preferences.getString(KEY_MAIN_ACTION, "1")),
                view -> showChoiceDialog(
                        "Main action",
                        KEY_MAIN_ACTION,
                        MAIN_ACTION_TITLES,
                        MAIN_ACTION_VALUES,
                        "1"
                )
        );
        mainActionSummary = rowSummary(mainActionRow);

        addSwitchRow(
                actions,
                "Hide on scroll",
                "Hide the top toolbar while scrolling feeds",
                preferences.getBoolean(KEY_HIDE_ON_SCROLL, true),
                checked -> applyBooleanPreference(KEY_HIDE_ON_SCROLL, checked)
        );

        addSpace(content, 24);
        addSectionLabel(content, "Community header");
        addSpace(content, 8);
        LinearLayout communityHeader = addGroup(content);

        addSwitchRow(
                communityHeader,
                "Show community header",
                "Show banner and icon in communities",
                preferences.getBoolean(KEY_SHOW_HEADER, false),
                checked -> {
                    applyBooleanPreference(KEY_SHOW_HEADER, checked);
                    updateHeaderDependencies(checked);
                }
        );

        headerTypeRow = addChoiceRow(
                communityHeader,
                "Community info alignment",
                headerTypeTitle(preferences.getString(KEY_HEADER_TYPE, "center")),
                view -> showChoiceDialog(
                        "Community info alignment",
                        KEY_HEADER_TYPE,
                        HEADER_TYPE_TITLES,
                        HEADER_TYPE_VALUES,
                        "center"
                )
        );
        headerTypeSummary = rowSummary(headerTypeRow);

        showDescriptionRow = addSwitchRow(
                communityHeader,
                "Show community description",
                "Display the community description below its header",
                preferences.getBoolean(KEY_SHOW_DESCRIPTION, true),
                checked -> applyBooleanPreference(KEY_SHOW_DESCRIPTION, checked)
        );

        updateHeaderDependencies(preferences.getBoolean(KEY_SHOW_HEADER, false));
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSummaries();
        if (preferences != null) {
            updateHeaderDependencies(
                    preferences.getBoolean(KEY_SHOW_HEADER, false)
            );
        }
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

    private LinearLayout addChoiceRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            View.OnClickListener listener
    ) {
        LinearLayout row = baseRow();
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(listener);
        row.addView(createLabels(titleValue, summaryValue), labelsParams());

        row.addView(MorpheSettingsV14Ui.chevron(requireContext(), tokens));
        addGroupedRow(parent, row);
        return row;
    }

    private LinearLayout addSwitchRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            boolean checked,
            CheckedChange change
    ) {
        LinearLayout row = baseRow();
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(createLabels(titleValue, summaryValue), labelsParams());

        MorpheSettingsV14Ui.Toggle toggle =
                new MorpheSettingsV14Ui.Toggle(
                        requireContext(),
                        tokens,
                        checked
                );
        toggle.setOnCheckedChangeListener(
                (button, value) -> change.onChanged(value)
        );
        row.setOnClickListener(view -> toggle.toggle());
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addGroupedRow(parent, row);
        return row;
    }

    private LinearLayout baseRow() {
        return MorpheSettingsV14Ui.baseRow(requireContext(), tokens);
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

    private TextView rowSummary(LinearLayout row) {
        if (row == null || row.getChildCount() == 0) {
            return null;
        }
        View labelsView = row.getChildAt(0);
        if (!(labelsView instanceof LinearLayout)) {
            return null;
        }
        LinearLayout labels = (LinearLayout) labelsView;
        if (labels.getChildCount() < 2
                || !(labels.getChildAt(1) instanceof TextView)) {
            return null;
        }
        return (TextView) labels.getChildAt(1);
    }

    private void showChoiceDialog(
            String titleValue,
            String key,
            String[] titles,
            String[] values,
            String defaultValue
    ) {
        Context context = requireContext();
        Dialog dialog = new Dialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                tokens.surfaceContainerHigh,
                28
        ));

        TextView title = textView(titleValue, 20, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(12), dp(8), dp(12), dp(10));
        content.addView(title);

        String current = preferences.getString(key, defaultValue);
        LinearLayout choiceGroup = MorpheSettingsV14Ui.group(context);
        content.addView(choiceGroup, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (int index = 0; index < values.length; index++) {
            final String selectedValue = values[index];
            MorpheSettingsV14Ui.ChoiceRow option =
                    MorpheSettingsV14Ui.choiceRow(
                    context,
                    tokens,
                    titles[index],
                    "",
                    selectedValue.equals(current)
            );
            option.setOnClickListener(view -> {
                applyChoicePreference(key, selectedValue);
                updateSummaries();
                dialog.dismiss();
            });
            MorpheSettingsV14Ui.addSegmentedRow(
                    choiceGroup,
                    option,
                    tokens
            );
        }

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
        }
        dialog.setOnShowListener(ignored -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                int width = context.getResources().getDisplayMetrics().widthPixels
                        - dp(40);
                shownWindow.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void applyChoicePreference(String key, String value) {
        if (KEY_MAIN_ACTION.equals(key)) {
            requireValue(MAIN_ACTION_VALUES, value, key);
            preferences.edit().putString(key, value).apply();
            setBoostStaticBoolean("i");
            return;
        }
        if (KEY_HEADER_TYPE.equals(key)) {
            requireValue(HEADER_TYPE_VALUES, value, key);
            preferences.edit().putString(key, value).apply();
            setBoostStaticBoolean("e");
            return;
        }
        throw new IllegalArgumentException("unsupported toolbar choice " + key);
    }

    private void applyBooleanPreference(String key, boolean value) {
        if (!KEY_HIDE_ON_SCROLL.equals(key)
                && !KEY_SHOW_HEADER.equals(key)
                && !KEY_SHOW_DESCRIPTION.equals(key)) {
            throw new IllegalArgumentException("unsupported toolbar boolean " + key);
        }
        preferences.edit().putBoolean(key, value).apply();
        setBoostStaticBoolean("e");
    }

    private void updateSummaries() {
        if (preferences == null) {
            return;
        }
        if (mainActionSummary != null) {
            mainActionSummary.setText(mainActionTitle(
                    preferences.getString(KEY_MAIN_ACTION, "1")
            ));
        }
        if (headerTypeSummary != null) {
            headerTypeSummary.setText(headerTypeTitle(
                    preferences.getString(KEY_HEADER_TYPE, "center")
            ));
        }
    }

    private void updateHeaderDependencies(boolean enabled) {
        setRowEnabled(headerTypeRow, enabled);
        setRowEnabled(showDescriptionRow, enabled);
    }

    private void setRowEnabled(LinearLayout row, boolean enabled) {
        if (row == null) {
            return;
        }
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1.0f : 0.44f);
        setChildrenEnabled(row, enabled);
    }

    private void setChildrenEnabled(ViewGroup parent, boolean enabled) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) {
                setChildrenEnabled((ViewGroup) child, enabled);
            }
        }
    }

    private String mainActionTitle(String value) {
        return titleFor(MAIN_ACTION_TITLES, MAIN_ACTION_VALUES, value, "Post view");
    }

    private String headerTypeTitle(String value) {
        return titleFor(HEADER_TYPE_TITLES, HEADER_TYPE_VALUES, value, "Center");
    }

    private String titleFor(
            String[] titles,
            String[] values,
            String value,
            String fallback
    ) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(value)) {
                return titles[index];
            }
        }
        return fallback;
    }

    private void requireValue(String[] values, String value, String key) {
        for (String candidate : values) {
            if (candidate.equals(value)) {
                return;
            }
        }
        throw new IllegalArgumentException("unsupported value for " + key);
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

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Toolbar");
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

    private interface CheckedChange {
        void onChanged(boolean checked);
    }
}
