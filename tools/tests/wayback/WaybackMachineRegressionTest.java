import java.io.IOException;
import java.lang.reflect.Field;
import okhttp3.*;
import app.morphe.extension.boostforreddit.http.*;
import app.morphe.extension.boostforreddit.http.wayback.*;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;

/** Runs the real WaybackMachine against the issue196 HttpUtils fixture. */
public final class WaybackMachineRegressionTest {
    private static int passed;
    private static final String URL = "https://i.redd.it/abc123.png";
    private static final Request REQUEST = new Request.Builder().url(URL).build();
    private static final String HEADER = "[\"urlkey\",\"timestamp\",\"original\",\"mimetype\",\"statuscode\",\"digest\",\"length\"]";
    interface Test { void run() throws Exception; }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void test(String name, Test test) throws Exception {
        HttpUtils.reset();
        LoggingUtils.failures = 0;
        Field cooldown = WaybackMachine.class.getDeclaredField("lastFailureNanos");
        cooldown.setAccessible(true);
        cooldown.set(null, null);
        cache().values.clear();
        test.run();
        passed++;
        System.out.println("PASS " + name);
    }
    private static AutoSavingCache cache() throws Exception {
        Field field = WaybackMachine.class.getDeclaredField("waybackCache");
        field.setAccessible(true);
        return (AutoSavingCache)field.get(null);
    }
    private static String row(String timestamp, String status) {
        return "[\"com,redd,i)/abc123.png\",\"" + timestamp + "\",\"" + URL + "\",\"image/png\",\"" + status + "\",\"D\",\"1\"]";
    }
    private static String capture(String timestamp) {
        return "https://web.archive.org/web/" + timestamp + "if_/" + URL;
    }
    private static Response page(String url, int status) {
        return new Response.Builder().request(new Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(ResponseBody.create("png", MediaType.get("image/png"))).build();
    }
    private static long timemapCalls() {
        return HttpUtils.calls.stream().filter(url -> url.contains("/timemap/")).count();
    }
    private static void expectIOException(Test test, String message) throws Exception {
        try {
            test.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        test("empty_or_header_only_timemap_is_not_found_and_cached", () -> {
            HttpUtils.replies.put("/timemap/", "[" + HEADER + "]");
            check(!WaybackMachine.getFromWayback(REQUEST, URL).found(), "Header-only timemap treated as a capture");
            check(!WaybackMachine.getFromWayback(REQUEST, URL).found() && timemapCalls() == 1, "Miss not cached");
        });
        test("malformed_rows_are_skipped", () -> {
            HttpUtils.replies.put("/timemap/", "[" + HEADER + ",[],{\"x\":1},[\"k\",\"not-a-date\"]]");
            check(!WaybackMachine.getFromWayback(REQUEST, URL).found(), "Malformed timemap rows used");
        });
        test("prefers_200_capture_over_redirects", () -> {
            HttpUtils.replies.put("/timemap/", "[" + HEADER + "," + row("20190101000000", "302") + "," + row("20200101000000", "200") + "]");
            HttpUtils.pages.put(capture("20200101000000"), page(capture("20200101000000"), 200));
            WaybackResponse response = WaybackMachine.getFromWayback(REQUEST, URL);
            check(response.found() && response.getTimestamp().get().equals("20200101000000"), "Redirect capture chosen");
        });
        test("timemap_failure_cools_down_later_lookups", () -> {
            HttpUtils.replies.put("/timemap/", new IOException("fixture timeout"));
            expectIOException(() -> WaybackMachine.getFromWayback(REQUEST, URL), "Timemap failure swallowed");
            expectIOException(() -> WaybackMachine.getFromWayback(REQUEST, "https://i.redd.it/other.png"), "No cooldown");
            check(timemapCalls() == 1, "Lookup during cooldown hit the archive");
            check(cache().values.isEmpty(), "Failure cached as a miss");
        });
        test("content_server_error_is_a_failure_not_a_capture", () -> {
            HttpUtils.replies.put("/timemap/", "[" + HEADER + "," + row("20200101000000", "200") + "]");
            HttpUtils.pages.put(capture("20200101000000"), page(capture("20200101000000"), 503));
            expectIOException(() -> WaybackMachine.getFromWayback(REQUEST, URL), "503 capture returned as found");
            check(cache().values.isEmpty(), "Failed capture cached");
        });
        test("content_404_is_cached_as_miss", () -> {
            HttpUtils.replies.put("/timemap/", "[" + HEADER + "," + row("20200101000000", "200") + "]");
            HttpUtils.pages.put(capture("20200101000000"), page(capture("20200101000000"), 404));
            check(!WaybackMachine.getFromWayback(REQUEST, URL).found(), "404 capture returned as found");
            check("".equals(cache().values.get(URL)), "Missing capture not cached");
        });
        test("successful_capture_is_cached_and_reused", () -> {
            HttpUtils.replies.put("/timemap/", "[" + HEADER + "," + row("20200101000000", "200") + "]");
            HttpUtils.pages.put(capture("20200101000000"), page(capture("20200101000000"), 200));
            check(WaybackMachine.getFromWayback(REQUEST, URL).found(), "Capture not found");
            check(WaybackMachine.getFromWayback(REQUEST, URL).found() && timemapCalls() == 1, "Cached capture not reused");
        });
        test("rate_limiter_spaces_permits_and_honours_timeouts", () -> {
            // Replaces reflection into Boost's obfuscated Guava, which could fail class init.
            app.morphe.extension.boostforreddit.utils.CompatibleRateLimiter limiter =
                    app.morphe.extension.boostforreddit.utils.CompatibleRateLimiter.create(10);
            long start = System.nanoTime();
            for (int i = 0; i < 4; i++) limiter.acquire();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            check(elapsedMs >= 250 && elapsedMs < 1000, "Permits not spaced at 10/s: " + elapsedMs + " ms");
            app.morphe.extension.boostforreddit.utils.CompatibleRateLimiter slow =
                    app.morphe.extension.boostforreddit.utils.CompatibleRateLimiter.create(0.5);
            check(slow.tryAcquire(), "First permit not free");
            check(!slow.tryAcquire(1, 100, java.util.concurrent.TimeUnit.MILLISECONDS), "Timeout ignored");
            check(slow.tryAcquire(1, 3, java.util.concurrent.TimeUnit.SECONDS), "Permit within timeout refused");
        });
        System.out.println("WAYBACK_REGRESSION_TESTS_PASS=" + passed);
    }
}
