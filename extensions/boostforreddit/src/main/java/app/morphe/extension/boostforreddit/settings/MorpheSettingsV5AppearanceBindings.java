package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical bindings for all 39 Appearance controls in the hidden V5 wave. */
final class MorpheSettingsV5AppearanceBindings {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_BINDINGS_ISSUE121_V1";
    static final String CANONICAL_STORAGE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_CANONICAL_STORAGE_ISSUE121_V1";
    static final String SPECIAL_CONTROL_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_SPECIAL_CONTROLS_ISSUE121_V1";
    static final String SIDE_EFFECT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_APPEARANCE_SIDE_EFFECTS_ISSUE121_V1";
    static final String FONT_PREVIEW_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_FONT_PREVIEW_ISSUE121_V1";
    static final String FONT_RESOLVER_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_FONT_RESOLVER_ISSUE121_V1";

    private static final String SAVED_VIEWS_PREFERENCES =
            "com.rubenmayayo.reddit.VIEW_PER_SUBSCRIPTION";
    private static final String MULTI_SUFFIX = ".multi";
    private static final String FRONT_PAGE_KEY =
            "_load_front_page_this_is_not_a_subreddit";
    private static final String SAVED_KEY =
            "_load_saved_this_is_not_a_subreddit";
    private static final String HISTORY_KEY =
            "_load_history_this_is_not_a_subreddit";
    private static final String ALIAS_PREFIX = "com.rubenmayayo.reddit.";

    private static final String[] VIEW_TITLES = new String[]{
            "Cards", "Cards 2.0", "Compact", "Small cards",
            "Dense", "Columns", "Images", "Swipe",
    };
    private static final String[] VIEW_VALUES = new String[]{
            "0", "7", "1", "4", "5", "2", "6", "3",
    };
    private static final int[] SAVED_VIEW_VALUES = new int[]{
            0, 7, 1, 4, 5, 2, 6, 3,
    };
    private static final String[] FONT_TITLES = new String[]{
            "Default", "Thin", "Light", "Regular", "Medium", "Black",
            "Condensed Light", "Condensed Regular", "Serif", "Monospace",
            "Serif Monospace", "Small Caps", "Roboto Slab",
    };
    private static final String[] FONT_VALUES = new String[]{
            "", "sans-serif-thin", "sans-serif-light", "sans-serif",
            "sans-serif-medium", "sans-serif-black",
            "sans-serif-condensed-light", "sans-serif-condensed", "serif",
            "monospace", "serif-monospace", "sans-serif-smallcaps",
            "RobotoSlab-Regular.ttf",
    };
    private static final String[] FONT_SIZE_TITLES = new String[]{
            "Extra small", "Small", "Medium", "Large", "Extra large",
            "Extra extra large",
    };
    private static final String[] FONT_SIZE_VALUES = new String[]{
            "XSmall", "Small", "Medium", "Large", "XLarge", "XXLarge",
    };
    private static final String[] ICON_TITLES = new String[]{
            "Default", "Grey", "Vivid", "Metal", "Yellow",
    };
    private static final String[] ICON_ALIASES = new String[]{
            "", "grey", "vivid", "metal", "yellow",
    };

    private MorpheSettingsV5AppearanceBindings() {
    }

    static void renderPage(
            MorpheSettingsV5AppearanceFragment host,
            LinearLayout content,
            MorpheSettingsV5Registry.V5PageSpec page,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        Session session = new Session(host, content, tokens);
        session.render(page);
    }

    static String titleFor(Context context, String key) {
        switch (key) {
            case "pref_toolbar_header_type":
                return "Community info alignment";
            case "pref_header_show_description":
                return "Show community description";
            case "pref_show_subreddit_header":
                return "Show community header";
            case "morphe_boost_prefer_high_refresh_rate":
                return "Prefer high refresh rate";
            case "pref_cards_gallery_carousel":
                return "Carousel for multiple images";
            case "pref_cards_full":
                return "Fill the screen width";
            case "pref_cards_full_preview":
                return "Full height images";
            case "pref_cards_preview_self_lines":
                return "Lines to preview";
            case "pref_cards_preview_self":
                return "Preview text from posts";
            case "pref_cards_rounded_corners":
                return "Rounded corners";
            case "pref_cards_subreddit_icon":
                return "Show community icon";
            case "pref_cards_links_as_thumbnails":
                return "Show thumbnails for link posts";
            case "pref_dense_buttons_visible":
            case "pref_mini_cards_buttons_visible":
                return "Buttons always visible";
            case "pref_mini_cards_full":
                return "Fill the screen width";
            case "pref_mini_cards_rounded_corners":
                return "Rounded corners";
            case "pref_mini_cards_truncate_title":
                return "Truncate title";
            case "pref_show_subreddit_prefix":
                return "Communities start with r/";
            case "pref_view":
                return "Default view";
            case "pref_view_per_sub":
                return "Manage saved views";
            case "pref_view_per_subscription":
                return "Remember per community";
            case "pref_left_handed":
                return "Thumbnails on left";
            case "action:saved_views:add":
                return "Add saved view";
            case "action:saved_views:clear_all":
                return "Clear all saved views";
            case "pref_split_screen":
                return "Tablet split screen mode";
            case "pref_colored_nav_bar":
                return "Colored navigation bar";
            case "pref_colored_status_bar":
                return "Colored status bar";
            case "pref_app_icon":
                return "App icon";
            case "pref_theme":
                return "Customize colors";
            case "pref_theme_night_start_minutes":
                return "Dark start time";
            case "pref_theme_night":
                return "Dark theme";
            case "pref_dynamic_colors":
                return "Dynamic color";
            case "pref_theme_night_end_minutes":
                return "Light start time";
            case "pref_theme_mode_type":
                return "Theme";
            case "pref_comments_font":
                return "Comments font";
            case "pref_font_size":
                return "Comments text size";
            case "pref_title_font":
                return "Title font";
            case "pref_font_size_title":
                return "Title text size";
            case "action:typography:restore_defaults":
                return "Restore font defaults";
            default:
                return key;
        }
    }

