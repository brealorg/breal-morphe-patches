package app.morphe.extension.boostforreddit.subreddit;

import app.morphe.extension.boostforreddit.utils.LoggingUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class RandomSubredditResolver {
    private static final String MARKER = "morphe_boost_random_subreddit_dynamic_resolver";
    private static final String RANDOM_SUBREDDIT = "random";

    private static final long MIN_SUBSCRIBERS = 50L;
    private static final long SOFT_MAX_SUBSCRIBERS = 50_000L;
    private static final long HARD_MAX_SUBSCRIBERS = 100_000L;
    private static final int MAX_SEARCH_ATTEMPTS = 4;
    private static final int LOOKUP_TIMEOUT_MS = 5_500;
    private static final int RECENT_LIMIT = 24;

    private static final Object LOCK = new Object();
    private static final Random RANDOM = new Random();
    private static final Set<String> RECENT = new LinkedHashSet<>();
    private static String last = null;

    private static final String[] QUERY_SEEDS = new String[] {
            "ab", "ac", "ad", "ae", "af", "ag", "ak", "al", "am", "an", "ap", "ar", "as", "at", "av",
            "ba", "be", "bi", "bo", "br", "bu",
            "ca", "ce", "ch", "co", "cr", "cu",
            "de", "di", "do", "dr", "du",
            "ea", "ec", "ed", "ef", "eg", "el", "em", "en", "er", "es", "et",
            "fa", "fi", "fo", "fr",
            "ga", "ge", "gl", "go", "gr",
            "ha", "he", "hi", "ho", "hu",
            "ia", "ic", "id", "ig", "il", "im", "in", "io", "ir", "is",
            "ka", "ki", "ko",
            "la", "le", "li", "lo", "lu",
            "ma", "me", "mi", "mo", "mu",
            "na", "ne", "ni", "no", "nu",
            "ob", "oc", "od", "of", "og", "ol", "om", "on", "op", "or", "os",
            "pa", "pe", "ph", "pi", "pl", "po", "pr",
            "qu",
            "ra", "re", "ri", "ro", "ru",
            "sa", "sc", "se", "sh", "si", "sk", "sl", "so", "sp", "st", "su",
            "ta", "te", "th", "ti", "to", "tr", "tu",
            "ul", "um", "un", "ur",
            "va", "ve", "vi", "vo",
            "wa", "we", "wi", "wo",
            "ya", "ye", "yo",
            "za", "ze", "zo",
            "analog", "archive", "basement", "botany", "bottle", "cabinet", "craft", "diagram",
            "field", "folk", "forgotten", "garden", "gear", "handmade", "index", "library",
            "manual", "micro", "museum", "repair", "small", "tool", "urban", "vintage"
    };

    private RandomSubredditResolver() {
    }

    public static Object normalize(Object subscription) {
        if (subscription == null) {
            return null;
        }

        try {
            Object name = subscription.getClass().getMethod("z").invoke(subscription);
            if (!(name instanceof String) || !RANDOM_SUBREDDIT.equalsIgnoreCase((String) name)) {
                return subscription;
            }

            final String picked = pick();
            if (!isValidName(picked)) {
                log("no dynamic replacement; keeping original random route");
                return subscription;
            }

            Object replacement = subscription.getClass()
                    .getConstructor(String.class)
                    .newInstance(picked);

            log("normalized SubscriptionViewModel random -> " + picked);
            return replacement;
        } catch (Throwable ignored) {
            return subscription;
        }
    }

    public static String pick() {
        FutureTask<String> task = new FutureTask<>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return pickDynamic();
            }
        });

        Thread worker = new Thread(task, "morphe-random-subreddit-dynamic");
        worker.setDaemon(true);
        worker.start();

        try {
            String picked = task.get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (isValidName(picked)) {
                return picked;
            }
        } catch (Throwable t) {
            logFailure("dynamic lookup failed", t);
        }

        task.cancel(true);
        return null;
    }

    private static String pickDynamic() throws Exception {
        Object client = getBoostRedditClient();
        Method search = client.getClass().getMethod("searchSubredditsCustom", String.class, boolean.class);

        for (int attempt = 0; attempt < MAX_SEARCH_ATTEMPTS && !Thread.currentThread().isInterrupted(); attempt++) {
            String query = nextQuery();
            Candidate picked = searchOnce(client, search, query);

            if (picked != null) {
                remember(picked.name);
                log("dynamic query=" + picked.query + " -> " + picked.name + " subscribers=" + picked.subscribers);
                return picked.name;
            }

            log("dynamic query=" + query + " -> no acceptable candidate");
        }

        return null;
    }

    private static Object getBoostRedditClient() throws Exception {
        Class<?> managerClass = Class.forName("xb.l");
        Object manager = managerClass.getMethod("V").invoke(null);
        Field clientField = managerClass.getField("g");
        Object client = clientField.get(manager);

        if (client == null) {
            throw new IllegalStateException("Boost RedditClient is null");
        }

        return client;
    }

    private static Candidate searchOnce(Object client, Method search, String query) throws Exception {
        Object result = search.invoke(client, query, Boolean.FALSE);
        if (!(result instanceof Iterable)) {
            return null;
        }

        ArrayList<Candidate> preferred = new ArrayList<>();
        ArrayList<Candidate> accepted = new ArrayList<>();

        for (Object item : (Iterable<?>) result) {
            Candidate candidate = toCandidate(item, query);
            if (candidate == null) {
                continue;
            }

            if (candidate.subscribers < MIN_SUBSCRIBERS || candidate.subscribers > HARD_MAX_SUBSCRIBERS) {
                continue;
            }

            if (isRejectedName(candidate.name)) {
                continue;
            }

            accepted.add(candidate);

            if (candidate.subscribers <= SOFT_MAX_SUBSCRIBERS) {
                preferred.add(candidate);
            }
        }

        if (!preferred.isEmpty()) {
            return choose(preferred);
        }

        if (!accepted.isEmpty()) {
            return choose(accepted);
        }

        return null;
    }

    private static Candidate toCandidate(Object item, String query) {
        if (item == null) {
            return null;
        }

        try {
            Object nameValue = item.getClass().getMethod("getName").invoke(item);
            Object subscriberValue = item.getClass().getMethod("getSubscriberCount").invoke(item);

            if (!(nameValue instanceof String) || !(subscriberValue instanceof Number)) {
                return null;
            }

            String name = ((String) nameValue).trim();
            long subscribers = ((Number) subscriberValue).longValue();

            if (!isValidName(name)) {
                return null;
            }

            return new Candidate(name, subscribers, query);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Candidate choose(List<Candidate> candidates) {
        ArrayList<Candidate> fresh = new ArrayList<>();

        synchronized (LOCK) {
            for (Candidate candidate : candidates) {
                String key = candidate.name.toLowerCase();
                if (!RECENT.contains(key) && !candidate.name.equalsIgnoreCase(last)) {
                    fresh.add(candidate);
                }
            }

            List<Candidate> pool = fresh.isEmpty() ? candidates : fresh;
            return pool.get(RANDOM.nextInt(pool.size()));
        }
    }

    private static String nextQuery() {
        synchronized (LOCK) {
            int mode = RANDOM.nextInt(4);

            if (mode == 0) {
                return randomLetters(2 + RANDOM.nextInt(2));
            }

            return QUERY_SEEDS[RANDOM.nextInt(QUERY_SEEDS.length)];
        }
    }

    private static String randomLetters(int length) {
        char[] chars = new char[length];

        for (int i = 0; i < length; i++) {
            chars[i] = (char) ('a' + RANDOM.nextInt(26));
        }

        return new String(chars);
    }

    private static void remember(String name) {
        if (!isValidName(name)) {
            return;
        }

        synchronized (LOCK) {
            last = name;
            RECENT.add(name.toLowerCase());

            while (RECENT.size() > RECENT_LIMIT) {
                Iterator<String> iterator = RECENT.iterator();
                if (!iterator.hasNext()) {
                    break;
                }

                iterator.next();
                iterator.remove();
            }
        }
    }

    private static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }

        String trimmed = name.trim();
        if (trimmed.length() < 2 || trimmed.length() > 32) {
            return false;
        }

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_';

            if (!ok) {
                return false;
            }
        }

        return true;
    }

    private static boolean isRejectedName(String name) {
        if (!isValidName(name)) {
            return true;
        }

        String lower = name.toLowerCase();

        return "all".equals(lower)
                || "popular".equals(lower)
                || "random".equals(lower)
                || "randnsfw".equals(lower)
                || "announcements".equals(lower)
                || "askreddit".equals(lower)
                || "funny".equals(lower)
                || "pics".equals(lower)
                || "gaming".equals(lower)
                || "todayilearned".equals(lower)
                || "worldnews".equals(lower)
                || "news".equals(lower)
                || "aww".equals(lower)
                || "videos".equals(lower)
                || "movies".equals(lower)
                || "music".equals(lower);
    }

    private static void log(String message) {
        LoggingUtils.logInfo(true, () -> MARKER + ": " + message);
    }

    private static void logFailure(String prefix, Throwable throwable) {
        final String type = throwable == null ? "unknown" : throwable.getClass().getSimpleName();
        LoggingUtils.logInfo(true, () -> MARKER + ": " + prefix + " [" + type + "]");
    }

    private static final class Candidate {
        final String name;
        final long subscribers;
        final String query;

        Candidate(String name, long subscribers, String query) {
            this.name = name;
            this.subscribers = subscribers;
            this.query = query;
        }
    }
}
