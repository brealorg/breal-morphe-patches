package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Morphe-native destinations for the remaining Boost v2 preference pages.
 *
 * <p>The page model is read from Boost's packaged XML so titles, summaries,
 * defaults, arrays, dependencies, and localization remain canonical. Unlike
 * the discarded V8 experiment, no Boost Fragment is created as a hidden
 * delegate. Morphe owns every visible control and writes the canonical Boost
 * preference keys directly. Special actions are bound explicitly below.</p>
 */
public final class MorpheSettingsV4NativePages {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_NATIVE_REST_ISSUE106_V1";
    public static final String NO_DELEGATE_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_NATIVE_NO_HIDDEN_DELEGATE_ISSUE106_V1";
    public static final String SPECIAL_ACTION_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_NATIVE_SPECIAL_ACTIONS_ISSUE106_V1";
    public static final String NESTED_EDITOR_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_NATIVE_NESTED_EDITORS_ISSUE106_V10";
    public static final String LEGACY_PRUNING_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_LEGACY_PRUNING_ISSUE106_V10";
    public static final String M3_SORT_PILOT_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_M3_SORT_PILOT_ISSUE106_V11";
    public static final String MATERIAL_EDITOR_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_MATERIAL_EDITOR_PAGES_ISSUE106_V12";
    public static final String MATERIAL_ABI_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_MATERIAL_ABI_SAFE_ISSUE106_V12";
    public static final String EDITOR_BACK_STACK_MARKER =
            "MORPHE_BOOST_SETTINGS_V4_FRAGMENT_BACK_STACK_ISSUE106_V13";
    public static final String V14_EDITOR_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_EDITOR_SYSTEM_ISSUE106_V1";
    public static final String V14_NO_COMPILE_ONLY_UI_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_NO_COMPILE_ONLY_UI_ABI_ISSUE106_V1";

    private static final String PREFIX =
            "app.morphe.extension.boostforreddit.settings."
                    + "MorpheSettingsV4NativePages$";

    private MorpheSettingsV4NativePages() {
    }

    public static String nativeDestination(String legacyDestination) {
        if (TextUtils.isEmpty(legacyDestination)) {
            return legacyDestination;
        }
        if (legacyDestination.endsWith("PreferenceFragmentPostsCompat")) {
            return PREFIX + "Posts";
        }
        if (legacyDestination.endsWith("PreferenceFragmentCommentsCompat")) {
            return PREFIX + "Comments";
        }
        if (legacyDestination.endsWith("PreferenceFragmentBottomNavigationCompat")) {
            return PREFIX + "BottomNavigation";
        }
        if (legacyDestination.endsWith("PreferenceFragmentDrawerCompat")) {
            return PREFIX + "Drawer";
        }
        if (legacyDestination.endsWith("PreferenceFragmentMediaCompat")) {
            return PREFIX + "Media";
        }
        if (legacyDestination.endsWith("PreferenceFragmentLinksCompat")) {
            return PREFIX + "Links";
        }
        if (legacyDestination.endsWith("PreferenceFragmentSearchCompat")) {
            return PREFIX + "Search";
        }
        if (legacyDestination.endsWith("PreferenceFragmentFiltersCompat")) {
            return PREFIX + "Filters";
        }
        if (legacyDestination.endsWith("PreferenceFragmentMessagesCompat")) {
            return PREFIX + "Messages";
        }
        if (legacyDestination.endsWith("PreferenceFragmentPrivacyCompat")) {
            return PREFIX + "Privacy";
        }
        if (legacyDestination.endsWith("PreferenceFragmentGeneralCompat")) {
            return PREFIX + "General";
        }
        if (legacyDestination.endsWith("PreferenceFragmentMiscCompat")) {
            return PREFIX + "Misc";
        }
        if (legacyDestination.endsWith("PreferenceFragmentAboutCompat")) {
            return PREFIX + "About";
        }
        if (legacyDestination.endsWith("PreferenceFragmentViewsCompat")) {
            return MorpheSettingsV4Catalog.V4_POST_VIEWS_FRAGMENT;
        }
        if (legacyDestination.endsWith("PreferenceFragmentToolbarCompat")) {
            return MorpheSettingsV4Catalog.V4_TOOLBAR_FRAGMENT;
        }
        return legacyDestination;
    }

    public static final class Posts extends NativePage {
        public Posts() {
            super("Posts", "pref_posts_v2");
        }
    }

    public static final class Comments extends NativePage {
        public Comments() {
            super("Comments", "pref_comments_v2");
        }
    }

    public static final class BottomNavigation extends NativePage {
        public BottomNavigation() {
            super("Bottom navigation", "pref_bottom_navigation_v2");
        }
    }

    public static final class Drawer extends NativePage {
        public Drawer() {
            super("Navigation drawer", "pref_drawer_v2");
        }
    }

    public static final class Media extends NativePage {
        public Media() {
            super("Media viewer", "pref_media_v2");
        }
    }

    public static final class Links extends NativePage {
        public Links() {
            super("Link handling", "pref_links_v2");
        }
    }

    public static final class Search extends NativePage {
        public Search() {
            super("Search", "pref_search_v2");
        }
    }

    public static final class Filters extends NativePage {
        public Filters() {
            super("Content filters", "pref_filters_v2");
        }
    }

    public static final class Messages extends NativePage {
        public Messages() {
            super("Notifications", "pref_messages_v2");
        }
    }

    public static final class Privacy extends NativePage {
        public Privacy() {
            super("History & privacy", "pref_privacy_v2");
        }
    }

    public static final class General extends NativePage {
        public General() {
            super("General", "pref_general_v2");
        }
    }

    public static final class Misc extends NativePage {
        public Misc() {
            super("Legacy features", "pref_misc_v2");
        }
    }

    public static final class About extends NativePage {
        public About() {
            super("About Boost", "pref_about_v2");
        }
    }

    public static final class Morphe extends NativePage {
        public static final String MATERIAL_TOGGLE_LAST_MARKER =
                "MORPHE_BOOST_SETTINGS_V14_3_MATERIAL_TOGGLE_LAST_ISSUE106_V1";

        public Morphe() {
            super("Morphe", "morphe_boost_settings_skeleton");
        }
    }

    public abstract static class NativePage extends Fragment {
        private static final String ANDROID_NS =
                "http://schemas.android.com/apk/res/android";
        private static final String APP_NS =
                "http://schemas.android.com/apk/res-auto";
        private static final String EXTRA_SHOW_FRAGMENT = "extra_show_fragment";
        private static final String BOOST_FRAGMENT_PREFIX =
                "com.rubenmayayo.reddit.ui.preferences.v2.";
        private static final int REQUEST_SYNCCIT_DEVICE = 1001;
        private static final int REQUEST_SYNCCIT_ACCOUNT = 1002;

        private final String pageTitle;
        private final String resourceName;
        private final List<Binding> bindings = new ArrayList<>();
        private final Map<String, Control> controlsByKey = new LinkedHashMap<>();

        private MorpheSettingsV4Theme.Tokens tokens;
        private SharedPreferences preferences;
        private EditText synccitUsernameInput;
        private EditText synccitAuthInput;
        private Object savedSearchObserver;
        private FrameLayout pageHost;
        private View settingsPage;
        private final List<EditorPage> editorPages = new ArrayList<>();
        private Object editorFragmentManager;
        private Object editorBackStackListener;
        private int editorBackStackBaseCount = -1;
        private int editorBackStackSequence;

