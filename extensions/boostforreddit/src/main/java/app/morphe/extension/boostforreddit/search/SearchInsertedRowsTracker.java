package app.morphe.extension.boostforreddit.search;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Tracks Search rows by a stable owner and removes only rows owned by Morphe. */
final class SearchInsertedRowsTracker<K> {
    private final WeakHashMap<K, List<Object>> insertedByOwner = new WeakHashMap<>();

    void record(K owner, List<?> inserted) {
        if (owner == null) {
            throw new NullPointerException("owner");
        }

        if (inserted == null || inserted.isEmpty()) {
            insertedByOwner.remove(owner);
            return;
        }

        ArrayList<Object> snapshot = new ArrayList<Object>(inserted.size());
        snapshot.addAll(inserted);
        insertedByOwner.put(owner, snapshot);
    }

    RemovalStats remove(K owner, List<?> rows) {
        List<Object> previous = insertedByOwner.remove(owner);
        if (previous == null || previous.isEmpty()) {
            return RemovalStats.EMPTY;
        }

        int matchedBefore = countOwnedRowsByIdentity(rows, previous);
        int removedOwned = removeOwnedRowsByIdentity(rows, previous);
        int remainingOwned = countOwnedRowsByIdentity(rows, previous);
        return new RemovalStats(previous.size(), matchedBefore, removedOwned, remainingOwned);
    }

    private static int countOwnedRowsByIdentity(List<?> rows, List<?> owned) {
        int matches = 0;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Object row = rows.get(rowIndex);
            for (int ownedIndex = 0; ownedIndex < owned.size(); ownedIndex++) {
                if (row == owned.get(ownedIndex)) {
                    matches++;
                    break;
                }
            }
        }
        return matches;
    }

    private static int removeOwnedRowsByIdentity(List<?> rows, List<?> owned) {
        int removed = 0;
        for (int rowIndex = rows.size() - 1; rowIndex >= 0; rowIndex--) {
            Object row = rows.get(rowIndex);
            for (int ownedIndex = 0; ownedIndex < owned.size(); ownedIndex++) {
                if (row == owned.get(ownedIndex)) {
                    rows.remove(rowIndex);
                    removed++;
                    break;
                }
            }
        }
        return removed;
    }

    static final class RemovalStats {
        static final RemovalStats EMPTY = new RemovalStats(0, 0, 0, 0);

        final int previous;
        final int matchedBefore;
        final int removedOwned;
        final int remainingOwned;

        RemovalStats(int previous, int matchedBefore, int removedOwned, int remainingOwned) {
            this.previous = previous;
            this.matchedBefore = matchedBefore;
            this.removedOwned = removedOwned;
            this.remainingOwned = remainingOwned;
        }
    }
}
