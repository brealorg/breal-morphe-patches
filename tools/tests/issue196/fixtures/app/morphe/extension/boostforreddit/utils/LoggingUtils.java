package app.morphe.extension.boostforreddit.utils;
import java.util.function.Supplier;
public final class LoggingUtils {
    public static int failures;
    public static void logInfo(boolean success, Supplier<String> message) {
        message.get();
        if (!success) failures++;
    }
    public static void logException(boolean success, Supplier<String> message) {
        message.get();
        if (!success) failures++;
    }
}