        protected NativePage(String pageTitle, String resourceName) {
            this.pageTitle = pageTitle;
            this.resourceName = resourceName;
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

            pageHost = new FrameLayout(context);
            pageHost.setBackgroundColor(tokens.background);

            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(true);
            scrollView.setClipToPadding(false);
            scrollView.setBackgroundColor(tokens.background);
            settingsPage = scrollView;
            pageHost.addView(scrollView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(10), dp(16), dp(32));
            scrollView.addView(content, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            List<Control> controls = readControls(context);
            if (controls.isEmpty()) {
                addSectionLabel(content, pageTitle);
                addSpace(content, 8);
                LinearLayout group = addGroup(content);
                addActionRow(
                        group,
                        "This settings page is unavailable",
                        "Boost's packaged preference contract could not be read.",
                        view -> showUnavailable()
                );
                return pageHost;
            }

            renderControls(content, controls);
            updateDependencies();
            return pageHost;
        }

        @Override
        public void onResume() {
            super.onResume();
            syncRows();
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
        public void onDestroyView() {
            bindings.clear();
            controlsByKey.clear();
            synccitUsernameInput = null;
            synccitAuthInput = null;
            savedSearchObserver = null;
            editorPages.clear();
            pageHost = null;
            settingsPage = null;
            editorFragmentManager = null;
            editorBackStackListener = null;
            editorBackStackBaseCount = -1;
            super.onDestroyView();
        }

        @Override
        public void onActivityResult(
                int requestCode,
                int resultCode,
                Intent data
        ) {
            super.onActivityResult(requestCode, resultCode, data);
            if ((requestCode != REQUEST_SYNCCIT_DEVICE
                    && requestCode != REQUEST_SYNCCIT_ACCOUNT)
                    || resultCode != Activity.RESULT_OK
                    || data == null) {
                return;
            }
            String username = data.getStringExtra("username");
            String auth = data.getStringExtra("auth");
            if (username == null || auth == null) {
                return;
            }
            saveSynccitCredentials(username, auth);
            if (synccitUsernameInput != null) {
                synccitUsernameInput.setText(username);
            }
            if (synccitAuthInput != null) {
                synccitAuthInput.setText(auth);
            }
        }

        @Override
        public void onPrepareOptionsMenu(Menu menu) {
            super.onPrepareOptionsMenu(menu);
            hideMenuItem(menu, "action_generic_search");
            hideMenuItem(menu, "action_search");
            hideMenuItem(menu, "search");
            hideMenuItem(menu, "menu_search");
        }

        private List<Control> readControls(Context context) {
            int resourceId = MorpheSettingsV4Catalog.resourceId(
                    context,
                    "xml",
                    resourceName
            );
            if (resourceId == 0) {
                return Collections.emptyList();
            }

            Resources resources = context.getResources();
            List<Control> result = new ArrayList<>();
            XmlResourceParser parser = null;
            String section = pageTitle;
            int categoryDepth = -1;
            int hiddenDepth = -1;
            try {
                parser = resources.getXml(resourceId);
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    int depth = parser.getDepth();
                    if (event == XmlPullParser.END_TAG) {
                        if (depth == hiddenDepth) {
                            hiddenDepth = -1;
                        }
                        if (depth == categoryDepth) {
                            section = pageTitle;
                            categoryDepth = -1;
                        }
                        continue;
                    }
                    if (event != XmlPullParser.START_TAG) {
                        continue;
                    }
                    if (hiddenDepth >= 0 && depth > hiddenDepth) {
                        continue;
                    }

                    String rawTag = parser.getName();
                    String tag = simpleTag(rawTag);
                    if ("PreferenceScreen".equals(tag)
                            || "intent".equalsIgnoreCase(tag)) {
                        continue;
                    }
                    boolean visible = parser.getAttributeBooleanValue(
                            APP_NS,
                            "isPreferenceVisible",
                            true
                    );
                    if ("PreferenceCategory".equals(tag)) {
                        if (!visible) {
                            hiddenDepth = depth;
                            continue;
                        }
                        String categoryTitle = attributeText(
                                resources,
                                parser,
                                "title"
                        );
                        section = TextUtils.isEmpty(categoryTitle)
                                ? pageTitle
                                : categoryTitle;
                        categoryDepth = depth;
                        continue;
                    }
                    if (!visible) {
                        continue;
                    }

                    Control control = new Control();
                    control.tag = tag;
                    control.section = section;
                    control.key = attributeText(resources, parser, "key");
                    control.title = attributeText(resources, parser, "title");
                    control.summary = attributeText(resources, parser, "summary");
                    control.summaryOn = attributeText(resources, parser, "summaryOn");
                    control.summaryOff = attributeText(resources, parser, "summaryOff");
                    control.defaultValue = attributeText(
                            resources,
                            parser,
                            "defaultValue"
                    );
                    control.dependency = attributeText(
                            resources,
                            parser,
                            "dependency"
                    );
                    control.fragmentName = parser.getAttributeValue(
                            ANDROID_NS,
                            "fragment"
                    );
                    control.disableDependentsState =
                            parser.getAttributeBooleanValue(
                                    ANDROID_NS,
                                    "disableDependentsState",
                                    false
                            );
                    control.min = parser.getAttributeIntValue(
                            ANDROID_NS,
                            "min",
                            0
                    );
                    control.max = parser.getAttributeIntValue(
                            ANDROID_NS,
                            "max",
                            100
                    );
                    control.entries = attributeArray(
                            resources,
                            parser,
                            "entries"
                    );
                    control.entryValues = attributeArray(
                            resources,
                            parser,
                            "entryValues"
                    );
                    configureMorpheControl(control);
                    if ("pref_general_v2".equals(resourceName)
                            && !TextUtils.isEmpty(control.fragmentName)) {
                        // These six routes duplicate the dedicated Morphe hubs.
                        // Classic Boost settings remains available separately.
                        continue;
                    }
                    if (!TextUtils.isEmpty(control.title)) {
                        result.add(control);
                        if (!TextUtils.isEmpty(control.key)) {
                            controlsByKey.put(control.key, control);
                        }
                    }
                }
            } catch (Exception ignored) {
                return Collections.emptyList();
            } finally {
                if (parser != null) {
                    parser.close();
                }
            }
            return result;
        }

        private void renderControls(
                LinearLayout content,
                List<Control> controls
        ) {
            String currentSection = null;
            LinearLayout group = null;
            for (Control control : controls) {
                if (!TextUtils.equals(currentSection, control.section)) {
                    if (content.getChildCount() > 0) {
                        addSpace(content, 24);
                    }
                    currentSection = control.section;
                    addSectionLabel(content, currentSection);
                    addSpace(content, 8);
                    group = addGroup(content);
                }
                if (isToggle(control)) {
                    addToggle(group, control);
                } else if (isChoice(control)) {
                    addChoice(group, control);
                } else if ("SeekBarPreference".equals(control.tag)) {
                    addSeek(group, control);
                } else {
                    addAction(group, control);
                }
            }
        }

        private void addToggle(LinearLayout group, Control control) {
            boolean checked = preferences.getBoolean(
                    control.key,
                    booleanDefault(control)
            );
            LinearLayout row = baseRow();
            TextView summary = addLabels(
                    row,
                    control.title,
                    toggleSummary(control, checked)
            );

            MorpheSettingsV14Ui.Toggle toggle =
                    new MorpheSettingsV14Ui.Toggle(
                            requireContext(),
                            tokens,
                            checked
                    );
            toggle.setOnCheckedChangeListener((button, value) -> {
                preferences.edit().putBoolean(control.key, value).apply();
                applySideEffects(control.key);
                summary.setText(toggleSummary(control, value));
                summary.setVisibility(
                        TextUtils.isEmpty(summary.getText())
                                ? View.GONE
                                : View.VISIBLE
                );
                updateDependencies();
            });
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    toggle.toggle();
                }
            });
            row.addView(toggle, wrapParams());
            addGroupedRow(group, row);
            bindings.add(new Binding(control, row, summary, toggle, null));
        }

        private void addChoice(LinearLayout group, Control control) {
            LinearLayout row = baseRow();
            TextView summary = addLabels(
                    row,
                    control.title,
                    selectedTitle(control)
            );
            addChevron(row);
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    showChoiceDialog(control, summary);
                }
            });
            addGroupedRow(group, row);
            bindings.add(new Binding(control, row, summary, null, null));
        }

        private void addSeek(LinearLayout group, Control control) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(12));
            row.setMinimumHeight(dp(82));
            row.setBackground(MorpheSettingsV4Theme.interactive(
                    requireContext(),
                    0x00000000,
                    0,
                    tokens.navigationAccent().color
            ));

            LinearLayout labels = new LinearLayout(requireContext());
            labels.setOrientation(LinearLayout.HORIZONTAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = textView(control.title, 16, tokens.textPrimary);
            title.setTypeface(Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
            ));
            labels.addView(title, labelsParams());
            int value = preferences.getInt(control.key, intDefault(control));
            TextView summary = textView(String.valueOf(value), 14, tokens.textSecondary);
            labels.addView(summary, wrapParams());
            row.addView(labels, matchWrapParams());

            SeekBar seek = new SeekBar(requireContext());
            seek.setMax(Math.max(0, control.max - control.min));
            seek.setProgress(Math.max(0, value - control.min));
            if (Build.VERSION.SDK_INT >= 21) {
                seek.setProgressTintList(ColorStateList.valueOf(
                        tokens.navigationAccent().color
                ));
                seek.setThumbTintList(ColorStateList.valueOf(
                        tokens.navigationAccent().color
                ));
            }
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                        SeekBar seekBar,
                        int progress,
                        boolean fromUser
                ) {
                    summary.setText(String.valueOf(control.min + progress));
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    int selected = control.min + seekBar.getProgress();
                    preferences.edit().putInt(control.key, selected).apply();
                    applySideEffects(control.key);
                }
            });
            row.addView(seek, matchWrapParams());
            addGroupedRow(group, row);
            bindings.add(new Binding(control, row, summary, null, seek));
        }

        private void addAction(LinearLayout group, Control control) {
            LinearLayout row = baseRow();
            TextView summary = addLabels(
                    row,
                    control.title,
                    actionSummary(control)
            );
            addChevron(row);
            row.setOnClickListener(view -> {
                if (row.isEnabled()) {
                    performAction(control);
                }
            });
            addGroupedRow(group, row);
            bindings.add(new Binding(control, row, summary, null, null));
        }

        private void addActionRow(
                LinearLayout group,
                String title,
                String summary,
                View.OnClickListener listener
        ) {
            LinearLayout row = baseRow();
            addLabels(row, title, summary);
            addChevron(row);
            row.setOnClickListener(listener);
            addGroupedRow(group, row);
        }

        private void performAction(Control control) {
            if ("FilterPreference".equals(control.tag)) {
                showFilterEditor(control);
                return;
            }
            if (control.tag.startsWith("Delete")) {
                confirmDelete(control);
                return;
            }
            if ("ColorPatternPreference".equals(control.tag)) {
                showColorPatternEditor();
                return;
            }
            if ("RevokeGDPRConsentPreference".equals(control.tag)) {
                openClassic("PreferenceFragmentAboutCompat");
                return;
            }

            String key = control.key;
            if ("synncit_config".equals(key)) {
                showSynccitEditor();
            } else if ("pref_sort_per_sub".equals(key)) {
                showSavedSortsEditor();
            } else if ("pref_frontpage_sort".equals(key)) {
                showSortEditor(false, frontpageSubscription(), null);
            } else if ("pref_default_sort".equals(key)) {
                showSortEditor(true, emptySubscription(), null);
            } else if ("pref_saved_searches".equals(key)) {
                showSavedSearchesEditor();
            } else if ("pref_search_advanced_help".equals(key)) {
                showFieldSearchHelp();
            } else if ("pref_notifications_configure".equals(key)) {
                openNotificationSettings();
            } else if ("pref_edit_subscriptions".equals(key)) {
                invokeActivityHelper("t0", Activity.class, hostActivity());
            } else if ("pref_manage_drafts".equals(key)) {
                invokeActivityHelper("P", Context.class, requireContext());
            } else if ("pref_imgur_uploads".equals(key)) {
                invokeActivityHelper("n0", Context.class, requireContext());
            } else if ("reset_tips".equals(key)) {
                resetTips();
            } else if ("support_launch".equals(key)) {
                invokeActivityHelper("c1", Context.class, requireContext());
            } else if ("rate_app".equals(key)) {
                invokeActivityHelper("u1", Context.class, requireContext());
            } else if ("about_subreddit".equals(key)) {
                invokeActivityHelper(
                        "Y0",
                        new Class<?>[]{Activity.class, String.class},
                        new Object[]{hostActivity(), "BoostForReddit"}
                );
            } else if ("about_faq".equals(key)) {
                invokeActivityHelper(
                        "m1",
                        new Class<?>[]{Activity.class, String.class, String.class},
                        new Object[]{hostActivity(), "boostforreddit", "index"}
                );
            } else if ("licenses_preference".equals(key)) {
                invokeActivityHelper("i0", Context.class, requireContext());
            } else if ("privacy_policy".equals(key)) {
                openUrl("https://www.iubenda.com/privacy-policy/7976518");
            } else if ("about_reddit".equals(key)) {
                invokeActivityHelper(
                        "y0",
                        new Class<?>[]{Context.class, String.class},
                        new Object[]{requireContext(), "rmayayo"}
                );
            } else if ("about_twitter".equals(key)) {
                openUrl("https://twitter.com/rmayayo");
            } else if ("contact_dev_key".equals(key)) {
                openEmail("mayayo.dev@gmail.com");
            } else if ("about_version".equals(key)) {
                showVersion();
            } else if (!TextUtils.isEmpty(control.fragmentName)) {
                openFragment(nativeDestination(control.fragmentName));
            } else {
                showUnavailable();
            }
        }

        private void showChoiceDialog(Control control, TextView summaryView) {
            if (control.entries.length == 0
                    || control.entries.length != control.entryValues.length) {
                showUnavailable();
                return;
            }
            EditorPage page = openEditorPage(control.title);

            String selected = preferences.getString(
                    control.key,
                    stringDefault(control)
            );
            LinearLayout group = addGroup(page.content);
            for (int index = 0; index < control.entries.length; index++) {
                final String value = control.entryValues[index];
                boolean checked = TextUtils.equals(value, selected);
                LinearLayout row = materialChoiceRow(
                        control.entries[index],
                        "",
                        checked
                );
                row.setOnClickListener(view -> {
                    preferences.edit().putString(control.key, value).apply();
                    applySideEffects(control.key);
                    summaryView.setText(selectedTitle(control));
                    updateDependencies();
                    page.dismiss();
                });
                addGroupedRow(group, row);
            }
        }

        private void showFilterEditor(Control control) {
            List<String> values = loadFilters(control.key);
            EditorPage page = openEditorPage(control.title);
            LinearLayout content = page.content;
            EditText input = addEditorInput(content, "Add filter", "", 4);

            TextView add = actionLabel("Add", true);
            content.addView(add, spacedMatchWrapParams(10));

            LinearLayout list = new LinearLayout(materialContext());
            list.setOrientation(LinearLayout.VERTICAL);
            content.addView(list, spacedMatchWrapParams(14));

            Runnable render = () -> renderFilterRows(
                    list,
                    values,
                    control.key
            );
            render.run();
            add.setOnClickListener(view -> {
                String value = input.getText().toString().trim();
                if (!TextUtils.isEmpty(value) && !values.contains(value)) {
                    values.add(0, value);
                    persistFilters(control.key, values);
                    input.setText("");
                    render.run();
                }
            });

        }

        private void renderFilterRows(
                LinearLayout list,
                List<String> values,
                String key
        ) {
            list.removeAllViews();
            if (values.isEmpty()) {
                TextView empty = textView(
                        "No filters yet",
                        14,
                        tokens.textSecondary
                );
                empty.setPadding(dp(4), dp(12), dp(4), dp(12));
                list.addView(empty);
                return;
            }
            LinearLayout group = addGroup(list);
            for (String value : new ArrayList<>(values)) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(5), dp(2), dp(5));
                TextView label = textView(value, 16, tokens.textPrimary);
                row.addView(label, labelsParams());
                TextView remove = actionLabel("Remove", false);
                remove.setOnClickListener(view -> {
                    values.remove(value);
                    persistFilters(key, values);
                    renderFilterRows(list, values, key);
                });
                row.addView(remove, wrapParams());
                addGroupedRow(group, row);
            }
        }

        @SuppressWarnings("unchecked")
        private List<String> loadFilters(String key) {
            try {
                Object settingsObject = invokeStatic("id.b", "v0");
                Method method = settingsObject.getClass().getMethod(
                        "j0",
                        String.class
                );
                Object value = method.invoke(settingsObject, key);
                if (value instanceof List) {
                    return new ArrayList<>((List<String>) value);
                }
            } catch (Throwable ignored) {
            }
            return new ArrayList<>();
        }

        private void persistFilters(String key, List<String> values) {
            try {
                Object settingsObject = invokeStatic("id.b", "v0");
                Method method = settingsObject.getClass().getMethod(
                        "d6",
                        String.class,
                        List.class
                );
                method.invoke(settingsObject, key, new ArrayList<>(values));
                applySideEffects(key);
            } catch (Throwable throwable) {
                showUnavailable();
            }
        }

        private void showColorPatternEditor() {
            Context context = requireContext();
            EditorPage page = openEditorPage("Color patterns");
            LinearLayout content = page.content;

            String[][] palettes = new String[][]{
                    {"Standard", "standard"},
                    {"Secondary", "secondary"},
                    {"Diverging", "diverging"},
                    {"Diverging translucent", "divergingTrans"},
                    {"Pain", "pain"},
                    {"Rainbow", "Rainbow"},
                    {"Viridis", "viridis2"},
                    {"Blues", "blues"},
                    {"Yellow to red", "YlOrRd"},
                    {"Grayscale", "gradient_gray"},
                    {"Transparent", "transparent"}
            };
            LinearLayout group = addGroup(content);
            int available = 0;
            for (String[] palette : palettes) {
                int resourceId = MorpheSettingsV4Catalog.resourceId(
                        context,
                        "array",
                        palette[1]
                );
                if (resourceId == 0) {
                    continue;
                }
                int[] colors;
                try {
                    colors = context.getResources().getIntArray(resourceId);
                } catch (Resources.NotFoundException ignored) {
                    continue;
                }
                available++;
                LinearLayout row = baseRow();
                addLabels(row, palette[0], "");
                LinearLayout preview = new LinearLayout(context);
                preview.setOrientation(LinearLayout.HORIZONTAL);
                preview.setGravity(Gravity.CENTER_VERTICAL);
                int shown = Math.min(colors.length, 7);
                for (int index = 0; index < shown; index++) {
                    View swatch = new View(context);
                    swatch.setBackground(MorpheSettingsV4Theme.rounded(
                            context,
                            colors[index],
                            5
                    ));
                    LinearLayout.LayoutParams swatchParams =
                            new LinearLayout.LayoutParams(dp(18), dp(30));
                    if (index > 0) {
                        swatchParams.setMarginStart(dp(3));
                    }
                    preview.addView(swatch, swatchParams);
                }
                row.addView(preview, wrapParams());
                row.setOnClickListener(view -> {
                    Object settingsObject = invokeStatic("id.b", "v0");
                    Object result = invokeInstance(
                            settingsObject,
                            "x6",
                            new Class<?>[]{int[].class},
                            new Object[]{colors}
                    );
                    if (result == InvocationFailure.INSTANCE) {
                        showUnavailable();
                        return;
                    }
                    setBoostStaticBoolean("j");
                    page.dismiss();
                });
                addGroupedRow(group, row);
            }
            if (available == 0) {
                page.dismiss();
                showUnavailable();
                return;
            }
        }

        private Object frontpageSubscription() {
            return invokeStatic(
                    "com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel",
                    "m"
            );
        }

        private Object emptySubscription() {
            return newInstance(
                    "com.rubenmayayo.reddit.models.reddit.SubscriptionViewModel",
                    new Class<?>[]{String.class},
                    new Object[]{""}
            );
        }

        @SuppressWarnings("unchecked")
        private void showSortEditor(
                boolean defaultSort,
                Object subscription,
                Runnable afterSelection
        ) {
            if (subscription == InvocationFailure.INSTANCE
                    || subscription == null) {
                showUnavailable();
                return;
            }
            try {
                Class<?> subscriptionType = Class.forName(
                        "com.rubenmayayo.reddit.models.reddit."
                                + "SubscriptionViewModel"
                );
                Object rawOptions = invokeStatic(
                        "qc.a",
                        "j",
                        new Class<?>[]{subscriptionType},
                        new Object[]{subscription}
                );
                if (!(rawOptions instanceof List)) {
                    showUnavailable();
                    return;
                }
                List<Object> options = (List<Object>) rawOptions;
                List<Integer> optionIds = new ArrayList<>();
                List<CharSequence> optionLabels = new ArrayList<>();
                for (Object option : options) {
                    int id = intResult(invokeInstance(option, "q"), -1);
                    String title = menuOptionTitle(option);
                    if (TextUtils.isEmpty(title) || id < 0) {
                        continue;
                    }
                    String supporting = stringResult(invokeInstance(option, "t"));
                    optionIds.add(id);
                    optionLabels.add(TextUtils.isEmpty(supporting)
                            ? title
                            : title + " — " + supporting);
                }
                if (optionIds.isEmpty()) {
                    showUnavailable();
                    return;
                }

                Integer selectedId = currentSortOptionId(
                        defaultSort,
                        subscription
                );
                EditorPage page = openEditorPage(
                        defaultSort ? "Default sort" : "Sort home by"
                );
                if (!defaultSort && Integer.valueOf(100).equals(selectedId)) {
                    page.content.addView(MorpheSettingsV14Ui.supportingText(
                            requireContext(),
                            tokens,
                            "Using the default sort"
                    ));
                    addSpace(page.content, 10);
                }
                LinearLayout group = addGroup(page.content);
                boolean hasReset = false;
                for (int index = 0; index < optionIds.size(); index++) {
                    int optionId = optionIds.get(index);
                    if (!defaultSort && optionId == 100) {
                        hasReset = true;
                        continue;
                    }
                    final int which = index;
                    MorpheSettingsV14Ui.ChoiceRow row = materialChoiceRow(
                            optionLabels.get(index).toString(),
                            "",
                            Integer.valueOf(optionId).equals(selectedId)
                    );
                    row.setOnClickListener(view -> {
                        int id = optionIds.get(which);
                        boolean saved = defaultSort
                                ? saveDefaultSort(id)
                                : saveSubscriptionSort(subscription, id);
                        if (!saved) {
                            showUnavailable();
                            return;
                        }
                        applySideEffects(defaultSort
                                ? "pref_default_sort"
                                : "pref_frontpage_sort");
                        page.dismiss();
                        if (afterSelection != null) {
                            afterSelection.run();
                        }
                    });
                    addGroupedRow(group, row);
                }
                if (hasReset) {
                    addSpace(page.content, 12);
                    TextView reset = actionLabel("Reset to default", false);
                    boolean resetEnabled = !Integer.valueOf(100).equals(
                            selectedId
                    );
                    reset.setEnabled(resetEnabled);
                    reset.setAlpha(resetEnabled ? 1.0f : 0.42f);
                    reset.setOnClickListener(view -> {
                        if (!saveSubscriptionSort(subscription, 100)) {
                            showUnavailable();
                            return;
                        }
                        applySideEffects("pref_frontpage_sort");
                        page.dismiss();
                        if (afterSelection != null) {
                            afterSelection.run();
                        }
                    });
                    page.content.addView(reset, wrapParams());
                }
            } catch (Throwable throwable) {
                showUnavailable();
            }
        }

        private int selectedSortIndex(
                boolean defaultSort,
                Object subscription,
                List<Integer> optionIds
        ) {
            Integer selectedId = currentSortOptionId(
                    defaultSort,
                    subscription
            );
            return selectedId == null ? -1 : optionIds.indexOf(selectedId);
        }

        private Integer currentSortOptionId(
                boolean defaultSort,
                Object subscription
        ) {
            try {
                Object sorting;
                Object period;
                if (defaultSort) {
                    Object settingsObject = invokeStatic("id.b", "v0");
                    sorting = invokeInstance(settingsObject, "Y1");
                    period = invokeInstance(settingsObject, "V1");
                } else {
                    Class<?> subscriptionType = Class.forName(
                            "com.rubenmayayo.reddit.models.reddit."
                                    + "SubscriptionViewModel"
                    );
                    Object manager = invokeStatic("he.c0", "e");
                    Object saved = invokeInstance(
                            manager,
                            "j",
                            new Class<?>[]{subscriptionType},
                            new Object[]{subscription}
                    );
                    if (saved == null || saved == InvocationFailure.INSTANCE) {
                        return 100;
                    }
                    sorting = invokeInstance(saved, "a");
                    period = invokeInstance(saved, "b");
                }
                if (sorting == InvocationFailure.INSTANCE
                        || sorting == null
                        || period == InvocationFailure.INSTANCE) {
                    return null;
                }
                String sortingName = enumName(sorting);
                if ("HOT".equals(sortingName)) {
                    return 0;
                }
                if ("NEW".equals(sortingName)) {
                    return 1;
                }
                if ("RISING".equals(sortingName)) {
                    return 2;
                }
                if ("GILDED".equals(sortingName)) {
                    return 5;
                }
                int periodIndex = timePeriodIndex(enumName(period));
                if (periodIndex < 0) {
                    return null;
                }
                if ("TOP".equals(sortingName)) {
                    return 30 + periodIndex;
                }
                if ("CONTROVERSIAL".equals(sortingName)) {
                    return 40 + periodIndex;
                }
                return null;
            } catch (Throwable throwable) {
                return null;
            }
        }

        private String enumName(Object value) {
            return value instanceof Enum
                    ? ((Enum<?>) value).name()
                    : "";
        }

        private int timePeriodIndex(String name) {
            String[] names = {"HOUR", "DAY", "WEEK", "MONTH", "YEAR", "ALL"};
            for (int index = 0; index < names.length; index++) {
                if (names[index].equals(name)) {
                    return index;
                }
            }
            return -1;
        }

        private boolean saveDefaultSort(int id) {
            String sort = null;
            String period = null;
            if (id == 0 || id == 1 || id == 2 || id == 5) {
                sort = String.valueOf(id);
            } else if (id >= 30 && id <= 35) {
                sort = "3";
                period = String.valueOf(id - 30);
            } else if (id >= 40 && id <= 45) {
                sort = "4";
                period = String.valueOf(id - 40);
            }
            if (sort == null) {
                return false;
            }
            Object settingsObject = invokeStatic("id.b", "v0");
            if (invokeInstance(
                    settingsObject,
                    "B6",
                    new Class<?>[]{String.class},
                    new Object[]{sort}
            ) == InvocationFailure.INSTANCE) {
                return false;
            }
            return period == null || invokeInstance(
                    settingsObject,
                    "A6",
                    new Class<?>[]{String.class},
                    new Object[]{period}
            ) != InvocationFailure.INSTANCE;
        }

        private boolean saveSubscriptionSort(Object subscription, int id) {
            try {
                Class<?> subscriptionType = Class.forName(
                        "com.rubenmayayo.reddit.models.reddit."
                                + "SubscriptionViewModel"
                );
                Object manager = invokeStatic("he.c0", "e");
                String method = id == 100 ? "a" : "m";
                Class<?>[] types = id == 100
                        ? new Class<?>[]{subscriptionType}
                        : new Class<?>[]{subscriptionType, int.class};
                Object[] values = id == 100
                        ? new Object[]{subscription}
                        : new Object[]{subscription, id};
                return invokeInstance(manager, method, types, values)
                        != InvocationFailure.INSTANCE;
            } catch (Throwable throwable) {
                return false;
            }
        }

        private String menuOptionTitle(Object option) {
            String title = stringResult(invokeInstance(option, "v"));
            if (!TextUtils.isEmpty(title)) {
                return title;
            }
            int resourceId = intResult(invokeInstance(option, "x"), 0);
            if (resourceId == 0) {
                return "";
            }
            try {
                return requireContext().getString(resourceId);
            } catch (Resources.NotFoundException ignored) {
                return "";
            }
        }

        @SuppressWarnings("unchecked")
        private void showSavedSortsEditor() {
            Object manager = invokeStatic("he.c0", "e");
            if (manager == InvocationFailure.INSTANCE) {
                showUnavailable();
                return;
            }
            EditorPage page = openEditorPage("Manage saved sorts");
            LinearLayout content = page.content;
            LinearLayout list = new LinearLayout(materialContext());
            list.setOrientation(LinearLayout.VERTICAL);
            content.addView(list, matchWrapParams());

            Runnable[] render = new Runnable[1];
            render[0] = () -> {
                list.removeAllViews();
                Object raw = invokeInstance(manager, "k");
                if (!(raw instanceof List) || ((List<?>) raw).isEmpty()) {
                    TextView empty = textView(
                            "No community-specific sorts yet.",
                            15,
                            tokens.textSecondary
                    );
                    empty.setPadding(dp(8), dp(16), dp(8), dp(16));
                    list.addView(empty, matchWrapParams());
                    return;
                }
                LinearLayout group = addGroup(list);
                for (Object subscription : (List<Object>) raw) {
                    String name = stringResult(
                            invokeInstance(subscription, "z")
                    );
                    if (TextUtils.isEmpty(name)) {
                        name = "Community";
                    }
                    LinearLayout row = baseRow();
                    addLabels(row, name, "Tap to change its saved sort");
                    TextView remove = actionLabel("Remove", false);
                    remove.setOnClickListener(view -> {
                        saveSubscriptionSort(subscription, 100);
                        render[0].run();
                    });
                    row.addView(remove, wrapParams());
                    row.setOnClickListener(view -> showSortEditor(
                            false,
                            subscription,
                            render[0]
                    ));
                    addGroupedRow(group, row);
                }
            };
            render[0].run();

            LinearLayout actions = endAlignedActions();
            TextView clear = actionLabel("Clear all", false);
            clear.setOnClickListener(view -> showConfirmationPage(
                    "Clear saved sorts?",
                    "Community-specific sort choices will be removed.",
                    "Clear",
                    () -> {
                        invokeInstance(manager, "c");
                        render[0].run();
                    }
            ));
            actions.addView(clear, wrapParams());
            content.addView(actions, matchWrapParams());
        }

        private void showSavedSearchesEditor() {
            Object model = savedSearchesViewModel();
            if (model == InvocationFailure.INSTANCE) {
                showUnavailable();
                return;
            }
            EditorPage page = openEditorPage("Saved searches");
            LinearLayout content = page.content;

            LinearLayout list = new LinearLayout(materialContext());
            list.setOrientation(LinearLayout.VERTICAL);
            content.addView(list, matchWrapParams());

            SavedSearchRenderer renderer = values -> {
                if (!page.isShowing()) {
                    return;
                }
                renderSavedSearches(list, model, values);
            };
            Object liveData = invokeInstance(model, "g");
            Object current = invokeInstance(liveData, "e");
            if (current instanceof List) {
                renderer.render((List<?>) current);
            } else {
                renderer.render(Collections.emptyList());
            }
            observeSavedSearches(liveData, renderer);

            LinearLayout actions = endAlignedActions();
            TextView add = actionLabel("Add saved search", true);
            add.setOnClickListener(view -> showSavedSearchEditor(
                    model,
                    null
            ));
            actions.addView(add, wrapParams());
            content.addView(actions, matchWrapParams());
        }

        private Object savedSearchesViewModel() {
            Activity activity = hostActivity();
            if (activity == null) {
                return InvocationFailure.INSTANCE;
            }
            try {
                Object factory = invokeStatic(
                        "hc.d",
                        "e",
                        new Class<?>[]{Context.class},
                        new Object[]{activity}
                );
                if (factory == InvocationFailure.INSTANCE) {
                    return InvocationFailure.INSTANCE;
                }
                Class<?> ownerType = Class.forName("androidx.lifecycle.r0");
                Class<?> factoryType = Class.forName("androidx.lifecycle.n0$b");
                Class<?> providerType = Class.forName("androidx.lifecycle.n0");
                Object provider = providerType
                        .getConstructor(ownerType, factoryType)
                        .newInstance(activity, factory);
                Method get = providerType.getMethod("a", Class.class);
                return get.invoke(provider, Class.forName("hc.i"));
            } catch (Throwable throwable) {
                return InvocationFailure.INSTANCE;
            }
        }

        private void observeSavedSearches(
                Object liveData,
                SavedSearchRenderer renderer
        ) {
            Activity activity = hostActivity();
            if (activity == null || liveData == InvocationFailure.INSTANCE) {
                return;
            }
            try {
                Class<?> ownerType = Class.forName("androidx.lifecycle.q");
                Class<?> observerType = Class.forName("androidx.lifecycle.y");
                savedSearchObserver = Proxy.newProxyInstance(
                        observerType.getClassLoader(),
                        new Class<?>[]{observerType},
                        (proxy, method, arguments) -> {
                            if ("a".equals(method.getName())
                                    && arguments != null
                                    && arguments.length == 1
                                    && arguments[0] instanceof List) {
                                List<?> values = new ArrayList<>(
                                        (List<?>) arguments[0]
                                );
                                activity.runOnUiThread(
                                        () -> renderer.render(values)
                                );
                            }
                            return null;
                        }
                );
                Method observe = liveData.getClass().getMethod(
                        "h",
                        ownerType,
                        observerType
                );
                observe.invoke(liveData, activity, savedSearchObserver);
            } catch (Throwable ignored) {
                savedSearchObserver = null;
            }
        }

        private void renderSavedSearches(
                LinearLayout list,
                Object model,
                List<?> values
        ) {
            list.removeAllViews();
            if (values.isEmpty()) {
                TextView empty = textView(
                        "No saved searches yet.",
                        15,
                        tokens.textSecondary
                );
                empty.setPadding(dp(8), dp(16), dp(8), dp(16));
                list.addView(empty, matchWrapParams());
                return;
            }
            LinearLayout group = addGroup(list);
            for (Object entity : values) {
                String name = stringField(entity, "b");
                String query = stringField(entity, "e");
                if (TextUtils.isEmpty(name)) {
                    name = query;
                }
                String scope = stringField(entity, "c");
                String summary = TextUtils.isEmpty(scope)
                        ? query
                        : query + "  ·  r/" + scope;
                LinearLayout row = baseRow();
                addLabels(row, name, summary);
                TextView remove = actionLabel("Remove", false);
                remove.setOnClickListener(view -> deleteSavedSearch(
                        model,
                        entity
                ));
                row.addView(remove, wrapParams());
                row.setOnClickListener(view -> showSavedSearchEditor(
                        model,
                        entity
                ));
                addGroupedRow(group, row);
            }
        }

        private void deleteSavedSearch(Object model, Object entity) {
            try {
                Class<?> entityType = Class.forName("hc.e");
                if (invokeInstance(
                        model,
                        "f",
                        new Class<?>[]{entityType},
                        new Object[]{entity}
                ) == InvocationFailure.INSTANCE) {
                    showUnavailable();
                }
            } catch (Throwable throwable) {
                showUnavailable();
            }
        }

        private void showSavedSearchEditor(Object model, Object existing) {
            SearchDraft draft = searchDraft(existing);
            EditorPage page = openEditorPage(
                    existing == null ? "Add saved search" : "Edit saved search"
            );
            LinearLayout content = page.content;

            EditText name = addEditorInput(
                    content,
                    "Name (optional)",
                    draft.name,
                    4
            );
            EditText query = addEditorInput(
                    content,
                    "Search query",
                    draft.query,
                    12
            );
            EditText community = addEditorInput(
                    content,
                    "Community (optional)",
                    draft.community,
                    12
            );

            addSpace(content, 18);
            LinearLayout choices = addGroup(content);
            LinearLayout sortRow = baseRow();
            TextView sortSummary = addLabels(
                    sortRow,
                    "Sort",
                    searchSortLabel(draft.sort)
            );
            addChevron(sortRow);
            sortRow.setOnClickListener(view -> showValueChoice(
                    "Sort",
                    new String[]{"Relevance", "New", "Top", "Hot", "Comments"},
                    new String[]{"relevance", "new", "top", "hot", "comments"},
                    draft.sort,
                    value -> {
                        draft.sort = value;
                        sortSummary.setText(searchSortLabel(value));
                    }
            ));
            addGroupedRow(choices, sortRow);

            LinearLayout periodRow = baseRow();
            TextView periodSummary = addLabels(
                    periodRow,
                    "Time period",
                    searchPeriodLabel(draft.period)
            );
            addChevron(periodRow);
            periodRow.setOnClickListener(view -> showValueChoice(
                    "Time period",
                    new String[]{"Hour", "Day", "Week", "Month", "Year", "All"},
                    new String[]{"hour", "day", "week", "month", "year", "all"},
                    draft.period,
                    value -> {
                        draft.period = value;
                        periodSummary.setText(searchPeriodLabel(value));
                    }
            ));
            addGroupedRow(choices, periodRow);

            LinearLayout actions = endAlignedActions();
            TextView cancel = actionLabel("Cancel", false);
            cancel.setOnClickListener(view -> page.dismiss());
            actions.addView(cancel, wrapParams());
            TextView save = actionLabel("Save", true);
            LinearLayout.LayoutParams saveParams = wrapParams();
            saveParams.setMarginStart(dp(8));
            actions.addView(save, saveParams);
            save.setOnClickListener(view -> {
                draft.name = name.getText().toString().trim();
                draft.query = query.getText().toString().trim();
                String previousCommunity = draft.community;
                draft.community = community.getText().toString().trim();
                if (!TextUtils.equals(previousCommunity, draft.community)) {
                    draft.scopeId = "";
                }
                if (TextUtils.isEmpty(draft.query)) {
                    query.setError("A search query is required");
                    return;
                }
                if (saveSavedSearch(model, existing, draft)) {
                    page.dismiss();
                } else {
                    showUnavailable();
                }
            });
            content.addView(actions, matchWrapParams());
        }

        private SearchDraft searchDraft(Object entity) {
            SearchDraft draft = new SearchDraft();
            if (entity == null) {
                int sortIndex = integerPreference(
                        "pref_default_search_sorting",
                        0
                );
                int periodIndex = integerPreference(
                        "pref_default_search_period",
                        5
                );
                String[] sorts = new String[]{
                        "relevance", "new", "top", "hot", "comments"
                };
                String[] periods = new String[]{
                        "hour", "day", "week", "month", "year", "all"
                };
                draft.sort = sorts[Math.max(0, Math.min(sortIndex, 4))];
                draft.period = periods[Math.max(0, Math.min(periodIndex, 5))];
                return draft;
            }
            draft.id = intField(entity, "a", 0);
            draft.name = stringField(entity, "b");
            draft.community = stringField(entity, "c");
            draft.scopeId = stringField(entity, "d");
            draft.query = stringField(entity, "e");
            draft.sort = stringField(entity, "f");
            draft.period = stringField(entity, "g");
            if (TextUtils.isEmpty(draft.sort)) {
                draft.sort = "relevance";
            }
            if (TextUtils.isEmpty(draft.period)) {
                draft.period = "all";
            }
            return draft;
        }

        private boolean saveSavedSearch(
                Object model,
                Object existing,
                SearchDraft draft
        ) {
            try {
                Class<?> entityType = Class.forName("hc.e");
                Object entity = entityType.getConstructor().newInstance();
                setField(entity, "a", draft.id);
                setField(
                        entity,
                        "b",
                        TextUtils.isEmpty(draft.name) ? draft.query : draft.name
                );
                setField(entity, "c", draft.community);
                setField(entity, "d", draft.scopeId);
                setField(entity, "e", draft.query);
                setField(entity, "f", draft.sort.toLowerCase());
                setField(entity, "g", draft.period.toLowerCase());
                String method = existing == null ? "h" : "i";
                return invokeInstance(
                        model,
                        method,
                        new Class<?>[]{entityType},
                        new Object[]{entity}
                ) != InvocationFailure.INSTANCE;
            } catch (Throwable throwable) {
                return false;
            }
        }

        private int integerPreference(String key, int fallback) {
            String value = preferences.getString(key, String.valueOf(fallback));
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private String searchSortLabel(String value) {
            if ("new".equals(value)) return "New";
            if ("top".equals(value)) return "Top";
            if ("hot".equals(value)) return "Hot";
            if ("comments".equals(value)) return "Comments";
            return "Relevance";
        }

        private String searchPeriodLabel(String value) {
            if ("hour".equals(value)) return "Hour";
            if ("day".equals(value)) return "Day";
            if ("week".equals(value)) return "Week";
            if ("month".equals(value)) return "Month";
            if ("year".equals(value)) return "Year";
            return "All";
        }

        private void showValueChoice(
                String title,
                String[] labels,
                String[] values,
                String current,
                ValueConsumer consumer
        ) {
            EditorPage page = openEditorPage(title);
            LinearLayout group = addGroup(page.content);
            for (int index = 0; index < labels.length; index++) {
                LinearLayout option = materialChoiceRow(
                        labels[index],
                        "",
                        TextUtils.equals(values[index], current)
                );
                final String value = values[index];
                option.setOnClickListener(view -> {
                    consumer.accept(value);
                    page.dismiss();
                });
                addGroupedRow(group, option);
            }
        }

        private void showSynccitEditor() {
            Context context = requireContext();
            EditorPage page = openEditorPage("Configure Synccit");
            LinearLayout content = page.content;

            TextView explanation = textView(
                    "Synchronize read posts between supported Reddit clients. "
                            + "Credentials are stored in Boost on this device.",
                    14,
                    tokens.textSecondary
            );
            explanation.setLineSpacing(0, 1.08f);
            content.addView(explanation, spacedMatchWrapParams(4));

            synccitUsernameInput = addEditorInput(
                    content,
                    "Synccit username",
                    settingsString("k4"),
                    14
            );
            synccitAuthInput = addEditorInput(
                    content,
                    "Device auth token",
                    settingsString("i4"),
                    12
            );

            addSpace(content, 18);
            LinearLayout registration = addGroup(content);
            LinearLayout account = baseRow();
            addLabels(
                    account,
                    "Create Synccit account",
                    "Register a new account through Boost"
            );
            addChevron(account);
            account.setOnClickListener(view -> startSynccitCreate(true));
            addGroupedRow(registration, account);

            LinearLayout device = baseRow();
            addLabels(
                    device,
                    "Add this device",
                    "Generate a device auth token for the username above"
            );
            addChevron(device);
            device.setOnClickListener(view -> startSynccitCreate(false));
            addGroupedRow(registration, device);

            LinearLayout actions = endAlignedActions();
            TextView disconnect = actionLabel("Disconnect", false);
            disconnect.setOnClickListener(view -> showConfirmationPage(
                    "Disconnect Synccit?",
                    "The saved Synccit username and token will be removed.",
                    "Disconnect",
                    () -> {
                        saveSynccitCredentials("", "");
                        synccitUsernameInput.setText("");
                        synccitAuthInput.setText("");
                    }
            ));
            actions.addView(disconnect, wrapParams());

            TextView save = actionLabel("Save", true);
            LinearLayout.LayoutParams saveParams = wrapParams();
            saveParams.setMarginStart(dp(8));
            actions.addView(save, saveParams);
            save.setOnClickListener(view -> {
                saveSynccitCredentials(
                        synccitUsernameInput.getText().toString().trim(),
                        synccitAuthInput.getText().toString().trim()
                );
                Toast.makeText(context, "Synccit settings saved", Toast.LENGTH_SHORT)
                        .show();
                page.dismiss();
            });
            content.addView(actions, matchWrapParams());
        }

        private void startSynccitCreate(boolean accountMode) {
            try {
                Class<?> type = Class.forName(
                        "com.rubenmayayo.reddit.ui.synccit."
                                + "SynccitCreateActivity"
                );
                Intent intent = new Intent(requireContext(), type);
                int requestCode;
                if (accountMode) {
                    intent.putExtra("account_mode", true);
                    requestCode = REQUEST_SYNCCIT_ACCOUNT;
                } else {
                    String username = synccitUsernameInput == null
                            ? settingsString("k4")
                            : synccitUsernameInput.getText().toString().trim();
                    intent.putExtra("username", username);
                    requestCode = REQUEST_SYNCCIT_DEVICE;
                }
                startActivityForResult(intent, requestCode);
            } catch (Throwable throwable) {
                showUnavailable();
            }
        }

        private void saveSynccitCredentials(String username, String auth) {
            Object settingsObject = invokeStatic("id.b", "v0");
            invokeInstance(
                    settingsObject,
                    "y7",
                    new Class<?>[]{String.class},
                    new Object[]{username}
            );
            invokeInstance(
                    settingsObject,
                    "x7",
                    new Class<?>[]{String.class},
                    new Object[]{auth}
            );
        }

        private String settingsString(String method) {
            Object settingsObject = invokeStatic("id.b", "v0");
            return stringResult(invokeInstance(settingsObject, method));
        }

        private void showFieldSearchHelp() {
            EditorPage page = openEditorPage("Field search help");
            LinearLayout content = page.content;
            LinearLayout group = addGroup(content);
            TextView help = textView(
                    "Use a field followed by a colon:\n\n"
                            + "author:username\n"
                            + "subreddit:android\n"
                            + "title:keyword\n"
                            + "selftext:keyword\n"
                            + "url:example.com\n"
                            + "site:example.com\n"
                            + "self:yes  or  self:no\n"
                            + "nsfw:yes  or  nsfw:no\n\n"
                            + "Combine terms with AND, OR and NOT. "
                            + "Use parentheses to group expressions.",
                    15,
                    tokens.textPrimary
            );
            help.setTextIsSelectable(true);
            help.setLineSpacing(0, 1.12f);
            help.setPadding(dp(18), dp(16), dp(18), dp(18));
            group.addView(help, matchWrapParams());
        }

        private boolean notificationAccessGranted() {
            String enabled = Settings.Secure.getString(
                    requireContext().getContentResolver(),
                    "enabled_notification_listeners"
            );
            if (TextUtils.isEmpty(enabled)) {
                return false;
            }
            String packageName = requireContext().getPackageName();
            for (String flat : enabled.split(":")) {
                ComponentName component = ComponentName.unflattenFromString(flat);
                if (component != null
                        && packageName.equals(component.getPackageName())) {
                    return true;
                }
            }
            return false;
        }

        private void reconcileNotificationAccess() {
            boolean pushEnabled = preferences.getBoolean(
                    "pref_check_messages_push",
                    false
            );
            boolean accessGranted = notificationAccessGranted();
            if (pushEnabled == accessGranted) {
                return;
            }
            String message = pushEnabled
                    ? "Reddit push needs Android notification access. "
                            + "Enable access for Boost on the next screen."
                    : "Boost still has Android notification access. "
                            + "You can revoke it on the next screen.";
            showConfirmationPage(
                    "Reddit push",
                    message,
                    "Open settings",
                    () -> {
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                            ));
                        } catch (Exception exception) {
                            showUnavailable();
                        }
                    }
            );
        }

        private void confirmDelete(Control control) {
            showConfirmationPage(
                    control.title,
                    "Delete the selected local history data?",
                    "Delete",
                    () -> {
                        deleteForKey(control.key);
                        Toast.makeText(
                                requireContext(),
                                "Deleted",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        }

        private void deleteForKey(String key) {
            if ("pref_history_delete_read".equals(key)) {
                factoryCall("he.a0", "b", "a");
            } else if ("pref_recent_posts_delete".equals(key)) {
                factoryCall("he.z", "e", "c");
            } else if ("pref_history_delete".equals(key)) {
                factoryCall("he.d0", "d", "c");
            } else if ("pref_searches_delete".equals(key)) {
                factoryCall("ld.c", "a", "clear");
            } else if ("pref_all_delete".equals(key)) {
                factoryCall("he.z", "e", "c");
                factoryCall("he.d0", "d", "c");
                factoryCall("ld.c", "a", "clear");
            }
        }

        private void applySideEffects(String key) {
            // These are Boost's existing invalidation flags. Setting a broader
            // superset is safe and keeps changes visible without a hidden
            // PreferenceFragment listener.
            setBoostStaticBoolean("e");
            setBoostStaticBoolean("f");
            setBoostStaticBoolean("h");
            setBoostStaticBoolean("i");
            setBoostStaticBoolean("j");

            if ("pref_subscriptions_drawer".equals(key)
                    || "pref_subscriptions_drawer_show_icon".equals(key)
                    || "pref_subscriptions_only_casual".equals(key)) {
                invokeStatic(
                        "he.h0",
                        "m0",
                        new Class<?>[]{Context.class},
                        new Object[]{requireContext()}
                );
            }
            if ("pref_check_messages".equals(key)) {
                String method = preferences.getBoolean(key, true) ? "b" : "c";
                invokeStatic(
                        "pe.b",
                        method,
                        new Class<?>[]{Context.class},
                        new Object[]{requireContext()}
                );
            } else if ("pref_check_messages_interval".equals(key)) {
                invokeStatic(
                        "pe.b",
                        "b",
                        new Class<?>[]{Context.class},
                        new Object[]{requireContext()}
                );
            } else if ("pref_check_messages_push".equals(key)) {
                reconcileNotificationAccess();
            } else if ("pref_search_show_trending_searches".equals(key)
                    && preferences.getBoolean(key, false)) {
                invokeStatic(
                        "com.rubenmayayo.reddit.work.trending.TrendingWorker",
                        "a",
                        new Class<?>[]{Context.class, int.class},
                        new Object[]{requireContext(), 2}
                );
            }
        }

        private void syncRows() {
            if (preferences == null) {
                return;
            }
            for (Binding binding : bindings) {
                Control control = binding.control;
                if (binding.toggle != null) {
                    boolean checked = preferences.getBoolean(
                            control.key,
                            booleanDefault(control)
                    );
                    binding.toggle.setCheckedSilently(checked);
                    binding.summary.setText(toggleSummary(control, checked));
                } else if (isChoice(control)) {
                    binding.summary.setText(selectedTitle(control));
                } else if (binding.seekBar != null) {
                    int value = preferences.getInt(
                            control.key,
                            intDefault(control)
                    );
                    binding.seekBar.setProgress(value - control.min);
                    binding.summary.setText(String.valueOf(value));
                }
            }
            updateDependencies();
        }

        private void updateDependencies() {
            for (Binding binding : bindings) {
                Control control = binding.control;
                if (TextUtils.isEmpty(control.dependency)) {
                    setRowEnabled(binding.row, true);
                    continue;
                }
                Control parent = controlsByKey.get(control.dependency);
                boolean enabled = true;
                if (parent != null && isToggle(parent)) {
                    boolean parentValue = preferences.getBoolean(
                            parent.key,
                            booleanDefault(parent)
                    );
                    enabled = parent.disableDependentsState
                            ? !parentValue
                            : parentValue;
                }
                setRowEnabled(binding.row, enabled);
            }
        }

        private void setRowEnabled(View row, boolean enabled) {
            row.setEnabled(enabled);
            row.setAlpha(enabled ? 1.0f : 0.44f);
            if (row instanceof ViewGroup) {
                setChildrenEnabled((ViewGroup) row, enabled);
            }
        }

        private void setChildrenEnabled(ViewGroup group, boolean enabled) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View child = group.getChildAt(index);
                child.setEnabled(enabled);
                if (child instanceof ViewGroup) {
                    setChildrenEnabled((ViewGroup) child, enabled);
                }
            }
        }

        private String actionSummary(Control control) {
            if (!TextUtils.isEmpty(control.summary)) {
                return control.summary;
            }
            if (control.tag.startsWith("Delete")) {
                return "Remove local history data";
            }
            if ("ColorPatternPreference".equals(control.tag)) {
                return "Choose comment thread colors";
            }
            if ("RevokeGDPRConsentPreference".equals(control.tag)) {
                return "Open Boost's consent controls";
            }
            return "";
        }

        private String toggleSummary(Control control, boolean checked) {
            if (checked && !TextUtils.isEmpty(control.summaryOn)) {
                return control.summaryOn;
            }
            if (!checked && !TextUtils.isEmpty(control.summaryOff)) {
                return control.summaryOff;
            }
            return control.summary;
        }

        private String selectedTitle(Control control) {
            String value = preferences.getString(
                    control.key,
                    stringDefault(control)
            );
            for (int index = 0; index < control.entryValues.length; index++) {
                if (TextUtils.equals(value, control.entryValues[index])) {
                    return control.entries[index];
                }
            }
            return value;
        }

        private boolean booleanDefault(Control control) {
            return "true".equalsIgnoreCase(control.defaultValue);
        }

        private int intDefault(Control control) {
            try {
                return Integer.parseInt(control.defaultValue);
            } catch (NumberFormatException ignored) {
                return control.min;
            }
        }

        private String stringDefault(Control control) {
            return TextUtils.isEmpty(control.defaultValue)
                    ? ""
                    : control.defaultValue;
        }

        private boolean isToggle(Control control) {
            return "CheckBoxPreference".equals(control.tag)
                    || "SwitchPreference".equals(control.tag)
                    || "SwitchPreferenceCompat".equals(control.tag);
        }

        private boolean isChoice(Control control) {
            return "ListPreference".equals(control.tag)
                    || "PreviewSizePreference".equals(control.tag)
                    || "PreviewAlignmentPreference".equals(control.tag)
                    || "MediaTapActionPreference".equals(control.tag);
        }

        private void configureMorpheControl(Control control) {
            if ("PreviewSizePreference".equals(control.tag)) {
                control.entries = new String[]{"Compact", "Balanced", "Large"};
                control.entryValues = new String[]{"compact", "balanced", "large"};
            } else if ("PreviewAlignmentPreference".equals(control.tag)) {
                control.entries = new String[]{"Left", "Center", "Right"};
                control.entryValues = new String[]{"left", "center", "right"};
            } else if ("MediaTapActionPreference".equals(control.tag)) {
                control.entries = new String[]{
                        "Image viewer",
                        "Video viewer",
                        "Browser",
                        "Disabled"
                };
                control.entryValues = new String[]{
                        "image_viewer",
                        "video_viewer",
                        "browser",
                        "disabled"
                };
            }
        }

        private void openClassic(String simpleName) {
            openFragment(BOOST_FRAGMENT_PREFIX + simpleName);
        }

        private void openFragment(String destination) {
            Activity activity = hostActivity();
            if (activity == null || TextUtils.isEmpty(destination)) {
                showUnavailable();
                return;
            }
            Intent intent = new Intent(activity, activity.getClass());
            intent.putExtra(EXTRA_SHOW_FRAGMENT, destination);
            activity.startActivity(intent);
        }

        private void openNotificationSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                startActivity(intent);
            } catch (Exception exception) {
                showUnavailable();
            }
        }

        private void openUrl(String url) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception exception) {
                showUnavailable();
            }
        }

        private void openEmail(String address) {
            try {
                startActivity(new Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse("mailto:" + address)
                ));
            } catch (Exception exception) {
                showUnavailable();
            }
        }

        private void showVersion() {
            String version = "Unknown";
            try {
                PackageInfo info = requireContext()
                        .getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0);
                version = info.versionName;
            } catch (Exception ignored) {
            }
            EditorPage page = openEditorPage("Boost version");
            LinearLayout group = addGroup(page.content);
            TextView value = textView(version, 18, tokens.textPrimary);
            value.setPadding(dp(18), dp(16), dp(18), dp(18));
            group.addView(value, matchWrapParams());
        }

        private void resetTips() {
            factoryCall("he.f", "b", "f");
            Toast.makeText(
                    requireContext(),
                    "Tips reset",
                    Toast.LENGTH_SHORT
            ).show();
        }

        private void invokeActivityHelper(
                String method,
                Class<?> parameterType,
                Object value
        ) {
            invokeActivityHelper(
                    method,
                    new Class<?>[]{parameterType},
                    new Object[]{value}
            );
        }

        private void invokeActivityHelper(
                String method,
                Class<?>[] parameterTypes,
                Object[] values
        ) {
            Object result = invokeStatic(
                    "com.rubenmayayo.reddit.ui.activities.i",
                    method,
                    parameterTypes,
                    values
            );
            if (result == InvocationFailure.INSTANCE) {
                showUnavailable();
            }
        }

        private void factoryCall(
                String className,
                String factory,
                String method
        ) {
            try {
                Object target = invokeStatic(className, factory);
                Method action = target.getClass().getMethod(method);
                action.invoke(target);
            } catch (Throwable throwable) {
                showUnavailable();
            }
        }

        private Object invokeStatic(String className, String method) {
            return invokeStatic(className, method, new Class<?>[0], new Object[0]);
        }

        private Object invokeStatic(
                String className,
                String method,
                Class<?>[] parameterTypes,
                Object[] values
        ) {
            try {
                Class<?> type = Class.forName(className);
                Method target = type.getMethod(method, parameterTypes);
                return target.invoke(null, values);
            } catch (Throwable throwable) {
                return InvocationFailure.INSTANCE;
            }
        }

        private Object invokeInstance(Object target, String method) {
            return invokeInstance(
                    target,
                    method,
                    new Class<?>[0],
                    new Object[0]
            );
        }

        private Object invokeInstance(
                Object target,
                String method,
                Class<?>[] parameterTypes,
                Object[] values
        ) {
            if (target == null || target == InvocationFailure.INSTANCE) {
                return InvocationFailure.INSTANCE;
            }
            try {
                Method action = target.getClass().getMethod(
                        method,
                        parameterTypes
                );
                return action.invoke(target, values);
            } catch (Throwable throwable) {
                return InvocationFailure.INSTANCE;
            }
        }

        private Object newInstance(
                String className,
                Class<?>[] parameterTypes,
                Object[] values
        ) {
            try {
                Class<?> type = Class.forName(className);
                return type.getConstructor(parameterTypes).newInstance(values);
            } catch (Throwable throwable) {
                return InvocationFailure.INSTANCE;
            }
        }

        private String stringResult(Object value) {
            return value instanceof String ? (String) value : "";
        }

        private int intResult(Object value, int fallback) {
            return value instanceof Number
                    ? ((Number) value).intValue()
                    : fallback;
        }

        private String stringField(Object target, String name) {
            Object value = fieldValue(target, name);
            return value instanceof String ? (String) value : "";
        }

        private int intField(Object target, String name, int fallback) {
            return intResult(fieldValue(target, name), fallback);
        }

        private Object fieldValue(Object target, String name) {
            if (target == null) {
                return null;
            }
            try {
                Field field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable throwable) {
                return null;
            }
        }

        private void setField(Object target, String name, Object value)
                throws ReflectiveOperationException {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }

        private void setBoostStaticBoolean(String fieldName) {
            try {
                Class<?> settingsClass = Class.forName("id.b");
                Field field = settingsClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setBoolean(null, true);
            } catch (Throwable ignored) {
            }
        }

        private void styleHost() {
            Activity activity = hostActivity();
            if (activity == null || tokens == null) {
                return;
            }
            activity.setTitle(editorPages.isEmpty()
                    ? pageTitle
                    : editorPages.get(editorPages.size() - 1).title);
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
            Context context = getContext();
            return context instanceof Activity ? (Activity) context : null;
        }

        private LinearLayout addGroup(LinearLayout parent) {
            LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
            parent.addView(group, matchWrapParams());
            return group;
        }

        private LinearLayout baseRow() {
            return MorpheSettingsV14Ui.baseRow(requireContext(), tokens);
        }

        private MorpheSettingsV14Ui.ChoiceRow materialChoiceRow(
                String title,
                String summary,
                boolean selected
        ) {
            return MorpheSettingsV14Ui.choiceRow(
                    requireContext(),
                    tokens,
                    title,
                    summary,
                    selected
            );
        }

        private TextView addLabels(
                LinearLayout row,
                String titleValue,
                String summaryValue
        ) {
            LinearLayout labels = new LinearLayout(requireContext());
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = textView(titleValue, 16, tokens.textPrimary);
            labels.addView(title);

            TextView summary = textView(
                    summaryValue,
                    14,
                    tokens.textSecondary
            );
            summary.setLineSpacing(0, 1.04f);
            summary.setMaxLines(4);
            summary.setVisibility(
                    TextUtils.isEmpty(summaryValue) ? View.GONE : View.VISIBLE
            );
            LinearLayout.LayoutParams summaryParams = wrapParams();
            summaryParams.topMargin = dp(3);
            labels.addView(summary, summaryParams);
            row.addView(labels, labelsParams());
            return summary;
        }

        private void addChevron(LinearLayout row) {
            row.addView(MorpheSettingsV14Ui.chevron(
                    requireContext(),
                    tokens
            ));
        }

        private void addGroupedRow(LinearLayout group, View row) {
            MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
        }

        private Context materialContext() {
            return requireContext();
        }

        private EditorPage openEditorPage(String title) {
            if (pageHost == null) {
                throw new IllegalStateException("Settings page is not attached");
            }
            if (!editorPages.isEmpty()) {
                editorPages.get(editorPages.size() - 1).view.setVisibility(
                        View.GONE
                );
            } else if (settingsPage != null) {
                settingsPage.setVisibility(View.GONE);
            }

            ScrollView scroll = new ScrollView(materialContext());
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setBackgroundColor(tokens.background);

            LinearLayout content = new LinearLayout(materialContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(16), dp(10), dp(16), dp(36));
            scroll.addView(content, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            EditorPage page = new EditorPage(title, scroll, content);
            editorPages.add(page);
            pageHost.addView(scroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            try {
                pushEditorBackStackEntry();
            } catch (Throwable throwable) {
                removeTopEditorView();
                throw new IllegalStateException(
                        "Morphe V13 editor back-stack bridge failed",
                        throwable
                );
            }
            styleHost();
            return page;
        }

        private void closeTopEditorPage() {
            if (editorPages.isEmpty()) {
                return;
            }
            if (!popEditorBackStackEntry()) {
                throw new IllegalStateException(
                        "Morphe V13 editor back-stack pop failed"
                );
            }
            syncEditorPagesWithBackStack();
        }

        private void removeTopEditorView() {
            if (editorPages.isEmpty()) {
                return;
            }
            EditorPage page = editorPages.remove(editorPages.size() - 1);
            if (pageHost != null) {
                pageHost.removeView(page.view);
            }
            if (editorPages.isEmpty()) {
                if (settingsPage != null) {
                    settingsPage.setVisibility(View.VISIBLE);
                }
                syncRows();
            } else {
                editorPages.get(editorPages.size() - 1).view.setVisibility(
                        View.VISIBLE
                );
            }
            styleHost();
        }

        private void ensureEditorBackStackBridge() throws Exception {
            if (editorFragmentManager != null
                    && editorBackStackListener != null) {
                return;
            }
            editorFragmentManager = getParentFragmentManager();
            editorBackStackBaseCount = editorBackStackCount();

            Method addListener = findBackStackListenerMethod();
            Class<?> listenerType = addListener.getParameterTypes()[0];
            if (!listenerType.isInterface()) {
                throw new NoSuchMethodException(
                        "Boost FragmentManager listener ABI unavailable"
                );
            }
            editorBackStackListener = Proxy.newProxyInstance(
                    listenerType.getClassLoader(),
                    new Class<?>[]{listenerType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("hashCode".equals(method.getName())) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(method.getName())) {
                                return proxy == args[0];
                            }
                            if ("toString".equals(method.getName())) {
                                return EDITOR_BACK_STACK_MARKER;
                            }
                        }
                        if ("onBackStackChanged".equals(method.getName())) {
                            syncEditorPagesWithBackStack();
                        }
                        return null;
                    }
            );
            addListener.invoke(editorFragmentManager, editorBackStackListener);
        }

        private Method findBackStackListenerMethod()
                throws NoSuchMethodException {
            for (Method method
                    : editorFragmentManager.getClass().getMethods()) {
                if (("addOnBackStackChangedListener".equals(method.getName())
                        || "i".equals(method.getName()))
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].isInterface()) {
                    for (Method callback
                            : method.getParameterTypes()[0].getMethods()) {
                        if ("onBackStackChanged".equals(callback.getName())
                                && callback.getParameterTypes().length == 0) {
                            method.setAccessible(true);
                            return method;
                        }
                    }
                }
            }
            throw new NoSuchMethodException(
                    "Boost FragmentManager back-stack listener unavailable"
            );
        }

        private void pushEditorBackStackEntry() throws Exception {
            ensureEditorBackStackBridge();
            Object transaction = invokeRuntimeMethod(
                    editorFragmentManager,
                    "beginTransaction",
                    "n",
                    new Class<?>[0],
                    new Object[0]
            );
            String name = "morphe-v13-editor-" + (++editorBackStackSequence);
            invokeRuntimeMethod(
                    transaction,
                    "addToBackStack",
                    "g",
                    new Class<?>[]{String.class},
                    new Object[]{name}
            );
            invokeRuntimeMethod(
                    transaction,
                    "commit",
                    "i",
                    new Class<?>[0],
                    new Object[0]
            );
        }

        private boolean popEditorBackStackEntry() {
            if (editorFragmentManager == null) {
                return false;
            }
            try {
                Object result = invokeRuntimeMethod(
                        editorFragmentManager,
                        "popBackStackImmediate",
                        "Z0",
                        new Class<?>[0],
                        new Object[0]
                );
                return Boolean.TRUE.equals(result);
            } catch (Throwable throwable) {
                return false;
            }
        }

        private void syncEditorPagesWithBackStack() {
            if (editorFragmentManager == null
                    || editorBackStackBaseCount < 0) {
                return;
            }
            try {
                int depth = Math.max(
                        0,
                        editorBackStackCount() - editorBackStackBaseCount
                );
                while (editorPages.size() > depth) {
                    removeTopEditorView();
                }
            } catch (Throwable ignored) {
            }
        }

        private int editorBackStackCount() throws Exception {
            Object value = invokeRuntimeMethod(
                    editorFragmentManager,
                    "getBackStackEntryCount",
                    "n0",
                    new Class<?>[0],
                    new Object[0]
            );
            if (!(value instanceof Number)) {
                throw new IllegalStateException(
                        "Boost FragmentManager back-stack count unavailable"
                );
            }
            return ((Number) value).intValue();
        }

        private Object invokeRuntimeMethod(
                Object target,
                String canonicalName,
                String boostName,
                Class<?>[] parameterTypes,
                Object[] values
        ) throws Exception {
            Method method = findRuntimeMethod(
                    target,
                    canonicalName,
                    boostName,
                    parameterTypes.length,
                    parameterTypes
            );
            return method.invoke(target, values);
        }

        private Method findRuntimeMethod(
                Object target,
                String canonicalName,
                String boostName,
                int parameterCount,
                Class<?>[] parameterTypes
        ) throws NoSuchMethodException {
            for (Method method : target.getClass().getMethods()) {
                if ((!canonicalName.equals(method.getName())
                        && !boostName.equals(method.getName()))
                        || method.getParameterTypes().length != parameterCount) {
                    continue;
                }
                if (parameterTypes != null) {
                    Class<?>[] actual = method.getParameterTypes();
                    boolean matches = true;
                    for (int index = 0; index < actual.length; index++) {
                        if (actual[index] != parameterTypes[index]) {
                            matches = false;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                }
                method.setAccessible(true);
                return method;
            }
            throw new NoSuchMethodException(
                    target.getClass().getName()
                            + "." + canonicalName
                            + "/" + boostName
            );
        }

        private EditText addEditorInput(
                LinearLayout parent,
                String hint,
                String value,
                int topMarginDp
        ) {
            MorpheSettingsV14Ui.Field field = MorpheSettingsV14Ui.outlinedField(
                    materialContext(),
                    tokens,
                    hint,
                    value
            );
            EditText input = field.input;
            input.setInputType(InputType.TYPE_CLASS_TEXT);

            LinearLayout.LayoutParams params = matchWrapParams();
            params.topMargin = dp(topMarginDp);
            parent.addView(field.root, params);
            return input;
        }

        private TextView actionLabel(String value, boolean filled) {
            return MorpheSettingsV14Ui.action(
                    materialContext(),
                    tokens,
                    value,
                    filled
            );
        }

        private LinearLayout endAlignedActions() {
            LinearLayout actions = new LinearLayout(requireContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            actions.setPadding(0, dp(14), 0, 0);
            return actions;
        }

        private LinearLayout.LayoutParams spacedMatchWrapParams(int topDp) {
            LinearLayout.LayoutParams params = matchWrapParams();
            params.topMargin = dp(topDp);
            return params;
        }

        private void showConfirmationPage(
                String title,
                String message,
                String confirmLabel,
                Runnable confirmation
        ) {
            EditorPage page = openEditorPage(title);
            TextView body = textView(message, 16, tokens.textPrimary);
            body.setLineSpacing(0, 1.12f);
            body.setPadding(dp(4), dp(12), dp(4), dp(12));
            page.content.addView(body, matchWrapParams());

            LinearLayout actions = endAlignedActions();
            TextView cancel = actionLabel("Cancel", false);
            cancel.setOnClickListener(view -> page.dismiss());
            actions.addView(cancel, wrapParams());
            TextView confirm = actionLabel(confirmLabel, true);
            LinearLayout.LayoutParams confirmParams = wrapParams();
            confirmParams.setMarginStart(dp(8));
            actions.addView(confirm, confirmParams);
            confirm.setOnClickListener(view -> {
                confirmation.run();
                page.dismiss();
            });
            page.content.addView(actions, matchWrapParams());
        }

        private final class EditorPage {
            final String title;
            final View view;
            final LinearLayout content;

            EditorPage(String title, View view, LinearLayout content) {
                this.title = title;
                this.view = view;
                this.content = content;
            }

            boolean isShowing() {
                return pageHost != null && view.getParent() == pageHost;
            }

            void dismiss() {
                if (!editorPages.isEmpty()
                        && editorPages.get(editorPages.size() - 1) == this) {
                    closeTopEditorPage();
                }
            }
        }

        private void hideMenuItem(Menu menu, String resourceName) {
            int id = MorpheSettingsV4Catalog.resourceId(
                    requireContext(),
                    "id",
                    resourceName
            );
            if (id != 0) {
                MenuItem item = menu.findItem(id);
                if (item != null) {
                    item.setVisible(false);
                }
            }
        }

        private void showUnavailable() {
            Toast.makeText(
                    requireContext(),
                    "This Boost setting is currently unavailable.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        private void addSectionLabel(LinearLayout parent, String value) {
            parent.addView(MorpheSettingsV14Ui.sectionLabel(
                    requireContext(),
                    tokens,
                    value
            ));
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

        private LinearLayout.LayoutParams matchWrapParams() {
            return new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        private void addSpace(LinearLayout parent, int heightDp) {
            parent.addView(new View(requireContext()), new LinearLayout.LayoutParams(
                    1,
                    dp(heightDp)
            ));
        }

        private int dp(float value) {
            return MorpheSettingsV4Theme.dp(requireContext(), value);
        }

        private static String simpleTag(String value) {
            if (value == null) {
                return "";
            }
            int index = value.lastIndexOf('.');
            return index < 0 ? value : value.substring(index + 1);
        }

        private static String attributeText(
                Resources resources,
                XmlResourceParser parser,
                String name
        ) {
            int resourceId = parser.getAttributeResourceValue(
                    ANDROID_NS,
                    name,
                    0
            );
            if (resourceId != 0) {
                try {
                    return resources.getText(resourceId).toString();
                } catch (Resources.NotFoundException ignored) {
                    return "";
                }
            }
            String value = parser.getAttributeValue(ANDROID_NS, name);
            return value == null || "@null".equals(value) ? "" : value;
        }

        private static String[] attributeArray(
                Resources resources,
                XmlResourceParser parser,
                String name
        ) {
            int resourceId = parser.getAttributeResourceValue(
                    ANDROID_NS,
                    name,
                    0
            );
            if (resourceId == 0) {
                return new String[0];
            }
            try {
                CharSequence[] values = resources.getTextArray(resourceId);
                String[] result = new String[values.length];
                for (int index = 0; index < values.length; index++) {
                    result[index] = values[index].toString();
                }
                return result;
            } catch (Resources.NotFoundException ignored) {
                return new String[0];
            }
        }

        private static final class Control {
            String tag = "";
            String section = "";
            String key = "";
            String title = "";
            String summary = "";
            String summaryOn = "";
            String summaryOff = "";
            String defaultValue = "";
            String dependency = "";
            String fragmentName = "";
            boolean disableDependentsState;
            int min;
            int max;
            String[] entries = new String[0];
            String[] entryValues = new String[0];
        }

        private static final class Binding {
            final Control control;
            final View row;
            final TextView summary;
            final MorpheSettingsV14Ui.Toggle toggle;
            final SeekBar seekBar;

            Binding(
                    Control control,
                    View row,
                    TextView summary,
                    MorpheSettingsV14Ui.Toggle toggle,
                    SeekBar seekBar
            ) {
                this.control = control;
                this.row = row;
                this.summary = summary;
                this.toggle = toggle;
                this.seekBar = seekBar;
            }
        }

        private static final class SearchDraft {
            int id;
            String name = "";
            String community = "";
            String scopeId = "";
            String query = "";
            String sort = "relevance";
            String period = "all";
        }

        private interface SavedSearchRenderer {
            void render(List<?> values);
        }

        private interface ValueConsumer {
            void accept(String value);
        }

        private enum InvocationFailure {
            INSTANCE
        }
    }
}
