import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import okio.*;
import app.morphe.extension.boostforreddit.http.*;
import app.morphe.extension.boostforreddit.http.reddit.*;
import app.morphe.extension.boostforreddit.utils.*;

public final class Issue196RegressionTest {
    private static int passed;
    private static final String POST = "{\"id\":\"abc\",\"title\":\"test\",\"author\":\"fixture\",\"subreddit\":\"test\",\"created_utc\":1,\"url\":\"https://www.reddit.com/r/test/comments/abc/\",\"selftext\":\"live\"}";
    private static final Request REQUEST = new Request.Builder().url("https://oauth.reddit.com/comments/abc?context=3").build();
    interface Test { void run() throws Exception; }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static JsonNode json(String text) { return HttpUtils.getJsonFromString(text); }
    private static String text(JsonNode node) { return HttpUtils.getStringFromJson(node); }
    private static void test(String name, Test test) throws Exception {
        HttpUtils.reset(); BoostUndeleteSettings.enabled = true; LoggingUtils.failures = 0;
        test.run(); passed++; System.out.println("PASS " + name);
    }
    private static ObjectNode deleted(String extra) {
        return (ObjectNode)json("{\"kind\":\"t1\",\"data\":{\"id\":\"c1\",\"body\":\"[deleted]\",\"collapsed\":true" + extra + "}}");
    }
    private static void archiveComment(String body) {
        HttpUtils.replies.put("comments/ids?ids=c1", "{\"data\":[{\"id\":\"c1\",\"body\":\"" + body + "\"}]}");
    }
    private static void restore(RedditSubmissionUndeleteInterceptor interceptor, JsonNode comment) throws Exception {
        Method method = RedditSubmissionUndeleteInterceptor.class.getDeclaredMethod("restoreDeletedComments", JsonNode.class);
        method.setAccessible(true);
        try { method.invoke(interceptor, comment); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw e;
        }
    }
    private static AutoSavingCache cache(RedditSubmissionUndeleteInterceptor i, String name) throws Exception {
        Field field = RedditSubmissionUndeleteInterceptor.class.getDeclaredField(name);
        field.setAccessible(true); return (AutoSavingCache)field.get(i);
    }
    private static String thread(String post, String comments) {
        return "[{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t3\",\"data\":" + post + "}]}},{\"kind\":\"Listing\",\"data\":{\"children\":" + comments + "}}]";
    }
    private static final class Body extends ResponseBody {
        boolean closed;
        final byte[] bytes;
        final BufferedSource source;
        Body(String text) {
            bytes = text.getBytes(StandardCharsets.UTF_8);
            source = Okio.buffer(new ForwardingSource(new Buffer().write(bytes)) {
                @Override public void close() throws IOException { closed = true; super.close(); }
            });
        }
        public MediaType contentType() { return MediaType.get("application/json; charset=utf-8"); }
        public long contentLength() { return bytes.length; }
        public BufferedSource source() { return source; }
    }
    private static final class Exchange {
        final Body body;
        final Response original;
        final Interceptor.Chain chain;
        int proceeds;
        Exchange(int status, String content) {
            body = new Body(content);
            original = new Response.Builder().request(REQUEST).protocol(Protocol.HTTP_1_1)
                    .code(status).message("fixture").header("X-Fixture", "preserved").body(body).build();
            chain = (Interceptor.Chain)Proxy.newProxyInstance(Interceptor.Chain.class.getClassLoader(),
                    new Class<?>[]{Interceptor.Chain.class}, (proxy, method, args) -> {
                        if (method.getName().equals("request")) return REQUEST;
                        if (method.getName().equals("proceed")) {
                            proceeds++;
                            check(proceeds == 1, "Reddit request repeated");
                            check(args[0] == REQUEST, "Request/context replaced");
                            return original;
                        }
                        throw new AssertionError("Unexpected Chain call: " + method.getName());
                    });
        }
        Response run(RedditSubmissionUndeleteInterceptor i) throws IOException { return i.intercept(chain); }
    }
    private static void baseline() throws Exception {
        test("baseline_missing_collapsed_reason_throws_NPE", () -> {
            archiveComment("recovered");
            try { restore(new RedditSubmissionUndeleteInterceptor(), deleted("")); }
            catch (NullPointerException expected) { return; }
            throw new AssertionError("Expected original missing-field NPE was not reproduced");
        });
        test("baseline_archive_IO_failure_logging_throws_NPE", () -> {
            HttpUtils.replies.put("comments/ids?ids=c1", new IOException("fixture offline"));
            try { restore(new RedditSubmissionUndeleteInterceptor(), deleted(",\"collapsed_reason_code\":\"DELETED\"")); }
            catch (NullPointerException expected) { return; }
            throw new AssertionError("Expected original error-logging NPE was not reproduced");
        });
        System.out.println("BASELINE_CRASH_PATHS_REPRODUCED=" + passed);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--baseline")) { baseline(); return; }
        test("missing_collapse_reason_restores", () -> {
            archiveComment("recovered"); ObjectNode c = deleted("");
            restore(new RedditSubmissionUndeleteInterceptor(), c);
            check(c.get("data").get("body").asText().equals("recovered"), "Not restored");
            check(c.get("data").get("collapsed").asBoolean(), "Unknown collapse reason changed");
        });
        test("null_collapse_reason_restores", () -> {
            archiveComment("recovered"); ObjectNode c = deleted(",\"collapsed_reason_code\":null");
            restore(new RedditSubmissionUndeleteInterceptor(), c);
            check(c.get("data").get("body").asText().equals("recovered"), "Not restored");
        });
        test("deleted_collapse_reason_expands_only_after_restore", () -> {
            archiveComment("recovered"); ObjectNode c = deleted(",\"collapsed_reason_code\":\"DELETED\"");
            restore(new RedditSubmissionUndeleteInterceptor(), c);
            check(!c.get("data").get("collapsed").asBoolean(), "Still collapsed");
        });
        test("archive_IO_failure_preserves_comment", () -> {
            HttpUtils.replies.put("comments/ids?ids=c1", new IOException("fixture offline"));
            ObjectNode c=deleted(",\"collapsed_reason_code\":\"DELETED\""); String before=text(c);
            restore(new RedditSubmissionUndeleteInterceptor(), c);
            check(text(c).equals(before), "Failed recovery changed live comment");
            check(LoggingUtils.failures > 0, "Failure not logged");
        });
        test("archive_runtime_failure_preserves_comment", () -> {
            HttpUtils.replies.put("comments/ids?ids=c1", new IllegalArgumentException("fixture"));
            ObjectNode c=deleted(""); String before=text(c);
            restore(new RedditSubmissionUndeleteInterceptor(), c);
            check(text(c).equals(before), "Partial change");
        });
        test("empty_or_invalid_archive_does_not_cache_or_expand", () -> {
            String[] fixtures={"{\"data\":[]}","{\"data\":null}","{\"data\":[null]}",
                    "{\"data\":[{\"id\":\"other\",\"body\":\"wrong\"}]}",
                    "{\"data\":[{\"id\":\"c1\",\"body\":null}]}",
                    "{\"data\":[{\"id\":\"c1\",\"body\":\"[deleted]\"}]}"};
            for(String fixture:fixtures) {
                HttpUtils.replies.put("comments/ids?ids=c1",fixture);
                RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
                ObjectNode c=deleted(",\"collapsed_reason_code\":\"DELETED\""); String before=text(c);
                restore(i,c); check(text(c).equals(before), "Invalid recovery changed item");
                check(cache(i,"commentsCache").values.isEmpty(),"Invalid recovery cached");
            }
        });
        test("malformed_wrappers_and_more_are_untouched", () -> {
            for(String raw:new String[]{"null","{}","{\"kind\":\"t1\",\"data\":null}",
                    "{\"kind\":\"t1\",\"data\":7}","{\"kind\":\"more\",\"data\":{\"id\":\"c1\",\"body\":\"[deleted]\"}}"}) {
                JsonNode node=json(raw); restore(new RedditSubmissionUndeleteInterceptor(),node);
            }
            check(HttpUtils.calls.isEmpty(),"Looked up malformed/more node");
        });
        test("cache_projection_excludes_live_reply_subtrees", () -> {
            archiveComment("recovered"); RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
            ObjectNode c=deleted(",\"replies\":{\"data\":{\"children\":[{\"kind\":\"more\",\"data\":{\"id\":\"tail\"}}]}}");
            restore(i,c); JsonNode cached=json(cache(i,"commentsCache").get("c1").get());
            check(cached.get("replies")==null && cached.get("body_html")==null,"Live/rendered tree retained");
            check(c.get("data").get("replies")!=null,"Live replies lost");
        });
        test("legacy_cache_hit_is_compacted_without_old_replies", () -> {
            RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
            cache(i,"commentsCache").put("c1","{\"id\":\"c1\",\"body\":\"cached\",\"replies\":{\"stale\":true}}");
            ObjectNode c=deleted(""); restore(i,c);
            check(c.get("data").get("body").asText().equals("cached"),"Cache not used");
            check(c.get("data").get("replies")==null,"Stale replies injected");
            check(json(cache(i,"commentsCache").get("c1").get()).get("replies")==null,"Old cache not compacted");
            check(HttpUtils.calls.isEmpty(),"Unexpected archive request");
        });
        test("corrupt_comment_cache_retries_archive", () -> {
            archiveComment("recovered"); RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
            cache(i,"commentsCache").put("c1","{broken"); ObjectNode c=deleted(""); restore(i,c);
            check(c.get("data").get("body").asText().equals("recovered"),"Corrupt cache blocks recovery");
        });
        test("OFF_passes_same_unconsumed_response", () -> {
            BoostUndeleteSettings.enabled=false; Exchange x=new Exchange(200,"unchanged");
            Response out=x.run(new RedditSubmissionUndeleteInterceptor());
            check(out==x.original && !x.body.closed,"OFF intercept altered response");
            check(out.body().string().equals("unchanged") && HttpUtils.calls.isEmpty(),"OFF consumed or queried");
        });
        test("malformed_live_JSON_replays_original_body_and_status", () -> {
            for(String content:new String[]{"{broken", "[]", "{\"error\":\"unexpected\"}"}) {
                Exchange x=new Exchange(200,content); Response out=x.run(new RedditSubmissionUndeleteInterceptor());
                check(out.code()==200 && out.header("X-Fixture").equals("preserved"),"Metadata lost");
                check(out.body().string().equals(content),"Original body not readable");
                check(x.proceeds==1 && x.body.closed,"Response ownership or request count incorrect");
            }
        });
        test("post_archive_failure_replays_complete_live_thread", () -> {
            HttpUtils.replies.put("posts/ids?ids=abc",new IOException("fixture offline"));
            String content=thread(POST.replace("\"live\"","\"[deleted]\""),"[]");
            Exchange x=new Exchange(200,content); Response out=x.run(new RedditSubmissionUndeleteInterceptor());
            check(out.body().string().equals(content),"Live thread lost after archive failure");
        });
        test("auth_rate_limit_and_server_errors_not_recovered", () -> {
            for(int status:new int[]{400,401,429,500,503}) {
                Exchange x=new Exchange(status,"original error"); Response out=x.run(new RedditSubmissionUndeleteInterceptor());
                check(out==x.original && !x.body.closed,"Original error replaced or closed"); out.close();
            }
            check(HttpUtils.calls.isEmpty(),"Queried archive for non-removal status");
        });
        test("404_archive_miss_keeps_original_error_stream", () -> {
            HttpUtils.replies.put("posts/ids?ids=abc","{\"data\":[]}");
            Exchange x=new Exchange(404,"original 404"); Response out=x.run(new RedditSubmissionUndeleteInterceptor());
            check(out==x.original && !x.body.closed,"404 replaced with skeletal post");
            check(out.body().string().equals("original 404"),"404 stream lost");
        });
        test("404_valid_archive_restore_closes_discarded_response", () -> {
            HttpUtils.replies.put("posts/ids?ids=abc","{\"data\":["+POST+"]}");
            HttpUtils.replies.put("comments/tree?","{\"data\":[]}");
            Exchange x=new Exchange(404,"original 404"); Response out=x.run(new RedditSubmissionUndeleteInterceptor());
            check(out.code()==200 && x.body.closed,"Successful fallback response ownership");
            check(json(out.body().string()).get(0).get("data").get("children").get(0).get("data").get("id").asText().equals("abc"),"Wrong recovered post");
        });
        test("cached_post_does_not_bypass_live_thread_or_request_full_archive_tree", () -> {
            RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
            cache(i,"submissionCache").put("abc",POST);
            String content=thread(POST,"[{\"kind\":\"t1\",\"data\":{\"id\":\"new\",\"body\":\"fresh\"}}]");
            Exchange x=new Exchange(200,content); Response out=x.run(i);
            JsonNode comments=json(out.body().string()).get(1).get("data").get("children");
            check(x.proceeds==1 && comments.get(0).get("data").get("body").asText().equals("fresh"),"Live thread bypassed");
            check(HttpUtils.calls.isEmpty(),"Fetched archive tree despite live success");
        });
        test("nested_recovery_preserves_more_nodes", () -> {
            archiveComment("nested");
            JsonNode c=json("{\"kind\":\"t1\",\"data\":{\"id\":\"parent\",\"body\":\"live\",\"replies\":{\"data\":{\"children\":["+text(deleted(""))+",{\"kind\":\"more\",\"data\":{\"id\":\"tail\"}}]}}}}");
            restore(new RedditSubmissionUndeleteInterceptor(),c);
            JsonNode children=c.get("data").get("replies").get("data").get("children");
            check(children.get(0).get("data").get("body").asText().equals("nested"),"Nested not restored");
            check(children.get(1).get("kind").asText().equals("more"),"More node damaged");
        });
        test("malformed_reply_listing_does_not_crash", () -> {
            JsonNode c=json("{\"kind\":\"t1\",\"data\":{\"body\":\"live\",\"replies\":{\"data\":{\"children\":null}}}}");
            restore(new RedditSubmissionUndeleteInterceptor(),c);
            check(HttpUtils.calls.isEmpty(),"Unexpected lookup");
        });
        test("live_network_failure_is_not_swallowed_or_retried", () -> {
            IOException failure=new IOException("fixture live network"); final int[] n={0};
            Interceptor.Chain chain=(Interceptor.Chain)Proxy.newProxyInstance(Interceptor.Chain.class.getClassLoader(),new Class<?>[]{Interceptor.Chain.class},(p,m,a)->{
                if(m.getName().equals("request")) return REQUEST;
                if(m.getName().equals("proceed")) { n[0]++; throw failure; }
                throw new AssertionError(m.getName());
            });
            try { new RedditSubmissionUndeleteInterceptor().intercept(chain); }
            catch(IOException e) { check(e==failure && n[0]==1 && HttpUtils.calls.isEmpty(),"Live error altered"); return; }
            throw new AssertionError("Live network failure hidden");
        });
        test("recovered_deleted_gallery_clears_deleted_render_marker", () -> {
            String archived="{\"id\":\"abc\",\"title\":\"gallery\",\"author\":\"fixture\",\"subreddit\":\"test\",\"created_utc\":1,"
                    +"\"url\":\"https://www.reddit.com/gallery/abc\",\"selftext\":\"\",\"is_gallery\":true,"
                    +"\"gallery_data\":{\"items\":[{\"media_id\":\"m1\"}]},"
                    +"\"media_metadata\":{\"m1\":{\"e\":\"Image\",\"id\":\"m1\"}}}";
            HttpUtils.replies.put("posts/ids?ids=abc","{\"data\":["+archived+"]}");
            String live="{\"id\":\"abc\",\"title\":\"gallery\",\"author\":\"fixture\",\"subreddit\":\"test\",\"created_utc\":1,"
                    +"\"url\":\"https://www.reddit.com/gallery/abc\",\"selftext\":\"[deleted]\","
                    +"\"removed_by_category\":\"deleted\"}";
            Exchange x=new Exchange(200,thread(live,"[]"));
            Response out=x.run(new RedditSubmissionUndeleteInterceptor());
            JsonNode restored=json(out.body().string()).get(0).get("data").get("children").get(0).get("data");
            check(restored.get("removed_by_category")==null || restored.get("removed_by_category").isNull(),
                    "User-deleted render marker survived successful recovery");
            check(restored.get("is_gallery")!=null && restored.get("is_gallery").asBoolean(),
                    "Recovered gallery flag missing");
            check(restored.get("gallery_data")!=null && restored.get("media_metadata")!=null,
                    "Recovered gallery metadata missing");
        });
        test("legacy_cached_gallery_clears_deleted_render_marker", () -> {
            RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
            String cached="{\"id\":\"abc\",\"title\":\"gallery\",\"author\":\"fixture\",\"subreddit\":\"test\",\"created_utc\":1,"
                    +"\"url\":\"https://www.reddit.com/gallery/abc\",\"selftext\":\"\","
                    +"\"removed_by_category\":\"deleted\",\"is_gallery\":true,"
                    +"\"gallery_data\":{\"items\":[{\"media_id\":\"m1\"}]},"
                    +"\"media_metadata\":{\"m1\":{\"e\":\"Image\",\"id\":\"m1\"}}}";
            cache(i,"submissionCache").put("abc",cached);
            String live="{\"id\":\"abc\",\"title\":\"gallery\",\"author\":\"fixture\",\"subreddit\":\"test\",\"created_utc\":1,"
                    +"\"url\":\"https://www.reddit.com/gallery/abc\",\"selftext\":\"[deleted]\","
                    +"\"removed_by_category\":\"deleted\"}";
            Exchange x=new Exchange(200,thread(live,"[]"));
            Response out=x.run(i);
            JsonNode restored=json(out.body().string()).get(0).get("data").get("children").get(0).get("data");
            check(restored.get("removed_by_category")==null || restored.get("removed_by_category").isNull(),
                    "Legacy cached deletion marker survived normalization");
            JsonNode rewritten=json(cache(i,"submissionCache").get("abc").get());
            check(rewritten.get("removed_by_category")==null || rewritten.get("removed_by_category").isNull(),
                    "Legacy cache was not rewritten");
            check(HttpUtils.calls.isEmpty(),"Legacy cache normalization unexpectedly hit archive");
        });
        System.out.println("ISSUE196_REGRESSION_TESTS_PASS="+passed);
    }
}
