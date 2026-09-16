package app.morphe.extension.boostforreddit.http.wayback;
import java.io.IOException;
import okhttp3.Request;
public final class WaybackMachine {
    public static WaybackResponse getFromWayback(Request request, String url) throws IOException {
        return new WaybackResponse();
    }
}
