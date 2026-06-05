/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.http.reddit;

import androidx.annotation.NonNull;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @noinspection unused
 */
public class RedditFixAudioInDownloadsInterceptor implements Interceptor {
    private static boolean enabled = false;
    private static final Pattern VIDEO_REGEX = Pattern.compile("^(https?://v\\.redd\\.it/[a-z0-9]+)/audio");

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        if (!enabled) {
            return chain.proceed(request);
        }

        Matcher matcher = VIDEO_REGEX.matcher(url);
        if (!matcher.find()) {
            return chain.proceed(request);
        }

        try {
            String baseUrl = matcher.group(1);

            /*
             * The intercepted request already contains the v.redd.it media base URL,
             * so use the DASH manifest directly instead of resolving the Reddit post
             * JSON through the v.redd.it redirect path.
             */
            String dashUrl = baseUrl + "/DASHPlaylist.mpd";

            String audioFilename;
            try (Response dashPlaylistResponse = HttpUtils.get(dashUrl)) {
                if (dashPlaylistResponse.body() == null) {
                    throw new RuntimeException("DASH playlist response body was null");
                }

                try (InputStream dashPlaylistStream = dashPlaylistResponse.body().byteStream()) {
                    audioFilename = parseDashPlaylist(dashPlaylistStream);
                }
            }

            return HttpUtils.get(baseUrl + "/" + audioFilename);
        } catch (NoAudioTrackException e) {
            return chain.proceed(request);
        } catch (Exception e) {
            LoggingUtils.logException(false, () -> "Failed to retrieve audio: " + e);
            return chain.proceed(request);
        }
    }

    private String parseDashPlaylist(InputStream xmlDocumentStream) throws IOException, SAXException, ParserConfigurationException {
        /*
        Looking for something like this in the DASH playlist:

        <Representation audioSamplingRate="48000" bandwidth="131398" codecs="mp4a.40.2" id="7" mimeType="audio/mp4">
          <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2" />
          <BaseURL>DASH_AUDIO_128.mp4</BaseURL>
          <SegmentBase indexRange="833-1008" timescale="48000">
            <Initialization range="0-832" />
          </SegmentBase>
        </Representation>
        */

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlDocumentStream);
        doc.getDocumentElement().normalize();

        NodeList representations = doc.getElementsByTagName("Representation");
        int maxBandwidth = 0;
        String baseUrl = null;

        for (int i = 0; i < representations.getLength(); i++) {
            Element representation = (Element) representations.item(i);

            if (!looksLikeAudioRepresentation(representation)) {
                continue;
            }

            String bandwidthStr = representation.getAttribute("bandwidth");
            if (bandwidthStr == null || bandwidthStr.isBlank()) {
                continue;
            }

            NodeList baseUrls = representation.getElementsByTagName("BaseURL");
            if (baseUrls == null || baseUrls.getLength() == 0 || baseUrls.item(0) == null) {
                continue;
            }

            int bandwidth = Integer.parseInt(bandwidthStr);
            if (bandwidth > maxBandwidth) {
                maxBandwidth = bandwidth;
                baseUrl = baseUrls.item(0).getTextContent();
            }
        }

        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }

        throw new NoAudioTrackException();
    }

    private boolean looksLikeAudioRepresentation(Element representation) {
        if (attributeContains(representation, "mimeType", "audio")) return true;
        if (attributeContains(representation, "contentType", "audio")) return true;
        if (attributeStartsWith(representation, "codecs", "mp4a")) return true;

        Node parent = representation.getParentNode();
        if (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (attributeContains(parentElement, "mimeType", "audio")) return true;
            if (attributeContains(parentElement, "contentType", "audio")) return true;
            if (attributeStartsWith(parentElement, "codecs", "mp4a")) return true;
        }

        return false;
    }

    private boolean attributeContains(Element element, String attributeName, String needle) {
        String value = element.getAttribute(attributeName);
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean attributeStartsWith(Element element, String attributeName, String prefix) {
        String value = element.getAttribute(attributeName);
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private static class NoAudioTrackException extends RuntimeException {
    }
}
