package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

/** Complete Settings V5 overview. */
public final class MorpheSettingsV5RootFragment extends Fragment {
    public static final String ROOT_OVERVIEW_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_ROOT_OVERVIEW_ISSUE121_V1";
    public static final String COMPLETENESS_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_COMPLETE_ISSUE121_V1";
    public static final String HIDDEN_ROOT_WAVE_MARKER =
            "MORPHE_BOOST_SETTINGS_V5_ROOT_HIDDEN_WAVE_ISSUE121_V1";
    public static final String CLASSIC_FALLBACK_MARKER =
            "MORPHE_V5_ROOT_CLASSIC_FALLBACK";

    private static final String PAGE_ROOT = "v5/root";
    private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";
    private static final String CLASSIC_HEADER_FRAGMENT =
            "com.rubenmayayo.reddit.ui.preferences.v2."
                    + "SettingsActivityCompat$HeaderFragment";

    private MorpheSettingsV4Theme.Tokens tokens;
    private String pageId = PAGE_ROOT;

    public MorpheSettingsV5RootFragment() {
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
        resolvePageId();

        if (!PAGE_ROOT.equals(pageId)) {
            throw new IllegalArgumentException(
                    "V5 root renderer cannot open " + pageId
            );
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(36));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        buildOverview(content);
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
        if (!PAGE_ROOT.equals(pageId)) {
            MorpheSettingsV5Search.prepareMenu(this, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MorpheSettingsV5Search.handleMenuItem(this, item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void buildOverview(LinearLayout content) {
        addSearchLauncher(content);
        addSpace(content, 18);
        renderDestinations(
                content,
                MorpheSettingsV5Registry.childrenFor(PAGE_ROOT),
                true
        );
        addSpace(content, 18);

        TextView classic = MorpheSettingsV14Ui.action(
                requireContext(),
                tokens,
                "Open classic Boost settings",
                false
        );
        classic.setContentDescription("Open classic Boost settings");
        classic.setOnClickListener(view -> openClassicSettings());
        content.addView(
                classic,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void addSearchLauncher(LinearLayout parent) {
        Context context = requireContext();
        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(tokens.textPrimary);
        search.setHintTextColor(tokens.textSecondary);
        search.setHint("Search all settings");
        search.setFocusable(false);
        search.setCursorVisible(false);
        search.setClickable(true);
        search.setContentDescription("Search all settings");
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(MorpheSettingsV4Theme.rounded(
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
            search.setCompoundDrawablePadding(dp(12));
            search.setCompoundDrawables(searchIcon, null, null, null);
        }

        search.setOnClickListener(
                view -> MorpheSettingsV5Search.openGlobalSearch(this)
        );
        parent.addView(
                search,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(56)
                )
        );
    }

    private void renderDestinations(
            LinearLayout content,
            String[] children,
            boolean rootOverview
    ) {
        if (children.length == 0) {
            throw new IllegalStateException(
                    "V5 root page has no children: " + pageId
            );
        }

        LinearLayout list = MorpheSettingsV14Ui.standardList(requireContext());
        content.addView(
                list,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        for (String childId : children) {
            String supporting = rootOverview
                    ? summaryFor(childId)
                    : MorpheSettingsV5Registry.introFor(childId);
            LinearLayout row = MorpheSettingsV14Ui.standardListRow(
                    requireContext(),
                    tokens,
                    !TextUtils.isEmpty(supporting)
            );

            String iconName = iconFor(childId);
            if (!TextUtils.isEmpty(iconName)) {
                row.addView(createLeadingIcon(iconName));
            }

            LinearLayout labels = MorpheSettingsV14Ui.standardListLabels(
                    requireContext(),
                    tokens,
                    MorpheSettingsV5Registry.titleFor(childId),
                    supporting
            );
            LinearLayout.LayoutParams labelParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    );
            if (!TextUtils.isEmpty(iconName)) {
                labelParams.setMarginStart(dp(16));
            }
            row.addView(labels, labelParams);
            row.addView(MorpheSettingsV14Ui.chevron(
                    requireContext(),
                    tokens
            ));
            row.setOnClickListener(
                    view -> MorpheSettingsV5Navigation.openPage(this, childId)
            );
            MorpheSettingsV14Ui.addStandardListRow(list, row, tokens);
        }
    }

    private View createLeadingIcon(String iconName) {
        Context context = requireContext();
        FrameLayout slot = new FrameLayout(context);
        ImageView image = new ImageView(context);
        Drawable drawable = icon(
                iconName,
                tokens.navigationAccent().color
        );
        if (drawable != null) {
            image.setImageDrawable(drawable);
        } else {
            image.setImageResource(android.R.drawable.ic_menu_preferences);
            image.setColorFilter(tokens.navigationAccent().color);
        }
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        slot.addView(
                image,
                new FrameLayout.LayoutParams(
                        dp(24),
                        dp(24),
                        Gravity.CENTER
                )
        );
        slot.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return slot;
    }

    private String summaryFor(String targetPageId) {
        switch (targetPageId) {
            case "v5/morphe":
                return "Configurable Morphe features and Settings behavior";
            case "v5/appearance":
                return "Themes, layout, typography, and visual presentation";
            case "v5/reading_and_interaction":
                return "Posts, comments, feeds, search, and composing";
            case "v5/navigation":
                return "Back behavior, bars, drawer, and gestures";
            case "v5/media":
                return "Playback, previews, links, and downloads";
            case "v5/notifications_and_account":
                return "Inbox, notifications, history, recovery, and account";
            case "v5/data_and_app":
                return "Storage, compatibility, backup, and app information";
            default:
                return MorpheSettingsV5Registry.introFor(targetPageId);
        }
    }

    private String iconFor(String targetPageId) {
        switch (targetPageId) {
            case "v5/morphe":
                return "ic_puzzle_24dp";
            case "v5/appearance":
                return "ic_color_lens_24dp";
            case "v5/reading_and_interaction":
                return "ic_post_24dp";
            case "v5/navigation":
                return "ic_toolbar_24dp";
            case "v5/media":
                return "ic_photo_outline_24dp";
            case "v5/notifications_and_account":
                return "ic_notifications_black_24dp";
            case "v5/data_and_app":
                return "ic_settings_24dp";
            default:
                return "";
        }
    }

    private void openClassicSettings() {
        Activity activity = hostActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(activity, activity.getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, CLASSIC_HEADER_FRAGMENT);
        activity.startActivity(intent);
    }

    private void resolvePageId() {
        Bundle arguments = getArguments();
        if (arguments != null) {
            String requested = arguments.getString(
                    MorpheSettingsV5Registry.EXTRA_PAGE_ID
            );
            if (!TextUtils.isEmpty(requested)) {
                pageId = requested;
                return;
            }
        }

        Activity activity = hostActivity();
        Intent intent = activity == null ? null : activity.getIntent();
        String requested = intent == null
                ? null
                : intent.getStringExtra(MorpheSettingsV5Registry.EXTRA_PAGE_ID);
        if (!TextUtils.isEmpty(requested)) {
            pageId = requested;
        }
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }

        activity.setTitle("Settings");
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
        View toolbar = toolbarId == 0 ? null : activity.findViewById(toolbarId);
        if (toolbar != null) {
            toolbar.setBackgroundColor(tokens.background);
            toolbar.setElevation(0);
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

    private Activity hostActivity() {
        Context context = getContext();
        return context instanceof Activity ? (Activity) context : null;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        parent.addView(
                new View(requireContext()),
                new LinearLayout.LayoutParams(1, dp(heightDp))
        );
    }

    private int dp(float value) {
        return MorpheSettingsV4Theme.dp(requireContext(), value);
    }
}
