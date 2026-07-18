package app.morphe.extension.boostforreddit.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public final class SearchInsertedRowsTrackerContractTest {
    private static final int STRESS_CYCLES = 10000;

    private SearchInsertedRowsTrackerContractTest() {
    }

    public static void main(String[] args) {
        reproduceLegacyMutableListKeyFailure();
        preserveEqualButUnownedRows();
        isolateOwners();
        stressStableOwnerAcrossMutableRows();

        System.out.println("LEGACY_MUTABLE_LIST_KEY=REPRODUCED");
        System.out.println("IDENTITY_EQUAL_ROW=PASS");
        System.out.println("OWNER_ISOLATION=PASS");
        System.out.println("STRESS_CYCLES=" + STRESS_CYCLES);
        System.out.println("RESULT=MORPHE_ISSUE61_SEARCH_DEDUP_CONTRACT_OK");
    }

    private static void reproduceLegacyMutableListKeyFailure() {
        WeakHashMap<ArrayList<Object>, List<Object>> legacy = new WeakHashMap<>();
        ArrayList<Object> rows = new ArrayList<Object>();
        rows.add(new HashRow(1));
        legacy.put(rows, Collections.<Object>singletonList(new Object()));

        boolean lookupLost = false;
        for (int index = 2; index < 1024; index++) {
            rows.add(new HashRow(index));
            if (legacy.get(rows) == null) {
                lookupLost = true;
                break;
            }
        }

        require(lookupLost, "legacy mutable ArrayList key unexpectedly remained addressable");
    }

    private static void preserveEqualButUnownedRows() {
        SearchInsertedRowsTracker<Object> tracker = new SearchInsertedRowsTracker<>();
        Object owner = new Object();
        EqualRow owned = new EqualRow("same");
        EqualRow foreign = new EqualRow("same");
        ArrayList<Object> rows = new ArrayList<Object>(Arrays.<Object>asList(owned, foreign));

        tracker.record(owner, Collections.<Object>singletonList(owned));
        SearchInsertedRowsTracker.RemovalStats stats = tracker.remove(owner, rows);

        requireStats(stats, 1, 1, 1, 0, "equal-row identity removal");
        require(rows.size() == 1 && rows.get(0) == foreign,
            "value-equal foreign row was removed");
    }

    private static void isolateOwners() {
        SearchInsertedRowsTracker<Object> tracker = new SearchInsertedRowsTracker<>();
        Object ownerA = new Object();
        Object ownerB = new Object();
        Object rowA = new Object();
        Object rowB = new Object();
        ArrayList<Object> rows = new ArrayList<Object>(Arrays.asList(rowA, rowB));

        tracker.record(ownerA, Collections.singletonList(rowA));
        tracker.record(ownerB, Collections.singletonList(rowB));

        requireStats(tracker.remove(ownerA, rows), 1, 1, 1, 0, "owner A");
        require(rows.size() == 1 && rows.get(0) == rowB, "owner A removed owner B row");
        requireStats(tracker.remove(ownerB, rows), 1, 1, 1, 0, "owner B");
        require(rows.isEmpty(), "owner B row remained");
    }

    private static void stressStableOwnerAcrossMutableRows() {
        SearchInsertedRowsTracker<Object> tracker = new SearchInsertedRowsTracker<>();
        Object activityOwner = new Object();
        ArrayList<Object> rows = new ArrayList<Object>();
        EqualRow nativeEqualRow = new EqualRow("header");
        rows.add(nativeEqualRow);

        for (int cycle = 0; cycle < STRESS_CYCLES; cycle++) {
            ArrayList<Object> inserted = new ArrayList<Object>();
            inserted.add(new EqualRow("header"));
            for (int row = 0; row < 10; row++) {
                inserted.add(new EqualRow("community-" + cycle + "-" + row));
            }

            rows.addAll(inserted);
            tracker.record(activityOwner, inserted);

            Object goTo = new Object();
            Object random = new Object();
            rows.add(0, goTo);
            rows.add(random);
            Collections.rotate(rows, (cycle % 5) + 1);

            SearchInsertedRowsTracker.RemovalStats stats = tracker.remove(activityOwner, rows);
            requireStats(stats, 11, 11, 11, 0, "cycle " + cycle);
            require(!containsAnyIdentity(rows, inserted), "owned row remained in cycle " + cycle);
            require(containsIdentity(rows, nativeEqualRow),
                "value-equal native row was removed in cycle " + cycle);

            rows.remove(goTo);
            rows.remove(random);
            require(rows.size() == 1 && rows.get(0) == nativeEqualRow,
                "non-owned row contract drifted in cycle " + cycle);
        }
    }

    private static boolean containsAnyIdentity(List<?> rows, List<?> candidates) {
        for (Object candidate : candidates) {
            if (containsIdentity(rows, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentity(List<?> rows, Object candidate) {
        for (Object row : rows) {
            if (row == candidate) {
                return true;
            }
        }
        return false;
    }

    private static void requireStats(
        SearchInsertedRowsTracker.RemovalStats stats,
        int previous,
        int matchedBefore,
        int removedOwned,
        int remainingOwned,
        String context
    ) {
        require(stats.previous == previous, context + ": previous=" + stats.previous);
        require(stats.matchedBefore == matchedBefore,
            context + ": matchedBefore=" + stats.matchedBefore);
        require(stats.removedOwned == removedOwned,
            context + ": removedOwned=" + stats.removedOwned);
        require(stats.remainingOwned == remainingOwned,
            context + ": remainingOwned=" + stats.remainingOwned);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class HashRow {
        private final int hash;

        HashRow(int hash) {
            this.hash = hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class EqualRow {
        private final String value;

        EqualRow(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualRow && value.equals(((EqualRow) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
