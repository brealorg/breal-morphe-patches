package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Platform-only global search for the hidden Settings V5 tree. */
final class MorpheSettingsV5Search {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_ISSUE121_V1";
    static final String PLATFORM_DIALOG_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_PLATFORM_DIALOG_ISSUE121_V1";
    static final String SINGLE_BACK_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_SINGLE_BACK_ISSUE121_V1";
    static final String MULTI_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_MULTI_WAVE_ISSUE121_V1";
    static final String MEDIA_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_MEDIA_WAVE_ISSUE121_V1";
    static final String NOTIFICATIONS_ACCOUNT_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_NOTIFICATIONS_ACCOUNT_WAVE_ISSUE121_V1";
    static final String NAVIGATION_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_NAVIGATION_WAVE_ISSUE121_V1";
    static final String DATA_APP_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_SEARCH_DATA_APP_WAVE_ISSUE121_V1";

    private static final int MENU_ID_GLOBAL_SEARCH = 0x4d535521;
    private static final int RESULT_LIMIT = 80;
    private static final String[] BOOST_SEARCH_ITEM_NAMES = new String[]{
            "action_generic_search",
            "action_search",
            "search",
            "menu_search",
    };

    private MorpheSettingsV5Search() {
    }

    static void prepareMenu(Fragment fragment, Menu menu) {
        if (fragment == null || menu == null || !fragment.isAdded()) {
            return;
        }
        Context context = fragment.requireContext();
        for (String resourceName : BOOST_SEARCH_ITEM_NAMES) {
            hideMenuItem(context, menu, resourceName);
        }
        menu.removeItem(MENU_ID_GLOBAL_SEARCH);

        MorpheSettingsV4Theme.Tokens tokens =
                MorpheSettingsV4Theme.resolve(context);
        MenuItem item = menu.add(
                Menu.NONE,
                MENU_ID_GLOBAL_SEARCH,
                Menu.FIRST,
                "Search all settings"
        );
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        int iconId = MorpheSettingsV4Catalog.resourceId(
                context,
                "drawable",
                "ic_search_color_24dp"
        );
        if (iconId != 0) {
            Drawable drawable = context.getDrawable(iconId);
            if (drawable != null) {
                drawable = drawable.mutate();
                drawable.setTintList(ColorStateList.valueOf(tokens.primary));
                item.setIcon(drawable);
            }
        }
    }

    static boolean handleMenuItem(Fragment fragment, MenuItem item) {
        if (fragment == null || item == null
                || item.getItemId() != MENU_ID_GLOBAL_SEARCH) {
            return false;
        }
        openGlobalSearch(fragment);
        return true;
    }

