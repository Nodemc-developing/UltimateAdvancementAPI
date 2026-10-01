package com.fren_gor.ultimateAdvancementAPI.util;

import java.util.*;

/** Computes additions, removals and progress changes for one client. */
public record ClientStateDelta<K>(Map<K, Integer> added, Set<K> removed, Map<K, Integer> progress) {
    public static <K> ClientStateDelta<K> between(Map<K, Integer> previous, Map<K, Integer> current) {
        Map<K, Integer> added = new HashMap<>(), progress = new HashMap<>();
        Set<K> removed = new HashSet<>(previous.keySet());
        removed.removeAll(current.keySet());
        current.forEach((key, value) -> {
            Integer old = previous.get(key);
            if (old == null) added.put(key, value);
            else if (!old.equals(value)) progress.put(key, value);
        });
        return new ClientStateDelta<>(Map.copyOf(added), Set.copyOf(removed), Map.copyOf(progress));
    }
    public boolean isEmpty() { return added.isEmpty() && removed.isEmpty() && progress.isEmpty(); }
}
