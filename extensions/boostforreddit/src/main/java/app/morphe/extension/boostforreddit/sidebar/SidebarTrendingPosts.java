package app.morphe.extension.boostforreddit.sidebar;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Restores Boost's sidebar "Trending today" data path when Reddit's legacy
 * trending-search endpoint returns no renderable rows.
 *
 * The native loader still runs first. Only an empty/non-renderable native
 * result falls back to public HOT listings, converted into Boost's own
 * TrendingSearch and SubmissionModel objects through reflection.
 */
public final class SidebarTrendingPosts {
    private static final String TAG = "MorpheSidebarTrending";
    private static final String MARKER = "MORPHE_SIDEBAR_TRENDING_POSTS_V3";

    private static final Object LOCK = new Object();
    private static final int LISTING_LIMIT = 75;
    private static final int FINAL_LIMIT = 12;
    private static final long CACHE_MAX_AGE_MS = 30L * 60L * 1000L;

    private static List<Object> cachedFallback = Collections.emptyList();
    private static long cachedFallbackAtMs;

    /*
     * Identity is deliberate: native and fallback rows use the same Boost
     * model class and may compare equal by content. Only rows constructed by
     * this extension should receive fallback-specific rendering policy.
     */
    private static final Set<Object> fallbackRows =
        Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

    /*
     * Bind parameters are safe at method entry, but Boost may reuse p1 later.
     * Record the row classification at entry and apply it at every return.
     * Weak keys avoid retaining recycled holders after the adapter releases them.
     */
    private static final Map<Object, Boolean> holderFallbackState =
        new WeakHashMap<Object, Boolean>();

    private SidebarTrendingPosts() {
    }

    /** Called from Boost's TrendingTodayAsyncLoader background method. */
    public static List load() {
        List nativeRows = loadNativeRows();
        List<Object> renderableNative = filterRenderableRows(nativeRows);
        if (!renderableNative.isEmpty()) {
            log("source=native rows=" + renderableNative.size() + " marker=" + MARKER);
            return renderableNative;
        }

        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (!cachedFallback.isEmpty()
                && now >= cachedFallbackAtMs
                && now - cachedFallbackAtMs < CACHE_MAX_AGE_MS) {
                log("source=fallback_cache rows=" + cachedFallback.size()
                    + " ageMs=" + (now - cachedFallbackAtMs)
                    + " marker=" + MARKER);
                return new ArrayList<Object>(cachedFallback);
            }

            try {
                List<Object> fallback = fetchFallbackRows();
                if (!fallback.isEmpty()) {
                    cachedFallback = new ArrayList<Object>(fallback);
                    cachedFallbackAtMs = now;
                    log("source=popular_hot rows=" + fallback.size() + " marker=" + MARKER);
                    return fallback;
                }
                log("source=popular_hot rows=0 marker=" + MARKER);
            } catch (Throwable t) {
                logFailure("fallback_failed", t);
            }
        }

