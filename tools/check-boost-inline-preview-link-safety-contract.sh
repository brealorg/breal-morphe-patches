#!/usr/bin/env bash

SOURCE="extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/giphy/InlineGiphyCommentPreview.java"
FAIL=0

fail() {
    printf 'FAIL=%s\n' "$*"
    FAIL=1
}

printf 'CONTRACT=BOOST_INLINE_PREVIEW_LINK_SAFETY_ISSUE120_V3\n'

if ! command -v javac >/dev/null 2>&1; then
    fail 'JAVAC_NOT_FOUND'
fi

ANDROID_JAR="$(
    find "${ANDROID_HOME:-$HOME/Android/Sdk}/platforms" \
        -mindepth 2 \
        -maxdepth 2 \
        -type f \
        -name android.jar \
        -printf '%h %p\n' 2>/dev/null |
    sort -V |
    tail -n 1 |
    awk '{print $2}'
)"

if [ -z "$ANDROID_JAR" ] || [ ! -f "$ANDROID_JAR" ]; then
    fail 'ANDROID_JAR_NOT_FOUND'
fi

if [ ! -f "$SOURCE" ]; then
    fail 'SOURCE_NOT_FOUND'
fi

if [ "$FAIL" -eq 0 ]; then
    grep -Fq \
        'MORPHE_BOOST_INLINE_MEDIA_SOURCE_LINK_SAFETY_ISSUE120_V3' \
        "$SOURCE" ||
        fail 'ISSUE120_V3_MARKER_MISSING'

    grep -Fq 'removeSourceLabelAnchors' "$SOURCE" ||
        fail 'SOURCE_LABEL_ANCHOR_CLEANUP_MISSING'
    grep -Fq 'removeTextOutsideHtmlMarkup' "$SOURCE" ||
        fail 'ENCODED_MARKUP_SAFE_TEXT_REMOVAL_MISSING'
    grep -Fq 'descriptiveLinksPreserved=true' "$SOURCE" ||
        fail 'DESCRIPTIVE_LINK_PRESERVATION_MARKER_MISSING'

    if grep -Fq \
        'MORPHE_BOOST_INLINE_MEDIA_SOURCE_LINK_SHAPE_ISSUE120_V1' \
        "$SOURCE"; then
        fail 'SOURCE_SHAPE_DIAGNOSTIC_STILL_PRESENT'
    fi

    if grep -Fq 'result = result.replace(candidate, "");' "$SOURCE"; then
        fail 'UNSAFE_BLANKET_REPLACE_PRESENT'
    fi
fi

TMP="$(mktemp -d /tmp/morphe-issue120-v3-contract-XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

HARNESS="$TMP/Issue120LinkSafetyContract.java"
CLASSES="$TMP/classes"
mkdir -p "$CLASSES"

cat > "$HARNESS" <<'JAVA'
package app.morphe.extension.boostforreddit.giphy;

import java.lang.reflect.Method;

public final class Issue120LinkSafetyContract {
    private static String invoke(String html, String source) throws Exception {
        Method method = InlineGiphyCommentPreview.class.getDeclaredMethod(
                "removePreviewSourceUrlFromHtml",
                String.class,
                String.class
        );
        method.setAccessible(true);
        return (String) method.invoke(null, html, source);
    }

    private static void pass(String name) {
        System.out.println("PASS=" + name);
    }

    private static void fail(String name, String detail) {
        throw new AssertionError(name + ": " + detail);
    }

    private static void assertContains(
            String name,
            String expected,
            String actual
    ) {
        if (actual == null || !actual.contains(expected)) {
            fail(name, "missing=" + expected + " actual=" + actual);
        }
        pass(name);
    }

    private static void assertNotContains(
            String name,
            String forbidden,
            String actual
    ) {
        if (actual != null && actual.contains(forbidden)) {
            fail(name, "forbidden=" + forbidden + " actual=" + actual);
        }
        pass(name);
    }

