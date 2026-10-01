package com.fren_gor.ultimateAdvancementAPI.util;

import java.util.*;

/** Computes additions, removals and progress changes for one client. */
public record ClientStateDelta<K>(Map<K, Integer> added, Set<K> removed, Map<K, Integer> progress) {
    private static final ClientStateDelta<?> EMPTY = new ClientStateDelta<>(Map.of(), Set.of(), Map.of());

    @SuppressWarnings("unchecked")
    public static <K> ClientStateDelta<K> between(Map<K, Integer> previous, Map<K, Integer> current) {
        Map<K, Integer> added = null, progress = null;
        Set<K> removed = null;
        for (var entry : current.entrySet()) {
            K key = entry.getKey();
            Integer value = entry.getValue();
            Integer old = previous.get(key);
            if (old == null) {
                if (added == null) added = new HashMap<>();
                added.put(key, value);
            } else if (!old.equals(value)) {
                if (progress == null) progress = new HashMap<>();
                progress.put(key, value);
            }
        }
        for (K key : previous.keySet()) {
            if (!current.containsKey(key)) {
                if (removed == null) removed = new HashSet<>();
                removed.add(key);
            }
        }
        if (added == null && removed == null && progress == null) return (ClientStateDelta<K>) EMPTY;
        return new ClientStateDelta<>(added == null ? Map.of() : Map.copyOf(added),
                removed == null ? Set.of() : Set.copyOf(removed), progress == null ? Map.of() : Map.copyOf(progress));
    }
    public boolean isEmpty() { return added.isEmpty() && removed.isEmpty() && progress.isEmpty(); }
}