    static String searchSummaryFor(Context context, String key) {
        String summary = consequenceFor(key);
        return TextUtils.isEmpty(summary)
                ? MorpheSettingsV5Registry.titleFor(
                MorpheSettingsV5Registry.pageIdForKey(key)
        )
                : summary;
    }

    private static String consequenceFor(String key) {
        switch (key) {
            case "pref_show_subreddit_header":
                return "Show banner and icon in communities";
            case "morphe_boost_prefer_high_refresh_rate":
                return "Request a high refresh rate on supported displays";
            case "pref_cards_full":
            case "pref_mini_cards_full":
                return "Remove horizontal margins";
            case "pref_cards_full_preview":
                return "Disable for a fixed image-preview height";
            case "pref_cards_links_as_thumbnails":
                return "Disable to use large image previews";
            case "pref_mini_cards_truncate_title":
                return "Limit the title to two lines";
            case "pref_view_per_subscription":
                return "Remember the last selected view for each community";
            case "action:saved_views:add":
                return "Choose a community or custom feed and its view";
            case "action:saved_views:clear_all":
                return "Remove every per-community saved view";
            case "pref_split_screen":
                return "Show posts and comments together in landscape";
            case "pref_colored_nav_bar":
            case "pref_colored_status_bar":
                return "Use the toolbar color";
            case "pref_dynamic_colors":
                return "Use the color palette from Android";
            case "action:typography:restore_defaults":
                return "Reset post and comment fonts and sizes";
            default:
                return "";
        }
    }

    private enum Kind {
        TOGGLE,
        LIST,
        SEEK,
        TIME,
        THEME,
        FONT,
        APP_ICON,
        SAVED_MANAGER,
        SAVED_ADD,
        SAVED_CLEAR,
        RESET
    }

    private static final class Spec {
        final String key;
        final Kind kind;
        final String dependency;
        final boolean inverseDependency;

        Spec(String key, Kind kind) {
            this(key, kind, "", false);
        }

        Spec(
                String key,
                Kind kind,
                String dependency,
                boolean inverseDependency
        ) {
            this.key = key;
            this.kind = kind;
            this.dependency = dependency;
            this.inverseDependency = inverseDependency;
        }
    }

    private static Spec specFor(String key) {
        if ("pref_toolbar_header_type".equals(key)) {
            return new Spec(key, Kind.LIST, "pref_show_subreddit_header", false);
        }
        if ("pref_header_show_description".equals(key)) {
            return new Spec(key, Kind.TOGGLE, "pref_show_subreddit_header", false);
        }
        if ("pref_cards_preview_self_lines".equals(key)) {
            return new Spec(key, Kind.SEEK, "pref_cards_preview_self", false);
        }
        if ("pref_view_per_sub".equals(key)) {
            return new Spec(
                    key,
                    Kind.SAVED_MANAGER,
                    "pref_view_per_subscription",
                    false
            );
        }
        if ("action:saved_views:add".equals(key)) {
            return new Spec(
                    key,
                    Kind.SAVED_ADD,
                    "pref_view_per_subscription",
                    false
            );
        }
        if ("action:saved_views:clear_all".equals(key)) {
            return new Spec(key, Kind.SAVED_CLEAR, "saved_views_not_empty", false);
        }
        if ("pref_theme".equals(key)) {
            return new Spec(key, Kind.THEME, "pref_dynamic_colors", true);
        }
        if ("pref_theme_night".equals(key)) {
            return new Spec(key, Kind.THEME);
        }
        if ("pref_theme_night_start_minutes".equals(key)
                || "pref_theme_night_end_minutes".equals(key)) {
            return new Spec(key, Kind.TIME);
        }
        if ("pref_comments_font".equals(key)
                || "pref_title_font".equals(key)) {
            return new Spec(key, Kind.FONT);
        }
        if ("pref_app_icon".equals(key)) {
            return new Spec(key, Kind.APP_ICON);
        }
        if ("action:typography:restore_defaults".equals(key)) {
            return new Spec(key, Kind.RESET);
        }
        if ("pref_theme_mode_type".equals(key)
                || "pref_font_size".equals(key)
                || "pref_font_size_title".equals(key)
                || "pref_view".equals(key)) {
            return new Spec(key, Kind.LIST);
        }
        return new Spec(key, Kind.TOGGLE);
    }

    private static final class Session {
        final MorpheSettingsV5AppearanceFragment host;
        final Context context;
        final LinearLayout content;
        final MorpheSettingsV4Theme.Tokens tokens;
        final SharedPreferences preferences;
        final SharedPreferences savedViews;
        final Map<String, Binding> bindings = new LinkedHashMap<>();
        LinearLayout savedEntries;

        Session(
                MorpheSettingsV5AppearanceFragment host,
                LinearLayout content,
                MorpheSettingsV4Theme.Tokens tokens
        ) {
            this.host = host;
            this.context = host.requireContext();
            this.content = content;
            this.tokens = tokens;
            this.preferences = PreferenceManager.getDefaultSharedPreferences(
                    context
            );
            this.savedViews = context.getSharedPreferences(
                    SAVED_VIEWS_PREFERENCES,
                    Context.MODE_PRIVATE
            );
        }

