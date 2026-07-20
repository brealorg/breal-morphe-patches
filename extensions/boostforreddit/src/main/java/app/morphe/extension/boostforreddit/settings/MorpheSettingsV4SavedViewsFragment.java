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
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Material 3 editor for Boost's per-community saved post views. */
public final class MorpheSettingsV4SavedViewsFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_SAVED_VIEWS_ISSUE106_V1";

    private static final String SAVED_VIEWS_PREFERENCES =
            "com.rubenmayayo.reddit.VIEW_PER_SUBSCRIPTION";
    private static final String MULTI_SUFFIX = ".multi";
    private static final String FRONT_PAGE_KEY =
            "_load_front_page_this_is_not_a_subreddit";
    private static final String SAVED_KEY =
            "_load_saved_this_is_not_a_subreddit";
    private static final String HISTORY_KEY =
            "_load_history_this_is_not_a_subreddit";

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
    private static final int[] VIEW_VALUES = new int[]{
            0, 7, 1, 4, 5, 2, 6, 3,
    };

    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences savedViews;
    private LinearLayout entriesHost;

    public MorpheSettingsV4SavedViewsFragment() {
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Context context = inflater.getContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
        savedViews = context.getSharedPreferences(
                SAVED_VIEWS_PREFERENCES,
                Context.MODE_PRIVATE
        );
        setHasOptionsMenu(true);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        entriesHost = new LinearLayout(context);
        entriesHost.setOrientation(LinearLayout.VERTICAL);
        entriesHost.setPadding(dp(18), dp(12), dp(18), dp(32));
        scrollView.addView(
                entriesHost,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        rebuildEntries();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (entriesHost != null) {
            rebuildEntries();
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

    private void rebuildEntries() {
        if (entriesHost == null || savedViews == null) {
            return;
        }
        entriesHost.removeAllViews();
        addSectionLabel(entriesHost, "Community views");
        addSpace(entriesHost, 8);

        addAddSavedViewCard(entriesHost);
        addSpace(entriesHost, 12);

        List<SavedView> entries = readEntries();
        if (entries.isEmpty()) {
            addEmptyState(entriesHost);
            addSpace(entriesHost, 18);
            addExplanation(entriesHost);
            return;
        }

        LinearLayout group = addGroup(entriesHost);
        for (SavedView entry : entries) {
            addSavedViewRow(group, entry);
        }

        addSpace(entriesHost, 18);
        TextView clearAll = actionText("Clear all saved views");
        clearAll.setOnClickListener(view -> showConfirmation(
                "Clear saved views?",
                "This removes the saved view for every community.",
                "Clear all",
                () -> {
                    savedViews.edit().clear().apply();
                    rebuildEntries();
                }
        ));
        LinearLayout.LayoutParams clearParams = wrapParams();
        clearParams.gravity = Gravity.CENTER_HORIZONTAL;
        entriesHost.addView(clearAll, clearParams);

        addSpace(entriesHost, 14);
        addExplanation(entriesHost);
    }

    private void addAddSavedViewCard(LinearLayout parent) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout action = new LinearLayout(context);
        action.setOrientation(LinearLayout.HORIZONTAL);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setMinimumHeight(dp(72));
        action.setPadding(dp(18), dp(10), dp(14), dp(10));
        action.setClickable(true);
        action.setFocusable(true);
        action.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                MorpheSettingsV4Theme.blend(
                        tokens.surfaceContainer,
                        accent.container,
                        tokens.dark ? 0.12f : 0.08f
                ),
                24,
                accent.color
        ));
        action.setOnClickListener(view -> showAddSavedViewDialog());

        TextView plus = textView("+", 26, accent.color);
        plus.setGravity(Gravity.CENTER);
        plus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout labels = createLabels(
                "Add saved view",
                "Choose a community or custom feed and its view"
        );
        LinearLayout.LayoutParams labelsLayout = labelsParams();
        labelsLayout.setMarginStart(dp(10));
        action.addView(labels, labelsLayout);
        parent.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void showAddSavedViewDialog() {
        Context context = requireContext();
        Dialog dialog = new Dialog(context);
        LinearLayout content = dialogContent();

        TextView heading = textView("Add saved view", 20, tokens.textPrimary);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.addView(heading);

        TextView description = textView(
                "Enter a community name, then choose the view Boost should remember.",
                14,
                tokens.textSecondary
        );
        description.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams descriptionParams = wrapParams();
        descriptionParams.topMargin = dp(6);
        content.addView(description, descriptionParams);

        EditText name = new EditText(context);
        name.setSingleLine(true);
        name.setHint("Community name");
        name.setTextColor(tokens.textPrimary);
        name.setHintTextColor(tokens.textSecondary);
        name.setTextSize(16);
        name.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        name.setPadding(dp(14), dp(11), dp(14), dp(11));
        name.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                tokens.surfaceContainer,
                16,
                tokens.navigationAccent().color
        ));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameParams.topMargin = dp(14);
        content.addView(name, nameParams);

        LinearLayout customFeedRow = dialogChoiceRow();
        TextView customFeedLabel = textView(
                "Custom feed",
                15,
                tokens.textPrimary
        );
        customFeedRow.addView(customFeedLabel, labelsParams());
        Switch customFeed = new Switch(context);
        tintSwitch(customFeed);
        customFeedRow.addView(customFeed);
        customFeedRow.setOnClickListener(
                view -> customFeed.setChecked(!customFeed.isChecked())
        );
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        customParams.topMargin = dp(6);
        content.addView(customFeedRow, customParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = dialogAction("Cancel");
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView continueButton = dialogAction("Choose view");
        continueButton.setOnClickListener(view -> {
            String key = normalizeSavedViewKey(
                    name.getText().toString(),
                    customFeed.isChecked()
            );
            if (TextUtils.isEmpty(key)) {
                name.setError("Enter a community name");
                return;
            }
            int current = savedViews.contains(key)
                    ? parseViewType(savedViews.getAll().get(key))
                    : 0;
            dialog.dismiss();
            showViewTypeDialog(new SavedView(
                    key,
                    displayName(key),
                    current
            ));
        });
        actions.addView(continueButton);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = dp(8);
        content.addView(actions, actionParams);
        showDialog(dialog, content);
    }

    private String normalizeSavedViewKey(String rawValue, boolean customFeed) {
        String value = rawValue == null ? "" : rawValue.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.regionMatches(true, 0, "r/", 0, 2)) {
            value = value.substring(2);
        }
        if (value.regionMatches(true, 0, "u/", 0, 2)) {
            int multi = value.toLowerCase().indexOf("/m/");
            value = multi >= 0 ? value.substring(multi + 3) : value.substring(2);
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

    private List<SavedView> readEntries() {
        List<SavedView> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : savedViews.getAll().entrySet()) {
            int viewType = parseViewType(entry.getValue());
            result.add(new SavedView(
                    entry.getKey(),
                    displayName(entry.getKey()),
                    viewType
            ));
        }
        Collections.sort(
                result,
                (left, right) -> left.key.compareToIgnoreCase(right.key)
        );
        return result;
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

    private void addEmptyState(LinearLayout parent) {
        LinearLayout group = addGroup(parent);
        group.setPadding(dp(22), dp(22), dp(22), dp(22));

        TextView title = textView("No saved views yet", 18, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        group.addView(title);

        TextView summary = textView(
                "Choose a different view inside a community and Boost will "
                        + "remember it here.",
                15,
                tokens.textSecondary
        );
        summary.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams summaryParams = wrapParams();
        summaryParams.topMargin = dp(6);
        group.addView(summary, summaryParams);
    }

    private void addExplanation(LinearLayout parent) {
        TextView note = textView(
                "Saved views are used when Remember per community is enabled.",
                14,
                tokens.textSecondary
        );
        note.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        noteParams.setMarginStart(dp(18));
        noteParams.setMarginEnd(dp(18));
        parent.addView(note, noteParams);
    }

    private void addSavedViewRow(LinearLayout group, SavedView entry) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(72));
        row.setPadding(dp(18), dp(9), dp(8), dp(9));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                Color.TRANSPARENT,
                0,
                accent.color
        ));
        row.setOnClickListener(view -> showViewTypeDialog(entry));

        row.addView(
                createLabels(entry.title, viewTitle(entry.viewType)),
                labelsParams()
        );

        ImageView delete = new ImageView(context);
        delete.setImageResource(android.R.drawable.ic_menu_delete);
        delete.setColorFilter(tokens.textSecondary);
        delete.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        delete.setContentDescription("Delete " + entry.title);
        delete.setClickable(true);
        delete.setFocusable(true);
        delete.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                Color.TRANSPARENT,
                22,
                accent.color
        ));
        delete.setOnClickListener(view -> showConfirmation(
                "Remove saved view?",
                entry.title + " will use the default view again.",
                "Remove",
                () -> {
                    savedViews.edit().remove(entry.key).apply();
                    rebuildEntries();
                }
        ));
        row.addView(delete, new LinearLayout.LayoutParams(dp(48), dp(48)));

        addGroupedRow(group, row);
    }

    private void showViewTypeDialog(SavedView entry) {
        Context context = requireContext();
        Dialog dialog = new Dialog(context);
        LinearLayout content = dialogContent();

        TextView heading = textView(entry.title, 20, tokens.textPrimary);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams headingParams = wrapParams();
        headingParams.setMarginStart(dp(8));
        headingParams.bottomMargin = dp(8);
        content.addView(heading, headingParams);

        for (int index = 0; index < VIEW_TITLES.length; index++) {
            final int choice = index;
            LinearLayout row = dialogChoiceRow();
            RadioButton radio = new RadioButton(context);
            radio.setChecked(VIEW_VALUES[index] == entry.viewType);
            radio.setClickable(false);
            radio.setFocusable(false);
            tintRadioButton(radio);
            row.addView(radio);

            TextView label = textView(VIEW_TITLES[index], 16, tokens.textPrimary);
            row.addView(label, labelsParams());
            row.setOnClickListener(view -> {
                savedViews.edit()
                        .putInt(entry.key, VIEW_VALUES[choice])
                        .apply();
                dialog.dismiss();
                rebuildEntries();
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        addDialogAction(content, "Cancel", () -> dialog.dismiss());
        showDialog(dialog, content);
    }

    private void showConfirmation(
            String titleValue,
            String message,
            String confirmLabel,
            Runnable confirmation
    ) {
        Dialog dialog = new Dialog(requireContext());
        LinearLayout content = dialogContent();

        TextView title = textView(titleValue, 20, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        content.addView(title);

        TextView body = textView(message, 15, tokens.textSecondary);
        body.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams bodyParams = wrapParams();
        bodyParams.topMargin = dp(8);
        bodyParams.bottomMargin = dp(14);
        content.addView(body, bodyParams);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = dialogAction("Cancel");
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel);
        TextView confirm = dialogAction(confirmLabel);
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            confirmation.run();
        });
        actions.addView(confirm);
        content.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        showDialog(dialog, content);
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

    private String displayName(String key) {
        String nativeTitle = nativeDisplayName(key);
        if (!TextUtils.isEmpty(nativeTitle)) {
            return nativeTitle;
        }
        if ("_load_front_page_this_is_not_a_subreddit".equals(key)) {
            return "Front page";
        }
        if ("_load_saved_this_is_not_a_subreddit".equals(key)) {
            return "Saved";
        }
        if ("_load_history_this_is_not_a_subreddit".equals(key)) {
            return "History";
        }
        if ("all".equalsIgnoreCase(key) || "popular".equalsIgnoreCase(key)) {
            return "r/" + key;
        }
        if (key.endsWith(MULTI_SUFFIX)) {
            return "Custom feed · "
                    + key.substring(0, key.length() - MULTI_SUFFIX.length());
        }
        return key.startsWith("r/") ? key : "r/" + key;
    }

    private String nativeDisplayName(String key) {
        try {
            Class<?> managerClass = Class.forName("he.k0");
            Object manager = managerClass.getDeclaredMethod("e").invoke(null);
            Method createModel = managerClass.getDeclaredMethod(
                    "i",
                    String.class
            );
            Object model = createModel.invoke(manager, key);
            Class<?> modelClass = Class.forName(
                    "com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel"
            );
            Method display = Class.forName("he.h0").getDeclaredMethod(
                    "Z0",
                    Context.class,
                    modelClass
            );
            Object value = display.invoke(null, requireContext(), model);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String viewTitle(int viewType) {
        try {
            Method display = Class.forName("he.h0").getDeclaredMethod(
                    "a1",
                    Context.class,
                    int.class
            );
            Object value = display.invoke(null, requireContext(), viewType);
            if (value instanceof String && !TextUtils.isEmpty((String) value)) {
                return (String) value;
            }
        } catch (Throwable ignored) {
        }
        for (int index = 0; index < VIEW_VALUES.length; index++) {
            if (VIEW_VALUES[index] == viewType) {
                return VIEW_TITLES[index];
            }
        }
        return "View " + viewType;
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

    private void tintRadioButton(RadioButton radio) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
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

    private void tintSwitch(Switch toggle) {
        if (Build.VERSION.SDK_INT < 21) {
            return;
        }
        int accent = tokens.navigationAccent().color;
        toggle.setThumbTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked},
                },
                new int[]{accent, tokens.textSecondary}
        ));
        toggle.setTrackTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked},
                },
                new int[]{
                        MorpheSettingsV4Theme.blend(
                                tokens.surfaceContainer,
                                accent,
                                0.45f
                        ),
                        tokens.surfaceContainerHigh,
                }
        ));
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Saved views");
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

    private static final class SavedView {
        final String key;
        final String title;
        final int viewType;

        SavedView(String key, String title, int viewType) {
            this.key = key;
            this.title = title;
            this.viewType = viewType;
        }
    }
}
