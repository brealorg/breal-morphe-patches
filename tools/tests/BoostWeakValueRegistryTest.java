package app.morphe.extension.boostforreddit.utils;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

public final class BoostWeakValueRegistryTest {
    private static final class Owner {
        Node attached;
        final byte[] retainedScreen = new byte[64 * 1024];
    }

    private static final class Node {
        final Owner context;
        Node(Owner context) { this.context = context; }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void collect() throws InterruptedException {
        System.gc();
        Thread.sleep(20L);
    }

    private static void awaitCleared(List<? extends WeakReference<?>> references)
            throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            collect();
            boolean allCleared = true;
            for (WeakReference<?> reference : references) {
                if (reference.get() != null) allCleared = false;
            }
            if (allCleared) return;
        }
        throw new AssertionError("objects remained strongly reachable after repeated GC");
    }

    private static WeakReference<Owner> legacyCycle(WeakHashMap<Owner, Node> registry) {
        Owner owner = new Owner();
        Node node = new Node(owner);
        owner.attached = node;
        registry.put(owner, node);
        return new WeakReference<>(owner);
    }

    private static List<WeakReference<?>> closedScreens(
            WeakValueRegistry<Owner, Node> containers,
            WeakValueRegistry<Owner, Node> navigation) {
        List<WeakReference<?>> references = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            Owner owner = new Owner();
            Node container = new Node(owner);
            Node view = new Node(owner);
            owner.attached = container;
            containers.put(owner, container);
            navigation.put(owner, view);
            require(containers.get(owner) == container, "live container lookup");
            require(navigation.get(owner) == view, "live navigation lookup");
            references.add(new WeakReference<>(owner));
            references.add(new WeakReference<>(container));
            references.add(new WeakReference<>(view));
        }
        return references;
    }

    private static WeakReference<Node> detachedValue(
            WeakValueRegistry<Owner, Node> registry, Owner liveOwner) {
        Node node = new Node(liveOwner);
        registry.put(liveOwner, node);
        return new WeakReference<>(node);
    }

    public static void main(String[] args) throws Exception {
        WeakHashMap<Owner, Node> legacy = new WeakHashMap<>();
        WeakReference<Owner> legacyOwner = legacyCycle(legacy);
        for (int i = 0; i < 5; i++) collect();
        require(legacyOwner.get() != null, "negative control must reproduce retention");
        require(legacy.size() == 1, "legacy map must still own its value");
        System.out.println("LEGACY_STRONG_VALUE_CYCLE=REPRODUCED");
        legacy.clear();
        awaitCleared(List.of(legacyOwner));

        WeakValueRegistry<Owner, Node> containers = new WeakValueRegistry<>();
        WeakValueRegistry<Owner, Node> navigation = new WeakValueRegistry<>();
        List<WeakReference<?>> closed = closedScreens(containers, navigation);
        awaitCleared(closed);
        System.out.println("CLOSED_SCREEN_CYCLES_COLLECTED=128");
        Reference.reachabilityFence(containers);
        Reference.reachabilityFence(navigation);

        Owner active = new Owner();
        active.attached = new Node(active);
        navigation.put(active, active.attached);
        for (int i = 0; i < 5; i++) collect();
        require(navigation.get(active) == active.attached, "attached view must remain available");
        System.out.println("LIVE_TREE_OWNERSHIP_AND_LOOKUP=PASS");

        Node replacement = new Node(active);
        navigation.put(active, replacement);
        require(navigation.get(active) == replacement, "replacement lookup");
        require(navigation.remove(active) == replacement, "remove returns former value");
        require(navigation.get(active) == null, "removed entry lookup");
        require(navigation.remove(active) == null, "missing entry removal");
        navigation.put(active, replacement);
        navigation.put(active, null);
        require(navigation.get(active) == null, "null value removes entry");
        Reference.reachabilityFence(replacement);
        System.out.println("REPLACE_REMOVE_AND_MISSING_LOOKUP=PASS");

        WeakReference<Node> detached = detachedValue(navigation, active);
        awaitCleared(List.of(detached));
        require(navigation.get(active) == null, "collected value must behave as cache miss");
        require(active.attached != null, "owner is deliberately still alive");
        Reference.reachabilityFence(active);
        Reference.reachabilityFence(navigation);
        System.out.println("DETACHED_VALUE_WITH_LIVE_OWNER=PASS");
        System.out.println("RESULT=BOOST_ISSUE191_WEAK_VALUE_REGISTRY_JVM_PASS");
    }
}
