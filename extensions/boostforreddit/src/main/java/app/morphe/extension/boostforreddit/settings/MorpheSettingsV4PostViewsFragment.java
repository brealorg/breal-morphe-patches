package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Field;

/** Morphe-owned post-view controls backed by Boost's canonical preferences. */
public final class MorpheSettingsV4PostViewsFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_POST_VIEWS_ISSUE106_V1";

    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";

    private static final String KEY_DEFAULT_VIEW = "pref_view";
    private static final String KEY_REMEMBER_PER_COMMUNITY =
            "pref_view_per_subscription";
    private static final String KEY_LEFT_HANDED = "pref_left_handed";
    private static final String KEY_SUBREDDIT_PREFIX =
            "pref_show_subreddit_prefix";
    private static final String KEY_CARDS_ROUNDED =
            "pref_cards_rounded_corners";
    private static final String KEY_CARDS_FULL_PREVIEW =
            "pref_cards_full_preview";
    private static final String KEY_CARDS_SUBREDDIT_ICON =
            "pref_cards_subreddit_icon";
    private static final String KEY_CARDS_GALLERY_CAROUSEL =
            "pref_cards_gallery_carousel";
    private static final String KEY_CARDS_LINK_THUMBNAILS =
            "pref_cards_links_as_thumbnails";
    private static final String KEY_CARDS_PREVIEW_TEXT =
            "pref_cards_preview_self";
    private static final String KEY_CARDS_PREVIEW_LINES =
            "pref_cards_preview_self_lines";
    private static final String KEY_MINI_ROUNDED =
            "pref_mini_cards_rounded_corners";
    private static final String KEY_MINI_TRUNCATE_TITLE =
            "pref_mini_cards_truncate_title";
    private static final String KEY_MINI_BUTTONS =
            "pref_mini_cards_buttons_visible";
    private static final String KEY_DENSE_BUTTONS =
            "pref_dense_buttons_visible";
    private static final String KEY_PREVIEW_EXTERNAL_LINKS =
            "pref_load_readability";
    private static final String KEY_LOCK_SIDEBAR = "pref_lock_sidebar";

    private static final String[] VIEW_TITLES = new String[]{
            "Cards",
            "Cards 2.0",
            "Compact",
            "Small cards",
            "Dense",
            "Columns",
            "Images",
            "Swipe",
    };
    private static final String[] VIEW_VALUES = new String[]{
            "0", "7", "1", "4", "5", "2", "6", "3",
    };

    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences preferences;
    private TextView defaultViewSummary;
    private LinearLayout manageSavedViewsRow;
    private LinearLayout previewLinesRow;
    private SeekBar previewLinesSeekBar;

    public MorpheSettingsV4PostViewsFragment() {
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
        int horizontal = dp(18);
        content.setPadding(horizontal, dp(12), horizontal, dp(32));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSectionLabel(content, "Layout");
        addSpace(content, 8);
        LinearLayout layoutGroup = addGroup(content);
        LinearLayout defaultViewRow = addNavigationRow(
                layoutGroup,
                "Default view",
                viewTitle(preferences.getString(KEY_DEFAULT_VIEW, "0")),
                true,
                view -> showDefaultViewDialog()
        );
        defaultViewSummary = rowSummary(defaultViewRow);
        addSwitchRow(
                layoutGroup,
                "Remember per community",
                "Use the last selected view for each community",
                preferences.getBoolean(KEY_REMEMBER_PER_COMMUNITY, false),
                checked -> {
                    putBoolean(KEY_REMEMBER_PER_COMMUNITY, checked, null);
                    setRowEnabled(manageSavedViewsRow, checked);
                }
        );
        manageSavedViewsRow = addNavigationRow(
                layoutGroup,
                "Manage saved views",
                "Review or clear community-specific views",
                preferences.getBoolean(KEY_REMEMBER_PER_COMMUNITY, false),
                view -> openMorpheFragment(
                        MorpheSettingsV4Catalog.V4_SAVED_VIEWS_FRAGMENT
                )
        );
        addSwitchRow(
                layoutGroup,
                "Thumbnails on left",
                "Place post thumbnails on the left side",
                preferences.getBoolean(KEY_LEFT_HANDED, false),
                checked -> putBoolean(KEY_LEFT_HANDED, checked, "h")
        );
        addSwitchRow(
                layoutGroup,
                "Communities start with r/",
                "Show Reddit's r/ prefix before community names",
                preferences.getBoolean(KEY_SUBREDDIT_PREFIX, false),
                this::updateSubredditPrefix
        );

        addSpace(content, 24);
        addSectionLabel(content, "Cards");
        addSpace(content, 8);
        LinearLayout cardsGroup = addGroup(content);
        addSwitchRow(cardsGroup, "Rounded corners", null,
                preferences.getBoolean(KEY_CARDS_ROUNDED, false),
                checked -> putBoolean(KEY_CARDS_ROUNDED, checked, "g"));
        addSwitchRow(cardsGroup, "Full height images",
                "Turn off to use fixed-height images",
                preferences.getBoolean(KEY_CARDS_FULL_PREVIEW, false),
                checked -> putBoolean(KEY_CARDS_FULL_PREVIEW, checked, "g"));
        addSwitchRow(cardsGroup, "Show community icon", null,
                preferences.getBoolean(KEY_CARDS_SUBREDDIT_ICON, true),
                checked -> putBoolean(KEY_CARDS_SUBREDDIT_ICON, checked, "g"));
        addSwitchRow(cardsGroup, "Carousel for multiple images", null,
                preferences.getBoolean(KEY_CARDS_GALLERY_CAROUSEL, true),
                checked -> putBoolean(KEY_CARDS_GALLERY_CAROUSEL, checked, "g"));
        addSwitchRow(cardsGroup, "Show thumbnails for link posts",
                "Turn off to use large image previews",
                preferences.getBoolean(KEY_CARDS_LINK_THUMBNAILS, true),
                checked -> putBoolean(KEY_CARDS_LINK_THUMBNAILS, checked, "g"));
        addSwitchRow(cardsGroup, "Preview text from posts", null,
                preferences.getBoolean(KEY_CARDS_PREVIEW_TEXT, true),
                checked -> {
                    putBoolean(KEY_CARDS_PREVIEW_TEXT, checked, "g");
                    setPreviewLinesEnabled(checked);
                });
        previewLinesRow = addSeekBarRow(
                cardsGroup,
                "Lines to preview",
                preferences.getInt(KEY_CARDS_PREVIEW_LINES, 5)
        );
        setPreviewLinesEnabled(
                preferences.getBoolean(KEY_CARDS_PREVIEW_TEXT, true)
        );

        addSpace(content, 24);
        addSectionLabel(content, "Small cards");
        addSpace(content, 8);
        LinearLayout miniCardsGroup = addGroup(content);
        addSwitchRow(miniCardsGroup, "Rounded corners", null,
                preferences.getBoolean(KEY_MINI_ROUNDED, true),
                checked -> putBoolean(KEY_MINI_ROUNDED, checked, "g"));
        addSwitchRow(miniCardsGroup, "Truncate title",
                "Limit the post title to two lines",
                preferences.getBoolean(KEY_MINI_TRUNCATE_TITLE, true),
                checked -> putBoolean(KEY_MINI_TRUNCATE_TITLE, checked, "g"));
        addSwitchRow(miniCardsGroup, "Buttons always visible",
                "When off, long-press to show buttons",
                preferences.getBoolean(KEY_MINI_BUTTONS, false),
                checked -> putBoolean(KEY_MINI_BUTTONS, checked, "g"));

        addSpace(content, 24);
        addSectionLabel(content, "Dense");
        addSpace(content, 8);
        LinearLayout denseGroup = addGroup(content);
        addSwitchRow(denseGroup, "Buttons always visible",
                "When off, long-press to show buttons",
                preferences.getBoolean(KEY_DENSE_BUTTONS, false),
                checked -> putBoolean(KEY_DENSE_BUTTONS, checked, "h"));

        addSpace(content, 24);
        addSectionLabel(content, "Swipe");
        addSpace(content, 8);
        LinearLayout swipeGroup = addGroup(content);
        addSwitchRow(swipeGroup, "Preview external links",
                "Automatically load a text preview for external links",
                preferences.getBoolean(KEY_PREVIEW_EXTERNAL_LINKS, false),
                checked -> putBoolean(KEY_PREVIEW_EXTERNAL_LINKS, checked, null));
        addSwitchRow(swipeGroup, "Lock sidebar",
                "Disable opening the sidebar with a swipe",
                preferences.getBoolean(KEY_LOCK_SIDEBAR, false),
                checked -> putBoolean(KEY_LOCK_SIDEBAR, checked, "i"));

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
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        int groupColor = MorpheSettingsV4Theme.blend(
                tokens.surfaceContainer,
                accent.container,
                tokens.dark ? 0.035f : 0.025f
        );
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                groupColor,
                24
        ));
        group.setClipToOutline(true);
        parent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return group;
    }

    private LinearLayout addNavigationRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            boolean enabled,
            View.OnClickListener listener
    ) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = baseRow(context);
        row.addView(createLabels(titleValue, summaryValue), labelsParams());

        TextView chevron = textView("›", 28, accent.color);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(44)));
        row.setOnClickListener(listener);
        setRowEnabled(row, enabled);
        addGroupedRow(parent, row);
        return row;
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

        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        tintSwitch(toggle, accent);
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

    private LinearLayout addSeekBarRow(
            LinearLayout parent,
            String titleValue,
            int progress
    ) {
        Context context = requireContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(10));
        row.setMinimumHeight(dp(92));

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = textView(titleValue, 16, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.addView(title, labelsParams());
        TextView value = textView(linesSummary(progress), 14, tokens.textSecondary);
        heading.addView(value);
        row.addView(heading);

        previewLinesSeekBar = new SeekBar(context);
        previewLinesSeekBar.setMax(100);
        previewLinesSeekBar.setProgress(progress);
        if (Build.VERSION.SDK_INT >= 21) {
            previewLinesSeekBar.setProgressTintList(
                    ColorStateList.valueOf(tokens.navigationAccent().color)
            );
            previewLinesSeekBar.setThumbTintList(
                    ColorStateList.valueOf(tokens.navigationAccent().color)
            );
        }
        previewLinesSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int current,
                            boolean fromUser
                    ) {
                        value.setText(linesSummary(current));
                        if (fromUser) {
                            preferences.edit()
                                    .putInt(KEY_CARDS_PREVIEW_LINES, current)
                                    .apply();
                            setBoostStaticBoolean("g");
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                }
        );
        row.addView(previewLinesSeekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addGroupedRow(parent, row);
        return row;
    }

    private void showDefaultViewDialog() {
        Context context = requireContext();
        Dialog dialog = new Dialog(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(14));
        content.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                tokens.surfaceContainerHigh,
                28
        ));

        TextView heading = textView("Default view", 20, tokens.textPrimary);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams headingParams = wrapParams();
        headingParams.setMarginStart(dp(8));
        headingParams.bottomMargin = dp(8);
        content.addView(heading, headingParams);

        String selected = preferences.getString(KEY_DEFAULT_VIEW, "0");
        for (int index = 0; index < VIEW_TITLES.length; index++) {
            final int choice = index;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(4), 0, dp(8), 0);
            row.setBackground(MorpheSettingsV4Theme.interactive(
                    context,
                    Color.TRANSPARENT,
                    16,
                    tokens.navigationAccent().color
            ));

            RadioButton radio = new RadioButton(context);
            radio.setChecked(VIEW_VALUES[index].equals(selected));
            radio.setClickable(false);
            radio.setFocusable(false);
            if (Build.VERSION.SDK_INT >= 21) {
                radio.setButtonTintList(new ColorStateList(
                        new int[][]{
                                new int[]{android.R.attr.state_checked},
                                new int[]{-android.R.attr.state_checked},
                        },
                        new int[]{
                                tokens.navigationAccent().color,
                                tokens.textSecondary,
                        }
                ));
            }
            row.addView(radio);

            TextView label = textView(VIEW_TITLES[index], 16, tokens.textPrimary);
            row.addView(label, labelsParams());
            row.setOnClickListener(view -> {
                preferences.edit()
                        .putString(KEY_DEFAULT_VIEW, VIEW_VALUES[choice])
                        .apply();
                if (defaultViewSummary != null) {
                    defaultViewSummary.setText(VIEW_TITLES[choice]);
                }
                dialog.dismiss();
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        TextView cancel = textView("Cancel", 14, tokens.navigationAccent().color);
        cancel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cancel.setGravity(Gravity.CENTER);
        cancel.setAllCaps(false);
        cancel.setPadding(dp(14), dp(10), dp(14), dp(10));
        cancel.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = wrapParams();
        cancelParams.gravity = Gravity.END;
        cancelParams.topMargin = dp(4);
        content.addView(cancel, cancelParams);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            int width = context.getResources().getDisplayMetrics().widthPixels
                    - dp(40);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void putBoolean(String key, boolean value, String refreshFlag) {
        preferences.edit().putBoolean(key, value).apply();
        if (refreshFlag != null) {
            setBoostStaticBoolean(refreshFlag);
        }
    }

    private void updateSubredditPrefix(boolean enabled) {
        preferences.edit().putBoolean(KEY_SUBREDDIT_PREFIX, enabled).apply();
        setBoostStaticString("c", enabled ? "r/" : "");
        setBoostStaticBoolean("i");
    }

    private void setBoostStaticBoolean(String fieldName) {
        try {
            Class<?> settings = Class.forName("id.b");
            Field field = settings.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }
    }

    private void setBoostStaticString(String fieldName, String value) {
        try {
            Class<?> settings = Class.forName("id.b");
            Field field = settings.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
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
        activity.setTitle("Post views");
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

    private LinearLayout baseRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(68));
        row.setPadding(dp(18), dp(10), dp(14), dp(10));
        row.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                Color.TRANSPARENT,
                0,
                tokens.navigationAccent().color
        ));
        return row;
    }

    private void addGroupedRow(LinearLayout group, LinearLayout row) {
        if (group.getChildCount() > 0) {
            addGroupDivider(group);
        }
        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
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
        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = textView(titleValue, 16, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labels.addView(title);
        if (!TextUtils.isEmpty(summaryValue)) {
            TextView summary = textView(summaryValue, 14, tokens.textSecondary);
            summary.setLineSpacing(0, 1.04f);
            summary.setMaxLines(3);
            LinearLayout.LayoutParams params = wrapParams();
            params.topMargin = dp(3);
            labels.addView(summary, params);
        }
        return labels;
    }

    private TextView rowSummary(LinearLayout row) {
        if (row.getChildCount() == 0 || !(row.getChildAt(0) instanceof LinearLayout)) {
            return null;
        }
        LinearLayout labels = (LinearLayout) row.getChildAt(0);
        return labels.getChildCount() > 1 && labels.getChildAt(1) instanceof TextView
                ? (TextView) labels.getChildAt(1)
                : null;
    }

    private void setRowEnabled(View row, boolean enabled) {
        if (row == null) {
            return;
        }
        row.setEnabled(enabled);
        row.setClickable(enabled);
        row.setFocusable(enabled);
        row.setAlpha(enabled ? 1.0f : 0.44f);
    }

    private void setPreviewLinesEnabled(boolean enabled) {
        setRowEnabled(previewLinesRow, enabled);
        if (previewLinesSeekBar != null) {
            previewLinesSeekBar.setEnabled(enabled);
        }
    }

    private void tintSwitch(
            Switch toggle,
            MorpheSettingsV4Theme.Accent accent
    ) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked},
        };
        toggle.setThumbTintList(new ColorStateList(
                states,
                new int[]{accent.color, tokens.textSecondary}
        ));
        toggle.setTrackTintList(new ColorStateList(
                states,
                new int[]{
                        MorpheSettingsV4Theme.blend(
                                tokens.surfaceContainerHigh,
                                accent.container,
                                0.72f
                        ),
                        tokens.surfaceContainerHigh,
                }
        ));
    }

    private String viewTitle(String value) {
        for (int index = 0; index < VIEW_VALUES.length; index++) {
            if (VIEW_VALUES[index].equals(value)) {
                return VIEW_TITLES[index];
            }
        }
        return VIEW_TITLES[0];
    }

    private String linesSummary(int value) {
        return value == 1 ? "1 line" : value + " lines";
    }

    private void addSectionLabel(LinearLayout parent, String value) {
        TextView label = textView(value, 14, tokens.primary);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.02f);
        parent.addView(label);
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
                "This post-view control is unavailable in the current Boost build.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private interface CheckedChange {
        void onChanged(boolean checked);
    }
}
