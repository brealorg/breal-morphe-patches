package app.morphe.extension.boostforreddit.http.redgifs;

import androidx.annotation.NonNull;

import java.io.IOException;

import app.morphe.extension.shared.Logger;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Boost's Redgifs URL cache (Firebase remote config flag {@code rg_api_save}) appends a
 * {@code &boost_expires=<epoch millis>} marker to every URL it stores, but the reader
 * ({@code h0.g0}) never strips it before handing the URL to the video player. When the
 * resolved URL has no query string the marker is glued onto the path itself
 * ({@code ....mp4&boost_expires=123}), so the expiry check cannot even see it. Either way
 * the CDN rejects the request with a 403, the player burns ~6 seconds in its retry ladder,
 * and only then does Boost's own 403 recovery ({@code rg_api_retry}) re-resolve and play.
 * Every tap on a Redgifs post pays that penalty again because the fresh URL is re-cached
 * with the same marker.
 *
 * <p>Stripping the marker here, just before the request leaves the app, keeps the cache
 * usable: the stored URL is valid once the marker is removed, so cached opens play
 * immediately instead of 403-ing into the recovery path.
 */
public class RedgifsUrlSanitizerInterceptor implements Interceptor {
    private static final String MARKER = "boost_expires";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();

        String host = url.host();
        if (!host.equals("redgifs.com") && !host.endsWith(".redgifs.com")) {
            return chain.proceed(request);
        }

        HttpUrl cleaned = removeMarker(url);
        if (cleaned == null || cleaned.equals(url)) {
            return chain.proceed(request);
        }

        Logger.printDebug(() -> "Removed " + MARKER + " marker from " + host + " request");
        return chain.proceed(request.newBuilder().url(cleaned).build());
    }

    /**
     * Removes the marker whether it was parsed as a real query parameter
     * ({@code ?expires=...&boost_expires=123}) or ended up inside the path because the
     * original URL had no query string ({@code /Name.mp4&boost_expires=123}).
     * Returns null when the URL carries no marker.
     */
    static HttpUrl removeMarker(HttpUrl url) {
        boolean changed = false;
        HttpUrl.Builder builder = url.newBuilder();

        if (url.queryParameter(MARKER) != null) {
            builder.removeAllQueryParameters(MARKER);
            changed = true;
        }

        String path = url.encodedPath();
        int glued = path.indexOf("&" + MARKER + "=");
        if (glued >= 0) {
            builder.encodedPath(path.substring(0, glued));
            changed = true;
        }

        return changed ? builder.build() : null;
    }
}
