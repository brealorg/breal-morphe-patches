package app.morphe.extension.boostforreddit.http.wayback;
import java.io.IOException;
import java.util.*;
import okhttp3.*;
public final class WaybackMachine {
    public static final long CONTENT_TIMEOUT_MS = 15_000L;
    /** contentUrl -> archived Response, null (not archived), or a Throwable to throw. */
    public static final Map<String,Object> archive = new HashMap<>();
    public static final List<String> calls = new ArrayList<>();
    public static void reset() { archive.clear(); calls.clear(); }
    public static Optional<WaybackResponse> fetchUrlFromCache(Request request, String url) { return Optional.empty(); }
    public static WaybackResponse getFromWayback(Request request, String url) throws IOException {
        calls.add(url);
        Object result = archive.get(url);
        if (result instanceof IOException) throw (IOException)result;
        if (result instanceof RuntimeException) throw (RuntimeException)result;
        if (result instanceof Error) throw (Error)result;
        if (result == null) return new WaybackResponse(request);
        return new WaybackResponse((Response)result, getTimestamp(((Response)result).request().url().toString()));
    }
    public static String getTimestamp(String url) {
        for (String s : url.split("/")) {
            String part = s.replace("if_", "");
            if (!part.isEmpty() && part.chars().allMatch(Character::isDigit)) return part;
        }
        throw new IllegalArgumentException("Timestamp not found");
    }
    public static String getWaybackContentUrl(String contentUrl, String timestamp) {
        return "https://web.archive.org/web/" + timestamp + "if_/" + contentUrl;
    }
}
