package app.morphe.extension.boostforreddit.utils;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * A non-owning lookup for objects owned elsewhere, such as attached Views.
 * Both keys and values are weak: a value may point back to its key through
 * a Context without making the registry an Activity retention root.
 * Do not use this for state whose only owner would be the registry.
 */
final class WeakValueRegistry<K, V> {
    private final WeakHashMap<K, WeakReference<V>> entries = new WeakHashMap<>();

    synchronized V get(K key) {
        WeakReference<V> reference = entries.get(key);
        V value = reference == null ? null : reference.get();
        if (reference != null && value == null) {
            entries.remove(key);
        }
        return value;
    }

    synchronized void put(K key, V value) {
        if (value == null) {
            entries.remove(key);
        } else {
            entries.put(key, new WeakReference<>(value));
        }
    }

    synchronized V remove(K key) {
        WeakReference<V> reference = entries.remove(key);
        return reference == null ? null : reference.get();
    }
}
