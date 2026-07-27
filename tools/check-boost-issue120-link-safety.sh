#!/usr/bin/env bash

SOURCE="extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/giphy/InlineGiphyCommentPreview.java"
MARKER="MORPHE_BOOST_ISSUE120_LINK_SAFETY_V1"
FAIL=0

fail() {
    printf 'FAIL=%s\n' "$1"
    FAIL=1
}

printf 'CONTRACT=BOOST_ISSUE120_LINK_SAFETY_V1\n'

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

test -f "$ANDROID_JAR" ||
    fail 'ANDROID_JAR_NOT_FOUND'

test -f "$SOURCE" ||
    fail 'SOURCE_NOT_FOUND'

grep -Fq "$MARKER" "$SOURCE" ||
    fail 'CLEAN_FIX_MARKER_MISSING'

if grep -Fq 'result = result.replace(candidate, "");' "$SOURCE"; then
    fail 'UNSAFE_BLANKET_REPLACE_PRESENT'
fi

for forbidden in \
    MORPHE_BOOST_ISSUE120_URLSPAN_DIAG_V1 \
    Issue120DiagnosticUrlSpan \
    URLSPAN_SNAPSHOT \
    COMMENT_LINK_CLICK \
    PREVIEW_CLICK
do
    if grep -Fq "$forbidden" "$SOURCE"; then
        fail "DIAGNOSTIC_TOKEN_PRESENT_${forbidden}"
    fi
done

TMP="$(mktemp -d /tmp/morphe-issue120-clean-contract-XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

HARNESS="$TMP/Issue120LinkSafetyContract.java"
LOG_STUB="$TMP/android/util/Log.java"
CLASSES="$TMP/classes"
mkdir -p "$CLASSES" "$(dirname "$LOG_STUB")"

cat > "$LOG_STUB" <<'JAVA'
package android.util;

public final class Log {
    private Log() {
    }

    public static int d(String tag, String message) {
        return 0;
    }

    public static int d(String tag, String message, Throwable throwable) {
        return 0;
    }

    public static int i(String tag, String message) {
        return 0;
    }

    public static int i(String tag, String message, Throwable throwable) {
        return 0;
    }

    public static int w(String tag, String message) {
        return 0;
    }

    public static int w(String tag, String message, Throwable throwable) {
        return 0;
    }

    public static int e(String tag, String message) {
        return 0;
    }

    public static int e(String tag, String message, Throwable throwable) {
        return 0;
    }
}
JAVA

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

    private static void assertEquals(
            String name,
            String expected,
            String actual
    ) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    name + " expected=" + expected + " actual=" + actual
            );
        }
        System.out.println("PASS=" + name);
    }

    private static void assertNotContains(
            String name,
            String forbidden,
            String actual
    ) {
        if (actual != null && actual.contains(forbidden)) {
            throw new AssertionError(
                    name + " forbidden=" + forbidden + " actual=" + actual
            );
        }
        System.out.println("PASS=" + name);
    }

    public static void main(String[] args) throws Exception {
        String source = "https://i.imgur.com/dSsphbN.jpeg";
        String stripped = "i.imgur.com/dSsphbN.jpeg";
        String label =
                "Stuff like this happening every episode is why 100 Kanojo "
                        + "is banned from r/animenocontext.";

        String exactIssue =
                "&lt;div class=\"md\"&gt;&lt;p&gt;"
                        + "&lt;a href=\"" + source + "\"&gt;"
                        + label
                        + "&lt;/a&gt;&lt;/p&gt;\\n&lt;/div&gt;";

        assertEquals(
                "EXACT_ISSUE120_DESCRIPTIVE_ANCHOR_PRESERVED",
                exactIssue,
                invoke(exactIssue, source)
        );

        String rawDescriptive =
                "<p><a href=\"" + source + "\">" + label + "</a></p>";
        assertEquals(
                "RAW_DESCRIPTIVE_ANCHOR_PRESERVED",
                rawDescriptive,
                invoke(rawDescriptive, source)
        );

        String encodedSourceLabel =
                "&lt;p&gt;&lt;a href=\"" + source + "\"&gt;"
                        + stripped
                        + "&lt;/a&gt;&lt;/p&gt;";
        String encodedSourceLabelResult =
                invoke(encodedSourceLabel, source);
        assertNotContains(
                "ENCODED_SOURCE_LABEL_REMOVED",
                stripped,
                encodedSourceLabelResult
        );
        assertNotContains(
                "ENCODED_SOURCE_LABEL_NO_EMPTY_HREF",
                "href=\"\"",
                encodedSourceLabelResult
        );

        String rawSourceLabel =
                "<p><a href=\"" + source + "\">" + source + "</a></p>";
        assertEquals(
                "RAW_SOURCE_LABEL_REMOVED",
                "&#8203;",
                invoke(rawSourceLabel, source)
        );

        String bareSource =
                "<p>before " + source + " after</p>";
        assertNotContains(
                "BARE_SOURCE_TEXT_REMOVED",
                source,
                invoke(bareSource, source)
        );

        String encodedAttribute =
                "&lt;span data-source=\"" + source + "\"&gt;"
                        + "keep&lt;/span&gt;";
        assertEquals(
                "ENCODED_NON_HREF_ATTRIBUTE_PRESERVED",
                encodedAttribute,
                invoke(encodedAttribute, source)
        );

        String unrelated =
                "&lt;p&gt;&lt;a href=\"https://example.com\"&gt;"
                        + "keep&lt;/a&gt;&lt;/p&gt;";
        assertEquals(
                "UNRELATED_ENCODED_LINK_PRESERVED",
                unrelated,
                invoke(unrelated, source)
        );

        System.out.println(
                "RESULT=BOOST_ISSUE120_LINK_SAFETY_V1_PASS"
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
    javac \
        -d "$CLASSES" \
        "$LOG_STUB" ||
        fail 'HOST_ANDROID_LOG_STUB_COMPILE_FAILED'
fi

if [ "$FAIL" -eq 0 ]; then
    java \
        -cp "$CLASSES:$ANDROID_JAR" \
        app.morphe.extension.boostforreddit.giphy.Issue120LinkSafetyContract ||
        fail 'CONTRACT_RUNTIME_FAILED'
fi

if [ "$FAIL" -eq 0 ]; then
    printf 'RESULT=BOOST_ISSUE120_LINK_SAFETY_V1_PASS\n'
else
    printf 'RESULT=BOOST_ISSUE120_LINK_SAFETY_V1_FAIL\n'
fi

exit "$FAIL"
