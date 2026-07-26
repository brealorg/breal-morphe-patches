#!/usr/bin/env bash

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/extensions/boostforreddit/src/main/java/app/morphe/extension/boostforreddit/codeblock/CodeBlockHtmlNormalizer.java"
TMP="$(mktemp -d)"
FAIL=0

cleanup() {
    rm -rf "$TMP"
}
trap cleanup EXIT

fail() {
    printf 'FAIL=%s\n' "$*"
    FAIL=1
}

printf 'CONTRACT=BOOST_HTML_NORMALIZER_V7\n'

if ! command -v javac >/dev/null 2>&1; then
    fail "JAVAC_NOT_FOUND"
fi

if [ ! -f "$SOURCE" ]; then
    fail "NORMALIZER_SOURCE_NOT_FOUND"
fi

if [ "$FAIL" -eq 0 ]; then
    grep -Fq 'MORPHE_CODEBLOCK_HTML_NORMALIZER_V7_MALFORMED_PARENTHESIZED_LINKS' "$SOURCE" ||
        fail "V7_MARKER_MISSING"
    grep -Fq 'normalizeMalformedParenthesizedLinks' "$SOURCE" ||
        fail "V7_NORMALIZER_METHOD_MISSING"
fi

if [ "$FAIL" -eq 0 ]; then
    cat > "$TMP/CodeBlockHtmlNormalizerContract.java" <<'JAVA'
import app.morphe.extension.boostforreddit.codeblock.CodeBlockHtmlNormalizer;

public final class CodeBlockHtmlNormalizerContract {
    private static void assertEquals(String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    name + "\nEXPECTED=" + expected + "\nACTUAL=" + actual
            );
        }
        System.out.println("PASS=" + name);
    }

    private static void assertContains(String name, String needle, String actual) {
        if (!actual.contains(needle)) {
            throw new AssertionError(
                    name + "\nNEEDLE=" + needle + "\nACTUAL=" + actual
            );
        }
        System.out.println("PASS=" + name);
    }

    public static void main(String[] args) {
        String malformed =
                "<p>ref: <a href=\"https://betawiki.net/wiki/Windows_XP_build_2474_(main\">"
                + "https://betawiki.net/wiki/Windows_XP_build_2474_(main)#Login_screen"
                + "</a>#Login_screen) and the gif can be found in the gallery.</p>";
        String repaired =
                "<p>ref: <a href=\"https://betawiki.net/wiki/Windows_XP_build_2474_(main)#Login_screen\">"
                + "https://betawiki.net/wiki/Windows_XP_build_2474_(main)#Login_screen"
                + "</a> and the gif can be found in the gallery.</p>";

        assertEquals(
                "ISSUE125_EXACT_REPRO_REPAIRED",
                repaired,
                CodeBlockHtmlNormalizer.normalize(malformed)
        );
        assertEquals(
                "ISSUE125_REPAIR_IDEMPOTENT",
                repaired,
                CodeBlockHtmlNormalizer.normalize(repaired)
        );

        String mismatchedFragment =
                "<p><a href=\"https://example.com/a(b\">https://example.com/a(b)#one</a>#two)</p>";
        assertEquals(
                "MISMATCHED_FRAGMENT_UNCHANGED",
                mismatchedFragment,
                CodeBlockHtmlNormalizer.normalize(mismatchedFragment)
        );

        String nonUrlLabel =
                "<p><a href=\"https://example.com/a(b\">article</a>#one)</p>";
        assertEquals(
                "NON_URL_LABEL_UNCHANGED",
                nonUrlLabel,
                CodeBlockHtmlNormalizer.normalize(nonUrlLabel)
        );

        String balancedHrefWithResidue =
                "<p><a href=\"https://example.com/a(b)#one\">"
                + "https://example.com/a(b)#one</a>#one)</p>";
        assertEquals(
                "BALANCED_HREF_WITH_RESIDUE_UNCHANGED",
                balancedHrefWithResidue,
                CodeBlockHtmlNormalizer.normalize(balancedHrefWithResidue)
        );

        String relativeLink =
                "<p><a href=\"/wiki/a(b\">/wiki/a(b)#one</a>#one)</p>";
        assertEquals(
                "RELATIVE_LINK_UNCHANGED",
                relativeLink,
                CodeBlockHtmlNormalizer.normalize(relativeLink)
        );

        String cleanPre = "<pre><code>line</code></pre>";
        assertEquals(
                "CLEAN_PRE_UNCHANGED",
                cleanPre,
                CodeBlockHtmlNormalizer.normalize(cleanPre)
        );

        String legacyMultilineCode = "<p>before <code>line1\nline2</code> after</p>";
        assertContains(
                "EXISTING_MULTILINE_CODE_PATH_PRESERVED",
                "<pre><code>line1\nline2</code></pre>",
                CodeBlockHtmlNormalizer.normalize(legacyMultilineCode)
        );

        if (!"MORPHE_CODEBLOCK_HTML_NORMALIZER_V7_MALFORMED_PARENTHESIZED_LINKS"
                .equals(CodeBlockHtmlNormalizer.MARKER)) {
            throw new AssertionError("V7_MARKER_VALUE_MISMATCH");
        }
        System.out.println("PASS=V7_MARKER_VALUE");
        System.out.println("RESULT=BOOST_HTML_NORMALIZER_V7_CONTRACT_PASS");
    }
}
JAVA

    mkdir -p "$TMP/classes"

    javac -encoding UTF-8 -d "$TMP/classes" \
        "$SOURCE" \
        "$TMP/CodeBlockHtmlNormalizerContract.java" ||
        fail "CONTRACT_JAVAC_FAILED"
fi

if [ "$FAIL" -eq 0 ]; then
    java -cp "$TMP/classes" CodeBlockHtmlNormalizerContract ||
        fail "CONTRACT_RUNTIME_FAILED"
fi

if [ "$FAIL" -eq 0 ]; then
    printf 'RESULT=BOOST_HTML_NORMALIZER_V7_CONTRACT_PASS\n'
else
    printf 'RESULT=BOOST_HTML_NORMALIZER_V7_CONTRACT_FAIL\n'
fi

exit "$FAIL"
