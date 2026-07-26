package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
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

/** Global Settings search entry available from every Morphe-owned page. */
final class MorpheSettingsV4Search {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_ISSUE121_PHASE1_2_V1";
    static final String FRAGMENT_ABI_COMPAT_MARKER =
            "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_FRAGMENT_ABI_COMPAT_"
                    + "ISSUE121_PHASE1_2_1_V1";
    static final String PLATFORM_DIALOG_MARKER =
            "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_PLATFORM_DIALOG_"
                    + "ISSUE121_PHASE1_2_3_V1";
    static final String SINGLE_BACK_DISMISS_MARKER =
            "MORPHE_BOOST_SETTINGS_GLOBAL_SEARCH_SINGLE_BACK_DISMISS_"
                    + "ISSUE121_PHASE1_2_4_V2";

    static final String ARGUMENT_PAGE =
            "morphe_boost_settings_v4_page";
    static final String PAGE_ROOT = "root";
    static final String EXTRA_OPEN_SEARCH =
            "morphe_boost_settings_v4_open_search";

    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";
    private static final int MENU_ID_GLOBAL_SEARCH = 0x4d535312;
    private static final int RESULT_LIMIT = 80;
    private static final String[] BOOST_SEARCH_ITEM_NAMES = new String[]{
            "action_generic_search",
            "action_search",
            "search",
            "menu_search",
    };

    private MorpheSettingsV4Search() {
    }

    static void prepareMenu(
            Fragment fragment,
            Menu menu,
            boolean showGlobalSearch
    ) {
        if (fragment == null || menu == null || !fragment.isAdded()) {
            return;
        }

        Context context = fragment.requireContext();
        for (String resourceName : BOOST_SEARCH_ITEM_NAMES) {
            hideMenuItem(context, menu, resourceName);
        }
        menu.removeItem(MENU_ID_GLOBAL_SEARCH);

        if (!showGlobalSearch) {
            return;
        }

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

    private static void openGlobalSearch(Fragment fragment) {
        Context context;
        try {
            context = fragment.requireContext();
        } catch (Throwable ignored) {
            return;
        }
        if (!(context instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) context;
        MorpheSettingsV4Theme.Tokens tokens =
                MorpheSettingsV4Theme.resolve(context);
        List<MorpheSettingsV4Catalog.SearchItem> searchIndex =
                MorpheSettingsV4Catalog.buildSearchIndex(context);

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
        close.setOnClickListener(
                view -> dismissSearch(dialog, null)
        );
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
        searchField.setPadding(
                dp(context, 18),
                0,
                dp(context, 18),
                0
        );
        searchField.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                tokens.surfaceContainerHigh,
                28
        ));
        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(context, 56),
                        1.0f
                );
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
                        fragment,
                        results,
                        searchIndex,
                        value == null ? "" : value.toString(),
                        tokens
                );
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });

        renderResults(
                dialog,
                fragment,
                results,
                searchIndex,
                "",
                tokens
        );

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
            InputMethodManager inputMethodManager =
                    (InputMethodManager) context.getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(
                        searchField,
                        InputMethodManager.SHOW_IMPLICIT
                );
            }
        });
    }

    private static void renderResults(
            Dialog dialog,
            Fragment fragment,
            LinearLayout results,
            List<MorpheSettingsV4Catalog.SearchItem> searchIndex,
            String query,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        results.removeAllViews();
        Context context = results.getContext();
        String normalized = MorpheSettingsV4Catalog.normalize(query);

        if (TextUtils.isEmpty(normalized)) {
            addSupportingText(
                    results,
                    "Type a setting name, category, summary, or preference key.",
                    tokens
            );
            return;
        }

        List<MorpheSettingsV4Catalog.SearchItem> matches = new ArrayList<>();
        for (MorpheSettingsV4Catalog.SearchItem item : searchIndex) {
            if (item.matches(normalized)) {
                matches.add(item);
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

        int limit = Math.min(matches.size(), RESULT_LIMIT);
        for (int index = 0; index < limit; index++) {
            MorpheSettingsV4Catalog.SearchItem item = matches.get(index);
            results.addView(createResultRow(
                    dialog,
                    fragment,
                    item,
                    tokens
            ));
        }

        if (matches.size() > limit) {
            addSupportingText(
                    results,
                    "Showing the first "
                            + limit
                            + " results. Keep typing to narrow the search.",
                    tokens
            );
        }
    }

    private static View createResultRow(
            Dialog dialog,
            Fragment fragment,
            MorpheSettingsV4Catalog.SearchItem item,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        Context context = fragment.requireContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(
                dp(context, 18),
                dp(context, 14),
                dp(context, 18),
                dp(context, 14)
        );
        row.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                tokens.surfaceContainer,
                18,
                tokens.primary
        ));
        row.setClickable(true);
        row.setFocusable(true);

        TextView title = new TextView(context);
        title.setText(item.title);
        title.setTextSize(16);
        title.setTextColor(tokens.textPrimary);
        row.addView(title);

        String secondary = item.category;
        if (!TextUtils.isEmpty(item.summary)) {
            secondary += "\n" + item.summary;
        }
        TextView summary = new TextView(context);
        summary.setText(secondary);
        summary.setTextSize(14);
        summary.setTextColor(tokens.textSecondary);
        summary.setLineSpacing(0, 1.06f);
        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        summaryParams.topMargin = dp(context, 4);
        row.addView(summary, summaryParams);

        row.setOnClickListener(view -> openSearchItem(
                dialog,
                fragment,
                item
        ));

        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        rowParams.bottomMargin = dp(context, 8);
        row.setLayoutParams(rowParams);
        return row;
    }

    private static void openSearchItem(
            Dialog dialog,
            Fragment fragment,
            MorpheSettingsV4Catalog.SearchItem item
    ) {
        Context context;
        try {
            context = fragment.requireContext();
        } catch (Throwable ignored) {
            return;
        }
        if (!(context instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) context;
        Intent intent;

        if (!TextUtils.isEmpty(item.activityName)) {
            intent = new Intent();
            intent.setClassName(context.getPackageName(), item.activityName);
        } else {
            String fragmentName = item.fragmentName;
            if (!TextUtils.isEmpty(item.pageId)) {
                fragmentName = MorpheSettingsV4Fragment.class.getName();
            }
            if (TextUtils.isEmpty(fragmentName)) {
                return;
            }

            intent = new Intent(activity, activity.getClass());
            intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
            if (!TextUtils.isEmpty(item.pageId)) {
                intent.putExtra(ARGUMENT_PAGE, item.pageId);
            }
        }

        dismissSearch(dialog, null);
        activity.startActivity(intent);
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
            InputMethodManager inputMethodManager =
                    (InputMethodManager) searchField.getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(
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

    private static int dp(Context context, float value) {
        return MorpheSettingsV4Theme.dp(context, value);
    }

    private static void hideMenuItem(
            Context context,
            Menu menu,
            String resourceName
    ) {
        if (context == null || menu == null || TextUtils.isEmpty(resourceName)) {
            return;
        }
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
}
