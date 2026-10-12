/*
 * Copyright 2026 wchill.
 * Modified for Breal issue #196: fail-open recovery and compact comment caching (2026-09-11).
 * Modified for Breal issue #219: batched comment recovery (2026-10-12).
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.morphe.extension.boostforreddit.utils.EditableObjectNode;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import app.morphe.extension.boostforreddit.utils.BoostUndeleteSettings;
import app.morphe.extension.boostforreddit.utils.MarkdownRenderer;
import app.morphe.extension.boostforreddit.http.arcticshift.ArcticShift;
import app.morphe.extension.boostforreddit.http.AutoSavingCache;
import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.http.wayback.WaybackMachine;
import app.morphe.extension.boostforreddit.http.wayback.WaybackResponse;
import app.morphe.extension.boostforreddit.utils.Emojis;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RedditSubmissionUndeleteInterceptor implements Interceptor {
    private static final Pattern SUBMISSION_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/comments/");
    private static final Pattern GALLERY_REGEX = Pattern.compile("window\\.___r\\s*=\\s*(\\{.+\\})\\s*</script>", Pattern.DOTALL | Pattern.MULTILINE);
    private static final String GALLERY_UNDELETE_MARKER = "morphe_boost_reddit_gallery_undelete_submission_json";
    private static final String FAIL_OPEN_MARKER = "morphe_boost_undelete_issue196_fail_open";
    private static final String COMMENT_BATCH_MARKER = "morphe_boost_undelete_issue219_comment_batch";
    private static final String[] COMMENT_RECOVERY_FIELDS = {
            "id", "author", "author_fullname", "author_flair_text", "body", "link_id"
    };
    private final AutoSavingCache submissionCache = new AutoSavingCache("RedditSubmissions", 10);
    private final AutoSavingCache commentsCache = new AutoSavingCache("RedditComments", 10000);

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!BoostUndeleteSettings.isRedditUndeleteEnabled()) {
            return chain.proceed(request);
        }

        String url = request.url().toString();

        if (!SUBMISSION_API_REGEX.matcher(url).find()) {
            return chain.proceed(request);
        }
        String[] pathParts = request.url().encodedPath().split("/");
        if (pathParts.length < 3) {
            return chain.proceed(request);
        }
        String submissionId = pathParts[2].replaceFirst("\\.json$", "");
        if (!submissionId.matches("[A-Za-z0-9]+")) {
            return chain.proceed(request);
        }

        // A cached post is enrichment, not evidence that the live thread is unavailable.
        // Always preserve Reddit's current comments and request context when available.
        Response response = chain.proceed(request);
        if (response.isSuccessful()) {
            if (response.body() == null) {
                return response;
            }
            // string() closes the original body. Keep the original text for recovery failures,
            // rather than returning a consumed body or issuing the Reddit request a second time.
            String originalJson = response.body().string();
            try {
                JsonNode data = handle200(response, submissionId, originalJson);
                return response.newBuilder()
                        .removeHeader("Content-Length")
                        .removeHeader("Content-Encoding")
                        .header("Content-Type", "application/json")
                        .body(HttpUtils.getResponseBodyFromJson(data))
                        .build();
            } catch (IOException | RuntimeException failure) {
                logRecoveryFailure(failure);
                return response.newBuilder()
                        .removeHeader("Content-Length")
                        .body(ResponseBody.create(originalJson, response.body().contentType()))
                        .build();
            }
        }

        // Authentication, throttling and transient server errors are not deleted content.
        if (response.code() != 403 && response.code() != 404) {
            return response;
        }
        try {
            Response restored = HttpUtils.makeJsonResponse(request, handle4xx(request, submissionId));
            if (response.body() != null) {
                response.close();
            }
            return restored;
        } catch (IOException | RuntimeException failure) {
            logRecoveryFailure(failure);
            return response;
        }
    }

    private static void logRecoveryFailure(Exception failure) {
        LoggingUtils.logInfo(false, () -> FAIL_OPEN_MARKER + ": " + failure.getClass().getSimpleName());
    }

    private JsonNode handle200(Response redditResponse, String submissionId, String jsonStr) throws IOException {
        JsonNode json = HttpUtils.getJsonFromString(jsonStr);

        JsonNode submissionListing = json.get(0);
        JsonNode submission = submissionListing.get("data").get("children").get(0).get("data");
        if (RedditApiUtils.isContentRemoved(submission)) {
            submission = new EditableObjectNode(Map.of(
                    "kind", new TextNode("t3"),
                    "data", fetchDeletedSubmission(redditResponse.request(), submissionId, submission)
            ));
            submissionListing = RedditApiUtils.createListing(submissionListing, List.of(submission));
        }

        JsonNode commentsListing = json.get(1);
        restoreDeletedComments(commentsListing.get("data").get("children"));

        ArrayNode root = new ArrayNode(JsonNodeFactory.instance);
        root.add(submissionListing);
        root.add(commentsListing);
        return root;
    }

    private JsonNode handle4xx(Request request, String submissionId) throws IOException {
        ObjectNode submissionNode = new EditableObjectNode(Map.of(
                "kind", new TextNode("t3"),
                "data", fetchDeletedSubmission(request, submissionId, null)
        ));
        ArrayNode comments = ArcticShift.getCommentTree(submissionId);
        List<JsonNode> topLevelReplies = new ArrayList<>();
        for (JsonNode node : comments) {
            EditableObjectNode reply = EditableObjectNode.wrap(node.get("data"));
            checkReply(reply);
            topLevelReplies.add(new EditableObjectNode(Map.of(
                    "kind", new TextNode("t1"),
                    "data", reply
            )));
        }
        ArrayNode root = new ArrayNode(JsonNodeFactory.instance);
        ObjectNode submissionListing = RedditApiUtils.createListing(List.of(submissionNode));
        ObjectNode commentsListing = RedditApiUtils.createListing(topLevelReplies);
        root.add(submissionListing);
        root.add(commentsListing);
        return root;
    }

    private JsonNode fetchDeletedSubmission(Request request, String id, JsonNode dataNode) throws IOException {
        Optional<String> cachedJson = submissionCache.get(id);
        if (cachedJson.isPresent()) {
            try {
                JsonNode cached = HttpUtils.getJsonFromString(cachedJson.get());
                if (isUsableSubmission(cached, id)) {
                    EditableObjectNode normalizedCached = EditableObjectNode.wrap(cached);
                    if (clearRecoveredUserDeletedRenderMarker(normalizedCached)) {
                        submissionCache.put(id, HttpUtils.getStringFromJson(normalizedCached));
                    }
                    LoggingUtils.logInfo(true, () -> "morphe_boost_undelete_issue196_post_restored_cached");
                    return normalizedCached;
                }
            } catch (RuntimeException failure) {
                logRecoveryFailure(failure);
            }
        }

        ArrayNode undeletedData = ArcticShift.getIds(ArcticShift.SubmissionType.POSTS, List.of(id));
        JsonNode recovered = null;
        if (undeletedData != null) {
            for (JsonNode candidate : undeletedData) {
                if (isUsableSubmission(candidate, id)) {
                    recovered = candidate;
                    break;
                }
            }
        }
        if (recovered == null) {
            if (dataNode != null) {
                return dataNode;
            }
            throw new IOException("No usable archived submission");
        }

        EditableObjectNode editableNode;
        if (dataNode == null) {
            editableNode = new EditableObjectNode();
            editableNode.set(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.PROHIBITED_EMOJI));
        } else {
            editableNode = EditableObjectNode.wrap(dataNode);
            RedditApiUtils.setRemovalEmoji(editableNode);
        }
        ArcticShift.updateSubmissionNode(editableNode, recovered);
        clearRecoveredUserDeletedRenderMarker(editableNode);
        editableNode.set("archived", BooleanNode.TRUE);
        editableNode.set("stickied", BooleanNode.FALSE);
        editableNode.setIfUnset("locked", BooleanNode.TRUE);
        editableNode.set("saved", BooleanNode.FALSE);
        editableNode.set("clicked", BooleanNode.FALSE);
        editableNode.set("likes", NullNode.instance);
        editableNode.setIfUnset("over_18", BooleanNode.FALSE);
        editableNode.setIfUnset("is_self", BooleanNode.FALSE);
        editableNode.setIfUnset("hidden", BooleanNode.FALSE);
        editableNode.setIfUnset("name", new TextNode("t3_" + id));
        editableNode.setIfUnset("suggested_sort", new TextNode("random"));
        editableNode.setIfUnset("author_flair_css_class", NullNode.instance);
        editableNode.setIfUnset("author_flair_text", NullNode.instance);
        editableNode.setIfUnset("link_flair_css_class", NullNode.instance);
        editableNode.setIfUnset("link_flair_text", NullNode.instance);

        restoreRedditGalleryMetadata(request, id, editableNode);

        submissionCache.put(id, HttpUtils.getStringFromJson(editableNode));
        LoggingUtils.logInfo(true, () -> "morphe_boost_undelete_issue196_post_restored");
        return editableNode;
    }

    private static boolean clearRecoveredUserDeletedRenderMarker(EditableObjectNode node) {
        JsonNode removedByCategory = node == null ? null : node.get("removed_by_category");
        if (removedByCategory != null && "deleted".equals(removedByCategory.asText())) {
            node.set("removed_by_category", NullNode.instance);
            LoggingUtils.logInfo(true, () -> "morphe_boost_undelete_issue196_user_deleted_marker_cleared");
            return true;
        }
        return false;
    }

    private static boolean hasText(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value != null && value.isTextual() && !value.asText().isBlank();
    }

    private static boolean isUsableSubmission(JsonNode node, String id) {
        return node != null && node.getNodeType() == JsonNodeType.OBJECT
                && hasText(node, "id") && id.equals(node.get("id").asText())
                && hasText(node, "title") && hasText(node, "author")
                && hasText(node, "url") && hasText(node, "subreddit")
                && node.get("created_utc") != null && !node.get("created_utc").isNull();
    }

    private static JsonNode commentRecoveryFields(JsonNode node, String id) {
        if (node == null || node.getNodeType() != JsonNodeType.OBJECT || !hasText(node, "id")
                || !id.equals(node.get("id").asText()) || !hasText(node, "body")
                || RedditApiUtils.isContentRemoved(node)) {
            return null;
        }
        EditableObjectNode recovery = new EditableObjectNode();
        for (String key : COMMENT_RECOVERY_FIELDS) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual()) {
                recovery.set(key, value);
            }
        }
        return recovery;
    }

    /**
     * Restores removed comments in a comment, reply listing or array of comments.
     *
     * Archive lookups run inside Boost's own Reddit request, so all uncached IDs are
     * fetched in as few Arctic Shift calls as possible rather than one call per comment.
     */
    private void restoreDeletedComments(JsonNode root) {
        List<ObjectNode> removed = new ArrayList<>();
        collectRemovedComments(root, removed);
        if (removed.isEmpty()) {
            return;
        }

        Map<String, JsonNode> recoveredById = new HashMap<>();
        Set<String> uncached = new LinkedHashSet<>();
        for (ObjectNode comment : removed) {
            String commentId = comment.get("data").get("id").asText();
            if (recoveredById.containsKey(commentId) || uncached.contains(commentId)) {
                continue;
            }
            JsonNode cached = cachedCommentRecovery(commentId);
            if (cached != null) {
                recoveredById.put(commentId, cached);
            } else {
                uncached.add(commentId);
            }
        }
        fetchArchivedComments(new ArrayList<>(uncached), recoveredById);

        int restored = 0;
        for (ObjectNode comment : removed) {
            ObjectNode data = (ObjectNode) comment.get("data");
            JsonNode recovered = recoveredById.get(data.get("id").asText());
            if (recovered == null) {
                continue;
            }
            try {
                // Merge into a new object so failed recovery cannot partially alter the live item.
                EditableObjectNode updated = new EditableObjectNode(data);
                RedditApiUtils.setRemovalEmoji(updated);
                ArcticShift.updateCommentNode(updated, recovered);
                JsonNode collapsedReason = data.get("collapsed_reason_code");
                if (collapsedReason != null && "DELETED".equals(collapsedReason.asText())) {
                    updated.replace("collapsed", BooleanNode.FALSE);
                }
                comment.replace("data", updated);
                restored++;
            } catch (RuntimeException failure) {
                logRecoveryFailure(failure);
            }
        }

        final int removedCount = removed.size();
        final int uncachedCount = uncached.size();
        final int restoredCount = restored;
        LoggingUtils.logInfo(true, () -> COMMENT_BATCH_MARKER
                + ": removed=" + removedCount
                + " uncached=" + uncachedCount
                + " restored=" + restoredCount);
    }

    private void collectRemovedComments(JsonNode node, List<ObjectNode> removed) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectRemovedComments(child, removed);
            }
            return;
        }
        if (node.getNodeType() != JsonNodeType.OBJECT || !hasText(node, "kind")
                || !"t1".equals(node.get("kind").asText())
                || !(node.get("data") instanceof ObjectNode)) {
            return;
        }
        ObjectNode data = (ObjectNode) node.get("data");
        if (RedditApiUtils.isContentRemoved(data) && hasText(data, "id")) {
            removed.add((ObjectNode) node);
        }

        JsonNode children = getNested(data.get("replies"), "data", "children");
        if (children != null && children.isArray()) {
            collectRemovedComments(children, removed);
        }
    }

    private JsonNode cachedCommentRecovery(String commentId) {
        Optional<String> cachedJson = commentsCache.get(commentId);
        if (!cachedJson.isPresent()) {
            return null;
        }
        try {
            JsonNode recovered = commentRecoveryFields(HttpUtils.getJsonFromString(cachedJson.get()), commentId);
            if (recovered != null) {
                // Rewriting hits also projects older, full-comment cache entries down to these fields.
                commentsCache.put(commentId, HttpUtils.getStringFromJson(recovered));
            }
            return recovered;
        } catch (RuntimeException failure) {
            logRecoveryFailure(failure);
            return null;
        }
    }

    private void fetchArchivedComments(List<String> commentIds, Map<String, JsonNode> recoveredById) {
        for (int from = 0; from < commentIds.size(); from += ArcticShift.MAX_IDS_PER_REQUEST) {
            List<String> batch = commentIds.subList(
                    from, Math.min(from + ArcticShift.MAX_IDS_PER_REQUEST, commentIds.size()));
            try {
                ArrayNode response = ArcticShift.getIds(ArcticShift.SubmissionType.COMMENTS, batch);
                for (JsonNode candidate : response) {
                    if (!hasText(candidate, "id")) {
                        continue;
                    }
                    String commentId = candidate.get("id").asText();
                    if (!batch.contains(commentId) || recoveredById.containsKey(commentId)) {
                        continue;
                    }
                    JsonNode recovered = commentRecoveryFields(candidate, commentId);
                    if (recovered != null) {
                        recoveredById.put(commentId, recovered);
                        // Do not retain the live replies subtree in every cached ancestor comment.
                        commentsCache.put(commentId, HttpUtils.getStringFromJson(recovered));
                    }
                }
            } catch (IOException | RuntimeException failure) {
                // Keep what earlier batches recovered; the rest of the thread stays live.
                logRecoveryFailure(failure);
                return;
            }
        }
    }

    private void checkReply(EditableObjectNode node) {
        node.setIfUnset("saved", BooleanNode.FALSE);
        node.setIfUnset("controversiality", new IntNode(0));
        node.setIfUnset("score_hidden", BooleanNode.FALSE);
        node.setIfUnset("locked", BooleanNode.TRUE);
        node.set("likes", NullNode.instance);

        if (node.get("body") != null) {
            String renderedHtml = MarkdownRenderer.render(node.get("body").asText());
            node.set("body_html", new TextNode(renderedHtml));
        }

        JsonNode replies = node.get("replies");
        if (replies == null) {
            node.set("replies", new TextNode(""));
        } else if (!replies.isNull() && replies.getNodeType() == JsonNodeType.OBJECT) {
            EditableObjectNode newReplies = EditableObjectNode.wrap(replies);
            EditableObjectNode dataNode = EditableObjectNode.wrap(replies.get("data"));
            ArrayNode newReplyArray = new ArrayNode(JsonNodeFactory.instance);
            for (JsonNode child : dataNode.get("children")) {
                EditableObjectNode newChild = EditableObjectNode.wrap(child);
                EditableObjectNode newChildData = EditableObjectNode.wrap(newChild.get("data"));
                checkReply(newChildData);
                newChild.set("data", newChildData);
                newReplyArray.add(newChild);
            }
            dataNode.set("children", newReplyArray);
            newReplies.set("data", dataNode);
            node.set("replies", newReplies);
        }
    }

    private void restoreRedditGalleryMetadata(Request request, String id, EditableObjectNode editableNode) throws IOException {
        if (!isRedditGallery(editableNode.get("url"))) {
            return;
        }

        if (hasUsableGalleryMetadata(editableNode)) {
            editableNode.set("is_gallery", BooleanNode.TRUE);
            LoggingUtils.logInfo(true, () -> GALLERY_UNDELETE_MARKER + ": existing gallery metadata for " + id);
            return;
        }

        String galleryUrl = editableNode.get("url").asText();
        JsonNode data = getPostDataFromWayback(request, galleryUrl);
        JsonNode media = getNested(data, "posts", "models", "t3_" + id, "media");
        if (media == null || media.isNull()) {
            return;
        }

        JsonNode gallery = media.get("gallery");
        JsonNode mediaMetadata = media.get("mediaMetadata");
        if (!isUsableJsonNode(gallery) || !isUsableJsonNode(mediaMetadata)) {
            return;
        }

        editableNode.set("gallery_data", gallery);
        editableNode.set("media_metadata", mediaMetadata);
        editableNode.set("is_gallery", BooleanNode.TRUE);
        LoggingUtils.logInfo(true, () -> GALLERY_UNDELETE_MARKER + ": restored gallery metadata for " + id);
    }

    private boolean hasUsableGalleryMetadata(JsonNode node) {
        return node != null
                && isUsableJsonNode(node.get("gallery_data"))
                && isUsableJsonNode(node.get("media_metadata"));
    }

    private boolean isUsableJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isTextual() && node.asText().isBlank()) {
            return false;
        }
        return !(node.getNodeType() == JsonNodeType.OBJECT || node.getNodeType() == JsonNodeType.ARRAY) || node.size() > 0;
    }

    private JsonNode getNested(JsonNode node, String... path) {
        JsonNode current = node;
        for (String segment : path) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private JsonNode getPostDataFromWayback(Request request, String galleryUrl) throws IOException {
        WaybackResponse response = WaybackMachine.getFromWayback(request, galleryUrl);
        if (!response.found() || response.getResponse() == null || response.getResponse().body() == null) {
            return null;
        }

        String html = response.getResponse().body().string();
        Matcher matcher = GALLERY_REGEX.matcher(html);
        if (matcher.find()) {
            String json = matcher.group(1);
            json = json.replaceAll("(web\\.archive\\.org/web/\\d+)/", "$1if_/");
            return HttpUtils.getJsonFromString(json);
        }
        return null;
    }

    private boolean isRedditGallery(JsonNode urlNode) {
        if (urlNode == null || urlNode.isNull()) return false;
        String url = urlNode.asText();
        return url != null && url.contains("reddit.com/gallery/");
    }

    private boolean hasImage(String url) {
        if (url == null) return false;
        if (url.isBlank()) return false;
        return url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".webp") || url.endsWith(".gif") || url.endsWith(".jpeg");
    }
}
