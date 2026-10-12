import java.io.IOException;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import okhttp3.*;
import okio.*;
import app.morphe.extension.boostforreddit.http.*;
import app.morphe.extension.boostforreddit.http.reddit.*;
import app.morphe.extension.boostforreddit.http.imgur.*;
import java.util.*;
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
        HttpUtils.reset(); app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.reset();
        BoostUndeleteSettings.enabled = true; BoostUndeleteSettings.imgurEnabled = true;
        LoggingUtils.failures = 0; resetArchiveCooldown();
        test.run(); passed++; System.out.println("PASS " + name);
    }
    private static void resetArchiveCooldown() throws Exception {
        Field field = app.morphe.extension.boostforreddit.http.arcticshift.ArcticShift.class.getDeclaredField("lastFailureNanos");
        field.setAccessible(true); field.set(null, null);
    }
    private static String deletedComment(String id, String replies) {
        return "{\"kind\":\"t1\",\"data\":{\"id\":\"" + id + "\",\"body\":\"[deleted]\",\"collapsed\":true"
                + (replies == null ? "" : ",\"replies\":{\"data\":{\"children\":" + replies + "}}") + "}}";
    }
    private static String archived(String... ids) {
        StringBuilder b = new StringBuilder("{\"data\":[");
        for (int n = 0; n < ids.length; n++) {
            if (n > 0) b.append(',');
            b.append("{\"id\":\"").append(ids[n]).append("\",\"body\":\"archived ").append(ids[n]).append("\"}");
        }
        return b.append("]}").toString();
    }
    private static long commentLookups() {
        return HttpUtils.calls.stream().filter(url -> url.contains("comments/ids")).count();
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
    /** Live server for interceptors that make their own follow-up requests. */
    private static final class Live {
        final Map<String, Object[]> routes = new HashMap<>();
        final List<Response> served = new ArrayList<>();
        final List<String> requested = new ArrayList<>();
        Live route(String url, int status, String body) { routes.put(url, new Object[]{status, body}); return this; }
        Response respond(Request request) {
            String url = request.url().toString();
            requested.add(url);
            Object[] route = routes.get(url);
            if (route == null) throw new AssertionError("Unexpected live request: " + url);
            Response response = new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code((Integer)route[0]).message("fixture").body(new Body((String)route[1])).build();
            served.add(response);
            return response;
        }
        Response run(Interceptor interceptor, String url) throws IOException {
            Request initial = new Request.Builder().url(url).build();
            Interceptor.Chain chain = (Interceptor.Chain)Proxy.newProxyInstance(Interceptor.Chain.class.getClassLoader(),
                    new Class<?>[]{Interceptor.Chain.class}, (proxy, method, args) -> {
                        if (method.getName().equals("request")) return initial;
                        if (method.getName().equals("proceed")) return respond((Request)args[0]);
                        throw new AssertionError("Unexpected Chain call: " + method.getName());
                    });
            return interceptor.intercept(chain);
        }
        boolean closed(int index) { return ((Body)served.get(index).body()).closed; }
    }
    private static Response page(String url, int status, String body) {
        return new Response.Builder().request(new Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(new Body(body)).build();
    }
    private static final String SUB_ABOUT = "https://oauth.reddit.com/r/gone/about";
    private static final String SUB_HOT = "https://oauth.reddit.com/r/gone/hot?limit=25";
    private static final String MEDIA = "https://i.redd.it/abc123.png";
    private static final String IMGUR_ALBUM_API = "https://api.imgur.com/3/album/AbC12";
    private static final String IMGUR_WAYBACK = "https://web.archive.org/web/20200101000000if_/https://imgur.com/a/AbC12";
    private static final String IMGUR_PAGE = "<script>window.postDataJSON = {\"count\":1,\"items\":[{\"hash\":\"Img1234\",\"ext\":\".jpg\"}]}</script>";

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
                // A successful but useless answer is remembered as a miss, never as content;
                // an error payload (data:null) is not remembered at all.
                Collection<String> cached=cache(i,"commentsCache").values.values();
                check(cached.stream().allMatch(v->v.startsWith("missing:")),"Invalid recovery cached as content");
                check(!fixture.equals("{\"data\":null}") || cached.isEmpty(),"Error payload cached as a miss");
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
        test("issue219_thread_comments_use_one_batched_lookup", () -> {
            HttpUtils.replies.put("comments/ids?ids=", archived("a1","a2","a3"));
            String comments="["+deletedComment("a1","["+deletedComment("a2",null)+"]")+",{\"kind\":\"more\",\"data\":{\"id\":\"tail\"}},"+deletedComment("a3",null)+"]";
            Exchange x=new Exchange(200,thread(POST,comments)); Response out=x.run(new RedditSubmissionUndeleteInterceptor());
            JsonNode c=json(out.body().string()).get(1).get("data").get("children");
            check(commentLookups()==1,"Expected one batched lookup, got "+HttpUtils.calls);
            check(HttpUtils.calls.get(0).endsWith("ids=a1,a2,a3"),"Batch IDs wrong: "+HttpUtils.calls);
            check(c.get(0).get("data").get("body").asText().equals("archived a1"),"Top-level not restored");
            check(c.get(0).get("data").get("replies").get("data").get("children").get(0).get("data").get("body").asText().equals("archived a2"),"Nested not restored");
            check(c.get(1).get("kind").asText().equals("more") && c.get(2).get("data").get("body").asText().equals("archived a3"),"Sibling/more damaged");
        });
        test("issue219_more_than_500_ids_are_split", () -> {
            StringBuilder comments=new StringBuilder("[");
            for(int n=0;n<501;n++){ if(n>0) comments.append(','); comments.append(deletedComment("b"+n,null)); }
            HttpUtils.replies.put("comments/ids?ids=", archived("b0","b500"));
            JsonNode listing=json(comments.append("]").toString());
            restore(new RedditSubmissionUndeleteInterceptor(),listing);
            check(commentLookups()==2,"Expected 2 lookups, got "+commentLookups());
            check(HttpUtils.calls.get(1).endsWith("ids=b500"),"Second batch wrong");
            check(listing.get(0).get("data").get("body").asText().equals("archived b0"),"First batch not applied");
            check(listing.get(500).get("data").get("body").asText().equals("archived b500"),"Second batch not applied");
            check(listing.get(1).get("data").get("body").asText().equals("[deleted]"),"Missing archive entry altered");
        });
        test("issue219_archive_failure_cools_down_later_threads", () -> {
            HttpUtils.replies.put("comments/ids?ids=", new java.net.SocketTimeoutException("fixture slow"));
            String content=thread(POST,"["+deletedComment("d1",null)+"]");
            Exchange first=new Exchange(200,content);
            check(first.run(new RedditSubmissionUndeleteInterceptor()).body().string().equals(content),"Live thread lost on timeout");
            check(commentLookups()==1,"First lookup missing");
            Exchange second=new Exchange(200,content);
            check(second.run(new RedditSubmissionUndeleteInterceptor()).body().string().equals(content),"Live thread lost during cooldown");
            check(commentLookups()==1,"Archive queried again during cooldown");
        });
        test("issue219_cache_hits_are_not_requested", () -> {
            RedditSubmissionUndeleteInterceptor i=new RedditSubmissionUndeleteInterceptor();
            cache(i,"commentsCache").put("h1","{\"id\":\"h1\",\"body\":\"cached h1\"}");
            HttpUtils.replies.put("comments/ids?ids=", archived("m1"));
            JsonNode listing=json("["+deletedComment("h1",null)+","+deletedComment("m1",null)+"]");
            restore(i,listing);
            check(HttpUtils.calls.size()==1 && HttpUtils.calls.get(0).endsWith("ids=m1"),"Cached ID requested: "+HttpUtils.calls);
            check(listing.get(0).get("data").get("body").asText().equals("cached h1"),"Cache hit not applied");
            check(listing.get(1).get("data").get("body").asText().equals("archived m1"),"Miss not applied");
            check(cache(i,"commentsCache").get("m1").isPresent(),"Recovered miss not cached");
        });
        test("subreddit_404_with_empty_archive_returns_original_not_crash", () -> {
            // Before the audit fix: .get(0) on an empty Arctic Shift array threw an NPE out of the interceptor.
            HttpUtils.replies.put("posts/search?sort=desc&subreddit=gone&limit=1", "{\"data\":[]}");
            Live live = new Live().route(SUB_ABOUT, 404, "{\"reason\":\"banned\"}");
            Response out = live.run(new RedditSubredditUndeleteInterceptor(), SUB_ABOUT);
            check(out == live.served.get(0) && !live.closed(0), "Original 404 not returned");
        });
        test("subreddit_non_removal_errors_never_query_archive", () -> {
            for (int status : new int[]{401, 429, 500, 503}) {
                Live live = new Live().route(SUB_ABOUT, status, "error").route(SUB_HOT, status, "error");
                check(live.run(new RedditSubredditUndeleteInterceptor(), SUB_ABOUT) == live.served.get(0), "About replaced");
                check(live.run(new RedditSubredditUndeleteInterceptor(), SUB_HOT) == live.served.get(1), "Listing replaced");
            }
            check(HttpUtils.calls.isEmpty(), "Queried archive for non-removal status: " + HttpUtils.calls);
        });
        test("subreddit_cached_archive_does_not_bypass_live_reddit", () -> {
            RedditSubredditUndeleteInterceptor i = new RedditSubredditUndeleteInterceptor();
            Field f = RedditSubredditUndeleteInterceptor.class.getDeclaredField("subredditCache"); f.setAccessible(true);
            ((AutoSavingCache)f.get(i)).put("gone", "{\"kind\":\"t5\",\"data\":{\"display_name\":\"stale\"}}");
            Live live = new Live().route(SUB_ABOUT, 200, "{\"live\":true}").route(SUB_HOT, 200, "{\"listing\":true}");
            check(live.run(i, SUB_ABOUT) == live.served.get(0), "Cached archive bypassed live about");
            check(live.run(i, SUB_HOT) == live.served.get(1), "Cached archive bypassed live listing");
        });
        test("subreddit_archive_failure_and_bad_limit_return_original", () -> {
            HttpUtils.replies.put("posts/search", new java.net.SocketTimeoutException("fixture slow"));
            Live live = new Live().route(SUB_HOT, 404, "gone").route("https://oauth.reddit.com/r/gone/hot?limit=abc", 404, "gone");
            check(live.run(new RedditSubredditUndeleteInterceptor(), SUB_HOT) == live.served.get(0) && !live.closed(0), "Archive failure escaped");
            resetArchiveCooldown(); HttpUtils.replies.clear();
            check(live.run(new RedditSubredditUndeleteInterceptor(), "https://oauth.reddit.com/r/gone/hot?limit=abc") == live.served.get(1), "Bad limit escaped");
            check(LoggingUtils.failures > 0, "Failure not logged");
        });
        test("subreddit_restored_listing_closes_live_response", () -> {
            HttpUtils.replies.put("posts/search", "{\"data\":[{\"id\":\"p1\",\"title\":\"archived\",\"created_utc\":1600000000}]}");
            Live live = new Live().route(SUB_HOT, 404, "gone");
            Response out = live.run(new RedditSubredditUndeleteInterceptor(), SUB_HOT);
            check(out != live.served.get(0) && out.code() == 200 && live.closed(0), "Live response leaked or not replaced");
            check(json(out.body().string()).get("data").get("children").get(0).get("data").get("id").asText().equals("p1"), "Archive listing wrong");
        });
        test("subreddit_listing_fills_fields_old_archive_posts_lack", () -> {
            // Pushshift-era posts have no hidden/clicked/visited/locked; Boost dropped the whole listing.
            HttpUtils.replies.put("posts/search", "{\"data\":[{\"id\":\"p1\",\"title\":\"old\",\"created_utc\":1388522231,\"subreddit\":\"gone\"}]}");
            Response out = new Live().route(SUB_HOT, 404, "gone").run(new RedditSubredditUndeleteInterceptor(), SUB_HOT);
            JsonNode post = json(out.body().string()).get("data").get("children").get(0).get("data");
            for (String key : new String[]{"hidden", "clicked", "visited", "locked", "archived", "stickied", "spoiler", "over_18", "is_self", "score", "num_comments", "name", "subreddit_name_prefixed", "created"}) {
                check(post.get(key) != null, "Archived listing post lacks " + key);
            }
            check(post.get("name").asText().equals("t3_p1") && post.get("subreddit_name_prefixed").asText().equals("r/gone"), "Wrong defaults");
        });
        test("subreddit_top_listing_pages_within_time_window", () -> {
            HttpUtils.replies.put("posts/search", "{\"data\":[{\"id\":\"p2\",\"title\":\"older\",\"created_utc\":1600000000}]}");
            String page2 = "https://oauth.reddit.com/r/gone/top?t=week&limit=25&after=2020-09-13T12:26:40";
            new Live().route(page2, 404, "gone").run(new RedditSubredditUndeleteInterceptor(), page2);
            String query = HttpUtils.calls.get(0);
            check(query.contains("&before=2020-09-13T12:26:40"), "Page cursor replaced by time window: " + query);
            check(query.contains("&after=" + java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusWeeks(1)), "Time window lost: " + query);
        });
        test("subreddit_limit_over_100_is_clamped", () -> {
            HttpUtils.replies.put("posts/search", "{\"data\":[{\"id\":\"p1\",\"title\":\"a\",\"created_utc\":1600000000}]}");
            String url = "https://oauth.reddit.com/r/gone/hot?limit=500";
            Response out = new Live().route(url, 404, "gone").run(new RedditSubredditUndeleteInterceptor(), url);
            check(out.code() == 200 && HttpUtils.calls.get(0).contains("limit=100"), "Large limit not clamped: " + HttpUtils.calls);
        });
        test("subreddit_search_retries_one_archive_query_timeout", () -> {
            // Arctic Shift sends its query timeout as HTTP 422 with an error body.
            HttpUtils.replies.put("posts/search", new ArrayDeque<>(List.of(
                    new HttpUtils.HttpStatusException(422, "{\"data\":null,\"error\":\"Timeout. Maybe slow down a bit\"}"),
                    "{\"data\":[{\"id\":\"p1\",\"title\":\"a\",\"created_utc\":1600000000}]}")));
            Response out = new Live().route(SUB_HOT, 404, "gone").run(new RedditSubredditUndeleteInterceptor(), SUB_HOT);
            check(out.code() == 200 && HttpUtils.calls.size() == 2, "Query timeout not retried: " + HttpUtils.calls);
            Field cooldown = app.morphe.extension.boostforreddit.http.arcticshift.ArcticShift.class.getDeclaredField("lastFailureNanos");
            cooldown.setAccessible(true);
            check(cooldown.get(null) == null, "Query timeout started the outage cooldown");
        });
        test("media_live_success_is_never_replaced", () -> {
            Live live = new Live().route(MEDIA, 200, "image");
            check(live.run(new RedditMediaUndeleteInterceptor(), MEDIA) == live.served.get(0), "Live media replaced");
            check(app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.calls.isEmpty(), "Archive queried for live media");
        });
        test("media_archive_failure_returns_404_and_skips_bogus_jpeg_retry", () -> {
            app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.archive.put(MEDIA, new ClassCastException("fixture odd CDX"));
            Live live = new Live().route(MEDIA, 404, "");
            Response out = live.run(new RedditMediaUndeleteInterceptor(), MEDIA);
            check(out.code() == 404, "Archive failure escaped or changed status");
            check(live.requested.size() == 1, "Extension URL retried as .png.jpeg: " + live.requested);
        });
        test("imgur_album_caches_wayback_url_and_reopens_from_it", () -> {
            app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.archive.put("https://imgur.com/a/AbC12", page(IMGUR_WAYBACK, 200, IMGUR_PAGE));
            ImgurUndeleteInterceptor i = new ImgurUndeleteInterceptor();
            Live live = new Live().route(IMGUR_ALBUM_API, 404, "");
            Response first = live.run(i, IMGUR_ALBUM_API);
            check(first.code() == 200 && live.closed(0), "Album not restored or live leaked");
            check(json(first.body().string()).get("data").get("images").get(0).get("link").asText().contains("20200101000000"), "Wrong archived image link");
            // Second open goes through the cache; before the fix it cached the Imgur API URL and threw.
            HttpUtils.pages.put(IMGUR_WAYBACK, page(IMGUR_WAYBACK, 200, IMGUR_PAGE));
            Response second = live.run(i, IMGUR_ALBUM_API);
            check(second.code() == 200 && live.requested.size() == 1, "Cached album not used");
        });
        test("imgur_archive_failure_returns_live_response", () -> {
            app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.archive.put("https://imgur.com/a/ZzZ99", new NoSuchMethodError("fixture linkage"));
            Live live = new Live().route("https://api.imgur.com/3/album/ZzZ99", 404, "gone");
            Response out = live.run(new ImgurUndeleteInterceptor(), "https://api.imgur.com/3/album/ZzZ99");
            check(out == live.served.get(0) && !live.closed(0), "Imgur failure escaped");
        });
        test("submission_errors_in_recovery_fail_open", () -> {
            HttpUtils.replies.put("comments/ids?ids=", new StackOverflowError("fixture deep tree"));
            String content = thread(POST, "[" + deletedComment("e1", null) + "]");
            Exchange x = new Exchange(200, content);
            check(x.run(new RedditSubmissionUndeleteInterceptor()).body().string().equals(content), "Error escaped recovery");
        });
        test("post_recovery_error_still_restores_comments", () -> {
            HttpUtils.replies.put("posts/ids?ids=abc", new NoSuchMethodError("fixture linkage"));
            archiveComment("archived c1");
            String livePost = POST.replace("\"live\"", "\"[deleted]\"");
            Exchange x = new Exchange(200, thread(livePost, "[" + deletedComment("c1", null) + "]"));
            JsonNode out = json(x.run(new RedditSubmissionUndeleteInterceptor()).body().string());
            check(out.get(0).get("data").get("children").get(0).get("data").get("selftext").asText().equals("[deleted]"),
                    "Live post not kept after post recovery failed");
            check(out.get(1).get("data").get("children").get(0).get("data").get("body").asText().equals("archived c1"),
                    "Comment recovery discarded with the failed post recovery");
        });
        test("gallery_wayback_failure_keeps_recovered_post_and_retries_from_cache", () -> {
            String galleryUrl = "https://www.reddit.com/gallery/abc";
            HttpUtils.replies.put("posts/ids?ids=abc", "{\"data\":[{\"id\":\"abc\",\"title\":\"gallery\",\"author\":\"fixture\","
                    + "\"subreddit\":\"test\",\"created_utc\":1,\"url\":\"" + galleryUrl + "\",\"selftext\":\"archived\"}]}");
            app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.archive.put(galleryUrl, new IOException("fixture timeout"));
            archiveComment("archived c1");
            String livePost = "{\"id\":\"abc\",\"title\":\"gallery\",\"author\":\"fixture\",\"subreddit\":\"test\",\"created_utc\":1,"
                    + "\"url\":\"" + galleryUrl + "\",\"selftext\":\"[removed]\"}";
            String content = thread(livePost, "[" + deletedComment("c1", null) + "]");
            RedditSubmissionUndeleteInterceptor i = new RedditSubmissionUndeleteInterceptor();
            JsonNode out = json(new Exchange(200, content).run(i).body().string());
            JsonNode post = out.get(0).get("data").get("children").get(0).get("data");
            check(post.get("selftext").asText().equals("archived"), "Recovered post lost to gallery failure");
            check(out.get(1).get("data").get("children").get(0).get("data").get("body").asText().equals("archived c1"),
                    "Comments lost to gallery failure");
            check(cache(i, "submissionCache").get("abc").isPresent(), "Recovered post not cached");

            String wayback = "https://web.archive.org/web/20200101000000if_/" + galleryUrl;
            app.morphe.extension.boostforreddit.http.wayback.WaybackMachine.archive.put(galleryUrl, page(wayback, 200,
                    "<script>window.___r = {\"posts\":{\"models\":{\"t3_abc\":{\"media\":{\"gallery\":{\"items\":[{\"mediaId\":\"m1\"}]},"
                    + "\"mediaMetadata\":{\"m1\":{\"e\":\"Image\"}}}}}}}</script>"));
            post = json(new Exchange(200, content).run(i).body().string()).get(0).get("data").get("children").get(0).get("data");
            check(post.get("gallery_data") != null && post.get("is_gallery").asBoolean(), "Cached post did not retry gallery metadata");
            check(json(cache(i, "submissionCache").get("abc").get()).get("gallery_data") != null, "Gallery metadata not cached");
        });
        test("quarantined_or_unexplained_403_is_returned_unchanged", () -> {
            for (String body : new String[]{"{\"reason\":\"quarantined\",\"quarantine_message\":\"x\"}", "", "{\"message\":\"Forbidden\"}"}) {
                HttpUtils.reset();
                Exchange x = new Exchange(403, body);
                Response out = x.run(new RedditSubmissionUndeleteInterceptor());
                check(out == x.original && !x.body.closed, "403 without a recoverable reason was replaced: " + body);
                check(out.body().string().equals(body), "403 body consumed: " + body);
                check(HttpUtils.calls.isEmpty(), "Archive queried for 403: " + body);
            }
        });
        test("private_403_is_recovered_from_archive", () -> {
            HttpUtils.replies.put("posts/ids?ids=abc", "{\"data\":[" + POST.replace("\"live\"", "\"archived\"") + "]}");
            HttpUtils.replies.put("comments/tree", "{\"data\":[]}");
            Exchange x = new Exchange(403, "{\"reason\":\"private\",\"message\":\"Forbidden\"}");
            Response out = x.run(new RedditSubmissionUndeleteInterceptor());
            check(out.code() == 200 && x.body.closed, "Private-sub post not recovered");
            check(json(out.body().string()).get(0).get("data").get("children").get(0).get("data").get("selftext").asText().equals("archived"), "Wrong post");
        });
        test("recovered_post_keeps_live_user_state", () -> {
            HttpUtils.replies.put("posts/ids?ids=abc", "{\"data\":[" + POST.replace("\"live\"", "\"archived\"") + "]}");
            String live = POST.replace("\"live\"", "\"[removed]\"").replace("}", ",\"saved\":true,\"likes\":true,\"score\":42,\"archived\":false}");
            RedditSubmissionUndeleteInterceptor i = new RedditSubmissionUndeleteInterceptor();
            for (int open = 0; open < 2; open++) {
                JsonNode post = json(new Exchange(200, thread(live, "[]")).run(i).body().string())
                        .get(0).get("data").get("children").get(0).get("data");
                check(post.get("selftext").asText().equals("archived"), "Post not recovered");
                check(post.get("saved").asBoolean() && post.get("likes").asBoolean() && post.get("score").asInt() == 42
                        && !post.get("archived").asBoolean(), "Live user state overwritten (open " + open + "): " + post);
            }
            check(HttpUtils.calls.size() == 1, "Second open did not use the cache");
            check(!json(cache(i, "submissionCache").get("abc").get()).get("saved").asBoolean(), "Live user state cached");
        });
        test("archive_misses_are_remembered_until_they_expire", () -> {
            HttpUtils.replies.put("comments/ids?ids=", "{\"data\":[]}");
            RedditSubmissionUndeleteInterceptor i = new RedditSubmissionUndeleteInterceptor();
            restore(i, json("[" + deletedComment("m1", null) + "]"));
            restore(i, json("[" + deletedComment("m1", null) + "]"));
            check(commentLookups() == 1, "Known-missing comment looked up again");
            cache(i, "commentsCache").put("m1", "missing:0");
            restore(i, json("[" + deletedComment("m1", null) + "]"));
            check(commentLookups() == 2, "Expired miss not retried");

            HttpUtils.replies.put("posts/ids?ids=abc", "{\"data\":[]}");
            String live = thread(POST.replace("\"live\"", "\"[removed]\""), "[]");
            new Exchange(200, live).run(i);
            new Exchange(200, live).run(i);
            check(HttpUtils.calls.stream().filter(u -> u.contains("posts/ids")).count() == 1, "Known-missing post looked up again");
        });
        System.out.println("ISSUE196_REGRESSION_TESTS_PASS="+passed);
    }
}