    private static void assertEquals(
            String name,
            String expected,
            String actual
    ) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail(name, "expected=" + expected + " actual=" + actual);
        }
        pass(name);
    }

    public static void main(String[] args) throws Exception {
        String source = "https://i.imgur.com/dSsphbN.jpeg";
        String stripped = "i.imgur.com/dSsphbN.jpeg";
        String label =
                "Stuff like this happening every episode is why 100 Kanojo "
                        + "is banned from r/animenocontext.";

        String encodedIssue =
                "&lt;div class=\"md\"&gt;&lt;p&gt;"
                        + "&lt;a href=\"" + source + "\"&gt;"
                        + label
                        + "&lt;/a&gt;&lt;/p&gt;\\n&lt;/div&gt;";
        assertEquals(
                "ISSUE120_EXACT_ENCODED_DESCRIPTIVE_LINK_PRESERVED",
                encodedIssue,
                invoke(encodedIssue, source)
        );

        String rawDescriptive =
                "<p><a href=\"" + source + "\">" + label + "</a></p>";
        assertEquals(
                "ISSUE120_RAW_DESCRIPTIVE_LINK_PRESERVED",
                rawDescriptive,
                invoke(rawDescriptive, source)
        );

        String rawSourceLabel =
                "<p><a href=\"" + source + "\">" + stripped + "</a></p>";
        assertEquals(
                "ISSUE120_RAW_SOURCE_LABEL_REMOVED",
                "&#8203;",
                invoke(rawSourceLabel, source)
        );

        String emptyHrefSourceLabel =
                "<p><a href=\"\">" + stripped + "</a></p>";
        assertEquals(
                "ISSUE120_EMPTY_HREF_SOURCE_LABEL_REMOVED",
                "&#8203;",
                invoke(emptyHrefSourceLabel, source)
        );

        String missingHrefSourceLabel =
                "<p><a>" + source + "</a></p>";
        assertEquals(
                "ISSUE120_MISSING_HREF_SOURCE_LABEL_REMOVED",
                "&#8203;",
                invoke(missingHrefSourceLabel, source)
        );

        String encodedSourceLabel =
                "&lt;p&gt;&lt;a href=\"" + source + "\"&gt;"
                        + stripped
                        + "&lt;/a&gt;&lt;/p&gt;";
        String encodedSourceLabelResult = invoke(encodedSourceLabel, source);
        assertNotContains(
                "ISSUE120_ENCODED_SOURCE_LABEL_TEXT_REMOVED",
                stripped,
                encodedSourceLabelResult
        );
        assertNotContains(
                "ISSUE120_ENCODED_SOURCE_LABEL_ANCHOR_REMOVED",
                "&lt;a",
                encodedSourceLabelResult
        );
        assertNotContains(
                "ISSUE120_ENCODED_SOURCE_LABEL_NO_EMPTY_HREF",
                "href=\"\"",
                encodedSourceLabelResult
        );

        String unrelated =
                "<p><a href=\"https://example.com\">keep</a></p>";
        assertEquals(
                "UNRELATED_RAW_LINK_PRESERVED",
                unrelated,
                invoke(unrelated, source)
        );

        String unrelatedEncoded =
                "&lt;p&gt;&lt;a href=\"https://example.com\"&gt;"
                        + "keep&lt;/a&gt;&lt;/p&gt;";
        assertEquals(
                "UNRELATED_ENCODED_LINK_PRESERVED",
                unrelatedEncoded,
                invoke(unrelatedEncoded, source)
        );

        String bare =
                "<p>before " + source + " after</p>";
        String bareResult = invoke(bare, source);
        assertNotContains(
                "BARE_SOURCE_TEXT_REMOVED",
                source,
                bareResult
        );
        assertContains(
                "BARE_NON_SOURCE_TEXT_PRESERVED",
                "before",
                bareResult
        );

        String rawAttributeOnly =
                "<span data-source=\"" + source + "\">keep</span>";
        assertEquals(
                "RAW_NON_HREF_TAG_ATTRIBUTE_NOT_MUTATED",
                rawAttributeOnly,
                invoke(rawAttributeOnly, source)
        );

        String encodedAttributeOnly =
                "&lt;span data-source=\"" + source + "\"&gt;"
                        + "keep&lt;/span&gt;";
        assertEquals(
                "ENCODED_NON_HREF_TAG_ATTRIBUTE_NOT_MUTATED",
                encodedAttributeOnly,
                invoke(encodedAttributeOnly, source)
        );

        System.out.println(
                "RESULT=BOOST_INLINE_PREVIEW_LINK_SAFETY_ISSUE120_V3_PASS"
        );
    }
}
JAVA

if [ "$FAIL" -eq 0 ]; then
    javac \
        -cp "$ANDROID_JAR" \
        -d "$CLASSES" \
        "$SOURCE" \
        "$HARNESS" ||
        fail 'CONTRACT_COMPILE_FAILED'
fi

if [ "$FAIL" -eq 0 ]; then
    java \
        -cp "$CLASSES:$ANDROID_JAR" \
        app.morphe.extension.boostforreddit.giphy.Issue120LinkSafetyContract ||
        fail 'CONTRACT_RUNTIME_FAILED'
fi

if [ "$FAIL" -eq 0 ]; then
    printf 'RESULT=BOOST_INLINE_PREVIEW_LINK_SAFETY_ISSUE120_V3_PASS\n'
else
    printf 'RESULT=BOOST_INLINE_PREVIEW_LINK_SAFETY_ISSUE120_V3_FAIL\n'
fi

exit "$FAIL"