        return renderableNative;
    }

    /**
     * Removes the meaningless global "Limit to Community" control. When a
     * concrete subreddit context can be resolved, it is retained as
     * "Limit to r/<name>" and preloaded without enabling the checkbox.
     */
    public static void configureSearchOptions(Object fragment, View root) {
        if (root == null) {
            return;
        }

        try {
            Object activity = invokeNoArg(fragment, "getActivity");
            String community = resolveCurrentCommunity(activity);
            View limitGroup = findView(root, "limit_group");

            if (limitGroup == null) {
                log("limit_control=missing marker=" + MARKER);
                return;
            }

            if (community == null) {
                Object parent = limitGroup.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(limitGroup);
                    log("limit_control=removed context=global marker=" + MARKER);
                } else {
                    limitGroup.setVisibility(View.GONE);
                    log("limit_control=hidden context=global marker=" + MARKER);
                }
                return;
            }

            View checkboxView = findView(root, "filter_limit");
            if (checkboxView instanceof TextView) {
                ((TextView) checkboxView).setText("Limit to r/" + community);
            }

            View valueView = findView(root, "limit_view");
            if (valueView instanceof TextView) {
                ((TextView) valueView).setText(community);
            }

            if (checkboxView instanceof CompoundButton) {
                ((CompoundButton) checkboxView).setChecked(false);
            }

            log("limit_control=retained context=r/" + community + " marker=" + MARKER);
        } catch (Throwable t) {
            logFailure("limit_control_failed", t);
        }
    }

    /**
     * Records whether the row currently bound to this holder was constructed
     * by the Morphe fallback. This is called at bind entry while p1 still has
     * the declared TrendingSearch type.
     */
    public static void recordBoundRow(Object holder, Object row) {
        if (holder == null) {
            return;
        }

        synchronized (LOCK) {
            holderFallbackState.put(holder, Boolean.valueOf(fallbackRows.contains(row)));
        }
    }

    /**
     * Hides the secondary SubmissionModel title for Morphe fallback rows only.
     * Native Reddit trending rows keep Boost's original two-line presentation.
     * The explicit VISIBLE reset is required because RecyclerView holders are
     * reused after a fallback row has hidden the description view.
     */
    public static void applyBoundTextPolicy(Object holder) {
        if (holder == null) {
            return;
        }

        try {
            Field itemViewField = holder.getClass().getField("itemView");
            Object value = itemViewField.get(holder);
            if (!(value instanceof View)) {
                return;
            }

            View descriptionView = findView((View) value, "item_description");
            if (!(descriptionView instanceof TextView)) {
                return;
            }

            boolean fallback;
            synchronized (LOCK) {
                fallback = Boolean.TRUE.equals(holderFallbackState.get(holder));
            }

            descriptionView.setVisibility(fallback ? View.GONE : View.VISIBLE);
        } catch (Throwable t) {
            logFailure("bound_text_policy_failed", t);
        }
    }

    /** Starts the sidebar load immediately instead of waiting for Boost's 8-second delayed refresh. */
    public static void triggerLoad(Object fragment) {
        if (fragment == null) {
            return;
        }

        try {
            Class<?> current = fragment.getClass();
            while (current != null) {
                for (Field field : current.getDeclaredFields()) {
                    if (!"od.d".equals(field.getType().getName())) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object viewModel = field.get(fragment);
                    if (viewModel != null) {
                        viewModel.getClass().getMethod("h").invoke(viewModel);
                        log("load_trigger=immediate marker=" + MARKER);
                        return;
                    }
                }
                current = current.getSuperclass();
            }
            log("load_trigger=view_model_missing marker=" + MARKER);
        } catch (Throwable t) {
            logFailure("load_trigger_failed", t);
        }
    }

    private static List loadNativeRows() {
        try {
            Class<?> repositoryClass = Class.forName("xb.l");
            Object repository = repositoryClass.getMethod("V").invoke(null);
            Object value = repositoryClass.getMethod("x0").invoke(repository);
            return value instanceof List ? (List) value : Collections.emptyList();
        } catch (Throwable t) {
            logFailure("native_load_failed", t);
            return Collections.emptyList();
        }
    }

    private static List<Object> filterRenderableRows(List rows) {
        ArrayList<Object> result = new ArrayList<Object>();
        if (rows == null) {
            return result;
        }

        for (Object row : rows) {
            try {
                Object submissions = row.getClass().getMethod("b").invoke(row);
                if (submissions instanceof List && !((List) submissions).isEmpty()) {
                    result.add(row);
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static List<Object> fetchFallbackRows() throws Exception {
        Object client = getBoostRedditClient();
        boolean includeNsfw = includeNsfw();

        List raw = fetchHotListing(client, "popular");
        if (raw.isEmpty()) {
            raw = fetchHotListing(client, "all");
        }

        ArrayList<Object> rows = new ArrayList<Object>();
        Set<String> seen = new HashSet<String>();

        for (int i = 0; i < raw.size() && rows.size() < FINAL_LIMIT; i++) {
            Object submission = raw.get(i);
            if (submission == null || isTrue(submission, "isStickied")) {
                continue;
            }
            if (isTrue(submission, "isPromoted") || isTrue(submission, "isAdvertisement")) {
                continue;
            }
            if (!includeNsfw && (isTrue(submission, "isNsfw") || isTrue(submission, "isOver18"))) {
                continue;
            }

            String title = firstNonEmpty(
                callString(submission, "getTitle"),
                jsonString(submission, "title")
            );
            if (title == null) {
                continue;
            }
            title = normalizeTitle(title);
            if (title.length() == 0) {
                continue;
            }

            String dedupe = firstNonEmpty(
                callString(submission, "getFullName"),
                callString(submission, "getId"),
                title.toLowerCase(Locale.US)
            );
            if (dedupe == null || !seen.add(dedupe)) {
                continue;
            }

            Object model = makeSubmissionModel(submission);
            if (model == null) {
                continue;
            }

            Object row = makeTrendingRow(title, model);
            if (row != null) {
                rows.add(row);
            }
        }

        if (!rows.isEmpty()) {
            // load() holds LOCK while this method runs.
            fallbackRows.clear();
            fallbackRows.addAll(rows);
        }

        return rows;
    }

    private static List fetchHotListing(Object client, String source) throws Exception {
        Class<?> paginatorClass = Class.forName("net.dean.jraw.paginators.SubredditPaginator");
        Object paginator = constructSourcePaginator(paginatorClass, client, source);
        if (paginator == null) {
            throw new NoSuchMethodException("SubredditPaginator for " + source);
        }

        applySorting(paginator, "HOT");
        applyLimit(paginator, LISTING_LIMIT);

        Object listing = paginator.getClass().getMethod("next").invoke(paginator);
        if (!(listing instanceof Iterable)) {
            return Collections.emptyList();
        }

        ArrayList<Object> result = new ArrayList<Object>();
        for (Object item : (Iterable) listing) {
            result.add(item);
        }
        return result;
    }

    private static Object constructSourcePaginator(Class<?> paginatorClass, Object client, String source) {
        for (Constructor<?> constructor : paginatorClass.getConstructors()) {
            try {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 3
                    && parameters[0].isAssignableFrom(client.getClass())
                    && parameters[1] == String.class
                    && parameters[2].isArray()
                    && parameters[2].getComponentType() == String.class) {
                    return constructor.newInstance(new Object[] { client, source, new String[0] });
                }
                if (parameters.length == 2
                    && parameters[0].isAssignableFrom(client.getClass())
                    && parameters[1] == String.class) {
                    return constructor.newInstance(client, source);
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void applySorting(Object paginator, String wanted) throws Exception {
        for (Method method : paginator.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            String name = method.getName().toLowerCase(Locale.US);
            if (parameters.length != 1 || !parameters[0].isEnum()) {
                continue;
            }
            if (!name.contains("sort") && !name.contains("where")) {
                continue;
            }
            Object value = enumConstant(parameters[0], wanted);
            if (value != null) {
                method.invoke(paginator, value);
                return;
            }
        }
        throw new NoSuchMethodException("HOT sorting setter");
    }

    private static void applyLimit(Object paginator, int limit) {
        for (String name : new String[] { "setLimit", "limit" }) {
            try {
                paginator.getClass().getMethod(name, int.class).invoke(paginator, Integer.valueOf(limit));
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static Object enumConstant(Class<?> enumClass, String wanted) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            if (constant != null && wanted.equalsIgnoreCase(String.valueOf(constant))) {
                return constant;
            }
        }
        return null;
    }

    private static Object makeSubmissionModel(Object submission) {
        try {
            Class<?> modelClass = Class.forName("com.rubenmayayo.reddit.models.reddit.SubmissionModel");
            for (Method method : modelClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers())
                    || parameters.length != 1
                    || !modelClass.isAssignableFrom(method.getReturnType())
                    || !parameters[0].isAssignableFrom(submission.getClass())) {
                    continue;
                }
                method.setAccessible(true);
                Object result = method.invoke(null, submission);
                if (result != null) {
                    return result;
                }
            }
        } catch (Throwable t) {
            logFailure("submission_model_failed", t);
        }
        return null;
    }

    private static Object makeTrendingRow(String title, Object submissionModel) {
        try {
            Class<?> rowClass = Class.forName("com.rubenmayayo.reddit.models.reddit.m");
            Object row = rowClass.getConstructor().newInstance();
            rowClass.getMethod("c", String.class).invoke(row, title);
            ArrayList<Object> models = new ArrayList<Object>();
            models.add(submissionModel);
            rowClass.getMethod("d", List.class).invoke(row, models);
            return row;
        } catch (Throwable t) {
            logFailure("trending_row_failed", t);
            return null;
        }
    }

    private static Object getBoostRedditClient() throws Exception {
        Class<?> repositoryClass = Class.forName("xb.l");
        Object repository = repositoryClass.getMethod("V").invoke(null);
        Class<?> current = repository.getClass();

        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                String type = field.getType().getName();
                if (!type.equals("xb.j") && !type.contains("RedditClient")) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(repository);
                if (value != null) {
                    return value;
                }
            }
            current = current.getSuperclass();
        }

        throw new NoSuchFieldException("Boost RedditClient");
    }

    private static boolean includeNsfw() {
        try {
            Class<?> preferences = Class.forName("id.b");
            Object instance = preferences.getMethod("v0").invoke(null);
            Object value = preferences.getMethod("J5").invoke(instance);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable t) {
            logFailure("nsfw_preference_failed", t);
            return false;
        }
    }

    private static String resolveCurrentCommunity(Object activity) {
        if (activity == null) {
            return null;
        }

        try {
            Method method = findNoArgMethod(activity.getClass(), "W1");
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            Object value = method.invoke(activity);
            if (!(value instanceof String)) {
                return null;
            }

            String name = ((String) value).trim();
            while (name.startsWith("/")) {
                name = name.substring(1);
            }
            if (name.startsWith("r/")) {
                name = name.substring(2);
            }
            if (name.length() == 0 || isGlobalSource(name)) {
                return null;
            }
            return name;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isGlobalSource(String value) {
        String name = value.toLowerCase(Locale.US).trim();

        // Boost uses this internal sentinel for Home/front-page context. It is
        // not a subreddit and must never be exposed as "Limit to r/...".
        if ("_load_front_page_this_is_not_a_subreddit".equals(name)
            || name.startsWith("_load_front_page")
            || name.contains("not_a_subreddit")) {
            return true;
        }

        return "all".equals(name)
            || "popular".equals(name)
            || "frontpage".equals(name)
            || "front_page".equals(name)
            || "home".equals(name)
            || "friends".equals(name)
            || "default".equals(name);
    }

    private static View findView(View root, String name) {
        try {
            int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
            return id == 0 ? null : root.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isTrue(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Boolean && ((Boolean) value).booleanValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String callString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String jsonString(Object item, String key) {
        try {
            Object data = item.getClass().getMethod("getDataNode").invoke(item);
            Object child = data.getClass().getMethod("get", String.class).invoke(data, key);
            Object value = child.getClass().getMethod("asText").invoke(child);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalizeTitle(String value) {
        String title = value.replaceAll("\\s+", " ").trim();
        if (title.length() > 120) {
            title = title.substring(0, 117).trim() + "…";
        }
        return title;
    }

    private static void log(String message) {
        Log.i(TAG, message);
    }

    private static void logFailure(String message, Throwable throwable) {
        Log.w(TAG, message + " type=" + throwable.getClass().getName()
            + " message=" + String.valueOf(throwable.getMessage()));
    }
}