        void render(MorpheSettingsV5Registry.V5PageSpec page) {
            LinearLayout list = MorpheSettingsV14Ui.standardList(context);
            content.addView(
                    list,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            for (String key : page.keys) {
                addControl(list, specFor(key));
            }
            updateRows();

            if ("v5/appearance/post_layout/layout_and_saved_views/"
                    .concat("saved_community_views").equals(page.pageId)) {
                addSpace(content, 22);
                content.addView(MorpheSettingsV14Ui.sectionLabel(
                        context,
                        tokens,
                        "Saved views"
                ));
                addSpace(content, 6);
                savedEntries = MorpheSettingsV14Ui.standardList(context);
                content.addView(
                        savedEntries,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );
                renderSavedEntries();
            }
        }

        private void addControl(LinearLayout list, Spec spec) {
            switch (spec.kind) {
                case TOGGLE:
                    addToggle(list, spec);
                    break;
                case LIST:
                    addList(list, spec);
                    break;
                case SEEK:
                    addSeek(list, spec);
                    break;
                case TIME:
                    addTime(list, spec);
                    break;
                case THEME:
                    addTheme(list, spec);
                    break;
                case FONT:
                    addFont(list, spec);
                    break;
                case APP_ICON:
                    addAppIcon(list, spec);
                    break;
                case SAVED_MANAGER:
                case SAVED_ADD:
                case SAVED_CLEAR:
                case RESET:
                    addAction(list, spec);
                    break;
                default:
                    throw new IllegalStateException("Unhandled " + spec.key);
            }
        }

        private void addToggle(LinearLayout list, Spec spec) {
            boolean checked = preferences.getBoolean(
                    spec.key,
                    booleanDefault(spec.key)
            );
            String supporting = consequenceFor(spec.key);
            LinearLayout row = standardRow(!TextUtils.isEmpty(supporting));
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    supporting
            );
            MorpheSettingsV14Ui.Toggle toggle =
                    new MorpheSettingsV14Ui.Toggle(context, tokens, checked);
            toggle.setOnCheckedChangeListener((button, value) -> {
                preferences.edit().putBoolean(spec.key, value).apply();
                applySideEffects(spec.key);
                updateRows();
            });
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    toggle.toggle();
                }
            });
            row.addView(toggle, wrapParams());
            addRow(list, row);
            bindings.put(
                    spec.key,
                    new Binding(spec, row, summary, toggle)
            );
        }

        private void addList(LinearLayout list, Spec spec) {
            String summaryValue = selectedTitle(spec.key);
            LinearLayout row = standardRow(true);
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    summaryValue
            );
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    showListDialog(spec.key);
                }
            });
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private void addSeek(LinearLayout list, Spec spec) {
            LinearLayout row = standardRow(true);
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    linesSummary(preferences.getInt(spec.key, 5))
            );
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    showSeekDialog(spec.key);
                }
            });
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private void addTime(LinearLayout list, Spec spec) {
            LinearLayout row = standardRow(true);
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    timeSummary(preferences.getInt(
                            spec.key,
                            intDefault(spec.key)
                    ))
            );
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> showTimeDialog(spec.key));
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private void addTheme(LinearLayout list, Spec spec) {
            LinearLayout row = standardRow(true);
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    themeSummary(spec.key)
            );
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    showThemeDialog(spec.key);
                }
            });
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private void addFont(LinearLayout list, Spec spec) {
            LinearLayout row = standardRow(true);
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    selectedFontTitle(spec.key)
            );
            if (summary != null) {
                summary.setTypeface(resolveFontTypeface(
                        preferences.getString(spec.key, "")
                ));
            }
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> showFontDialog(spec.key));
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private void addAppIcon(LinearLayout list, Spec spec) {
            LinearLayout row = standardRow(true);
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    selectedIconTitle()
            );
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> showAppIconDialog());
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private void addAction(LinearLayout list, Spec spec) {
            String summaryValue = actionSummary(spec.key);
            LinearLayout row = standardRow(!TextUtils.isEmpty(summaryValue));
            TextView summary = addLabels(
                    row,
                    titleFor(context, spec.key),
                    summaryValue
            );
            row.addView(MorpheSettingsV14Ui.chevron(context, tokens));
            row.setOnClickListener(view -> {
                if (!row.isEnabled()) {
                    return;
                }
                if (spec.kind == Kind.SAVED_MANAGER) {
                    host.openPage(
                            "v5/appearance/post_layout/layout_and_saved_views/"
                                    + "saved_community_views"
                    );
                } else if (spec.kind == Kind.SAVED_ADD) {
                    showAddSavedViewDialog();
                } else if (spec.kind == Kind.SAVED_CLEAR) {
                    confirmClearSavedViews();
                } else if (spec.kind == Kind.RESET) {
                    confirmResetTypography();
                }
            });
            addRow(list, row);
            bindings.put(spec.key, new Binding(spec, row, summary, null));
        }

        private LinearLayout standardRow(boolean hasSupportingText) {
            return MorpheSettingsV14Ui.standardListRow(
                    context,
                    tokens,
                    hasSupportingText
            );
        }

        private TextView addLabels(
                LinearLayout row,
                String title,
                String summary
        ) {
            LinearLayout labels = MorpheSettingsV14Ui.standardListLabels(
                    context,
                    tokens,
                    title,
                    summary
            );
            row.addView(
                    labels,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );
            return labels.getChildCount() > 1
                    ? (TextView) labels.getChildAt(1)
                    : null;
        }

        private void addRow(LinearLayout list, View row) {
            MorpheSettingsV14Ui.addStandardListRow(list, row, tokens);
        }

        private void updateRows() {
            for (Binding binding : bindings.values()) {
                boolean enabled = dependencySatisfied(binding.spec);
                setEnabledRecursive(binding.row, enabled);
                binding.row.setAlpha(enabled ? 1.0f : 0.48f);
                if (binding.toggle != null) {
                    binding.toggle.setCheckedSilently(preferences.getBoolean(
                            binding.spec.key,
                            booleanDefault(binding.spec.key)
                    ));
                }
                if (binding.summary != null) {
                    String summary = currentSummary(binding.spec);
                    binding.summary.setText(summary);
                    binding.summary.setVisibility(
                            TextUtils.isEmpty(summary) ? View.GONE : View.VISIBLE
                    );
                    if (binding.spec.kind == Kind.FONT) {
                        binding.summary.setTypeface(resolveFontTypeface(
                                preferences.getString(binding.spec.key, "")
                        ));
                    }
                }
            }
            if (savedEntries != null) {
                renderSavedEntries();
            }
        }

        private boolean dependencySatisfied(Spec spec) {
            if (TextUtils.isEmpty(spec.dependency)) {
                return true;
            }
            if ("saved_views_not_empty".equals(spec.dependency)) {
                return !savedViews.getAll().isEmpty();
            }
            boolean state = preferences.getBoolean(spec.dependency, false);
            return spec.inverseDependency ? !state : state;
        }

        private String currentSummary(Spec spec) {
            switch (spec.kind) {
                case LIST:
                    return selectedTitle(spec.key);
                case SEEK:
                    return linesSummary(preferences.getInt(spec.key, 5));
                case TIME:
                    return timeSummary(preferences.getInt(
                            spec.key,
                            intDefault(spec.key)
                    ));
                case THEME:
                    return themeSummary(spec.key);
                case FONT:
                    return selectedFontTitle(spec.key);
                case APP_ICON:
                    return selectedIconTitle();
                case SAVED_MANAGER:
                    return savedViewsSummary();
                case SAVED_ADD:
                    return consequenceFor(spec.key);
                case SAVED_CLEAR:
                    return savedViewsSummary();
                case RESET:
                    return consequenceFor(spec.key);
                case TOGGLE:
                default:
                    return consequenceFor(spec.key);
            }
        }

        private String actionSummary(String key) {
            if ("pref_view_per_sub".equals(key)
                    || "action:saved_views:clear_all".equals(key)) {
                return savedViewsSummary();
            }
            return consequenceFor(key);
        }

        private String savedViewsSummary() {
            int count = savedViews.getAll().size();
            return count + (count == 1 ? " saved view" : " saved views");
        }

        private void showListDialog(String key) {
            String[] entries;
            String[] values;
            if ("pref_toolbar_header_type".equals(key)) {
                entries = stringArray(
                        "pref_toolbar_header_type_titles",
                        new String[]{"Centered", "Left aligned"}
                );
                values = stringArray(
                        "pref_toolbar_header_type_values",
                        new String[]{"center", "left"}
                );
            } else if ("pref_view".equals(key)) {
                entries = stringArray("pref_view_titles", VIEW_TITLES);
                values = stringArray("pref_view_values", VIEW_VALUES);
            } else if ("pref_theme_mode_type".equals(key)) {
                entries = stringArray(
                        "dark_theme_type_titles",
                        new String[]{"Off", "On", "Follow system", "Scheduled"}
                );
                values = stringArray(
                        "dark_theme_type_values",
                        new String[]{"off", "on", "system", "scheduled"}
                );
            } else if ("pref_font_size".equals(key)
                    || "pref_font_size_title".equals(key)) {
                entries = stringArray("pref_font_size_titles", FONT_SIZE_TITLES);
                values = stringArray("pref_font_size_values", FONT_SIZE_VALUES);
            } else {
                return;
            }
            String selected = preferences.getString(key, stringDefault(key));
            showChoiceDialog(
                    titleFor(context, key),
                    entries,
                    values,
                    selected,
                    value -> {
                        preferences.edit().putString(key, value).apply();
                        applySideEffects(key);
                        updateRows();
                    }
            );
        }

        private void showThemeDialog(String key) {
            String valuesResource = "pref_theme_night".equals(key)
                    ? "pref_theme_values_night"
                    : "pref_theme_values";
            String[] values = stringArray(valuesResource, new String[]{
                    "0", "5", "3", "6", "1", "2",
            });
            String[] entries = new String[values.length];
            for (int index = 0; index < values.length; index++) {
                entries[index] = themeTitle(values[index]);
            }
            String selected = preferences.getString(key, stringDefault(key));
            showChoiceDialog(
                    titleFor(context, key),
                    entries,
                    values,
                    selected,
                    value -> {
                        preferences.edit().putString(key, value).apply();
                        applySideEffects(key);
                        updateRows();
                    }
            );
        }

        private void showFontDialog(String key) {
            String[] entries = stringArray("font_options", FONT_TITLES);
            String[] values = stringArray("font_values", FONT_VALUES);
            String selected = preferences.getString(key, "");
            showFontChoiceDialog(
                    titleFor(context, key),
                    entries,
                    values,
                    selected,
                    value -> {
                        preferences.edit().putString(key, value).apply();
                        applySideEffects(key);
                        updateRows();
                    }
            );
        }

        private void showAppIconDialog() {
            String selected = selectedIconAlias();
            showChoiceDialog(
                    "App icon",
                    ICON_TITLES,
                    ICON_ALIASES,
                    selected == null ? "" : selected,
                    value -> {
                        applyIconSelection(TextUtils.isEmpty(value) ? null : value);
                        updateRows();
                        Toast.makeText(
                                context,
                                "App icon updated",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        }

        private void showSeekDialog(String key) {
            Dialog dialog = new Dialog(context);
            LinearLayout body = dialogBody();
            addDialogHeading(body, titleFor(context, key));

            int current = preferences.getInt(key, 5);
            TextView value = textView(linesSummary(current), 15, tokens.textSecondary);
            value.setGravity(Gravity.CENTER_HORIZONTAL);
            body.addView(value, matchWrapParams());

            SeekBar seekBar = new SeekBar(context);
            seekBar.setMax(100);
            seekBar.setProgress(Math.max(0, Math.min(100, current)));
            if (Build.VERSION.SDK_INT >= 21) {
                seekBar.setProgressTintList(ColorStateList.valueOf(
                        tokens.navigationAccent().color
                ));
                seekBar.setThumbTintList(ColorStateList.valueOf(
                        tokens.navigationAccent().color
                ));
            }
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                        SeekBar bar,
                        int progress,
                        boolean fromUser
                ) {
                    value.setText(linesSummary(progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                }
            });
            LinearLayout.LayoutParams seekParams = matchWrapParams();
            seekParams.topMargin = dp(10);
            body.addView(seekBar, seekParams);
            addDialogActions(
                    body,
                    dialog,
                    "Save",
                    () -> {
                        int selected = seekBar.getProgress();
                        preferences.edit().putInt(key, selected).apply();
                        applySideEffects(key);
                        dialog.dismiss();
                        updateRows();
                    }
            );
            showDialog(dialog, body);
        }

        private void showTimeDialog(String key) {
            int minutes = preferences.getInt(key, intDefault(key));
            int hour = Math.max(0, Math.min(23, minutes / 60));
            int minute = Math.max(0, Math.min(59, minutes % 60));
            TimePickerDialog dialog = new TimePickerDialog(
                    context,
                    (view, selectedHour, selectedMinute) -> {
                        preferences.edit()
                                .putInt(key, selectedHour * 60 + selectedMinute)
                                .apply();
                        applySideEffects(key);
                        updateRows();
                    },
                    hour,
                    minute,
                    DateFormat.is24HourFormat(context)
            );
            dialog.setTitle(titleFor(context, key));
            dialog.show();
        }

        private void showChoiceDialog(
                String title,
                String[] entries,
                String[] values,
                String selected,
                ValueConsumer consumer
        ) {
            if (entries.length == 0 || entries.length != values.length) {
                Toast.makeText(context, "This option is unavailable", Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            Dialog dialog = new Dialog(context);
            LinearLayout body = dialogBody();
            addDialogHeading(body, title);
            LinearLayout group = MorpheSettingsV14Ui.group(context);
            body.addView(group, matchWrapParams());
            for (int index = 0; index < entries.length; index++) {
                final String value = values[index];
                MorpheSettingsV14Ui.ChoiceRow row =
                        MorpheSettingsV14Ui.choiceRow(
                                context,
                                tokens,
                                entries[index],
                                "",
                                TextUtils.equals(selected, value)
                        );
                row.setOnClickListener(view -> {
                    consumer.accept(value);
                    dialog.dismiss();
                });
                MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
            }
            addDialogActions(body, dialog, null, null);
            showDialog(dialog, body);
        }

        private void showFontChoiceDialog(
                String title,
                String[] entries,
                String[] values,
                String selected,
                ValueConsumer consumer
        ) {
            if (entries.length == 0 || entries.length != values.length) {
                Toast.makeText(context, "This option is unavailable", Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            Dialog dialog = new Dialog(context);
            LinearLayout body = dialogBody();
            addDialogHeading(body, title);
            LinearLayout group = MorpheSettingsV14Ui.group(context);
            body.addView(group, matchWrapParams());
            for (int index = 0; index < entries.length; index++) {
                final String value = values[index];
                MorpheSettingsV14Ui.ChoiceRow row =
                        MorpheSettingsV14Ui.choiceRow(
                                context,
                                tokens,
                                entries[index],
                                "",
                                TextUtils.equals(selected, value)
                        );
                row.setTitleTypeface(resolveFontTypeface(value));
                row.setTitleSize(18);
                row.setOnClickListener(view -> {
                    consumer.accept(value);
                    dialog.dismiss();
                });
                MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
            }
            addDialogActions(body, dialog, null, null);
            showDialog(dialog, body);
        }

        private Typeface resolveFontTypeface(String value) {
            try {
                Class<?> settingsClass = Class.forName("id.b");
                Method instanceMethod = settingsClass.getDeclaredMethod("v0");
                instanceMethod.setAccessible(true);
                Object settings = instanceMethod.invoke(null);
                Method resolver = settingsClass.getDeclaredMethod(
                        "p4",
                        Context.class,
                        String.class
                );
                resolver.setAccessible(true);
                Object result = resolver.invoke(settings, context, value);
                if (result instanceof Typeface) {
                    return (Typeface) result;
                }
            } catch (Throwable ignored) {
            }

            if (TextUtils.isEmpty(value)) {
                return Typeface.DEFAULT;
            }
            if (value.endsWith(".ttf")) {
                String[] assetPaths = new String[]{value, "fonts/" + value};
                for (String assetPath : assetPaths) {
                    try {
                        return Typeface.createFromAsset(
                                context.getAssets(),
                                assetPath
                        );
                    } catch (Throwable ignored) {
                    }
                }
            }
            return Typeface.create(value, Typeface.NORMAL);
        }

        private void showAddSavedViewDialog() {
            Dialog dialog = new Dialog(context);
            LinearLayout body = dialogBody();
            addDialogHeading(body, "Add saved view");

            MorpheSettingsV14Ui.Field field = MorpheSettingsV14Ui.outlinedField(
                    context,
                    tokens,
                    "Community or custom feed",
                    ""
            );
            field.input.setInputType(InputType.TYPE_CLASS_TEXT);
            body.addView(field.root, matchWrapParams());

            CheckBox customFeed = new CheckBox(context);
            customFeed.setText("Custom feed");
            customFeed.setTextColor(tokens.textPrimary);
            customFeed.setPadding(dp(4), dp(8), dp(4), dp(8));
            body.addView(customFeed, matchWrapParams());

            addDialogActions(
                    body,
                    dialog,
                    "Continue",
                    () -> {
                        String key = normalizeSavedViewKey(
                                field.input.getText().toString(),
                                customFeed.isChecked()
                        );
                        if (TextUtils.isEmpty(key)) {
                            Toast.makeText(
                                    context,
                                    "Enter a community or custom feed",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }
                        dialog.dismiss();
                        showSavedViewTypeDialog(key);
                    }
            );
            showDialog(dialog, body);
        }

        private void showSavedViewTypeDialog(String key) {
            int selected = parseViewType(savedViews.getAll().get(key));
            String[] values = new String[SAVED_VIEW_VALUES.length];
            for (int index = 0; index < SAVED_VIEW_VALUES.length; index++) {
                values[index] = String.valueOf(SAVED_VIEW_VALUES[index]);
            }
            showChoiceDialog(
                    displayName(key),
                    VIEW_TITLES,
                    values,
                    String.valueOf(selected),
                    value -> {
                        int selectedView = Integer.parseInt(value);
                        savedViews.edit().putInt(key, selectedView).apply();
                        updateRows();
                    }
            );
        }

        private void confirmClearSavedViews() {
            showConfirmation(
                    "Clear all saved views?",
                    "Every community will use the default view again.",
                    "Clear",
                    () -> {
                        savedViews.edit().clear().apply();
                        updateRows();
                    }
            );
        }

        private void confirmResetTypography() {
            showConfirmation(
                    "Restore font defaults?",
                    "Post titles and comments will use Boost's default fonts and sizes.",
                    "Restore",
                    () -> {
                        preferences.edit()
                                .remove("pref_title_font")
                                .remove("pref_font_size_title")
                                .remove("pref_comments_font")
                                .remove("pref_font_size")
                                .apply();
                        setBoostStaticBoolean("d");
                        setBoostStaticBoolean("h");
                        updateRows();
                    }
            );
        }

        private void showConfirmation(
                String title,
                String message,
                String confirmLabel,
                Runnable confirmation
        ) {
            Dialog dialog = new Dialog(context);
            LinearLayout body = dialogBody();
            addDialogHeading(body, title);
            TextView text = textView(message, 15, tokens.textSecondary);
            text.setLineSpacing(0, 1.08f);
            LinearLayout.LayoutParams textParams = matchWrapParams();
            textParams.topMargin = dp(6);
            body.addView(text, textParams);
            addDialogActions(
                    body,
                    dialog,
                    confirmLabel,
                    () -> {
                        confirmation.run();
                        dialog.dismiss();
                    }
            );
            showDialog(dialog, body);
        }

        private void renderSavedEntries() {
            if (savedEntries == null) {
                return;
            }
            savedEntries.removeAllViews();
            List<SavedEntry> entries = new ArrayList<>();
            for (Map.Entry<String, ?> entry : savedViews.getAll().entrySet()) {
                entries.add(new SavedEntry(
                        entry.getKey(),
                        parseViewType(entry.getValue())
                ));
            }
            Collections.sort(entries, Comparator.comparing(
                    left -> left.key.toLowerCase(Locale.ROOT)
            ));
            if (entries.isEmpty()) {
                TextView empty = MorpheSettingsV14Ui.supportingText(
                        context,
                        tokens,
                        "No saved views yet"
                );
                savedEntries.addView(empty);
                return;
            }

            for (SavedEntry entry : entries) {
                LinearLayout row = standardRow(true);
                addLabels(
                        row,
                        displayName(entry.key),
                        savedViewTitle(entry.viewType)
                );
                TextView remove = textView("Remove", 14, tokens.primary);
                remove.setGravity(Gravity.CENTER);
                remove.setMinimumWidth(dp(72));
                remove.setMinimumHeight(dp(48));
                remove.setClickable(true);
                remove.setFocusable(true);
                remove.setOnClickListener(view -> showConfirmation(
                        "Remove saved view?",
                        displayName(entry.key)
                                + " will use the default view again.",
                        "Remove",
                        () -> {
                            savedViews.edit().remove(entry.key).apply();
                            updateRows();
                        }
                ));
                row.addView(remove, wrapParams());
                row.setOnClickListener(view -> showSavedViewTypeDialog(entry.key));
                addRow(savedEntries, row);
            }
        }

        private String[] stringArray(String resourceName, String[] fallback) {
            int resourceId = MorpheSettingsV4Catalog.resourceId(
                    context,
                    "array",
                    resourceName
            );
            if (resourceId == 0) {
                return fallback.clone();
            }
            try {
                return context.getResources().getStringArray(resourceId);
            } catch (Throwable ignored) {
                return fallback.clone();
            }
        }

        private String selectedTitle(String key) {
            String selected = preferences.getString(key, stringDefault(key));
            if ("pref_toolbar_header_type".equals(key)) {
                return valueTitle(
                        selected,
                        stringArray(
                                "pref_toolbar_header_type_titles",
                                new String[]{"Centered", "Left aligned"}
                        ),
                        stringArray(
                                "pref_toolbar_header_type_values",
                                new String[]{"center", "left"}
                        )
                );
            }
            if ("pref_view".equals(key)) {
                return valueTitle(
                        selected,
                        stringArray("pref_view_titles", VIEW_TITLES),
                        stringArray("pref_view_values", VIEW_VALUES)
                );
            }
            if ("pref_theme_mode_type".equals(key)) {
                return valueTitle(
                        selected,
                        stringArray(
                                "dark_theme_type_titles",
                                new String[]{"Off", "On", "Follow system", "Scheduled"}
                        ),
                        stringArray(
                                "dark_theme_type_values",
                                new String[]{"off", "on", "system", "scheduled"}
                        )
                );
            }
            if ("pref_font_size".equals(key)
                    || "pref_font_size_title".equals(key)) {
                return valueTitle(
                        selected,
                        stringArray("pref_font_size_titles", FONT_SIZE_TITLES),
                        stringArray("pref_font_size_values", FONT_SIZE_VALUES)
                );
            }
            return selected;
        }

        private String themeSummary(String key) {
            return themeTitle(preferences.getString(key, stringDefault(key)));
        }

        private String themeTitle(String value) {
            try {
                int theme = Integer.parseInt(value);
                Method method = Class.forName("he.f0").getDeclaredMethod(
                        "z",
                        int.class,
                        Context.class
                );
                method.setAccessible(true);
                Object result = method.invoke(null, theme, context);
                if (result instanceof String
                        && !TextUtils.isEmpty((String) result)) {
                    return (String) result;
                }
            } catch (Throwable ignored) {
            }
            return "Theme " + value;
        }

        private String selectedFontTitle(String key) {
            return valueTitle(
                    preferences.getString(key, ""),
                    stringArray("font_options", FONT_TITLES),
                    stringArray("font_values", FONT_VALUES)
            );
        }

        private String selectedIconTitle() {
            String selected = selectedIconAlias();
            return valueTitle(
                    selected == null ? "" : selected,
                    ICON_TITLES,
                    ICON_ALIASES
            );
        }

        private String selectedIconAlias() {
            PackageManager packageManager = context.getPackageManager();
            for (int index = 1; index < ICON_ALIASES.length; index++) {
                String alias = ICON_ALIASES[index];
                int state = packageManager.getComponentEnabledSetting(
                        aliasComponent(alias)
                );
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return alias;
                }
            }
            return null;
        }

        private void applyIconSelection(String selectedAlias) {
            PackageManager packageManager = context.getPackageManager();
            for (int index = 1; index < ICON_ALIASES.length; index++) {
                String alias = ICON_ALIASES[index];
                int state = TextUtils.equals(alias, selectedAlias)
                        ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                packageManager.setComponentEnabledSetting(
                        aliasComponent(alias),
                        state,
                        PackageManager.DONT_KILL_APP
                );
            }
        }

        private ComponentName aliasComponent(String alias) {
            return new ComponentName(
                    context.getPackageName(),
                    ALIAS_PREFIX + alias
            );
        }

        private String timeSummary(int minutes) {
            int hour = Math.max(0, Math.min(23, minutes / 60));
            int minute = Math.max(0, Math.min(59, minutes % 60));
            if (DateFormat.is24HourFormat(context)) {
                return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            }
            String suffix = hour >= 12 ? "PM" : "AM";
            int displayHour = hour % 12;
            if (displayHour == 0) {
                displayHour = 12;
            }
            return String.format(
                    Locale.getDefault(),
                    "%d:%02d %s",
                    displayHour,
                    minute,
                    suffix
            );
        }

        private String linesSummary(int value) {
            return value + (value == 1 ? " line" : " lines");
        }

        private String valueTitle(
                String value,
                String[] titles,
                String[] values
        ) {
            int count = Math.min(titles.length, values.length);
            for (int index = 0; index < count; index++) {
                if (TextUtils.equals(value, values[index])) {
                    return titles[index];
                }
            }
            return TextUtils.isEmpty(value) ? "Default" : value;
        }

        private String stringDefault(String key) {
            if ("pref_toolbar_header_type".equals(key)) {
                return "center";
            }
            if ("pref_view".equals(key)) {
                return "0";
            }
            if ("pref_theme".equals(key)) {
                return "0";
            }
            if ("pref_theme_night".equals(key)) {
                return "8";
            }
            if ("pref_theme_mode_type".equals(key)) {
                return "system";
            }
            if ("pref_font_size".equals(key)
                    || "pref_font_size_title".equals(key)) {
                return "Medium";
            }
            return "";
        }

        private int intDefault(String key) {
            if ("pref_theme_night_start_minutes".equals(key)) {
                return 1140;
            }
            if ("pref_theme_night_end_minutes".equals(key)) {
                return 420;
            }
            if ("pref_cards_preview_self_lines".equals(key)) {
                return 5;
            }
            return 0;
        }

        private boolean booleanDefault(String key) {
            switch (key) {
                case "pref_header_show_description":
                case "morphe_boost_prefer_high_refresh_rate":
                case "pref_cards_gallery_carousel":
                case "pref_cards_full":
                case "pref_cards_preview_self":
                case "pref_cards_subreddit_icon":
                case "pref_cards_links_as_thumbnails":
                case "pref_mini_cards_rounded_corners":
                case "pref_mini_cards_truncate_title":
                case "pref_split_screen":
                case "pref_colored_status_bar":
                    return true;
                default:
                    return false;
            }
        }

        private void applySideEffects(String key) {
            if (key.startsWith("pref_cards_")
                    || key.startsWith("pref_mini_cards_")) {
                setBoostStaticBoolean("g");
            }
            if ("pref_dense_buttons_visible".equals(key)
                    || "pref_left_handed".equals(key)) {
                setBoostStaticBoolean("h");
            }
            if ("pref_show_subreddit_header".equals(key)
                    || "pref_header_show_description".equals(key)
                    || "pref_toolbar_header_type".equals(key)) {
                setBoostStaticBoolean("e");
            }
            if ("pref_show_subreddit_prefix".equals(key)) {
                setBoostStaticString(
                        "c",
                        preferences.getBoolean(key, false) ? "r/" : ""
                );
                setBoostStaticBoolean("i");
            }
            if ("pref_colored_nav_bar".equals(key)
                    || "pref_colored_status_bar".equals(key)) {
                setBoostStaticBoolean("h");
                recreateHost();
            }
            if ("pref_dynamic_colors".equals(key)) {
                setBoostStaticBoolean("i");
                recreateHost();
            }
            if ("pref_theme".equals(key)
                    || "pref_theme_night".equals(key)
                    || "pref_theme_mode_type".equals(key)
                    || "pref_theme_night_start_minutes".equals(key)
                    || "pref_theme_night_end_minutes".equals(key)) {
                invokeStaticNoArgs("kb.a", "d");
                setBoostStaticBoolean("i");
                recreateHost();
            }
            if ("pref_font_size".equals(key)
                    || "pref_font_size_title".equals(key)) {
                setBoostStaticBoolean("d");
            }
            if ("pref_title_font".equals(key)) {
                setBoostStaticBoolean("h");
            }
            if ("morphe_boost_prefer_high_refresh_rate".equals(key)) {
                recreateHost();
            }
        }

        private void recreateHost() {
            Activity activity = context instanceof Activity
                    ? (Activity) context
                    : null;
            if (activity != null) {
                activity.recreate();
            }
        }

        private void setEnabledRecursive(View view, boolean enabled) {
            view.setEnabled(enabled);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int index = 0; index < group.getChildCount(); index++) {
                    setEnabledRecursive(group.getChildAt(index), enabled);
                }
            }
        }

        private LinearLayout dialogBody() {
            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(22), dp(20), dp(22), dp(16));
            body.setBackground(MorpheSettingsV4Theme.rounded(
                    context,
                    tokens.surfaceContainer,
                    28
            ));
            return body;
        }

        private void addDialogHeading(LinearLayout body, String value) {
            TextView title = textView(value, 20, tokens.textPrimary);
            title.setTypeface(Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
            ));
            LinearLayout.LayoutParams params = matchWrapParams();
            params.bottomMargin = dp(12);
            body.addView(title, params);
        }

        private void addDialogActions(
                LinearLayout body,
                Dialog dialog,
                String positiveLabel,
                Runnable positive
        ) {
            LinearLayout actions = new LinearLayout(context);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            TextView cancel = MorpheSettingsV14Ui.action(
                    context,
                    tokens,
                    "Cancel",
                    false
            );
            cancel.setOnClickListener(view -> dialog.dismiss());
            actions.addView(cancel, wrapParams());
            if (!TextUtils.isEmpty(positiveLabel) && positive != null) {
                TextView confirm = MorpheSettingsV14Ui.action(
                        context,
                        tokens,
                        positiveLabel,
                        true
                );
                LinearLayout.LayoutParams confirmParams = wrapParams();
                confirmParams.setMarginStart(dp(8));
                actions.addView(confirm, confirmParams);
                confirm.setOnClickListener(view -> positive.run());
            }
            LinearLayout.LayoutParams actionsParams = matchWrapParams();
            actionsParams.topMargin = dp(14);
            body.addView(actions, actionsParams);
        }

        private void showDialog(Dialog dialog, LinearLayout body) {
            dialog.setContentView(body);
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

        private TextView textView(String value, int sizeSp, int color) {
            TextView view = new TextView(context);
            view.setText(value);
            view.setTextSize(sizeSp);
            view.setTextColor(color);
            return view;
        }

        private void addSpace(LinearLayout parent, int heightDp) {
            parent.addView(
                    new View(context),
                    new LinearLayout.LayoutParams(1, dp(heightDp))
            );
        }

        private int dp(float value) {
            return MorpheSettingsV4Theme.dp(context, value);
        }

        private LinearLayout.LayoutParams wrapParams() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        private LinearLayout.LayoutParams matchWrapParams() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        private String normalizeSavedViewKey(
                String rawValue,
                boolean customFeed
        ) {
            String value = rawValue == null ? "" : rawValue.trim();
            while (value.startsWith("/")) {
                value = value.substring(1);
            }
            if (value.regionMatches(true, 0, "r/", 0, 2)) {
                value = value.substring(2);
            }
            if (value.regionMatches(true, 0, "u/", 0, 2)) {
                int multi = value.toLowerCase(Locale.ROOT).indexOf("/m/");
                value = multi >= 0
                        ? value.substring(multi + 3)
                        : value.substring(2);
            }
            value = value.trim();
            if (TextUtils.isEmpty(value)) {
                return null;
            }
            if (customFeed) {
                return value.endsWith(MULTI_SUFFIX)
                        ? value
                        : value + MULTI_SUFFIX;
            }
            if ("home".equalsIgnoreCase(value)
                    || "front page".equalsIgnoreCase(value)
                    || "frontpage".equalsIgnoreCase(value)) {
                return FRONT_PAGE_KEY;
            }
            if ("saved".equalsIgnoreCase(value)) {
                return SAVED_KEY;
            }
            if ("history".equalsIgnoreCase(value)) {
                return HISTORY_KEY;
            }
            return value;
        }

        private int parseViewType(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private String displayName(String key) {
            if (FRONT_PAGE_KEY.equals(key)) {
                return "Front page";
            }
            if (SAVED_KEY.equals(key)) {
                return "Saved";
            }
            if (HISTORY_KEY.equals(key)) {
                return "History";
            }
            if ("all".equalsIgnoreCase(key)
                    || "popular".equalsIgnoreCase(key)) {
                return "r/" + key;
            }
            if (key.endsWith(MULTI_SUFFIX)) {
                return "Custom feed · "
                        + key.substring(0, key.length() - MULTI_SUFFIX.length());
            }
            return key.startsWith("r/") ? key : "r/" + key;
        }

        private String savedViewTitle(int viewType) {
            try {
                Method method = Class.forName("he.h0").getDeclaredMethod(
                        "a1",
                        Context.class,
                        int.class
                );
                method.setAccessible(true);
                Object result = method.invoke(null, context, viewType);
                if (result instanceof String
                        && !TextUtils.isEmpty((String) result)) {
                    return (String) result;
                }
            } catch (Throwable ignored) {
            }
            for (int index = 0; index < SAVED_VIEW_VALUES.length; index++) {
                if (SAVED_VIEW_VALUES[index] == viewType) {
                    return VIEW_TITLES[index];
                }
            }
            return "View " + viewType;
        }
    }

    private static void setBoostStaticBoolean(String fieldName) {
        try {
            Field field = Class.forName("id.b").getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }
    }

    private static void setBoostStaticString(
            String fieldName,
            String value
    ) {
        try {
            Field field = Class.forName("id.b").getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Throwable ignored) {
        }
    }

    private static void invokeStaticNoArgs(String className, String methodName) {
        try {
            Method method = Class.forName(className).getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private interface ValueConsumer {
        void accept(String value);
    }

    private static final class Binding {
        final Spec spec;
        final View row;
        final TextView summary;
        final MorpheSettingsV14Ui.Toggle toggle;

        Binding(
                Spec spec,
                View row,
                TextView summary,
                MorpheSettingsV14Ui.Toggle toggle
        ) {
            this.spec = spec;
            this.row = row;
            this.summary = summary;
            this.toggle = toggle;
        }
    }

    private static final class SavedEntry {
        final String key;
        final int viewType;

        SavedEntry(String key, int viewType) {
            this.key = key;
            this.viewType = viewType;
        }
    }
}
