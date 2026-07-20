package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
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
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Field;

/** Material 3 editor for Boost's canonical post and comment font preferences. */
public final class MorpheSettingsV4FontsFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_FONTS_ISSUE106_V1";
    public static final String PREVIEW_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_FONT_PREVIEWS_ISSUE106_V2";
    public static final String SPECIMEN_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_FONT_SPECIMEN_ISSUE106_V3";

    private static final String KEY_TITLE_FONT = "pref_title_font";
    private static final String KEY_TITLE_SIZE = "pref_font_size_title";
    private static final String KEY_COMMENTS_FONT = "pref_comments_font";
    private static final String KEY_COMMENTS_SIZE = "pref_font_size";

    private static final String[] FONT_TITLES = new String[]{
            "Default",
            "Thin",
            "Light",
            "Regular",
            "Medium",
            "Black",
            "Condensed light",
            "Condensed regular",
            "Serif",
            "Monospace",
            "Serif monospace",
            "Small caps",
            "Roboto Slab",
    };
    private static final String[] FONT_VALUES = new String[]{
            "",
            "sans-serif-thin",
            "sans-serif-light",
            "sans-serif",
            "sans-serif-medium",
            "sans-serif-black",
            "sans-serif-condensed-light",
            "sans-serif-condensed",
            "serif",
            "monospace",
            "serif-monospace",
            "sans-serif-smallcaps",
            "RobotoSlab-Regular.ttf",
    };
    private static final String[] SIZE_TITLES = new String[]{
            "Extra small",
            "Small",
            "Medium",
            "Large",
            "Extra large",
            "Extra extra large",
    };
    private static final String[] SIZE_VALUES = new String[]{
            "XSmall",
            "Small",
            "Medium",
            "Large",
            "XLarge",
            "XXLarge",
    };

    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences preferences;
    private TextView previewTitle;
    private TextView previewComment;
    private TextView titleFontSummary;
    private TextView titleSizeSummary;
    private TextView commentsFontSummary;
    private TextView commentsSizeSummary;

    public MorpheSettingsV4FontsFragment() {
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
        content.setPadding(dp(18), dp(12), dp(18), dp(32));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addSectionLabel(content, "Live preview");
        addSpace(content, 8);
        addPreview(content);

        addSpace(content, 18);
        addSectionLabel(content, "Posts");
        addSpace(content, 8);
        LinearLayout posts = addGroup(content);
        LinearLayout titleFont = addNavigationRow(
                posts,
                "Title font",
                fontTitle(preferences.getString(KEY_TITLE_FONT, "")),
                view -> showFontDialog(KEY_TITLE_FONT, "Title font")
        );
        titleFontSummary = rowSummary(titleFont);
        LinearLayout titleSize = addNavigationRow(
                posts,
                "Title text size",
                sizeTitle(preferences.getString(KEY_TITLE_SIZE, "Medium")),
                view -> showSizeDialog(KEY_TITLE_SIZE, "Title text size")
        );
        titleSizeSummary = rowSummary(titleSize);

        addSpace(content, 18);
        addSectionLabel(content, "Comments & messages");
        addSpace(content, 8);
        LinearLayout comments = addGroup(content);
        LinearLayout commentsFont = addNavigationRow(
                comments,
                "Comments font",
                fontTitle(preferences.getString(KEY_COMMENTS_FONT, "")),
                view -> showFontDialog(KEY_COMMENTS_FONT, "Comments font")
        );
        commentsFontSummary = rowSummary(commentsFont);
        LinearLayout commentsSize = addNavigationRow(
                comments,
                "Comments text size",
                sizeTitle(preferences.getString(KEY_COMMENTS_SIZE, "Medium")),
                view -> showSizeDialog(KEY_COMMENTS_SIZE, "Comments text size")
        );
        commentsSizeSummary = rowSummary(commentsSize);

        addSpace(content, 18);
        TextView reset = actionText("Restore font defaults");
        reset.setOnClickListener(view -> showResetConfirmation());
        LinearLayout.LayoutParams resetParams = wrapParams();
        resetParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(reset, resetParams);

        updatePreviewAndSummaries();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePreviewAndSummaries();
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

    private void addPreview(LinearLayout parent) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();

        LinearLayout previewCanvas = new LinearLayout(context);
        previewCanvas.setOrientation(LinearLayout.VERTICAL);
        previewCanvas.setPadding(dp(18), dp(16), dp(18), dp(18));
        int previewSurface = MorpheSettingsV4Theme.blend(
                tokens.surfaceContainerHigh,
                accent.container,
                tokens.dark ? 0.07f : 0.05f
        );
        previewCanvas.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                previewSurface,
                24
        ));
        previewCanvas.setClipToOutline(true);
        parent.addView(previewCanvas, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView postMeta = textView(
                "r/BoostForReddit  ·  just now",
                12,
                tokens.textSecondary
        );
        postMeta.setTypeface(Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
        ));
        previewCanvas.addView(postMeta, wrapParams());

        previewTitle = textView(
                "A post title using your chosen font",
                18,
                tokens.textPrimary
        );
        LinearLayout.LayoutParams titleParams = wrapParams();
        titleParams.topMargin = dp(6);
        previewCanvas.addView(previewTitle, titleParams);

        addPreviewDivider(previewCanvas);

        TextView commentMeta = textView(
                "u/morphe  ·  moments ago",
                12,
                tokens.textSecondary
        );
        commentMeta.setTypeface(Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
        ));
        previewCanvas.addView(commentMeta, wrapParams());

        LinearLayout commentThread = new LinearLayout(context);
        commentThread.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams threadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        threadParams.topMargin = dp(8);
        previewCanvas.addView(commentThread, threadParams);

        View threadRail = new View(context);
        threadRail.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                accent.color,
                1.5f
        ));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(
                dp(3),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        railParams.setMarginEnd(dp(10));
        commentThread.addView(threadRail, railParams);

        previewComment = textView(
                "This comment uses your selected comment font and size.",
                15,
                tokens.textPrimary
        );
        previewComment.setLineSpacing(0, 1.08f);
        commentThread.addView(previewComment, labelsParams());
    }

    private void addPreviewDivider(LinearLayout parent) {
        View divider = new View(requireContext());
        divider.setBackgroundColor(MorpheSettingsV4Theme.blend(
                tokens.surfaceContainerHigh,
                tokens.outline,
                tokens.dark ? 0.28f : 0.20f
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        params.topMargin = dp(16);
        params.bottomMargin = dp(16);
        parent.addView(divider, params);
    }

    private LinearLayout addNavigationRow(
            LinearLayout group,
            String titleValue,
            String summaryValue,
            View.OnClickListener listener
    ) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(18), dp(9), dp(12), dp(9));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                Color.TRANSPARENT,
                0,
                accent.color
        ));
        row.setOnClickListener(listener);

        LinearLayout labels = createLabels(titleValue, summaryValue);
        row.addView(labels, labelsParams());
        TextView chevron = textView("›", 28, accent.color);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(36), dp(48)));
        addGroupedRow(group, row);
        return row;
    }

    private void showFontDialog(String key, String titleValue) {
        String selected = preferences.getString(key, "");
        Dialog dialog = new Dialog(requireContext());
        LinearLayout content = dialogContent();
        addDialogHeading(content, titleValue);

        ScrollView choicesScroll = new ScrollView(requireContext());
        LinearLayout choices = new LinearLayout(requireContext());
        choices.setOrientation(LinearLayout.VERTICAL);
        choicesScroll.addView(choices, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (int index = 0; index < FONT_TITLES.length; index++) {
            final int choice = index;
            LinearLayout row = dialogChoiceRow();
            RadioButton radio = radioButton(
                    TextUtils.equals(selected, FONT_VALUES[index])
            );
            row.addView(radio);
            TextView label = textView(FONT_TITLES[index], 16, tokens.textPrimary);
            label.setTypeface(typefaceFor(FONT_VALUES[index]));
            row.addView(label, labelsParams());
            row.setOnClickListener(view -> {
                saveFontPreference(key, FONT_VALUES[choice]);
                dialog.dismiss();
            });
            choices.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(430)
        );
        content.addView(choicesScroll, choicesParams);
        addDialogAction(content, "Cancel", () -> dialog.dismiss());
        showDialog(dialog, content);
    }

    private void showSizeDialog(String key, String titleValue) {
        String selected = preferences.getString(key, "Medium");
        Dialog dialog = new Dialog(requireContext());
        LinearLayout content = dialogContent();
        addDialogHeading(content, titleValue);

        for (int index = 0; index < SIZE_TITLES.length; index++) {
            final int choice = index;
            LinearLayout row = dialogChoiceRow();
            row.addView(radioButton(TextUtils.equals(
                    selected,
                    SIZE_VALUES[index]
            )));
            TextView label = textView(
                    SIZE_TITLES[index],
                    previewSizeSp(SIZE_VALUES[index], false),
                    tokens.textPrimary
            );
            row.addView(label, labelsParams());
            row.setOnClickListener(view -> {
                saveFontPreference(key, SIZE_VALUES[choice]);
                dialog.dismiss();
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        addDialogAction(content, "Cancel", () -> dialog.dismiss());
        showDialog(dialog, content);
    }

    private void saveFontPreference(String key, String value) {
        preferences.edit().putString(key, value).apply();
        markNativeFontCacheDirty(key);
        updatePreviewAndSummaries();
    }

    private void showResetConfirmation() {
        Dialog dialog = new Dialog(requireContext());
        LinearLayout content = dialogContent();
        addDialogHeading(content, "Restore font defaults?");
        TextView body = textView(
                "Post titles, comments, and messages will return to Boost's default fonts and sizes.",
                14,
                tokens.textSecondary
        );
        body.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams bodyParams = wrapParams();
        bodyParams.topMargin = dp(6);
        bodyParams.bottomMargin = dp(10);
        content.addView(body, bodyParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = dialogAction("Cancel");
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView reset = dialogAction("Restore");
        reset.setOnClickListener(view -> {
            preferences.edit()
                    .remove(KEY_TITLE_FONT)
                    .remove(KEY_TITLE_SIZE)
                    .remove(KEY_COMMENTS_FONT)
                    .remove(KEY_COMMENTS_SIZE)
                    .apply();
            markNativeFontCacheDirty(KEY_TITLE_SIZE);
            markNativeFontCacheDirty(KEY_TITLE_FONT);
            dialog.dismiss();
            updatePreviewAndSummaries();
        });
        actions.addView(reset);
        content.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        showDialog(dialog, content);
    }

    private void updatePreviewAndSummaries() {
        if (preferences == null) {
            return;
        }
        String titleFontValue = preferences.getString(KEY_TITLE_FONT, "");
        String titleSizeValue = preferences.getString(KEY_TITLE_SIZE, "Medium");
        String commentsFontValue = preferences.getString(KEY_COMMENTS_FONT, "");
        String commentsSizeValue = preferences.getString(
                KEY_COMMENTS_SIZE,
                "Medium"
        );
        setText(titleFontSummary, fontTitle(titleFontValue));
        setText(titleSizeSummary, sizeTitle(titleSizeValue));
        setText(commentsFontSummary, fontTitle(commentsFontValue));
        setText(commentsSizeSummary, sizeTitle(commentsSizeValue));
        if (previewTitle != null) {
            previewTitle.setTypeface(typefaceFor(titleFontValue));
            previewTitle.setTextSize(previewSizeSp(titleSizeValue, true));
        }
        if (previewComment != null) {
            previewComment.setTypeface(typefaceFor(commentsFontValue));
            previewComment.setTextSize(previewSizeSp(
                    commentsSizeValue,
                    false
            ));
        }
    }

    private void markNativeFontCacheDirty(String key) {
        String fieldName = null;
        if (KEY_TITLE_SIZE.equals(key) || KEY_COMMENTS_SIZE.equals(key)) {
            fieldName = "d";
        } else if (KEY_TITLE_FONT.equals(key)) {
            fieldName = "h";
        }
        if (fieldName == null) {
            return;
        }
        try {
            Field field = Class.forName("id.b").getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }
    }

    private Typeface typefaceFor(String value) {
        if (TextUtils.isEmpty(value)) {
            return Typeface.DEFAULT;
        }
        if (value.endsWith(".ttf")) {
            return Typeface.create("serif", Typeface.NORMAL);
        }
        Typeface result = Typeface.create(value, Typeface.NORMAL);
        return result == null ? Typeface.DEFAULT : result;
    }

    private int previewSizeSp(String value, boolean title) {
        int index = indexOf(SIZE_VALUES, value);
        if (index < 0) {
            index = 2;
        }
        int[] bodySizes = new int[]{12, 13, 15, 17, 19, 21};
        int[] titleSizes = new int[]{14, 16, 18, 20, 22, 24};
        return title ? titleSizes[index] : bodySizes[index];
    }

    private String fontTitle(String value) {
        int index = indexOf(FONT_VALUES, value);
        return index < 0 ? "Default" : FONT_TITLES[index];
    }

    private String sizeTitle(String value) {
        int index = indexOf(SIZE_VALUES, value);
        return index < 0 ? "Medium" : SIZE_TITLES[index];
    }

    private int indexOf(String[] values, String target) {
        for (int index = 0; index < values.length; index++) {
            if (TextUtils.equals(values[index], target)) {
                return index;
            }
        }
        return -1;
    }

    private void setText(TextView view, String value) {
        if (view != null) {
            view.setText(value);
        }
    }

    private RadioButton radioButton(boolean checked) {
        RadioButton radio = new RadioButton(requireContext());
        radio.setChecked(checked);
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
        return radio;
    }

    private LinearLayout dialogContent() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(14));
        content.setBackground(MorpheSettingsV4Theme.rounded(
                requireContext(),
                tokens.surfaceContainerHigh,
                28
        ));
        return content;
    }

    private void addDialogHeading(LinearLayout content, String value) {
        TextView heading = textView(value, 20, tokens.textPrimary);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams params = wrapParams();
        params.setMarginStart(dp(8));
        params.bottomMargin = dp(8);
        content.addView(heading, params);
    }

    private LinearLayout dialogChoiceRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(4), 0, dp(8), 0);
        row.setBackground(MorpheSettingsV4Theme.interactive(
                requireContext(),
                Color.TRANSPARENT,
                16,
                tokens.navigationAccent().color
        ));
        return row;
    }

    private void addDialogAction(
            LinearLayout content,
            String value,
            Runnable action
    ) {
        TextView button = dialogAction(value);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = wrapParams();
        params.gravity = Gravity.END;
        params.topMargin = dp(4);
        content.addView(button, params);
    }

    private TextView dialogAction(String value) {
        TextView button = textView(value, 14, tokens.navigationAccent().color);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setBackground(MorpheSettingsV4Theme.interactive(
                requireContext(),
                Color.TRANSPARENT,
                18,
                tokens.navigationAccent().color
        ));
        return button;
    }

    private TextView actionText(String value) {
        TextView action = dialogAction(value);
        action.setTextSize(15);
        return action;
    }

    private void showDialog(Dialog dialog, LinearLayout content) {
        Context context = requireContext();
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

    private LinearLayout addGroup(LinearLayout parent) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        int color = MorpheSettingsV4Theme.blend(
                tokens.surfaceContainer,
                accent.container,
                tokens.dark ? 0.035f : 0.025f
        );
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(MorpheSettingsV4Theme.rounded(context, color, 24));
        group.setClipToOutline(true);
        parent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return group;
    }

    private void addGroupedRow(LinearLayout group, LinearLayout row) {
        if (group.getChildCount() > 0) {
            View divider = new View(requireContext());
            divider.setBackgroundColor(MorpheSettingsV4Theme.blend(
                    tokens.surfaceContainer,
                    tokens.outline,
                    tokens.dark ? 0.22f : 0.16f
            ));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(1)
            );
            dividerParams.setMarginStart(dp(18));
            dividerParams.setMarginEnd(dp(18));
            group.addView(divider, dividerParams);
        }
        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private LinearLayout createLabels(String titleValue, String summaryValue) {
        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = textView(titleValue, 16, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labels.addView(title);
        TextView summary = textView(summaryValue, 14, tokens.textSecondary);
        LinearLayout.LayoutParams summaryParams = wrapParams();
        summaryParams.topMargin = dp(3);
        labels.addView(summary, summaryParams);
        return labels;
    }

    private TextView rowSummary(LinearLayout row) {
        if (row.getChildCount() == 0
                || !(row.getChildAt(0) instanceof LinearLayout)) {
            return null;
        }
        LinearLayout labels = (LinearLayout) row.getChildAt(0);
        return labels.getChildCount() > 1
                && labels.getChildAt(1) instanceof TextView
                ? (TextView) labels.getChildAt(1)
                : null;
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Fonts");
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
        TextView label = textView(value, 14, tokens.primary);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.02f);
        parent.addView(label);
    }

    private TextView textView(String value, int sizeSp, int color) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
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
}
