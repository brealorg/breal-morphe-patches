package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Explicit DEV-only transaction used by the repository runtime binding audit.
 * This fragment is never linked from the settings UI.
 */
public final class MorpheSettingsV4BindingRuntimeAuditFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_BINDING_RUNTIME_AUDIT_ISSUE106_V1";
    public static final String EFFECT_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_APPEARANCE_LAYOUT_EFFECT_AUDIT_ISSUE106_V1";
    public static final String HARNESS_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_AUDIT_HARNESS_V56";
    public static final String SYSTEM_BAR_RENDER_CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_AUDIT_SYSTEM_BARS_ANDROID15_V56_1";

    private static final String TAG = "MorpheSettingsAudit";
    private static final String DEV_PACKAGE = "com.rubenmayayo.reddit.dev";
    private static final String EXTRA_PHASE =
            "morphe_settings_binding_audit_phase";
    private static final String EXTRA_NONCE =
            "morphe_settings_binding_audit_nonce";
    private static final String EXTRA_SCOPE =
            "morphe_settings_binding_audit_scope";
    private static final String SCOPE_APPEARANCE_LAYOUT = "appearance-layout";
    private static final String PHASE_WRITE = "write";
    private static final String PHASE_VERIFY_RESTORE = "verify_restore";
    private static final String PHASE_RECOVER = "recover";
    private static final String PHASE_RECOVER_FORCE = "recover_force";
    private static final String STATUS_BAR_SURFACE_TAG =
            "MORPHE_BOOST_COMMENTS_SYSTEM_BAR_SURFACE_V1";
    private static final String NAVIGATION_BAR_SURFACE_TAG =
            "MORPHE_BOOST_SETTINGS_V4_NAVIGATION_SURFACE_ISSUE106_V3";

    private static final String SNAPSHOT_PREFERENCES =
            "morphe.settings.binding.runtime.snapshot";
    private static final String SNAPSHOT_ACTIVE = "snapshot_active";
    private static final String SNAPSHOT_NONCE = "snapshot_nonce";
    private static final String SNAPSHOT_SCOPE = "snapshot_scope";
    private static final String SNAPSHOT_STARTED_AT = "snapshot_started_at";
    private static final String SNAPSHOT_AUDIT_ICON_ALIAS =
            "snapshot_audit_icon_alias";
    private static final String SNAPSHOT_ICON_STATES_PRESENT =
            "snapshot_icon_states_present";
    private static final long SNAPSHOT_LOCK_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final String SAVED_VIEW_PREFERENCES =
            "com.rubenmayayo.reddit.VIEW_PER_SUBSCRIPTION";
    private static final String SAVED_VIEW_AUDIT_KEY =
            "__morphe_runtime_binding_audit__";

    private static final String[] BOOLEAN_KEYS = new String[]{
            "pref_dynamic_colors",
            "pref_colored_status_bar",
            "pref_colored_nav_bar",
            "pref_view_per_subscription",
            "pref_left_handed",
            "pref_show_subreddit_prefix",
            "pref_cards_rounded_corners",
            "pref_cards_full_preview",
            "pref_cards_subreddit_icon",
            "pref_cards_gallery_carousel",
            "pref_cards_links_as_thumbnails",
            "pref_cards_preview_self",
            "pref_mini_cards_rounded_corners",
            "pref_mini_cards_truncate_title",
            "pref_mini_cards_buttons_visible",
            "pref_dense_buttons_visible",
            "pref_load_readability",
            "pref_lock_sidebar",
    };

    private static final String[] BOOLEAN_DEFAULT_TRUE_KEYS = new String[]{
            "pref_colored_status_bar",
            "pref_cards_subreddit_icon",
            "pref_cards_gallery_carousel",
            "pref_cards_links_as_thumbnails",
            "pref_cards_preview_self",
            "pref_mini_cards_rounded_corners",
            "pref_mini_cards_truncate_title",
    };

    private static final String[] STRING_KEYS = new String[]{
            "pref_view",
            "pref_title_font",
            "pref_font_size_title",
            "pref_comments_font",
            "pref_font_size",
    };

    private static final String[] INTEGER_KEYS = new String[]{
            "pref_cards_preview_self_lines",
    };

    private static final String[] VIEW_VALUES = new String[]{
            "0", "7", "1", "4", "5", "2", "6", "3"
    };

    private static final int[] SAVED_VIEW_VALUES = new int[]{
            0, 7, 1, 4, 5, 2, 6, 3
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

    private static final String[] FONT_SIZE_VALUES = new String[]{
            "XSmall", "Small", "Medium", "Large", "XLarge", "XXLarge"
    };

    private static final int INTEGER_MINIMUM = 0;
    private static final int INTEGER_MAXIMUM = 100;
    private static final int AUDIT_ITEM_COUNT = 26;
    private static final int DOMAIN_ACTION_COUNT = 196;
    private static final int EFFECT_AUDIT_RELOAD_COUNT = 26;
    private static final int RENDER_PROBE_COUNT = 6;

    private static final String ICON_ALIAS_PREFIX = "com.rubenmayayo.reddit.";
    private static final String[] ICON_RESOURCES = new String[]{
            "ic_launcher",
            "ic_launcher_grey",
            "ic_launcher_vivid",
            "ic_launcher_metal",
            "ic_launcher_yellow",
    };
    private static final String[] ICON_ALIASES = new String[]{
            "grey", "vivid", "metal", "yellow"
    };
    private static final String[] ICON_AUDIT_VALUES = new String[]{
            "", "grey", "vivid", "metal", "yellow"
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        Activity activity = activityFromContext(inflater.getContext());
        Intent intent = activity == null ? null : activity.getIntent();
        String phase = intent == null ? null : intent.getStringExtra(EXTRA_PHASE);
        String nonce = intent == null ? null : intent.getStringExtra(EXTRA_NONCE);
        String scope = intent == null ? null : intent.getStringExtra(EXTRA_SCOPE);
        if (intent != null) {
            intent.removeExtra(EXTRA_PHASE);
            intent.removeExtra(EXTRA_NONCE);
            intent.removeExtra(EXTRA_SCOPE);
        }
        if (!TextUtils.isEmpty(phase) && activity != null) {
            runAudit(activity, phase, nonce, scope);
        }

        TextView status = new TextView(inflater.getContext());
        status.setText("Morphe DEV binding audit");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        return status;
    }

    private static Activity activityFromContext(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static void runAudit(
            Context context,
            String phase,
            String nonce,
            String scope
    ) {
        if (!DEV_PACKAGE.equals(context.getPackageName())) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_REFUSED_NON_DEV_PACKAGE");
            return;
        }

        if (!SCOPE_APPEARANCE_LAYOUT.equals(scope)) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL scope=unsupported");
            return;
        }

        try {
            if (PHASE_WRITE.equals(phase)) {
                writeAuditValues(context, nonce, scope);
            } else if (PHASE_VERIFY_RESTORE.equals(phase)) {
                verifyReloadAndRestore(context, nonce, scope);
            } else if (PHASE_RECOVER.equals(phase)) {
                recoverIfNeeded(context, nonce, scope, false);
            } else if (PHASE_RECOVER_FORCE.equals(phase)) {
                recoverIfNeeded(context, nonce, scope, true);
            } else {
                Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=unknown");
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=" + phase, throwable);
        }
    }

    private static void writeAuditValues(
            Context context,
            String nonce,
            String scope
    ) {
        if (TextUtils.isEmpty(nonce)) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=write missing_nonce");
            return;
        }

        SharedPreferences snapshot = snapshotPreferences(context);
        if (snapshot.getBoolean(SNAPSHOT_ACTIVE, false)) {
            long startedAt = snapshot.getLong(SNAPSHOT_STARTED_AT, 0L);
            long now = System.currentTimeMillis();
            boolean fresh = startedAt > 0L
                    && (startedAt > now
                    || now - startedAt < SNAPSHOT_LOCK_TIMEOUT_MS);
            if (fresh) {
                Log.e(
                        TAG,
                        "MORPHE_BINDING_AUDIT_FAIL phase=write active_lock"
                );
                return;
            }
            boolean recovered = restoreOriginalValues(context, snapshot);
            if (!recovered) {
                Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=write stale_restore");
                return;
            }
            snapshot.edit().clear().commit();
            Log.w(TAG, "MORPHE_BINDING_AUDIT_STALE_SNAPSHOT_RECOVERED");
        }

        SharedPreferences defaults =
                PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences savedViews = context.getSharedPreferences(
                SAVED_VIEW_PREFERENCES,
                Context.MODE_PRIVATE
        );

        if (!validateExistingTypes(defaults)) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=write invalid_existing_type");
            return;
        }
        if (!createSnapshot(
                context,
                snapshot,
                defaults,
                savedViews,
                nonce,
                scope
        )) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=write snapshot_commit");
            return;
        }

        try {
            exerciseFullDomainsViaUiActions(
                    context,
                    defaults,
                    savedViews,
                    snapshot
            );
            runRenderedEffectProbes(context, snapshot);
            if (!verifyIconComponents(context)) {
                throw new IllegalStateException("app icon component audit failed");
            }
            Log.i(TAG, "MORPHE_BINDING_AUDIT_APP_ICONS_OK");
            Log.i(TAG, "MORPHE_BINDING_AUDIT_WRITE_OK nonce=" + nonce);
        } catch (Throwable throwable) {
            boolean restored = restoreOriginalValues(context, snapshot);
            if (restored) {
                snapshot.edit().clear().commit();
            }
            Log.e(
                    TAG,
                    "MORPHE_BINDING_AUDIT_FAIL phase=write restored=" + restored,
                    throwable
            );
        }
    }

    private static void verifyReloadAndRestore(
            Context context,
            String nonce,
            String scope
    ) {
        SharedPreferences snapshot = snapshotPreferences(context);
        if (!snapshot.getBoolean(SNAPSHOT_ACTIVE, false)) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=verify no_snapshot");
            return;
        }

        String expectedNonce = snapshot.getString(SNAPSHOT_NONCE, "");
        String expectedScope = snapshot.getString(SNAPSHOT_SCOPE, "");
        boolean nonceMatches = !TextUtils.isEmpty(nonce)
                && nonce.equals(expectedNonce)
                && scope.equals(expectedScope);
        boolean verified = false;
        boolean effectsReloaded = false;
        boolean iconsVerified = false;
        try {
            verified = nonceMatches && verifyPersistedAuditValues(context, snapshot);
            effectsReloaded = verified
                    && verifyNativeConsumersAfterReload(context, snapshot);
            iconsVerified = verified
                    && verifyExpectedAuditIcon(context, snapshot)
                    && verifyIconComponents(context);
            if (verified) {
                Log.i(TAG, "MORPHE_BINDING_AUDIT_RELOAD_OK nonce=" + expectedNonce);
            }
            if (iconsVerified) {
                Log.i(TAG, "MORPHE_BINDING_AUDIT_APP_ICONS_OK");
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_VERIFY_EXCEPTION", throwable);
        }

        boolean restored = restoreOriginalValues(context, snapshot);
        if (restored) {
            snapshot.edit().clear().commit();
            Log.i(TAG, "MORPHE_BINDING_AUDIT_RESTORE_OK nonce=" + expectedNonce);
        }

        if (verified && effectsReloaded && iconsVerified && restored) {
            Log.i(
                    TAG,
                    "RESULT=MORPHE_BOOST_SETTINGS_APPEARANCE_LAYOUT_AUDIT_V56_APP_PASS"
            );
        } else {
            Log.e(
                    TAG,
                    "MORPHE_BINDING_AUDIT_FAIL phase=verify"
                            + " nonce=" + nonceMatches
                            + " persisted=" + verified
                            + " effects_reloaded=" + effectsReloaded
                            + " icons=" + iconsVerified
                            + " restored=" + restored
            );
        }
    }

    private static void recoverIfNeeded(
            Context context,
            String nonce,
            String scope,
            boolean force
    ) {
        SharedPreferences snapshot = snapshotPreferences(context);
        if (!snapshot.getBoolean(SNAPSHOT_ACTIVE, false)) {
            Log.i(TAG, "MORPHE_BINDING_AUDIT_RECOVERY_NOT_NEEDED");
            return;
        }
        String expectedNonce = snapshot.getString(SNAPSHOT_NONCE, "");
        String expectedScope = snapshot.getString(SNAPSHOT_SCOPE, "");
        if (!force && (!TextUtils.equals(nonce, expectedNonce)
                || !TextUtils.equals(scope, expectedScope))) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=recover owner_mismatch");
            return;
        }
        if (force) {
            Log.w(TAG, "MORPHE_BINDING_AUDIT_ORPHAN_RECOVERY_FORCED");
        }
        boolean restored = restoreOriginalValues(context, snapshot);
        if (restored) {
            snapshot.edit().clear().commit();
            Log.i(TAG, "MORPHE_BINDING_AUDIT_RECOVERY_OK");
        } else {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_FAIL phase=recover");
        }
    }

    private static SharedPreferences snapshotPreferences(Context context) {
        return context.getSharedPreferences(
                SNAPSHOT_PREFERENCES,
                Context.MODE_PRIVATE
        );
    }

    private static boolean validateExistingTypes(SharedPreferences defaults) {
        Map<String, ?> values = defaults.getAll();
        for (String key : BOOLEAN_KEYS) {
            if (defaults.contains(key) && !(values.get(key) instanceof Boolean)) {
                return false;
            }
        }
        for (String key : STRING_KEYS) {
            if (defaults.contains(key) && !(values.get(key) instanceof String)) {
                return false;
            }
        }
        for (String key : INTEGER_KEYS) {
            if (defaults.contains(key) && !(values.get(key) instanceof Integer)) {
                return false;
            }
        }
        return true;
    }

    private static boolean createSnapshot(
            Context context,
            SharedPreferences snapshot,
            SharedPreferences defaults,
            SharedPreferences savedViews,
            String nonce,
            String scope
    ) {
        SharedPreferences.Editor editor = snapshot.edit().clear()
                .putBoolean(SNAPSHOT_ACTIVE, true)
                .putString(SNAPSHOT_NONCE, nonce)
                .putString(SNAPSHOT_SCOPE, scope)
                .putLong(SNAPSHOT_STARTED_AT, System.currentTimeMillis());
        for (String key : BOOLEAN_KEYS) {
            snapshotValue(editor, "default", key, defaults);
        }
        for (String key : STRING_KEYS) {
            snapshotValue(editor, "default", key, defaults);
        }
        for (String key : INTEGER_KEYS) {
            snapshotValue(editor, "default", key, defaults);
        }
        snapshotValue(editor, "saved", SAVED_VIEW_AUDIT_KEY, savedViews);
        snapshotIconComponents(context, editor);
        String originalAlias = MorpheSettingsV4AppIconFragment.selectedAlias(
                context
        );
        String auditAlias = "grey".equals(originalAlias) ? "vivid" : "grey";
        editor.putString(SNAPSHOT_AUDIT_ICON_ALIAS, auditAlias);
        return editor.commit();
    }

    private static void snapshotIconComponents(
            Context context,
            SharedPreferences.Editor editor
    ) {
        PackageManager packageManager = context.getPackageManager();
        editor.putBoolean(SNAPSHOT_ICON_STATES_PRESENT, true);
        for (String alias : ICON_ALIASES) {
            int state = packageManager.getComponentEnabledSetting(
                    iconAliasComponent(context, alias)
            );
            editor.putInt("icon_state:" + alias, state);
        }
    }

    private static void snapshotValue(
            SharedPreferences.Editor editor,
            String store,
            String key,
            SharedPreferences preferences
    ) {
        String identity = store + ":" + key;
        boolean present = preferences.contains(key);
        editor.putBoolean("present:" + identity, present);
        if (!present) {
            return;
        }
        Object value = preferences.getAll().get(key);
        putSnapshotValue(editor, identity, value);
    }

    private static void putSnapshotValue(
            SharedPreferences.Editor editor,
            String identity,
            Object value
    ) {
        String typeKey = "type:" + identity;
        String valueKey = "value:" + identity;
        if (value instanceof Boolean) {
            editor.putString(typeKey, "boolean").putBoolean(valueKey, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(typeKey, "string").putString(valueKey, (String) value);
        } else if (value instanceof Integer) {
            editor.putString(typeKey, "integer").putInt(valueKey, (Integer) value);
        } else if (value instanceof Long) {
            editor.putString(typeKey, "long").putLong(valueKey, (Long) value);
        } else if (value instanceof Float) {
            editor.putString(typeKey, "float").putFloat(valueKey, (Float) value);
        } else if (value instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> strings = new HashSet<>((Set<String>) value);
            editor.putString(typeKey, "string_set").putStringSet(valueKey, strings);
        } else {
            throw new IllegalStateException("unsupported preference type for " + identity);
        }
    }

    private static void exerciseFullDomainsViaUiActions(
            Context context,
            SharedPreferences defaults,
            SharedPreferences savedViews,
            SharedPreferences snapshot
    ) throws Exception {
        int actions = 0;
        int items = 0;
        for (String key : BOOLEAN_KEYS) {
            actions += exerciseBooleanDomain(defaults, snapshot, key);
            logAuditItem(key, 2);
            items++;
        }
        for (String key : STRING_KEYS) {
            int domainCount = exerciseStringDomain(defaults, snapshot, key);
            actions += domainCount;
            logAuditItem(key, domainCount);
            items++;
        }
        actions += exerciseIntegerDomain(
                defaults,
                snapshot,
                "pref_cards_preview_self_lines"
        );
        logAuditItem(
                "pref_cards_preview_self_lines",
                INTEGER_MAXIMUM - INTEGER_MINIMUM + 1
        );
        items++;

        actions += exerciseSavedViewDomain(savedViews, snapshot);
        logAuditItem("saved_views", SAVED_VIEW_VALUES.length);
        items++;

        actions += exerciseAppIconDomain(context, snapshot);
        logAuditItem("app_icon", ICON_AUDIT_VALUES.length);
        items++;

        if (items != AUDIT_ITEM_COUNT || actions != DOMAIN_ACTION_COUNT) {
            throw new IllegalStateException(
                    "domain audit count items=" + items
                            + " actions=" + actions
            );
        }
        Log.i(
                TAG,
                "MORPHE_DOMAIN_AUDIT_OK items=" + items
                        + " actions=" + actions
        );
    }

    private static int exerciseBooleanDomain(
            SharedPreferences defaults,
            SharedPreferences snapshot,
            String key
    ) throws Exception {
        String effectField = effectFieldForKey(key);
        Object originalEffect = effectField == null
                ? null
                : boostStaticField(effectField);
        boolean prefix = "pref_show_subreddit_prefix".equals(key);
        Object originalPrefix = prefix ? boostStaticField("c") : null;
        boolean expected = expectedAuditBoolean(snapshot, key);
        try {
            assertBooleanDomainValue(
                    defaults,
                    key,
                    !expected,
                    effectField,
                    prefix
            );
            assertBooleanDomainValue(
                    defaults,
                    key,
                    expected,
                    effectField,
                    prefix
            );
        } finally {
            if (prefix) {
                setBoostStaticField("c", originalPrefix);
            }
            if (effectField != null) {
                setBoostStaticField(effectField, originalEffect);
            }
        }
        return 2;
    }

    private static void assertBooleanDomainValue(
            SharedPreferences defaults,
            String key,
            boolean value,
            String effectField,
            boolean prefix
    ) throws Exception {
        if (effectField != null) {
            setBoostStaticField(effectField, false);
        }
        if (prefix) {
            setBoostStaticField("c", "__morphe_effect_audit__");
        }
        applyBooleanUiAction(defaults, key, value);
        assertNativeBoolean(key, value);
        assertEffectState(key, effectField);
        if (prefix) {
            String expectedPrefix = value ? "r/" : "";
            if (!expectedPrefix.equals(boostStaticField("c"))) {
                throw new IllegalStateException(
                        "subreddit prefix cache mismatch"
                );
            }
        }
    }

    private static int exerciseStringDomain(
            SharedPreferences defaults,
            SharedPreferences snapshot,
            String key
    ) throws Exception {
        String effectField = effectFieldForKey(key);
        Object originalEffect = effectField == null
                ? null
                : boostStaticField(effectField);
        String expected = expectedAuditString(snapshot, key);
        String[] domain = stringDomain(key);
        try {
            for (String value : domain) {
                if (!expected.equals(value)) {
                    assertStringDomainValue(
                            defaults,
                            key,
                            value,
                            effectField
                    );
                }
            }
            assertStringDomainValue(
                    defaults,
                    key,
                    expected,
                    effectField
            );
        } finally {
            if (effectField != null) {
                setBoostStaticField(effectField, originalEffect);
            }
        }
        return domain.length;
    }

    private static void assertStringDomainValue(
            SharedPreferences defaults,
            String key,
            String value,
            String effectField
    ) throws Exception {
        if (effectField != null) {
            setBoostStaticField(effectField, false);
        }
        applyStringUiAction(defaults, key, value);
        assertNativeString(key, value);
        assertEffectState(key, effectField);
    }

    private static int exerciseIntegerDomain(
            SharedPreferences defaults,
            SharedPreferences snapshot,
            String key
    ) throws Exception {
        Object originalEffect = boostStaticField("g");
        int expected = expectedAuditInteger(snapshot, key);
        try {
            for (int value = INTEGER_MINIMUM;
                    value <= INTEGER_MAXIMUM;
                    value++) {
                if (value != expected) {
                    assertIntegerDomainValue(defaults, key, value);
                }
            }
            assertIntegerDomainValue(defaults, key, expected);
        } finally {
            setBoostStaticField("g", originalEffect);
        }
        return INTEGER_MAXIMUM - INTEGER_MINIMUM + 1;
    }

    private static void assertIntegerDomainValue(
            SharedPreferences defaults,
            String key,
            int value
    ) throws Exception {
        setBoostStaticField("g", false);
        MorpheSettingsV4PostViewsFragment.applyPreviewLinesPreference(
                defaults,
                value
        );
        assertNativeInteger(key, value);
        assertEffectState(key, "g");
    }

    private static int exerciseSavedViewDomain(
            SharedPreferences savedViews,
            SharedPreferences snapshot
    ) {
        int expected = expectedSavedViewAuditValue(snapshot);
        for (int value : SAVED_VIEW_VALUES) {
            if (value != expected) {
                MorpheSettingsV4SavedViewsFragment.applySavedView(
                        savedViews,
                        SAVED_VIEW_AUDIT_KEY,
                        value
                );
                assertInteger(savedViews, SAVED_VIEW_AUDIT_KEY, value);
            }
        }
        MorpheSettingsV4SavedViewsFragment.applySavedView(
                savedViews,
                SAVED_VIEW_AUDIT_KEY,
                expected
        );
        assertInteger(savedViews, SAVED_VIEW_AUDIT_KEY, expected);
        return SAVED_VIEW_VALUES.length;
    }

    private static int exerciseAppIconDomain(
            Context context,
            SharedPreferences snapshot
    ) {
        String expected = snapshot.getString(SNAPSHOT_AUDIT_ICON_ALIAS, "grey");
        for (String value : ICON_AUDIT_VALUES) {
            if (!expected.equals(value)) {
                String alias = TextUtils.isEmpty(value) ? null : value;
                MorpheSettingsV4AppIconFragment.applyIconSelection(
                        context,
                        alias
                );
                assertSelectedIcon(context, alias);
            }
        }
        MorpheSettingsV4AppIconFragment.applyIconSelection(context, expected);
        assertSelectedIcon(context, expected);
        return ICON_AUDIT_VALUES.length;
    }

    private static void assertSelectedIcon(Context context, String expected) {
        if (!TextUtils.equals(
                expected,
                MorpheSettingsV4AppIconFragment.selectedAlias(context)
        )) {
            throw new IllegalStateException("app icon action did not apply");
        }
        if (!verifyIconComponents(context)) {
            throw new IllegalStateException("app icon components invalid");
        }
    }

    private static void assertEffectState(
            String key,
            String effectField
    ) throws Exception {
        if (effectField != null
                && !Boolean.TRUE.equals(boostStaticField(effectField))) {
            throw new IllegalStateException(
                    "native cache flag not invalidated for " + key
            );
        }
    }

    private static void runRenderedEffectProbes(
            Context context,
            SharedPreferences snapshot
    ) {
        int count = 0;
        TextView title = new TextView(context);
        String titleFont = expectedAuditString(snapshot, "pref_title_font");
        String titleSize = expectedAuditString(
                snapshot,
                "pref_font_size_title"
        );
        for (String value : FONT_VALUES) {
            MorpheSettingsV4FontsFragment.applyPreviewTypography(
                    title,
                    value,
                    titleSize,
                    true
            );
            assertTypography(title, value, titleSize, true);
        }
        for (String value : FONT_SIZE_VALUES) {
            MorpheSettingsV4FontsFragment.applyPreviewTypography(
                    title,
                    titleFont,
                    value,
                    true
            );
            assertTypography(title, titleFont, value, true);
        }
        MorpheSettingsV4FontsFragment.applyPreviewTypography(
                title,
                titleFont,
                titleSize,
                true
        );
        assertTypography(title, titleFont, titleSize, true);
        logRenderProbe(
                "title_typography",
                "pref_title_font,pref_font_size_title"
        );
        count++;

        TextView comments = new TextView(context);
        String commentsFont = expectedAuditString(
                snapshot,
                "pref_comments_font"
        );
        String commentsSize = expectedAuditString(
                snapshot,
                "pref_font_size"
        );
        for (String value : FONT_VALUES) {
            MorpheSettingsV4FontsFragment.applyPreviewTypography(
                    comments,
                    value,
                    commentsSize,
                    false
            );
            assertTypography(comments, value, commentsSize, false);
        }
        for (String value : FONT_SIZE_VALUES) {
            MorpheSettingsV4FontsFragment.applyPreviewTypography(
                    comments,
                    commentsFont,
                    value,
                    false
            );
            assertTypography(comments, commentsFont, value, false);
        }
        MorpheSettingsV4FontsFragment.applyPreviewTypography(
                comments,
                commentsFont,
                commentsSize,
                false
        );
        assertTypography(comments, commentsFont, commentsSize, false);
        logRenderProbe(
                "comment_typography",
                "pref_comments_font,pref_font_size"
        );
        count++;

        View savedViewsRow = new View(context);
        boolean remember = expectedAuditBoolean(
                snapshot,
                "pref_view_per_subscription"
        );
        MorpheSettingsV4PostViewsFragment.applyDependentRowState(
                savedViewsRow,
                null,
                !remember
        );
        assertEnabledState(savedViewsRow, null, !remember);
        MorpheSettingsV4PostViewsFragment.applyDependentRowState(
                savedViewsRow,
                null,
                remember
        );
        assertEnabledState(savedViewsRow, null, remember);
        logRenderProbe("saved_views_enabled", "pref_view_per_subscription");
        count++;

        View previewLinesRow = new View(context);
        SeekBar previewLinesControl = new SeekBar(context);
        boolean previewText = expectedAuditBoolean(
                snapshot,
                "pref_cards_preview_self"
        );
        MorpheSettingsV4PostViewsFragment.applyDependentRowState(
                previewLinesRow,
                previewLinesControl,
                !previewText
        );
        assertEnabledState(
                previewLinesRow,
                previewLinesControl,
                !previewText
        );
        MorpheSettingsV4PostViewsFragment.applyDependentRowState(
                previewLinesRow,
                previewLinesControl,
                previewText
        );
        assertEnabledState(
                previewLinesRow,
                previewLinesControl,
                previewText
        );
        logRenderProbe("preview_lines_enabled", "pref_cards_preview_self");
        count++;

        TextView defaultViewSummary = new TextView(context);
        Set<String> viewTitles = new HashSet<>();
        for (String value : VIEW_VALUES) {
            String renderedTitle = MorpheSettingsV4PostViewsFragment.viewTitle(
                    value
            );
            defaultViewSummary.setText(renderedTitle);
            if (TextUtils.isEmpty(defaultViewSummary.getText())
                    || !viewTitles.add(renderedTitle)) {
                throw new IllegalStateException(
                        "default-view summary render mismatch"
                );
            }
        }
        logRenderProbe("default_view_summary", "pref_view");
        count++;

        Activity activity = context instanceof Activity
                ? (Activity) context
                : null;
        if (activity == null) {
            throw new IllegalStateException("render probe missing activity");
        }
        Window window = activity.getWindow();
        int originalStatus = window.getStatusBarColor();
        int originalNavigation = window.getNavigationBarColor();
        MorpheSettingsV4Theme.Tokens tokens = MorpheSettingsV4Theme.resolve(
                context
        );
        try {
            BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(
                    activity,
                    tokens.background,
                    tokens.dark
            );
            assertSettingsSystemBarRendering(activity, tokens.background);
        } finally {
            BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);
            window.setStatusBarColor(originalStatus);
            window.setNavigationBarColor(originalNavigation);
        }
        logRenderProbe(
                "settings_shell_system_bars",
                "-"
        );
        count++;

        if (count != RENDER_PROBE_COUNT) {
            throw new IllegalStateException("render probe count " + count);
        }
        Log.i(TAG, "MORPHE_RENDER_AUDIT_OK count=" + count);
    }

    private static void assertSettingsSystemBarRendering(
            Activity activity,
            int expectedColor
    ) {
        Window window = activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (window == null || decor == null) {
            throw new IllegalStateException("system-bar render missing window");
        }

        int decorColor = backgroundColor(decor);
        int statusSurfaceColor = backgroundColor(
                decor.findViewWithTag(STATUS_BAR_SURFACE_TAG)
        );
        int navigationSurfaceColor = backgroundColor(
                decor.findViewWithTag(NAVIGATION_BAR_SURFACE_TAG)
        );

        // Android 15 enforces edge-to-edge for targetSdk 35: the status-bar
        // Window color is transparent and setStatusBarColor() has no visual
        // effect. The visible result is therefore owned by the inset surfaces.
        boolean legacyWindowColorsMatch = Build.VERSION.SDK_INT >= 35
                || window.getStatusBarColor() == expectedColor
                && window.getNavigationBarColor() == expectedColor;
        if (decorColor != expectedColor
                || statusSurfaceColor != expectedColor
                || navigationSurfaceColor != expectedColor
                || !legacyWindowColorsMatch) {
            throw new IllegalStateException(
                    "settings system-bar render mismatch"
                            + " sdk=" + Build.VERSION.SDK_INT
                            + " expected=" + colorHex(expectedColor)
                            + " decor=" + colorHex(decorColor)
                            + " status_surface=" + colorHex(statusSurfaceColor)
                            + " navigation_surface="
                            + colorHex(navigationSurfaceColor)
                            + " status_window="
                            + colorHex(window.getStatusBarColor())
                            + " navigation_window="
                            + colorHex(window.getNavigationBarColor())
            );
        }

        Log.i(
                TAG,
                "MORPHE_SYSTEM_BAR_RENDER_AUDIT_OK sdk="
                        + Build.VERSION.SDK_INT
                        + " mode="
                        + (Build.VERSION.SDK_INT >= 35
                        ? "edge_to_edge_surfaces"
                        : "legacy_window_and_surfaces")
                        + " color=" + colorHex(expectedColor)
        );
    }

    private static int backgroundColor(View view) {
        if (view == null) {
            return 0;
        }
        Drawable background = view.getBackground();
        return background instanceof ColorDrawable
                ? ((ColorDrawable) background).getColor()
                : 0;
    }

    private static String colorHex(int color) {
        return String.format("#%08X", color);
    }

    private static void assertTypography(
            TextView view,
            String font,
            String size,
            boolean title
    ) {
        if (view.getTypeface() == null
                || !view.getTypeface().equals(
                MorpheSettingsV4FontsFragment.typefaceFor(font)
        )) {
            throw new IllegalStateException("font render mismatch");
        }
        float expected = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                MorpheSettingsV4FontsFragment.previewSizeSp(size, title),
                view.getResources().getDisplayMetrics()
        );
        if (Math.abs(view.getTextSize() - expected) > 0.5f) {
            throw new IllegalStateException("text-size render mismatch");
        }
    }

    private static void assertEnabledState(
            View row,
            View control,
            boolean expected
    ) {
        float expectedAlpha = expected ? 1.0f : 0.44f;
        if (row.isEnabled() != expected
                || row.isClickable() != expected
                || row.isFocusable() != expected
                || Math.abs(row.getAlpha() - expectedAlpha) > 0.001f
                || control != null && control.isEnabled() != expected) {
            throw new IllegalStateException("dependent-row render mismatch");
        }
    }

    private static void logRenderProbe(String probe, String keys) {
        Log.i(
                TAG,
                "MORPHE_RENDER_AUDIT_ITEM_OK probe=" + probe
                        + " keys=" + keys
        );
    }

    private static void logAuditItem(String key, int domainCount) {
        String consumer;
        String effect;
        if ("saved_views".equals(key)) {
            consumer = "VIEW_PER_SUBSCRIPTION";
            effect = "canonical_named_preferences";
        } else if ("app_icon".equals(key)) {
            consumer = "PackageManager";
            effect = "launcher_alias";
        } else {
            consumer = "id.b." + nativeConsumerForKey(key);
            String field = effectFieldForKey(key);
            effect = field == null ? "direct_consumer" : field;
        }
        Log.i(
                TAG,
                "MORPHE_AUDIT_ITEM_OK key=" + key
                        + " domain_count=" + domainCount
                        + " consumer=" + consumer
                        + " effect=" + effect
                        + " render=" + renderProbeForKey(key)
        );
    }

    private static String renderProbeForKey(String key) {
        if ("pref_title_font".equals(key)
                || "pref_font_size_title".equals(key)) {
            return "title_typography";
        }
        if ("pref_comments_font".equals(key)
                || "pref_font_size".equals(key)) {
            return "comment_typography";
        }
        if ("pref_view_per_subscription".equals(key)) {
            return "saved_views_enabled";
        }
        if ("pref_view".equals(key)) {
            return "default_view_summary";
        }
        if ("pref_cards_preview_self".equals(key)) {
            return "preview_lines_enabled";
        }
        if ("app_icon".equals(key)) {
            return "launcher_components";
        }
        return "not_rendered";
    }

    private static ComponentName iconAliasComponent(
            Context context,
            String alias
    ) {
        return new ComponentName(
                context.getPackageName(),
                ICON_ALIAS_PREFIX + alias
        );
    }

    private static void applyBooleanUiAction(
            SharedPreferences defaults,
            String key,
            boolean value
    ) {
        if ("pref_dynamic_colors".equals(key)) {
            MorpheSettingsV4AppearanceFragment.applyDynamicColors(
                    defaults,
                    value
            );
            return;
        }
        if ("pref_colored_status_bar".equals(key)
                || "pref_colored_nav_bar".equals(key)) {
            MorpheSettingsV4AppearanceFragment.applySystemBarPreference(
                    defaults,
                    key,
                    value
            );
            return;
        }
        if ("pref_show_subreddit_prefix".equals(key)) {
            MorpheSettingsV4PostViewsFragment.applySubredditPrefix(
                    defaults,
                    value
            );
            return;
        }
        MorpheSettingsV4PostViewsFragment.applyBooleanPreference(
                defaults,
                key,
                value,
                effectFieldForKey(key)
        );
    }

    private static void applyStringUiAction(
            SharedPreferences defaults,
            String key,
            String value
    ) {
        if ("pref_view".equals(key)) {
            MorpheSettingsV4PostViewsFragment.applyDefaultViewPreference(
                    defaults,
                    value
            );
            return;
        }
        MorpheSettingsV4FontsFragment.applyFontPreference(
                defaults,
                key,
                value
        );
    }

    private static boolean verifyNativeConsumersAfterReload(
            Context context,
            SharedPreferences snapshot
    ) throws Exception {
        int count = 0;
        for (String key : BOOLEAN_KEYS) {
            assertNativeBoolean(
                    key,
                    expectedAuditBoolean(snapshot, key)
            );
            count++;
        }
        for (String key : STRING_KEYS) {
            assertNativeString(
                    key,
                    expectedAuditString(snapshot, key)
            );
            count++;
        }
        assertNativeInteger(
                "pref_cards_preview_self_lines",
                expectedAuditInteger(
                        snapshot,
                        "pref_cards_preview_self_lines"
                )
        );
        count++;

        SharedPreferences savedViews = context.getSharedPreferences(
                SAVED_VIEW_PREFERENCES,
                Context.MODE_PRIVATE
        );
        assertInteger(
                savedViews,
                SAVED_VIEW_AUDIT_KEY,
                expectedSavedViewAuditValue(snapshot)
        );
        count++;

        if (!verifyExpectedAuditIcon(context, snapshot)) {
            throw new IllegalStateException(
                    "app icon did not survive cold reload"
            );
        }
        count++;

        if (count != EFFECT_AUDIT_RELOAD_COUNT) {
            throw new IllegalStateException(
                    "effect audit reload count " + count
            );
        }
        Log.i(TAG, "MORPHE_DOMAIN_AUDIT_RELOAD_OK count=" + count);
        return true;
    }

    private static void assertNativeBoolean(
            String key,
            boolean expected
    ) throws Exception {
        Object actual = invokeNativeConsumer(nativeConsumerForKey(key));
        if (!(actual instanceof Boolean) || ((Boolean) actual) != expected) {
            throw new IllegalStateException(
                    "native boolean consumer mismatch for " + key
            );
        }
    }

    private static void assertNativeString(
            String key,
            String expected
    ) throws Exception {
        Object actual = invokeNativeConsumer(nativeConsumerForKey(key));
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "native string consumer mismatch for " + key
            );
        }
    }

    private static void assertNativeInteger(
            String key,
            int expected
    ) throws Exception {
        Object actual = invokeNativeConsumer(nativeConsumerForKey(key));
        if (!(actual instanceof Integer) || ((Integer) actual) != expected) {
            throw new IllegalStateException(
                    "native integer consumer mismatch for " + key
            );
        }
    }

    private static Object invokeNativeConsumer(String methodName)
            throws Exception {
        Class<?> settingsClass = Class.forName("id.b");
        Method singleton = settingsClass.getDeclaredMethod("v0");
        singleton.setAccessible(true);
        Object settings = singleton.invoke(null);
        Method consumer = settingsClass.getDeclaredMethod(methodName);
        consumer.setAccessible(true);
        return consumer.invoke(settings);
    }

    private static Object boostStaticField(String name) throws Exception {
        Field field = Class.forName("id.b").getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setBoostStaticField(String name, Object value)
            throws Exception {
        Field field = Class.forName("id.b").getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static String effectFieldForKey(String key) {
        switch (key) {
            case "pref_dynamic_colors":
            case "pref_lock_sidebar":
            case "pref_show_subreddit_prefix":
                return "i";
            case "pref_colored_status_bar":
            case "pref_colored_nav_bar":
            case "pref_left_handed":
            case "pref_dense_buttons_visible":
            case "pref_title_font":
                return "h";
            case "pref_cards_rounded_corners":
            case "pref_cards_full_preview":
            case "pref_cards_subreddit_icon":
            case "pref_cards_gallery_carousel":
            case "pref_cards_links_as_thumbnails":
            case "pref_cards_preview_self":
            case "pref_cards_preview_self_lines":
            case "pref_mini_cards_rounded_corners":
            case "pref_mini_cards_truncate_title":
            case "pref_mini_cards_buttons_visible":
                return "g";
            case "pref_font_size_title":
            case "pref_font_size":
                return "d";
            default:
                return null;
        }
    }

    private static String nativeConsumerForKey(String key) {
        switch (key) {
            case "pref_dynamic_colors": return "m2";
            case "pref_colored_status_bar": return "r1";
            case "pref_colored_nav_bar": return "m1";
            case "pref_view": return "Y3";
            case "pref_view_per_subscription": return "L2";
            case "pref_left_handed": return "s2";
            case "pref_show_subreddit_prefix": return "S7";
            case "pref_cards_rounded_corners": return "Y0";
            case "pref_cards_full_preview": return "V0";
            case "pref_cards_subreddit_icon": return "e3";
            case "pref_cards_gallery_carousel": return "L3";
            case "pref_cards_links_as_thumbnails": return "Q";
            case "pref_cards_preview_self": return "f4";
            case "pref_cards_preview_self_lines": return "g4";
            case "pref_mini_cards_rounded_corners": return "D2";
            case "pref_mini_cards_truncate_title": return "Y7";
            case "pref_mini_cards_buttons_visible": return "B2";
            case "pref_dense_buttons_visible": return "h2";
            case "pref_load_readability": return "I0";
            case "pref_lock_sidebar": return "w2";
            case "pref_title_font": return "m4";
            case "pref_font_size_title": return "q0";
            case "pref_comments_font": return "W";
            case "pref_font_size": return "p0";
            default:
                throw new IllegalArgumentException(
                        "missing native consumer for " + key
                );
        }
    }

    private static boolean verifyPersistedAuditValues(
            Context context,
            SharedPreferences snapshot
    ) {
        SharedPreferences defaults =
                PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences savedViews = context.getSharedPreferences(
                SAVED_VIEW_PREFERENCES,
                Context.MODE_PRIVATE
        );
        for (String key : BOOLEAN_KEYS) {
            assertBoolean(defaults, key, expectedAuditBoolean(snapshot, key));
        }
        for (String key : STRING_KEYS) {
            assertString(defaults, key, expectedAuditString(snapshot, key));
        }
        for (String key : INTEGER_KEYS) {
            assertInteger(defaults, key, expectedAuditInteger(snapshot, key));
        }
        assertInteger(
                savedViews,
                SAVED_VIEW_AUDIT_KEY,
                expectedSavedViewAuditValue(snapshot)
        );
        return true;
    }

    private static boolean restoreOriginalValues(
            Context context,
            SharedPreferences snapshot
    ) {
        if (!snapshot.getBoolean(SNAPSHOT_ACTIVE, false)) {
            return true;
        }
        SharedPreferences defaults =
                PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences savedViews = context.getSharedPreferences(
                SAVED_VIEW_PREFERENCES,
                Context.MODE_PRIVATE
        );

        SharedPreferences.Editor defaultEditor = defaults.edit();
        for (String key : BOOLEAN_KEYS) {
            restoreValue(defaultEditor, snapshot, "default", key);
        }
        for (String key : STRING_KEYS) {
            restoreValue(defaultEditor, snapshot, "default", key);
        }
        for (String key : INTEGER_KEYS) {
            restoreValue(defaultEditor, snapshot, "default", key);
        }
        SharedPreferences.Editor savedEditor = savedViews.edit();
        restoreValue(savedEditor, snapshot, "saved", SAVED_VIEW_AUDIT_KEY);

        boolean defaultsRestored = defaultEditor.commit();
        boolean savedViewsRestored = savedEditor.commit();
        boolean preferencesRestored = defaultsRestored
                && savedViewsRestored
                && restoredValuesMatch(defaults, savedViews, snapshot);
        boolean iconsRestored = restoreIconComponents(context, snapshot);
        return preferencesRestored && iconsRestored;
    }

    private static boolean restoreIconComponents(
            Context context,
            SharedPreferences snapshot
    ) {
        if (!snapshot.getBoolean(SNAPSHOT_ICON_STATES_PRESENT, false)) {
            return true;
        }
        PackageManager packageManager = context.getPackageManager();
        try {
            for (String alias : ICON_ALIASES) {
                packageManager.setComponentEnabledSetting(
                        iconAliasComponent(context, alias),
                        snapshot.getInt(
                                "icon_state:" + alias,
                                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        ),
                        PackageManager.DONT_KILL_APP
                );
            }
            for (String alias : ICON_ALIASES) {
                int expected = snapshot.getInt(
                        "icon_state:" + alias,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                );
                int actual = packageManager.getComponentEnabledSetting(
                        iconAliasComponent(context, alias)
                );
                if (actual != expected) {
                    return false;
                }
            }
            return true;
        } catch (Throwable throwable) {
            Log.e(TAG, "MORPHE_BINDING_AUDIT_ICON_RESTORE_FAIL", throwable);
            return false;
        }
    }

    private static void restoreValue(
            SharedPreferences.Editor editor,
            SharedPreferences snapshot,
            String store,
            String key
    ) {
        String identity = store + ":" + key;
        if (!snapshot.getBoolean("present:" + identity, false)) {
            editor.remove(key);
            return;
        }
        String type = snapshot.getString("type:" + identity, "");
        String valueKey = "value:" + identity;
        if ("boolean".equals(type)) {
            editor.putBoolean(key, snapshot.getBoolean(valueKey, false));
        } else if ("string".equals(type)) {
            editor.putString(key, snapshot.getString(valueKey, ""));
        } else if ("integer".equals(type)) {
            editor.putInt(key, snapshot.getInt(valueKey, 0));
        } else if ("long".equals(type)) {
            editor.putLong(key, snapshot.getLong(valueKey, 0L));
        } else if ("float".equals(type)) {
            editor.putFloat(key, snapshot.getFloat(valueKey, 0.0f));
        } else if ("string_set".equals(type)) {
            Set<String> value = snapshot.getStringSet(valueKey, new HashSet<String>());
            editor.putStringSet(key, new HashSet<>(value));
        } else {
            throw new IllegalStateException("missing snapshot type for " + identity);
        }
    }

    private static boolean restoredValuesMatch(
            SharedPreferences defaults,
            SharedPreferences savedViews,
            SharedPreferences snapshot
    ) {
        for (String key : BOOLEAN_KEYS) {
            if (!restoredValueMatches(defaults, snapshot, "default", key)) {
                return false;
            }
        }
        for (String key : STRING_KEYS) {
            if (!restoredValueMatches(defaults, snapshot, "default", key)) {
                return false;
            }
        }
        for (String key : INTEGER_KEYS) {
            if (!restoredValueMatches(defaults, snapshot, "default", key)) {
                return false;
            }
        }
        return restoredValueMatches(
                savedViews,
                snapshot,
                "saved",
                SAVED_VIEW_AUDIT_KEY
        );
    }

    private static boolean restoredValueMatches(
            SharedPreferences preferences,
            SharedPreferences snapshot,
            String store,
            String key
    ) {
        String identity = store + ":" + key;
        boolean expectedPresent = snapshot.getBoolean("present:" + identity, false);
        if (preferences.contains(key) != expectedPresent) {
            return false;
        }
        if (!expectedPresent) {
            return true;
        }
        Object expected = snapshot.getAll().get("value:" + identity);
        Object actual = preferences.getAll().get(key);
        return expected == null ? actual == null : expected.equals(actual);
    }

    private static boolean expectedAuditBoolean(
            SharedPreferences snapshot,
            String key
    ) {
        String identity = "default:" + key;
        boolean defaultValue = defaultBooleanForKey(key);
        boolean original = snapshot.getBoolean("present:" + identity, false)
                ? snapshot.getBoolean("value:" + identity, defaultValue)
                : defaultValue;
        return !original;
    }

    private static String expectedAuditString(
            SharedPreferences snapshot,
            String key
    ) {
        String identity = "default:" + key;
        String defaultValue = defaultStringForKey(key);
        String original = snapshot.getBoolean("present:" + identity, false)
                ? snapshot.getString("value:" + identity, defaultValue)
                : defaultValue;
        String[] domain = stringDomain(key);
        for (String candidate : domain) {
            if (!original.equals(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no alternate string value for " + key);
    }

    private static int expectedAuditInteger(
            SharedPreferences snapshot,
            String key
    ) {
        String identity = "default:" + key;
        int original = snapshot.getInt("value:" + identity, INTEGER_MINIMUM);
        return original == 37 ? 38 : 37;
    }

    private static int expectedSavedViewAuditValue(SharedPreferences snapshot) {
        String identity = "saved:" + SAVED_VIEW_AUDIT_KEY;
        Object originalValue = snapshot.getAll().get("value:" + identity);
        int original = originalValue instanceof Integer
                ? (Integer) originalValue
                : Integer.MIN_VALUE;
        for (int candidate : SAVED_VIEW_VALUES) {
            if (candidate != original) {
                return candidate;
            }
        }
        throw new IllegalStateException("no alternate saved-view value");
    }

    private static boolean defaultBooleanForKey(String key) {
        for (String candidate : BOOLEAN_DEFAULT_TRUE_KEYS) {
            if (candidate.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String defaultStringForKey(String key) {
        if ("pref_view".equals(key)) {
            return "0";
        }
        if ("pref_title_font".equals(key) || "pref_comments_font".equals(key)) {
            return "";
        }
        if ("pref_font_size_title".equals(key) || "pref_font_size".equals(key)) {
            return "Medium";
        }
        throw new IllegalArgumentException("missing string default for " + key);
    }

    private static String[] stringDomain(String key) {
        if ("pref_view".equals(key)) {
            return VIEW_VALUES;
        }
        if ("pref_title_font".equals(key) || "pref_comments_font".equals(key)) {
            return FONT_VALUES;
        }
        if ("pref_font_size_title".equals(key) || "pref_font_size".equals(key)) {
            return FONT_SIZE_VALUES;
        }
        throw new IllegalArgumentException("missing domain for " + key);
    }

    private static boolean verifyExpectedAuditIcon(
            Context context,
            SharedPreferences snapshot
    ) {
        String expected = snapshot.getString(SNAPSHOT_AUDIT_ICON_ALIAS, "grey");
        return TextUtils.equals(
                expected,
                MorpheSettingsV4AppIconFragment.selectedAlias(context)
        );
    }

    private static boolean verifyIconComponents(Context context) {
        for (String resource : ICON_RESOURCES) {
            if (context.getResources().getIdentifier(
                    resource,
                    "mipmap",
                    context.getPackageName()
            ) == 0) {
                return false;
            }
        }

        PackageManager packageManager = context.getPackageManager();
        int enabledAliases = 0;
        for (String alias : ICON_ALIASES) {
            ComponentName component = new ComponentName(
                    context.getPackageName(),
                    ICON_ALIAS_PREFIX + alias
            );
            try {
                packageManager.getActivityInfo(
                        component,
                        PackageManager.MATCH_DISABLED_COMPONENTS
                );
            } catch (PackageManager.NameNotFoundException exception) {
                return false;
            }
            if (packageManager.getComponentEnabledSetting(component)
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                enabledAliases++;
            }
        }
        return enabledAliases <= 1;
    }

    private static void assertBoolean(
            SharedPreferences preferences,
            String key,
            boolean expected
    ) {
        if (!preferences.contains(key) || preferences.getBoolean(key, !expected) != expected) {
            throw new IllegalStateException("boolean readback failed for " + key);
        }
    }

    private static void assertString(
            SharedPreferences preferences,
            String key,
            String expected
    ) {
        if (!preferences.contains(key)
                || !expected.equals(preferences.getString(key, null))) {
            throw new IllegalStateException("string readback failed for " + key);
        }
    }

    private static void assertInteger(
            SharedPreferences preferences,
            String key,
            int expected
    ) {
        if (!preferences.contains(key) || preferences.getInt(key, -1) != expected) {
            throw new IllegalStateException("integer readback failed for " + key);
        }
    }
}
