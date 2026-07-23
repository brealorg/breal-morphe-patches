package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Method;

/** Morphe-owned M3 surface for Boost's complete download-folder contract. */
public final class MorpheSettingsV4DownloadsFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_DOWNLOADS_ISSUE106_V1";

    private static final int REQUEST_FOLDER = 0x4ee9;
    private static final int FOLDER_DEFAULT = 0;
    private static final int FOLDER_IMAGES = 1;
    private static final int FOLDER_VIDEOS = 2;
    private static final int FOLDER_GIFS = 3;

    private static final String[] FOLDER_KEYS = new String[]{
            "pref_download_folder_default",
            "pref_download_folder_img",
            "pref_download_folder_mp4",
            "pref_download_folder_gif",
    };
    private static final String KEY_COMMUNITY_SUBFOLDERS =
            "pref_download_folder_per_subreddit";

    private final FolderRow[] folderRows = new FolderRow[4];
    private MorpheSettingsV4Theme.Tokens tokens;
    private SharedPreferences preferences;
    private int pendingFolderType = -1;

    public MorpheSettingsV4DownloadsFragment() {
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
        if (savedInstanceState != null) {
            pendingFolderType = savedInstanceState.getInt(
                    "morphe_pending_folder_type",
                    -1
            );
        }
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

        addSectionLabel(content, "Download folders");
        addSpace(content, 8);
        LinearLayout foldersGroup = addGroup(content);
        folderRows[FOLDER_DEFAULT] = addFolderRow(
                foldersGroup,
                "Default folder",
                FOLDER_DEFAULT
        );
        folderRows[FOLDER_IMAGES] = addFolderRow(
                foldersGroup,
                "Images",
                FOLDER_IMAGES
        );
        folderRows[FOLDER_VIDEOS] = addFolderRow(
                foldersGroup,
                "Videos",
                FOLDER_VIDEOS
        );
        folderRows[FOLDER_GIFS] = addFolderRow(
                foldersGroup,
                "GIFs",
                FOLDER_GIFS
        );
        applyBoostFolderVisibility();

        addSpace(content, 24);
        addSectionLabel(content, "Organization");
        addSpace(content, 8);
        LinearLayout organizationGroup = addGroup(content);
        addSwitchRow(
                organizationGroup,
                "Community subfolders",
                "Organize downloaded media into community folders",
                communitySubfoldersEnabled(),
                this::setCommunitySubfoldersEnabled
        );

        refreshFolderRows();
        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHost();
        refreshFolderRows();
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
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt("morphe_pending_folder_type", pendingFolderType);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        hideMenuItem(menu, "action_generic_search");
        hideMenuItem(menu, "action_search");
        hideMenuItem(menu, "search");
    }

    @Override
    public void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FOLDER
                || resultCode != Activity.RESULT_OK
                || data == null
                || data.getData() == null
                || pendingFolderType < FOLDER_DEFAULT
                || pendingFolderType > FOLDER_GIFS) {
            return;
        }

        Uri folder = data.getData();
        int permissionFlags = data.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (permissionFlags == 0) {
            permissionFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    folder,
                    permissionFlags
            );
        } catch (SecurityException ignored) {
            // Some document providers grant access without a persistable flag.
        }

        int folderType = pendingFolderType;
        pendingFolderType = -1;
        String value = folder.toString();
        if (!invokeBoostVoid(
                "D6",
                new Class<?>[]{int.class, String.class},
                folderType,
                value
        )) {
            preferences.edit().putString(FOLDER_KEYS[folderType], value).apply();
        }
        refreshFolderRows();
    }

    private FolderRow addFolderRow(
            LinearLayout parent,
            String titleValue,
            int folderType
    ) {
        MorpheSettingsV4Theme.Accent accent = tokens.navigationAccent();
        LinearLayout row = baseRow(requireContext());
        LinearLayout labels = createLabels(titleValue, "Not set");
        row.addView(labels, labelsParams());

        TextView reset = textView("Reset", 13, accent.color);
        reset.setTypeface(Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
        ));
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(10), dp(8), dp(10), dp(8));
        reset.setBackground(MorpheSettingsV4Theme.interactive(
                requireContext(),
                Color.TRANSPARENT,
                16,
                accent.color
        ));
        reset.setOnClickListener(view -> resetFolder(folderType));
        row.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView chevron = textView("›", 28, accent.color);
        chevron.setGravity(Gravity.CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(44)));
        row.setOnClickListener(view -> chooseFolder(folderType));
        addGroupedRow(parent, row);

        TextView summary = labels.getChildCount() > 1
                && labels.getChildAt(1) instanceof TextView
                ? (TextView) labels.getChildAt(1)
                : null;
        return new FolderRow(row, summary, reset);
    }

    private void addSwitchRow(
            LinearLayout parent,
            String titleValue,
            String summaryValue,
            boolean checked,
            CheckedChange change
    ) {
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

    private void chooseFolder(int folderType) {
        pendingFolderType = folderType;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        try {
            startActivityForResult(intent, REQUEST_FOLDER);
        } catch (Throwable ignored) {
            pendingFolderType = -1;
            showUnavailable();
        }
    }

    private void resetFolder(int folderType) {
        if (!invokeBoostVoid(
                "D6",
                new Class<?>[]{int.class, String.class},
                folderType,
                null
        )) {
            preferences.edit().remove(FOLDER_KEYS[folderType]).apply();
        }
        refreshFolderRows();
    }

    private void refreshFolderRows() {
        if (preferences == null) {
            return;
        }
        for (int folderType = FOLDER_DEFAULT;
                folderType <= FOLDER_GIFS;
                folderType++) {
            FolderRow folderRow = folderRows[folderType];
            if (folderRow == null) {
                continue;
            }
            if (folderRow.summary != null) {
                folderRow.summary.setText(folderSummary(folderType));
            }
            folderRow.reset.setVisibility(
                    hasCustomFolder(folderType) ? View.VISIBLE : View.GONE
            );
        }
    }

    private String folderSummary(int folderType) {
        Object value = invokeBoost(
                "d0",
                new Class<?>[]{int.class},
                folderType
        );
        if (!(value instanceof String) || TextUtils.isEmpty((String) value)) {
            value = preferences.getString(FOLDER_KEYS[folderType], "");
        }
        if (!(value instanceof String) || TextUtils.isEmpty((String) value)) {
            return "Not set";
        }
        return friendlyFolder((String) value);
    }

    private boolean hasCustomFolder(int folderType) {
        Object value = invokeBoost(
                "O",
                new Class<?>[]{int.class},
                folderType
        );
        return value instanceof Boolean
                ? (Boolean) value
                : preferences.contains(FOLDER_KEYS[folderType]);
    }

    private boolean communitySubfoldersEnabled() {
        Object value = invokeBoost("l2", new Class<?>[0]);
        return value instanceof Boolean
                ? (Boolean) value
                : preferences.getBoolean(KEY_COMMUNITY_SUBFOLDERS, false);
    }

    private void setCommunitySubfoldersEnabled(boolean enabled) {
        if (!invokeBoostVoid(
                "E6",
                new Class<?>[]{boolean.class},
                enabled
        )) {
            preferences.edit()
                    .putBoolean(KEY_COMMUNITY_SUBFOLDERS, enabled)
                    .apply();
        }
    }

    private void applyBoostFolderVisibility() {
        boolean storageAccessFramework = invokeStaticBoolean(
                "sb.a",
                "f",
                true
        );
        boolean typedFolders = storageAccessFramework && invokeStaticBoolean(
                "sb.a",
                "g",
                true
        );
        setFolderRowVisible(FOLDER_DEFAULT, storageAccessFramework);
        setFolderRowVisible(FOLDER_IMAGES, typedFolders);
        setFolderRowVisible(FOLDER_VIDEOS, typedFolders);
        setFolderRowVisible(FOLDER_GIFS, typedFolders);
    }

    private void setFolderRowVisible(int folderType, boolean visible) {
        FolderRow folderRow = folderRows[folderType];
        if (folderRow != null) {
            folderRow.row.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private Object invokeBoost(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        try {
            Class<?> type = Class.forName("id.b");
            Method instanceMethod = type.getDeclaredMethod("v0");
            instanceMethod.setAccessible(true);
            Object instance = instanceMethod.invoke(null);
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(instance, arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean invokeBoostVoid(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        try {
            Class<?> type = Class.forName("id.b");
            Method instanceMethod = type.getDeclaredMethod("v0");
            instanceMethod.setAccessible(true);
            Object instance = instanceMethod.invoke(null);
            Method method = type.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(instance, arguments);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean invokeStaticBoolean(
            String className,
            String methodName,
            boolean fallback
    ) {
        try {
            Class<?> type = Class.forName(className);
            Method method = type.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(null);
            return value instanceof Boolean ? (Boolean) value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String friendlyFolder(String rawValue) {
        String value = Uri.decode(rawValue);
        int tree = value.indexOf("/tree/");
        if (tree >= 0 && tree + 6 < value.length()) {
            value = value.substring(tree + 6);
        }
        if (value.startsWith("primary:")) {
            value = "Internal storage / " + value.substring(8);
        }
        value = value.replace(':', '/');
        return value.replace("/", " / ").replaceAll("\\s+", " ").trim();
    }

    private LinearLayout addGroup(LinearLayout parent) {
        LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
        parent.addView(group, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return group;
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

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }
        activity.setTitle("Configure downloads");
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
                "Folder selection is unavailable in the current Boost build.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private static final class FolderRow {
        final View row;
        final TextView summary;
        final TextView reset;

        FolderRow(View row, TextView summary, TextView reset) {
            this.row = row;
            this.summary = summary;
            this.reset = reset;
        }
    }

    private interface CheckedChange {
        void onChanged(boolean checked);
    }
}
