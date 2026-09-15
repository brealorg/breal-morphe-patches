package app.morphe.extension.boostforreddit.http;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;
import okhttp3.*;
public final class HttpUtils {
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final Map<String,Object> replies = new LinkedHashMap<>();
    public static final List<String> calls = new ArrayList<>();
    public static void reset() { replies.clear(); calls.clear(); }
    public static JsonNode getJson(String url) throws IOException {
        calls.add(url);
        for (Map.Entry<String,Object> fixture : replies.entrySet()) {
            if (url.contains(fixture.getKey())) {
                Object result = fixture.getValue();
                if (result instanceof IOException) throw (IOException)result;
                if (result instanceof RuntimeException) throw (RuntimeException)result;
                return getJsonFromString((String)result);
            }
        }
        throw new AssertionError("Unconfigured archive request: " + url);
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
