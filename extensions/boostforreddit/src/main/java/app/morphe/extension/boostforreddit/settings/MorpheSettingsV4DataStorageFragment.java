package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Method;

/** Morphe-owned data and storage controls backed by Boost's canonical keys. */
public final class MorpheSettingsV4DataStorageFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_DATA_STORAGE_ISSUE106_V2";

    private static final String EXTRA_SHOW_FRAGMENT =
            "extra_show_fragment";

    private static final String KEY_DOWNLOAD_FOLDERS =
            "pref_download_folders";
    private static final String KEY_REDUCE_MOBILE = "pref_reduce_mobile";
    private static final String KEY_REDUCE_WIFI = "pref_reduce_wifi";
    private static final String KEY_AUTOPLAY = "pref_autoplay_cards";
    private static final String KEY_VIDEO_QUALITY = "pref_video_quality";
    private static final String KEY_VIDEO_QUALITY_MIN =
            "pref_video_quality_min";
    private static final String KEY_VIDEO_QUALITY_MAX =
            "pref_video_quality_max";
    private static final String KEY_LOAD_IMAGES = "pref_load_images";
    private static final String KEY_CACHE_CURRENT_SIZE =
            "pref_cache_current_size";
    private static final String KEY_CACHE_MAX_SIZE = "pref_cache_max_size";

    private static final String[] AUTOPLAY_TITLES = new String[]{
            "Always",
            "When on Wi-Fi",
            "Never",
    };
    private static final String[] AUTOPLAY_VALUES = new String[]{
            "0", "1", "2",
    };
    private static final String[] VIDEO_QUALITY_TITLES = new String[]{
            "Prefer lower size",
            "Prefer best quality",
            "Auto based on data saving settings",
    };
    private static final String[] VIDEO_QUALITY_VALUES = new String[]{
            "0", "1", "2",
    };
    private static final String[] RESOLUTION_TITLES = new String[]{
            "240p", "360p", "480p", "720p (HD)", "1080p (HD)",
    };
    private static final String[] RESOLUTION_VALUES = new String[]{
            "240", "360", "480", "720", "1080",
    };
    private static final String[] IMAGE_TITLES = new String[]{
            "Enabled",
            "Disabled",
            "Auto based on network",
    };
    private static final String[] IMAGE_VALUES = new String[]{
            "0", "1", "2",
    };
    private static final String[] CACHE_SIZE_TITLES = new String[]{
            "128MB", "256MB", "512MB", "1GB", "2GB",
    };
    private static final String[] CACHE_SIZE_VALUES = new String[]{
            "128", "256", "512", "1024", "2048",
    };

    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences preferences;
    private TextView autoplaySummary;
    private TextView videoQualitySummary;
    private TextView videoQualityMinSummary;
    private TextView videoQualityMaxSummary;
    private TextView loadImagesSummary;
    private TextView cacheCurrentSizeSummary;
    private TextView cacheMaxSizeSummary;
    private LinearLayout videoQualityMinRow;
    private LinearLayout videoQualityMaxRow;
    private boolean autoplayWasDisabled;

    public MorpheSettingsV4DataStorageFragment() {
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
        autoplayWasDisabled = "2".equals(preferenceString(KEY_AUTOPLAY, "1"));
        setHasOptionsMenu(true);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(16);
        content.setPadding(horizontal, dp(10), horizontal, dp(32));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        addSectionLabel(content, "Downloads");
        addSpace(content, 8);
        LinearLayout downloadsGroup = addGroup(content);
        addNavigationRow(
                downloadsGroup,
                "Configure downloads",
                "Change download folder location and folder organization",
                true,
                view -> openDownloadFolders()
        );

        addSpace(content, 24);
        addSectionLabel(content, "Data saver");
        addSpace(content, 8);
        LinearLayout dataSaverGroup = addGroup(content);
        addSwitchRow(
                dataSaverGroup,
                "Mobile data saver",
                "Load lower-size media on mobile data",
                preferences.getBoolean(KEY_REDUCE_MOBILE, true),
                checked -> {
                    preferences.edit().putBoolean(KEY_REDUCE_MOBILE, checked).apply();
                    updateVideoQualityRows();
                }
        );
        addSwitchRow(
                dataSaverGroup,
                "Wi-Fi data saver",
                "Load lower-size media on Wi-Fi",
                preferences.getBoolean(KEY_REDUCE_WIFI, false),
                checked -> {
                    preferences.edit().putBoolean(KEY_REDUCE_WIFI, checked).apply();
                    updateVideoQualityRows();
                }
        );

        addSpace(content, 24);
        addSectionLabel(content, "Videos");
        addSpace(content, 8);
        LinearLayout videosGroup = addGroup(content);
        LinearLayout autoplayRow = addNavigationRow(
                videosGroup,
                "Autoplay videos",
                titleFor(
                        AUTOPLAY_TITLES,
                        AUTOPLAY_VALUES,
                        preferenceString(KEY_AUTOPLAY, "1")
                ),
                true,
                view -> showChoiceDialog(
                        "Autoplay videos",
                        KEY_AUTOPLAY,
                        AUTOPLAY_TITLES,
                        AUTOPLAY_VALUES,
                        "1",
                        autoplaySummary,
                        this::onAutoplayChanged
                )
        );
        autoplaySummary = rowSummary(autoplayRow);

        LinearLayout qualityRow = addNavigationRow(
                videosGroup,
                "Video quality",
                titleFor(
                        VIDEO_QUALITY_TITLES,
                        VIDEO_QUALITY_VALUES,
                        preferenceString(KEY_VIDEO_QUALITY, "0")
                ),
                true,
                view -> showChoiceDialog(
                        "Video quality",
                        KEY_VIDEO_QUALITY,
                        VIDEO_QUALITY_TITLES,
                        VIDEO_QUALITY_VALUES,
                        "0",
                        videoQualitySummary,
                        this::updateVideoQualityRows
                )
        );
        videoQualitySummary = rowSummary(qualityRow);

        videoQualityMinRow = addNavigationRow(
                videosGroup,
                "Minimum quality",
                titleFor(
                        RESOLUTION_TITLES,
                        RESOLUTION_VALUES,
                        preferenceString(KEY_VIDEO_QUALITY_MIN, "480")
                ),
                true,
                view -> showChoiceDialog(
                        "Minimum quality",
                        KEY_VIDEO_QUALITY_MIN,
                        RESOLUTION_TITLES,
                        RESOLUTION_VALUES,
                        "480",
                        videoQualityMinSummary,
                        null
                )
        );
        videoQualityMinSummary = rowSummary(videoQualityMinRow);

        videoQualityMaxRow = addNavigationRow(
                videosGroup,
                "Maximum quality",
                titleFor(
                        RESOLUTION_TITLES,
                        RESOLUTION_VALUES,
                        preferenceString(KEY_VIDEO_QUALITY_MAX, "1080")
                ),
                true,
                view -> showChoiceDialog(
                        "Maximum quality",
                        KEY_VIDEO_QUALITY_MAX,
                        RESOLUTION_TITLES,
                        RESOLUTION_VALUES,
                        "1080",
                        videoQualityMaxSummary,
                        null
                )
        );
        videoQualityMaxSummary = rowSummary(videoQualityMaxRow);
        updateVideoQualityRows();

        addSpace(content, 24);
        addSectionLabel(content, "Images");
        addSpace(content, 8);
        LinearLayout imagesGroup = addGroup(content);
        LinearLayout imagesRow = addNavigationRow(
                imagesGroup,
                "Load images",
                titleFor(
                        IMAGE_TITLES,
                        IMAGE_VALUES,
                        preferenceString(KEY_LOAD_IMAGES, "0")
                ),
                true,
                view -> showChoiceDialog(
                        "Load images",
                        KEY_LOAD_IMAGES,
                        IMAGE_TITLES,
                        IMAGE_VALUES,
                        "0",
                        loadImagesSummary,
                        null
                )
        );
        loadImagesSummary = rowSummary(imagesRow);

        addSpace(content, 24);
        addSectionLabel(content, "Cache");
        addSpace(content, 8);
        LinearLayout cacheGroup = addGroup(content);
        LinearLayout clearCacheRow = addNavigationRow(
                cacheGroup,
                "Clear cache",
                "Delete images and videos from cache",
                true,
                view -> clearCache()
        );
        cacheCurrentSizeSummary = rowSummary(clearCacheRow);

        LinearLayout cacheSizeRow = addNavigationRow(
                cacheGroup,
                "Maximum cache size",
                titleFor(
                        CACHE_SIZE_TITLES,
                        CACHE_SIZE_VALUES,
                        preferenceString(KEY_CACHE_MAX_SIZE, "128")
                ),
                true,
                view -> showChoiceDialog(
                        "Maximum cache size",
                        KEY_CACHE_MAX_SIZE,
                        CACHE_SIZE_TITLES,
                        CACHE_SIZE_VALUES,
                        "128",
                        cacheMaxSizeSummary,
                        this::resizeCache
                )
        );
        cacheMaxSizeSummary = rowSummary(cacheSizeRow);
        refreshCacheSummary();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHost();
        refreshCacheSummary();
        updateVideoQualityRows();
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

    private LinearLayout addNavigationRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            boolean enabled,
            View.OnClickListener listener
    ) {
        LinearLayout row = baseRow(requireContext());
        row.addView(createLabels(titleValue, summaryValue), labelsParams());

        row.addView(MorpheSettingsV14Ui.chevron(requireContext(), tokens));
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
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = baseRow(requireContext());
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
    }

    private void showChoiceDialog(
            String titleValue,
            String key,
            String[] titles,
            String[] values,
            String defaultValue,
            TextView summary,
            Runnable afterChange
    ) {
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

        TextView heading = textView(titleValue, 20, tokens.textPrimary);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams headingParams = wrapParams();
        headingParams.setMarginStart(dp(8));
        headingParams.bottomMargin = dp(8);
        content.addView(heading, headingParams);

        String selected = preferenceString(key, defaultValue);
        LinearLayout choiceGroup = MorpheSettingsV14Ui.group(context);
        content.addView(choiceGroup, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (int index = 0; index < titles.length; index++) {
            final int choice = index;
            MorpheSettingsV14Ui.ChoiceRow row =
                    MorpheSettingsV14Ui.choiceRow(
                    context,
                    tokens,
                    titles[index],
                    "",
                    values[index].equals(selected)
            );
            row.setOnClickListener(view -> {
                String oldValue = preferenceString(key, defaultValue);
                preferences.edit().putString(key, values[choice]).apply();
                if (summary != null) {
                    summary.setText(titles[choice]);
                }
                if (KEY_AUTOPLAY.equals(key)) {
                    onAutoplayChoice(oldValue, values[choice]);
                }
                if (afterChange != null) {
                    afterChange.run();
                }
                dialog.dismiss();
            });
            MorpheSettingsV14Ui.addSegmentedRow(choiceGroup, row, tokens);
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

    private void onAutoplayChanged() {
        autoplayWasDisabled = "2".equals(preferenceString(KEY_AUTOPLAY, "1"));
    }

    private void onAutoplayChoice(String oldValue, String newValue) {
        if (autoplayWasDisabled
                && "2".equals(oldValue)
                && !"2".equals(newValue)) {
            Toast.makeText(
                    requireContext(),
                    "Changes will be applied on app restart",
                    Toast.LENGTH_SHORT
            ).show();
        }
        autoplayWasDisabled = "2".equals(newValue);
    }

    private void updateVideoQualityRows() {
        if (preferences == null) {
            return;
        }
        String quality = preferenceString(KEY_VIDEO_QUALITY, "0");
        boolean minimumEnabled;
        boolean maximumEnabled;
        if ("1".equals(quality)) {
            minimumEnabled = false;
            maximumEnabled = true;
        } else if ("2".equals(quality)) {
            boolean mobile = preferences.getBoolean(KEY_REDUCE_MOBILE, true);
            boolean wifi = preferences.getBoolean(KEY_REDUCE_WIFI, false);
            minimumEnabled = mobile || wifi;
            maximumEnabled = !(mobile && wifi);
        } else {
            minimumEnabled = true;
            maximumEnabled = false;
        }
        setRowEnabled(videoQualityMinRow, minimumEnabled);
        setRowEnabled(videoQualityMaxRow, maximumEnabled);
    }

    private void openDownloadFolders() {
        Activity activity = hostActivity();
        if (activity == null) {
            showUnavailable();
            return;
        }
        try {
            Intent intent = new Intent(activity, activity.getClass());
            intent.putExtra(
                    EXTRA_SHOW_FRAGMENT,
                    MorpheSettingsV4Catalog.V4_DOWNLOADS_FRAGMENT
            );
            activity.startActivity(intent);
        } catch (Throwable ignored) {
            showUnavailable();
        }
    }

    private void clearCache() {
        if (!invokeStaticVoid("qb.a", "a", requireContext())) {
            showUnavailable();
            return;
        }
        refreshCacheSummary();
        if (cacheCurrentSizeSummary != null) {
            cacheCurrentSizeSummary.postDelayed(this::refreshCacheSummary, 350);
        }
    }

    private void resizeCache() {
        invokeStaticVoid("qb.b", "d", requireContext());
    }

    private void refreshCacheSummary() {
        if (cacheCurrentSizeSummary == null || getContext() == null) {
            return;
        }
        Object result = invokeStatic("qb.a", "b", requireContext());
        if (result instanceof String && !TextUtils.isEmpty((String) result)) {
            cacheCurrentSizeSummary.setText((String) result);
        } else {
            cacheCurrentSizeSummary.setText(
                    "Delete images and videos from cache"
            );
        }
    }

    private Object invokeStatic(
            String className,
            String methodName,
            Context context
    ) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, Context.class);
            method.setAccessible(true);
            return method.invoke(null, context);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean invokeStaticVoid(
            String className,
            String methodName,
            Context context
    ) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName, Context.class);
            method.setAccessible(true);
            method.invoke(null, context);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String preferenceString(String key, String defaultValue) {
        try {
            return preferences.getString(key, defaultValue);
        } catch (ClassCastException ignored) {
            return defaultValue;
        }
    }

    static String titleFor(
            String[] titles,
            String[] values,
            String value
    ) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(value)) {
                return titles[index];
            }
        }
        return titles[0];
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Data storage");
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
        return MorpheSettingsV14Ui.baseRow(context, tokens);
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

    private void setRowEnabled(View row, boolean enabled) {
        if (row == null) {
            return;
        }
        row.setEnabled(enabled);
        row.setClickable(enabled);
        row.setFocusable(enabled);
        row.setAlpha(enabled ? 1.0f : 0.44f);
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

    private void showUnavailable() {
        Toast.makeText(
                requireContext(),
                "This data and storage control is unavailable in the current Boost build.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private interface CheckedChange {
        void onChanged(boolean checked);
    }
}
