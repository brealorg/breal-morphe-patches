package app.morphe.extension.boostforreddit.http;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;
import okhttp3.*;
public final class HttpUtils {
    public static final class HttpStatusException extends IOException {
        public final int code;
        public final String body;
        public HttpStatusException(int code, String body) { super("HTTP " + code); this.code = code; this.body = body; }
    }
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final Map<String,Object> replies = new LinkedHashMap<>();
    public static final List<String> calls = new ArrayList<>();
    /** url -> Response for get(url), e.g. cached Wayback pages. */
    public static final Map<String,Response> pages = new LinkedHashMap<>();
    public static void reset() { replies.clear(); calls.clear(); pages.clear(); }
    public static Response get(String url) throws IOException {
        calls.add(url);
        Response page = pages.get(url);
        if (page == null) throw new AssertionError("Unconfigured page request: " + url);
        return page;
    }
    public static Response get(String url, long connectAndReadTimeoutMillis) throws IOException {
        return get(url);
    }
    public static Response create503(Request request) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(503).message("Service unavailable").body(getResponseBodyFromString("")).build();
    }
    public static Response create404(Request request) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(404).message("Not found").body(getResponseBodyFromString("")).build();
    }
    public static JsonNode getJson(String url) throws IOException {
        calls.add(url);
        for (Map.Entry<String,Object> fixture : replies.entrySet()) {
            if (url.contains(fixture.getKey())) {
                Object result = fixture.getValue();
                // A Deque answers successive calls in order and then repeats its last answer.
                if (result instanceof Deque) {
                    Deque<?> answers = (Deque<?>) result;
                    result = answers.size() > 1 ? answers.poll() : answers.peek();
                }
                if (result instanceof IOException) throw (IOException)result;
                if (result instanceof RuntimeException) throw (RuntimeException)result;
                if (result instanceof Error) throw (Error)result;
                return getJsonFromString((String)result);
            }
        }
        throw new AssertionError("Unconfigured archive request: " + url);
    }
    public static JsonNode getJson(String url, long callTimeoutMillis) throws IOException {
        return getJson(url);
    }
    public static JsonNode getJsonFromString(String text) {
        try { return mapper.readTree(text); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
    public static String getStringFromJson(JsonNode node) {
        try { return mapper.writeValueAsString(node); }
        catch (IOException e) { throw new RuntimeException(e); }
    }
    public static ResponseBody getResponseBodyFromJson(JsonNode node) {
        return getResponseBodyFromString(getStringFromJson(node));
    }
    public static ResponseBody getResponseBodyFromString(String text) {
        return ResponseBody.create(text, MediaType.get("application/json"));
    }
    public static Response makeJsonResponse(Request request, JsonNode node) {
        return makeJsonResponse(request, getStringFromJson(node));
    }
    public static Response makeJsonResponse(Request request, String text) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").header("Content-Type", "application/json")
                .body(getResponseBodyFromString(text)).build();
    }
}
