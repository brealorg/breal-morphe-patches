package app.morphe.extension.boostforreddit.utils;
public final class MarkdownRenderer {
    public static String render(String text) {
        return "<p>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>";
    }
}
