package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

/** Morphe-owned Material 3 launcher-icon picker backed by Boost's aliases. */
public final class MorpheSettingsV4AppIconFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_APP_ICON_ISSUE106_V1";

    private static final String ALIAS_PREFIX = "com.rubenmayayo.reddit.";

    private static final IconOption[] ICONS = new IconOption[]{
            new IconOption("Default", "ic_launcher", null),
            new IconOption("Grey", "ic_launcher_grey", "grey"),
            new IconOption("Vivid", "ic_launcher_vivid", "vivid"),
            new IconOption("Metal", "ic_launcher_metal", "metal"),
            new IconOption("Yellow", "ic_launcher_yellow", "yellow"),
    };

    private MorpheSettingsV4Theme.Tokens tokens;
    private LinearLayout optionsGroup;
    private String selectedAlias;

    public MorpheSettingsV4AppIconFragment() {
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Context context = inflater.getContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
        selectedAlias = selectedAlias(context);
        setHasOptionsMenu(true);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(32));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSectionLabel(content, "Launcher icon");
        addSpace(content, 8);
        optionsGroup = addGroup(content);
        rebuildOptions();

        addSpace(content, 18);
        TextView note = textView(
                "Your launcher may take a few minutes to show a new icon.",
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
        content.addView(note, noteParams);
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

    private void rebuildOptions() {
        optionsGroup.removeAllViews();
        for (IconOption option : ICONS) {
            addIconRow(optionsGroup, option);
        }
    }

    private void addIconRow(LinearLayout group, IconOption option) {
        Context context = requireContext();
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(84));
        row.setPadding(dp(14), dp(10), dp(10), dp(10));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                0x00000000,
                0,
                accent.color
        ));
        row.setOnClickListener(view -> select(option));

        row.addView(iconContainer(option), new LinearLayout.LayoutParams(
                dp(56),
                dp(56)
        ));

        TextView title = textView(option.title, 17, tokens.textPrimary);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        titleParams.setMarginStart(dp(16));
        row.addView(title, titleParams);

        RadioButton selected = new RadioButton(context);
        selected.setClickable(false);
        selected.setFocusable(false);
        selected.setChecked(sameAlias(selectedAlias, option.alias));
        if (Build.VERSION.SDK_INT >= 21) {
            selected.setButtonTintList(new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{accent.color, tokens.textSecondary}
            ));
        }
        row.addView(selected, new LinearLayout.LayoutParams(dp(48), dp(48)));

        if (group.getChildCount() > 0) {
            addDivider(group);
        }
        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private FrameLayout iconContainer(IconOption option) {
        Context context = requireContext();
        FrameLayout container = new FrameLayout(context);
        container.setBackground(MorpheSettingsV4Theme.rounded(
                context,
                tokens.surfaceContainerHigh,
                18
        ));
        container.setClipToOutline(true);

        ImageView icon = new ImageView(context);
        Drawable drawable = iconDrawable(option.resourceName);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        ));
        return container;
    }

    private Drawable iconDrawable(String resourceName) {
        int resourceId = MorpheSettingsV4Catalog.resourceId(
                requireContext(),
                "mipmap",
                resourceName
        );
        if (resourceId == 0) {
            return null;
        }
        return requireContext().getResources().getDrawable(resourceId);
    }

    private void select(IconOption option) {
        Context context = requireContext();
        try {
            applyIconSelection(context, option.alias);
            selectedAlias = option.alias;
            rebuildOptions();
            Toast.makeText(
                    context,
                    option.alias == null
                            ? "Default app icon selected"
                            : option.title + " app icon selected",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (Throwable ignored) {
            Toast.makeText(
                    context,
                    "This app icon is unavailable in the current Boost build.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    static void applyIconSelection(Context context, String selectedAlias) {
        PackageManager packageManager = context.getPackageManager();
        for (IconOption candidate : ICONS) {
            if (candidate.alias == null) {
                continue;
            }
            ComponentName component = aliasComponent(context, candidate.alias);
            int state = candidate.alias.equals(selectedAlias)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            packageManager.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP
            );
        }
    }

    static String selectedAlias(Context context) {
        PackageManager packageManager = context.getPackageManager();
        for (IconOption option : ICONS) {
            if (option.alias == null) {
                continue;
            }
            int state = packageManager.getComponentEnabledSetting(
                    aliasComponent(context, option.alias)
            );
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return option.alias;
            }
        }
        return null;
    }

    private static ComponentName aliasComponent(Context context, String alias) {
        return new ComponentName(
                context.getPackageName(),
                ALIAS_PREFIX + alias
        );
    }

    private boolean sameAlias(String first, String second) {
        return first == null ? second == null : first.equals(second);
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

    private void addDivider(LinearLayout group) {
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

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("App icon");
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

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(requireContext());
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(float value) {
        return MorpheSettingsV4Theme.dp(requireContext(), value);
    }

    private static final class IconOption {
        final String title;
        final String resourceName;
        final String alias;

        IconOption(String title, String resourceName, String alias) {
            this.title = title;
            this.resourceName = resourceName;
            this.alias = alias;
        }
    }
}
