package app.morphe.extension.boostforreddit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.fixes.slink.BaseFixSLinksPatch;

import com.rubenmayayo.reddit.ui.activities.WebViewActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @noinspection unused
 */
public class FixSLinksPatch extends BaseFixSLinksPatch {
    private static final String LOG_TAG = "MorphePoll";
    private static final String POLL_ROUTE_MARKER =
            "MORPHE_BOOST_POLL_WEBVIEW_V1";

    private static final Pattern REDDIT_POLL_URL = Pattern.compile(
            "^https?://(?:[a-z0-9-]+\\.)*reddit\\.com/poll/([a-z0-9]+)(?:[/?#].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REDDIT_PLAYER_URL = Pattern.compile(
            "^https?://(?:www\\.)?reddit\\.com/link/[^/]+/video/([^/?#]+)/player(?:[/?#].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    static {
        INSTANCE = new FixSLinksPatch();
    }

    private FixSLinksPatch() {
        webViewActivityClass = WebViewActivity.class;
    }

    public static boolean patchResolveSLink(String link) {
        if (openRedditPollLink(link)) {
            return true;
        }

        if (openRedditPlayerLink(link)) {
            return true;
        }

        return INSTANCE.resolveSLink(link);
    }

    public static void patchSetAccessToken(String accessToken) {
        INSTANCE.setAccessToken(accessToken);
    }

    private static boolean openRedditPollLink(String link) {
        if (link == null) {
            return false;
        }

        Matcher matcher = REDDIT_POLL_URL.matcher(link);
        if (!matcher.matches()) {
            return false;
        }

        String pollId = matcher.group(1);
        if (pollId == null || pollId.isEmpty()) {
            return false;
        }

        String pollUrl = "https://www.reddit.com/poll/" + pollId;
        Context context = Utils.getContext();

        Intent intent = new Intent(context, WebViewActivity.class);
        intent.putExtra("url", pollUrl);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Log.d(LOG_TAG, POLL_ROUTE_MARKER + " url=" + pollUrl);
        context.startActivity(intent);
        return true;
    }

    private static boolean openRedditPlayerLink(String link) {
        if (link == null) {
            return false;
        }

        Matcher matcher = REDDIT_PLAYER_URL.matcher(link);
        if (!matcher.matches()) {
            return false;
        }

        String videoId = matcher.group(1);
        if (videoId == null || videoId.isEmpty()) {
            return false;
        }

        Context context = Utils.getContext();
        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://v.redd.it/" + videoId)
        );
        intent.setPackage(context.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }
}