    static void openGlobalSearch(Fragment host) {
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
        MorpheSettingsV4Theme.Tokens tokens =
                MorpheSettingsV4Theme.resolve(context);
        List<SearchEntry> index = buildIndex(context);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(tokens.background);
        shell.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), 0);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(
                toolbar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(context, 64)
                )
        );

        ImageButton close = new ImageButton(context);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setColorFilter(tokens.primary);
        close.setBackgroundColor(0x00000000);
        close.setContentDescription("Close search");
        toolbar.addView(
                close,
                new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48))
        );

        EditText searchField = new EditText(context);
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setTextColor(tokens.textPrimary);
        searchField.setHintTextColor(tokens.textSecondary);
        searchField.setHint("Search all settings");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        searchField.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                tokens.surfaceContainerHigh,
                28
        ));
        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(0, dp(context, 56), 1.0f);
        searchParams.setMarginStart(dp(context, 8));
        toolbar.addView(searchField, searchParams);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        LinearLayout results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(
                dp(context, 4),
                dp(context, 14),
                dp(context, 4),
                dp(context, 36)
        );
        scrollView.addView(
                results,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        shell.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1.0f
                )
        );

        close.setOnClickListener(view -> dismissSearch(dialog, searchField));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence value,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence value,
                    int start,
                    int before,
                    int count
            ) {
                renderResults(
                        dialog,
                        host,
                        results,
                        index,
                        value == null ? "" : value.toString(),
                        tokens
                );
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        renderResults(dialog, host, results, index, "", tokens);
        dialog.setContentView(shell);
        dialog.show();
        installSingleBackDismiss(dialog, searchField);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(tokens.background));
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            );
        }

        searchField.post(() -> {
            searchField.requestFocus();
            InputMethodManager manager =
                    (InputMethodManager) context.getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );
            if (manager != null) {
                manager.showSoftInput(
                        searchField,
                        InputMethodManager.SHOW_IMPLICIT
                );
            }
        });
    }

    private static List<SearchEntry> buildIndex(Context context) {
        List<SearchEntry> result = new ArrayList<>();
        for (MorpheSettingsV5Registry.V5PageSpec page
                : MorpheSettingsV5Registry.allPages()) {
            if (!MorpheSettingsV5Registry.DEFAULT_PAGE_ID.equals(page.pageId)) {
                result.add(new SearchEntry(
                        MorpheSettingsV5Registry.titleFor(page.pageId),
                        MorpheSettingsV5Registry.introFor(page.pageId),
                        page.pageId,
                        null
                ));
            }
            for (String key : page.keys) {
                result.add(new SearchEntry(
                        titleForKey(context, page.pageId, key),
                        summaryForKey(context, page.pageId, key),
                        page.pageId,
                        key
                ));
            }
        }
        return result;
    }

    private static String titleForKey(
            Context context,
            String pageId,
            String key
    ) {
        if (pageId.startsWith("v5/reading_and_interaction")) {
            return MorpheSettingsV5ReadingMetadata.titleFor(context, key);
        }
        if (pageId.startsWith("v5/navigation")) {
            return MorpheSettingsV5NavigationMetadata.titleFor(context, key);
        }
        if (pageId.startsWith("v5/media")) {
            return MorpheSettingsV5MediaMetadata.titleFor(context, key);
        }
        if (pageId.startsWith("v5/data_and_app")) {
            return MorpheSettingsV5DataAppMetadata.titleFor(context, key);
        }
        if (pageId.startsWith("v5/notifications_and_account")) {
            return MorpheSettingsV5NotificationsAccountMetadata.titleFor(
                    context,
                    key
            );
        }
        return MorpheSettingsV5AppearanceBindings.titleFor(context, key);
    }

    private static String summaryForKey(
            Context context,
            String pageId,
            String key
    ) {
        if (pageId.startsWith("v5/reading_and_interaction")) {
            return MorpheSettingsV5ReadingMetadata.searchSummaryFor(
                    context,
                    key
            );
        }
        if (pageId.startsWith("v5/navigation")) {
            return MorpheSettingsV5NavigationMetadata.searchSummaryFor(
                    context,
                    key
            );
        }
        if (pageId.startsWith("v5/media")) {
            return MorpheSettingsV5MediaMetadata.searchSummaryFor(
                    context,
                    key
            );
        }
        if (pageId.startsWith("v5/data_and_app")) {
            return MorpheSettingsV5DataAppMetadata.searchSummaryFor(
                    context,
                    key
            );
        }
        if (pageId.startsWith("v5/notifications_and_account")) {
            return MorpheSettingsV5NotificationsAccountMetadata.searchSummaryFor(
                    context,
                    key
            );
        }
        return MorpheSettingsV5AppearanceBindings.searchSummaryFor(
                context,
                key
        );
    }

    private static void renderResults(
            Dialog dialog,
            Fragment host,
            LinearLayout results,
            List<SearchEntry> index,
            String query,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        results.removeAllViews();
        String normalized = normalize(query);
        if (TextUtils.isEmpty(normalized)) {
            addSupportingText(
                    results,
                    "Type a setting name, value, or category.",
                    tokens
            );
            return;
        }

        List<SearchEntry> matches = new ArrayList<>();
        for (SearchEntry entry : index) {
            if (entry.matches(normalized)) {
                matches.add(entry);
            }
        }
        addSupportingText(
                results,
                matches.isEmpty()
                        ? "No matching settings"
                        : matches.size()
                        + (matches.size() == 1 ? " result" : " results"),
                tokens
        );

        LinearLayout list = MorpheSettingsV14Ui.standardList(
                results.getContext()
        );
        results.addView(
                list,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        int limit = Math.min(matches.size(), RESULT_LIMIT);
        for (int indexValue = 0; indexValue < limit; indexValue++) {
            SearchEntry entry = matches.get(indexValue);
            LinearLayout row = MorpheSettingsV14Ui.standardListRow(
                    results.getContext(),
                    tokens,
                    !TextUtils.isEmpty(entry.summary)
            );
            row.addView(
                    MorpheSettingsV14Ui.standardListLabels(
                            results.getContext(),
                            tokens,
                            entry.title,
                            entry.summary
                    ),
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );
            row.addView(MorpheSettingsV14Ui.chevron(
                    results.getContext(),
                    tokens
            ));
            row.setOnClickListener(view -> {
                dismissSearch(dialog, null);
                MorpheSettingsV5Navigation.openPage(host, entry.pageId);
            });
            MorpheSettingsV14Ui.addStandardListRow(list, row, tokens);
        }
    }

    private static void installSingleBackDismiss(
            Dialog dialog,
            EditText searchField
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback callback = () ->
                    dismissSearch(dialog, searchField);
            Api33.registerOverlayBack(dialog, callback);
            dialog.setOnDismissListener(
                    ignored -> Api33.unregisterBack(dialog, callback)
            );
            return;
        }
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK
                    || event == null
                    || event.getAction() != KeyEvent.ACTION_UP) {
                return false;
            }
            dismissSearch(dialog, searchField);
            return true;
        });
    }

    private static void dismissSearch(
            Dialog dialog,
            EditText searchField
    ) {
        if (searchField != null) {
            InputMethodManager manager =
                    (InputMethodManager) searchField.getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.hideSoftInputFromWindow(
                        searchField.getWindowToken(),
                        0
                );
            }
        }
        dialog.dismiss();
    }

    private static final class Api33 {
        private Api33() {
        }

        static void registerOverlayBack(
                Dialog dialog,
                OnBackInvokedCallback callback
        ) {
            dialog.getOnBackInvokedDispatcher()
                    .registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                            callback
                    );
        }

        static void unregisterBack(
                Dialog dialog,
                OnBackInvokedCallback callback
        ) {
            dialog.getOnBackInvokedDispatcher()
                    .unregisterOnBackInvokedCallback(callback);
        }
    }

    private static void addSupportingText(
            LinearLayout parent,
            String value,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        TextView text = new TextView(parent.getContext());
        text.setText(value);
        text.setTextSize(14);
        text.setTextColor(tokens.textSecondary);
        text.setPadding(
                dp(parent.getContext(), 10),
                dp(parent.getContext(), 8),
                dp(parent.getContext(), 10),
                dp(parent.getContext(), 16)
        );
        parent.addView(text);
    }

    private static void hideMenuItem(
            Context context,
            Menu menu,
            String resourceName
    ) {
        int resourceId = MorpheSettingsV4Catalog.resourceId(
                context,
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

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int dp(Context context, float value) {
        return MorpheSettingsV4Theme.dp(context, value);
    }

    private static final class SearchEntry {
        final String title;
        final String summary;
        final String pageId;
        final String key;

        SearchEntry(
                String title,
                String summary,
                String pageId,
                String key
        ) {
            this.title = title == null ? "" : title;
            this.summary = summary == null ? "" : summary;
            this.pageId = pageId;
            this.key = key;
        }

        boolean matches(String normalizedQuery) {
            return normalize(title).contains(normalizedQuery)
                    || normalize(summary).contains(normalizedQuery)
                    || normalize(pageId).contains(normalizedQuery)
                    || normalize(key).contains(normalizedQuery);
        }
    }
}
