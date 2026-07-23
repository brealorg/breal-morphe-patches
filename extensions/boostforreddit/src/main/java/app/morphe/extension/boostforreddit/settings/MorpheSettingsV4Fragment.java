package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.util.ArrayList;
import java.util.List;

/**
 * Morphe-owned, non-Compose settings shell. Migrated pages own their controls
 * and Boost bindings; remaining pages use Boost's classic fragments.
 */
public final class MorpheSettingsV4Fragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_UI_ISSUE106_V1";
    public static final String COLOR_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_SUBTLE_COLOR_ISSUE106_V1";
    public static final String SYSTEM_BARS_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_SYSTEM_BARS_ISSUE106_V1";
    public static final String SYSTEM_BAR_OWNER_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_SYSTEM_BAR_OWNER_ISSUE106_V2";

    private static final String ARGUMENT_PAGE =
            "morphe_boost_settings_v4_page";
    private static final String PAGE_ROOT = "root";
    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";
    private static final String CLASSIC_HEADER_FRAGMENT =
            "com.rubenmayayo.reddit.ui.preferences.v2."
                    + "SettingsActivityCompat$HeaderFragment";

    private MorpheSettingsV4Theme.Tokens tokens;
    private LinearLayout dynamicContent;
    private EditText searchField;
    private List<MorpheSettingsV4Catalog.SearchItem> searchIndex;
    private String page = PAGE_ROOT;

    public MorpheSettingsV4Fragment() {
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Context context = inflater.getContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
        setHasOptionsMenu(true);

        Activity activity = hostActivity();
        if (activity != null) {
            Intent intent = activity.getIntent();
            String requestedPage = intent == null
                    ? null
                    : intent.getStringExtra(ARGUMENT_PAGE);
            if (!TextUtils.isEmpty(requestedPage)) {
                page = requestedPage;
            }
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(tokens.background);
        scrollView.setClipToPadding(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(16);
        content.setPadding(horizontal, dp(10), horizontal, dp(36));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        if (PAGE_ROOT.equals(page)) {
            buildRoot(content);
        } else {
            buildHub(content, page);
        }
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHostAndTitle();
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
        hideMenuItem(menu, "menu_search");
    }

    @Override
    public void onDestroyView() {
        dynamicContent = null;
        searchField = null;
        super.onDestroyView();
    }

    private void buildRoot(LinearLayout content) {
        addSearchField(content);
        addSpace(content, 22);

        dynamicContent = new LinearLayout(requireContext());
        dynamicContent.setOrientation(LinearLayout.VERTICAL);
        content.addView(
                dynamicContent,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        renderRootContent("");
    }

    private void buildHub(LinearLayout content, String categoryId) {
        MorpheSettingsV4Catalog.Category category =
                MorpheSettingsV4Catalog.findCategory(categoryId);
        if (category == null) {
            addSectionLabel(content, "Settings");
            addBodyText(content, "This settings category is unavailable.");
            return;
        }

        addSectionLabel(content, category.title);
        addSpace(content, 10);

        LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
        content.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (MorpheSettingsV4Catalog.Leaf leaf : category.leaves) {
            View row = createNavigationCard(
                    leaf.title,
                    leaf.summary,
                    leaf.iconName,
                    false,
                    view -> openLeaf(leaf, null)
            );
            MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
        }
    }

    private void addSearchField(LinearLayout parent) {
        Context context = requireContext();
        searchField = new EditText(context);
        searchField.setSingleLine(true);
        searchField.setTextSize(16);
        searchField.setTextColor(tokens.textPrimary);
        searchField.setHintTextColor(tokens.textSecondary);
        searchField.setHint("Search all settings");
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setPadding(dp(18), 0, dp(18), 0);
        searchField.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                MorpheSettingsV4Theme.blend(
                        tokens.surfaceContainerHigh,
                        tokens.primaryContainer,
                        tokens.dark ? 0.06f : 0.08f
                ),
                28
        ));

        Drawable searchIcon = icon("ic_search_color_24dp", tokens.primary);
        if (searchIcon != null) {
            searchIcon.setBounds(0, 0, dp(22), dp(22));
            searchField.setCompoundDrawablePadding(dp(12));
            searchField.setCompoundDrawables(searchIcon, null, null, null);
        }

        parent.addView(searchField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

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
                renderRootContent(value == null ? "" : value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
    }

    private void renderRootContent(String query) {
        if (dynamicContent == null) {
            return;
        }
        dynamicContent.removeAllViews();

        String normalized = MorpheSettingsV4Catalog.normalize(query);
        if (TextUtils.isEmpty(normalized)) {
            renderCategories();
        } else {
            renderSearchResults(normalized);
        }
    }

    private void renderCategories() {
        addSectionLabel(dynamicContent, "Morphe");
        addSpace(dynamicContent, 10);
        LinearLayout morpheGroup = MorpheSettingsV14Ui.group(requireContext());
        dynamicContent.addView(morpheGroup, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        MorpheSettingsV4Catalog.Leaf morphe = MorpheSettingsV4Catalog.morphe();
        MorpheSettingsV14Ui.addSegmentedRow(
                morpheGroup,
                createNavigationCard(
                        "Patch features",
                        "Media routing, recovery, search, and performance",
                        morphe.iconName,
                        true,
                        view -> openLeaf(morphe, null)
                ),
                tokens
        );

        renderHomeSection(
                "Look & feel",
                "appearance_layout"
        );
        renderHomeSection(
                "Reading & interaction",
                "posts_comments",
                "search_filters"
        );
        renderHomeSection(
                "Navigation & media",
                "navigation",
                "media_links"
        );
        renderHomeSection(
                "Data & account",
                "notifications",
                "data_storage",
                "account_privacy"
        );
        renderHomeSection(
                "App & support",
                "app_legacy",
                "about"
        );

        addSpace(dynamicContent, 12);
        TextView classic = MorpheSettingsV14Ui.action(
                requireContext(),
                tokens,
                "Open classic Boost settings",
                false
        );
        classic.setOnClickListener(view -> openClassicSettings());
        dynamicContent.addView(classic, wrapParams());
    }

    private void renderHomeSection(String title, String... categoryIds) {
        addSpace(dynamicContent, 24);
        addSectionLabel(dynamicContent, title);
        addSpace(dynamicContent, 10);

        LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
        dynamicContent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (String categoryId : categoryIds) {
            MorpheSettingsV4Catalog.Category category =
                    MorpheSettingsV4Catalog.findCategory(categoryId);
            if (category == null) {
                continue;
            }
            for (MorpheSettingsV4Catalog.Leaf leaf : category.leaves) {
                MorpheSettingsV14Ui.addSegmentedRow(
                        group,
                        createNavigationCard(
                                leaf.title,
                                leaf.summary,
                                leaf.iconName,
                                false,
                                view -> openLeaf(leaf, null)
                        ),
                        tokens
                );
            }
        }
    }

    private void renderSearchResults(String normalizedQuery) {
        if (searchIndex == null) {
            searchIndex = MorpheSettingsV4Catalog.buildSearchIndex(requireContext());
        }

        List<MorpheSettingsV4Catalog.SearchItem> matches = new ArrayList<>();
        for (MorpheSettingsV4Catalog.SearchItem item : searchIndex) {
            if (item.matches(normalizedQuery)) {
                matches.add(item);
            }
        }

        addSectionLabel(
                dynamicContent,
                matches.isEmpty()
                        ? "No matching settings"
                        : matches.size() + (matches.size() == 1 ? " result" : " results")
        );
        addSpace(dynamicContent, 10);

        if (matches.isEmpty()) {
            addBodyText(
                    dynamicContent,
                    "Try a setting name, category, summary, or preference key."
            );
            return;
        }

        LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
        dynamicContent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int limit = Math.min(matches.size(), 80);
        for (int index = 0; index < limit; index++) {
            MorpheSettingsV4Catalog.SearchItem item = matches.get(index);
            String secondary = item.category;
            if (!TextUtils.isEmpty(item.summary)) {
                secondary += "\n" + item.summary;
            }
            View row = createNavigationCard(
                    item.title,
                    secondary,
                    item.iconName,
                    false,
                    view -> openSearchItem(item)
            );
            MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
        }

        if (matches.size() > limit) {
            addBodyText(
                    dynamicContent,
                    "Showing the first " + limit + " results. Keep typing to narrow the search."
            );
        }
    }

    private View createNavigationCard(
            String titleValue,
            String summaryValue,
            String iconName,
            boolean highlighted,
            View.OnClickListener clickListener
    ) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        int iconBackground = highlighted
                ? MorpheSettingsV4Theme.blend(
                        tokens.surfaceContainerHigh,
                        accent.container,
                        tokens.dark ? 0.42f : 0.34f
                )
                : MorpheSettingsV4Theme.blend(
                        tokens.surfaceContainerHigh,
                        accent.container,
                        tokens.dark ? 0.34f : 0.28f
                );
        int iconColor = accent.color;

        LinearLayout card = MorpheSettingsV14Ui.baseRow(context, tokens);
        card.setMinimumHeight(dp(TextUtils.isEmpty(summaryValue) ? 56 : 72));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(clickListener);

        card.addView(createIconContainer(
                iconName,
                iconColor,
                iconBackground,
                40
        ));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        labelsParams.setMarginStart(dp(14));
        card.addView(labels, labelsParams);

        TextView title = textView(titleValue, 16, tokens.textPrimary);
        labels.addView(title);

        if (!TextUtils.isEmpty(summaryValue)) {
            TextView summary = textView(summaryValue, 14, tokens.textSecondary);
            summary.setLineSpacing(0, 1.06f);
            summary.setMaxLines(2);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams summaryParams = wrapParams();
            summaryParams.topMargin = dp(3);
            labels.addView(summary, summaryParams);
        }

        card.addView(MorpheSettingsV14Ui.chevron(context, tokens));
        return card;
    }

    private View createIconContainer(
            String iconName,
            int iconColor,
            int backgroundColor,
            int sizeDp
    ) {
        Context context = requireContext();
        FrameLayout container = new FrameLayout(context);
        container.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                backgroundColor,
                sizeDp / 2.0f
        ));

        ImageView iconView = new ImageView(context);
        Drawable drawable = icon(iconName, iconColor);
        if (drawable != null) {
            iconView.setImageDrawable(drawable);
        } else {
            iconView.setImageResource(android.R.drawable.ic_menu_preferences);
            iconView.setColorFilter(iconColor);
        }
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int iconSize = dp(24);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.CENTER
        );
        container.addView(iconView, iconParams);
        container.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return container;
    }

    private void openSearchItem(MorpheSettingsV4Catalog.SearchItem item) {
        if (!TextUtils.isEmpty(item.activityName)) {
            openActivity(item.activityName);
            return;
        }
        openFragment(item.fragmentName, item.title);
    }

    private void openLeaf(
            MorpheSettingsV4Catalog.Leaf leaf,
            String preferenceKey
    ) {
        if (!TextUtils.isEmpty(leaf.activityName)) {
            openActivity(leaf.activityName);
            return;
        }
        openFragment(leaf.fragmentName, leaf.title);
    }

    private void openFragment(String fragmentName, String title) {
        if (TextUtils.isEmpty(fragmentName)) {
            showUnavailable();
            return;
        }

        Activity activity = hostActivity();
        if (activity == null) {
            showUnavailable();
            return;
        }

        Intent intent = new Intent(activity, activity.getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        activity.startActivity(intent);
    }

    private void openActivity(String activityName) {
        try {
            Context context = requireContext();
            Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), activityName);
            context.startActivity(intent);
        } catch (Exception exception) {
            showUnavailable();
        }
    }

    private void openClassicSettings() {
        Activity activity = hostActivity();
        if (activity == null) {
            showUnavailable();
            return;
        }

        Intent intent = new Intent(activity, activity.getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, CLASSIC_HEADER_FRAGMENT);
        activity.startActivity(intent);
    }

    private void styleHostAndTitle() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }

        String title = "Settings";
        if (!PAGE_ROOT.equals(page)) {
            MorpheSettingsV4Catalog.Category category =
                    MorpheSettingsV4Catalog.findCategory(page);
            if (category != null) {
                title = category.title;
            }
        }
        activity.setTitle(title);
        styleSystemBars(activity);

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

    private void styleSystemBars(Activity activity) {
        BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(
                activity,
                tokens.background,
                tokens.dark
        );
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

    private Drawable icon(String resourceName, int color) {
        int resourceId = MorpheSettingsV4Catalog.resourceId(
                requireContext(),
                "drawable",
                resourceName
        );
        if (resourceId == 0) {
            return null;
        }
        Drawable drawable = requireContext().getDrawable(resourceId);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTintList(ColorStateList.valueOf(color));
        }
        return drawable;
    }

    private void addSectionLabel(LinearLayout parent, String value) {
        parent.addView(MorpheSettingsV14Ui.sectionLabel(
                requireContext(),
                tokens,
                value
        ));
    }

    private void addBodyText(LinearLayout parent, String value) {
        parent.addView(MorpheSettingsV14Ui.supportingText(
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
                "This settings page is unavailable in the current Boost build.",
                Toast.LENGTH_SHORT
        ).show();
    }
}
