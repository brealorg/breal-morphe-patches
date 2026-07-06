/*
 * Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.codeblock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CodeBlockHtmlNormalizer {
    public static final String MARKER = "MORPHE_CODEBLOCK_HTML_NORMALIZER_V6_RAW_HTML_MALFORMED_CODE_CLOSE";
    public static final String PREVIOUS_MARKER = "MORPHE_CODEBLOCK_HTML_NORMALIZER_V4_FENCED_SELFTEXT_ACTUAL_SHAPE";
    public static final String V3_MARKER = "MORPHE_CODEBLOCK_HTML_NORMALIZER_V3_FENCED_SELFTEXT";
    public static final String V2_MARKER = "MORPHE_CODEBLOCK_HTML_NORMALIZER_V2_SEGMENTED";

    private static final String FENCE = "\u0060\u0060\u0060";

    private static final Pattern PARAGRAPH_PATTERN =
            Pattern.compile("(?is)<p>(.*?)</p>");

    private static final Pattern CODE_PATTERN =
            Pattern.compile("(?is)<code>(.*?)</code>");

    private CodeBlockHtmlNormalizer() {
    }

    public static String normalize(String html) {
        if (html == null || html.length() == 0) {
            return html;
        }

        try {
            String plainTextFenced = normalizePlainTextFencedCodeBlocks(html);
            if (plainTextFenced != null) {
                return plainTextFenced;
            }

            String actualRawHtml = normalizeActualRawHtmlMalformedCodeBlock(html);
            if (actualRawHtml != null) {
                return actualRawHtml;
            }

            String fenced = normalizeMalformedFencedCodeBlocks(html);
            if (fenced != null) {
                return fenced;
            }

            if (containsIgnoreCase(html, "<pre")) {
                return html;
            }

            if (!containsIgnoreCase(html, "<code") || !containsIgnoreCase(html, "</code>")) {
                return html;
            }

            return normalizeParagraphs(html);
        } catch (Throwable ignored) {
        return html;
        }
    }



    private static String normalizeActualRawHtmlMalformedCodeBlock(String html) {
        if (html == null || html.indexOf(FENCE) < 0) {
            return null;
        }

        if (!containsIgnoreCase(html, "<p") || !containsIgnoreCase(html, "</p>")) {
            return null;
        }

        int openStart = html.indexOf(FENCE);
        int openEnd = openStart + FENCE.length();
        int openingParagraphEnd = html.indexOf("</p>", openEnd);
        if (openingParagraphEnd < 0) {
            return null;
        }

        String language = html.substring(openEnd, openingParagraphEnd).trim();
        if (!isSimpleFenceMarker(language)) {
            return null;
        }

        int codeParagraphStart = html.indexOf("<p>", openingParagraphEnd + 4);
        if (codeParagraphStart < 0) {
            return null;
        }

        int codeStart = codeParagraphStart + 3;
        ClosingFence close = findActualRawHtmlClosingFence(html, codeStart);
        if (close == null || close.start <= codeStart) {
            return null;
        }

        String before = closeParagraphBeforeFence(html.substring(0, openStart));
        String code = cleanupRawHtmlCodeBody(html.substring(codeStart, close.start));
        String after = cleanupRawHtmlAfterMalformedClose(html.substring(close.end));

        if (code.length() == 0) {
            return null;
        }

        StringBuilder out = new StringBuilder(html.length() + 64);
        out.append(before);
        out.append("\n\n<pre><code>");
        out.append(code);
        out.append("</code></pre>");

        if (after.length() > 0) {
            out.append("\n\n");
            out.append(after);
        }

        String normalized = out.toString();
        return normalized.equals(html) ? null : normalized;
    }

    private static boolean isSimpleFenceMarker(String marker) {
        if (marker.length() == 0) {
            return true;
        }

        for (int i = 0; i < marker.length(); i++) {
            char c = marker.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '+' || c == '.' || c == '-') {
                continue;
            }

            return false;
        }

        return true;
    }

    private static ClosingFence findActualRawHtmlClosingFence(String html, int from) {
        int index = html.lastIndexOf("\n``<code>");
        if (index >= from) {
            return new ClosingFence(index + 1, index + 3);
        }

        index = html.lastIndexOf("\r``<code>");
        if (index >= from) {
            return new ClosingFence(index + 1, index + 3);
        }

        index = html.lastIndexOf("\n```");
        if (index >= from) {
            return new ClosingFence(index + 1, index + 4);
        }

        index = html.lastIndexOf("\r```");
        if (index >= from) {
            return new ClosingFence(index + 1, index + 4);
        }

        return null;
    }

    private static String closeParagraphBeforeFence(String before) {
        String value = stripTrailingBreakTag(before).trim();

        if (!endsWithIgnoreCase(value, "</p>")) {
            value += "</p>";
        }

        return value;
    }

    private static String stripTrailingBreakTag(String value) {
        String out = value;

        while (true) {
            String trimmed = trimTrailingWhitespaceOnly(out);

            if (endsWithIgnoreCase(trimmed, "<br/>")) {
                out = trimmed.substring(0, trimmed.length() - 5);
                continue;
            }

            if (endsWithIgnoreCase(trimmed, "<br />")) {
                out = trimmed.substring(0, trimmed.length() - 6);
                continue;
            }

            if (endsWithIgnoreCase(trimmed, "<br>")) {
                out = trimmed.substring(0, trimmed.length() - 4);
                continue;
            }

            return trimmed;
        }
    }

    private static String cleanupRawHtmlCodeBody(String value) {
        String code = value
                .replace("\r\n", "\n")
                .replace("</p>\n\n<p>", "\n\n")
                .replace("</p>\n<p>", "\n")
                .replace("</p><p>", "\n")
                .replace("<br/>\n", "\n")
                .replace("<br />\n", "\n")
                .replace("<br>\n", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<br>", "\n")
                .replace("<p>", "")
                .replace("</p>", "");

        return trimBlankCodeLines(code);
    }

    private static String cleanupRawHtmlAfterMalformedClose(String value) {
        String after = value.replace("\r\n", "\n");

        if (startsWithIgnoreCase(after, "<code>")) {
            after = after.substring(6);
            if (after.startsWith("\n")) {
                after = after.substring(1);
            }
        }

        after = after.replace("</code>", "");
        after = after.trim();

        while (startsWithIgnoreCase(after, "</p>")) {
            after = after.substring(4).trim();
        }

        if (after.length() == 0) {
            return after;
        }

        if (!startsWithIgnoreCase(after, "<p")
                && !startsWithIgnoreCase(after, "<div")
                && after.indexOf("</p>") >= 0) {
            after = "<p>" + after;
        }

        return after;
    }

    private static String trimTrailingWhitespaceOnly(String value) {
        int end = value.length();

        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                end--;
            } else {
                break;
            }
        }

        return value.substring(0, end);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean endsWithIgnoreCase(String value, String suffix) {
        int start = value.length() - suffix.length();
        return start >= 0 && value.regionMatches(true, start, suffix, 0, suffix.length());
    }


    private static String normalizePlainTextFencedCodeBlocks(String text) {
        if (text == null || text.indexOf(FENCE) < 0) {
            return null;
        }

        if (containsIgnoreCase(text, "<p")
                || containsIgnoreCase(text, "<br")
                || containsIgnoreCase(text, "<div")
                || containsIgnoreCase(text, "<code")
                || containsIgnoreCase(text, "<pre")
                || containsIgnoreCase(text, "&lt;p")
                || containsIgnoreCase(text, "&lt;br")
                || containsIgnoreCase(text, "&lt;div")
                || containsIgnoreCase(text, "&lt;code")
                || containsIgnoreCase(text, "&lt;pre")) {
            return null;
        }

        int openStart = text.indexOf(FENCE);
        int markerStart = openStart + FENCE.length();
        int lineEnd = -1;

        for (int i = markerStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                lineEnd = i;
                break;
            }
        }

        if (lineEnd < 0) {
            return null;
        }

        String marker = text.substring(markerStart, lineEnd).trim();
        for (int i = 0; i < marker.length(); i++) {
            char c = marker.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '+' || c == '.' || c == '-') {
                continue;
            }

            return null;
        }

        int codeStart = lineEnd;
        if (codeStart < text.length() && text.charAt(codeStart) == '\r') {
            codeStart++;
            if (codeStart < text.length() && text.charAt(codeStart) == '\n') {
                codeStart++;
            }
        } else if (codeStart < text.length() && text.charAt(codeStart) == '\n') {
            codeStart++;
        }

        int closeStart = text.lastIndexOf(FENCE);
        int closeEnd = closeStart + FENCE.length();

        if (closeStart <= codeStart || closeStart == openStart) {
            closeStart = text.lastIndexOf("\n``");
            if (closeStart >= codeStart) {
                closeStart++;
                closeEnd = closeStart + 2;
            } else {
                closeStart = text.lastIndexOf("\r``");
                if (closeStart >= codeStart) {
                    closeStart++;
                    closeEnd = closeStart + 2;
                } else {
                    return null;
                }
            }
        }

        String before = trimPlainTextEdges(text.substring(0, openStart));
        String code = trimBlankCodeLines(text.substring(codeStart, closeStart));
        String after = trimPlainTextEdges(text.substring(closeEnd));

        if (code.length() == 0) {
            return null;
        }

        StringBuilder out = new StringBuilder(text.length() + 32);

        if (before.length() > 0) {
            out.append(escapePlainTextForHtml(before));
            out.append('\n');
        }

        out.append("<pre><code>");
        out.append(escapePlainTextForHtml(code));
        out.append("</code></pre>");

        if (after.length() > 0) {
            out.append('\n');
            out.append(escapePlainTextForHtml(after));
        }

        String normalized = out.toString();
        return normalized.equals(text) ? null : normalized;
    }

    private static String trimPlainTextEdges(String value) {
        int start = 0;
        int end = value.length();

        while (start < end) {
            char c = value.charAt(start);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                start++;
            } else {
                break;
            }
        }

        while (end > start) {
            char c = value.charAt(end - 1);
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                end--;
            } else {
                break;
            }
        }

        return value.substring(start, end);
    }

    private static String escapePlainTextForHtml(String value) {
        StringBuilder out = new StringBuilder(value.length());

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }


    private static String normalizeMalformedFencedCodeBlocks(String html) {
        if (html.indexOf(FENCE) < 0) {
            return null;
        }

        // he/h0.d can receive an escaped HTML string before Boost decodes it.
        // Do not rewrite that shape; TableTextView receives the raw HTML shape next.
        if (html.indexOf("<p") < 0 && containsIgnoreCase(html, "&lt;p")) {
            return null;
        }

        int openStart = html.indexOf(FENCE);
        int openEnd = consumeOpeningFence(html, openStart);

        ClosingFence close = findClosingFence(html, openEnd);
        if (close == null || close.start <= openStart) {
            return null;
        }

        String before = html.substring(0, openStart);
        String codeFragment = html.substring(openEnd, close.start);
        String after = html.substring(close.end);

        String code = htmlFragmentToCode(codeFragment);
        if (code.length() == 0) {
            return null;
        }

        StringBuilder out = new StringBuilder(html.length() + 32);
        out.append(closeOpenParagraphBeforeFence(before));
        out.append("<pre><code>");
        out.append(code);
        out.append("</code></pre>");
        out.append(stripDanglingParagraphClose(after));

        String normalized = out.toString();
        return normalized.equals(html) ? null : normalized;
    }

    private static int consumeOpeningFence(String html, int openStart) {
        int index = openStart + FENCE.length();

        while (index < html.length()) {
            char c = html.charAt(index);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '+' || c == '.' || c == '-') {
                index++;
                continue;
            }
            break;
        }

        return index;
    }

    private static ClosingFence findClosingFence(String html, int from) {
        int index = from;

        while (index < html.length()) {
            if (startsWithAt(html, FENCE, index)) {
                return new ClosingFence(index, index + FENCE.length());
            }

            if (startsWithAt(html, "``", index)) {
                int afterTicks = index + 2;
                int afterSpaces = skipSpaces(html, afterTicks);

                if (startsWithIgnoreCaseAt(html, "</code>", afterSpaces)) {
                    return new ClosingFence(index, afterSpaces + "</code>".length());
                }
            }

            index++;
        }

        return null;
    }

    private static int skipSpaces(String value, int index) {
        while (index < value.length()) {
            char c = value.charAt(index);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                index++;
                continue;
            }
            break;
        }

        return index;
    }

    private static String closeOpenParagraphBeforeFence(String before) {
        String normalized = removeTrailingBreak(before);
        if (hasUnclosedParagraph(normalized)) {
            return normalized + "</p>";
        }

        return normalized;
    }

    private static String stripDanglingParagraphClose(String after) {
        if (after == null || after.length() == 0) {
            return after;
        }

        return after.replaceFirst("(?is)^\\s*</p>\\s*", "");
    }

    private static String removeTrailingBreak(String value) {
        if (value == null || value.length() == 0) {
            return value;
        }

        return value.replaceFirst("(?is)(\\s*<br\\s*/?>\\s*)+$", "");
    }

    private static boolean hasUnclosedParagraph(String value) {
        int open = lastIndexOfIgnoreCase(value, "<p");
        int close = lastIndexOfIgnoreCase(value, "</p>");
        return open >= 0 && open > close;
    }

    private static String htmlFragmentToCode(String fragment) {
        String code = fragment;

        code = code.replaceFirst("(?is)^\\s*</p>\\s*", "");
        code = code.replaceAll("(?is)<p[^>]*>", "\n");
        code = code.replaceAll("(?is)</p>", "\n");
        code = code.replaceAll("(?is)<br\\s*/?>", "\n");
        code = code.replaceAll("(?is)</?code>", "");

        return trimBlankCodeLines(code);
    }

    private static String normalizeParagraphs(String html) {
        Matcher paragraphMatcher = PARAGRAPH_PATTERN.matcher(html);
        StringBuffer out = new StringBuffer();
        boolean changed = false;

        while (paragraphMatcher.find()) {
            String paragraphBody = paragraphMatcher.group(1);
            String normalized = normalizeParagraphBody(paragraphBody);

            if (!normalized.equals(paragraphBody)) {
                changed = true;
                paragraphMatcher.appendReplacement(out, Matcher.quoteReplacement(normalized));
            } else {
                paragraphMatcher.appendReplacement(out, Matcher.quoteReplacement(paragraphMatcher.group(0)));
            }
        }

        paragraphMatcher.appendTail(out);

        if (changed) {
            return out.toString();
        }

        return normalizeParagraphBodyFallback(html);
    }

    private static String normalizeParagraphBodyFallback(String html) {
        String normalized = normalizeParagraphBody(html);
        return normalized.equals(html) ? html : normalized;
    }

    private static String normalizeParagraphBody(String paragraphBody) {
        Matcher codeMatcher = CODE_PATTERN.matcher(paragraphBody);
        StringBuffer out = new StringBuffer();
        boolean changed = false;

        while (codeMatcher.find()) {
            String codeBody = codeMatcher.group(1);

            if (!isMultilineCode(codeBody)) {
                codeMatcher.appendReplacement(out, Matcher.quoteReplacement(codeMatcher.group(0)));
                continue;
            }

            changed = true;
            StringBuilder replacement = new StringBuilder();
            replacement.append("</p><pre><code>");
            replacement.append(trimCodeBlockEdges(codeBody));
            replacement.append("</code></pre><p>");

            codeMatcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }

        codeMatcher.appendTail(out);

        return changed ? out.toString() : paragraphBody;
    }

    private static boolean isMultilineCode(String codeBody) {
        return containsIgnoreCase(codeBody, "<br")
                || codeBody.indexOf('\n') >= 0
                || codeBody.indexOf('\r') >= 0;
    }

    private static String trimCodeBlockEdges(String codeBody) {
        String normalized = codeBody;

        normalized = normalized.replaceFirst("(?is)^(\\s|<br\\s*/?>)+", "");
        normalized = normalized.replaceFirst("(?is)(\\s|<br\\s*/?>)+$", "");

        return normalized;
    }

    private static String trimBlankCodeLines(String code) {
        String normalized = code;

        normalized = normalized.replaceFirst("(?s)^(?:\\s|\\u00a0|&#160;|&nbsp;|<br\\s*/?>)+", "");
        normalized = normalized.replaceFirst("(?s)(?:\\s|\\u00a0|&#160;|&nbsp;|<br\\s*/?>)+$", "");

        return normalized;
    }

    private static boolean startsWithAt(String value, String needle, int index) {
        return index >= 0
                && needle != null
                && index + needle.length() <= value.length()
                && value.regionMatches(index, needle, 0, needle.length());
    }

    private static boolean startsWithIgnoreCaseAt(String value, String needle, int index) {
        return index >= 0
                && needle != null
                && index + needle.length() <= value.length()
                && value.regionMatches(true, index, needle, 0, needle.length());
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null
                && needle != null
                && value.toLowerCase().contains(needle.toLowerCase());
    }

    private static int lastIndexOfIgnoreCase(String value, String needle) {
        if (value == null || needle == null) {
            return -1;
        }

        return value.toLowerCase().lastIndexOf(needle.toLowerCase());
    }

    private static final class ClosingFence {
        private final int start;
        private final int end;

        private ClosingFence(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
