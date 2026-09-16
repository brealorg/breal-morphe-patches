package app.morphe.extension.boostforreddit.http;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
public final class AutoSavingCache {
    public final Map<String,String> values = new HashMap<>();
    public AutoSavingCache(String namespace, int size) {}
    public Optional<String> get(String key) { return Optional.ofNullable(values.get(key)); }
    public void put(String key, String value) { values.put(key, value); }
}
